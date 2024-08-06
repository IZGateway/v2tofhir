package test.gov.cdc.izgateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.BaseDateTimeType;
import org.hl7.fhir.r4.model.CodeType;
import org.hl7.fhir.r4.model.PrimitiveType;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.UriType;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Segment;
import ca.uhn.hl7v2.model.Type;
import ca.uhn.hl7v2.model.Varies;
import ca.uhn.hl7v2.model.Visitable;
import gov.cdc.izgw.v2tofhir.utils.ParserUtils;
import lombok.extern.slf4j.Slf4j;

/**
 * A variety of utility functions for testing FHIR Conversions
 * 
 * @author Audacious Inquiry
 *
 */
@Slf4j
public class TestUtils {
	private static final String[] PAD_FORMATS = { " %s ", " %s", "%s ", "%s" };
	private static final FhirContext ctx = FhirContext.forR4();
	private static final IParser fhirParser = ctx.newJsonParser().setPrettyPrint(true);
	
	/** 
	 * Check two strings for equality, where null and blank are considered equivalent
	 * @param a	The first string to compare
	 * @param b	The second string to compare
	 */
	public static void assertEqualStrings(String a, String b) {
		// If one is empty or blank, the other one must be.
		assertEquals(StringUtils.isBlank(a), StringUtils.isBlank(b), a + " <> '" + b + "'");
		// if we get here, both have values or both are empty
		if (StringUtils.isNotEmpty(a)) {
			// and now we can safely compare them.
			assertEquals(a, b);
		}
	}

	/**
	 * Verify a collection has a value
	 * @param first	The collection
	 */
	public static void assertNotEmpty(Collection<String> first) {
		assertNotNull(first);
		assertFalse(first.isEmpty());
	}

	/**
	 * Compare two resources by their JSON representations.
	 * @param a	The first resource
	 * @param b	The second resource
	 * @return	The comparison
	 */
	public static int compare(
			org.hl7.fhir.r4.model.Resource a,
			org.hl7.fhir.r4.model.Resource b) {
		int comp = compareObjects(a, b);
		if (comp != 2) {
			return comp;
		}
		return StringUtils.compare(toString(a), toString(b));
	}

	/**
	 * Compare V2 types by their V2 encoded representations.
	 * 
	 * @param a	The first type
	 * @param b	The second type
	 * @return	The comparison
	 */
	public static int compare(
			org.hl7.fhir.r4.model.Type a,
			org.hl7.fhir.r4.model.Type b) {
		int comp = compareObjects(a, b);
		if (comp != 2) {
			return comp;
		}
		return StringUtils.compare(toString(a), toString(b));
	}

	/**
	 * Compare V2 structures (segments and groups) by their V2 encoded representations.
	 * Used to provide an ordered list of V2 structures in tests
	 * @param a	The first structures 
	 * @param b	The second structures 
	 * @return	The comparison
	 */
	public static int compare(Visitable a, Visitable b) {
		int comp = compareObjects(a, b);
		if (comp != 2) {
			return comp;
		}
		return a.toString().compareTo(b.toString());
	}

	
	/**
	 * First half of compare that checks for equality based
	 * only on class level information
	 * 
	 * @param a	The first object 
	 * @param b	The second object
	 * @return	The comparison value.  A value of 2 indicates the objects are non-null and of identical type
	 */
	public static int compareObjects(Object a, Object b) {
		if (a == null && b == null) {
			return 0;
		}
		if (a == null) {
			return -1;
		}
		if (b == null) {
			return 1;
		}
		int comp = StringUtils.compare(a.getClass().getName(), b.getClass().getName());
		if (comp != 0) {
			return comp;
		}
		return 2;
	}

