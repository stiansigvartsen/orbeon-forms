/**
 * Copyright (C) 2010 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.function.exforms;

import org.orbeon.oxf.util.PooledXPathExpression;
import org.orbeon.oxf.xforms.function.xxforms.XXFormsSort;
import org.orbeon.saxon.expr.Expression;
import org.orbeon.saxon.expr.ExpressionVisitor;
import org.orbeon.saxon.expr.StaticProperty;
import org.orbeon.saxon.expr.XPathContext;
import org.orbeon.saxon.om.SequenceIterator;
import org.orbeon.saxon.trans.XPathException;

/**
 * exf:sort() function
 */
public class EXFormsSort extends XXFormsSort {

    @Override
    public SequenceIterator iterate(XPathContext xpathContext) throws XPathException {

        final Expression sequenceToSortExpression = argument[0];
        final Expression selectExpression = argument[1];

        final XPathContext sortKeyContext;
        final Expression sortKeyExpression;
        {
            // Prepare dynamic sort expression and its dynamic context
            final PooledXPathExpression pooledExpression = prepareExpression(xpathContext, selectExpression, false);

            sortKeyContext = pooledExpression.prepareDynamicContext(null, PooledXPathExpression.getFunctionContext(xpathContext)).getXPathContextObject();
            sortKeyExpression = pooledExpression.getExpression();
        }

        return sort(xpathContext, sortKeyContext, sequenceToSortExpression, sortKeyExpression);
    }

    @Override
    public void checkArguments(ExpressionVisitor visitor) throws XPathException {
        // Needed by prepareExpression()
        copyStaticContextIfNeeded(visitor);
    }

    @Override
    public int getIntrinsicDependencies() {
	    return StaticProperty.DEPENDS_ON_CONTEXT_ITEM;
    }
}
