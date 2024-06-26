package test.gov.cdc.izgateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Collection;
import java.util.function.Function;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.PrimitiveType;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Segment;
import ca.uhn.hl7v2.model.Type;
import ca.uhn.hl7v2.model.Visitable;
import gov.cdc.izgw.v2tofhir.converter.ParserUtils;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AssertionUtils {
	private static final FhirContext ctx = FhirContext.forR4();
	private static final IParser fhirParser = ctx.newJsonParser().setPrettyPrint(true);
	public static void assertEqualStrings(String a, String b) {
		// If one is empty or blank, the other one must be.
		assertEquals(StringUtils.isBlank(a), StringUtils.isBlank(b), a + " <> '" + b + "'");
		// if we get here, both have values or both are empty
		if (StringUtils.isNotEmpty(a)) {
			// and now we can safely compare them.
			assertEquals(a, b);
		}
	}

	public static void assertNotEmpty(Collection<String> first) {
		assertNotNull(first);
		assertFalse(first.isEmpty());
	}

	public static int compare(
			org.hl7.fhir.r4.model.Resource a,
			org.hl7.fhir.r4.model.Resource b) {
		int comp = compareObjects(a, b);
		if (comp != 2) {
			return comp;
		}
		return StringUtils.compare(toString(a), toString(b));
	}

	public static int compare(
			org.hl7.fhir.r4.model.Type a,
			org.hl7.fhir.r4.model.Type b) {
		int comp = compareObjects(a, b);
		if (comp != 2) {
			return comp;
		}
		return StringUtils.compare(toString(a), toString(b));
	}

	public static int compare(Visitable a, Visitable b) {
		int comp = compareObjects(a, b);
		if (comp != 2) {
			return comp;
		}
		// Two Types are equal if they encode to the same string values.
		String a1;
		try {
			a1 = toString(a);
		} catch (HL7Exception e) {
			e.printStackTrace();
			return -1;
		}
		String b1;
		try {
			b1 = toString(b);
		} catch (HL7Exception e) {
			e.printStackTrace();
			return 1;
		}
		return StringUtils.compare(a1, b1);
	}

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

	public static void compareStringValues(
		Function<Type, PrimitiveType<?>> conversionFunction, 
		Function<String, PrimitiveType<?>> stringFunction,
		Type t
	) throws HL7Exception {
		Throwable fhirConversionFailure = null;
		PrimitiveType<?> fhirTypeFromString = null;
		if (t.encode() == null) {
			log.warn("t is null for ", t.getMessage().encode());
		}
		try {
			String value = ParserUtils.unescapeV2Chars(t.encode());
			Boolean cleanupNeeded = needsIsoCleanup(t);
			if (cleanupNeeded != null) {
				value = ParserUtils.cleanupIsoDateTime(value, cleanupNeeded);
			}
			fhirTypeFromString = stringFunction.apply(value);
			assertEquals(t.encode(), fhirTypeFromString.asStringValue());
		} catch (Exception ex) {
			fhirConversionFailure = ex;
		} catch (AssertionError err) {
			fhirConversionFailure = err;
		}
		PrimitiveType<?> fhirType = conversionFunction.apply(t);
		// In these cases, if we reproduce the encoded value, count it as a win.
		String encodedValue = null;
		boolean needsIntFix = false;
		if (fhirType != null) {
			switch (fhirType.fhirType()) {
			case "instant", "datetime", "date":
				encodedValue = fhirType.asStringValue();
				if (encodedValue != null) {
					encodedValue = encodedValue.replaceAll("[\\-T:]", "");
				}
				break;
			case "time":
				encodedValue = fhirType.asStringValue();
				if (encodedValue != null) {
					encodedValue = encodedValue.replace(":", "");
				}
				break;
			case "integer":
			case "long":
			case "positiveInt":
			case "unsignedInt":
				encodedValue = fhirType.asStringValue();
				needsIntFix = true;
				break;
			default:
				encodedValue = fhirType.asStringValue();
				break;
			}
		}
		
		if (fhirConversionFailure == null) {
			assertNotNull(fhirTypeFromString);
			// The values are the same.
			if (fhirType == null) {
				// TimeType has a bug in that it doesn't parse the actual value
				// in use.
				if (!"time".equals(fhirTypeFromString.fhirType())) {
					assertEqualStrings(null, fhirTypeFromString.asStringValue());
				}
			} else {
				assertEquals(isEmpty(fhirTypeFromString.getValue()), isEmpty(fhirType.getValue()),
					"'" + fhirTypeFromString.getValue() + "' <> '" + fhirType.getValue() + "'");
				if (!isEmpty(fhirTypeFromString.getValue())) {
					assertEquals(fhirTypeFromString.getValue(), fhirType.getValue());
				}
				// The types are the same.
				assertEquals(fhirTypeFromString.fhirType(), fhirType.fhirType());
			}
		} 
		
		if (fhirType != null) {
			if (!needsIntFix) {
				assertEqualStrings(ParserUtils.unescapeV2Chars(t.encode()), encodedValue);
			} else {
				// The string representations should be the same, except in special cases
				assertEqualStrings(intFix(t), encodedValue);
			}
		}  
	}

	/**
	 * To capture numbers from ST values, the v2 converter ignores anything after the numeric part
	 * of the message, and truncates decimals to integers where needed.
	 * 
	 * @param t	The field being extracted from.
	 * @return	The corrected value.
	 * @throws HL7Exception Unlikely, but if something went wrong in the parse.
	 */
	private static String intFix(Type t) throws HL7Exception {
		String value = ParserUtils.unescapeV2Chars(t.encode()).trim();
		value = value.split("\\s+")[0];
		value = StringUtils.substringBefore(value, ".");
		if (value.length() == 0) {
			value = "0";
		}
		
		return Integer.valueOf(value).toString();
	}

	private static boolean isEmpty(Object o) {
		return o == null || (o instanceof String s && StringUtils.isEmpty(s)); 
	}

	public static Boolean needsIsoCleanup(Type t) {
		String typeName = t.getName();
		switch (typeName) {
		case "TM": return Boolean.FALSE;
		case "DIN", "DLD", "DR", "DT", "DTM": 
			return Boolean.TRUE;
		default:
			return null;  // No cleanup needed
		}
	}

	public static String toString(org.hl7.fhir.r4.model.Resource a) {
		return fhirParser.encodeResourceToString(a);
	}

	public static String toString(org.hl7.fhir.r4.model.Type a) {
		return fhirParser.encodeToString(a);
	}

	public static String toString(Visitable a) throws HL7Exception {
		if (a instanceof Type t) {
			return t.encode();
		} else if (a instanceof Segment s) {
			return s.encode();
		}
		return null;
	}
}
