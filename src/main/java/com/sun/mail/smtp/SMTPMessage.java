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

package com.sun1.mail.smtp;

import java.io.*;
import javax1.mail.*;
import javax1.mail.internet.*;

/**
 * This class is a specialization of the MimeMessage class that allows
 * you to specify various SMTP options and parameters that will be
 * used when this message is sent over SMTP.  Simply use this class
 * instead of MimeMessage and set SMTP options using the methods on
 * this class. <p>
 *
 * See the <a href="package-summary.html">com.sun1.mail.smtp</a> package
 * documentation for further information on the SMTP protocol provider. <p>
 *
 * @author Bill Shannon
 * @see	javax1.mail.internet.MimeMessage
 */

public class SMTPMessage extends MimeMessage {

    /** Never notify of delivery status */
    public static final int NOTIFY_NEVER = -1;
    /** Notify of delivery success */
    public static final int NOTIFY_SUCCESS = 1;
    /** Notify of delivery failure */
    public static final int NOTIFY_FAILURE = 2;
    /** Notify of delivery delay */
    public static final int NOTIFY_DELAY = 4;

    /** Return full message with delivery status notification */
    public static final int RETURN_FULL = 1;
    /** Return only message headers with delivery status notification */
    public static final int RETURN_HDRS = 2;

    private static final String[] returnOptionString = { null, "FULL", "HDRS" };

    private String envelopeFrom; // the string to use in the MAIL FROM: command
    private int notifyOptions = 0;
    private int returnOption = 0;
    private boolean sendPartial = false;
    private boolean allow8bitMIME = false;
    private String submitter = null;	// RFC 2554 AUTH=submitter
    private String extension = null;	// extensions to use with MAIL command

    /**
     * Default constructor. An empty message object is created.
     * The <code>headers</code> field is set to an empty InternetHeaders
     * object. The <code>flags</code> field is set to an empty Flags
     * object. The <code>modified</code> flag is set to true.
     */
    public SMTPMessage(Session session) {
	super(session);
    }

    /**
     * Constructs an SMTPMessage by reading and parsing the data from the
     * specified MIME InputStream. The InputStream will be left positioned
     * at the end of the data for the message. Note that the input stream
     * parse is done within this constructor itself.
     *
     * @param session	Session object for this message
     * @param is	the message input stream
     * @exception	MessagingException
     */
    public SMTPMessage(Session session, InputStream is) 
			throws MessagingException {
	super(session, is);
    }

    /**
     * Constructs a new SMTPMessage with content initialized from the
     * <code>source</code> MimeMessage.  The new message is independent
     * of the original. <p>
     *
     * Note: The current implementation is rather inefficient, copying
     * the data more times than strictly necessary.
     *
     * @param	source	the message to copy content from
     * @exception	MessagingException
     */
    public SMTPMessage(MimeMessage source) throws MessagingException {
	super(source);
    }

    /**
     * Set the From address to appear in the SMTP envelope.  Note that this
     * is different than the From address that appears in the message itself.
     * The envelope From address is typically used when reporting errors.
     * See <A HREF="http://www.ietf.org/rfc/rfc821.txt">RFC 821</A> for
     * details. <p>
     *
     * If set, overrides the <code>mail.smtp.from</code> property.
     *
     * @param	from	the envelope From address
     */
    public void setEnvelopeFrom(String from) {
	envelopeFrom = from;
    }

    /**
     * Return the envelope From address.
     *
     * @return	the envelope From address, or null if not set
     */
    public String getEnvelopeFrom() {
	return envelopeFrom;
    }

    /**
     * Set notification options to be used if the server supports
     * Delivery Status Notification
     * (<A HREF="http://www.ietf.org/rfc/rfc1891.txt">RFC 1891</A>).
     * Either <code>NOTIFY_NEVER</code> or some combination of
     * <code>NOTIFY_SUCCESS</code>, <code>NOTIFY_FAILURE</code>, and
     * <code>NOTIFY_DELAY</code>. <p>
     *
     * If set, overrides the <code>mail.smtp.dsn.notify</code> property.
     *
     * @param	options	notification options
     */
    public void setNotifyOptions(int options) {
	if (options < -1 || options >= 8)
	    throw new IllegalArgumentException("Bad return option");
	notifyOptions = options;
    }

    /**
     * Get notification options.  Returns zero if no options set.
     *
     * @return	notification options
     */
    public int getNotifyOptions() {
	return notifyOptions;
    }

