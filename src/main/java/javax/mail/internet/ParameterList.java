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

package javax1.mail.internet;

import java.util.*;
import java.io.*;
import com.sun1.mail.util.PropUtil;
import com.sun1.mail.util.ASCIIUtility;

/**
 * This class holds MIME parameters (attribute-value pairs).
 * The <code>mail.mime.encodeparameters</code> and
 * <code>mail.mime.decodeparameters</code> System properties
 * control whether encoded parameters, as specified by 
 * <a href="http://www.ietf.org/rfc/rfc2231.txt">RFC 2231</a>,
 * are supported.  By default, such encoded parameters <b>are</b>
 * supported. <p>
 *
 * Also, in the current implementation, setting the System property
 * <code>mail.mime.decodeparameters.strict</code> to <code>"true"</code>
 * will cause a <code>ParseException</code> to be thrown for errors
 * detected while decoding encoded parameters.  By default, if any
 * decoding errors occur, the original (undecoded) string is used. <p>
 *
 * The current implementation supports the System property
 * <code>mail.mime.parameters.strict</code>, which if set to false
 * when parsing a parameter list allows parameter values
 * to contain whitespace and other special characters without
 * being quoted; the parameter value ends at the next semicolon.
 * If set to true (the default), parameter values are required to conform
 * to the MIME specification and must be quoted if they contain whitespace
 * or special characters.
 *
 * @author  John Mani
 * @author  Bill Shannon
 */

public class ParameterList {

    /**
     * The map of name, value pairs.
     * The value object is either a String, for unencoded
     * values, or a Value object, for encoded values,
     * or a MultiValue object, for multi-segment parameters.
     *
     * We use a LinkedHashMap so that parameters are (as much as
     * possible) kept in the original order.  Note however that
     * multi-segment parameters (see below) will appear in the
     * position of the first seen segment and orphan segments
     * will all move to the end.
     */
    private Map list = new LinkedHashMap();	// keep parameters in order

    /**
     * A set of names for multi-segment parameters that we
     * haven't processed yet.  Normally such names are accumulated
     * during the inital parse and processed at the end of the parse,
     * but such names can also be set via the set method when the
     * IMAP provider accumulates pre-parsed pieces of a parameter list.
     * (A special call to the set method tells us when the IMAP provider
     * is done setting parameters.)
     *
     * A multi-segment parameter is defined by RFC 2231.  For example,
     * "title*0=part1; title*1=part2", which represents a parameter
     * named "title" with value "part1part2".
     *
     * Note also that each segment of the value might or might not be
     * encoded, indicated by a trailing "*" on the parameter name.
     * If any segment is encoded, the first segment must be encoded.
     * Only the first segment contains the charset and language
     * information needed to decode any encoded segments.
     *
     * RFC 2231 introduces many possible failure modes, which we try
     * to handle as gracefully as possible.  Generally, a failure to
     * decode a parameter value causes the non-decoded parameter value
     * to be used instead.  Missing segments cause all later segments
     * to be appear as independent parameters with names that include
     * the segment number.  For example, "title*0=part1; title*1=part2;
     * title*3=part4" appears as two parameters named "title" and "title*3".
     */
    private Set multisegmentNames;

    /**
     * A map containing the segments for all not-yet-processed
     * multi-segment parameters.  The map is indexed by "name*seg".
     * The value object is either a String or a Value object.
     * The Value object is not decoded during the initial parse
     * because the segments may appear in any order and until the
     * first segment appears we don't know what charset to use to
     * decode the encoded segments.  The segments are hex decoded
     * in order, combined into a single byte array, and converted
     * to a String using the specified charset in the
     * combineMultisegmentNames method.
     */
    private Map slist;

    /**
     * MWB 3BView: The name of the last parameter added to the map.
     * Used for the AppleMail hack.
     */
    private String lastName = null;

