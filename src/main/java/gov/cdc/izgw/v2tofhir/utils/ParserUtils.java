package gov.cdc.izgw.v2tofhir.utils;
import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Reference;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Composite;
import ca.uhn.hl7v2.model.Group;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.model.Primitive;
import ca.uhn.hl7v2.model.Segment;
import ca.uhn.hl7v2.model.Structure;
import ca.uhn.hl7v2.model.Type;
import ca.uhn.hl7v2.model.Varies;
import gov.cdc.izgw.v2tofhir.converter.DatatypeConverter;
import lombok.extern.slf4j.Slf4j;

/**
 * ParserUtils is a utility class supporting Parsing of V2 messages into FHIR
 * 
 * @author Audacious Inquiry
 *
 */
@Slf4j
public class ParserUtils {
	private ParserUtils() {}
	
	/**
	 * Remove the string at the given position from the array
	 * @param lineParts	The String array
	 * @param position	The position
	 * @return	The new array with the specified string removed
	 */
	public static String[] removeArrayElement(String[] lineParts, int position) {
		if (lineParts.length <= 1 && position < lineParts.length) {
			return new String[0];
		} 
		String[] newArray = new String[lineParts.length - 1];
		if (position < lineParts.length) {
			System.arraycopy(lineParts, 0, newArray, 0, position);
		} 
		if (lineParts.length > position + 1) {
			System.arraycopy(lineParts, position + 1, newArray, position,
				lineParts.length - position - 1);
		}
		return newArray;
	}
	
	/**
	 * Convert a list of values into a Pattern that matches it (case insensitively)
	 * 
	 * This method is used to create Matchers to match sets of known values (e.g., states, countries,
	 * street name abbreviations, et cetera).
	 * 
	 * @param values	The values to convert
	 * @return	A compiled pattern that matches the specified string
	 */
	public static Pattern toPattern(String... values) {
		StringBuilder b = new StringBuilder();
		for (String value : values) {
			b.append("^").append(value).append("$|");
		}
		b.setLength(b.length() - 1);
		return Pattern.compile(b.toString(), Pattern.CASE_INSENSITIVE);
	}
	
	/**
     * Convert a HAPI V2 datatype to a String
     * @param type The Type to convert
     * @return The string conversion
     */
    public static String toString(Type type) {
		if (type == null) {
			return null;
		}
		while (type != null) {
			type = DatatypeConverter.adjustIfVaries(type);
			if (type instanceof Primitive pt) {
				return pt.getValue();
			}
			if (type instanceof Composite comp) {
				return toString(comp, 0);
			} 
			if (type instanceof Varies v) {
				type = v.getData();
			} else {
				return null;
			}
		}
		return null;
	}
	
	/**
     * Convert a component of a HAPI V2 datatype to a String
     * @param types Components of the composite type to convert
     * @param i The index of the component to convert
     * @return The string conversion
     */
	public static String toString(Type[] types, int i) {
		return types.length > i ? toString(types[i]) : "";
		
	}
	/**
     * Convert a HAPI V2 Composite component to a String
     * @param v2Type The composite type containing the component to convert
     * @param i The index of the component to convert
     * @return The string conversion
     */
	public static String toString(Composite v2Type, int i) {
		if (v2Type == null) {
			return "";
		}
		return toString(v2Type.getComponents(), i);
	}
	
	/**
     * Convert a HAPI V2 Composite subcomponent to a String
     * @param v2Type The composite type containing the subcomponent to convert
     * @param i The index of the component to convert
     * @param j The index of the subcomponent within the component to convert
     * @return The string conversion
     */
	public static String toString(Composite v2Type, int i, int j) {
		if (v2Type == null) {
			return "";
		}
		
		Type[] types = v2Type.getComponents();
		Type t = types.length > i ? types[i] : null;
		if (t instanceof Varies v) {
			t = v.getData();
		}
		if (t instanceof Composite comp) {
			return toString(comp, j);
		}
		return toString(t);
	}

	/**
     * Convert HAPI V2 Composite components to an array of Strings
     * @param v2Type The composite type containing the components to convert
     * @param locations The locations within the component to convert
     * @return The string conversion
     */

