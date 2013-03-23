/**
 * Copyright (C) 2011 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.xforms

import java.util.Set
import org.orbeon.oxf.util.IndentedLogger
import org.orbeon.oxf.xml.dom4j.LocationData
import org.orbeon.oxf.xml.XMLUtils
import state.AnnotatedTemplate

trait XFormsStaticState extends XMLUtils.DebugXML  {

    def locationData: LocationData
    def getIndentedLogger: IndentedLogger

    def digest: String
    def encodedState: String
    def getAllowedExternalEvents: Set[String]   // later make more generic
    def isDynamicNoscriptTemplate: Boolean
    def template: Option[AnnotatedTemplate]

    def topLevelPart: PartAnalysis

    def isCacheDocument: Boolean
    def isClientStateHandling: Boolean
    def isServerStateHandling: Boolean
    def isNoscript: Boolean
    def isHTMLDocument: Boolean

    def isXPathAnalysis: Boolean
    def sanitizeInput: String ⇒ String

    def getNonDefaultProperties: Map[String, AnyRef]
    def getProperty[T](propertyName: String): T
    def getStringProperty(propertyName: String): String
    def getBooleanProperty(propertyName: String): Boolean
    def getIntegerProperty(propertyName: String): Int

    def dumpAnalysis()
}