    private static final boolean encodeParameters =
	PropUtil.getBooleanSystemProperty("mail.mime.encodeparameters", true);
    private static final boolean decodeParameters =
	PropUtil.getBooleanSystemProperty("mail.mime.decodeparameters", true);
    private static final boolean decodeParametersStrict =
	PropUtil.getBooleanSystemProperty(
	    "mail.mime.decodeparameters.strict", false);
    private static final boolean applehack =
	PropUtil.getBooleanSystemProperty("mail.mime.applefilenames", false);
    private static final boolean windowshack =
	PropUtil.getBooleanSystemProperty("mail.mime.windowsfilenames", false);
    private static final boolean parametersStrict = 
	PropUtil.getBooleanSystemProperty("mail.mime.parameters.strict", true);


    /**
     * A struct to hold an encoded value.
     * A parsed encoded value is stored as both the
     * decoded value and the original encoded value
     * (so that toString will produce the same result).
     * An encoded value that is set explicitly is stored
     * as the original value and the encoded value, to
     * ensure that get will return the same value that
     * was set.
     */
    private static class Value {
	String value;
	String charset;
	String encodedValue;
    }

    /**
     * A struct for a multi-segment parameter.  Each entry in the
     * List is either a String or a Value object.  When all the
     * segments are present and combined in the combineMultisegmentNames
     * method, the value field contains the combined and decoded value.
     * Until then the value field contains an empty string as a placeholder.
     */
    private static class MultiValue extends ArrayList {
	String value;
    }

    /**
     * Map the LinkedHashMap's keySet iterator to an Enumeration.
     */
    private static class ParamEnum implements Enumeration {
	private Iterator it;

	ParamEnum(Iterator it) {
	    this.it = it;
	}

	public boolean hasMoreElements() {
	    return it.hasNext();
	}

	public Object nextElement() {
	    return it.next();
	}
    }

    /**
     * No-arg Constructor.
     */
    public ParameterList() { 
	// initialize other collections only if they'll be needed
	if (decodeParameters) {
	    multisegmentNames = new HashSet();
	    slist = new HashMap();
	}
    }

    /**
     * Constructor that takes a parameter-list string. The String
     * is parsed and the parameters are collected and stored internally.
     * A ParseException is thrown if the parse fails. 
     * Note that an empty parameter-list string is valid and will be 
     * parsed into an empty ParameterList.
     *
     * @param	s	the parameter-list string.
     * @exception	ParseException if the parse fails.
     */
    public ParameterList(String s) throws ParseException {
	this();

	HeaderTokenizer h = new HeaderTokenizer(s, HeaderTokenizer.MIME);
	for (;;) {
	    HeaderTokenizer.Token tk = h.next();
	    int type = tk.getType();
	    String name, value;

	    if (type == HeaderTokenizer.Token.EOF) // done
		break;

	    if ((char)type == ';') {
		// expect parameter name
		tk = h.next();
		// tolerate trailing semicolon, even though it violates the spec
		if (tk.getType() == HeaderTokenizer.Token.EOF)
		    break;
		// parameter name must be a MIME Atom
		if (tk.getType() != HeaderTokenizer.Token.ATOM)
		    throw new ParseException("Expected parameter name, " +
					    "got \"" + tk.getValue() + "\"");
		name = tk.getValue().toLowerCase(Locale.ENGLISH);

		// expect '='
		tk = h.next();
		if ((char)tk.getType() != '=')
		    throw new ParseException("Expected '=', " +
					    "got \"" + tk.getValue() + "\"");

		// expect parameter value
		if (windowshack &&
			(name.equals("name") || name.equals("filename")))
		    tk = h.next(';', true);
		else if (parametersStrict)
		    tk = h.next();
		else
		    tk = h.next(';');
		type = tk.getType();
		// parameter value must be a MIME Atom or Quoted String
		if (type != HeaderTokenizer.Token.ATOM &&
		    type != HeaderTokenizer.Token.QUOTEDSTRING)
		    throw new ParseException("Expected parameter value, " +
					    "got \"" + tk.getValue() + "\"");

		value = tk.getValue();
		lastName = name;
		if (decodeParameters)
		    putEncodedName(name, value);
		else
		    list.put(name, value);
            } else {
		// MWB 3BView new code to add in filenames generated by 
		// AppleMail.
		// Note - one space is assumed between name elements.
		// This may not be correct but it shouldn't matter too much.
		// Note: AppleMail encodes filenames with non-ascii characters 
		// correctly, so we don't need to worry about the name* subkeys.
		if (type == HeaderTokenizer.Token.ATOM && lastName != null &&
			    ((applehack &&
				(lastName.equals("name") ||
				 lastName.equals("filename"))) ||
			    !parametersStrict)
			 ) {
		    // Add value to previous value
		    String lastValue = (String)list.get(lastName);
		    value = lastValue + " " + tk.getValue();
		    list.put(lastName, value);
                } else {
		    throw new ParseException("Expected ';', " +
					    "got \"" + tk.getValue() + "\"");
		}
	    }
        }

	if (decodeParameters) {
	    /*
	     * After parsing all the parameters, combine all the
	     * multi-segment parameter values together.
	     */
	    combineMultisegmentNames(false);
	}
    }

