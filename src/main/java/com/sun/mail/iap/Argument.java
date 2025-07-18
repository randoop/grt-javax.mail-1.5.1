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

package com.sun1.mail.iap;

import java.util.List;
import java.util.ArrayList;
import java.io.*;
import com.sun1.mail.util.*;

/**
 * @author  John Mani
 * @author  Bill Shannon
 */

public class Argument {
    protected List items;

    /**
     * Constructor
     */
    public Argument() {
	items = new ArrayList(1);
    }

    /**
     * Append the given Argument to this Argument. All items
     * from the source argument are copied into this destination
     * argument.
     */
    public Argument append(Argument arg) {
	items.addAll(arg.items);
	return this;
    }

    /**
     * Write out given string as an ASTRING, depending on the type
     * of the characters inside the string. The string should
     * contain only ASCII characters. <p>
     *
     * XXX: Hmm .. this should really be called writeASCII()
     *
     * @param s  String to write out
     */
    public Argument writeString(String s) {
	items.add(new AString(ASCIIUtility.getBytes(s)));
	return this;
    }

    /**
     * Convert the given string into bytes in the specified
     * charset, and write the bytes out as an ASTRING
     */
    public Argument writeString(String s, String charset)
		throws UnsupportedEncodingException {
	if (charset == null) // convenience
	    writeString(s);
	else
	    items.add(new AString(s.getBytes(charset)));
	return this;
    }

    /**
     * Write out given string as an NSTRING, depending on the type
     * of the characters inside the string. The string should
     * contain only ASCII characters. <p>
     *
     * @param s  String to write out
     * @since	JavaMail 1.5.1
     */
    public Argument writeNString(String s) {
	if (s == null)
	    items.add(new NString(null));
	else
	    items.add(new NString(ASCIIUtility.getBytes(s)));
	return this;
    }

    /**
     * Convert the given string into bytes in the specified
     * charset, and write the bytes out as an NSTRING
     *
     * @since	JavaMail 1.5.1
     */
    public Argument writeNString(String s, String charset)
		throws UnsupportedEncodingException {
	if (s == null)
	    items.add(new NString(null));
	else if (charset == null) // convenience
	    writeString(s);
	else
	    items.add(new NString(s.getBytes(charset)));
	return this;
    }

    /**
     * Write out given byte[] as a Literal.
     * @param b  byte[] to write out
     */
    public Argument writeBytes(byte[] b)  {
	items.add(b);
	return this;
    }

    /**
     * Write out given ByteArrayOutputStream as a Literal.
     * @param b  ByteArrayOutputStream to be written out.
     */
    public Argument writeBytes(ByteArrayOutputStream b)  {
	items.add(b);
	return this;
    }

    /**
     * Write out given data as a literal.
     * @param b  Literal representing data to be written out.
     */
    public Argument writeBytes(Literal b)  {
	items.add(b);
	return this;
    }

    /**
     * Write out given string as an Atom. Note that an Atom can contain only
     * certain US-ASCII characters.  No validation is done on the characters 
     * in the string.
     * @param s  String
     */
    public Argument writeAtom(String s) {
	items.add(new Atom(s));
	return this;
    }

    /**
     * Write out number.
     * @param i number
     */
    public Argument writeNumber(int i) {
	items.add(Integer.valueOf(i));
	return this;
    }

    /**
     * Write out number.
     * @param i number
     */
    public Argument writeNumber(long i) {
	items.add(Long.valueOf(i));
	return this;
    }

    /**
     * Write out as parenthesised list.
     */
    public Argument writeArgument(Argument c) {
	items.add(c);
	return this;
    }

    /*
     * Write out all the buffered items into the output stream.
     */
    public void write(Protocol protocol) 
		throws IOException, ProtocolException {
	int size = items != null ? items.size() : 0;
	DataOutputStream os = (DataOutputStream)protocol.getOutputStream();

	for (int i=0; i < size; i++) {
	    if (i > 0)	// write delimiter if not the first item
		os.write(' ');

	    Object o = items.get(i);
	    if (o instanceof Atom) {
		os.writeBytes(((Atom)o).string);
	    } else if (o instanceof Number) {
		os.writeBytes(((Number)o).toString());
	    } else if (o instanceof AString) {
		astring(((AString)o).bytes, protocol);
	    } else if (o instanceof NString) {
		nstring(((NString)o).bytes, protocol);
	    } else if (o instanceof byte[]) {
		literal((byte[])o, protocol);
	    } else if (o instanceof ByteArrayOutputStream) {
		literal((ByteArrayOutputStream)o, protocol);
	    } else if (o instanceof Literal) {
		literal((Literal)o, protocol);
	    } else if (o instanceof Argument) {
		os.write('('); // open parans
		((Argument)o).write(protocol);
		os.write(')'); // close parans
	    }
	}
    }

