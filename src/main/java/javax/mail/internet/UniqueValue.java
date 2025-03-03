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

package javax1.mail.internet;

import java.net.*;
import javax1.mail.Session;

/**
 * This is a utility class that generates unique values. The generated
 * String contains only US-ASCII characters and hence is safe for use
 * in RFC822 headers. <p>
 *
 * This is a package private class.
 *
 * @author John Mani
 * @author Max Spivak
 * @author Bill Shannon
 */

class UniqueValue {
    /**
     * A global unique number, to ensure uniqueness of generated strings.
     */
    private static int id = 0;

    /**
     * Get a unique value for use in a multipart boundary string.
     *
     * This implementation generates it by concatenating a global
     * part number, a newly created object's <code>hashCode()</code>,
     * and the current time (in milliseconds).
     */
    public static String getUniqueBoundaryValue() {
	StringBuffer s = new StringBuffer();

	// Unique string is ----=_Part_<part>_<hashcode>.<currentTime>
	s.append("----=_Part_").append(getUniqueId()).append("_").
	  append(s.hashCode()).append('.').
	  append(System.currentTimeMillis());
	return s.toString();
    }

    /**
     * Get a unique value for use in a Message-ID.
     *
     * This implementation generates it by concatenating a newly
     * created object's <code>hashCode()</code>, a global ID
     * (incremented on every use), the current
     * time (in milliseconds), the string "JavaMail", and
     * this user's local address generated by 
     * <code>InternetAddress.getLocalAddress()</code>.
     * (The address defaults to "javamailuser@localhost" if
     * <code>getLocalAddress()</code> returns null.)
     *
     * @param ssn Session object used to get the local address
     * @see javax1.mail.internet.InternetAddress
     */
    public static String getUniqueMessageIDValue(Session ssn) {
	String suffix = null;

	InternetAddress addr = InternetAddress.getLocalAddress(ssn);
	if (addr != null)
	    suffix = addr.getAddress();
	else {
	    suffix = "javamailuser@localhost"; // worst-case default
	}

	StringBuffer s = new StringBuffer();

	// Unique string is <hashcode>.<id>.<currentTime>.JavaMail.<suffix>
	s.append(s.hashCode()).append('.').append(getUniqueId()).append('.').
	  append(System.currentTimeMillis()).append('.').
	  append("JavaMail.").
	  append(suffix);
	return s.toString();
    }

    /**
     * Ensure ID is unique by synchronizing access.
     * XXX - Could use AtomicInteger.getAndIncrement() in J2SE 5.0.
     */
    private static synchronized int getUniqueId() {
	return id++;
    }
}