	/**
	 * Serves as a basis for tests on primitive type conversions
	 * 
	 * @param <P>	A primitive Type (e.g., String, Integer, Boolean, etc)
	 * @param <F>	The FHIR representation of the primitive type
	 * @param v2Converter	The converter from a V2 type to a FHIR PrimitiveType 
	 * @param toFhirFromString	A function to convert from a string to a FHIR type
	 * @param normalizer	A normalizer to apply to the string
	 * @param t	A V2 type to convert
	 * @param expectedClass	The expected FHIR Type class
	 * @throws HL7Exception If an HL7 exception occurs
	 */
	public static <P, F extends org.hl7.fhir.r4.model.PrimitiveType<P>> void compareStringValues(
		Function<Type, F> v2Converter, 
		Function<String, F> toFhirFromString,
		UnaryOperator<String> normalizer,
		Type t,
		Class<F> expectedClass
	) throws HL7Exception {
		Throwable fhirConversionFailure = null;
		F expectedFhirType = null;
		String expectedFhirString = null;
		if (normalizer == null) {
			normalizer = s -> s;
		}
		if (t.encode() == null) {
			log.warn("t is null for ", t.getMessage().encode());
		}
		boolean isDateTime = BaseDateTimeType.class.isAssignableFrom(expectedClass);
		try {
			String value = ParserUtils.toString(t);
			
			if (isDateTime) {
				Boolean cleanupNeeded = ParserUtils.needsIsoCleanup(t);
				if (cleanupNeeded != null) {
					value = ParserUtils.cleanupIsoDateTime(value, cleanupNeeded);
				}
			} else if (UriType.class.equals(expectedClass) || CodeType.class.equals(expectedClass)) {
				value = StringUtils.trim(value);
			} 
			expectedFhirType = toFhirFromString.apply(normalizer.apply(value));
			expectedFhirType.setValue(expectedFhirType.getValue());  // Force renormalization of String
			if (expectedFhirType != null) {
				expectedFhirString = expectedFhirType.asStringValue();
			}
		} catch (Exception ex) {
			fhirConversionFailure = ex;
		} catch (AssertionError err) {
			fhirConversionFailure = err;
		}
		PrimitiveType<?> actualFhirType = v2Converter.apply(t);
		// In these cases, if we reproduce the encoded value, count it as a win.
		String actualString = (actualFhirType != null) ? actualFhirType.asStringValue() : null;
		
		if (fhirConversionFailure != null) {
			// See if we matched the input string value
			return;
		}
		assertNotNull(expectedFhirType);

		// TimeType has a bug in that it doesn't parse the actual value in use.
		if (!"time".equals(expectedFhirType.fhirType())) {
			assertEqualStrings(expectedFhirType.asStringValue(), actualString);
		}
		
		// The values are the same.
		if (actualFhirType != null) {
			// Types are the same
			assertEquals(expectedFhirType.fhirType(), actualFhirType.fhirType());
			// Classes are the same
			assertEquals(expectedClass, actualFhirType.getClass());
			// String representations are essentially the same
			assertEquals(isEmpty(actualString), isEmpty(expectedFhirString), 
				"'" + actualString + "' <> '" + expectedFhirString + "'");
			if (StringUtils.isNotEmpty(actualString)) {
				assertEquals(expectedFhirString, actualString, 
					"'" + expectedFhirString  + "' <> '" + actualString + "'");
			}
		}
	}

	/**
	 * Generic isEmpty for String, FHIR Type, Resource, V2 type or Collection
	 * @param o	The object to check for emptiness
	 * @return true if the object is null or otherwise empty
	 */
	public static boolean isEmpty(Object o) {
		try {
			return o == null || 
				(o instanceof String s && StringUtils.isEmpty(s)) ||
				(o instanceof org.hl7.fhir.r4.model.Type t && t.isEmpty()) || 
				(o instanceof Resource r && r.isEmpty()) || 
				(o instanceof Type t && t.isEmpty() ||
				(o instanceof Collection<?> c && c.isEmpty())		
			);
		} catch (HL7Exception e) {
			return true;
		} 
	}

	/**
	 * Convert a resource to a JSON String
	 * @param r	The resource
	 * @return	The JSON String
	 */
	public static String toString(org.hl7.fhir.r4.model.Resource r) {
		return r == null ? null : fhirParser.encodeResourceToString(r);
	}

	/**
	 * Convert a FHIR type to a JSON String
	 * @param t	The type
	 * @return	The JSON String
	 */
	public static String toString(org.hl7.fhir.r4.model.Type t) {
		return t == null ? null : fhirParser.encodeToString(t);
	}

	/**
	 * Convert a V2 Structure or Type to its encoded String
	 * @param a	The Structure or Type 
	 * @return	The V2 encoded String
	 * @throws HL7Exception If an HL7 exception occurs during encoding
	 */
	public static String toString(Visitable a) throws HL7Exception {
		if (a instanceof Varies v) {
			a = v.getData();
		}
		if (a instanceof Type t) {
			return t.encode();
		} else if (a instanceof Segment s) {
			return s.encode();
		}
		return null;
	}

	/**
	 * Given a single object, represent it as a string with different string paddings (left, right, both and none).
	 * @param v	The object to represent (uses toString()).  This method supports producing strings with varying padding.
	 * @return The stream of padded strings.
	 */
	public static Stream<String> pad(Object v) {
		return format(v, null, PAD_FORMATS);
	}

	/**
	 * Format an object with multiple formatters. This method support the pad function above, and providing other formatting
	 * for test data generators.
	 * 
	 * @param <T> The type of value
	 * @param v	The object to format
	 * @param test The test applied to the object and format before performing the formatting
	 * @param formats	The format strings to use
	 * @return The stream of formatted objects.
	 */
	public static <T> Stream<String> format(T v, BiPredicate<T, String> test, String ... formats) {
		List<String> a = new ArrayList<>();
		for (String format: formats) {
			if (test != null && !test.test(v, format)) {
				continue;
			}
			a.add(String.format(format, v));
		}
		return a.stream();
	}
}
