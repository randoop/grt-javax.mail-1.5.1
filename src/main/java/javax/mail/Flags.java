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

package javax1.mail;

import java.io.Serializable;
import java.util.*;

/**
 * The Flags class represents the set of flags on a Message.  Flags
 * are composed of predefined system flags, and user defined flags. <p>
 *
 * A System flag is represented by the <code>Flags.Flag</code> 
 * inner class. A User defined flag is represented as a String.
 * User flags are case-independent. <p>
 *
 * A set of standard system flags are predefined.  Most folder
 * implementations are expected to support these flags.  Some
 * implementations may also support arbitrary user-defined flags.  The
 * <code>getPermanentFlags</code> method on a Folder returns a Flags
 * object that holds all the flags that are supported by that folder
 * implementation. <p>
 *
 * A Flags object is serializable so that (for example) the
 * use of Flags objects in search terms can be serialized
 * along with the search terms. <p>
 *
 * <strong>Warning:</strong>
 * Serialized objects of this class may not be compatible with future
 * JavaMail API releases.  The current serialization support is
 * appropriate for short term storage. <p>
 *
 * The below code sample illustrates how to set, examine, and get the 
 * flags for a message. <p>
 * <pre>
 *
 * Message m = folder.getMessage(1);
 * m.setFlag(Flags.Flag.DELETED, true); // set the DELETED flag
 *
 * // Check if DELETED flag is set on this message
 * if (m.isSet(Flags.Flag.DELETED))
 *	System.out.println("DELETED message");
 *
 * // Examine ALL system flags for this message
 * Flags flags = m.getFlags();
 * Flags.Flag[] sf = flags.getSystemFlags();
 * for (int i = 0; i < sf.length; i++) {
 *	if (sf[i] == Flags.Flag.DELETED)
 *            System.out.println("DELETED message");
 *	else if (sf[i] == Flags.Flag.SEEN)
 *            System.out.println("SEEN message");
 *      ......
 *      ......
 * }
 * </pre>
 * <p>
 *
 * @see    Folder#getPermanentFlags
 * @author John Mani
 * @author Bill Shannon
 */

public class Flags implements Cloneable, Serializable {

    private int system_flags = 0;
    private Hashtable user_flags = null;

    private final static int ANSWERED_BIT 	= 0x01;
    private final static int DELETED_BIT 	= 0x02;
    private final static int DRAFT_BIT 		= 0x04;
    private final static int FLAGGED_BIT 	= 0x08;
    private final static int RECENT_BIT		= 0x10;
    private final static int SEEN_BIT		= 0x20;
    private final static int USER_BIT		= 0x80000000;

    private static final long serialVersionUID = 6243590407214169028L;

    /**
     * This inner class represents an individual system flag. A set
     * of standard system flag objects are predefined here.
     */
    public static final class Flag {
	/**
	 * This message has been answered. This flag is set by clients 
	 * to indicate that this message has been answered to.
	 */
	public static final Flag ANSWERED = new Flag(ANSWERED_BIT);

	/**
	 * This message is marked deleted. Clients set this flag to
	 * mark a message as deleted. The expunge operation on a folder
	 * removes all messages in that folder that are marked for deletion.
	 */
	public static final Flag DELETED = new Flag(DELETED_BIT);

	/**
	 * This message is a draft. This flag is set by clients
	 * to indicate that the message is a draft message.
	 */
	public static final Flag DRAFT = new Flag(DRAFT_BIT);

	/**
	 * This message is flagged. No semantic is defined for this flag.
	 * Clients alter this flag.
	 */
	public static final Flag FLAGGED = new Flag(FLAGGED_BIT);

	/**
	 * This message is recent. Folder implementations set this flag
	 * to indicate that this message is new to this folder, that is,
	 * it has arrived since the last time this folder was opened. <p>
	 *
	 * Clients cannot alter this flag.
	 */
	public static final Flag RECENT = new Flag(RECENT_BIT);

	/**
	 * This message is seen. This flag is implicitly set by the 
	 * implementation when the this Message's content is returned 
	 * to the client in some form. The <code>getInputStream</code>
	 * and <code>getContent</code> methods on Message cause this
	 * flag to be set. <p>
	 *
	 * Clients can alter this flag.
	 */
	public static final Flag SEEN = new Flag(SEEN_BIT);

	/**
	 * A special flag that indicates that this folder supports
	 * user defined flags. <p>
	 *
	 * The implementation sets this flag. Clients cannot alter 
	 * this flag but can use it to determine if a folder supports
	 * user defined flags by using
	 * <code>folder.getPermanentFlags().contains(Flags.Flag.USER)</code>.
	 */
	public static final Flag USER = new Flag(USER_BIT);

	// flags are stored as bits for efficiency
	private int bit;
	private Flag(int bit) {
	    this.bit = bit;
	}
    }


    /**
     * Construct an empty Flags object.
     */
    public Flags() { }

    /**
     * Construct a Flags object initialized with the given flags.
     *
     * @param flags	the flags for initialization
     */
    public Flags(Flags flags) {
	this.system_flags = flags.system_flags;
	if (flags.user_flags != null)
	    this.user_flags = (Hashtable)flags.user_flags.clone();
    }