    /**
     * Normal users of this class will use simple parameter names.
     * In some cases, for example, when processing IMAP protocol
     * messages, individual segments of a multi-segment name
     * (specified by RFC 2231) will be encountered and passed to
     * the {@link #set} method.  After all these segments are added
     * to this ParameterList, they need to be combined to represent
     * the logical parameter name and value.  This method will combine
     * all segments of multi-segment names. <p>
     *
     * Normal users should never need to call this method.
     *
     * @since	JavaMail 1.5
     */ 
    public void combineSegments() {
	/*
	 * If we've accumulated any multi-segment names from calls to
	 * the set method from (e.g.) the IMAP provider, combine the pieces.
	 * Ignore any parse errors (e.g., from decoding the values)
	 * because it's too late to report them.
	 */
	if (decodeParameters && multisegmentNames.size() > 0) {
	    try {
		combineMultisegmentNames(true);
	    } catch (ParseException pex) {
		// too late to do anything about it
	    }
	}
    }

    /**
     * If the name is an encoded or multi-segment name (or both)
     * handle it appropriately, storing the appropriate String
     * or Value object.  Multi-segment names are stored in the
     * main parameter list as an emtpy string as a placeholder,
     * replaced later in combineMultisegmentNames with a MultiValue
     * object.  This causes all pieces of the multi-segment parameter
     * to appear in the position of the first seen segment of the
     * parameter.
     */
    private void putEncodedName(String name, String value)
				throws ParseException {
	int star = name.indexOf('*');
	if (star < 0) {
	    // single parameter, unencoded value
	    list.put(name, value);
	} else if (star == name.length() - 1) {
	    // single parameter, encoded value
	    name = name.substring(0, star);
	    Value v = extractCharset(value);
	    try {
		v.value = decodeBytes(v.value, v.charset);
	    } catch (UnsupportedEncodingException ex) {
		if (decodeParametersStrict)
		    throw new ParseException(ex.toString());
	    }
	    list.put(name, v);
	} else {
	    // multiple segments
	    String rname = name.substring(0, star);
	    multisegmentNames.add(rname);
	    list.put(rname, "");

	    Object v;
	    if (name.endsWith("*")) {
		// encoded value
		if (name.endsWith("*0*")) {	// first segment
		    v = extractCharset(value);
		} else {
		    v = new Value();
		    ((Value)v).encodedValue = value;
		    ((Value)v).value = value;	// default; decoded later
		}
		name = name.substring(0, name.length() - 1);
	    } else {
		// unencoded value
		v = value;
	    }
	    slist.put(name, v);
	}
    }

