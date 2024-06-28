package test.gov.cdc.izgateway.v2tofhir;

import java.util.Set;

import org.hl7.fhir.r4.model.Address;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Type;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import ca.uhn.hl7v2.model.Composite;
import ca.uhn.hl7v2.model.DataTypeException;

class TestCompositeStringTypes extends BaseTest {

	protected <FT extends Type, V2 extends Composite> void testFhirType(FT expected, V2 input) {
		
	}

	@ParameterizedTest
	@MethodSource("getTestDataForAddress")
	void testAddress(Type addrType) throws DataTypeException {
	}
	
	@ParameterizedTest
	@MethodSource("getTestDataForCoding")
	void testCodings(Type codableType) throws DataTypeException {
	}
	
	@ParameterizedTest
	@MethodSource("getTestDataForIdentifier")
	void testIdentifiers(Type idType) throws DataTypeException {
	}
	
	@ParameterizedTest
	@MethodSource("getTestDataForName")
	void testHumanName(Type nameType) throws DataTypeException {
		
	}
	
	@ParameterizedTest
	@MethodSource("getTestDataForQuantity")
	void testQuantity(Type quantity) throws DataTypeException {
		
	}
	
}
