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

package com.sun1.mail.imap;

import java.util.Date;
import java.util.Vector;
import java.util.Hashtable;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.logging.Level;
import java.io.*;

import javax1.mail.*;
import javax1.mail.event.*;
import javax1.mail.internet.*;
import javax1.mail.search.*;

import com.sun1.mail.util.*;
import com.sun1.mail.iap.*;
import com.sun1.mail.imap.protocol.*;

/**
 * This class implements an IMAP folder. <p>
 *
 * A closed IMAPFolder object shares a protocol connection with its IMAPStore
 * object. When the folder is opened, it gets its own protocol connection. <p>
 *
 * Applications that need to make use of IMAP-specific features may cast
 * a <code>Folder</code> object to an <code>IMAPFolder</code> object and
 * use the methods on this class. <p>
 *
 * The {@link #getQuota getQuota} and
 * {@link #setQuota setQuota} methods support the IMAP QUOTA extension.
 * Refer to <A HREF="http://www.ietf.org/rfc/rfc2087.txt">RFC 2087</A>
 * for more information. <p>
 *
 * The {@link #getACL getACL}, {@link #addACL addACL},
 * {@link #removeACL removeACL}, {@link #addRights addRights},
 * {@link #removeRights removeRights}, {@link #listRights listRights}, and
 * {@link #myRights myRights} methods support the IMAP ACL extension.
 * Refer to <A HREF="http://www.ietf.org/rfc/rfc2086.txt">RFC 2086</A>
 * for more information. <p>
 *
 * The {@link #getSortedMessages getSortedMessages}
 * methods support the IMAP SORT extension.
 * Refer to <A HREF="http://www.ietf.org/rfc/rfc5256.txt">RFC 5256</A>
 * for more information. <p>
 *
 * The {@link #open(int,com.sun1.mail.imap.ResyncData) open(int,ResyncData)}
 * method and {@link com.sun1.mail.imap.ResyncData ResyncData} class supports
 * the IMAP CONDSTORE and QRESYNC extensions.
 * Refer to <A HREF="http://www.ietf.org/rfc/rfc4551.txt">RFC 4551</A>
 * and <A HREF="http://www.ietf.org/rfc/rfc5162.txt">RFC 5162</A>
 * for more information. <p>
 *
 * The {@link #doCommand doCommand} method and
 * {@link IMAPFolder.ProtocolCommand IMAPFolder.ProtocolCommand}
 * interface support use of arbitrary IMAP protocol commands. <p>
 *
 * See the <a href="package-summary.html">com.sun1.mail.imap</a> package
 * documentation for further information on the IMAP protocol provider. <p>
 *
 * <strong>WARNING:</strong> The APIs unique to this class should be
 * considered <strong>EXPERIMENTAL</strong>.  They may be changed in the
 * future in ways that are incompatible with applications using the
 * current APIs.
 *
 * @author  John Mani
 * @author  Bill Shannon
 * @author  Jim Glennon
 */

/*
 * The folder object itself serves as a lock for the folder's state
 * EXCEPT for the message cache (see below), typically by using
 * synchronized methods.  When checking that a folder is open or
 * closed, the folder's lock must be held.  It's important that the
 * folder's lock is acquired before the messageCacheLock (see below).
 * Thus, the locking hierarchy is that the folder lock, while optional,
 * must be acquired before the messageCacheLock, if it's acquired at
 * all.  Be especially careful of callbacks that occur while holding
 * the messageCacheLock into (e.g.) superclass Folder methods that are
 * synchronized.  Note that methods in IMAPMessage will acquire the
 * messageCacheLock without acquiring the folder lock. <p>
 *
 * When a folder is opened, it creates a messageCache (a Vector) of 
 * empty IMAPMessage objects. Each Message has a messageNumber - which
 * is its index into the messageCache, and a sequenceNumber - which is
 * its IMAP sequence-number. All operations on a Message which involve
 * communication with the server, use the message's sequenceNumber. <p>
 *
 * The most important thing to note here is that the server can send
 * unsolicited EXPUNGE notifications as part of the responses for "most"
 * commands. Refer RFC 3501, sections 5.3 & 5.5 for gory details. Also, 
 * the server sends these  notifications AFTER the message has been 
 * expunged. And once a message is expunged, the sequence-numbers of 
 * those messages after the expunged one are renumbered. This essentially
 * means that the mapping between *any* Message and its sequence-number 
 * can change in the period when a IMAP command is issued and its responses
 * are processed. Hence we impose a strict locking model as follows: <p>
 *
 * We define one mutex per folder - this is just a Java Object (named 
 * messageCacheLock). Any time a command is to be issued to the IMAP
 * server (i.e., anytime the corresponding IMAPProtocol method is
 * invoked), follow the below style:
 *		
 *	synchronized (messageCacheLock) { // ACQUIRE LOCK
 *	    issue command ()
 *	    
 *	    // The response processing is typically done within
 *	    // the handleResponse() callback. A few commands (Fetch,
 *	    // Expunge) return *all* responses and hence their
 *	    // processing is done here itself. Now, as part of the
 *	    // processing unsolicited EXPUNGE responses, we renumber
 *	    // the necessary sequence-numbers. Thus the renumbering
 *	    // happens within this critical-region, surrounded by
 *	    // locks.
 *	    process responses ()
 *	} // RELEASE LOCK
 *
 * This technique is used both by methods in IMAPFolder and by methods
 * in IMAPMessage and other classes that operate on data in the folder.
 * Note that holding the messageCacheLock has the side effect of
 * preventing the folder from being closed, and thus ensuring that the
 * folder's protocol object is still valid.  The protocol object should
 * only be accessed while holding the messageCacheLock (except for calls
 * to IMAPProtocol.isREV1(), which don't need to be protected because it
 * doesn't access the server).
 *	    
 * Note that interactions with the Store's protocol connection do
 * not have to be protected as above, since the Store's protocol is
 * never in a "meaningful" SELECT-ed state.
 */

public class IMAPFolder extends Folder implements UIDFolder, ResponseHandler {
    
    protected String fullName;		// full name
    protected String name;		// name
    protected int type;			// folder type. 
    protected char separator;		// separator
    protected Flags availableFlags; 	// available flags
    protected Flags permanentFlags; 	// permanent flags
    protected volatile boolean exists;	// whether this folder really exists ?
    protected boolean isNamespace = false; // folder is a namespace name
    protected volatile String[] attributes;// name attributes from LIST response

    protected volatile IMAPProtocol protocol; // this folder's protocol object
    protected MessageCache messageCache;// message cache
    // accessor lock for message cache
    protected final Object messageCacheLock = new Object();

    protected Hashtable uidTable;	// UID->Message hashtable

    /* An IMAP delimiter is a 7bit US-ASCII character. (except NUL).
     * We use '\uffff' (a non 7bit character) to indicate that we havent
     * yet determined what the separator character is.
     * We use '\u0000' (NUL) to indicate that no separator character
     * exists, i.e., a flat hierarchy
     */
    static final protected char UNKNOWN_SEPARATOR = '\uffff';

    private volatile boolean opened = false; 	// is this folder opened ?

    /* This field tracks the state of this folder. If the folder is closed
     * due to external causes (i.e, not thru the close() method), then
     * this field will remain false. If the folder is closed thru the
     * close() method, then this field is set to true.
     *
     * If reallyClosed is false, then a FolderClosedException is
     * generated when a method is invoked on any Messaging object
     * owned by this folder. If reallyClosed is true, then the
     * IllegalStateException runtime exception is thrown.
     */
    private boolean reallyClosed = true;

    /*
     * The idleState field supports the IDLE command.
     * Normally when executing an IMAP command we hold the
     * messageCacheLock and often the folder lock (see above).
     * While executing the IDLE command we can't hold either
     * of these locks or it would prevent other threads from
     * entering Folder methods even far enough to check whether
     * an IDLE command is in progress.  We need to check before
     * issuing another command so that we can abort the IDLE
     * command.
     *
     * The idleState field is protected by the messageCacheLock.
     * The RUNNING state is the normal state and means no IDLE
     * command is in progress.  The IDLE state means we've issued
     * an IDLE command and are reading responses.  The ABORTING
     * state means we've sent the DONE continuation command and
     * are waiting for the thread running the IDLE command to
     * break out of its read loop.
     *
     * When an IDLE command is in progress, the thread calling
     * the idle method will be reading from the IMAP connection
     * while holding neither the folder lock nor the messageCacheLock.
     * It's obviously critical that no other thread try to send a
     * command or read from the connection while in this state.
     * However, other threads can send the DONE continuation
     * command that will cause the server to break out of the IDLE
     * loop and send the ending tag response to the IDLE command.
     * The thread in the idle method that's reading the responses
     * from the IDLE command will see this ending response and
     * complete the idle method, setting the idleState field back
     * to RUNNING, and notifying any threads waiting to use the
     * connection.
     *
     * All uses of the IMAP connection (IMAPProtocol object) must
     * be done while holding the messageCacheLock and must be
     * preceeded by a check to make sure an IDLE command is not
     * running, and abort the IDLE command if necessary.  While
     * waiting for the IDLE command to complete, these other threads
     * will give up the messageCacheLock, but might still be holding
     * the folder lock.  This check is done by the getProtocol()
     * method, resulting in a typical usage pattern of:
     *
     *	    synchronized (messageCacheLock) {
     *		IMAPProtocol p = getProtocol();	// may block waiting for IDLE
     *		// ... use protocol
     *	    }
     */
    private static final int RUNNING = 0;	// not doing IDLE command
    private static final int IDLE = 1;		// IDLE command in effect
    private static final int ABORTING = 2;	// IDLE command aborting
    private int idleState = RUNNING;

    private volatile int total = -1;	// total number of messages in the
					// message cache
    private volatile int recent = -1;	// number of recent messages
    private int realTotal = -1;		// total number of messages on
    					// the server
    private long uidvalidity = -1;	// UIDValidity
    private long uidnext = -1;		// UIDNext
    private volatile long highestmodseq = -1;	// RFC 4551 - CONDSTORE
    private boolean doExpungeNotification = true; // used in expunge handler

    private Status cachedStatus = null;
    private long cachedStatusTime = 0;

    private boolean hasMessageCountListener = false;	// optimize notification

    protected MailLogger logger;
    private MailLogger connectionPoolLogger;

    /**
     * A fetch profile item for fetching headers.
     * This inner class extends the <code>FetchProfile.Item</code>
     * class to add new FetchProfile item types, specific to IMAPFolders.
     *
     * @see FetchProfile
     */
    public static class FetchProfileItem extends FetchProfile.Item {
	protected FetchProfileItem(String name) {
	    super(name);
	}

	/**
	 * HEADERS is a fetch profile item that can be included in a
	 * <code>FetchProfile</code> during a fetch request to a Folder.
	 * This item indicates that the headers for messages in the specified 
	 * range are desired to be prefetched. <p>
	 * 
	 * An example of how a client uses this is below: <p>
	 * <blockquote><pre>
	 *
	 * 	FetchProfile fp = new FetchProfile();
	 *	fp.add(IMAPFolder.FetchProfileItem.HEADERS);
	 *	folder.fetch(msgs, fp);
	 *
	 * </pre></blockquote><p>
	 */ 
	public static final FetchProfileItem HEADERS = 
		new FetchProfileItem("HEADERS");

	/**
	 * SIZE is a fetch profile item that can be included in a
	 * <code>FetchProfile</code> during a fetch request to a Folder.
	 * This item indicates that the sizes of the messages in the specified 
	 * range are desired to be prefetched. <p>
	 *
	 * SIZE was moved to FetchProfile.Item in JavaMail 1.5.
	 *
	 * @deprecated
	 */
	public static final FetchProfileItem SIZE = 
		new FetchProfileItem("SIZE");
    }

    /**
     * Constructor used to create a possibly non-existent folder.
     *
     * @param fullName	fullname of this folder
     * @param separator the default separator character for this 
     *			folder's namespace
     * @param store	the Store
     */
    protected IMAPFolder(String fullName, char separator, IMAPStore store,
				Boolean isNamespace) {
	super(store);
	if (fullName == null)
	    throw new NullPointerException("Folder name is null");
	this.fullName = fullName;
	this.separator = separator;
	logger = new MailLogger(this.getClass(),
				"DEBUG IMAP", store.getSession());
	connectionPoolLogger = ((IMAPStore)store).getConnectionPoolLogger();

	/*
	 * Work around apparent bug in Exchange.  Exchange
	 * will return a name of "Public Folders/" from
	 * LIST "%".
	 *
	 * If name has one separator, and it's at the end,
	 * assume this is a namespace name and treat it
	 * accordingly.  Usually this will happen as a result
	 * of the list method, but this also allows getFolder
	 * to work with namespace names.
	 */
	this.isNamespace = false;
	if (separator != UNKNOWN_SEPARATOR && separator != '\0') {
	    int i = this.fullName.indexOf(separator);
	    if (i > 0 && i == this.fullName.length() - 1) {
		this.fullName = this.fullName.substring(0, i);
		this.isNamespace = true;
	    }
	}

	// if we were given a value, override default chosen above
	if (isNamespace != null)
	    this.isNamespace = isNamespace.booleanValue();
    }

    /**
     * Constructor used to create an existing folder.
     */
    protected IMAPFolder(ListInfo li, IMAPStore store) {
	this(li.name, li.separator, store, null);

	if (li.hasInferiors)
	    type |= HOLDS_FOLDERS;
	if (li.canOpen)
	    type |= HOLDS_MESSAGES;
	exists = true;
	attributes = li.attrs;
    }
	