    /**
     * Construct a Flags object initialized with the given system flag.
     *
     * @param flag	the flag for initialization
     */
    public Flags(Flag flag) {
	this.system_flags |= flag.bit;
    }

    /**
     * Construct a Flags object initialized with the given user flag.
     *
     * @param flag	the flag for initialization
     */
    public Flags(String flag) {
	user_flags = new Hashtable(1);
	user_flags.put(flag.toLowerCase(Locale.ENGLISH), flag);
    }

    /**
     * Add the specified system flag to this Flags object.
     *
     * @param flag	the flag to add
     */
    public void add(Flag flag) {
	system_flags |= flag.bit;
    }

    /**
     * Add the specified user flag to this Flags object.
     *
     * @param flag	the flag to add
     */
    public void add(String flag) {
	if (user_flags == null)
	    user_flags = new Hashtable(1);
	user_flags.put(flag.toLowerCase(Locale.ENGLISH), flag);
    }

    /**
     * Add all the flags in the given Flags object to this
     * Flags object.
     *
     * @param f	Flags object
     */
    public void add(Flags f) {
	system_flags |= f.system_flags; // add system flags

	if (f.user_flags != null) { // add user-defined flags
	    if (user_flags == null)
		user_flags = new Hashtable(1);

	    Enumeration e = f.user_flags.keys();

	    while (e.hasMoreElements()) {
		String s = (String)e.nextElement();
		user_flags.put(s, f.user_flags.get(s));
	    }
	}
    }

    /**
     * Remove the specified system flag from this Flags object.
     *
     * @param	flag 	the flag to be removed
     */
    public void remove(Flag flag) {
	system_flags &= ~flag.bit;
    }

    /**
     * Remove the specified user flag from this Flags object.
     *
     * @param	flag 	the flag to be removed
     */
    public void remove(String flag) {
	if (user_flags != null)
	    user_flags.remove(flag.toLowerCase(Locale.ENGLISH));
    }

    /**
     * Remove all flags in the given Flags object from this 
     * Flags object.
     *
     * @param	f 	the flag to be removed
     */
    public void remove(Flags f) {
	system_flags &= ~f.system_flags; // remove system flags

	if (f.user_flags != null) {
	    if (user_flags == null)
		return;

	    Enumeration e = f.user_flags.keys();
	    while (e.hasMoreElements())
		user_flags.remove(e.nextElement());
	}
    }

    /**
     * Check whether the specified system flag is present in this Flags object.
     *
     * @return 		true of the given flag is present, otherwise false.
     */
    public boolean contains(Flag flag) {
	return (system_flags & flag.bit) != 0;
    }

    /**
     * Check whether the specified user flag is present in this Flags object.
     *
     * @return 		true of the given flag is present, otherwise false.
     */
    public boolean contains(String flag) {
	if (user_flags == null) 
	    return false;
	else
	    return user_flags.containsKey(flag.toLowerCase(Locale.ENGLISH));
    }

    /**
     * Check whether all the flags in the specified Flags object are
     * present in this Flags object.
     *
     * @return	true if all flags in the given Flags object are present, 
     *		otherwise false.
     */
    public boolean contains(Flags f) {
	// Check system flags
	if ((f.system_flags & system_flags) != f.system_flags)
	    return false;

	// Check user flags
	if (f.user_flags != null) {
	    if (user_flags == null)
		return false;
	    Enumeration e = f.user_flags.keys();

	    while (e.hasMoreElements()) {
		if (!user_flags.containsKey(e.nextElement()))
		    return false;
	    }
	}

	// If we've made it till here, return true
	return true;
    }

    /**
     * Check whether the two Flags objects are equal.
     *
     * @return	true if they're equal
     */
    public boolean equals(Object obj) {
	if (!(obj instanceof Flags))
	    return false;

	Flags f = (Flags)obj;

	// Check system flags
	if (f.system_flags != this.system_flags)
	    return false;

	// Check user flags
	if (f.user_flags == null && this.user_flags == null)
	    return true;
	if (f.user_flags != null && this.user_flags != null &&
		f.user_flags.size() == this.user_flags.size()) {
	    Enumeration e = f.user_flags.keys();

	    while (e.hasMoreElements()) {
		if (!this.user_flags.containsKey(e.nextElement()))
		    return false;
	    }
	    return true;
	}

	return false;
    }

    /**
     * Compute a hash code for this Flags object.
     *
     * @return	the hash code
     */
    public int hashCode() {
	int hash = system_flags;
	if (user_flags != null) {
	    Enumeration e = user_flags.keys();
	    while (e.hasMoreElements())
		hash += ((String)e.nextElement()).hashCode();
	}
	return hash;
    }

