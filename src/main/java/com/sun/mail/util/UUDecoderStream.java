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

package com.sun1.mail.util;

import java.io.*;

/**
 * This class implements a UUDecoder. It is implemented as
 * a FilterInputStream, so one can just wrap this class around
 * any input stream and read bytes from this filter. The decoding
 * is done as the bytes are read out.
 * 
 * @author John Mani
 * @author Bill Shannon
 */

public class UUDecoderStream extends FilterInputStream {
    private String name;
    private int mode;

    private byte[] buffer = new byte[45]; // max decoded chars in a line = 45
    private int bufsize = 0;	// size of the cache
    private int index = 0;	// index into the cache
    private boolean gotPrefix = false;
    private boolean gotEnd = false;
    private LineInputStream lin;
    private boolean ignoreErrors;
    private boolean ignoreMissingBeginEnd;
    private String readAhead;

    /**
     * Create a UUdecoder that decodes the specified input stream.
     * The System property <code>mail.mime.uudecode.ignoreerrors</code>
     * controls whether errors in the encoded data cause an exception
     * or are ignored.  The default is false (errors cause exception).
     * The System property <code>mail.mime.uudecode.ignoremissingbeginend</code>
     * controls whether a missing begin or end line cause an exception
     * or are ignored.  The default is false (errors cause exception).
     * @param in        the input stream
     */
    public UUDecoderStream(InputStream in) {
	super(in);
	lin = new LineInputStream(in);
	// default to false
	ignoreErrors = PropUtil.getBooleanSystemProperty(
	    "mail.mime.uudecode.ignoreerrors", false);
	// default to false
	ignoreMissingBeginEnd = PropUtil.getBooleanSystemProperty(
	    "mail.mime.uudecode.ignoremissingbeginend", false);
    }

    /**
     * Create a UUdecoder that decodes the specified input stream.
     * @param in        	the input stream
     * @param ignoreErrors	ignore errors?
     * @param ignoreMissingBeginEnd	ignore missing begin or end?
     */
    public UUDecoderStream(InputStream in, boolean ignoreErrors,
				boolean ignoreMissingBeginEnd) {
	super(in);
	lin = new LineInputStream(in);
	this.ignoreErrors = ignoreErrors;
	this.ignoreMissingBeginEnd = ignoreMissingBeginEnd;
    }

    /**
     * Read the next decoded byte from this input stream. The byte
     * is returned as an <code>int</code> in the range <code>0</code>
     * to <code>255</code>. If no byte is available because the end of
     * the stream has been reached, the value <code>-1</code> is returned.
     * This method blocks until input data is available, the end of the
     * stream is detected, or an exception is thrown.
     *
     * @return     next byte of data, or <code>-1</code> if the end of 
     *             stream is reached.
     * @exception  IOException  if an I/O error occurs.
     * @see        java.io.FilterInputStream#in
     */
    public int read() throws IOException {
	if (index >= bufsize) {
	    readPrefix();
	    if (!decode())
		return -1;
	    index = 0; // reset index into buffer
	}
	return buffer[index++] & 0xff; // return lower byte
    }

    public int read(byte[] buf, int off, int len) throws IOException {
	int i, c;
	for (i = 0; i < len; i++) {
	    if ((c = read()) == -1) {
		if (i == 0) // At end of stream, so we should
		    i = -1; // return -1, NOT 0.
		break;
	    }
	    buf[off+i] = (byte)c;
	}
	return i;
    }

    public boolean markSupported() {
	return false;
    }

    public int available() throws IOException {
	 // This is only an estimate, since in.available()
	 // might include CRLFs too ..
	 return ((in.available() * 3)/4 + (bufsize-index));
    }

    /**
     * Get the "name" field from the prefix. This is meant to
     * be the pathname of the decoded file
     *
     * @return     name of decoded file
     * @exception  IOException  if an I/O error occurs.
     */
    public String getName() throws IOException {
	readPrefix();
	return name;
    }

    /**
     * Get the "mode" field from the prefix. This is the permission
     * mode of the source file.
     *
     * @return     permission mode of source file
     * @exception  IOException  if an I/O error occurs.
     */
    public int getMode() throws IOException {
	readPrefix();
	return mode;
    }

