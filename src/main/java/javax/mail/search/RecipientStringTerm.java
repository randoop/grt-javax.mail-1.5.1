/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 1997-2010 Oracle and/or its affiliates. All rights reserved.
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

import javax1.mail.Message;
import javax1.mail.Address;

/**
 * This class implements string comparisons for the Recipient Address
 * headers. <p>
 *
 * Note that this class differs from the <code>RecipientTerm</code> class
 * in that this class does comparisons on address strings rather than Address
 * objects. The string comparisons are case-insensitive.
 *
 * @since       JavaMail 1.1
 */

public final class RecipientStringTerm extends AddressStringTerm {

    /**
     * The recipient type.
     *
     * @serial
     */
    private Message.RecipientType type;

    private static final long serialVersionUID = -8293562089611618849L;

    /**
     * Constructor.
     *
     * @param type      the recipient type
     * @param pattern   the address pattern to be compared.
     */
    public RecipientStringTerm(Message.RecipientType type, String pattern) {
	super(pattern);
	this.type = type;
    }

    /**
     * Return the type of recipient to match with.
     */
    public Message.RecipientType getRecipientType() {
	return type;
    }

    /**
     * Check whether the address specified in the constructor is
     * a substring of the recipient address of this Message.
     *
     * @param   msg 	The comparison is applied to this Message's recipient
     *		    	address.
     * @return          true if the match succeeds, otherwise false.
     */
    public boolean match(Message msg) {
	Address[] recipients;

	try {
	    recipients = msg.getRecipients(type);
	} catch (Exception e) {
	    return false;
	}

	if (recipients == null)
	    return false;
	
	for (int i=0; i < recipients.length; i++)
	    if (super.match(recipients[i]))
		return true;
	return false;
    }

    /**
     * Equality comparison.
     */
    public boolean equals(Object obj) {
	if (!(obj instanceof RecipientStringTerm))
	    return false;
	RecipientStringTerm rst = (RecipientStringTerm)obj;
	return rst.type.equals(this.type) && super.equals(obj);
    }

    /**
     * Compute a hashCode for this object.
     */
    public int hashCode() {
	return type.hashCode() + super.hashCode();
    }
}
