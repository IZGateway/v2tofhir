package test.gov.cdc.izgateway.v2tofhir;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.InvocationTargetException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ServiceConfigurationError;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.CodeType;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.PrimitiveType;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.UriType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import ca.uhn.hl7v2.model.DataTypeException;
import ca.uhn.hl7v2.model.Type;
import gov.cdc.izgw.v2tofhir.converter.DatatypeConverter;
import test.gov.cdc.izgateway.TestUtils;

class TestStringPrimitives {

	private <P, T extends PrimitiveType<P>> void testStrings(String inputString, UnaryOperator<String> normalize,
			Predicate<String> rangeTest, Class<T> clazz, Function<Type, T> creator) throws DataTypeException {
		T expected = null;
		String expectedString = normalize == null ? inputString : normalize.apply(inputString);
		String input = inputString;

		if (rangeTest == null || rangeTest.test(input)) {
			// Input is within range.
			// Convert the plain string with FHIR
			try {
				expected = clazz.getDeclaredConstructor(String.class).newInstance(expectedString);
			} catch (InstantiationException | IllegalAccessException | IllegalArgumentException
					| InvocationTargetException | NoSuchMethodException | SecurityException e) {
				throw new ServiceConfigurationError("Could not create expected value", e);
			} catch (Exception e) {
				// Ignore FHIR creation failures, these are normal with invalid inputs, we'll
				// check if expected == null later.
				expectedString = null; // If FHIR create fails, expected string is null;
			}
			if ((expected instanceof IdType) && StringUtils.isBlank(inputString)) {
				// IdType is normalized from blanks to null
				expectedString = null;
			}
			assertEquals(expectedString, expected.asStringValue()); // Verify FHIR String matches with expected String.

			MyPrimitive st = new MyPrimitive(inputString); // Create a V2 Numeric using the base for all V2 NM types
			T actual = creator.apply(st);
			testFormattedInput(actual, expected, expectedString);
		}

	}

	private <P, T extends PrimitiveType<P>> void testFormattedInput(T actual, T expected, String expectedString)
			throws DataTypeException {
		if (actual != null) {
			actual.setValue(actual.getValue()); // Force renormalization of the String Value in FHIR
		}
		String actualString = actual == null ? null : actual.getValueAsString();
		if (expected != null) { // FHIR Created something
			assertEquals(expected.getValue(), actual.getValue());
		}
		assertEquals(expectedString, actualString);
	}

	@ParameterizedTest
	@MethodSource("validStrings")
	void testStringType(String s) throws DataTypeException {
		testStrings(s, null, null, StringType.class, DatatypeConverter::toStringType);
	}

	@ParameterizedTest
	@MethodSource("validStrings")
	void testCodeType(String s) throws DataTypeException {
		testStrings(s, StringUtils::strip, null, CodeType.class, DatatypeConverter::toCodeType);
	}

	@ParameterizedTest
	@MethodSource("validStrings")
	void testIdType(String s) throws DataTypeException {
		testStrings(s, StringUtils::strip, null, IdType.class, DatatypeConverter::toIdType);
	}

	@ParameterizedTest
	@MethodSource("validStrings")
	void testUriType(String s) throws DataTypeException {
		testStrings(s, StringUtils::strip, null, UriType.class, DatatypeConverter::toUriType);
	}

	private final static String UNICODE_BOM = "\ufeff";
	// FWIW, these are all different encodings of the same identifier!
	private final static String AUUID = "0190531D-5F94-9202-FD85-4F207E7073BC";
	private final static String ANOID = "2.25." + new BigInteger(AUUID.replace("-", ""), 16).toString();
	private final static String AULID = "01J19HTQWMJ81FV1AF41Z70WXW";

	private static String[] validStrings = { "http://example.com/page#tag", // A URI
			"urn:uuid:" + AUUID, // Another URI
			"urn:oid:" + ANOID, // Another URI
			"urn:ulid" + AULID, // Another URI
			AUUID, // An ID in UUID format
			ANOID, // An ID in OID format
			AULID, // An ID in ULID format
			StringUtils.repeat("X", 80), // An 80 character string
			StringUtils.repeat("K", 1024), // A 1024 character string
			"\t some stuff with control characters and whitespace \n \r \f", UNICODE_BOM, // Just BOM
			UNICODE_BOM + "X", // A BOM followed by characters.
			"'" + UNICODE_BOM + "'", // A BOM in the middle of a string
			"\ud834\udd1e", // The Treble clef, which is Unicode surrogate pair.
			"\udd1e\ud834" // A bad encoding of the Treble clef, with high and low surrogate's reversed.
	};

	static List<String> validStrings() {
		List<String> l = Arrays.asList(validStrings).stream().flatMap(TestUtils::pad)
				.collect(Collectors.toCollection(ArrayList::new));
		l.add(null);
		l.add(" ");
		return l;
	}
}