    /*
     * Ensure that this folder exists. If 'exists' has been set to true,
     * we don't attempt to validate it with the server again. Note that
     * this can result in a possible loss of sync with the server.
     * ASSERT: Must be called with this folder's synchronization lock held.
     */
    protected void checkExists() throws MessagingException {
	// If the boolean field 'exists' is false, check with the
	// server by invoking exists() ..
	if (!exists && !exists())
	    throw new FolderNotFoundException(
		this, fullName + " not found");
    }

    /*
     * Ensure the folder is closed.
     * ASSERT: Must be called with this folder's synchronization lock held.
     */
    protected void checkClosed() {
	if (opened)
	    throw new IllegalStateException(
		"This operation is not allowed on an open folder"
		);
    }

    /*
     * Ensure the folder is open.
     * ASSERT: Must be called with this folder's synchronization lock held.
     */
    protected void checkOpened() throws FolderClosedException {
	assert Thread.holdsLock(this);
	if (!opened) {
	    if (reallyClosed)
		throw new IllegalStateException(
		    "This operation is not allowed on a closed folder"
	    	);
	    else // Folder was closed "implicitly"
		throw new FolderClosedException(this, 
		    "Lost folder connection to server"
		);
	}
    }

    /*
     * Check that the given message number is within the range
     * of messages present in this folder. If the message
     * number is out of range, we ping the server to obtain any
     * pending new message notifications from the server.
     */
    protected void checkRange(int msgno) throws MessagingException {
	if (msgno < 1) // message-numbers start at 1
	    throw new IndexOutOfBoundsException("message number < 1");

	if (msgno <= total)
	    return;

	// Out of range, let's ping the server and see if
	// the server has more messages for us.

	synchronized(messageCacheLock) { // Acquire lock
	    try {
		keepConnectionAlive(false);
	    } catch (ConnectionException cex) {
		// Oops, lost connection
		throw new FolderClosedException(this, cex.getMessage());
	    } catch (ProtocolException pex) { 
		throw new MessagingException(pex.getMessage(), pex);
	    }
	} // Release lock

	if (msgno > total) // Still out of range ? Throw up ...
	    throw new IndexOutOfBoundsException(msgno + " > " + total);
    }

    /*
     * Check whether the given flags are supported by this server,
     * and also verify that the folder allows setting flags.
     */
    private void checkFlags(Flags flags) throws MessagingException {
	assert Thread.holdsLock(this);
	if (mode != READ_WRITE)
	    throw new IllegalStateException(
		"Cannot change flags on READ_ONLY folder: " + fullName
		);
	/*
	if (!availableFlags.contains(flags))
	    throw new MessagingException(
		"These flags are not supported by this implementation"
		);
	*/
    }

    /**
     * Get the name of this folder.
     */
    public synchronized String getName() {
	/* Return the last component of this Folder's full name.
	 * Folder components are delimited by the separator character.
	 */
	if (name == null) {
	    try {
		name = 	fullName.substring(
			    fullName.lastIndexOf(getSeparator()) + 1
			);
	    } catch (MessagingException mex) { }
	}
	return name;
    }

    /**
     * Get the fullname of this folder.
     */
    public synchronized String getFullName() {
	return fullName;	
    }

    /**
     * Get this folder's parent.
     */
    public synchronized Folder getParent() throws MessagingException {
	char c = getSeparator();
	int index;
	if ((index = fullName.lastIndexOf(c)) != -1)
	    return ((IMAPStore)store).newIMAPFolder(
			    fullName.substring(0, index), c);
	else
	    return new DefaultFolder((IMAPStore)store);
    }

    /**
     * Check whether this folder really exists on the server.
     */
    public synchronized boolean exists() throws MessagingException {
	// Check whether this folder exists ..
	ListInfo[] li = null;
	final String lname;
	if (isNamespace && separator != '\0')
	    lname = fullName + separator;
	else
	    lname = fullName;

	li = (ListInfo[])doCommand(new ProtocolCommand() {
	    public Object doCommand(IMAPProtocol p) throws ProtocolException {
		return p.list("", lname);
	    }
	});

	if (li != null) {
	    int i = findName(li, lname);
	    fullName = li[i].name;
	    separator = li[i].separator;
	    int len = fullName.length();
	    if (separator != '\0' && len > 0 &&
		    fullName.charAt(len - 1) == separator) {
		fullName = fullName.substring(0, len - 1);
	    }
	    type = 0;
	    if (li[i].hasInferiors)
		type |= HOLDS_FOLDERS;
	    if (li[i].canOpen)
		type |= HOLDS_MESSAGES;
	    exists = true;
	    attributes = li[i].attrs;
	} else {
	    exists = opened;
	    attributes = null;
	}

	return exists;
    }

    /**
     * Which entry in <code>li</code> matches <code>lname</code>?
     * If the name contains wildcards, more than one entry may be
     * returned.
     */
    private int findName(ListInfo[] li, String lname) {
	int i;
	// if the name contains a wildcard, there might be more than one
	for (i = 0; i < li.length; i++) {
	    if (li[i].name.equals(lname))
		break;
	}
	if (i >= li.length) {	// nothing matched exactly
	    // XXX - possibly should fail?  But what if server
	    // is case insensitive and returns the preferred
	    // case of the name here?
	    i = 0;		// use first one
	}
	return i;
    }

    /**
     * List all subfolders matching the specified pattern.
     */
    public Folder[] list(String pattern) throws MessagingException {
	return doList(pattern, false);
    }

    /**
     * List all subscribed subfolders matching the specified pattern.
     */
    public Folder[] listSubscribed(String pattern) throws MessagingException {
	return doList(pattern, true);
    }

    private synchronized Folder[] doList(final String pattern,
		final boolean subscribed) throws MessagingException {
	checkExists(); // insure that this folder does exist.
	
	// Why waste a roundtrip to the server?
	if (attributes != null && !isDirectory())
	    return new Folder[0];

	final char c = getSeparator();

	ListInfo[] li = (ListInfo[])doCommandIgnoreFailure(
	    new ProtocolCommand() {
		public Object doCommand(IMAPProtocol p)
			throws ProtocolException {
		    if (subscribed)
			return p.lsub("", fullName + c + pattern);
		    else 
			return p.list("", fullName + c + pattern);
		}
	    });

	if (li == null)
	    return new Folder[0];

	/*
	 * The UW based IMAP4 servers (e.g. SIMS2.0) include
	 * current folder (terminated with the separator), when
	 * the LIST pattern is '%' or '*'. i.e, <LIST "" mail/%> 
	 * returns "mail/" as the first LIST response.
	 *
	 * Doesn't make sense to include the current folder in this
	 * case, so we filter it out. Note that I'm assuming that
	 * the offending response is the *first* one, my experiments
	 * with the UW & SIMS2.0 servers indicate that .. 
	 */
	int start = 0;
	// Check the first LIST response.
	if (li.length > 0 && li[0].name.equals(fullName + c)) 
	    start = 1; // start from index = 1

	IMAPFolder[] folders = new IMAPFolder[li.length - start];
	IMAPStore st = (IMAPStore)store;
	for (int i = start; i < li.length; i++)
	    folders[i-start] = st.newIMAPFolder(li[i]);
	return folders;
    }

    /**
     * Get the separator character.
     */
    public synchronized char getSeparator() throws MessagingException {
	if (separator == UNKNOWN_SEPARATOR) {
	    ListInfo[] li = null;

	    li = (ListInfo[])doCommand(new ProtocolCommand() {
		public Object doCommand(IMAPProtocol p)
			throws ProtocolException {
		    // REV1 allows the following LIST format to obtain
		    // the hierarchy delimiter of non-existent folders
		    if (p.isREV1()) // IMAP4rev1
		        return p.list(fullName, "");
		    else // IMAP4, note that this folder must exist for this
		        // to work :(
		        return p.list("", fullName);
		}
	    });

	    if (li != null) 
		separator = li[0].separator;
	    else
		separator = '/'; // punt !
	}
	return separator;
    }

    /**
     * Get the type of this folder.
     */
    public synchronized int getType() throws MessagingException {
	if (opened) {
	    // never throw FolderNotFoundException if folder is open
	    if (attributes == null)
		exists();	// try to fetch attributes
	} else {
	    checkExists();
	}
	return type;
    }
    
    /**
     * Check whether this folder is subscribed. <p>
     */
    public synchronized boolean isSubscribed() {
	ListInfo[] li = null;
	final String lname;
	if (isNamespace && separator != '\0')
	    lname = fullName + separator;
	else
	    lname = fullName;

	try {
	    li = (ListInfo[])doProtocolCommand(new ProtocolCommand() {
		public Object doCommand(IMAPProtocol p)
			throws ProtocolException {
		    return p.lsub("", lname);
		}
	    });
	} catch (ProtocolException pex) {
        }

	if (li != null) {
	    int i = findName(li, lname);
	    return li[i].canOpen;
	} else
	    return false;
    }

    /**
     * Subscribe/Unsubscribe this folder.
     */
    public synchronized void setSubscribed(final boolean subscribe) 
			throws MessagingException {
	doCommandIgnoreFailure(new ProtocolCommand() {
	    public Object doCommand(IMAPProtocol p) throws ProtocolException {
		if (subscribe)
		    p.subscribe(fullName);
		else
		    p.unsubscribe(fullName);
		return null;
	    }
	});
    }
	
    /**
     * Create this folder, with the specified type.
     */
    public synchronized boolean create(final int type)
				throws MessagingException {

	char c = 0;
	if ((type & HOLDS_MESSAGES) == 0)	// only holds folders
	    c = getSeparator();
	final char sep = c;
	Object ret = doCommandIgnoreFailure(new ProtocolCommand() {
		public Object doCommand(IMAPProtocol p)
			throws ProtocolException {
		    if ((type & HOLDS_MESSAGES) == 0)	// only holds folders
			p.create(fullName + sep);
		    else {
			p.create(fullName);

			// Certain IMAP servers do not allow creation of folders
			// that can contain messages *and* subfolders. So, if we
			// were asked to create such a folder, we should verify
			// that we could indeed do so.
			if ((type & HOLDS_FOLDERS) != 0) {
			    // we want to hold subfolders and messages. Check
			    // whether we could create such a folder.
			    ListInfo[] li = p.list("", fullName);
			    if (li != null && !li[0].hasInferiors) {
				// Hmm ..the new folder 
				// doesn't support Inferiors ? Fail
				p.delete(fullName);
				throw new ProtocolException("Unsupported type");
			    }
			}
		    }
		    return Boolean.TRUE;
		}
	    });

	if (ret == null)
	    return false; // CREATE failure, maybe this 
			  // folder already exists ?

	// exists = true;
	// this.type = type;
	boolean retb = exists();	// set exists, type, and attributes
	if (retb)		// Notify listeners on self and our Store
	    notifyFolderListeners(FolderEvent.CREATED);
	return retb;
    }

    /**
     * Check whether this folder has new messages.
     */
    public synchronized boolean hasNewMessages() throws MessagingException {
	if (opened) {	// If we are open, we already have this information
	    // Folder is open, make sure information is up to date
	    synchronized(messageCacheLock) {
		// tickle the folder and store connections.
		try {
		    keepConnectionAlive(true);
		} catch (ConnectionException cex) {
		    throw new FolderClosedException(this, cex.getMessage());
		} catch (ProtocolException pex) {
		    throw new MessagingException(pex.getMessage(), pex);
		}
		return recent > 0 ? true : false;
	    }
	}

	// First, the cheap way - use LIST and look for the \Marked
	// or \Unmarked tag

	ListInfo[] li = null;
	final String lname;
	if (isNamespace && separator != '\0')
	    lname = fullName + separator;
	else
	    lname = fullName;
	li = (ListInfo[])doCommandIgnoreFailure(new ProtocolCommand() {
	    public Object doCommand(IMAPProtocol p) throws ProtocolException {
		return p.list("", lname);
	    }
	});

	// if folder doesn't exist, throw exception
	if (li == null)
	    throw new FolderNotFoundException(this, fullName + " not found");

	int i = findName(li, lname);
	if (li[i].changeState == ListInfo.CHANGED)
	    return true;
	else if (li[i].changeState == ListInfo.UNCHANGED)
	    return false;

	// LIST didn't work. Try the hard way, using STATUS
	try {
	    Status status = getStatus();
	    if (status.recent > 0)
		return true;
	    else
		return false;
	} catch (BadCommandException bex) {
	    // Probably doesn't support STATUS, tough luck.
	    return false;
	} catch (ConnectionException cex) {
	    throw new StoreClosedException(store, cex.getMessage());
	} catch (ProtocolException pex) {
	    throw new MessagingException(pex.getMessage(), pex);
	}
    }

    /**
     * Get the named subfolder. <p>
     */
    public synchronized Folder getFolder(String name)
				throws MessagingException {
	// If we know that this folder is *not* a directory, don't
	// send the request to the server at all ...
	if (attributes != null && !isDirectory())
	    throw new MessagingException("Cannot contain subfolders");

	char c = getSeparator();
	return ((IMAPStore)store).newIMAPFolder(fullName + c + name, c);
    }

    /**
     * Delete this folder.
     */
    public synchronized boolean delete(boolean recurse) 
			throws MessagingException {  
	checkClosed(); // insure that this folder is closed.

	if (recurse) {
	    // Delete all subfolders.
	    Folder[] f = list();
	    for (int i = 0; i < f.length; i++)
		f[i].delete(recurse); // ignore intermediate failures
	}

	// Attempt to delete this folder

	Object ret = doCommandIgnoreFailure(new ProtocolCommand() {
	    public Object doCommand(IMAPProtocol p) throws ProtocolException {
		p.delete(fullName);
		return Boolean.TRUE;
	    }
	});

	if (ret == null)
	    // Non-existent folder/No permission ??
	    return false;

	// DELETE succeeded.
	exists = false;
	attributes = null;

	// Notify listeners on self and our Store
	notifyFolderListeners(FolderEvent.DELETED);
	return true;
    }

