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

package com.sun1.mail.imap;

import java.io.*;

import java.util.Enumeration;
import javax1.mail.*;
import javax1.mail.internet.*;
import javax.activation.*;

import com.sun1.mail.util.*;
import com.sun1.mail.iap.*;
import com.sun1.mail.imap.protocol.*;

/**
 * An IMAP body part.
 *
 * @author  John Mani
 * @author  Bill Shannon
 */

public class IMAPBodyPart extends MimeBodyPart implements ReadableMime {
    private IMAPMessage message;
    private BODYSTRUCTURE bs;
    private String sectionId;

    // processed values ..
    private String type;
    private String description;

    private boolean headersLoaded = false;

    private static final boolean decodeFileName =
	PropUtil.getBooleanSystemProperty("mail.mime.decodefilename", false);

    protected IMAPBodyPart(BODYSTRUCTURE bs, String sid, IMAPMessage message) {
	super();
	this.bs = bs;
	this.sectionId = sid;
	this.message = message;
	// generate content-type
	ContentType ct = new ContentType(bs.type, bs.subtype, bs.cParams);
	type = ct.toString();
    }

    /* Override this method to make it a no-op, rather than throw
     * an IllegalWriteException. This will permit IMAPBodyParts to
     * be inserted in newly crafted MimeMessages, especially when
     * forwarding or replying to messages.
     */
    protected void updateHeaders() {
	return;
    }

    public int getSize() throws MessagingException {
	return bs.size;
    }

    public int getLineCount() throws MessagingException {
	return bs.lines;
    }

    public String getContentType() throws MessagingException {
	return type;
    }

    public String getDisposition() throws MessagingException {
	return bs.disposition;
    }

    public void setDisposition(String disposition) throws MessagingException {
	throw new IllegalWriteException("IMAPBodyPart is read-only");
    }

    public String getEncoding() throws MessagingException {
	return bs.encoding;
    }

    public String getContentID() throws MessagingException {
	return bs.id;
    }

    public String getContentMD5() throws MessagingException {
	return bs.md5;
    }

    public void setContentMD5(String md5) throws MessagingException {
	throw new IllegalWriteException("IMAPBodyPart is read-only");
    }

    public String getDescription() throws MessagingException {
	if (description != null) // cached value ?
	    return description;

	if (bs.description == null)
	    return null;
	
	try {
	    description = MimeUtility.decodeText(bs.description);
	} catch (UnsupportedEncodingException ex) {
	    description = bs.description;
	}

	return description;
    }

    public void setDescription(String description, String charset)
			throws MessagingException {
	throw new IllegalWriteException("IMAPBodyPart is read-only");
    }

    public String getFileName() throws MessagingException {
	String filename = null;
	if (bs.dParams != null)
	    filename = bs.dParams.get("filename");
	if (filename == null && bs.cParams != null)
	    filename = bs.cParams.get("name");
	if (decodeFileName && filename != null) {
	    try {
		filename = MimeUtility.decodeText(filename);
	    } catch (UnsupportedEncodingException ex) {
		throw new MessagingException("Can't decode filename", ex);
	    }
	}
	return filename;
    }

    public void setFileName(String filename) throws MessagingException {
	throw new IllegalWriteException("IMAPBodyPart is read-only");
    }

    protected InputStream getContentStream() throws MessagingException {
	InputStream is = null;
	boolean pk = message.getPeek();	// acquire outside of message cache lock

        // Acquire MessageCacheLock, to freeze seqnum.
        synchronized(message.getMessageCacheLock()) {
	    try {
		IMAPProtocol p = message.getProtocol();

		// Check whether this message is expunged
		message.checkExpunged();

		if (p.isREV1() && (message.getFetchBlockSize() != -1))
		    return new IMAPInputStream(message, sectionId,
			message.ignoreBodyStructureSize() ? -1 : bs.size, pk);

		// Else, vanila IMAP4, no partial fetch 

		int seqnum = message.getSequenceNumber();
		BODY b;
		if (pk)
		    b = p.peekBody(seqnum, sectionId);
		else
		    b = p.fetchBody(seqnum, sectionId);
		if (b != null)
		    is = b.getByteArrayInputStream();
	    } catch (ConnectionException cex) {
		throw new FolderClosedException(
			message.getFolder(), cex.getMessage());
	    } catch (ProtocolException pex) { 
		throw new MessagingException(pex.getMessage(), pex);
	    }
	}

	if (is == null)
	    throw new MessagingException("No content");
	else
	    return is;
    }

    /**
     * Return the MIME format stream of headers for this body part.
     */
    private InputStream getHeaderStream() throws MessagingException {
	if (!message.isREV1())
	    loadHeaders();	// will be needed below

	// Acquire MessageCacheLock, to freeze seqnum.
	synchronized(message.getMessageCacheLock()) {
	    try {
		IMAPProtocol p = message.getProtocol();

		// Check whether this message got expunged
		message.checkExpunged();

		if (p.isREV1()) {
		    int seqnum = message.getSequenceNumber();
		    BODY b = p.peekBody(seqnum, sectionId + ".MIME");

		    if (b == null)
			throw new MessagingException("Failed to fetch headers");

		    ByteArrayInputStream bis = b.getByteArrayInputStream();
		    if (bis == null)
			throw new MessagingException("Failed to fetch headers");
		    return bis;

		} else {
		    // Can't read it from server, have to fake it
		    SharedByteArrayOutputStream bos =
			new SharedByteArrayOutputStream(0);
		    LineOutputStream los = new LineOutputStream(bos);

		    try {
			// Write out the header
			Enumeration hdrLines = super.getAllHeaderLines();
			while (hdrLines.hasMoreElements())
			    los.writeln((String)hdrLines.nextElement());

			// The CRLF separator between header and content
			los.writeln();
		    } catch (IOException ioex) {
			// should never happen
		    } finally {
			try {
			    los.close();
			} catch (IOException cex) { }
		    }
		    return bos.toStream();
		}
	    } catch (ConnectionException cex) {
		throw new FolderClosedException(
			    message.getFolder(), cex.getMessage());
	    } catch (ProtocolException pex) {
		throw new MessagingException(pex.getMessage(), pex);
	    }
	}
    }

