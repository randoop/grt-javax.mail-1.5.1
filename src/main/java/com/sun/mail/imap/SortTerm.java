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

package com.sun1.mail.imap;

/**
 * A particular sort criteria, as defined by
 * <A HREF="http://www.ietf.org/rfc/rfc5256.txt">RFC 5256</A>.
 * Sort criteria are used with the
 * {@link IMAPFolder#getSortedMessages getSortedMessages} method.
 * Multiple sort criteria are specified in an array with the order in
 * the array specifying the order in which the sort criteria are applied.
 *
 * @since JavaMail 1.4.4
 */
public final class SortTerm {
    /**
     * Sort by message arrival date and time.
     */
    public static final SortTerm ARRIVAL = new SortTerm("ARRIVAL");

    /**
     * Sort by email address of first Cc recipient.
     */
    public static final SortTerm CC = new SortTerm("CC");

    /**
     * Sort by sent date and time.
     */
    public static final SortTerm DATE = new SortTerm("DATE");

    /**
     * Sort by first From email address.
     */
    public static final SortTerm FROM = new SortTerm("FROM");

    /**
     * Reverse the sort order of the following item.
     */
    public static final SortTerm REVERSE = new SortTerm("REVERSE");

    /**
     * Sort by the message size.
     */
    public static final SortTerm SIZE = new SortTerm("SIZE");

    /**
     * Sort by the base subject text.  Note that the "base subject"
     * is defined by RFC 5256 and doesn't include items such as "Re:"
     * in the subject header.
     */
    public static final SortTerm SUBJECT = new SortTerm("SUBJECT");

    /**
     * Sort by email address of first To recipient.
     */
    public static final SortTerm TO = new SortTerm("TO");

    private String term;
    private SortTerm(String term) {
	this.term = term;
    }

    public String toString() {
	return term;
    }
}
