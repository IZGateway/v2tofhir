package gov.cdc.izgw.v2tofhir.converter;
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
import ca.uhn.hl7v2.model.Primitive;
import ca.uhn.hl7v2.model.Segment;
import ca.uhn.hl7v2.model.Structure;
import ca.uhn.hl7v2.model.Type;
import ca.uhn.hl7v2.model.Varies;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ParserUtils {
	private ParserUtils() {}
	public static String[] removeArrayElement(String[] lineParts,
			int position) {
		String[] newArray = new String[lineParts.length - 1];
		if (position == 1) {
			System.arraycopy(lineParts, 0, newArray, 1, lineParts.length - 1);
		} else {
			System.arraycopy(lineParts, 0, newArray, 0, position - 1);
			if (lineParts.length > position) {
				System.arraycopy(lineParts, position, newArray, position - 1,
						lineParts.length - position);
			}
		}
		return newArray;
	}
	public static Pattern toPattern(String... values) {
		StringBuilder b = new StringBuilder();
		for (String value : values) {
			b.append("^").append(value).append("$|");
		}
		b.setLength(b.length() - 1);
		return Pattern.compile(b.toString(), Pattern.CASE_INSENSITIVE);
	}
	
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
	
	public static String toString(Type[] types, int i) {
		return types.length > i ? toString(types[i]) : "";
		
	}
	public static String toString(Composite v2Type, int i) {
		if (v2Type == null) {
			return "";
		}
		return toString(v2Type.getComponents(), i);
	}
	
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
	
	public static boolean isEmpty(Type type) {
		try {
			return type.isEmpty();
		} catch (HL7Exception e) {
			warnException("Unexpected HL7Exception: {}", e.getMessage(), e);
			return true;
		}
	}
	
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
	private static boolean hasLegalIso8601Chars(String value, boolean hasDate) {
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
	public static String unescapeV2Chars(String value) {
		value = value.replace("\\F\\", "|");
		value = value.replace("\\S\\", "^");
		value = value.replace("\\R\\", "~");
		value = value.replace("\\T\\", "&");
		value = value.replace("\\E\\", "\\");
		return value;
	}
	public static String escapeV2Chars(String value) {
		value = value.replace("\\", "\\E\\");
		value = value.replace("|", "\\F\\");
		value = value.replace("^", "\\S\\");
		value = value.replace("~", "\\R\\");
		value = value.replace("&", "\\T\\");
		return value;
	}
	public static void iterateStructures(Group g, Set<Structure> testStructures) {
		if (g == null) {
			return;
		}
		for (String name: g.getNames()) {
			try {
				for (Structure s: g.getAll(name)) {
					if (s instanceof Segment seg) {
						testStructures.add(seg);
					} else if (s instanceof Group group) {
						testStructures.add(s);
						iterateStructures(group, testStructures);
					}
				}
			} catch (HL7Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	public static void iterateSegments(Group g, Set<Segment> testSegments) {
		if (g == null) {
			return;
		}
		for (String name: g.getNames()) {
			try {
				for (Structure s: g.getAll(name)) {
					if (s instanceof Segment seg) {
						testSegments.add(seg);
					} else if (s instanceof Group group) {
						iterateSegments(group, testSegments);
					}
				}
			} catch (HL7Exception e) {
				e.printStackTrace();
			}
		}
	}

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
	
	public static Type getField(Segment segment, int field) {
		try {
			Type[] types = segment.getField(field); 
			return types == null || types.length == 0 ? null : types[0];
		} catch (HL7Exception e) {
			return null;
		} 
	}
	public static  boolean hasField(Segment segment, int field) {
		return getField(segment, field) != null; 
	}

	
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
	@SuppressWarnings("unchecked")
	private static Class<Segment> getSegmentClass(String segment) {
		String name = null;
		Exception saved = null;
		for (Class<?> errClass : Arrays.asList(ca.uhn.hl7v2.model.v281.segment.ERR.class, ca.uhn.hl7v2.model.v251.segment.ERR.class)) {
			name = errClass.getPackageName() +  segment;
			try {
				return (Class<Segment>) ClassLoader.getSystemClassLoader().loadClass(name);
			} catch (ClassNotFoundException e) {
				saved = e;
			}
		}
		warnException("Unexpected {} loading {}: {}", saved.getClass().getSimpleName(), segment, saved.getMessage(), saved);
		return null;
	}
	
	public static Segment getSegment(String segment) {
		Class<Segment> segClass = getSegmentClass(segment);
		if (segClass == null) {
			return null;
		}
		try {
			return segClass.getDeclaredConstructor().newInstance();
		} catch (InstantiationException | IllegalAccessException | IllegalArgumentException
				| InvocationTargetException | NoSuchMethodException | SecurityException e) {
			warnException("Unexpected {} loading {}", e.getClass().getSimpleName(), segClass.getName(), e);
			return null;
		}
	}
	public static Reference toReference(DomainResource resource) {
		IdType id = resource.getIdElement();
		if (id != null) {
			return new Reference(id.toString());
		}
		return null;
	}
	private static void warn(String msg, Object ...args) {
		log.warn(msg, args);
	}
	private static void warnException(String msg, Object ...args) {
		log.warn(msg, args);
	}
}