    /**
     * Rename this folder. <p>
     */
    public synchronized boolean renameTo(final Folder f)
				throws MessagingException {
	checkClosed(); // insure that we are closed.
	checkExists();
	if (f.getStore() != store)
	    throw new MessagingException("Can't rename across Stores");


	Object ret = doCommandIgnoreFailure(new ProtocolCommand() {
	    public Object doCommand(IMAPProtocol p) throws ProtocolException {
		p.rename(fullName, f.getFullName());
		return Boolean.TRUE;
	    }
	});

	if (ret == null)
	    return false;

	exists = false;
	attributes = null;
	notifyFolderRenamedListeners(f);
	return true;
    }

    /**
     * Open this folder in the given mode.
     */
    public synchronized void open(int mode) throws MessagingException {
	open(mode, null);
    }

    /**
     * Open this folder in the given mode, with the given
     * resynchronization data.
     *
     * @param	mode	the open mode (Folder.READ_WRITE or Folder.READ_ONLY)
     * @param	rd	the ResyncData instance
     * @return		a List of MailEvent instances, or null if none
     * @exception MessagingException	if the open fails
     * @since	JavaMail 1.5.1
     */
    public synchronized List<MailEvent> open(int mode, ResyncData rd)
				throws MessagingException {
	checkClosed(); // insure that we are not already open
	
	MailboxInfo mi = null;
	// Request store for our own protocol connection.
	protocol = ((IMAPStore)store).getProtocol(this);

	List<MailEvent> openEvents = null;
	synchronized(messageCacheLock) { // Acquire messageCacheLock

	    /*
	     * Add response handler right away so we get any alerts or
	     * notifications that occur during the SELECT or EXAMINE.
	     * Have to be sure to remove it if we fail to open the
	     * folder.
	     */
	    protocol.addResponseHandler(this);

	    try {
		/*
		 * Enable QRESYNC or CONDSTORE if needed and not enabled.
		 * QRESYNC implies CONDSTORE, but servers that support
		 * QRESYNC are not required to support just CONDSTORE
		 * per RFC 5162.
		 */
		if (rd != null) {
		    if (rd == ResyncData.CONDSTORE) {
			if (!protocol.isEnabled("CONDSTORE") &&
			    !protocol.isEnabled("QRESYNC")) {
			    if (protocol.hasCapability("CONDSTORE"))
				protocol.enable("CONDSTORE");
			    else
				protocol.enable("QRESYNC");
			}
		    } else {
			if (!protocol.isEnabled("QRESYNC"))
			    protocol.enable("QRESYNC");
		    }
		}

		if (mode == READ_ONLY)
		    mi = protocol.examine(fullName, rd);
		else
		    mi = protocol.select(fullName, rd);
	    } catch (CommandFailedException cex) {
		/*
		 * Handle SELECT or EXAMINE failure.
		 * Try to figure out why the operation failed so we can
		 * report a more reasonable exception.
		 *
		 * Will use our existing protocol object.
		 */
		try {
		    checkExists(); // throw exception if folder doesn't exist

		    if ((type & HOLDS_MESSAGES) == 0)
			throw new MessagingException(
			    "folder cannot contain messages");
		    throw new MessagingException(cex.getMessage(), cex);

		} finally {
		    // folder not open, don't keep this information
		    exists = false;
		    attributes = null;
		    type = 0;
		    // connection still good, return it
		    releaseProtocol(true);
		}
		// NOTREACHED
	    } catch (ProtocolException pex) {
		// got a BAD or a BYE; connection may be bad, close it
		try {
		    protocol.logout();
		} catch (ProtocolException pex2) {
		    // ignore
		} finally {
		    releaseProtocol(false);
		    throw new MessagingException(pex.getMessage(), pex);
		}
	    }

	    if (mi.mode != mode) {
		if (mode == READ_WRITE && mi.mode == READ_ONLY &&
			((IMAPStore)store).allowReadOnlySelect()) {
		    ;		// all ok, allow it
		} else {	// otherwise, it's an error
		    try {
			// close mailbox and return connection
			protocol.close();
			releaseProtocol(true);
		    } catch (ProtocolException pex) {
			// something went wrong, close connection
			try {
			    protocol.logout();
			} catch (ProtocolException pex2) {
			    // ignore
			} finally {
			    releaseProtocol(false);
			}
		    } finally {
			throw new ReadOnlyFolderException(this,
				      "Cannot open in desired mode");
		    }

		}
            }
	
	    // Initialize stuff.
	    opened = true;
	    reallyClosed = false;
	    this.mode = mi.mode;
	    availableFlags = mi.availableFlags;
	    permanentFlags = mi.permanentFlags;
	    total = realTotal = mi.total;
	    recent = mi.recent;
	    uidvalidity = mi.uidvalidity;
	    uidnext = mi.uidnext;
	    highestmodseq = mi.highestmodseq;

	    // Create the message cache of appropriate size
	    messageCache = new MessageCache(this, (IMAPStore)store, total);

	    // process saved responses and return corresponding events
	    if (mi.responses != null) {
		openEvents = new ArrayList<MailEvent>();
		for (IMAPResponse ir : mi.responses) {
		    if (ir.keyEquals("VANISHED")) {
			// "VANISHED" SP ["(EARLIER)"] SP known-uids
			String[] s = ir.readAtomStringList();
			// check that it really is "EARLIER"
			if (s == null || s.length != 1 ||
					    !s[0].equalsIgnoreCase("EARLIER"))
			    continue;	// it's not, what to do with it here?
			String uids = ir.readAtom();
			UIDSet[] uidset = UIDSet.parseUIDSets(uids);
			long[] luid = UIDSet.toArray(uidset, uidnext);
			if (luid != null && luid.length > 0)
			    openEvents.add(
				new MessageVanishedEvent(this, luid));
		    } else if (ir.keyEquals("FETCH")) {
			assert ir instanceof FetchResponse :
				"!ir instanceof FetchResponse";
			Message msg = processFetchResponse((FetchResponse)ir);
			if (msg != null)
			    openEvents.add(new MessageChangedEvent(this,
				    MessageChangedEvent.FLAGS_CHANGED, msg));
		    }
		}
	    }
	} // Release lock

	exists = true;		// if we opened it, it must exist
	attributes = null;	// but we don't yet know its attributes
	type = HOLDS_MESSAGES;	// lacking more info, we know at least this much

	// notify listeners
	notifyConnectionListeners(ConnectionEvent.OPENED);

	return openEvents;
    }

    /**
     * Prefetch attributes, based on the given FetchProfile.
     */
    public synchronized void fetch(Message[] msgs, FetchProfile fp)
			throws MessagingException {
	checkOpened();

	StringBuffer command = new StringBuffer();
	boolean first = true;
	boolean allHeaders = false;

	if (fp.contains(FetchProfile.Item.ENVELOPE)) {
	    command.append(getEnvelopeCommand());
	    first = false;
	}
	if (fp.contains(FetchProfile.Item.FLAGS)) {
	    command.append(first ? "FLAGS" : " FLAGS");
	    first = false;
	}
	if (fp.contains(FetchProfile.Item.CONTENT_INFO)) {
	    command.append(first ? "BODYSTRUCTURE" : " BODYSTRUCTURE");
	    first = false;
	}
	if (fp.contains(UIDFolder.FetchProfileItem.UID)) {
	    command.append(first ? "UID" : " UID");
	    first = false;
	}
	if (fp.contains(IMAPFolder.FetchProfileItem.HEADERS)) {
	    allHeaders = true;
	    if (protocol.isREV1())
		command.append(first ?
			    "BODY.PEEK[HEADER]" : " BODY.PEEK[HEADER]");
	    else
		command.append(first ? "RFC822.HEADER" : " RFC822.HEADER");
	    first = false;
	}
	if (fp.contains(FetchProfile.Item.SIZE) ||
		fp.contains(IMAPFolder.FetchProfileItem.SIZE)) {
	    command.append(first ? "RFC822.SIZE" : " RFC822.SIZE");
	    first = false;
	}

	// if we're not fetching all headers, fetch individual headers
	String[] hdrs = null;
	if (!allHeaders) {
	    hdrs = fp.getHeaderNames();
	    if (hdrs.length > 0) {
		if (!first)
		    command.append(" ");
		command.append(createHeaderCommand(hdrs));
	    }
	}

	/*
	 * Add any additional extension fetch items.
	 */
	FetchItem[] fitems = protocol.getFetchItems();
	for (int i = 0; i < fitems.length; i++) {
	    if (fp.contains(fitems[i].getFetchProfileItem())) {
		if (command.length() != 0)
		    command.append(" ");
		command.append(fitems[i].getName());
	    }
	}

	Utility.Condition condition =
	    new IMAPMessage.FetchProfileCondition(fp, fitems);

        // Acquire the Folder's MessageCacheLock.
        synchronized(messageCacheLock) {

	    // Apply the test, and get the sequence-number set for
	    // the messages that need to be prefetched.
	    MessageSet[] msgsets = Utility.toMessageSet(msgs, condition);

	    if (msgsets == null)
		// We already have what we need.
		return;

	    Response[] r = null;
	    Vector v = new Vector(); // to collect non-FETCH responses &
	    			     // unsolicited FETCH FLAG responses 
	    try {
		r = getProtocol().fetch(msgsets, command.toString());
	    } catch (ConnectionException cex) {
		throw new FolderClosedException(this, cex.getMessage());
	    } catch (CommandFailedException cfx) {
		// Ignore these, as per RFC 2180
	    } catch (ProtocolException pex) { 
		throw new MessagingException(pex.getMessage(), pex);
	    }

	    if (r == null)
		return;
	   
	    for (int i = 0; i < r.length; i++) {
		if (r[i] == null)
		    continue;
		if (!(r[i] instanceof FetchResponse)) {
		    v.addElement(r[i]); // Unsolicited Non-FETCH response
		    continue;
		}

		// Got a FetchResponse.
		FetchResponse f = (FetchResponse)r[i];
		// Get the corresponding message.
		IMAPMessage msg = getMessageBySeqNumber(f.getNumber());

		int count = f.getItemCount();
		boolean unsolicitedFlags = false;

		for (int j = 0; j < count; j++) {
		    Item item = f.getItem(j);
		    // Check for the FLAGS item
		    if (item instanceof Flags &&
			    (!fp.contains(FetchProfile.Item.FLAGS) ||
				msg == null)) {
			// Ok, Unsolicited FLAGS update.
			unsolicitedFlags = true;
		    } else if (msg != null)
			msg.handleFetchItem(item, hdrs, allHeaders);
		}
		if (msg != null)
		    msg.handleExtensionFetchItems(f.getExtensionItems());

		// If this response contains any unsolicited FLAGS
		// add it to the unsolicited response vector
		if (unsolicitedFlags)
		    v.addElement(f);
	    }

	    // Dispatch any unsolicited responses
	    int size = v.size();
	    if (size != 0) {
		Response[] responses = new Response[size];
		v.copyInto(responses);
		handleResponses(responses);
	    }

	} // Release messageCacheLock
    }

    /**
     * Return the IMAP FETCH items to request in order to load
     * all the "envelope" data.  Subclasses can override this
     * method to fetch more data when FetchProfile.Item.ENVELOPE
     * is requested.
     *
     * @since JavaMail 1.4.6
     */
    protected String getEnvelopeCommand() {
	return IMAPMessage.EnvelopeCmd;
    }

    /**
     * Create a new IMAPMessage object to represent the given message number.
     * Subclasses of IMAPFolder may override this method to create a
     * subclass of IMAPMessage.
     *
     * @since JavaMail 1.4.6
     */
    protected IMAPMessage newIMAPMessage(int msgnum) {
	return new IMAPMessage(this, msgnum);
    }

    /**
     * Create the appropriate IMAP FETCH command items to fetch the
     * requested headers.
     */
    private String createHeaderCommand(String[] hdrs) {
	StringBuffer sb;

	if (protocol.isREV1())
	    sb = new StringBuffer("BODY.PEEK[HEADER.FIELDS (");
	else
	    sb = new StringBuffer("RFC822.HEADER.LINES (");

	for (int i = 0; i < hdrs.length; i++) {
	    if (i > 0)
		sb.append(" ");
	    sb.append(hdrs[i]);
	}

	if (protocol.isREV1())
	    sb.append(")]");
	else
	    sb.append(")");
	
	return sb.toString();
    }

    /**
     * Set the specified flags for the given array of messages.
     */
    public synchronized void setFlags(Message[] msgs, Flags flag, boolean value)
			throws MessagingException {
	checkOpened();
	checkFlags(flag); // validate flags

	if (msgs.length == 0) // boundary condition
	    return;

	synchronized(messageCacheLock) {
	    try {
		IMAPProtocol p = getProtocol();
		MessageSet[] ms = Utility.toMessageSet(msgs, null);
		if (ms == null)
		    throw new MessageRemovedException(
					"Messages have been removed");
		p.storeFlags(ms, flag, value);
	    } catch (ConnectionException cex) {
		throw new FolderClosedException(this, cex.getMessage());
	    } catch (ProtocolException pex) {
		throw new MessagingException(pex.getMessage(), pex);
	    }
	}
    }

