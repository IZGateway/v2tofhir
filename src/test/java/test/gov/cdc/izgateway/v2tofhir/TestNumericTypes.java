package test.gov.cdc.izgateway.v2tofhir;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ServiceConfigurationError;
import java.util.function.Function;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.DecimalType;
import org.hl7.fhir.r4.model.IntegerType;
import org.hl7.fhir.r4.model.PositiveIntType;
import org.hl7.fhir.r4.model.PrimitiveType;
import org.hl7.fhir.r4.model.UnsignedIntType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import ca.uhn.hl7v2.model.DataTypeException;
import ca.uhn.hl7v2.model.Type;
import gov.cdc.izgw.v2tofhir.converter.DatatypeConverter;

class TestNumericTypes {
	private <P, T extends PrimitiveType<P>> void testNumbers(String inputString, BigDecimal low, BigDecimal high, Class<T> clazz, Function<Type, T> creator) throws DataTypeException {
		T expected = null;
		String expectedString = inputString.trim().replace("+", "").split("\\s+")[0];
		BigDecimal input = new BigDecimal(inputString.trim().split("\\s+")[0]);
		if (input.equals(BigDecimal.ZERO)) {
			expectedString = "0";  // Sign on 0 doesn't matter
		}
		if (input.compareTo(low) >= 0 && input.compareTo(high) <= 0) {
			// Input is within range.
			// Convert the plain string with FHIR
			try {
				expected = clazz.getDeclaredConstructor(String.class).newInstance(expectedString);
			} catch (InstantiationException | IllegalAccessException | IllegalArgumentException
					| InvocationTargetException | NoSuchMethodException | SecurityException e) {
				throw new ServiceConfigurationError("Could not create expected value", e);
			} catch (Exception e) {
				// Ignore FHIR creation failures, these are normal with invalid inputs, we'll check if expected == null later.
				expectedString = null;  // If FHIR create fails, expected string is null;
			}
			
			assertEquals(expectedString, expected.asStringValue()); // Verify FHIR String matches with expected String.

			MyPrimitive nm = new MyPrimitive(inputString);  // Create a V2 Numeric using the base for all V2 NM types
			T actual = creator.apply(nm);
			testFormattedInput(actual, expected, expectedString);
		}
			
	}

	private <P, T extends PrimitiveType<P>> void testFormattedInput(T actual, T expected, String expectedString) throws DataTypeException {
		if (actual != null) {
			actual.setValue(actual.getValue()); // Force renormalization of the String Value in FHIR
		}
		String actualString = actual == null ? null : actual.getValueAsString();
		if (expected != null) {  // FHIR Created something
			assertEquals(expected.getValue(), actual.getValue());
		}
		assertEquals(expectedString, actualString);
	}
	
	@ParameterizedTest
	@MethodSource("validNumbers")
	void testIntegers(String s) throws DataTypeException {
		testNumbers(s, MIN_INT, MAX_INT, IntegerType.class, DatatypeConverter::toIntegerType);
	}
	@ParameterizedTest
	@MethodSource("validNumbers")
	void testUnsignedInt(String s) throws DataTypeException {
		testNumbers(s, BigDecimal.ZERO, MAX_INT, UnsignedIntType.class, DatatypeConverter::toUnsignedIntType);
	}
	@ParameterizedTest
	@MethodSource("validNumbers")
	void testPositiveInt(String s) throws DataTypeException {
		testNumbers(s, BigDecimal.ONE, MAX_INT, PositiveIntType.class, DatatypeConverter::toPositiveIntType);
	}
	@ParameterizedTest
	@MethodSource("validNumbers")
	void testDecimalType(String s) throws DataTypeException {
		testNumbers(s, MIN_DECIMAL_MINUS_ONE, MAX_DECIMAL_PLUS_ONE, DecimalType.class, DatatypeConverter::toDecimalType);
	}
	
	private static final BigDecimal MAX_INT = BigDecimal.valueOf(Integer.MAX_VALUE);
	private static final BigDecimal MAX_INT_PLUS_ONE = MAX_INT.add(BigDecimal.ONE);
	private static final BigDecimal MIN_INT = BigDecimal.valueOf(Integer.MIN_VALUE);
	private static final BigDecimal MIN_INT_MINUS_ONE = MAX_INT.add(BigDecimal.ONE.negate());
	private static final BigDecimal MAX_LONG = BigDecimal.valueOf(Long.MAX_VALUE);
	private static final BigDecimal MAX_LONG_PLUS_ONE = MAX_LONG.add(BigDecimal.ONE);
	private static final BigDecimal MIN_LONG = BigDecimal.valueOf(Long.MIN_VALUE);
	private static final BigDecimal MIN_LONG_MINUS_ONE = MAX_LONG.add(BigDecimal.ONE.negate());
	private static final BigDecimal SOME_DECIMAL = new BigDecimal("999999999999999");
	private static final BigDecimal MAX_DECIMAL = new BigDecimal("9999999999999999");
	private static final BigDecimal MIN_DECIMAL = new BigDecimal("-999999999999999");
	private static final BigDecimal MAX_DECIMAL_PLUS_ONE = new BigDecimal("10000000000000000");
	private static final BigDecimal MIN_DECIMAL_MINUS_ONE = new BigDecimal("-1000000000000000");
	private static BigDecimal[] validNumbers = {
		BigDecimal.valueOf(0),
		BigDecimal.valueOf(1),
		MAX_INT,
		MAX_INT_PLUS_ONE,
		MIN_INT,
		MIN_INT_MINUS_ONE,
		MAX_LONG,
		MAX_LONG_PLUS_ONE,
		MIN_LONG,
		MIN_LONG_MINUS_ONE,
		SOME_DECIMAL,
		MAX_DECIMAL,
		MAX_DECIMAL_PLUS_ONE,
		MIN_DECIMAL,
		MIN_DECIMAL_MINUS_ONE
	};
	static List<String> validNumbers() {
		return Arrays.asList(validNumbers).stream().flatMap(s -> pad(s)).toList();
	}
	private static final String[] NUMBER_FORMATS = { " %s ", " %s", "%s ", "%s", " +%s ", " +%s", "+%s ", "+%s", " -%s ", " -%s", "-%s ", "-%s", " %s kg " };

	private static Stream<String> pad(BigDecimal n) {
		List<String> a = new ArrayList<>();
		for (String format: NUMBER_FORMATS) {
			String v = n.toPlainString();
			if (StringUtils.contains(v, "-") && StringUtils.containsAny(format, "+", "-")) {
				continue;
			}
			a.add(String.format(format, v));
		}
		return a.stream();
	}
}