    /**
     * Return all the system flags in this Flags object.  Returns
     * an array of size zero if no flags are set.
     *
     * @return	array of Flags.Flag objects representing system flags
     */
    public Flag[] getSystemFlags() {
	Vector v = new Vector();
	if ((system_flags & ANSWERED_BIT) != 0)
	    v.addElement(Flag.ANSWERED);
	if ((system_flags & DELETED_BIT) != 0)
	    v.addElement(Flag.DELETED);
	if ((system_flags & DRAFT_BIT) != 0)
	    v.addElement(Flag.DRAFT);
	if ((system_flags & FLAGGED_BIT) != 0)
	    v.addElement(Flag.FLAGGED);
	if ((system_flags & RECENT_BIT) != 0)
	    v.addElement(Flag.RECENT);
	if ((system_flags & SEEN_BIT) != 0)
	    v.addElement(Flag.SEEN);
	if ((system_flags & USER_BIT) != 0)
	    v.addElement(Flag.USER);

	Flag[] f = new Flag[v.size()];
	v.copyInto(f);
	return f;
    }

    /**
     * Return all the user flags in this Flags object.  Returns
     * an array of size zero if no flags are set.
     *
     * @return	array of Strings, each String represents a flag.
     */
    public String[] getUserFlags() {
	Vector v = new Vector();
	if (user_flags != null) {
	    Enumeration e = user_flags.elements();

	    while (e.hasMoreElements())
		v.addElement(e.nextElement());
	}

	String[] f = new String[v.size()];
	v.copyInto(f);
	return f;
    }

    /**
     * Returns a clone of this Flags object.
     */
    public Object clone() {
	Flags f = null;
	try {
	    f = (Flags)super.clone();
	} catch (CloneNotSupportedException cex) {
	    // ignore, can't happen
	}
	if (this.user_flags != null)
	    f.user_flags = (Hashtable)this.user_flags.clone();
	return f;
    }

    /*****
    public static void main(String argv[]) throws Exception {
	// a new flags object
	Flags f1 = new Flags();
	f1.add(Flags.Flag.DELETED);
	f1.add(Flags.Flag.SEEN);
	f1.add(Flags.Flag.RECENT);
	f1.add(Flags.Flag.ANSWERED);

	// check copy constructor with only system flags
	Flags fc = new Flags(f1);
	if (f1.equals(fc) && fc.equals(f1))
	    System.out.println("success");
	else
	    System.out.println("fail");

	// check clone with only system flags
	fc = (Flags)f1.clone();
	if (f1.equals(fc) && fc.equals(f1))
	    System.out.println("success");
	else
	    System.out.println("fail");

	// add a user flag and make sure it still works right
	f1.add("MyFlag");

	// shouldn't be equal here
	if (!f1.equals(fc) && !fc.equals(f1))
	    System.out.println("success");
	else
	    System.out.println("fail");

	// check clone
	fc = (Flags)f1.clone();
	if (f1.equals(fc) && fc.equals(f1))
	    System.out.println("success");
	else
	    System.out.println("fail");

	// make sure user flag hash tables are separate
	fc.add("AnotherFlag");
	if (!f1.equals(fc) && !fc.equals(f1))
	    System.out.println("success");
	else
	    System.out.println("fail");

	// check copy constructor
	fc = new Flags(f1);
	if (f1.equals(fc) && fc.equals(f1))
	    System.out.println("success");
	else
	    System.out.println("fail");

	// another new flags object
	Flags f2 = new Flags(Flags.Flag.ANSWERED);
	f2.add("MyFlag");

	if (f1.contains(Flags.Flag.DELETED))
	    System.out.println("success");
	else
	    System.out.println("fail");
		
	if (f1.contains(Flags.Flag.SEEN))
	    System.out.println("success");
	else
	    System.out.println("fail");

	if (f1.contains(Flags.Flag.RECENT))
	    System.out.println("success");
	else
	    System.out.println("fail");

	if (f1.contains("MyFlag"))
	    System.out.println("success");
	else
	    System.out.println("fail");

	if (f2.contains(Flags.Flag.ANSWERED))
	    System.out.println("success");
	else
	    System.out.println("fail");


	System.out.println("----------------");

	String[] s = f1.getUserFlags();
	for (int i = 0; i < s.length; i++)
	    System.out.println(s[i]);
	System.out.println("----------------");
	s = f2.getUserFlags();
	for (int i = 0; i < s.length; i++)
	    System.out.println(s[i]);

	System.out.println("----------------");

	if (f1.contains(f2)) // this should be true
	    System.out.println("success");
	else
	    System.out.println("fail");

	if (!f2.contains(f1)) // this should be false
	    System.out.println("success");
	else
	    System.out.println("fail");

	Flags f3 = new Flags();
	f3.add(Flags.Flag.DELETED);
	f3.add(Flags.Flag.SEEN);
	f3.add(Flags.Flag.RECENT);
	f3.add(Flags.Flag.ANSWERED);
	f3.add("ANOTHERFLAG");
	f3.add("MYFLAG");

	f1.add("AnotherFlag");

	if (f1.equals(f3))
	    System.out.println("equals success");
	else
	    System.out.println("fail");
	if (f3.equals(f1))
	    System.out.println("equals success");
	else
	    System.out.println("fail");
	System.out.println("f1 hash code " + f1.hashCode());
	System.out.println("f3 hash code " + f3.hashCode());
	if (f1.hashCode() == f3.hashCode())
	    System.out.println("success");
	else
	    System.out.println("fail");
    }
    ****/
}
