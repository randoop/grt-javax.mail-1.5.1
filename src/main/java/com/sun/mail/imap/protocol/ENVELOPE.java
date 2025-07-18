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
import java.util.Date;
import java.io.UnsupportedEncodingException;
import java.text.ParseException;
import javax1.mail.internet.InternetAddress;
import javax1.mail.internet.AddressException;
import javax1.mail.internet.MailDateFormat;
import javax1.mail.internet.MimeUtility;
import com.sun1.mail.iap.*;

/**
 * The ENEVELOPE item of an IMAP FETCH response.
 *
 * @author  John Mani
 * @author  Bill Shannon
 */

public class ENVELOPE implements Item {
    
    // IMAP item name
    static final char[] name = {'E','N','V','E','L','O','P','E'};
    public int msgno;

    public Date date = null;
    public String subject;
    public InternetAddress[] from;
    public InternetAddress[] sender;
    public InternetAddress[] replyTo;
    public InternetAddress[] to;
    public InternetAddress[] cc;
    public InternetAddress[] bcc;
    public String inReplyTo;
    public String messageId;

    // Used to parse dates
    private static MailDateFormat mailDateFormat = new MailDateFormat();
    
    public ENVELOPE(FetchResponse r) throws ParsingException {
	msgno = r.getNumber();

	r.skipSpaces();

	if (r.readByte() != '(')
	    throw new ParsingException("ENVELOPE parse error");
	
	String s = r.readString();
	if (s != null) {
	    try {
		date = mailDateFormat.parse(s);
	    } catch (ParseException pex) {
	    } catch (RuntimeException pex) {
		// We need to be *very* tolerant about bogus dates (and
		// there's lots of 'em around), so we ignore any 
		// exception (including RuntimeExceptions) and just let 
		// date be null.
	    }
	}

	subject = r.readString();
	from = parseAddressList(r);
	sender = parseAddressList(r);
	replyTo = parseAddressList(r);
	to = parseAddressList(r);
	cc = parseAddressList(r);
	bcc = parseAddressList(r);
	inReplyTo = r.readString();
	messageId = r.readString();

	if (r.readByte() != ')')
	    throw new ParsingException("ENVELOPE parse error");
    }

    private InternetAddress[] parseAddressList(Response r) 
		throws ParsingException {
	r.skipSpaces(); // skip leading spaces

	byte b = r.readByte();
	if (b == '(') {
	    /*
	     * Some broken servers (e.g., Yahoo Mail) return an empty
	     * list instead of NIL.  Handle that here even though it
	     * doesn't conform to the IMAP spec.
	     */
	    if (r.peekByte() == ')') {
		r.skip(1);
		return null;
	    }

	    List<InternetAddress> v = new ArrayList<InternetAddress>();

	    do {
		IMAPAddress a = new IMAPAddress(r);
		// if we see an end-of-group address at the top, ignore it
		if (!a.isEndOfGroup())
		    v.add(a);
	    } while (r.peekByte() != ')');

	    // skip the terminating ')' at the end of the addresslist
	    r.skip(1);

	    return v.toArray(new InternetAddress[v.size()]);
	} else if (b == 'N' || b == 'n') { // NIL
	    r.skip(2); // skip 'NIL'
	    return null;
	} else
	    throw new ParsingException("ADDRESS parse error");
    }
}

class IMAPAddress extends InternetAddress {
    private boolean group = false;
    private InternetAddress[] grouplist;
    private String groupname;

    private static final long serialVersionUID = -3835822029483122232L;

    IMAPAddress(Response r) throws ParsingException {
        r.skipSpaces(); // skip leading spaces

        if (r.readByte() != '(')
            throw new ParsingException("ADDRESS parse error");

        encodedPersonal = r.readString();

        r.readString(); // throw away address_list
	String mb = r.readString();
	String host = r.readString();
	// skip bogus spaces inserted by Yahoo IMAP server if
	// "undisclosed-recipients" is a recipient
	r.skipSpaces();
        if (r.readByte() != ')') // skip past terminating ')'
            throw new ParsingException("ADDRESS parse error");

	if (host == null) {
	    // it's a group list, start or end
	    group = true;
	    groupname = mb;
	    if (groupname == null)	// end of group list
		return;
	    // Accumulate a group list.  The members of the group
	    // are accumulated in a List and the corresponding string
	    // representation of the group is accumulated in a StringBuffer.
	    StringBuffer sb = new StringBuffer();
	    sb.append(groupname).append(':');
	    List<InternetAddress> v = new ArrayList<InternetAddress>();
	    while (r.peekByte() != ')') {
		IMAPAddress a = new IMAPAddress(r);
		if (a.isEndOfGroup())	// reached end of group
		    break;
		if (v.size() != 0)	// if not first element, need a comma
		    sb.append(',');
		sb.append(a.toString());
		v.add(a);
	    }
	    sb.append(';');
	    address = sb.toString();
	    grouplist = v.toArray(new IMAPAddress[v.size()]);
	} else {
	    if (mb == null || mb.length() == 0)
		address = host;
	    else if (host.length() == 0)
		address = mb;
	    else
		address = mb + "@" + host;
	}

    }

    boolean isEndOfGroup() {
	return group && groupname == null;
    }

    public boolean isGroup() {
	return group;
    }

    public InternetAddress[] getGroup(boolean strict) throws AddressException {
	if (grouplist == null)
	    return null;
	return (InternetAddress[])grouplist.clone();
    }
}