    /**
     * Set the specified flags for the given range of message numbers.
     */
    public synchronized void setFlags(int start, int end,
			Flags flag, boolean value) throws MessagingException {
	checkOpened();
	Message[] msgs = new Message[end - start + 1];
	int i = 0;
	for (int n = start; n <= end; n++)
	    msgs[i++] = getMessage(n);
	setFlags(msgs, flag, value);
    }

    /**
     * Set the specified flags for the given array of message numbers.
     */
    public synchronized void setFlags(int[] msgnums, Flags flag, boolean value)
			throws MessagingException {
	checkOpened();
	Message[] msgs = new Message[msgnums.length];
	for (int i = 0; i < msgnums.length; i++)
	    msgs[i] = getMessage(msgnums[i]);
	setFlags(msgs, flag, value);
    }

    /**
     * Close this folder.
     */
    public synchronized void close(boolean expunge) throws MessagingException {
	close(expunge, false);
    }

    /**
     * Close this folder without waiting for the server.
     */
    public synchronized void forceClose() throws MessagingException {
	close(false, true);
    }

    /*
     * Common close method.
     */
    private void close(boolean expunge, boolean force)
				throws MessagingException {
	assert Thread.holdsLock(this);
	synchronized(messageCacheLock) {
	    /*
	     * If we already know we're closed, this is illegal.
	     * Can't use checkOpened() because if we were forcibly
	     * closed asynchronously we just want to complete the
	     * closing here.
	     */
	    if (!opened && reallyClosed)
		throw new IllegalStateException(
		    "This operation is not allowed on a closed folder"
		);

	    reallyClosed = true; // Ok, lets reset

	    // Maybe this folder is already closed, or maybe another
	    // thread which had the messageCacheLock earlier, found
	    // that our server connection is dead and cleaned up
	    // everything ..
	    if (!opened)
		return;

	    try {
		waitIfIdle();
		if (force) {
		    logger.log(Level.FINE, "forcing folder {0} to close",
								    fullName);
		    if (protocol != null)
			protocol.disconnect();
                } else if (((IMAPStore)store).isConnectionPoolFull()) {
		    // If the connection pool is full, logout the connection
		    logger.fine(
			"pool is full, not adding an Authenticated connection");

		    // If the expunge flag is set, close the folder first.
		    if (expunge && protocol != null)
			protocol.close();

		    if (protocol != null)
			protocol.logout();
                } else {
		    // If the expunge flag is set or we're open read-only we
		    // can just close the folder, otherwise open it read-only
		    // before closing, or unselect it if supported.
                    if (!expunge && mode == READ_WRITE) {
                        try {
			    if (protocol != null &&
				    protocol.hasCapability("UNSELECT"))
				protocol.unselect();
			    else {
				if (protocol != null) {
				    protocol.examine(fullName);
				    if (protocol != null) // XXX - unnecessary?
					protocol.close();
				}
			    }
                        } catch (ProtocolException pex2) {
                            if (protocol != null)
				protocol.disconnect();
                        }
                    } else {
			if (protocol != null)
			    protocol.close();
		    }
                }
	    } catch (ProtocolException pex) {
		throw new MessagingException(pex.getMessage(), pex);
	    } finally {
		// cleanup if we haven't already
		if (opened)
		    cleanup(true);
	    }
	}
    }

    // NOTE: this method can currently be invoked from close() or
    // from handleResponses(). Both invocations are conditional,
    // based on the "opened" flag, so we are sure that multiple
    // Connection.CLOSED events are not generated. Also both
    // invocations are from within messageCacheLock-ed areas.
    private void cleanup(boolean returnToPool) {
	assert Thread.holdsLock(messageCacheLock);
        releaseProtocol(returnToPool);
	messageCache = null;
	uidTable = null;
	exists = false; // to force a recheck in exists().
	attributes = null;
        opened = false;
	idleState = RUNNING;	// just in case
	notifyConnectionListeners(ConnectionEvent.CLOSED);
    }

    /**
     * Check whether this connection is really open.
     */
    public synchronized boolean isOpen() {
	synchronized(messageCacheLock) {
	    // Probe the connection to make sure its really open.
	    if (opened) {
		try {
		    keepConnectionAlive(false);
		} catch (ProtocolException pex) { }
	    }
	}

	return opened;
    }

    /**
     * Return the permanent flags supported by the server.
     */
    public synchronized Flags getPermanentFlags() {
	if (permanentFlags == null)
	    return null;
	return (Flags)(permanentFlags.clone());
    }

    /**
     * Get the total message count.
     */
    public synchronized int getMessageCount() throws MessagingException {
	if (!opened) {
	    checkExists();
	    // If this folder is not yet open, we use STATUS to
	    // get the total message count
	    try {
		Status status = getStatus();
		return status.total;
	    } catch (BadCommandException bex) {
		// doesn't support STATUS, probably vanilla IMAP4 ..
		// lets try EXAMINE
                IMAPProtocol p = null;

	        try {
	            p = getStoreProtocol();	// XXX
		    MailboxInfo minfo = p.examine(fullName);
		    p.close();
		    return minfo.total;
		} catch (ProtocolException pex) {
		    // Give up.
		    throw new MessagingException(pex.getMessage(), pex);
		} finally {
                    releaseStoreProtocol(p);
                }
	    } catch (ConnectionException cex) {
                throw new StoreClosedException(store, cex.getMessage());
	    } catch (ProtocolException pex) {
		throw new MessagingException(pex.getMessage(), pex);
	    }
	}

	// Folder is open, we know what the total message count is ..
	synchronized(messageCacheLock) {
	    // tickle the folder and store connections.
	    try {
		keepConnectionAlive(true);
		return total;
	    } catch (ConnectionException cex) {
		throw new FolderClosedException(this, cex.getMessage());
	    } catch (ProtocolException pex) {
		throw new MessagingException(pex.getMessage(), pex);
	    }
	}
    }

    /**
     * Get the new message count.
     */
    public synchronized int getNewMessageCount()
			throws MessagingException {
	if (!opened) {
	    checkExists();
	    // If this folder is not yet open, we use STATUS to
	    // get the new message count
	    try {
		Status status = getStatus();
		return status.recent;
	    } catch (BadCommandException bex) {
		// doesn't support STATUS, probably vanilla IMAP4 ..
		// lets try EXAMINE
                IMAPProtocol p = null;

	        try {
	            p = getStoreProtocol();	// XXX
		    MailboxInfo minfo = p.examine(fullName);
		    p.close();
		    return minfo.recent;
		} catch (ProtocolException pex) {
		    // Give up.
		    throw new MessagingException(pex.getMessage(), pex);
		} finally {
                    releaseStoreProtocol(p);
                }
	    } catch (ConnectionException cex) {
		throw new StoreClosedException(store, cex.getMessage());
	    } catch (ProtocolException pex) {
		throw new MessagingException(pex.getMessage(), pex);
	    }
	}

	// Folder is open, we know what the new message count is ..
	synchronized(messageCacheLock) {
	    // tickle the folder and store connections.
	    try {
		keepConnectionAlive(true);
		return recent;
	    } catch (ConnectionException cex) {
		throw new FolderClosedException(this, cex.getMessage());
	    } catch (ProtocolException pex) {
		throw new MessagingException(pex.getMessage(), pex);
	    }
	}
    }

    /**
     * Get the unread message count.
     */
    public synchronized int getUnreadMessageCount()
			throws MessagingException {
	if (!opened) {
	    checkExists();
	    // If this folder is not yet open, we use STATUS to
	    // get the unseen message count
	    try {
		Status status = getStatus();
		return status.unseen;
	    } catch (BadCommandException bex) {
		// doesn't support STATUS, probably vanilla IMAP4 ..
		// Could EXAMINE, SEARCH for UNREAD messages and
		// return the count .. bah, not worth it.
		return -1;
	    } catch (ConnectionException cex) {
		throw new StoreClosedException(store, cex.getMessage());
	    } catch (ProtocolException pex) {
		throw new MessagingException(pex.getMessage(), pex);
	    }
	}

	// if opened, issue server-side search for messages that do
	// *not* have the SEEN flag.
	Flags f = new Flags();
	f.add(Flags.Flag.SEEN);
	try {
	    synchronized(messageCacheLock) {
		int[] matches = getProtocol().search(new FlagTerm(f, false));
		return matches.length; // NOTE: 'matches' is never null
	    }
	} catch (ConnectionException cex) {
	    throw new FolderClosedException(this, cex.getMessage());
	} catch (ProtocolException pex) {
	    // Shouldn't happen
	    throw new MessagingException(pex.getMessage(), pex);
	}
    }

    /**
     * Get the deleted message count.
     */
    public synchronized int getDeletedMessageCount()
			throws MessagingException {
	if (!opened) {
	    checkExists();
	    // no way to do this on closed folders
	    return -1;
	}

	// if opened, issue server-side search for messages that do
	// have the DELETED flag.
	Flags f = new Flags();
	f.add(Flags.Flag.DELETED);
	try {
	    synchronized(messageCacheLock) {
		int[] matches = getProtocol().search(new FlagTerm(f, true));
		return matches.length; // NOTE: 'matches' is never null
	    }
	} catch (ConnectionException cex) {
	    throw new FolderClosedException(this, cex.getMessage());
	} catch (ProtocolException pex) {
	    // Shouldn't happen
	    throw new MessagingException(pex.getMessage(), pex);
	}
    }

    /*
     * Get results of STATUS command for this folder, checking cache first.
     * ASSERT: Must be called with this folder's synchronization lock held.
     * ASSERT: The folder must be closed.
     */
    private Status getStatus() throws ProtocolException {
	int statusCacheTimeout = ((IMAPStore)store).getStatusCacheTimeout();

	// if allowed to cache and our cache is still valid, return it
	if (statusCacheTimeout > 0 && cachedStatus != null &&
	    System.currentTimeMillis() - cachedStatusTime < statusCacheTimeout)
	    return cachedStatus;

        IMAPProtocol p = null;

	try {
	    p = getStoreProtocol();	// XXX
	    Status s = p.status(fullName, null); 
	    // if allowed to cache, do so
	    if (statusCacheTimeout > 0) {
		cachedStatus = s;
		cachedStatusTime = System.currentTimeMillis();
	    }
	    return s;
        } finally {
            releaseStoreProtocol(p);
        }
    }

    /**
     * Get the specified message.
     */
    public synchronized Message getMessage(int msgnum) 
		throws MessagingException {
	checkOpened();
	checkRange(msgnum);

	return messageCache.getMessage(msgnum);
    }

    /**
     * Append the given messages into this folder.
     */
    public synchronized void appendMessages(Message[] msgs)
				throws MessagingException {
	checkExists(); // verify that self exists

	// XXX - have to verify that messages are in a different
	// store (if any) than target folder, otherwise could
	// deadlock trying to fetch messages on the same connection
	// we're using for the append.

	int maxsize = ((IMAPStore)store).getAppendBufferSize();

	for (int i = 0; i < msgs.length; i++) {
	    final Message m = msgs[i];
	    Date d = m.getReceivedDate(); // retain dates
	    if (d == null)
		d = m.getSentDate();
	    final Date dd = d;
	    final Flags f = m.getFlags();

	    final MessageLiteral mos;
	    try {
		// if we know the message is too big, don't buffer any of it
		mos = new MessageLiteral(m,
				m.getSize() > maxsize ? 0 : maxsize);
	    } catch (IOException ex) {
		throw new MessagingException(
				"IOException while appending messages", ex);
	    } catch (MessageRemovedException mrex) {
		continue; // just skip this expunged message
	    }

	    doCommand(new ProtocolCommand() {
		public Object doCommand(IMAPProtocol p)
			throws ProtocolException {
		    p.append(fullName, f, dd, mos);
		    return null;
		}
	    });
	}
    }

    /**
     * Append the given messages into this folder.
     * Return array of AppendUID objects containing
     * UIDs of these messages in the destination folder.
     * Each element of the returned array corresponds to
     * an element of the <code>msgs</code> array.  A null
     * element means the server didn't return UID information
     * for the appended message.  <p>
     *
     * Depends on the APPENDUID response code defined by the
     * UIDPLUS extension -
     * <A HREF="http://www.ietf.org/rfc/rfc2359.txt">RFC 2359</A>.
     *
     * @since	JavaMail 1.4
     */
    public synchronized AppendUID[] appendUIDMessages(Message[] msgs)
				throws MessagingException {
	checkExists(); // verify that self exists

	// XXX - have to verify that messages are in a different
	// store (if any) than target folder, otherwise could
	// deadlock trying to fetch messages on the same connection
	// we're using for the append.

	int maxsize = ((IMAPStore)store).getAppendBufferSize();

	AppendUID[] uids = new AppendUID[msgs.length];
	for (int i = 0; i < msgs.length; i++) {
	    final Message m = msgs[i];
	    final MessageLiteral mos;

	    try {
		// if we know the message is too big, don't buffer any of it
		mos = new MessageLiteral(m,
				m.getSize() > maxsize ? 0 : maxsize);
	    } catch (IOException ex) {
		throw new MessagingException(
				"IOException while appending messages", ex);
	    } catch (MessageRemovedException mrex) {
		continue; // just skip this expunged message
	    }

	    Date d = m.getReceivedDate(); // retain dates
	    if (d == null)
		d = m.getSentDate();
	    final Date dd = d;
	    final Flags f = m.getFlags();
	    AppendUID auid = (AppendUID)doCommand(new ProtocolCommand() {
		public Object doCommand(IMAPProtocol p)
			throws ProtocolException {
		    return p.appenduid(fullName, f, dd, mos);
		}
	    });
	    uids[i] = auid;
	}
	return uids;
    }

