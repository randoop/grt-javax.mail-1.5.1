/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 1997-2013 Oracle and/or its affiliates. All rights reserved.
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

package com.sun1.mail.imap.protocol;

import java.util.List;
import java.util.ArrayList;

import com.sun1.mail.iap.*;

/**
 * A LIST response.
 *
 * @author  John Mani
 * @author  Bill Shannon
 */

public class ListInfo { 
    public String name = null;
    public char separator = '/';
    public boolean hasInferiors = true;
    public boolean canOpen = true;
    public int changeState = INDETERMINATE;
    public String[] attrs;

    public static final int CHANGED		= 1;
    public static final int UNCHANGED		= 2;
    public static final int INDETERMINATE	= 3;

    public ListInfo(IMAPResponse r) throws ParsingException {
	String[] s = r.readSimpleList();

	List<String> v = new ArrayList<String>();	// accumulate attributes
	if (s != null) {
	    // non-empty attribute list
	    for (int i = 0; i < s.length; i++) {
		if (s[i].equalsIgnoreCase("\\Marked"))
		    changeState = CHANGED;
		else if (s[i].equalsIgnoreCase("\\Unmarked"))
		    changeState = UNCHANGED;
		else if (s[i].equalsIgnoreCase("\\Noselect"))
		    canOpen = false;
		else if (s[i].equalsIgnoreCase("\\Noinferiors"))
		    hasInferiors = false;
		v.add(s[i]);
	    }
	}
	attrs = v.toArray(new String[v.size()]);

	r.skipSpaces();
	if (r.readByte() == '"') {
	    if ((separator = (char)r.readByte()) == '\\')
		// escaped separator character
		separator = (char)r.readByte();	
	    r.skip(1); // skip <">
	} else // NIL
	    r.skip(2);
	
	r.skipSpaces();
	name = r.readAtomString();

	// decode the name (using RFC2060's modified UTF7)
	name = BASE64MailboxDecoder.decode(name);
    }
}