    /**
     * Iterate through the saved set of names of multi-segment parameters,
     * for each parameter find all segments stored in the slist map,
     * decode each segment as needed, combine the segments together into
     * a single decoded value, and save all segments in a MultiValue object
     * in the main list indexed by the parameter name.
     */
    private void combineMultisegmentNames(boolean keepConsistentOnFailure)
				throws ParseException {
	boolean success = false;
	try {
	    Iterator it = multisegmentNames.iterator();
	    while (it.hasNext()) {
		String name = (String)it.next();
		MultiValue mv = new MultiValue();
		/*
		 * Now find all the segments for this name and
		 * decode each segment as needed.
		 */
		String charset = null;
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		int segment;
		for (segment = 0; ; segment++) {
		    String sname = name + "*" + segment;
		    Object v = slist.get(sname);
		    if (v == null)	// out of segments
			break;
		    mv.add(v);
		    try {
			if (v instanceof Value) {
			    Value vv = (Value)v;
			    if (segment == 0) {
				// the first segment specifies the charset
				// for all other encoded segments
				charset = vv.charset;
			    } else {
				if (charset == null) {
				    // should never happen
				    multisegmentNames.remove(name);
				    break;
				}
			    }
			    decodeBytes(vv.value, bos);
			} else {
			    bos.write(ASCIIUtility.getBytes((String)v));
			}
		    } catch (IOException ex) {
			// XXX - should never happen
		    }
		    slist.remove(sname);
		}
		if (segment == 0) {
		    // didn't find any segments at all
		    list.remove(name);
		} else {
		    try {
			if (charset != null)
			    charset = MimeUtility.javaCharset(charset);
			if (charset == null || charset.length() == 0)
			    charset = MimeUtility.getDefaultJavaCharset();
			if (charset != null)
			    mv.value = bos.toString(charset);
			else
			    mv.value = bos.toString();
		    } catch (UnsupportedEncodingException uex) {
			if (decodeParametersStrict)
			    throw new ParseException(uex.toString());
			// convert as if ASCII
			mv.value = bos.toString(0);
		    }
		    list.put(name, mv);
		}
	    }
	    success = true;
	} finally {
	    /*
	     * If we get here because of an exception that's going to
	     * be thrown (success == false) from the constructor
	     * (keepConsistentOnFailure == false), this is all wasted effort.
	     */
	    if (keepConsistentOnFailure || success)  {
		// we should never end up with anything in slist,
		// but if we do, add it all to list
		if (slist.size() > 0) {
		    // first, decode any values that we'll add to the list
		    Iterator sit = slist.values().iterator();
		    while (sit.hasNext()) {
			Object v = sit.next();
			if (v instanceof Value) {
			    Value vv = (Value)v;
			    try {
				vv.value =
				    decodeBytes(vv.value, vv.charset);
			    } catch (UnsupportedEncodingException ex) {
				if (decodeParametersStrict)
				    throw new ParseException(ex.toString());
			    }
			}
		    }
		    list.putAll(slist);
		}

		// clear out the set of names and segments
		multisegmentNames.clear();
		slist.clear();
	    }
	}
    }

    /**
     * Return the number of parameters in this list.
     * 
     * @return  number of parameters.
     */
    public int size() {
	return list.size();
    }

    /**
     * Returns the value of the specified parameter. Note that 
     * parameter names are case-insensitive.
     *
     * @param name	parameter name.
     * @return		Value of the parameter. Returns 
     *			<code>null</code> if the parameter is not 
     *			present.
     */
    public String get(String name) {
	String value;
	Object v = list.get(name.trim().toLowerCase(Locale.ENGLISH));
	if (v instanceof MultiValue)
	    value = ((MultiValue)v).value;
	else if (v instanceof Value)
	    value = ((Value)v).value;
	else
	    value = (String)v;
	return value;
    }

    /**
     * Set a parameter. If this parameter already exists, it is
     * replaced by this new value.
     *
     * @param	name 	name of the parameter.
     * @param	value	value of the parameter.
     */
    public void set(String name, String value) {
	name = name.trim().toLowerCase(Locale.ENGLISH);
	if (decodeParameters) {
	    try {
		putEncodedName(name, value);
	    } catch (ParseException pex) {
		// ignore it
		list.put(name, value);
	    }
	} else
	    list.put(name, value);
    }

    /**
     * Set a parameter. If this parameter already exists, it is
     * replaced by this new value.  If the
     * <code>mail.mime.encodeparameters</code> System property
     * is true, and the parameter value is non-ASCII, it will be
     * encoded with the specified charset, as specified by RFC 2231.
     *
     * @param	name 	name of the parameter.
     * @param	value	value of the parameter.
     * @param	charset	charset of the parameter value.
     * @since	JavaMail 1.4
     */
    public void set(String name, String value, String charset) {
	if (encodeParameters) {
	    Value ev = encodeValue(value, charset);
	    // was it actually encoded?
	    if (ev != null)
		list.put(name.trim().toLowerCase(Locale.ENGLISH), ev);
	    else
		set(name, value);
	} else
	    set(name, value);
    }