    /**
     * Append the given messages into this folder.
     * Return array of Message objects representing
     * the messages in the destination folder.  Note
     * that the folder must be open.
     * Each element of the returned array corresponds to
     * an element of the <code>msgs</code> array.  A null
     * element means the server didn't return UID information
     * for the appended message. <p>
     *
     * Depends on the APPENDUID response code defined by the
     * UIDPLUS extension -
     * <A HREF="http://www.ietf.org/rfc/rfc2359.txt">RFC 2359</A>.
     *
     * @since	JavaMail 1.4
     */
    public synchronized Message[] addMessages(Message[] msgs)
				throws MessagingException {
	checkOpened();
	Message[] rmsgs = new MimeMessage[msgs.length];
	AppendUID[] uids = appendUIDMessages(msgs);
	for (int i = 0; i < uids.length; i++) {
	    AppendUID auid = uids[i];
	    if (auid != null) {
		if (auid.uidvalidity == uidvalidity) {
		    try {
			rmsgs[i] = getMessageByUID(auid.uid);
		    } catch (MessagingException mex) {
			// ignore errors at this stage
		    }
		}
	    }
	}
	return rmsgs;
    }

    /**
     * Copy the specified messages from this folder, to the
     * specified destination.
     */
    public synchronized void copyMessages(Message[] msgs, Folder folder)
			throws MessagingException {
	checkOpened();

	if (msgs.length == 0) // boundary condition
	    return;

	// If the destination belongs to our same store, optimize
	if (folder.getStore() == store) {
	    synchronized(messageCacheLock) {
		try {
		    IMAPProtocol p = getProtocol();
		    MessageSet[] ms = Utility.toMessageSet(msgs, null);
		    if (ms == null)
			throw new MessageRemovedException(
					"Messages have been removed");
		    p.copy(ms, folder.getFullName());
		} catch (CommandFailedException cfx) {
		    if (cfx.getMessage().indexOf("TRYCREATE") != -1)
			throw new FolderNotFoundException(
                            folder,
			    folder.getFullName() + " does not exist"
			   );
		    else 
			throw new MessagingException(cfx.getMessage(), cfx);
		} catch (ConnectionException cex) {
		    throw new FolderClosedException(this, cex.getMessage());
		} catch (ProtocolException pex) {
		    throw new MessagingException(pex.getMessage(), pex);
	    	}
	    }
	} else // destination is a different store.
	    super.copyMessages(msgs, folder);
    }

    /**
     * Copy the specified messages from this folder, to the
     * specified destination.
     * Return array of AppendUID objects containing
     * UIDs of these messages in the destination folder.
     * Each element of the returned array corresponds to
     * an element of the <code>msgs</code> array.  A null
     * element means the server didn't return UID information
     * for the copied message.  <p>
     *
     * Depends on the COPYUID response code defined by the
     * UIDPLUS extension -
     * <A HREF="http://www.ietf.org/rfc/rfc2359.txt">RFC 2359</A>.
     *
     * @since	JavaMail 1.5.1
     */
    public synchronized AppendUID[] copyUIDMessages(Message[] msgs,
			Folder folder) throws MessagingException {
	checkOpened();

	if (msgs.length == 0) // boundary condition
	    return null;

	// the destination must belong to our same store
	if (folder.getStore() == store) {
	    synchronized (messageCacheLock) {
		try {
		    IMAPProtocol p = getProtocol();
		    // XXX - messages have to be from this Folder, who checks?
		    MessageSet[] ms = Utility.toMessageSet(msgs, null);
		    if (ms == null)
			throw new MessageRemovedException(
					"Messages have been removed");
		    CopyUID cuid = p.copyuid(ms, folder.getFullName());

		    /*
		     * Correlate source UIDs with destination UIDs.
		     * This won't be time or space efficient if there's
		     * a lot of messages.
		     *
		     * In order to make sense of the returned UIDs, we need
		     * the UIDs for every one of the original messages.
		     * If we already have them, great, otherwise we need
		     * to fetch them from the server.
		     *
		     * Assume the common case is that the messages are
		     * in in order by UID.  Map the returned source
		     * UIDs to their corresponding Message objects.
		     * Step through the msgs array looking for the
		     * Message object in the returned source message
		     * list.  Most commonly the source message (UID)
		     * for the Nth original message will be in the Nth
		     * position in the returned source message (UID)
		     * list.  Thus, the destination UID is in the Nth
		     * position in the returned destination UID list.
		     * But if the source message isn't where expected,
		     * we have to search the entire source message
		     * list, starting from where we expect it and
		     * wrapping around until we've searched it all.
		     */
		    long[] srcuids = UIDSet.toArray(cuid.src);
		    long[] dstuids = UIDSet.toArray(cuid.dst);
		    // map source UIDs to Message objects
		    // XXX - could inline/optimize this
		    Message[] srcmsgs = getMessagesByUID(srcuids);
		    AppendUID[] result = new AppendUID[msgs.length];
		    for (int i = 0; i < srcmsgs.length; i++) {
			int j = i;
			do {
			    if (msgs[j] == srcmsgs[i]) {
				result[j] = new AppendUID(
						cuid.uidvalidity, dstuids[i]);
				break;
			    }
			    j++;
			    if (j >= msgs.length)
				j = 0;
			} while (j != i);
		    }
		    for (int i = 0; i < msgs.length; i++) {
			int j = i;
			do {
			    if (msgs[i] == srcmsgs[j]) {
				result[i] = new AppendUID(
						cuid.uidvalidity, dstuids[j]);
				break;
			    }
			    j++;
			    if (j >= msgs.length)
				j = 0;
			} while (j != i);
		    }
		    return result;
		} catch (CommandFailedException cfx) {
		    if (cfx.getMessage().indexOf("TRYCREATE") != -1)
			throw new FolderNotFoundException(
                            folder,
			    folder.getFullName() + " does not exist"
			   );
		    else 
			throw new MessagingException(cfx.getMessage(), cfx);
		} catch (ConnectionException cex) {
		    throw new FolderClosedException(this, cex.getMessage());
		} catch (ProtocolException pex) {
		    throw new MessagingException(pex.getMessage(), pex);
	    	}
	    }
	} else // destination is a different store.
	    throw new MessagingException(
			"can't copyUIDMessages to a different store");
    }

    /**
     * Expunge all messages marked as DELETED.
     */
    public synchronized Message[] expunge() throws MessagingException {
	return expunge(null);
    }

    /**
     * Expunge the indicated messages, which must have been marked as DELETED.
     *
     * Depends on the UIDPLUS extension -
     * <A HREF="http://www.ietf.org/rfc/rfc2359.txt">RFC 2359</A>.
     */
    public synchronized Message[] expunge(Message[] msgs)
				throws MessagingException {
	checkOpened();

	if (msgs != null) {
	    // call fetch to make sure we have all the UIDs
	    FetchProfile fp = new FetchProfile();
	    fp.add(UIDFolder.FetchProfileItem.UID);
	    fetch(msgs, fp);
	}

	IMAPMessage[] rmsgs;
	synchronized(messageCacheLock) {
	    doExpungeNotification = false; // We do this ourselves later
	    try {
		IMAPProtocol p = getProtocol();
		if (msgs != null)
		    p.uidexpunge(Utility.toUIDSet(msgs));
		else
		    p.expunge();
	    } catch (CommandFailedException cfx) {
		// expunge not allowed, perhaps due to a permission problem?
		if (mode != READ_WRITE)
		    throw new IllegalStateException(
			"Cannot expunge READ_ONLY folder: " + fullName);
		else
		    throw new MessagingException(cfx.getMessage(), cfx);
	    } catch (ConnectionException cex) {
		throw new FolderClosedException(this, cex.getMessage());
	    } catch (ProtocolException pex) {
		// Bad bad server ..
		throw new MessagingException(pex.getMessage(), pex);
	    } finally {
		doExpungeNotification = true;
	    }

	    // Cleanup expunged messages and sync messageCache with reality.
	    if (msgs != null)
		rmsgs = messageCache.removeExpungedMessages(msgs);
	    else
		rmsgs = messageCache.removeExpungedMessages();
	    if (uidTable != null) {
		for (int i = 0; i < rmsgs.length; i++) {
		    IMAPMessage m = rmsgs[i];
		    /* remove this message from the UIDTable */
		    long uid = m.getUID();
		    if (uid != -1)
			uidTable.remove(Long.valueOf(uid));
		}
	    }

	    // Update 'total'
	    total = messageCache.size();
	}

	// Notify listeners. This time its for real, guys.
	if (rmsgs.length > 0)
	    notifyMessageRemovedListeners(true, rmsgs);
	return rmsgs;
    }

    /**
     * Search whole folder for messages matching the given term.
     */
    public synchronized Message[] search(SearchTerm term)
				throws MessagingException {
	checkOpened();

	try {
	    Message[] matchMsgs = null;

	    synchronized(messageCacheLock) {
		int[] matches = getProtocol().search(term);
		if (matches != null) {
		    matchMsgs = new IMAPMessage[matches.length];
		    int size = messageCache.size();
		    // Map seq-numbers into actual Messages.
		    for (int i = 0; i < matches.length; i++) {
			// Microsoft Exchange will sometimes return message
			// numbers that it has not yet notified the client
			// about via EXISTS; ignore those messages here.
			if (matches[i] > size) {
			    if (logger.isLoggable(Level.FINE))
				logger.fine("ignoring message number " +
				    matches[i] + " in search results, " +
				    "outside range " + size);
			} else
			    matchMsgs[i] = getMessageBySeqNumber(matches[i]);
		    }
		}
	    }
	    return matchMsgs;

	} catch (CommandFailedException cfx) {
	    // unsupported charset or search criterion
	    return super.search(term);
	} catch (SearchException sex) {
	    // too complex for IMAP
	    return super.search(term);
	} catch (ConnectionException cex) {
	    throw new FolderClosedException(this, cex.getMessage());
	} catch (ProtocolException pex) {
	    // bug in our IMAP layer ?
	    throw new MessagingException(pex.getMessage(), pex);
	}
    }

    /**
     * Search the folder for messages matching the given term. Returns
     * array of matching messages. Returns an empty array if no matching
     * messages are found.
     */
    public synchronized Message[] search(SearchTerm term, Message[] msgs) 
			throws MessagingException {
	checkOpened();

	if (msgs.length == 0)
	    // need to return an empty array (not null!)
	    return msgs;

	try {
	    Message[] matchMsgs = null;

	    synchronized(messageCacheLock) {
		IMAPProtocol p = getProtocol();
		MessageSet[] ms = Utility.toMessageSet(msgs, null);
		if (ms == null)
		    throw new MessageRemovedException(
					"Messages have been removed");
		int[] matches = p.search(ms, term);
		if (matches != null) {
		    matchMsgs = new IMAPMessage[matches.length];
		    for (int i = 0; i < matches.length; i++)	
			matchMsgs[i] = getMessageBySeqNumber(matches[i]);
		}
	    }
	    return matchMsgs;

	} catch (CommandFailedException cfx) {
	    // unsupported charset or search criterion
	    return super.search(term, msgs);
	} catch (SearchException sex) {
	    // too complex for IMAP
	    return super.search(term, msgs);
	} catch (ConnectionException cex) {
	    throw new FolderClosedException(this, cex.getMessage());
	} catch (ProtocolException pex) {
	    // bug in our IMAP layer ?
	    throw new MessagingException(pex.getMessage(), pex);
	}
    }

    /**
     * Sort the messages in the folder according to the sort criteria.
     * The messages are returned in the sorted order, but the order of
     * the messages in the folder is not changed. <p>
     *
     * Depends on the SORT extension -
     * <A HREF="http://www.ietf.org/rfc/rfc5256.txt">RFC 5256</A>.
     *
     * @since JavaMail 1.4.4
     */
    public synchronized Message[] getSortedMessages(SortTerm[] term)
				throws MessagingException {
	return getSortedMessages(term, null);
    }

    /**
     * Sort the messages in the folder according to the sort criteria.
     * The messages are returned in the sorted order, but the order of
     * the messages in the folder is not changed.  Only messages matching
     * the search criteria are considered. <p>
     *
     * Depends on the SORT extension -
     * <A HREF="http://www.ietf.org/rfc/rfc5256.txt">RFC 5256</A>.
     *
     * @since JavaMail 1.4.4
     */
    public synchronized Message[] getSortedMessages(SortTerm[] term,
				SearchTerm sterm) throws MessagingException {
	checkOpened();

	try {
	    Message[] matchMsgs = null;

	    synchronized(messageCacheLock) {
		int[] matches = getProtocol().sort(term, sterm);
		if (matches != null) {
		    matchMsgs = new IMAPMessage[matches.length];
		    // Map seq-numbers into actual Messages.
		    for (int i = 0; i < matches.length; i++)	
			matchMsgs[i] = getMessageBySeqNumber(matches[i]);
		}
	    }
	    return matchMsgs;

	} catch (CommandFailedException cfx) {
	    // unsupported charset or search criterion
	    throw new MessagingException(cfx.getMessage(), cfx);
	} catch (SearchException sex) {
	    // too complex for IMAP
	    throw new MessagingException(sex.getMessage(), sex);
	} catch (ConnectionException cex) {
	    throw new FolderClosedException(this, cex.getMessage());
	} catch (ProtocolException pex) {
	    // bug in our IMAP layer ?
	    throw new MessagingException(pex.getMessage(), pex);
	}
    }