    /**
     * Return the MIME format stream corresponding to this message part.
     *
     * @return	the MIME format stream
     * @since	JavaMail 1.4.5
     */
    public InputStream getMimeStream() throws MessagingException {
	/*
	 * The IMAP protocol doesn't support returning the entire
	 * part content in one operation so we have to fake it by
	 * concatenating the header stream and the content stream.
	 */
	return new SequenceInputStream(getHeaderStream(), getContentStream());
    }
	    
    public synchronized DataHandler getDataHandler() 
		throws MessagingException {
	if (dh == null) {
	   if (bs.isMulti())
		dh = new DataHandler(
			new IMAPMultipartDataSource(
				this, bs.bodies, sectionId, message)
		     );
	    else if (bs.isNested() && message.isREV1() && bs.envelope != null)
		dh = new DataHandler(
			new IMAPNestedMessage(message, 
					      bs.bodies[0],
					      bs.envelope,
					      sectionId),
			type
		     );
	}

	return super.getDataHandler();
    }

    public void setDataHandler(DataHandler content) throws MessagingException {
	throw new IllegalWriteException("IMAPBodyPart is read-only");
    }

    public void setContent(Object o, String type) throws MessagingException {
	throw new IllegalWriteException("IMAPBodyPart is read-only");
    }

    public void setContent(Multipart mp) throws MessagingException {
	throw new IllegalWriteException("IMAPBodyPart is read-only");
    }

    public String[] getHeader(String name) throws MessagingException {
	loadHeaders();
	return super.getHeader(name);
    }

    public void setHeader(String name, String value)
		throws MessagingException {
	throw new IllegalWriteException("IMAPBodyPart is read-only");
    }

    public void addHeader(String name, String value)
		throws MessagingException {
	throw new IllegalWriteException("IMAPBodyPart is read-only");
    }

    public void removeHeader(String name) throws MessagingException {
	throw new IllegalWriteException("IMAPBodyPart is read-only");
    }

    public Enumeration getAllHeaders() throws MessagingException {
	loadHeaders();
	return super.getAllHeaders();
    }

    public Enumeration getMatchingHeaders(String[] names)
		throws MessagingException {
	loadHeaders();
	return super.getMatchingHeaders(names);
    }

    public Enumeration getNonMatchingHeaders(String[] names)
		throws MessagingException {
	loadHeaders();
	return super.getNonMatchingHeaders(names);
    }

    public void addHeaderLine(String line) throws MessagingException {
	throw new IllegalWriteException("IMAPBodyPart is read-only");
    }

    public Enumeration getAllHeaderLines() throws MessagingException {
	loadHeaders();
	return super.getAllHeaderLines();
    }

    public Enumeration getMatchingHeaderLines(String[] names)
		throws MessagingException {
	loadHeaders();
	return super.getMatchingHeaderLines(names);
    }

    public Enumeration getNonMatchingHeaderLines(String[] names)
		throws MessagingException {
	loadHeaders();
	return super.getNonMatchingHeaderLines(names);
    }

    private synchronized void loadHeaders() throws MessagingException {
	if (headersLoaded)
	    return;

	// "headers" should never be null since it's set in the constructor.
	// If something did go wrong this will fix it, but is an unsynchronized
	// assignment of "headers".
	if (headers == null)
	    headers = new InternetHeaders();

	// load headers

	// Acquire MessageCacheLock, to freeze seqnum.
	synchronized(message.getMessageCacheLock()) {
	    try {
		IMAPProtocol p = message.getProtocol();

		// Check whether this message got expunged
		message.checkExpunged();

		if (p.isREV1()) {
		    int seqnum = message.getSequenceNumber();
		    BODY b = p.peekBody(seqnum, sectionId + ".MIME");

		    if (b == null)
			throw new MessagingException("Failed to fetch headers");

		    ByteArrayInputStream bis = b.getByteArrayInputStream();
		    if (bis == null)
			throw new MessagingException("Failed to fetch headers");

		    headers.load(bis);

		} else {

		    // RFC 1730 does not provide for fetching BodyPart headers
		    // So, just dump the RFC1730 BODYSTRUCTURE into the
		    // headerStore
		    
		    // Content-Type
		    headers.addHeader("Content-Type", type);
		    // Content-Transfer-Encoding
		    headers.addHeader("Content-Transfer-Encoding", bs.encoding);
		    // Content-Description
		    if (bs.description != null)
			headers.addHeader("Content-Description",
							    bs.description);
		    // Content-ID
		    if (bs.id != null)
			headers.addHeader("Content-ID", bs.id);
		    // Content-MD5
		    if (bs.md5 != null)
			headers.addHeader("Content-MD5", bs.md5);
		}
	    } catch (ConnectionException cex) {
		throw new FolderClosedException(
			    message.getFolder(), cex.getMessage());
	    } catch (ProtocolException pex) {
		throw new MessagingException(pex.getMessage(), pex);
	    }
	}
	headersLoaded = true;
    }
}
