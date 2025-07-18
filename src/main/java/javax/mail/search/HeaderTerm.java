/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 1997-2012 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */

package javax1.mail.search;

import java.util.Locale;
import javax1.mail.Message;

/**
 * This class implements comparisons for Message headers.
 * The comparison is case-insensitive.
 *
 * @author Bill Shannon
 * @author John Mani
 */
public final class HeaderTerm extends StringTerm {
    /**
     * The name of the header.
     *
     * @serial
     */
    private String headerName;

    private static final long serialVersionUID = 8342514650333389122L;

    /**
     * Constructor.
     *
     * @param headerName The name of the header
     * @param pattern    The pattern to search for
     */
    public HeaderTerm(String headerName, String pattern) {
	super(pattern);
	this.headerName = headerName;
    }

    /**
     * Return the name of the header to compare with.
     */
    public String getHeaderName() {
	return headerName;
    }

    /**
     * The header match method.
     *
     * @param msg	The match is applied to this Message's header
     * @return		true if the match succeeds, otherwise false
     */
    public boolean match(Message msg) {
	String[] headers;

	try {
	    headers = msg.getHeader(headerName);
	} catch (Exception e) {
	    return false;
	}

	if (headers == null)
	    return false;

	for (int i=0; i < headers.length; i++)
	    if (super.match(headers[i]))
		return true;
	return false;
    }

    /**
     * Equality comparison.
     */
    public boolean equals(Object obj) {
	if (!(obj instanceof HeaderTerm))
	    return false;
	HeaderTerm ht = (HeaderTerm)obj;
	// XXX - depends on header comparisons being case independent
	return ht.headerName.equalsIgnoreCase(headerName) && super.equals(ht);
    }

    /**
     * Compute a hashCode for this object.
     */
    public int hashCode() {
	// XXX - depends on header comparisons being case independent
	return headerName.toLowerCase(Locale.ENGLISH).hashCode() +
					super.hashCode();
    }
}