    /*
     * Override Folder method to keep track of whether we have any
     * message count listeners.  Normally we won't have any, so we
     * can avoid creating message objects to pass to the notify
     * method.  It's too hard to keep track of when all listeners
     * are removed, and that's a rare case, so we don't try.
     */
    public synchronized void addMessageCountListener(MessageCountListener l) { 
	super.addMessageCountListener(l);
	hasMessageCountListener = true;
    }

    /***********************************************************
     *		UIDFolder interface methods
     **********************************************************/

    /**
     * Returns the UIDValidity for this folder.
     */
    public synchronized long getUIDValidity() throws MessagingException {
	if (opened) // we already have this information
	    return uidvalidity;

        IMAPProtocol p = null;
        Status status = null;

	try {
	    p = getStoreProtocol();	// XXX
	    String[] item = { "UIDVALIDITY" };
	    status = p.status(fullName, item);
	} catch (BadCommandException bex) {
	    // Probably a RFC1730 server
	    throw new MessagingException("Cannot obtain UIDValidity", bex);
	} catch (ConnectionException cex) {
            // Oops, the store or folder died on us.
            throwClosedException(cex);
	} catch (ProtocolException pex) {
	    throw new MessagingException(pex.getMessage(), pex);
	} finally {
            releaseStoreProtocol(p);
        }

	return status.uidvalidity;
    }

    /**
     * Returns the predicted UID that will be assigned to the
     * next message that is appended to this folder.
     * If the folder is closed, the STATUS command is used to
     * retrieve this value.  If the folder is open, the value
     * returned from the SELECT or EXAMINE command is returned.
     * Note that messages may have been appended to the folder
     * while it was open and thus this value may be out of
     * date. <p>
     *
     * Servers implementing RFC2060 likely won't return this value
     * when a folder is opened.  Servers implementing RFC3501
     * should return this value when a folder is opened. <p>
     *
     * @return	the UIDNEXT value, or -1 if unknown
     * @since	JavaMail 1.3.3
     */
    // Not a UIDFolder method, but still useful
    public synchronized long getUIDNext() throws MessagingException {
	if (opened) // we already have this information
	    return uidnext;

        IMAPProtocol p = null;
        Status status = null;

	try {
	    p = getStoreProtocol();	// XXX
	    String[] item = { "UIDNEXT" };
	    status = p.status(fullName, item);
	} catch (BadCommandException bex) {
	    // Probably a RFC1730 server
	    throw new MessagingException("Cannot obtain UIDNext", bex);
	} catch (ConnectionException cex) {
            // Oops, the store or folder died on us.
            throwClosedException(cex);
	} catch (ProtocolException pex) {
	    throw new MessagingException(pex.getMessage(), pex);
	} finally {
            releaseStoreProtocol(p);
        }

	return status.uidnext;
    }

    /**
     * Get the Message corresponding to the given UID.
     * If no such message exists, <code> null </code> is returned.
     */
    public synchronized Message getMessageByUID(long uid) 
			throws MessagingException {
	checkOpened(); // insure folder is open

	IMAPMessage m = null;

	try {
	    synchronized(messageCacheLock) {
		Long l = Long.valueOf(uid);

		if (uidTable != null) {
		    // Check in uidTable
		    m = (IMAPMessage)uidTable.get(l);
		    if (m != null) // found it
			return m;
		} else
		    uidTable = new Hashtable();

		// Check with the server
		// Issue UID FETCH command
		UID u = getProtocol().fetchSequenceNumber(uid);

		if (u != null && u.seqnum <= total) { // Valid UID 
		    m = getMessageBySeqNumber(u.seqnum);
		    m.setUID(u.uid); // set this message's UID ..
		    // .. and put this into the hashtable
		    uidTable.put(l, m);
		}
	    }
	} catch(ConnectionException cex) {
	    throw new FolderClosedException(this, cex.getMessage());
	} catch (ProtocolException pex) {
	    throw new MessagingException(pex.getMessage(), pex);
	}

	return m;
    }

    /**
     * Get the Messages specified by the given range. <p>
     * Returns Message objects for all valid messages in this range.
     * Returns an empty array if no messages are found.
     */
    public synchronized Message[] getMessagesByUID(long start, long end) 
			throws MessagingException {
	checkOpened(); // insure that folder is open

	Message[] msgs; // array of messages to be returned

	try {
	    synchronized(messageCacheLock) {
		if (uidTable == null)
		    uidTable = new Hashtable();

		// Issue UID FETCH for given range
		UID[] ua = getProtocol().fetchSequenceNumbers(start, end);

		msgs = new Message[ua.length];
		IMAPMessage m;
		// NOTE: Below must be within messageCacheLock region
		for (int i = 0; i < ua.length; i++) {
		    m = getMessageBySeqNumber(ua[i].seqnum);
		    m.setUID(ua[i].uid);
		    msgs[i] = m;
		    uidTable.put(Long.valueOf(ua[i].uid), m);
		}
	    }
	} catch(ConnectionException cex) {
	    throw new FolderClosedException(this, cex.getMessage());
	} catch (ProtocolException pex) {
	    throw new MessagingException(pex.getMessage(), pex);
	}

	return msgs;
    }

    /**
     * Get the Messages specified by the given array. <p>
     *
     * <code>uids.length()</code> elements are returned.
     * If any UID in the array is invalid, a <code>null</code> entry
     * is returned for that element.
     */
    public synchronized Message[] getMessagesByUID(long[] uids)
			throws MessagingException {
	checkOpened(); // insure that folder is open

	try {
	    synchronized(messageCacheLock) {
		long[] unavailUids = uids;
		if (uidTable != null) {
		    Vector v = new Vector(); // to collect unavailable UIDs
		    Long l;
		    for (int i = 0; i < uids.length; i++) {
			if (!uidTable.containsKey(l = Long.valueOf(uids[i])))
			    // This UID has not been loaded yet.
			    v.addElement(l);
		    }

		    int vsize = v.size();
		    unavailUids = new long[vsize];
		    for (int i = 0; i < vsize; i++)
			unavailUids[i] = ((Long)v.elementAt(i)).longValue();
		} else
		    uidTable = new Hashtable();

		if (unavailUids.length > 0) {
		    // Issue UID FETCH request for given uids
		    UID[] ua = getProtocol().fetchSequenceNumbers(unavailUids);
		    IMAPMessage m;
		    for (int i = 0; i < ua.length; i++) {
			m = getMessageBySeqNumber(ua[i].seqnum);
			m.setUID(ua[i].uid);
			uidTable.put(Long.valueOf(ua[i].uid), m);
		    }
		}

		// Return array of size = uids.length
		Message[] msgs = new Message[uids.length];
		for (int i = 0; i < uids.length; i++)
		    msgs[i] = (Message)uidTable.get(Long.valueOf(uids[i]));
		return msgs;
	    }
	} catch(ConnectionException cex) {
	    throw new FolderClosedException(this, cex.getMessage());
	} catch (ProtocolException pex) {
	    throw new MessagingException(pex.getMessage(), pex);
	}
    }

    /**
     * Get the UID for the specified message.
     */
    public synchronized long getUID(Message message) 
			throws MessagingException {
	if (message.getFolder() != this)
	    throw new NoSuchElementException(
		"Message does not belong to this folder");

	checkOpened(); // insure that folder is open

	if (!(message instanceof IMAPMessage))
	    throw new MessagingException("message is not an IMAPMessage");
	IMAPMessage m = (IMAPMessage)message;
	// If the message already knows its UID, great ..
	long uid;
	if ((uid = m.getUID()) != -1)
	    return uid;

	synchronized(messageCacheLock) { // Acquire Lock
	    try {
		IMAPProtocol p = getProtocol();
		m.checkExpunged(); // insure that message is not expunged
		UID u = p.fetchUID(m.getSequenceNumber());

		if (u != null) {
		    uid = u.uid;
		    m.setUID(uid); // set message's UID

		    // insert this message into uidTable
		    if (uidTable == null)
			uidTable = new Hashtable();
		    uidTable.put(Long.valueOf(uid), m);
		}
	    } catch (ConnectionException cex) {
		throw new FolderClosedException(this, cex.getMessage());
	    } catch (ProtocolException pex) {
		throw new MessagingException(pex.getMessage(), pex);
	    }
	}

	return uid;
    }

    /**
     * Get or create Message objects for the UIDs.
     */
    private Message[] createMessagesForUIDs(long[] uids) {
	IMAPMessage[] msgs = new IMAPMessage[uids.length];
	for (int i = 0; i < uids.length; i++) {
	    IMAPMessage m = null;
	    if (uidTable != null)
		m = (IMAPMessage)uidTable.get(Long.valueOf(uids[i]));
	    if (m == null) {
		// fake it, we don't know what message this really is
		m = newIMAPMessage(-1);	// no sequence number
		m.setUID(uids[i]);
		m.setExpunged(true);
	    }
	    msgs[i++] = m;
	}
	return msgs;
    }

    /**
     * Returns the HIGHESTMODSEQ for this folder.
     *
     * @see "RFC 4551"
     * @since	JavaMail 1.5.1
     */
    public synchronized long getHighestModSeq() throws MessagingException {
	if (opened) // we already have this information
	    return highestmodseq;

        IMAPProtocol p = null;
        Status status = null;

	try {
	    p = getStoreProtocol();	// XXX
	    if (!p.hasCapability("CONDSTORE"))
		throw new BadCommandException("CONDSTORE not supported");
	    String[] item = { "HIGHESTMODSEQ" };
	    status = p.status(fullName, item);
	} catch (BadCommandException bex) {
	    // Probably a RFC1730 server
	    throw new MessagingException("Cannot obtain HIGHESTMODSEQ", bex);
	} catch (ConnectionException cex) {
            // Oops, the store or folder died on us.
            throwClosedException(cex);
	} catch (ProtocolException pex) {
	    throw new MessagingException(pex.getMessage(), pex);
	} finally {
            releaseStoreProtocol(p);
        }

	return status.highestmodseq;
    }

    /**
     * Get the messages that have been changed since the given MODSEQ value.
     * Also, prefetch the flags for the messages. <p>
     *
     * The server must support the CONDSTORE extension.
     *
     * @see "RFC 4551"
     * @since	JavaMail 1.5.1
     */
    public synchronized Message[] getMessagesByUIDChangedSince(
				long start, long end, long modseq)
				throws MessagingException {
	checkOpened(); // insure that folder is open

	Message[] msgs; // array of messages to be returned

	try {
	    synchronized (messageCacheLock) {
		IMAPProtocol p = getProtocol();
		if (!p.hasCapability("CONDSTORE"))
		    throw new BadCommandException("CONDSTORE not supported");

		// Issue FETCH for given range
		int[] nums = p.uidfetchChangedSince(start, end, modseq);

		msgs = new Message[nums.length];
		for (int i = 0; i < nums.length; i++)
		    msgs[i] = getMessageBySeqNumber(nums[i]);
	    }
	} catch(ConnectionException cex) {
	    throw new FolderClosedException(this, cex.getMessage());
	} catch (ProtocolException pex) {
	    throw new MessagingException(pex.getMessage(), pex);
	}

	return msgs;
    }

    /**
     * Get the quotas for the quotaroot associated with this
     * folder.  Note that many folders may have the same quotaroot.
     * Quotas are controlled on the basis of a quotaroot, not
     * (necessarily) a folder.  The relationship between folders
     * and quotaroots depends on the IMAP server.  Some servers
     * might implement a single quotaroot for all folders owned by
     * a user.  Other servers might implement a separate quotaroot
     * for each folder.  A single folder can even have multiple
     * quotaroots, perhaps controlling quotas for different
     * resources.
     *
     * @return	array of Quota objects for the quotaroots associated with
     *		this folder
     * @exception MessagingException	if the server doesn't support the
     *					QUOTA extension
     */
    public Quota[] getQuota() throws MessagingException {
	return (Quota[])doOptionalCommand("QUOTA not supported",
	    new ProtocolCommand() {
		public Object doCommand(IMAPProtocol p)
			throws ProtocolException {
		    return p.getQuotaRoot(fullName);
		}
	    });
    }

    /**
     * Set the quotas for the quotaroot specified in the quota argument.
     * Typically this will be one of the quotaroots associated with this
     * folder, as obtained from the <code>getQuota</code> method, but it
     * need not be.
     *
     * @param	quota	the quota to set
     * @exception MessagingException	if the server doesn't support the
     *					QUOTA extension
     */
    public void setQuota(final Quota quota) throws MessagingException {
	doOptionalCommand("QUOTA not supported",
	    new ProtocolCommand() {
		public Object doCommand(IMAPProtocol p)
			throws ProtocolException {
		    p.setQuota(quota);
		    return null;
		}
	    });
    }

    /**
     * Get the access control list entries for this folder.
     *
     * @return	array of access control list entries
     * @exception MessagingException	if the server doesn't support the
     *					ACL extension
     */
    public ACL[] getACL() throws MessagingException {
	return (ACL[])doOptionalCommand("ACL not supported",
	    new ProtocolCommand() {
		public Object doCommand(IMAPProtocol p)
			throws ProtocolException {
		    return p.getACL(fullName);
		}
	    });
    }

    /**
     * Add an access control list entry to the access control list
     * for this folder.
     *
     * @param	acl	the access control list entry to add
     * @exception MessagingException	if the server doesn't support the
     *					ACL extension
     */
    public void addACL(ACL acl) throws MessagingException {
	setACL(acl, '\0');
    }

    /**
     * Remove any access control list entry for the given identifier
     * from the access control list for this folder.
     *
     * @param	name	the identifier for which to remove all ACL entries
     * @exception MessagingException	if the server doesn't support the
     *					ACL extension
     */
    public void removeACL(final String name) throws MessagingException {
	doOptionalCommand("ACL not supported",
	    new ProtocolCommand() {
		public Object doCommand(IMAPProtocol p)
			throws ProtocolException {
		    p.deleteACL(fullName, name);
		    return null;
		}
	    });
    }