	public static String[] toStrings(Composite v2Type, int ...locations) {
		String[] strings = new String[locations.length];
		
		Type[] types = v2Type.getComponents();
		for (int i = 0; i < locations.length; i++) {
			if (locations[i] < types.length) {
				strings[i] = toString(v2Type, locations[i]);
			}
		}
		return strings;
	}
	
	/**
	 * Detect (without exceptions) if HAPI V2 type is empty
	 * @param type	The type to check
	 * @return true if the component is empty or throws an exception, false if it has data.
	 */
	public static boolean isEmpty(Type type) {
		try {
			return type.isEmpty();
		} catch (HL7Exception e) {
			warnException("Unexpected HL7Exception: {}", e.getMessage(), e);
			return true;
		}
	}
	
	/**
	 * Convert an ISO-8601 string to one with punctuation
	 * @param value	The string to cleanup
	 * @param hasDate True if this string has a date, false if just the time
	 * @return	The cleaned up ISO-8601 string suitable for use in FHIR
	 */
	public static String cleanupIsoDateTime(String value, boolean hasDate) {
		if (StringUtils.substringBefore(value, ".").length() < (hasDate ? 4 : 2)) {
			return null;
		}
		if (!hasLegalIso8601Chars(value, hasDate)) {
			return null;
		}
		StringReader r = new StringReader(value.trim());
		StringBuilder b = new StringBuilder();
		int[] data = { 4, '-', 2, '-', 2, 'T', 2, ':', 2, ':', 2, '.', 6, '+', 2, ':', 2 };
		int c = 0;
		int state = hasDate ? 0 : 6;
		try {
			while (state < data.length && (c = r.read()) >= 0) {
				if (Character.isWhitespace(c)) {
					continue;
				}
				if (data[state] < ' ') {  
					state = getIsoDigits(b, data, c, state);
				} else {
					state = getIsoPunct(b, data, c, state);
				}
			}
			if (c < 0 || r.read() < 0) {
				return b.toString();
			}
		} catch (IOException e) {
			// Not going to happen on StringReader.
		}
		// We finished reading, but there was more data.
		return null;
	}
	
	/**
	 * Check to see if a string contains only legal counts of ISO-8601 characters
	 * @param value	The string to check
	 * @param hasDate True if this string has a date, false if just the time
	 * @return	True if the string looks like an ISO-8601 string, false otherwise.
	 */
	
	public static boolean hasLegalIso8601Chars(String value, boolean hasDate) {
		// Check for legal ISO characters
		String legalChars = hasDate ? "0123456789T:-+.Z" : "0123456789:-+.Z";
		if (!StringUtils.containsOnly(value, legalChars)) {
			return false;
		}
		// Check for legal number of ISO punctuation characters
		if (StringUtils.countMatches(value, 'T') > 1 ||  // NOSONAR This style is more readable
			StringUtils.countMatches(value, '.') > 1 ||
			StringUtils.countMatches(value, '+') > 1 ||
			StringUtils.countMatches(value, '-') > 3 ||
			StringUtils.countMatches(value, ':') > 3) {
			return false;
		}
		return true;
	}

	private static int getIsoDigits(StringBuilder b, int[] data, int c, int state) {
		// We are Looking for digits
		if (Character.isDigit(c)) {
			b.append((char)c);
			if (--data[state] == 0) {
				state++;
			}
		} else if (state + 1 == data.length) {
			// We've finished this, but we have an unexpected character
			b.append((char)c);
			state++;
		} else if (data[state + 1] == c || (data[state + 1] == '+' && c == '-')) {
			// We got the separator early, append it and advance two states
			b.append((char)c);
			state += 2;
		}
		return state;
	}
	
	private static int getIsoPunct(StringBuilder b, int[] data, int c, int state) {
		if (c == data[state]) { 
			// Waiting on punctuation and we got it
			b.append((char)c);
			if (c == 'Z') {
				state = data.length; // We're done.
			}
			state++;
		} else if (state > 5 && "+-Z".indexOf(c) >= 0 ) {
			// Timezone can show up early
			b.append((char)c);
			state = 14;
		} else if ((char)c == '.') {
			// Decimal can show up early
			b.append((char)c);
			state = 12;
		} else if (Character.isDigit(c)) {
			// We got a digit while waiting on punctuation
			b.append((char)data[state]);
			++state;
			b.append((char)c);
			--data[state];
		}
		return state;
	}
	