    /**
     * Removes the specified parameter from this ParameterList.
     * This method does nothing if the parameter is not present.
     *
     * @param	name	name of the parameter.
     */
    public void remove(String name) {
	list.remove(name.trim().toLowerCase(Locale.ENGLISH));
    }

    /**
     * Return an enumeration of the names of all parameters in this
     * list.
     *
     * @return Enumeration of all parameter names in this list.
     */
    public Enumeration getNames() {
	return new ParamEnum(list.keySet().iterator());
    }

    /**
     * Convert this ParameterList into a MIME String. If this is
     * an empty list, an empty string is returned.
     *
     * @return		String
     */
    public String toString() {
	return toString(0);
    }

    /**
     * Convert this ParameterList into a MIME String. If this is
     * an empty list, an empty string is returned.
     *   
     * The 'used' parameter specifies the number of character positions
     * already taken up in the field into which the resulting parameter
     * list is to be inserted. It's used to determine where to fold the
     * resulting parameter list.
     *
     * @param used      number of character positions already used, in
     *                  the field into which the parameter list is to
     *                  be inserted.
     * @return          String
     */  
    public String toString(int used) {
        ToStringBuffer sb = new ToStringBuffer(used);
        Iterator e = list.keySet().iterator();
 
        while (e.hasNext()) {
            String name = (String)e.next();
	    Object v = list.get(name);
	    if (v instanceof MultiValue) {
		MultiValue vv = (MultiValue)v;
		String ns = name + "*";
		for (int i = 0; i < vv.size(); i++) {
		    Object va = vv.get(i);
		    if (va instanceof Value)
			sb.addNV(ns + i + "*", ((Value)va).encodedValue);
		    else
			sb.addNV(ns + i, (String)va);
		}
	    } else if (v instanceof Value)
		sb.addNV(name + "*", ((Value)v).encodedValue);
	    else
		sb.addNV(name, (String)v);
        }
        return sb.toString();
    }

    /**
     * A special wrapper for a StringBuffer that keeps track of the
     * number of characters used in a line, wrapping to a new line
     * as necessary; for use by the toString method.
     */
    private static class ToStringBuffer {
	private int used;	// keep track of how much used on current line
	private StringBuffer sb = new StringBuffer();

	public ToStringBuffer(int used) {
	    this.used = used;
	}

	public void addNV(String name, String value) {
	    value = quote(value);
	    sb.append("; ");
	    used += 2;
	    int len = name.length() + value.length() + 1;
	    if (used + len > 76) { // overflows ...
		sb.append("\r\n\t"); // .. start new continuation line
		used = 8; // account for the starting <tab> char
	    }
	    sb.append(name).append('=');
	    used += name.length() + 1;
	    if (used + value.length() > 76) { // still overflows ...
		// have to fold value
		String s = MimeUtility.fold(used, value);
		sb.append(s);
		int lastlf = s.lastIndexOf('\n');
		if (lastlf >= 0)	// always true
		    used += s.length() - lastlf - 1;
		else
		    used += s.length();
	    } else {
		sb.append(value);
		used += value.length();
	    }
	}

	public String toString() {
	    return sb.toString();
	}
    }
 
    // Quote a parameter value token if required.
    private static String quote(String value) {
	return MimeUtility.quote(value, HeaderTokenizer.MIME);
    }

    private static final char hex[] = {
	'0','1', '2', '3', '4', '5', '6', '7',
	'8','9', 'A', 'B', 'C', 'D', 'E', 'F'
    };