    /**
     * Add the rights specified in the ACL to the entry for the
     * identifier specified in the ACL.  If an entry for the identifier
     * doesn't already exist, add one.
     *
     * @param	acl	the identifer and rights to add
     * @exception MessagingException	if the server doesn't support the
     *					ACL extension
     */
    public void addRights(ACL acl) throws MessagingException {
	setACL(acl, '+');
    }

    /**
     * Remove the rights specified in the ACL from the entry for the
     * identifier specified in the ACL.
     *
     * @param	acl	the identifer and rights to remove
     * @exception MessagingException	if the server doesn't support the
     *					ACL extension
     */
    public void removeRights(ACL acl) throws MessagingException {
	setACL(acl, '-');
    }

    /**
     * Get all the rights that may be allowed to the given identifier.
     * Rights are grouped per RFC 2086 and each group is returned as an
     * element of the array.  The first element of the array is the set
     * of rights that are always granted to the identifier.  Later
     * elements are rights that may be optionally granted to the
     * identifier. <p>
     *
     * Note that this method lists the rights that it is possible to
     * assign to the given identifier, <em>not</em> the rights that are
     * actually granted to the given identifier.  For the latter, see
     * the <code>getACL</code> method.
     *
     * @param	name	the identifier to list rights for
     * @return		array of Rights objects representing possible
     *			rights for the identifier
     * @exception MessagingException	if the server doesn't support the
     *					ACL extension
     */
    public Rights[] listRights(final String name) throws MessagingException {
	return (Rights[])doOptionalCommand("ACL not supported",
	    new ProtocolCommand() {
		public Object doCommand(IMAPProtocol p)
			throws ProtocolException {
		    return p.listRights(fullName, name);
		}
	    });
    }

    /**
     * Get the rights allowed to the currently authenticated user.
     *
     * @return	the rights granted to the current user
     * @exception MessagingException	if the server doesn't support the
     *					ACL extension
     */
    public Rights myRights() throws MessagingException {
	return (Rights)doOptionalCommand("ACL not supported",
	    new ProtocolCommand() {
		public Object doCommand(IMAPProtocol p)
			throws ProtocolException {
		    return p.myRights(fullName);
		}
	    });
    }

    private void setACL(final ACL acl, final char mod)
				throws MessagingException {
	doOptionalCommand("ACL not supported",
	    new ProtocolCommand() {
		public Object doCommand(IMAPProtocol p)
			throws ProtocolException {
		    p.setACL(fullName, mod, acl);
		    return null;
		}
	    });
    }

    /**
     * Get the attributes that the IMAP server returns with the
     * LIST response.
     *
     * @since	JavaMail 1.3.3
     */
    public synchronized String[] getAttributes() throws MessagingException {
	checkExists();
	if (attributes == null)
	    exists();		// do a LIST to set the attributes
	return attributes == null ? new String[0] :
		(String[])(attributes.clone());
    }

    /**
     * Use the IMAP IDLE command (see
     * <A HREF="http://www.ietf.org/rfc/rfc2177.txt">RFC 2177</A>),
     * if supported by the server, to enter idle mode so that the server
     * can send unsolicited notifications of new messages arriving, etc.
     * without the need for the client to constantly poll the server.
     * Use an appropriate listener to be notified of new messages or
     * other events.  When another thread (e.g., the listener thread)
     * needs to issue an IMAP comand for this folder, the idle mode will
     * be terminated and this method will return.  Typically the caller
     * will invoke this method in a loop. <p>
     *
     * The mail.imap.minidletime property enforces a minimum delay
     * before returning from this method, to ensure that other threads
     * have a chance to issue commands before the caller invokes this
     * method again.  The default delay is 10 milliseconds.
     *
     * @exception MessagingException	if the server doesn't support the
     *					IDLE extension
     * @exception IllegalStateException	if the folder isn't open
     *
     * @since	JavaMail 1.4.1
     */
    public void idle() throws MessagingException {
	idle(false);
    }

    /**
     * Like {@link #idle}, but if <code>once</code> is true, abort the
     * IDLE command after the first notification, to allow the caller
     * to process any notification synchronously.
     *
     * @exception MessagingException	if the server doesn't support the
     *					IDLE extension
     * @exception IllegalStateException	if the folder isn't open
     *
     * @since	JavaMail 1.4.3
     */
    public void idle(boolean once) throws MessagingException {
	// ASSERT: Must NOT be called with this folder's
	// synchronization lock held.
	assert !Thread.holdsLock(this);
	synchronized(this) {
	    checkOpened();
	    Boolean started = (Boolean)doOptionalCommand("IDLE not supported",
		new ProtocolCommand() {
		    public Object doCommand(IMAPProtocol p)
			    throws ProtocolException {
			if (idleState == RUNNING) {
			    p.idleStart();
			    idleState = IDLE;
			    return Boolean.TRUE;
			} else {
			    // some other thread must be running the IDLE
			    // command, we'll just wait for it to finish
			    // without aborting it ourselves
			    try {
				// give up lock and wait to be not idle
				messageCacheLock.wait();
			    } catch (InterruptedException ex) { }
			    return Boolean.FALSE;
			}
		    }
		});
	    if (!started.booleanValue())
		return;
	}

	/*
	 * We gave up the folder lock so that other threads
	 * can get into the folder far enough to see that we're
	 * in IDLE and abort the IDLE.
	 *
	 * Now we read responses from the IDLE command, especially
	 * including unsolicited notifications from the server.
	 * We don't hold the messageCacheLock while reading because
	 * it protects the idleState and other threads need to be
	 * able to examine the state.
	 *
	 * We hold the messageCacheLock while processing the
	 * responses so that we can update the number of messages
	 * in the folder (for example).
	 */
	for (;;) {
	    Response r = protocol.readIdleResponse();
	    try {
		synchronized (messageCacheLock) {
		    try {
			if (r == null || protocol == null ||
				!protocol.processIdleResponse(r)) {
			    idleState = RUNNING;
			    messageCacheLock.notifyAll();
			    break;
			}
		    } catch (ProtocolException pex) {
			idleState = RUNNING;
			messageCacheLock.notifyAll();
			throw pex;
		    }
		    if (once) {
			if (idleState == IDLE) {
			    protocol.idleAbort();
			    idleState = ABORTING;
			}
		    }
		}
	    } catch (ConnectionException cex) {
		// Oops, the store or folder died on us.
		throwClosedException(cex);
	    } catch (ProtocolException pex) {
		throw new MessagingException(pex.getMessage(), pex);
	    }
	}

	/*
	 * Enforce a minimum delay to give time to threads
	 * processing the responses that came in while we
	 * were idle.
	 */
	int minidle = ((IMAPStore)store).getMinIdleTime();
	if (minidle > 0) {
	    try {
		Thread.sleep(minidle);
	    } catch (InterruptedException ex) { }
	}
    }

    /*
     * If an IDLE command is in progress, abort it if necessary,
     * and wait until it completes.
     * ASSERT: Must be called with the message cache lock held.
     */
    void waitIfIdle() throws ProtocolException {
	assert Thread.holdsLock(messageCacheLock);
	while (idleState != RUNNING) {
	    if (idleState == IDLE) {
		protocol.idleAbort();
		idleState = ABORTING;
	    }
	    try {
		// give up lock and wait to be not idle
		messageCacheLock.wait();
	    } catch (InterruptedException ex) { }
	}
    }

    /**
     * Send the IMAP ID command (if supported by the server) and return
     * the result from the server.  The ID command identfies the client
     * to the server and returns information about the server to the client.
     * See <A HREF="http://www.ietf.org/rfc/rfc2971.txt">RFC 2971</A>.
     * The returned Map is unmodifiable.
     *
     * @param	clientParams	a Map of keys and values identifying the client
     * @return			a Map of keys and values identifying the server
     * @exception MessagingException	if the server doesn't support the
     *					ID extension
     * @since	JavaMail 1.5.1
     */
    public Map<String, String> id(final Map<String, String> clientParams)
				throws MessagingException {
	checkOpened();
	return (Map<String,String>)doOptionalCommand("ID not supported",
	    new ProtocolCommand() {
		public Object doCommand(IMAPProtocol p)
			throws ProtocolException {
		    return p.id(clientParams);
		}
	    });
    }

    /**
     * The response handler. This is the callback routine that is 
     * invoked by the protocol layer.
     */
    /*
     * ASSERT: This method must be called only when holding the
     * messageCacheLock.
     * ASSERT: This method must *not* invoke any other method that
     * might grab the 'folder' lock or 'message' lock (i.e., any 
     * synchronized methods on IMAPFolder or IMAPMessage)
     * since that will result in violating the locking hierarchy.
     */
    public void handleResponse(Response r) {
	assert Thread.holdsLock(messageCacheLock);

	/*
	 * First, delegate possible ALERT or notification to the Store.
	 */
	if (r.isOK() || r.isNO() || r.isBAD() || r.isBYE())
	    ((IMAPStore)store).handleResponseCode(r);

	/*
	 * Now check whether this is a BYE or OK response and
	 * handle appropriately.
	 */
	if (r.isBYE()) {
	    if (opened)		// XXX - accessed without holding folder lock
		cleanup(false); 
	    return;
	} else if (r.isOK()) {
	    // HIGHESTMODSEQ can be updated on any OK response
	    r.skipSpaces();
	    if (r.readByte() == '[') {
		String s = r.readAtom();
		if (s.equalsIgnoreCase("HIGHESTMODSEQ"))
		    highestmodseq = r.readLong();
	    }
	    r.reset();
	    return;
	} else if (!r.isUnTagged()) {
	    return;	// might be a continuation for IDLE
	}

	/* Now check whether this is an IMAP specific response */
	if (!(r instanceof IMAPResponse)) {
	    // Probably a bug in our code !
	    // XXX - should be an assert
	    logger.fine("UNEXPECTED RESPONSE : " + r.toString());
	    return;
	}

	IMAPResponse ir = (IMAPResponse)r;

	if (ir.keyEquals("EXISTS")) { // EXISTS
	    int exists = ir.getNumber();
	    if (exists <= realTotal) 
		// Could be the EXISTS following EXPUNGE, ignore 'em
		return;
	
	    int count = exists - realTotal; // number of new messages
	    Message[] msgs = new Message[count];

	    // Add 'count' new IMAPMessage objects into the messageCache
	    messageCache.addMessages(count, realTotal + 1);
	    int oldtotal = total;	// used in loop below
	    realTotal += count;
	    total += count;

	    // avoid instantiating Message objects if no listeners.
	    if (hasMessageCountListener) {
		for (int i = 0; i < count; i++)
		    msgs[i] = messageCache.getMessage(++oldtotal);

		// Notify listeners.
		notifyMessageAddedListeners(msgs);
	    }

	} else if (ir.keyEquals("EXPUNGE")) {
	    // EXPUNGE response.

	    int seqnum = ir.getNumber();
	    Message[] msgs = null;
	    if (doExpungeNotification && hasMessageCountListener) {
		// save the Message object first; can't look it
		// up after it's expunged
		msgs = new Message[] { getMessageBySeqNumber(seqnum) };
	    }

	    messageCache.expungeMessage(seqnum);

	    // decrement 'realTotal'; but leave 'total' unchanged
	    realTotal--;

	    if (msgs != null)	// Do the notification here.
		notifyMessageRemovedListeners(false, msgs);

	} else if (ir.keyEquals("VANISHED")) {
	    // after the folder is opened with QRESYNC, a VANISHED response
	    // without the (EARLIER) tag is used instead of the EXPUNGE
	    // response

	    // "VANISHED" SP ["(EARLIER)"] SP known-uids
	    String[] s = ir.readAtomStringList();
	    if (s == null) {	// no (EARLIER)
		String uids = ir.readAtom();
		UIDSet[] uidset = UIDSet.parseUIDSets(uids);
		// assume no duplicates and no UIDs out of range
		realTotal -= UIDSet.size(uidset);
		long[] luid = UIDSet.toArray(uidset);
		Message[] msgs = createMessagesForUIDs(luid);
		for (Message m : msgs) {
		    if (m.getMessageNumber() > 0)
			messageCache.expungeMessage(m.getMessageNumber());
		}
		if (doExpungeNotification && hasMessageCountListener) {
		    notifyMessageRemovedListeners(true, msgs);
		}
	    } // else if (EARLIER), ignore

	} else if (ir.keyEquals("FETCH")) {
	    assert ir instanceof FetchResponse : "!ir instanceof FetchResponse";
	    Message msg = processFetchResponse((FetchResponse)ir);
	    if (msg != null)
		notifyMessageChangedListeners(
			MessageChangedEvent.FLAGS_CHANGED, msg);

	} else if (ir.keyEquals("RECENT")) {
	    // update 'recent'
	    recent = ir.getNumber();
	}
    }

