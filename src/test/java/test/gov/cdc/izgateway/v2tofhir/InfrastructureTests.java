package test.gov.cdc.izgateway.v2tofhir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.hl7.fhir.r4.model.Coding;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import ca.uhn.hl7v2.model.Type;
import gov.cdc.izgw.v2tofhir.converter.Systems;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class InfrastructureTests extends TestBase {
	static {
		log.debug("{} loaded", InfrastructureTests.class.getName());
	}
	@ParameterizedTest
	@MethodSource("getCodeSystemAliases")
	void testCodeSystemAliases(List<String> aliases) {
		String mapsTo = aliases.get(0);
		for (String alias: aliases) {
			Coding coding = new Coding(alias, null, null);
			if (coding.getUserData("originalSystem") != null) {
				// It was mapped
				assertEquals(mapsTo, coding.getSystem());
			}
		}
	}
	static List<List<String>> getCodeSystemAliases() {
		return Systems.getCodeSystemAliases();
	}
	
	private static String[][] V2TYPES = {
		{ "AD", "ADDRESS"}, 
		{ "AUI", "AUTHORIZATION INFORMATION"}, 
		{ "CCD", "CHARGE CODE AND DATE"}, 
		{ "CCP", "CHANNEL CALIBRATION PARAMETERS"}, 
		{ "CD", "CHANNEL DEFINITION"}, 
		{ "CE", "CODED ELEMENT"}, 
		{ "CF", "CODED ELEMENT WITH FORMATTED VALUES"}, 
		{ "CNE", "CODED WITH NO EXCEPTIONS"}, 
		{ "CNN", "COMPOSITE ID NUMBER AND NAME SIMPLIFIED"}, 
		{ "CP", "COMPOSITE PRICE"}, 
		{ "CQ", "COMPOSITE QUANTITY WITH UNITS"}, 
		{ "CSU", "CHANNEL SENSITIVITY"}, 
		{ "CWE", "CODED WITH EXCEPTIONS"}, 
		{ "CX", "EXTENDED COMPOSITE ID WITH CHECK DIGIT"}, 
		{ "DDI", "DAILY DEDUCTIBLE INFORMATION"}, 
		{ "DIN", "DATE AND INSTITUTION NAME"}, 
		{ "DLD", "DISCHARGE TO LOCATION AND DATE"}, 
		{ "DLN", "DRIVER’S LICENSE NUMBER"}, 
		{ "DLT", "DELTA"}, 
		{ "DR", "DATE/TIME RANGE"}, 
		{ "DT", "DATE"}, 
		{ "DTM", "DATE/TIME"}, 
		{ "DTN", "DAY TYPE AND NUMBER"}, 
		{ "ED", "ENCAPSULATED DATA"}, 
		{ "EI", "ENTITY IDENTIFIER"}, 
		{ "EIP", "ENTITY IDENTIFIER PAIR"}, 
		{ "ELD", "ERROR LOCATION AND DESCRIPTION"}, 
		{ "ERL", "ERROR LOCATION"}, 
		{ "FC", "FINANCIAL CLASS"}, 
		{ "FN", "FAMILY NAME"}, 
		{ "FT", "FORMATTED TEXT DATA"}, 
		{ "GTS", "GENERAL TIMING SPECIFICATION"}, 
		{ "HD", "HIERARCHIC DESIGNATOR"}, 
		{ "ICD", "INSURANCE CERTIFICATION DEFINITION"}, 
		{ "ID", "CODED VALUE FOR HL7 DEFINED TABLES"}, 
		{ "IS", "CODED VALUE FOR USER DEFINES TABLES"}, 
		{ "JCC", "JOB CODE/CLASS"}, 
		{ "LA1", "LOCATION WITH ADDRESS VARIATION 1"}, 
		{ "LA2", "LOCATION WITH ADDRESS VARIATION 2"}, 
		{ "MA", "MULTIPLEXED ARRAY"}, 
		{ "MO", "MONEY"}, 
		{ "MOC", "MONEY AND CHARGE CODE"}, 
		{ "MOP", "MONEY OR PERCENTAGE"}, 
		{ "MSG", "MESSAGE TYPE"}, 
		{ "NA", "NUMERIC ARRAY"}, 
		{ "NDL", ""}, 
		{ "NM", "NUMERIC"}, 
		{ "NR", "NUMERIC RANGE"}, 
		{ "OCD", "OCCURRENCE CODE AND DATE"}, 
		{ "OSD", "ORDER SEQUENCE DEFINITION"}, 
		{ "OSP", "OCCURRENCE SPAN CODE AND DATE"}, 
		{ "PIP", "PRACTITIONER INSTITUTIONAL PRIVILEGES"}, 
		{ "PL", "PERSON LOCATION"}, 
		{ "PLN", "PRACTITIONER LICENSE OR OTHER ID NUMBER"}, 
		{ "PPN", "PERFORMING PERSON TIME STAMP"}, 
		{ "PRL", "PARENT RESULT LINK"}, 
		{ "PT", "PROCESSING TYPE"}, 
		{ "PTA", "POLICY TYPE AND AMOUNT"}, 
		{ "QIP", "QUERY INPUT PARAMETER LIST"}, 
		{ "QSC", "QUERY SELECTION CRITERIA"}, 
		{ "RCD", "ROW COLUMN DEFINITION"}, 
		{ "RFR", "REFERENCE RANGE"}, 
		{ "RI", "REPEAT INTERVAL"}, 
		{ "RMC", "ROOM COVERAGE"}, 
		{ "RP", "REFERENCE POINTER"}, 
		{ "RPT", "REPEAT PATTERN"}, 
		{ "SAD", "STREET ADDRESS"}, 
		{ "SCV", "SCHEDULING CLASS VALUE PAIR"}, 
		{ "SI", "SEQUENCE ID"}, 
		{ "SN", "STRUCTURED NUMERIC"}, 
		{ "SPD", "SPECIALTY DESCRIPTION"}, 
		{ "SPS", "SPECIMEN SOURCE"}, 
		{ "SRT", "SORT ORDER"}, 
		{ "ST", "STRING DATA"}, 
		{ "TM", "TIME"}, 
		{ "TN", "TELEPHONE NUMBER"}, 
		{ "TQ", "TIMING QUANTITY"}, 
		{ "TS", "TIME STAMP"}, 
		{ "TX", "TEXT DATA"}, 
		{ "UVC", "UB VALUE CODE AND AMOUNT"}, 
		{ "VH", "VISITING HOURS"}, 
		{ "VID", "VERSION IDENTIFIER"}, 
		{ "VR", "VALUE RANGE"}, 
		{ "WVI", "CHANNEL IDENTIFIER"}, 
		{ "WVS", "WAVEFORM SOURCE"}, 
		{ "XAD", "EXTENDED ADDRESS"}, 
		{ "XCN", "EXTENDED COMPOSITE ID NUMBER AND NAME FOR PERSONS"}, 
		{ "XON", "EXTENDED COMPOSITE NAME AND IDENTIFICATION NUMBER FOR ORGANIZATIONS"}, 
		{ "XPN", "EXTENDED PERSON NAME"}, 
		{ "XTN", "EXTENDED TELECOMMUNICATION NUMBER"} 
	};
	
	private static final Set<String> TEST_DATATYPES_AVAILABLE = getTypesInUse();
	
	private static Set<String> getTypesInUse() {
		Set<String> typesInUse = new LinkedHashSet<>();
		for (Type field: TestBase.getTestData(null)) {
			typesInUse.add(field.getName());
		}
		return typesInUse;
	}
	private static List<String[]> getAllV2Types() {
		List<String[]> l = new ArrayList<>(V2TYPES.length);
		for (String[] a : V2TYPES) {
			l.add(a);
		}
		return l;
	}
	
	@ParameterizedTest
	@MethodSource("getAllV2Types")
	@Disabled("Run this test to see what kind of test data is available from messages")
	void testTypeList(String v2Type, String v2Name) {
		assertTrue(TEST_DATATYPES_AVAILABLE.contains(v2Type),
			"V2 " + v2Type + " - " + v2Name + " is not found in test data"
			);
	}
}