    /**
     * Write out given String as either an Atom, QuotedString or Literal
     */
    private void astring(byte[] bytes, Protocol protocol) 
			throws IOException, ProtocolException {
	nastring(bytes, protocol, false);
    }

    /**
     * Write out given String as either NIL, QuotedString, or Literal.
     */
    private void nstring(byte[] bytes, Protocol protocol) 
			throws IOException, ProtocolException {
	if (bytes == null) {
	    DataOutputStream os = (DataOutputStream)protocol.getOutputStream();
	    os.writeBytes("NIL");
	} else
	    nastring(bytes, protocol, true);
    }

    private void nastring(byte[] bytes, Protocol protocol, boolean doQuote) 
			throws IOException, ProtocolException {
	DataOutputStream os = (DataOutputStream)protocol.getOutputStream();
	int len = bytes.length;

	// If length is greater than 1024 bytes, send as literal
	if (len > 1024) {
	    literal(bytes, protocol);
	    return;
	}

        // if 0 length, send as quoted-string
        boolean quote = len == 0 ? true : doQuote;
	boolean escape = false;
 	
	byte b;
	for (int i = 0; i < len; i++) {
	    b = bytes[i];
	    if (b == '\0' || b == '\r' || b == '\n' || ((b & 0xff) > 0177)) {
		// NUL, CR or LF means the bytes need to be sent as literals
		literal(bytes, protocol);
		return;
	    }
	    if (b == '*' || b == '%' || b == '(' || b == ')' || b == '{' ||
		b == '"' || b == '\\' || ((b & 0xff) <= ' ')) {
		quote = true;
		if (b == '"' || b == '\\') // need to escape these characters
		    escape = true;
	    }
	}

	/*
	 * Make sure the (case-independent) string "NIL" is always quoted,
	 * so as not to be confused with a real NIL (handled above in nstring).
	 * This is more than is necessary, but it's rare to begin with and
	 * this makes it safer than doing the test in nstring above in case
	 * some code calls writeString when it should call writeNString.
	 */
	if (!quote && bytes.length == 3 &&
		(bytes[0] == 'N' || bytes[0] == 'n') &&
		(bytes[1] == 'I' || bytes[1] == 'i') &&
		(bytes[2] == 'L' || bytes[2] == 'l'))
	    quote = true;

	if (quote) // start quote
	    os.write('"');

        if (escape) {
            // already quoted
            for (int i = 0; i < len; i++) {
                b = bytes[i];
                if (b == '"' || b == '\\')
                    os.write('\\');
                os.write(b);
            }
        } else 
            os.write(bytes);
 

	if (quote) // end quote
	    os.write('"');
    }

    /**
     * Write out given byte[] as a literal
     */
    private void literal(byte[] b, Protocol protocol) 
			throws IOException, ProtocolException {
	startLiteral(protocol, b.length).write(b);
    }

    /**
     * Write out given ByteArrayOutputStream as a literal.
     */
    private void literal(ByteArrayOutputStream b, Protocol protocol) 
			throws IOException, ProtocolException {
	b.writeTo(startLiteral(protocol, b.size()));
    }

    /**
     * Write out given Literal as a literal.
     */
    private void literal(Literal b, Protocol protocol) 
			throws IOException, ProtocolException {
	b.writeTo(startLiteral(protocol, b.size()));
    }

    private OutputStream startLiteral(Protocol protocol, int size) 
			throws IOException, ProtocolException {
	DataOutputStream os = (DataOutputStream)protocol.getOutputStream();
	boolean nonSync = protocol.supportsNonSyncLiterals();

	os.write('{');
	os.writeBytes(Integer.toString(size));
	if (nonSync) // server supports non-sync literals
	    os.writeBytes("+}\r\n");
	else
	    os.writeBytes("}\r\n");
	os.flush();

	// If we are using synchronized literals, wait for the server's
	// continuation signal
	if (!nonSync) {
	    for (; ;) {
		Response r = protocol.readResponse();
		if (r.isContinuation())
		    break;
		if (r.isTagged())
		    throw new LiteralException(r);
		// XXX - throw away untagged responses;
		//	 violates IMAP spec, hope no servers do this
	    }
	}
	return os;
    }
}

class Atom {
    String string;

    Atom(String s) {
	string = s;
    }
}

class AString {
    byte[] bytes;

    AString(byte[] b) {
	bytes = b;
    }
}

class NString {
    byte[] bytes;

    NString(byte[] b) {
	bytes = b;
    }
}
