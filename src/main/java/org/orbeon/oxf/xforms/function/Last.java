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
package org.orbeon.oxf.xforms.function;

import org.orbeon.saxon.expr.*;
import org.orbeon.saxon.om.Item;
import org.orbeon.saxon.sxpath.XPathExpression;
import org.orbeon.saxon.trans.XPathException;
import org.orbeon.saxon.value.Int64Value;

public class Last extends XFormsFunction {

    public Item evaluateItem(XPathContext xpathContext) throws XPathException {
        if (xpathContext.getCurrentIterator() instanceof XPathExpression.ContextItemSequenceIterator) {
            // We have a top level expression and Saxon does not know about the context nodeset
            return new Int64Value(getContextStack(xpathContext).getCurrentNodeset().size());
        } else {
            return new Int64Value(xpathContext.getLast());
        }
    }

    @Override
    public int getIntrinsicDependencies() {
        return StaticProperty.DEPENDS_ON_LAST;
    }

    @Override
    public PathMap.PathMapNodeSet addToPathMap(PathMap pathMap, PathMap.PathMapNodeSet pathMapNodeSet) {
        // Leave everything unchanged
        return pathMapNodeSet;
    }
}