    /**
     * Return notification options as an RFC 1891 string.
     * Returns null if no options set.
     */
    String getDSNNotify() {
	if (notifyOptions == 0)
	    return null;
	if (notifyOptions == NOTIFY_NEVER)
	    return "NEVER";
	StringBuffer sb = new StringBuffer();
	if ((notifyOptions & NOTIFY_SUCCESS) != 0)
	    sb.append("SUCCESS");
	if ((notifyOptions & NOTIFY_FAILURE) != 0) {
	    if (sb.length() != 0)
		sb.append(',');
	    sb.append("FAILURE");
	}
	if ((notifyOptions & NOTIFY_DELAY) != 0) {
	    if (sb.length() != 0)
		sb.append(',');
	    sb.append("DELAY");
	}
	return sb.toString();
    }

    /**
     * Set return option to be used if server supports
     * Delivery Status Notification
     * (<A HREF="http://www.ietf.org/rfc/rfc1891.txt">RFC 1891</A>).
     * Either <code>RETURN_FULL</code> or <code>RETURN_HDRS</code>. <p>
     *
     * If set, overrides the <code>mail.smtp.dsn.ret</code> property.
     *
     * @param	option	return option
     */
    public void setReturnOption(int option) {
	if (option < 0 || option > RETURN_HDRS)
	    throw new IllegalArgumentException("Bad return option");
	returnOption = option;
    }

    /**
     * Return return option.  Returns zero if no option set.
     *
     * @return	return option
     */
    public int getReturnOption() {
	return returnOption;
    }

    /**
     * Return return option as an RFC 1891 string.
     * Returns null if no option set.
     */
    String getDSNRet() {
	return returnOptionString[returnOption];
    }

    /**
     * If set to true, and the server supports the 8BITMIME extension, text
     * parts of this message that use the "quoted-printable" or "base64"
     * encodings are converted to use "8bit" encoding if they follow the
     * RFC 2045 rules for 8bit text. <p>
     *
     * If true, overrides the <code>mail.smtp.allow8bitmime</code> property.
     *
     * @param	allow	allow 8-bit flag
     */
    public void setAllow8bitMIME(boolean allow) {
	allow8bitMIME = allow;
    }

    /**
     * Is use of the 8BITMIME extension is allowed?
     *
     * @return	allow 8-bit flag
     */
    public boolean getAllow8bitMIME() {
	return allow8bitMIME;
    }

    /**
     * If set to true, and this message has some valid and some invalid
     * addresses, send the message anyway, reporting the partial failure with
     * a SendFailedException.  If set to false (the default), the message is
     * not sent to any of the recipients if there is an invalid recipient
     * address. <p>
     *
     * If true, overrides the <code>mail.smtp.sendpartial</code> property.
     *
     * @param partial	send partial flag
     */
    public void setSendPartial(boolean partial) {
	sendPartial = partial;
    }

    /**
     * Send message if some addresses are invalid?
     *
     * @return	send partial flag
     */
    public boolean getSendPartial() {
	return sendPartial;
    }

    /**
     * Gets the submitter to be used for the RFC 2554 AUTH= value
     * in the MAIL FROM command.
     *
     * @return	the name of the submitter.
     */
    public String getSubmitter() {
	return submitter;
    }

    /**
     * Sets the submitter to be used for the RFC 2554 AUTH= value
     * in the MAIL FROM command.  Normally only used by a server
     * that's relaying a message.  Clients will typically not
     * set a submitter.  See
     * <A HREF="http://www.ietf.org/rfc/rfc2554.txt">RFC 2554</A>
     * for details.
     *
     * @param	submitter	the name of the submitter
     */
    public void setSubmitter(String submitter) {
	this.submitter = submitter;
    }

    /**
     * Gets the extension string to use with the MAIL command.
     *
     * @return	the extension string
     *
     * @since	JavaMail 1.3.2
     */
    public String getMailExtension() {
	return extension;
    }

    /**
     * Set the extension string to use with the MAIL command.
     * The extension string can be used to specify standard SMTP
     * service extensions as well as vendor-specific extensions.
     * Typically the application should use the
     * {@link com.sun1.mail.smtp.SMTPTransport SMTPTransport}
     * method {@link com.sun1.mail.smtp.SMTPTransport#supportsExtension
     * supportsExtension}
     * to verify that the server supports the desired service extension.
     * See <A HREF="http://www.ietf.org/rfc/rfc1869.txt">RFC 1869</A>
     * and other RFCs that define specific extensions. <p>
     *
     * For example: <p>
     *
     * <blockquote><pre>
     * if (smtpTransport.supportsExtension("DELIVERBY"))
     *    smtpMsg.setMailExtension("BY=60;R");
     * </pre></blockquote>
     *
     * @since	JavaMail 1.3.2
     */
    public void setMailExtension(String extension) {
	this.extension = extension;
    }
}