    /**
     * UUencoded streams start off with the line:
     *  "begin <mode> <filename>"
     * Search for this prefix and gobble it up.
     */
    private void readPrefix() throws IOException {
	if (gotPrefix) // got the prefix
	    return;

	mode = 0666;		// defaults, overridden below
	name = "encoder.buf";	// same default used by encoder
	String line;
	for (;;) {
	    // read till we get the prefix: "begin MODE FILENAME"
	    line = lin.readLine(); // NOTE: readLine consumes CRLF pairs too
	    if (line == null) {
		if (!ignoreMissingBeginEnd)
		    throw new DecodingException("UUDecoder: Missing begin");
		// at EOF, fake it
		gotPrefix = true;
		gotEnd = true;
		break;
	    }
	    if (line.regionMatches(false, 0, "begin", 0, 5)) {
		try {
		    mode = Integer.parseInt(line.substring(6,9));
		} catch (NumberFormatException ex) {
		    if (!ignoreErrors)
			throw new DecodingException(
				"UUDecoder: Error in mode: " + ex.toString());
		}
		if (line.length() > 10) {
		    name = line.substring(10);
		} else {
		    if (!ignoreErrors)
			throw new DecodingException(
				"UUDecoder: Missing name: " + line);
		}
		gotPrefix = true;
		break;
	    } else if (ignoreMissingBeginEnd && line.length() != 0) {
		int count = line.charAt(0);
		count = (count - ' ') & 0x3f;
		int need = ((count * 8)+5)/6;
		if (need == 0 || line.length() >= need + 1) {
		    /*
		     * Looks like a legitimate encoded line.
		     * Pretend we saw the "begin" line and
		     * save this line for later processing in
		     * decode().
		     */
		    readAhead = line;
		    gotPrefix = true;	// fake it
		    break;
		}
	    }
	}
    }

    private boolean decode() throws IOException {

	if (gotEnd)
	    return false;
	bufsize = 0;
	int count = 0;
	String line;
	for (;;) {
	    /*
	     * If we ignored a missing "begin", the first line
	     * will be saved in readAhead.
	     */
	    if (readAhead != null) {
		line = readAhead;
		readAhead = null;
	    } else
		line = lin.readLine();

	    /*
	     * Improperly encoded data sometimes omits the zero length
	     * line that starts with a space character, we detect the
	     * following "end" line here.
	     */
	    if (line == null) {
		if (!ignoreMissingBeginEnd)
		    throw new DecodingException(
					"UUDecoder: Missing end at EOF");
		gotEnd = true;
		return false;
	    }
	    if (line.equals("end")) {
		gotEnd = true;
		return false;
	    }
	    if (line.length() == 0)
		continue;
	    count = line.charAt(0);
	    if (count < ' ') {
		if (!ignoreErrors)
		    throw new DecodingException(
					"UUDecoder: Buffer format error");
		continue;
	    }

	    /*
	     * The first character in a line is the number of original (not
	     *  the encoded atoms) characters in the line. Note that all the
	     *  code below has to handle the <SPACE> character that indicates
	     *  end of encoded stream.
	     */
	    count = (count - ' ') & 0x3f;

	    if (count == 0) {
		line = lin.readLine();
		if (line == null || !line.equals("end")) {
		    if (!ignoreMissingBeginEnd)
			throw new DecodingException(
				"UUDecoder: Missing End after count 0 line");
		}
		gotEnd = true;
		return false;
	    }

	    int need = ((count * 8)+5)/6;
//System.out.println("count " + count + ", need " + need + ", len " + line.length());
	    if (line.length() < need + 1) {
		if (!ignoreErrors)
		    throw new DecodingException(
					"UUDecoder: Short buffer error");
		continue;
	    }

	    // got a line we're committed to, break out and decode it
	    break;
	}
	    
	int i = 1;
	byte a, b;
	/*
	 * A correct uuencoder always encodes 3 characters at a time, even
	 * if there aren't 3 characters left.  But since some people out
	 * there have broken uuencoders we handle the case where they
	 * don't include these "unnecessary" characters.
	 */
	while (bufsize < count) {
	    // continue decoding until we get 'count' decoded chars
	    a = (byte)((line.charAt(i++) - ' ') & 0x3f);
	    b = (byte)((line.charAt(i++) - ' ') & 0x3f);
	    buffer[bufsize++] = (byte)(((a << 2) & 0xfc) | ((b >>> 4) & 3));

	    if (bufsize < count) {
		a = b;
		b = (byte)((line.charAt(i++) - ' ') & 0x3f);
		buffer[bufsize++] =
				(byte)(((a << 4) & 0xf0) | ((b >>> 2) & 0xf));
	    }

	    if (bufsize < count) {
		a = b;
		b = (byte)((line.charAt(i++) - ' ') & 0x3f);
		buffer[bufsize++] = (byte)(((a << 6) & 0xc0) | (b & 0x3f));
	    }
	}
	return true;
    }

    /*** begin TEST program *****
    public static void main(String argv[]) throws Exception {
    	FileInputStream infile = new FileInputStream(argv[0]);
	UUDecoderStream decoder = new UUDecoderStream(infile);
	int c;

	try {
	    while ((c = decoder.read()) != -1)
		System.out.write(c);
	    System.out.flush();
	} catch (Exception e) {
	    e.printStackTrace();
	}
    }
    **** end TEST program ****/
}