    /**
     * Process a FETCH response.
     * The only unsolicited FETCH response that makes sense
     * to me (for now) is FLAGS updates, which might include
     * UID and MODSEQ information.  Ignore any other junk.
     */
    private Message processFetchResponse(FetchResponse fr) {
	IMAPMessage msg = getMessageBySeqNumber(fr.getNumber());
	if (msg != null) {	// should always be true
	    boolean notify = false;

	    UID uid = fr.getItem(UID.class);
	    if (uid != null && msg.getUID() != uid.uid) {
		msg.setUID(uid.uid);
		if (uidTable == null)
		    uidTable = new Hashtable();
		uidTable.put(Long.valueOf(uid.uid), msg);
		notify = true;
	    }

	    MODSEQ modseq = fr.getItem(MODSEQ.class);
	    if (modseq != null && msg._getModSeq() != modseq.modseq) {
		msg.setModSeq(modseq.modseq);
		/*
		 * XXX - should we update the folder's HIGHESTMODSEQ or not?
		 *
		if (modseq.modseq > highestmodseq)
		    highestmodseq = modseq.modseq;
		 */
		notify = true;
	    }

	    // Get FLAGS response, if present
	    FLAGS flags = fr.getItem(FLAGS.class);
	    if (flags != null) {
		msg._setFlags(flags);	// assume flags changed
		notify = true;
	    }

	    if (!notify)
		msg = null;
	}
	return msg;
    }

    /**
     * Handle the given array of Responses.
     *
     * ASSERT: This method must be called only when holding the
     * 	messageCacheLock
     */
    void handleResponses(Response[] r) {
	for (int i = 0; i < r.length; i++) {
	    if (r[i] != null)
		handleResponse(r[i]);
	}
    }

    /**
     * Get this folder's Store's protocol connection.
     *
     * When acquiring a store protocol object, it is important to
     * use the following steps:
     *
     *     IMAPProtocol p = null;
     *     try {
     *         p = getStoreProtocol();
     *         // perform the command
     *     } catch (WhateverException ex) {
     *         // handle it
     *     } finally {
     *         releaseStoreProtocol(p);
     *     }
     */
    protected synchronized IMAPProtocol getStoreProtocol() 
            throws ProtocolException {
	connectionPoolLogger.fine("getStoreProtocol() borrowing a connection");
	return ((IMAPStore)store).getFolderStoreProtocol();
    }

    /**
     * Throw the appropriate 'closed' exception.
     */
    protected synchronized void throwClosedException(ConnectionException cex) 
            throws FolderClosedException, StoreClosedException {
	// If it's the folder's protocol object, throw a FolderClosedException;
	// otherwise, throw a StoreClosedException.
	// If a command has failed because the connection is closed,
	// the folder will have already been forced closed by the
	// time we get here and our protocol object will have been
	// released, so if we no longer have a protocol object we base
	// this decision on whether we *think* the folder is open.
	if ((protocol != null && cex.getProtocol() == protocol) ||
		(protocol == null && !reallyClosed))
            throw new FolderClosedException(this, cex.getMessage());
        else
            throw new StoreClosedException(store, cex.getMessage());
    }

    /**
     * Return the IMAPProtocol object for this folder. <p>
     *
     * This method will block if necessary to wait for an IDLE
     * command to finish.
     *
     * @return	the IMAPProtocol object used when the folder is open
     */
    protected IMAPProtocol getProtocol() throws ProtocolException {
	assert Thread.holdsLock(messageCacheLock);
	waitIfIdle();
        return protocol;
    }

    /**
     * A simple interface for user-defined IMAP protocol commands.
     */
    public static interface ProtocolCommand {
	/**
	 * Execute the user-defined command using the supplied IMAPProtocol
	 * object.
	 */
	public Object doCommand(IMAPProtocol protocol) throws ProtocolException;
    }

    /**
     * Execute a user-supplied IMAP command.  The command is executed
     * in the appropriate context with the necessary locks held and
     * using the appropriate <code>IMAPProtocol</code> object. <p>
     *
     * This method returns whatever the <code>ProtocolCommand</code>
     * object's <code>doCommand</code> method returns.  If the
     * <code>doCommand</code> method throws a <code>ConnectionException</code>
     * it is translated into a <code>StoreClosedException</code> or
     * <code>FolderClosedException</code> as appropriate.  If the
     * <code>doCommand</code> method throws a <code>ProtocolException</code>
     * it is translated into a <code>MessagingException</code>. <p>
     *
     * The following example shows how to execute the IMAP NOOP command.
     * Executing more complex IMAP commands requires intimate knowledge
     * of the <code>com.sun1.mail.iap</code> and
     * <code>com.sun1.mail.imap.protocol</code> packages, best acquired by
     * reading the source code. <p>
     *
     * <blockquote><pre>
     * import com.sun1.mail.iap.*;
     * import com.sun1.mail.imap.*;
     * import com.sun1.mail.imap.protocol.*;
     *
     * ...
     *
     * IMAPFolder f = (IMAPFolder)folder;
     * Object val = f.doCommand(new IMAPFolder.ProtocolCommand() {
     *	public Object doCommand(IMAPProtocol p)
     *			throws ProtocolException {
     *	    p.simpleCommand("NOOP", null);
     *	    return null;
     *	}
     * });
     * </pre></blockquote>
     * <p>
     *
     * Here's a more complex example showing how to use the proposed
     * IMAP SORT extension: <p>
     *
     * <pre><blockquote>
     * import com.sun1.mail.iap.*;
     * import com.sun1.mail.imap.*;
     * import com.sun1.mail.imap.protocol.*;
     *
     * ...
     *
     * IMAPFolder f = (IMAPFolder)folder;
     * Object val = f.doCommand(new IMAPFolder.ProtocolCommand() {
     *	public Object doCommand(IMAPProtocol p)
     *			throws ProtocolException {
     *	    // Issue command
     *	    Argument args = new Argument();
     *	    Argument list = new Argument();
     *	    list.writeString("SUBJECT");
     *	    args.writeArgument(list);
     *	    args.writeString("UTF-8");
     *	    args.writeString("ALL");
     *	    Response[] r = p.command("SORT", args);
     *	    Response response = r[r.length-1];
     *
     *	    // Grab response
     *	    Vector v = new Vector();
     *	    if (response.isOK()) { // command succesful 
     *		for (int i = 0, len = r.length; i < len; i++) {
     *		    if (!(r[i] instanceof IMAPResponse))
     *			continue;
     *
     *		    IMAPResponse ir = (IMAPResponse)r[i];
     *		    if (ir.keyEquals("SORT")) {
     *			String num;
     *			while ((num = ir.readAtomString()) != null)
     *			    System.out.println(num);
     *			r[i] = null;
     *		    }
     *		}
     *	    }
     *
     *	    // dispatch remaining untagged responses
     *	    p.notifyResponseHandlers(r);
     *	    p.handleResult(response);
     *
     *	    return null;
     *	}
     * });
     * </pre></blockquote>
     */
    public Object doCommand(ProtocolCommand cmd) throws MessagingException {
	try {
	    return doProtocolCommand(cmd);
	} catch (ConnectionException cex) {
            // Oops, the store or folder died on us.
            throwClosedException(cex);
	} catch (ProtocolException pex) {
	    throw new MessagingException(pex.getMessage(), pex);
	}
	return null;
    }

    public Object doOptionalCommand(String err, ProtocolCommand cmd)
				throws MessagingException {
	try {
	    return doProtocolCommand(cmd);
	} catch (BadCommandException bex) {
	    throw new MessagingException(err, bex);
	} catch (ConnectionException cex) {
            // Oops, the store or folder died on us.
            throwClosedException(cex);
	} catch (ProtocolException pex) {
	    throw new MessagingException(pex.getMessage(), pex);
	}
	return null;
    }

    public Object doCommandIgnoreFailure(ProtocolCommand cmd)
				throws MessagingException {
	try {
	    return doProtocolCommand(cmd);
	} catch (CommandFailedException cfx) {
	    return null;
	} catch (ConnectionException cex) {
            // Oops, the store or folder died on us.
            throwClosedException(cex);
	} catch (ProtocolException pex) {
	    throw new MessagingException(pex.getMessage(), pex);
	}
	return null;
    }

    protected Object doProtocolCommand(ProtocolCommand cmd)
				throws ProtocolException {
	synchronized (this) {
	    /*
	     * Check whether we have a protocol object, not whether we're
	     * opened, to allow use of the exsting protocol object in the
	     * open method before the state is changed to "opened".
	     */
	    if (protocol != null) {
		synchronized (messageCacheLock) {
		    return cmd.doCommand(getProtocol());
		}
	    }
	}

	// only get here if using store's connection
	IMAPProtocol p = null;

	try {
            p = getStoreProtocol();
	    return cmd.doCommand(p);
	} finally {
	    releaseStoreProtocol(p);
	}
    }

    /**
     * Release the store protocol object.  If we borrowed a protocol
     * object from the connection pool, give it back.  If we used our
     * own protocol object, nothing to do.
     */
    protected synchronized void releaseStoreProtocol(IMAPProtocol p) {
        if (p != protocol)
            ((IMAPStore)store).releaseFolderStoreProtocol(p);
	else {
	    // XXX - should never happen
	    logger.fine("releasing our protocol as store protocol?");
	}
    }

    /**
     * Release the protocol object.
     *
     * ASSERT: This method must be called only when holding the
     *  messageCacheLock
     */
    protected void releaseProtocol(boolean returnToPool) {
        if (protocol != null) {
            protocol.removeResponseHandler(this);

            if (returnToPool)
                ((IMAPStore)store).releaseProtocol(this, protocol);
            else {
		protocol.disconnect();	// make sure it's disconnected
                ((IMAPStore)store).releaseProtocol(this, null);
	    }
	    protocol = null;
        }
    }
    
    /**
     * Issue a noop command for the connection if the connection has not been
     * used in more than a second. If <code>keepStoreAlive</code> is true,
     * also issue a noop over the store's connection.
     *
     * ASSERT: This method must be called only when holding the
     *  messageCacheLock
     */
    protected void keepConnectionAlive(boolean keepStoreAlive) 
                    throws ProtocolException {

        if (System.currentTimeMillis() - protocol.getTimestamp() > 1000) {
	    waitIfIdle();
	    if (protocol != null)
		protocol.noop(); 
	}

        if (keepStoreAlive && ((IMAPStore)store).hasSeparateStoreConnection()) {
            IMAPProtocol p = null;
	    try {
		p = ((IMAPStore)store).getFolderStoreProtocol();
		if (System.currentTimeMillis() - p.getTimestamp() > 1000)
		    p.noop();
	    } finally {
		((IMAPStore)store).releaseFolderStoreProtocol(p);
	    }
        }
    }

    /**
     * Get the message object for the given sequence number. If
     * none found, null is returned.
     *
     * ASSERT: This method must be called only when holding the
     *  messageCacheLock
     */
    protected IMAPMessage getMessageBySeqNumber(int seqnum) {
	return messageCache.getMessageBySeqnum(seqnum);
    }

    private boolean isDirectory() {
	return ((type & HOLDS_FOLDERS) != 0);
    }
}

/**
 * An object that holds a Message object
 * and reports its size and writes it to another OutputStream
 * on demand.  Used by appendMessages to avoid the need to
 * buffer the entire message in memory in a single byte array
 * before sending it to the server.
 */
class MessageLiteral implements Literal {
    private Message msg;
    private int msgSize = -1;
    private byte[] buf;		// the buffered message, if not null

    public MessageLiteral(Message msg, int maxsize)
				throws MessagingException, IOException {
	this.msg = msg;
	// compute the size here so exceptions can be returned immediately
	LengthCounter lc = new LengthCounter(maxsize);
	OutputStream os = new CRLFOutputStream(lc);
	msg.writeTo(os);
	os.flush();
	msgSize = lc.getSize();
	buf = lc.getBytes();
    }

    public int size() {
	return msgSize;
    }

    public void writeTo(OutputStream os) throws IOException {
	// the message should not change between the constructor and this call
	try {
	    if (buf != null)
		os.write(buf, 0, msgSize);
	    else {
		os = new CRLFOutputStream(os);
		msg.writeTo(os);
	    }
	} catch (MessagingException mex) {
	    // exceptions here are bad, "should" never happen
	    throw new IOException("MessagingException while appending message: "
				    + mex);
	}
    }
}

/**
 * Count the number of bytes written to the stream.
 * Also, save a copy of small messages to avoid having to process
 * the data again.
 */
class LengthCounter extends OutputStream {
    private int size = 0;
    private byte[] buf;
    private int maxsize;

    public LengthCounter(int maxsize) {
	buf = new byte[8192];
	this.maxsize = maxsize;
    }

    public void write(int b) {
	int newsize = size + 1;
	if (buf != null) {
	    if (newsize > maxsize && maxsize >= 0) {
		buf = null;
	    } else if (newsize > buf.length) {
		byte newbuf[] = new byte[Math.max(buf.length << 1, newsize)];
		System.arraycopy(buf, 0, newbuf, 0, size);
		buf = newbuf;
		buf[size] = (byte)b;
	    } else {
		buf[size] = (byte)b;
	    }
	}
	size = newsize;
    }

    public void write(byte b[], int off, int len) {
	if ((off < 0) || (off > b.length) || (len < 0) ||
            ((off + len) > b.length) || ((off + len) < 0)) {
	    throw new IndexOutOfBoundsException();
	} else if (len == 0) {
	    return;
	}
        int newsize = size + len;
	if (buf != null) {
	    if (newsize > maxsize && maxsize >= 0) {
		buf = null;
	    } else if (newsize > buf.length) {
		byte newbuf[] = new byte[Math.max(buf.length << 1, newsize)];
		System.arraycopy(buf, 0, newbuf, 0, size);
		buf = newbuf;
		System.arraycopy(b, off, buf, size, len);
	    } else {
		System.arraycopy(b, off, buf, size, len);
	    }
	}
        size = newsize;
    }

    public void write(byte[] b) throws IOException {
	write(b, 0, b.length);
    }

    public int getSize() {
	return size;
    }

    public byte[] getBytes() {
	return buf;
    }
}
