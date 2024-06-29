package test.gov.cdc.izgateway.v2tofhir;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import ca.uhn.hl7v2.model.DataTypeException;
import ca.uhn.hl7v2.model.Type;
import ca.uhn.hl7v2.model.Varies;
import gov.cdc.izgw.v2tofhir.converter.DatatypeConverter;
import gov.cdc.izgw.v2tofhir.converter.Mapping;
import gov.cdc.izgw.v2tofhir.converter.TextUtils;
import lombok.extern.slf4j.Slf4j;
import test.gov.cdc.izgateway.TestUtils;

@Slf4j
class CompositeStringTypeTests extends TestBase {

	protected <FT extends org.hl7.fhir.r4.model.Type, V2 extends Type> void testFhirType(FT actual, V2 input, boolean isTextComparable) {
		// Both are empty, or both have values.
		assertEquals(TestUtils.isEmpty(input), TestUtils.isEmpty(actual));
		String inputString = TextUtils.toString(input);
		String actualString = TextUtils.toString(actual);
		if (!isTextComparable) {
			return;
		}
		try {
			// Triming and capitalization don't matter in the string conversions
			assertEquals(StringUtils.trim(inputString).toUpperCase(), StringUtils.trim(actualString).toUpperCase());
		} catch (AssertionError err) {
			// Remove any converter added extra data.
			@SuppressWarnings("unchecked")
			FT save = actual == null ? null : (FT)actual.copy();  // Save the old data
			Mapping.reset(actual);
			actualString = TextUtils.toString(actual);
			assertEquals(StringUtils.trim(inputString).toUpperCase(), StringUtils.trim(actualString).toUpperCase());
			log.warn("Assertion Error: {}", err.getMessage());
		}
	}
	
	@Test
	void testTypeList() {
		Set<Type> data = getTestData(null);
		Set<String> typeNames = new TreeSet<>();
		for (Type datum : data) {
			String typeName = datum.getName();
			if (datum instanceof Varies) {
				typeName = "Varies " + typeName;
			}
			typeNames.add(typeName);
		}
		System.out.println(typeNames);
	}

	@ParameterizedTest
	@MethodSource("getTestDataForAddress")
	void testAddress(Type type) throws DataTypeException {
		testFhirType(DatatypeConverter.toAddress(type), type, IS_ADDR.contains(type.getName()));
	}
	
	@ParameterizedTest
	@MethodSource("getTestDataForCoding")
	void testCodings(Type type) throws DataTypeException {
		testFhirType(DatatypeConverter.toCodeableConcept(type), type, IS_CODING.contains(type.getName()));
	}
	
	@ParameterizedTest
	@MethodSource("getTestDataForIdentifier")
	void testIdentifiers(Type type) throws DataTypeException {
		testFhirType(DatatypeConverter.toIdentifier(type), type, IS_ID.contains(type.getName()));
	}
	
	@ParameterizedTest
	@MethodSource("getTestDataForName")
	void testHumanName(Type type) throws DataTypeException {
		testFhirType(DatatypeConverter.toHumanName(type), type, IS_NAME.contains(type.getName()));
	}
	
	@ParameterizedTest
	@MethodSource("getTestDataForQuantity")
	void testQuantity(Type type) throws DataTypeException {
		testFhirType(DatatypeConverter.toQuantity(type), type, IS_QUANTITY.contains(type.getName()));
	}
	
}