    /**
     * Encode a parameter value, if necessary.
     * If the value is encoded, a Value object is returned.
     * Otherwise, null is returned.
     * XXX - Could return a MultiValue object if parameter value is too long.
     */
    private static Value encodeValue(String value, String charset) {
	if (MimeUtility.checkAscii(value) == MimeUtility.ALL_ASCII)
	    return null;	// no need to encode it

	byte[] b;	// charset encoded bytes from the string
	try {
	    b = value.getBytes(MimeUtility.javaCharset(charset));
	} catch (UnsupportedEncodingException ex) {
	    return null;
	}
	StringBuffer sb = new StringBuffer(b.length + charset.length() + 2);
	sb.append(charset).append("''");
	for (int i = 0; i < b.length; i++) {
	    char c = (char)(b[i] & 0xff);
	    // do we need to encode this character?
	    if (c <= ' ' || c >= 0x7f || c == '*' || c == '\'' || c == '%' ||
		    HeaderTokenizer.MIME.indexOf(c) >= 0) {
		sb.append('%').append(hex[c>>4]).append(hex[c&0xf]);
	    } else
		sb.append(c);
	}
	Value v = new Value();
	v.charset = charset;
	v.value = value;
	v.encodedValue = sb.toString();
	return v;
    }

    /**
     * Extract charset and encoded value.
     * Value will be decoded later.
     */
    private static Value extractCharset(String value) throws ParseException {
	Value v = new Value();
	v.value = v.encodedValue = value;
	try {
	    int i = value.indexOf('\'');
	    if (i < 0) {
		if (decodeParametersStrict)
		    throw new ParseException(
			"Missing charset in encoded value: " + value);
		return v;	// not encoded correctly?  return as is.
	    }
	    String charset = value.substring(0, i);
	    int li = value.indexOf('\'', i + 1);
	    if (li < 0) {
		if (decodeParametersStrict)
		    throw new ParseException(
			"Missing language in encoded value: " + value);
		return v;	// not encoded correctly?  return as is.
	    }
	    // String lang = value.substring(i + 1, li);
	    v.value = value.substring(li + 1);
	    v.charset = charset;
	} catch (NumberFormatException nex) {
	    if (decodeParametersStrict)
		throw new ParseException(nex.toString());
	} catch (StringIndexOutOfBoundsException ex) {
	    if (decodeParametersStrict)
		throw new ParseException(ex.toString());
	}
	return v;
    }

    /**
     * Decode the encoded bytes in value using the specified charset.
     */
    private static String decodeBytes(String value, String charset)
			throws ParseException, UnsupportedEncodingException {
	/*
	 * Decode the ASCII characters in value
	 * into an array of bytes, and then convert
	 * the bytes to a String using the specified
	 * charset.  We'll never need more bytes than
	 * encoded characters, so use that to size the
	 * array.
	 */
	byte[] b = new byte[value.length()];
	int i, bi;
	for (i = 0, bi = 0; i < value.length(); i++) {
	    char c = value.charAt(i);
	    if (c == '%') {
		try {
		    String hex = value.substring(i + 1, i + 3);
		    c = (char)Integer.parseInt(hex, 16);
		    i += 2;
		} catch (NumberFormatException ex) {
		    if (decodeParametersStrict)
			throw new ParseException(ex.toString());
		} catch (StringIndexOutOfBoundsException ex) {
		    if (decodeParametersStrict)
			throw new ParseException(ex.toString());
		}
	    }
	    b[bi++] = (byte)c;
	}
	if (charset != null)
	    charset = MimeUtility.javaCharset(charset);
	if (charset == null || charset.length() == 0)
	    charset = MimeUtility.getDefaultJavaCharset();
	return new String(b, 0, bi, charset);
    }

    /**
     * Decode the encoded bytes in value and write them to the OutputStream.
     */
    private static void decodeBytes(String value, OutputStream os)
				throws ParseException, IOException {
	/*
	 * Decode the ASCII characters in value
	 * and write them to the stream.
	 */
	int i;
	for (i = 0; i < value.length(); i++) {
	    char c = value.charAt(i);
	    if (c == '%') {
		try {
		    String hex = value.substring(i + 1, i + 3);
		    c = (char)Integer.parseInt(hex, 16);
		    i += 2;
		} catch (NumberFormatException ex) {
		    if (decodeParametersStrict)
			throw new ParseException(ex.toString());
		} catch (StringIndexOutOfBoundsException ex) {
		    if (decodeParametersStrict)
			throw new ParseException(ex.toString());
		}
	    }
	    os.write((byte)c);
	}
    }
}