	/**
	 * Given a string containing escaped HL7 Version 2 characters, translate them
	 * to the standard values.
	 * 
	 * @param value	The string to translated
	 * @return	The translated string
	 */
	public static String unescapeV2Chars(String value) {
		value = value.replace("\\F\\", "|");
		value = value.replace("\\S\\", "^");
		value = value.replace("\\R\\", "~");
		value = value.replace("\\T\\", "&");
		value = value.replace("\\E\\", "\\");
		return value;
	}
	
	/**
	 * Given a string possibly containing V2 reserved characters, translate
	 * the reserved characters to their standard escape sequences.
	 * 
	 * @param value	The string to escape
	 * @return	The escaped string
	 */
	public static String escapeV2Chars(String value) {
		value = value.replace("\\", "\\E\\");
		value = value.replace("|", "\\F\\");
		value = value.replace("^", "\\S\\");
		value = value.replace("~", "\\R\\");
		value = value.replace("&", "\\T\\");
		return value;
	}
	
	/**
	 * Iterate over a set of structures and add them to a set
	 * 
	 * This is primarily used by testing components to get a set of segments
	 * for testing from test messages, by may also be useful in StructureParser objects
	 * to enumerate the message components in a message or group.
	 *  
	 * NOTE: Use a LinkedHashSet to get the structures in order.
	 * 
	 * @param g	The group to traverse
	 * @param structures The set of structures to add to.
	 */
	public static void iterateStructures(Group g, Set<Structure> structures) {
		if (g == null) {
			return;
		}
		for (String name: g.getNames()) {
			try {
				for (Structure s: g.getAll(name)) {
					if (s instanceof Segment seg) {
						structures.add(seg);
					} else if (s instanceof Group group) {
						structures.add(s);
						iterateStructures(group, structures);
					}
				}
			} catch (HL7Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	/**
	 * Iterate over a set of structures and the segments within them to a set
	 * 
	 * This is primarily used by testing components to get a set of segments
	 * for testing from test messages, by may also be useful in StructureParser objects
	 * to enumerate the message components in a message or group.
	 *  
	 * NOTE: Use a LinkedHashSet to get the segments in order.
	 * 
	 * @see #iterateStructures(Group, Set)
	 * @param g	The group to traverse
	 * @param structures The set of structures to add to.
	 */
	public static void iterateSegments(Group g, Set<Segment> structures) {
		if (g == null) {
			return;
		}
		for (String name: g.getNames()) {
			try {
				for (Structure s: g.getAll(name)) {
					if (s instanceof Segment seg) {
						structures.add(seg);
					} else if (s instanceof Group group) {
						iterateSegments(group, structures);
					}
				}
			} catch (HL7Exception e) {
				log.error("Unexpected HL7Exception: {}", e.getMessage(), e);
			}
		}
	}

	/**
	 * Get a component from a composite
	 * @param type	The composite to get the component from
	 * @param number The zero-indexed component to get 
	 * @return	The component, or null if type doesn't represent a Composite 
	 */
	public static Type getComponent(Type type, int number) {
		if (type instanceof Varies v) {
			type = v.getData();
		}
		if (type instanceof Composite comp) {
			return comp.getComponents()[number];
		}
		warn("{}-{} is not a composite", type.getName(), number, type);
		return null;
	}
	
	/**
	 * Get the first occurrence of a field in a segment, nor null if it does not exist or an exception is
	 * thrown.
	 * 
	 * @param segment	The segment to get the field from
	 * @param field	The zero-index field number
	 * @return	The first occurence of the field
	 */
	public static Type getField(Segment segment, int field) {
		try {
			Type[] types = segment.getField(field); 
			if (types == null || types.length == 0) {
				return null;
			}
			if (types[0] == null || types[0].isEmpty()) {
				return null;
			}
			return types[0];
		} catch (HL7Exception e) {
			return null;
		} 
	}
	
	/**
	 * Get the all occurrences of a field in a segment, nor null if it does not exist or an exception is
	 * thrown.
	 * 
	 * @param segment	The segment to get the field from
	 * @param field	The zero-index field number
	 * @return	The fields
	 */
	public static Type[] getFields(Segment segment, int field) {
		try {
			Type[] types = segment.getField(field); 
			if (types == null || types.length == 0) {
				return new Type[0];
			}
			return types;
		} catch (HL7Exception e) {
			return new Type[0];
		} 
	}
	

	
	/**
	 * Test if a segment has a value in a given field
	 * @param segment	The segment to test
	 * @param field		The field to look for
	 * @return	true if the field is present, false otherwise
	 */
	public static  boolean hasField(Segment segment, int field) {
		return getField(segment, field) != null;
	}

	/**
	 * Get the type of a field in a segment
	 * 
	 * Used in path conversions to determine the type of a component
	 * 
	 * @param segment	The segment to check
	 * @param number	The field within the segment
	 * @return The first occurrence of the field
	 */
	public static Type getFieldType(String segment, int number) {
		Segment seg = getSegment(segment);
		if (seg == null) {
			return null;
		}
		try {
			return seg.getField(number, 0);
		} catch (HL7Exception e) {
			warnException("Unexpected {} {}: {}", e.getClass().getSimpleName(), segment, e.getMessage(), e);
		}
		return null;
	}
	/**
	 * The the class representing the specified segment type
	 * 
	 * Classes are taken preferentially from HAPI V2 2.8.1 models, but some withdrawn classes will
	 * come from earlier model versions (usually HAPI V2 2.5.1).
	 * 
	 * @param segment	The HL7 V2 segment name
	 * @return	A HAPI V2 class representing the segment.
	 */
	private static Class<Segment> getSegmentClass(String segment) {
		String name = null;
		Exception saved = null;
		for (Class<?> errClass : Arrays.asList(ca.uhn.hl7v2.model.v281.segment.ERR.class, ca.uhn.hl7v2.model.v251.segment.ERR.class)) {
			name = errClass.getPackageName() +  segment;
			try {
				@SuppressWarnings("unchecked")
				Class<Segment> clazz = (Class<Segment>) ClassLoader.getSystemClassLoader().loadClass(name);
				return clazz;
			} catch (ClassNotFoundException e) {
				saved = e;
			}
		}
		if (saved != null) {
			warnException("Unexpected {} loading {}: {}", saved.getClass().getSimpleName(), segment, saved.getMessage(), saved);
		}
		return null;
	}
	
	/**
	 * Create a segment of the specified type
	 * @param segment	The type of segment to create 
	 * @return	The constructed segment, or null if the segment type is unknown.
	 */
	public static Segment getSegment(String segment) {
		Class<Segment> segClass = getSegmentClass(segment);
		if (segClass == null) {
			return null;
		}
		try {
			return segClass.getDeclaredConstructor(Message.class).newInstance((Message)null);
		} catch (InstantiationException | IllegalAccessException | IllegalArgumentException
				| InvocationTargetException | NoSuchMethodException | SecurityException e) {
			warnException("Unexpected {} loading {}", e.getClass().getSimpleName(), segClass.getName(), e);
			return null;
		}
	}
	/**
	 * Create a reference to a resource.  
	 * 
	 * Set the reference id to the resource id, and make this reference point 
	 * to the resource through getResource().
	 * 
	 * @param resource	The resource to create the reference to
	 * @return	The created Reference
	 */
	public static Reference toReference(DomainResource resource) {
		if (resource == null) {
			throw new NullPointerException("Resource cannot be null");
		}
		IdType id = resource.getIdElement();
		Reference ref = new Reference();
		if (id != null) {
			ref.setId(id.getIdPart());
		}
		ref.setUserData("Resource", resource);
		return ref;
	}
	
	private static void warn(String msg, Object ...args) {
		log.warn(msg, args);
	}
	private static void warnException(String msg, Object ...args) {
		log.warn(msg, args);
	}

	/**
	 * Return true if this datatypes string representation is of a date type 
	 * and therefore needs to be restated to ISO with punctuation.
	 * @param t	The data type
	 * @return	true if cleanup is needed.
	 */
	public static Boolean needsIsoCleanup(Type t) {
		String typeName = t.getName();
		switch (typeName) {
		case "TM": return Boolean.FALSE;
		case "DIN", "DLD", "DR", "DT", "DTM": 
			return Boolean.TRUE;
		default:
			return null;  // NOSONAR null = No cleanup needed
		}
	}
}
