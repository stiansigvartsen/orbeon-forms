/**
 *  Copyright (C) 2013 Orbeon, Inc.
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.util

import org.orbeon.oxf.xml.{XMLUtils, ShareableXPathStaticContext, NamespaceMapping}
import org.orbeon.saxon.functions.FunctionLibrary
import org.orbeon.saxon.sxpath.{XPathEvaluator, XPathStaticContext, XPathExpression}
import org.orbeon.saxon.style.AttributeValueTemplate
import org.orbeon.saxon.expr.{XPathContextMajor, ExpressionTool, ExpressionVisitor, Expression}
import org.orbeon.saxon.`type`.Type
import org.orbeon.saxon.om._
import java.util.{List ⇒ JList}
import org.orbeon.oxf.xml.dom4j.{Dom4jUtils, ExtendedLocationData, LocationData}
import org.orbeon.saxon.value.{Value, AtomicValue}
import org.orbeon.oxf.common.ValidationException
import org.orbeon.saxon.Configuration
import javax.xml.transform.{TransformerException, Source, URIResolver}
import org.orbeon.oxf.resources.URLFactory
import javax.xml.transform.sax.SAXSource
import org.xml.sax.InputSource
import org.orbeon.saxon.dom4j.DocumentWrapper

object XPath {

    // Marker for XPath function context
    trait FunctionContext

    // To report timing information
    type Reporter = (String, Long) ⇒ Unit

    // To resolve a variable
    type VariableResolver = (StructuredQName, Item) ⇒ ValueRepresentation

    // Compiled expression with source information
    case class CompiledExpression(expression: XPathExpression, string: String, locationData: LocationData)

    // For Java callers, mix-in trait
    abstract class JVariableResolver extends VariableResolver

    // Global Saxon configuration with a global name pool
    val GlobalConfiguration = new Configuration {

        setNamePool(new NamePool)

        override def setAllowExternalFunctions(allowExternalFunctions: Boolean): Unit =
            throw new IllegalStateException("Global XPath configuration is read-only")

        override def setConfigurationProperty(name: String, value: AnyRef): Unit =
            throw new IllegalStateException("Global XPath configuration is read-only")
    }

    // Global document wrapper
    // NOTE: Not ideal to require this. See also XML.
    val DocumentWrapper = new DocumentWrapper(Dom4jUtils.createDocument, null, XPathCache.getGlobalConfiguration)

    // Create and compile an expression
    def compileExpression(xpathString: String, namespaceMapping: NamespaceMapping, locationData: LocationData, functionLibrary: FunctionLibrary, avt: Boolean): CompiledExpression = {
        val staticContext = new ShareableXPathStaticContext(GlobalConfiguration, namespaceMapping, functionLibrary)
        CompiledExpression(compileExpressionWithStaticContext(staticContext, xpathString, avt), xpathString, locationData)
    }

    // Create and compile an expression
    def compileExpressionWithStaticContext(staticContext: XPathStaticContext, xpathString: String, avt: Boolean): XPathExpression =
        if (avt) {
            val tempExpression = AttributeValueTemplate.make(xpathString, -1, staticContext)
            prepareExpressionForAVT(staticContext, tempExpression)
        } else {
            val evaluator = new XPathEvaluator
            evaluator.setStaticContext(staticContext)
            evaluator.createExpression(xpathString)
        }

    // Ideally: add this to Saxon XPathEvaluator
    private def prepareExpressionForAVT(staticContext: XPathStaticContext, expression: Expression): XPathExpression = {
        // Based on XPathEvaluator.createExpression()
        expression.setContainer(staticContext)
        val visitor = ExpressionVisitor.make(staticContext)
        visitor.setExecutable(staticContext.getExecutable)
        var newExpression = visitor.typeCheck(expression, Type.ITEM_TYPE)
        newExpression = visitor.optimize(newExpression, Type.ITEM_TYPE)
        val map = staticContext.getStackFrameMap
        val numberOfExternalVariables = map.getNumberOfVariables
        ExpressionTool.allocateSlots(expression, numberOfExternalVariables, map)

        // Set an evaluator as later it might be requested
        val evaluator = new XPathEvaluator
        evaluator.setStaticContext(staticContext)

        // See history for comment on CustomXPathExpression vs. modifying Saxon
        new XPathExpression(evaluator, newExpression) {
            setStackFrameMap(map, numberOfExternalVariables)
        }
    }

    // Return either a NodeInfo for nodes, a native Java value for atomic values, or null
    def evaluateSingle(
            contextItems: JList[Item],
            contextPosition: Int,
            compiledExpression: CompiledExpression,
            functionContext: FunctionContext,
            variableResolver: VariableResolver)
            (implicit reporter: Reporter) = {

        withEvaluation(compiledExpression) { xpathExpression ⇒

            val (contextItem, position) =
                if (contextPosition > 0 && contextPosition <= contextItems.size)
                    (contextItems.get(contextPosition - 1), contextPosition)
                else
                    (null, 0)

            val dynamicContext = xpathExpression.createDynamicContext(contextItem, position)
            val xpathContext = dynamicContext.getXPathContextObject.asInstanceOf[XPathContextMajor]

            // Pass dynamic data to controller
            xpathContext.getController.setUserData("", classOf[PooledXPathExpression].getName, functionContext)
            xpathContext.getController.setUserData(classOf[ShareableXPathStaticContext].getName, "variableResolver", variableResolver)

            val iterator = xpathExpression.iterate(dynamicContext)
            iterator.next() match {
                case atomicValue: AtomicValue ⇒ Value.convertToJava(atomicValue)
                case nodeInfo: NodeInfo       ⇒ nodeInfo
                case null                     ⇒ null
                case _                        ⇒ throw new IllegalStateException // Saxon guarantees that an Item is either AtomicValue or NodeInfo
            }
        }
    }

    // Return a string, or null if the expression returned an empty sequence
    // TODO: Should always return a string!
    // TODO: Check what we do upon NodeInfo
    // NOTE: callers tend to use string(foo), so issue of null/NodeInfo should not occur
    def evaluateAsString(
            contextItems: JList[Item],
            contextPosition: Int,
            compiledExpression: CompiledExpression,
            functionContext: FunctionContext,
            variableResolver: VariableResolver)
            (implicit reporter: Reporter) =
        Option(evaluateSingle(contextItems, contextPosition, compiledExpression, functionContext, variableResolver)) map (_.toString) orNull

    private def withEvaluation[T](expression: CompiledExpression)(body: XPathExpression ⇒ T)(implicit reporter: Reporter): T =
        try {
            if (reporter ne null) {
                val startTime = System.nanoTime
                val result = body(expression.expression)
                val totalTimeMicroSeconds = (System.nanoTime - startTime) / 1000 // never smaller than 1000 ns on OS X
                if (totalTimeMicroSeconds > 0)
                    reporter(expression.string, totalTimeMicroSeconds)

                result
            } else
                body(expression.expression)
        } catch {
            case e: Exception ⇒ throw handleXPathException(e, expression.string, "evaluating XPath expression", expression.locationData)
        }

    def handleXPathException(e: Exception, xpathString: String, description: String, locationData: LocationData) = {
        val validationException = ValidationException.wrapException(e, new ExtendedLocationData(locationData, description, "expression", xpathString))

        // Details of ExtendedLocationData passed are discarded by the constructor for ExtendedLocationData above,
        // so we need to explicitly add them.
        if (locationData.isInstanceOf[ExtendedLocationData])
            validationException.addLocationData(locationData)

        validationException
    }

    val URIResolver = new URIResolver {
        def resolve(href: String, base: String): Source =
            try {
                // Saxon Document.makeDoc() changes the base to "" if it is null
                // NOTE: We might use TransformerURIResolver/ExternalContext in the future (ThreadLocal)
                val url = URLFactory.createURL(if (base == "") null else base, href)
                new SAXSource(XMLUtils.newXMLReader(XMLUtils.ParserConfiguration.PLAIN), new InputSource(url.openStream))
            } catch {
                case e: Exception ⇒ throw new TransformerException(e)
            }
    }
}
