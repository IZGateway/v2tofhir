package test.gov.cdc.izgateway.v2tofhir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.Address;
import org.hl7.fhir.r4.model.HumanName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import ca.uhn.hl7v2.model.DataTypeException;
import ca.uhn.hl7v2.model.Type;
import ca.uhn.hl7v2.model.Varies;
import gov.cdc.izgw.v2tofhir.converter.DatatypeConverter;
import gov.cdc.izgw.v2tofhir.datatype.AddressParser;
import gov.cdc.izgw.v2tofhir.datatype.HumanNameParser;
import gov.cdc.izgw.v2tofhir.utils.Mapping;
import gov.cdc.izgw.v2tofhir.utils.TextUtils;
import lombok.extern.slf4j.Slf4j;
import test.gov.cdc.izgateway.TestUtils;

@Slf4j
class CompositeStringTypeTests extends TestBase {
	static {
		log.debug("{} loaded", CompositeStringTypeTests.class.getName());
	}

	private static final AddressParser addressParser = new AddressParser();
	private static final HumanNameParser humanNameParser = new HumanNameParser();

	protected <FT extends org.hl7.fhir.r4.model.Type, V2 extends Type> void testFhirType(FT actual, V2 input, boolean isTextComparable) {
		String inputString = TextUtils.toString(input);
		String actualString = TextUtils.toString(actual);
		// Both are empty, or both have USEFUL values, where useful
		// is defined as generating text output on TextUtils.toString.
		assertEquals(
			TestUtils.isEmpty(input) || StringUtils.isEmpty(inputString), 
			TestUtils.isEmpty(actual) || StringUtils.isEmpty(actualString)
		);
		if (!isTextComparable) {
			return;
		}
		try {
			// Triming and capitalization don't matter in the string conversions
			assertEquals( // NOSONAR : Enable catch for debugging
				StringUtils.trim(inputString).toUpperCase(), 
				StringUtils.trim(actualString).toUpperCase()
			);
		} catch (AssertionError err) {
			// Remove any converter added extra data.
			@SuppressWarnings({ "unchecked", "unused" })
			FT save = actual == null ? null : (FT)actual.copy();  // Save the old data
			Mapping.reset(actual);
			actualString = TextUtils.toString(actual);
			assertEquals(StringUtils.trim(inputString).toUpperCase(), StringUtils.trim(actualString).toUpperCase());
			// log.warn("Assertion Error: {}", err.getMessage());
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
		log.info("{}", typeNames);
	}

	@ParameterizedTest
	@MethodSource("getTestDataForAddress")
	void testAddress(Type type) throws DataTypeException {
		Address addr = DatatypeConverter.toAddress(type);
		testFhirType(addr, type, IS_ADDR.contains(type.getName()));
		
		String text = TextUtils.toString(type);
		Address addrFromText = addressParser.fromString(text);
		
		if (addrFromText != null) {
			addrFromText.setText(null); // Remove text because addr will not have it.
			addr.setUse(null); // Remove use because addrFromText won't know it
			addr.setPeriod(null); // Remove period because addrFromText won't know it.
			assertEquals(TestUtils.toString(addr), TestUtils.toString(addrFromText));
		} else {
			assertNull(addr);
		}
	}
	
	@ParameterizedTest
	@MethodSource("getTestDataForCoding")
	void testCodings(Type type) throws DataTypeException {
		testFhirType(DatatypeConverter.toCodeableConcept(type), type, IS_CODING.contains(type.getName()));
	}
	
	@ParameterizedTest
	@MethodSource("getTestDataForContactPoint")
	void testContactPoint(Type type) throws DataTypeException {
		testFhirType(DatatypeConverter.toContactPoint(type), type, IS_CONTACT.contains(type.getName()));
	}
	
	@ParameterizedTest
	@MethodSource("getTestDataForIdentifier")
	void testIdentifiers(Type type) throws DataTypeException {
		testFhirType(DatatypeConverter.toIdentifier(type), type, IS_ID.contains(type.getName()));
	}
	
	@ParameterizedTest
	@MethodSource("getTestDataForName")
	void testHumanName(Type type) throws DataTypeException {
		HumanName hn = DatatypeConverter.toHumanName(type); 
		testFhirType(hn, type, IS_NAME.contains(type.getName()));
		String text = TextUtils.toString(type);
		HumanName hnFromText = humanNameParser.fromString(text);
		if (hnFromText != null) {
			hnFromText.setText(null); // Remove text because hn will not have it.
			hn.setUse(null); // Remove use because hnFromText won't know it
			hn.setPeriod(null); // Remove period because hnFromText won't know it.
			assertEquals(TestUtils.toString(hn), TestUtils.toString(hnFromText));
		} else {
			assertNull(hn);
		}
	}
	
	@ParameterizedTest
	@MethodSource("getTestDataForQuantity")
	void testQuantity(Type type) throws DataTypeException {
		testFhirType(DatatypeConverter.toQuantity(type), type, IS_QUANTITY.contains(type.getName()));
	}
	
}
