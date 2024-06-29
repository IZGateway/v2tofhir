package test.gov.cdc.izgateway.v2tofhir;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Composite;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.model.Primitive;
import ca.uhn.hl7v2.model.Segment;
import ca.uhn.hl7v2.model.Type;
import ca.uhn.hl7v2.model.Varies;
import ca.uhn.hl7v2.parser.Parser;
import ca.uhn.hl7v2.parser.PipeParser;
import gov.cdc.izgw.v2tofhir.converter.ParserUtils;
import test.gov.cdc.izgateway.TestUtils;
import test.gov.cdc.izgateway.v2tofhir.ConverterTest.NamedSegment;

public class TestBase {
	static final Parser v2Parser = new PipeParser();

	static void explodeComposite(Composite comp, Set<Type> set) {
		for (Type part : comp.getComponents()) {
			if (part instanceof Varies v) {
				part = v.getData();
			}
			if (part instanceof Primitive) {
				set.add(part);
			} else if (part instanceof Composite comp2) {
				explodeComposite(comp2, set);
			}
		}
	}

	// These are defined constants which indicate which V2 types should be used for
	// testing.
	final static List<String> CODING_TYPES = Arrays.asList("CE", "CWE", "CNE");
	final static List<String> ID_TYPES = Arrays.asList("CX", "EI", "CNN", "XCN", "XON");
	final static List<String> NAME_TYPES = Arrays.asList("CNN", "XCN", "XPN");
	final static List<String> ADDR_TYPES = Arrays.asList("AD", "SAD", "XAD");
	final static List<String> QUANTITY_TYPES = Arrays.asList("CQ", "NM");

	// These are defined constants which indicate which V2 types should match when
	// converted to text.
	final static List<String> IS_CODING = CODING_TYPES;
	final static List<String> IS_ID = Arrays.asList("CX", "EI");
	final static List<String> IS_NAME = NAME_TYPES;
	final static List<String> IS_ADDR = ADDR_TYPES;
	final static List<String> IS_QUANTITY = QUANTITY_TYPES;

	static Set<Type> getTestDataForCoding() {
		return getTestData(t -> CODING_TYPES.contains(t.getName()));
	}

	static Set<Type> getTestDataForIdentifier() {
		return getTestData(t -> ID_TYPES.contains(t.getName())
				&& (!"XCN".equals(t.getName()) || !StringUtils.startsWith(encode(t), "^")));
	}

	static String encode(Type t) {
		try {
			return t.encode();
		} catch (HL7Exception e) {
			return null;
		}
	}

	static Set<Type> getTestDataForName() {
		return getTestData(t -> NAME_TYPES.contains(t.getName()));
	}

	static Set<Type> getTestDataForAddress() {
		return getTestData(t -> ADDR_TYPES.contains(t.getName()));
	}

	static Set<Type> getTestDataForQuantity() {
		return getTestData(t -> QUANTITY_TYPES.contains(t.getName()));
	}

	static Set<Type> getTestData(Predicate<Type> test) {
		Set<Type> types = getTestFields(null);
		Set<Type> s = new TreeSet<>((Type t, Type u) -> StringUtils.compare(t.toString(), u.toString()));
		addTestData(test, types, s);
		return s;
	}

	static Set<Type> getTestPrimitives() {
		return getTestData(t -> t instanceof Primitive || (t instanceof Varies v && v.getData() instanceof Primitive));
	}

	static Set<Type> getTestComposites() {
		return getTestData(t -> t instanceof Composite || (t instanceof Varies v && v.getData() instanceof Composite));
	}

	private static void addTestData(Predicate<Type> test, Iterable<Type> types, Set<Type> s) {
		if (test == null) {
			test = t -> true;
		}
		for (Type type : types) {
			if (s.contains(type)) {
				continue;
			}
			if (test.test(type)) {
				s.add(type);
			}
			if (type instanceof Varies v) {
				addTestData(test, Collections.singleton(v.getData()), s);
			} else if (type instanceof Composite comp) {
				// Explode the component and add any matching subcomponents
				addTestData(test, Arrays.asList(comp.getComponents()), s);
			}
		}
	}

	static Set<Type> getTestFields(Predicate<Type> test) {
		Set<Type> testFields = new TreeSet<Type>(TestUtils::compare);
		for (NamedSegment segment : getTestSegments()) {
			for (int i = 1; i <= segment.segment().numFields(); i++) {
				Type[] fields;
				try {
					fields = segment.segment().getField(i);
				} catch (HL7Exception e) {
					e.printStackTrace();
					continue;
				}
				for (Type type : fields) {
					if (test == null || test.test(type)) {
						testFields.add(type);
					}
				}
			}
		}
		return testFields;
	}

	static List<NamedSegment> getTestSegments() {
		Set<Segment> testSegments = new TreeSet<Segment>(TestUtils::compare);
		for (Message msg : testV2Messages().toList()) {
			ParserUtils.iterateSegments(msg, testSegments);
		}
		return testSegments.stream().map(s -> new NamedSegment(s)).toList();
	}

	static Message parse(String message) {
		try {
			return v2Parser.parse(message);
		} catch (HL7Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	static List<String> testMessages() {
		return Arrays.asList(TEST_MESSAGES);
	}

	static Stream<Message> testV2Messages() {
		return testMessages().stream().map(TestBase::parse);
	}
	
	private static final String[] TEST_MESSAGES = {
			"MSH|^~\\&|TEST|MOCK|IZGW|IZGW|20240618083101-0400||RSP^K11^RSP_K11|RESULT-01|P|2.5.1|||ER|AL|||||Z32^CDCPHINVS\r"
					+ "MSA|AA|QUERY-01\r" + "QAK|QUERY-01|OK|Z34^Request Immunization History^CDCPHINVS\r"
					+ "ERR|||0^Message Accepted^HL70357|I||||3 of 3 immunizations have been added to IIS\r"
					+ "QPD|Z34^Request Immunization History^CDCPHINVS|QUERY-01|234814^^^MYEHR^MR\r"
					+ "PID|1||234814^^^MYEHR^MR||FagenAIRA^TheodoricAIRA^ElbertAIRA^^^^L|FagenAIRA^TheodoricAIRA^^^^^M|19631226|M||ASIAN|1517 Huth Ave^^Wyndmere^ND^58081^^L||{{Telephone Number}}|||||||||not HISPANIC\r"
					+ "NK1|1|FagenAIRA^TheodoricAIRA^^^^^L|MTH^Mom^HL70063|1517 Huth Ave^^Wyndmere^ND^58081^^L\r"
					+ "ORC|RE||65930^DCS||||||20120113|^FagenAIRA^TheodoricAIRA||^FagenAIRA^TheodoricAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\r"
					+ "RXA|0|1|20210516||208^208^CVX|0.3|ml||01^historical^NIP001|||||||||||CP|A\r"
					+ "RXR|C28161^IM^NCIT^IM^^HL70162|RT^Right Thigh^HL70163\r"
					+ "OBX|1|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\r"
					+ "OBX|2|DT|29769-7^VIS presented^LN|2|20120113||||||F\r"
					+ "OBX|3|CE|69764-9 ^Document type^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F \r"
					+ "ORC|RE||65949^DCS||||||20120113|^FagenAIRA^TheodoricAIRA||^FagenAIRA^TheodoricAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\r"
					+ "OBX|4|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\r"
					+ "OBX|5|DT|29769-7^VIS presented^LN|2|20120113||||||F\r"
					+ "OBX|6|CE|69764-9^Eligibility Status^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F\r",
			"MSH|^~\\&|TEST|MOCK|IZGW|IZGW|20240618083102-0400||RSP^K11^RSP_K11|RESULT-02|P|2.5.1|||ER|AL|||||Z32^CDCPHINVS\r"
					+ "MSA|AA|QUERY-02\r" + "QAK|QUERY-02|OK|Z34^Request Immunization History^CDCPHINVS\r"
					+ "ERR||PD1^^16|101^Required field missing^HL70357|W||||patient immunization registry status is missing|\r"				+ "QPD|Z34^Request Immunization History^CDCPHINVS|QUERY-02|234815^^^MYEHR^MR\r"
					+ "PID|1||234815^^^MYEHR^MR||FagenAIRA^ChristosAIRA^DemarionAIRA^^^^L|FagenAIRA^ChristosAIRA^^^^^M|20040315|M||ASIAN|1606 Ealfsen St^^Bismarck^ND^58501^^L||{{Telephone Number}}|||||||||not HISPANIC\r"
					+ "NK1|1|FagenAIRA^ChristosAIRA^^^^^L|MTH^Mom^HL70063|1606 Ealfsen St^^Bismarck^ND^58501^^L\r"
					+ "ORC|RE||65930^DCS||||||20120113|^FagenAIRA^ChristosAIRA||^FagenAIRA^ChristosAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\r"
					+ "RXA|0|1|20040315||08^08^CVX|.05|ml||01^historical^NIP001|||||||||||CP|A\r"
					+ "RXR|C28161^IM^NCIT^IM^^HL70162|RT^Right Thigh^HL70163\r"
					+ "OBX|1|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\r"
					+ "OBX|2|DT|29769-7^VIS presented^LN|2|20120113||||||F\r"
					+ "OBX|3|CE|69764-9 ^Document type^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F \r"
					+ "ORC|RE||65949^DCS||||||20120113|^FagenAIRA^ChristosAIRA||^FagenAIRA^ChristosAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\r"
					+ "OBX|4|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\r"
					+ "OBX|5|DT|29769-7^VIS presented^LN|2|20120113||||||F\r"
					+ "OBX|6|CE|69764-9^Eligibility Status^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F\r"
					+ "ORC|RE||65930^DCS||||||20120113|^FagenAIRA^ChristosAIRA||^FagenAIRA^ChristosAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\r"
					+ "RXA|0|1|20040515||08^08^CVX|.05|ml||01^historical^NIP001|||||||||||CP|A\r"
					+ "RXR|C28161^IM^NCIT^IM^^HL70162|RT^Right Thigh^HL70163\r"
					+ "OBX|1|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\r"
					+ "OBX|2|DT|29769-7^VIS presented^LN|2|20120113||||||F\r"
					+ "OBX|3|CE|69764-9 ^Document type^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F \r"
					+ "ORC|RE||65949^DCS||||||20120113|^FagenAIRA^ChristosAIRA||^FagenAIRA^ChristosAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\r"
					+ "OBX|4|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\r"
					+ "OBX|5|DT|29769-7^VIS presented^LN|2|20120113||||||F\r"
					+ "OBX|6|CE|69764-9^Eligibility Status^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F\r"
					+ "ORC|RE||65930^DCS||||||20120113|^FagenAIRA^ChristosAIRA||^FagenAIRA^ChristosAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\r"
					+ "RXA|0|1|20040515||116^116^CVX||ml||01^historical^NIP001|||||||||||CP|A\r"
					+ "RXR|C28161^IM^NCIT^IM^^HL70162|RT^Right Thigh^HL70163\r"
					+ "OBX|1|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\r"
					+ "OBX|2|DT|29769-7^VIS presented^LN|2|20120113||||||F\r"
					+ "OBX|3|CE|69764-9 ^Document type^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F \r"
					+ "ORC|RE||65949^DCS||||||20120113|^FagenAIRA^ChristosAIRA||^FagenAIRA^ChristosAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\r"
					+ "OBX|4|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\r"
					+ "OBX|5|DT|29769-7^VIS presented^LN|2|20120113||||||F\r"
					+ "OBX|6|CE|69764-9^Eligibility Status^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F\r"
					+ "ORC|RE||65930^DCS||||||20120113|^FagenAIRA^ChristosAIRA||^FagenAIRA^ChristosAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\r"
					+ "RXA|0|1|20040515||106^106^CVX||ml||01^historical^NIP001|||||||||||CP|A\r"
					+ "RXR|C28161^IM^NCIT^IM^^HL70162|RT^Right Thigh^HL70163\r"
					+ "OBX|1|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\r"
					+ "OBX|2|DT|29769-7^VIS presented^LN|2|20120113||||||F\r"
					+ "OBX|3|CE|69764-9 ^Document type^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F \r"
					+ "ORC|RE||65949^DCS||||||20120113|^FagenAIRA^ChristosAIRA||^FagenAIRA^ChristosAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\r"
					+ "OBX|4|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\r"
					+ "OBX|5|DT|29769-7^VIS presented^LN|2|20120113||||||F\r"
					+ "OBX|6|CE|69764-9^Eligibility Status^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F\r"
					+ "ORC|RE||65930^DCS||||||20120113|^FagenAIRA^ChristosAIRA||^FagenAIRA^ChristosAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\r"
					+ "RXA|0|1|20040515||49^49^CVX||ml||01^historical^NIP001|||||||||||CP|A\r"
					+ "RXR|C28161^IM^NCIT^IM^^HL70162|RT^Right Thigh^HL70163\r"
					+ "OBX|1|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\r"
					+ "OBX|2|DT|29769-7^VIS presented^LN|2|20120113||||||F\r"
					+ "OBX|3|CE|69764-9 ^Document type^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F \r"
					+ "ORC|RE||65949^DCS||||||20120113|^FagenAIRA^ChristosAIRA||^FagenAIRA^ChristosAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\r"
					+ "OBX|4|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\r"
					+ "OBX|5|DT|29769-7^VIS presented^LN|2|20120113||||||F\r"
					+ "OBX|6|CE|69764-9^Eligibility Status^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F\r"
					+ "ORC|RE||65930^DCS||||||20120113|^FagenAIRA^ChristosAIRA||^FagenAIRA^ChristosAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\r"
					+ "RXA|0|1|20040515||10^10^CVX||ml||01^historical^NIP001|||||||||||CP|A\r"
					+ "RXR|C28161^IM^NCIT^IM^^HL70162|RT^Right Thigh^HL70163\r"
					+ "OBX|1|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\r"
					+ "OBX|2|DT|29769-7^VIS presented^LN|2|20120113||||||F\r"
					+ "OBX|3|CE|69764-9 ^Document type^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F \r"
					+ "ORC|RE||65949^DCS||||||20120113|^FagenAIRA^ChristosAIRA||^FagenAIRA^ChristosAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\r"
					+ "OBX|4|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\r"
					+ "OBX|5|DT|29769-7^VIS presented^LN|2|20120113||||||F\r"
					+ "OBX|6|CE|69764-9^Eligibility Status^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F\r"
					+ "ORC|RE||65930^DCS||||||20120113|^FagenAIRA^ChristosAIRA||^FagenAIRA^ChristosAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\r"
					+ "RXA|0|1|20040715||116^116^CVX||ml||01^historical^NIP001|||||||||||CP|A\r"
					+ "RXR|C28161^IM^NCIT^IM^^HL70162|RT^Right Thigh^HL70163\r"
					+ "OBX|1|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\r"
					+ "OBX|2|DT|29769-7^VIS presented^LN|2|20120113||||||F\r"
					+ "OBX|3|CE|69764-9 ^Document type^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F \r"
					+ "ORC|RE||65949^DCS||||||20120113|^FagenAIRA^ChristosAIRA||^FagenAIRA^ChristosAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\r"
					+ "OBX|4|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\r"
					+ "OBX|5|DT|29769-7^VIS presented^LN|2|20120113||||||F\r"
					+ "OBX|6|CE|69764-9^Eligibility Status^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F\r"
					+ "ORC|RE||65930^DCS||||||20120113|^FagenAIRA^ChristosAIRA||^FagenAIRA^ChristosAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\r"
					+ "RXA|0|1|20040715||106^106^CVX||ml||01^historical^NIP001|||||||||||CP|A\r"
					+ "RXR|C28161^IM^NCIT^IM^^HL70162|RT^Right Thigh^HL70163\r"
					+ "OBX|1|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\r"
					+ "OBX|2|DT|29769-7^VIS presented^LN|2|20120113||||||F\r"
					+ "OBX|3|CE|69764-9 ^Document type^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F \r"
					+ "ORC|RE||65949^DCS||||||20120113|^FagenAIRA^ChristosAIRA||^FagenAIRA^ChristosAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\r"
					+ "OBX|4|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\r"
					+ "OBX|5|DT|29769-7^VIS presented^LN|2|20120113||||||F\r"
					+ "OBX|6|CE|69764-9^Eligibility Status^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F\r"
					+ "ORC|RE||65930^DCS||||||20120113|^FagenAIRA^ChristosAIRA||^FagenAIRA^ChristosAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\r"
					+ "RXA|0|1|20040715||49^49^CVX||ml||01^historical^NIP001|||||||||||CP|A\r"
					+ "RXR|C28161^IM^NCIT^IM^^HL70162|RT^Right Thigh^HL70163\r"
					+ "OBX|1|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\r"
					+ "OBX|2|DT|29769-7^VIS presented^LN|2|20120113||||||F\r"
					+ "OBX|3|CE|69764-9 ^Document type^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F \r"
					+ "ORC|RE||65949^DCS||||||20120113|^FagenAIRA^ChristosAIRA||^FagenAIRA^ChristosAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\r"
					+ "OBX|4|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\r"
					+ "OBX|5|DT|29769-7^VIS presented^LN|2|20120113||||||F\r"
					+ "OBX|6|CE|69764-9^Eligibility Status^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F\r"
					+ "ORC|RE||65930^DCS||||||20120113|^FagenAIRA^ChristosAIRA||^FagenAIRA^ChristosAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\r"
					+ "RXA|0|1|20040715||10^10^CVX||ml||01^historical^NIP001|||||||||||CP|A\r"
					+ "RXR|C28161^IM^NCIT^IM^^HL70162|RT^Right Thigh^HL70163\r"
					+ "OBX|1|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\r"
					+ "OBX|2|DT|29769-7^VIS presented^LN|2|20120113||||||F\r"
					+ "OBX|3|CE|69764-9 ^Document type^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F \r"
					+ "ORC|RE||65949^DCS||||||20120113|^FagenAIRA^ChristosAIRA||^FagenAIRA^ChristosAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\r"
					+ "OBX|4|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\r"
					+ "OBX|5|DT|29769-7^VIS presented^LN|2|20120113||||||F\r"
					+ "OBX|6|CE|69764-9^Eligibility Status^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F\r"
					+ "ORC|RE||65930^DCS||||||20120113|^FagenAIRA^ChristosAIRA||^FagenAIRA^ChristosAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\r"
					+ "RXA|0|1|20040915||08^08^CVX|.05|ml||01^historical^NIP001|||||||||||CP|A\r"
					+ "RXR|C28161^IM^NCIT^IM^^HL70162|RT^Right Thigh^HL70163\r"
					+ "OBX|1|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\r"
					+ "OBX|2|DT|29769-7^VIS presented^LN|2|20120113||||||F\r"
					+ "OBX|3|CE|69764-9 ^Document type^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F \r"
					+ "ORC|RE||65949^DCS||||||20120113|^FagenAIRA^ChristosAIRA||^FagenAIRA^ChristosAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\r"
					+ "OBX|4|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\r"
					+ "OBX|5|DT|29769-7^VIS presented^LN|2|20120113||||||F\r"
					+ "OBX|6|CE|69764-9^Eligibility Status^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F\r"
					+ "ORC|RE||65930^DCS||||||20120113|^FagenAIRA^ChristosAIRA||^FagenAIRA^ChristosAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\r"
					+ "RXA|0|1|20040915||116^116^CVX||ml||01^historical^NIP001|||||||||||CP|A\r"
					+ "RXR|C28161^IM^NCIT^IM^^HL70162|RT^Right Thigh^HL70163\r"
					+ "OBX|1|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\r"
					+ "OBX|2|DT|29769-7^VIS presented^LN|2|20120113||||||F\r"
					+ "OBX|3|CE|69764-9 ^Document type^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F \r"
					+ "ORC|RE||65949^DCS||||||20120113|^FagenAIRA^ChristosAIRA||^FagenAIRA^ChristosAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\r"
					+ "OBX|4|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\r"
					+ "OBX|5|DT|29769-7^VIS presented^LN|2|20120113||||||F\r"
					+ "OBX|6|CE|69764-9^Eligibility Status^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F\r"
					+ "ORC|RE||65930^DCS||||||20120113|^FagenAIRA^ChristosAIRA||^FagenAIRA^ChristosAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\r"
					+ "RXA|0|1|20040915||106^106^CVX||ml||01^historical^NIP001|||||||||||CP|A\r"
					+ "RXR|C28161^IM^NCIT^IM^^HL70162|RT^Right Thigh^HL70163\r"
					+ "OBX|1|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\r"
					+ "OBX|2|DT|29769-7^VIS presented^LN|2|20120113||||||F\r"
					+ "OBX|3|CE|69764-9 ^Document type^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F \r"
					+ "ORC|RE||65949^DCS||||||20120113|^FagenAIRA^ChristosAIRA||^FagenAIRA^ChristosAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\r"
					+ "OBX|4|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\r"
					+ "OBX|5|DT|29769-7^VIS presented^LN|2|20120113||||||F\r"
					+ "OBX|6|CE|69764-9^Eligibility Status^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F\r"
					+ "ORC|RE||65930^DCS||||||20120113|^FagenAIRA^ChristosAIRA||^FagenAIRA^ChristosAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\r"
					+ "RXA|0|1|20040915||10^10^CVX||ml||01^historical^NIP001|||||||||||CP|A\r"
					+ "RXR|C28161^IM^NCIT^IM^^HL70162|RT^Right Thigh^HL70163\r"
					+ "OBX|1|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\r"
					+ "OBX|2|DT|29769-7^VIS presented^LN|2|20120113||||||F\r"
					+ "OBX|3|CE|69764-9 ^Document type^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F \r"
					+ "ORC|RE||65949^DCS||||||20120113|^FagenAIRA^ChristosAIRA||^FagenAIRA^ChristosAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\r"
					+ "OBX|4|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\r"
					+ "OBX|5|DT|29769-7^VIS presented^LN|2|20120113||||||F\r"
					+ "OBX|6|CE|69764-9^Eligibility Status^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F\r"
					+ "ORC|RE||65930^DCS||||||20120113|^FagenAIRA^ChristosAIRA||^FagenAIRA^ChristosAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\r"
					+ "RXA|0|1|20050315||03^03^CVX|0.5|ml||01^historical^NIP001|||||||||||CP|A\r"
					+ "RXR|C28161^IM^NCIT^IM^^HL70162|RT^Right Thigh^HL70163\r"
					+ "OBX|1|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\r"
					+ "OBX|2|DT|29769-7^VIS presented^LN|2|20120113||||||F\r"
					+ "OBX|3|CE|69764-9 ^Document type^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F \r"
					+ "ORC|RE||65949^DCS||||||20120113|^FagenAIRA^ChristosAIRA||^FagenAIRA^ChristosAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\r"
					+ "OBX|4|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\r"
					+ "OBX|5|DT|29769-7^VIS presented^LN|2|20120113||||||F\r"
					+ "OBX|6|CE|69764-9^Eligibility Status^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F\r"
					+ "ORC|RE||65930^DCS||||||20120113|^FagenAIRA^ChristosAIRA||^FagenAIRA^ChristosAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\r"
					+ "RXA|0|1|20050315||21^21^CVX||ml||01^historical^NIP001|||||||||||CP|A\r"
					+ "RXR|C28161^IM^NCIT^IM^^HL70162|RT^Right Thigh^HL70163\r"
					+ "OBX|1|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\r"
					+ "OBX|2|DT|29769-7^VIS presented^LN|2|20120113||||||F\r"
					+ "OBX|3|CE|69764-9 ^Document type^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F \r"
					+ "ORC|RE||65949^DCS||||||20120113|^FagenAIRA^ChristosAIRA||^FagenAIRA^ChristosAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\r"
					+ "OBX|4|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\r"
					+ "OBX|5|DT|29769-7^VIS presented^LN|2|20120113||||||F\r"
					+ "OBX|6|CE|69764-9^Eligibility Status^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F\r"
					+ "ORC|RE||65930^DCS||||||20120113|^FagenAIRA^ChristosAIRA||^FagenAIRA^ChristosAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\r"
					+ "RXA|0|1|20050315||83^83^CVX||ml||01^historical^NIP001|||||||||||CP|A\r"
					+ "RXR|C28161^IM^NCIT^IM^^HL70162|RT^Right Thigh^HL70163\r"
					+ "OBX|1|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\r"
					+ "OBX|2|DT|29769-7^VIS presented^LN|2|20120113||||||F\r"
					+ "OBX|3|CE|69764-9 ^Document type^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F \r"
					+ "ORC|RE||65949^DCS||||||20120113|^FagenAIRA^ChristosAIRA||^FagenAIRA^ChristosAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\r"
					+ "OBX|4|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\r"
					+ "OBX|5|DT|29769-7^VIS presented^LN|2|20120113||||||F\r"
					+ "OBX|6|CE|69764-9^Eligibility Status^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F\r"
					+ "ORC|RE||65930^DCS||||||20120113|^FagenAIRA^ChristosAIRA||^FagenAIRA^ChristosAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\r"
					+ "RXA|0|1|20050615||106^106^CVX||ml||01^historical^NIP001|||||||||||CP|A\r"
					+ "RXR|C28161^IM^NCIT^IM^^HL70162|RT^Right Thigh^HL70163\r"
					+ "OBX|1|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\r"
					+ "OBX|2|DT|29769-7^VIS presented^LN|2|20120113||||||F\r"
					+ "OBX|3|CE|69764-9 ^Document type^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F \r"
					+ "ORC|RE||65949^DCS||||||20120113|^FagenAIRA^ChristosAIRA||^FagenAIRA^ChristosAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\r"
					+ "OBX|4|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\r"
					+ "OBX|5|DT|29769-7^VIS presented^LN|2|20120113||||||F\r"
					+ "OBX|6|CE|69764-9^Eligibility Status^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F\r"
					+ "ORC|RE||65930^DCS||||||20120113|^FagenAIRA^ChristosAIRA||^FagenAIRA^ChristosAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\r"
					+ "RXA|0|1|20050615||49^49^CVX||ml||01^historical^NIP001|||||||||||CP|A\r"
					+ "RXR|C28161^IM^NCIT^IM^^HL70162|RT^Right Thigh^HL70163\r"
					+ "OBX|1|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\r"
					+ "OBX|2|DT|29769-7^VIS presented^LN|2|20120113||||||F\r"
					+ "OBX|3|CE|69764-9 ^Document type^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F \r"
					+ "ORC|RE||65949^DCS||||||20120113|^FagenAIRA^ChristosAIRA||^FagenAIRA^ChristosAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\r"
					+ "OBX|4|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\r"
					+ "OBX|5|DT|29769-7^VIS presented^LN|2|20120113||||||F\r"
					+ "OBX|6|CE|69764-9^Eligibility Status^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F\r",
			"MSH|^~\\&|TEST|MOCK|IZGW|IZGW|20240618083103-0400||RSP^K11^RSP_K11|RESULT-03|P|2.5.1|||ER|AL|||||Z32^CDCPHINVS\r"
					+ "MSA|AA|QUERY-03\r" + "QAK|QUERY-03|OK|Z34^Request Immunization History^CDCPHINVS\r"
					+ "ERR||PID^1^24|101^Required field missing^HL70357|W||||patient multiple birth indicator is missing|\r"
					+ "QPD|Z34^Request Immunization History^CDCPHINVS|QUERY-03|234816^^^MYEHR^MR\r"
					+ "PID|1||234816^^^MYEHR^MR||FagenAIRA^SophoclesAIRA^JerrieAIRA^^^^L|FagenAIRA^SophoclesAIRA^^^^^M|19760128|M||ASIAN|1760 Ve Marne Ln^^Fargo^ND^58104^^L||{{Telephone Number}}|||||||||not HISPANIC\r"
					+ "NK1|1|FagenAIRA^SophoclesAIRA^^^^^L|MTH^Mom^HL70063|1760 Ve Marne Ln^^Fargo^ND^58104^^L\r"
					+ "ORC|RE||65930^DCS||||||20120113|^FagenAIRA^SophoclesAIRA||^FagenAIRA^SophoclesAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\r"
					+ "RXA|0|1|20210429||208^208^CVX|0.3|ml||01^historical^NIP001|||||||||||CP|A\r"
					+ "RXR|C28161^IM^NCIT^IM^^HL70162|RT^Right Thigh^HL70163\r"
					+ "OBX|1|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\r"
					+ "OBX|2|DT|29769-7^VIS presented^LN|2|20120113||||||F\r"
					+ "OBX|3|CE|69764-9 ^Document type^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F \r"
					+ "ORC|RE||65949^DCS||||||20120113|^FagenAIRA^SophoclesAIRA||^FagenAIRA^SophoclesAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\r"
					+ "OBX|4|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\r"
					+ "OBX|5|DT|29769-7^VIS presented^LN|2|20120113||||||F\r"
					+ "OBX|6|CE|69764-9^Eligibility Status^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F\r"
					+ "ORC|RE||65930^DCS||||||20120113|^FagenAIRA^SophoclesAIRA||^FagenAIRA^SophoclesAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\r"
					+ "RXA|0|1|20210531||208^208^CVX|0.3|ml||01^historical^NIP001|||||||||||CP|A\r"
					+ "RXR|C28161^IM^NCIT^IM^^HL70162|RT^Right Thigh^HL70163\r"
					+ "OBX|1|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\r"
					+ "OBX|2|DT|29769-7^VIS presented^LN|2|20120113||||||F\r"
					+ "OBX|3|CE|69764-9 ^Document type^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F \r"
					+ "ORC|RE||65949^DCS||||||20120113|^FagenAIRA^SophoclesAIRA||^FagenAIRA^SophoclesAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\r"
					+ "OBX|4|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\r"
					+ "OBX|5|DT|29769-7^VIS presented^LN|2|20120113||||||F\r"
					+ "OBX|6|CE|69764-9^Eligibility Status^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F\r",
			"MSH|^~\\&|TEST|MOCK|IZGW|IZGW|20240618083104-0400||RSP^K11^RSP_K11|RESULT-04|P|2.5.1|||ER|AL|||||Z32^CDCPHINVS\r"
					+ "MSA|AA|QUERY-04\r" + "QAK|QUERY-04|OK|Z34^Request Immunization History^CDCPHINVS\r"
					+ "ERR||PID^1^7|101^required field missing^HL70357|E||||Birth Date is required.\r"
					+ "QPD|Z34^Request Immunization History^CDCPHINVS|QUERY-04|234817^^^MYEHR^MR\r"
					+ "PID|1||234817^^^MYEHR^MR||NuckollsAIRA^NurhanAIRA^InceAIRA^^^^L|NuckollsAIRA^NurhanAIRA^^^^^M|19521007|M||ASIAN|1154 Laadhoeke St^^Grand Forks^ND^58201^^L||{{Telephone Number}}|||||||||not HISPANIC\r"
					+ "NK1|1|NuckollsAIRA^NurhanAIRA^^^^^L|MTH^Mom^HL70063|1154 Laadhoeke St^^Grand Forks^ND^58201^^L\r"
					+ "ORC|RE||65930^DCS||||||20120113|^NuckollsAIRA^NurhanAIRA||^NuckollsAIRA^NurhanAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\r"
					+ "RXA|0|1|20210710||208^208^CVX|0.3|ml||01^historical^NIP001|||||||||||CP|A\r"
					+ "RXR|C28161^IM^NCIT^IM^^HL70162|RT^Right Thigh^HL70163\r"
					+ "OBX|1|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\r"
					+ "OBX|2|DT|29769-7^VIS presented^LN|2|20120113||||||F\r"
					+ "OBX|3|CE|69764-9 ^Document type^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F \r"
					+ "ORC|RE||65949^DCS||||||20120113|^NuckollsAIRA^NurhanAIRA||^NuckollsAIRA^NurhanAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\r"
					+ "OBX|4|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\r"
					+ "OBX|5|DT|29769-7^VIS presented^LN|2|20120113||||||F\r"
					+ "OBX|6|CE|69764-9^Eligibility Status^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F\r"
					+ "ORC|RE||65930^DCS||||||20120113|^NuckollsAIRA^NurhanAIRA||^NuckollsAIRA^NurhanAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\r"
					+ "RXA|0|1|20210730||208^208^CVX|0.3|ml||01^historical^NIP001|||||||||||CP|A\r"
					+ "RXR|C28161^IM^NCIT^IM^^HL70162|RT^Right Thigh^HL70163\r"
					+ "OBX|1|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\r"
					+ "OBX|2|DT|29769-7^VIS presented^LN|2|20120113||||||F\r"
					+ "OBX|3|CE|69764-9 ^Document type^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F \r"
					+ "ORC|RE||65949^DCS||||||20120113|^NuckollsAIRA^NurhanAIRA||^NuckollsAIRA^NurhanAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\r"
					+ "OBX|4|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\r"
					+ "OBX|5|DT|29769-7^VIS presented^LN|2|20120113||||||F\r"
					+ "OBX|6|CE|69764-9^Eligibility Status^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F\r",
			"MSH|^~\\&|TEST|MOCK|IZGW|IZGW|20240618083105-0400||RSP^K11^RSP_K11|RESULT-05|P|2.5.1|||ER|AL|||||Z32^CDCPHINVS\r"
					+ "MSA|AA|QUERY-05\r" + "QAK|QUERY-05|OK|Z34^Request Immunization History^CDCPHINVS\r"
					+ "QPD|Z34^Request Immunization History^CDCPHINVS|QUERY-05|234818^^^MYEHR^MR\r"
					+ "PID|1||234818^^^MYEHR^MR||LaurelAIRA^ZechariahAIRA^KenverAIRA^^^^L|LaurelAIRA^ZechariahAIRA^^^^^M|19970705|M||ASIAN|1964 Aapelle Cir^^Fargo^ND^58104^^L||{{Telephone Number}}|||||||||not HISPANIC\r"
					+ "NK1|1|LaurelAIRA^ZechariahAIRA^^^^^L|MTH^Mom^HL70063|1964 Aapelle Cir^^Fargo^ND^58104^^L\r"
					+ "ORC|RE||65930^DCS||||||20120113|^LaurelAIRA^ZechariahAIRA||^LaurelAIRA^ZechariahAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\r"
					+ "RXA|0|1|20210510||208^208^CVX|0.3|ml||01^historical^NIP001|||||||||||CP|A\r"
					+ "RXR|C28161^IM^NCIT^IM^^HL70162|RT^Right Thigh^HL70163\r"
					+ "OBX|1|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\r"
					+ "OBX|2|DT|29769-7^VIS presented^LN|2|20120113||||||F\r"
					+ "OBX|3|CE|69764-9 ^Document type^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F \r"
					+ "ORC|RE||65949^DCS||||||20120113|^LaurelAIRA^ZechariahAIRA||^LaurelAIRA^ZechariahAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\r"
					+ "OBX|4|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\r"
					+ "OBX|5|DT|29769-7^VIS presented^LN|2|20120113||||||F\r"
					+ "OBX|6|CE|69764-9^Eligibility Status^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F\r"
					+ "ORC|RE||65930^DCS||||||20120113|^LaurelAIRA^ZechariahAIRA||^LaurelAIRA^ZechariahAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\r"
					+ "RXA|0|1|20210611||208^208^CVX|0.3|ml||01^historical^NIP001|||||||||||CP|A\r"
					+ "RXR|C28161^IM^NCIT^IM^^HL70162|RT^Right Thigh^HL70163\r"
					+ "OBX|1|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\r"
					+ "OBX|2|DT|29769-7^VIS presented^LN|2|20120113||||||F\r"
					+ "OBX|3|CE|69764-9 ^Document type^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F \r"
					+ "ORC|RE||65949^DCS||||||20120113|^LaurelAIRA^ZechariahAIRA||^LaurelAIRA^ZechariahAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\r"
					+ "OBX|4|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\r"
					+ "OBX|5|DT|29769-7^VIS presented^LN|2|20120113||||||F\r"
					+ "OBX|6|CE|69764-9^Eligibility Status^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F\r",
			"MSH|^~\\&|TEST|MOCK|IZGW|IZGW|20240618083106-0400||RSP^K11^RSP_K11|RESULT-06|P|2.5.1|||ER|AL|||||Z32^CDCPHINVS\r"
					+ "MSA|AA|QUERY-06\r" + "QAK|QUERY-06|OK|Z34^Request Immunization History^CDCPHINVS\r"
					+ "ERR||MSH^1^11|202^Unsupported Processing ID^HL70357|E||||The only Processing ID used to submit HL7 messages to the IIS is \"P\". Debug mode cannot be used. - Message Rejected|\r" 
					+ "QPD|Z34^Request Immunization History^CDCPHINVS|QUERY-06|234819^^^MYEHR^MR\r"
					+ "PID|1||234819^^^MYEHR^MR||NavarroAIRA^ZaylinAIRA^DonteAIRA^^^^L|NavarroAIRA^ZaylinAIRA^^^^^M|20100628|F||ASIAN|1719 Zuren Pl^^Kindred^ND^58051^^L||{{Telephone Number}}|||||||||not HISPANIC\r"
					+ "NK1|1|NavarroAIRA^ZaylinAIRA^^^^^L|MTH^Mom^HL70063|1719 Zuren Pl^^Kindred^ND^58051^^L\r"
					+ "ORC|RE||65930^DCS||||||20120113|^NavarroAIRA^ZaylinAIRA||^NavarroAIRA^ZaylinAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\r"
					+ "RXA|0|1|20100628||08^08^CVX|.05|ml||01^historical^NIP001|||||||||||CP|A\r"
					+ "RXR|C28161^IM^NCIT^IM^^HL70162|RT^Right Thigh^HL70163\r"
					+ "OBX|1|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\r"
					+ "OBX|2|DT|29769-7^VIS presented^LN|2|20120113||||||F\r"
					+ "OBX|3|CE|69764-9 ^Document type^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F \r"
					+ "ORC|RE||65949^DCS||||||20120113|^NavarroAIRA^ZaylinAIRA||^NavarroAIRA^ZaylinAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\r"
					+ "OBX|4|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\r"
					+ "OBX|5|DT|29769-7^VIS presented^LN|2|20120113||||||F\r"
					+ "OBX|6|CE|69764-9^Eligibility Status^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F\r"
					+ "ORC|RE||65930^DCS||||||20120113|^NavarroAIRA^ZaylinAIRA||^NavarroAIRA^ZaylinAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\r"
					+ "RXA|0|1|20100828||08^08^CVX|.05|ml||01^historical^NIP001|||||||||||CP|A\r"
					+ "RXR|C28161^IM^NCIT^IM^^HL70162|RT^Right Thigh^HL70163\r"
					+ "OBX|1|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\r"
					+ "OBX|2|DT|29769-7^VIS presented^LN|2|20120113||||||F\r"
					+ "OBX|3|CE|69764-9 ^Document type^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F \r"
					+ "ORC|RE||65949^DCS||||||20120113|^NavarroAIRA^ZaylinAIRA||^NavarroAIRA^ZaylinAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\r"
					+ "OBX|4|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\r"
					+ "OBX|5|DT|29769-7^VIS presented^LN|2|20120113||||||F\r"
					+ "OBX|6|CE|69764-9^Eligibility Status^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F\r"
					+ "ORC|RE||65930^DCS||||||20120113|^NavarroAIRA^ZaylinAIRA||^NavarroAIRA^ZaylinAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\r"
					+ "RXA|0|1|20100828||116^116^CVX||ml||01^historical^NIP001|||||||||||CP|A\r"
					+ "RXR|C28161^IM^NCIT^IM^^HL70162|RT^Right Thigh^HL70163\r"
					+ "OBX|1|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\r"
					+ "OBX|2|DT|29769-7^VIS presented^LN|2|20120113||||||F\r"
					+ "OBX|3|CE|69764-9 ^Document type^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F \r"
					+ "ORC|RE||65949^DCS||||||20120113|^NavarroAIRA^ZaylinAIRA||^NavarroAIRA^ZaylinAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\r"
					+ "OBX|4|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\r"
					+ "OBX|5|DT|29769-7^VIS presented^LN|2|20120113||||||F\r"
					+ "OBX|6|CE|69764-9^Eligibility Status^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F\r"
					+ "ORC|RE||65930^DCS||||||20120113|^NavarroAIRA^ZaylinAIRA||^NavarroAIRA^ZaylinAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\r"
					+ "RXA|0|1|20100828||106^106^CVX||ml||01^historical^NIP001|||||||||||CP|A\r"
					+ "RXR|C28161^IM^NCIT^IM^^HL70162|RT^Right Thigh^HL70163\r"
					+ "OBX|1|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\r"
					+ "OBX|2|DT|29769-7^VIS presented^LN|2|20120113||||||F\r"
					+ "OBX|3|CE|69764-9 ^Document type^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F \r"
					+ "ORC|RE||65949^DCS||||||20120113|^NavarroAIRA^ZaylinAIRA||^NavarroAIRA^ZaylinAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\r"
					+ "OBX|4|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\r"
					+ "OBX|5|DT|29769-7^VIS presented^LN|2|20120113||||||F\r"
					+ "OBX|6|CE|69764-9^Eligibility Status^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F\r"
					+ "ORC|RE||65930^DCS||||||20120113|^NavarroAIRA^ZaylinAIRA||^NavarroAIRA^ZaylinAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\r"
					+ "RXA|0|1|20100828||49^49^CVX||ml||01^historical^NIP001|||||||||||CP|A\r"
					+ "RXR|C28161^IM^NCIT^IM^^HL70162|RT^Right Thigh^HL70163\r"
					+ "OBX|1|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\r"
					+ "OBX|2|DT|29769-7^VIS presented^LN|2|20120113||||||F\r"
					+ "OBX|3|CE|69764-9 ^Document type^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F \r"
					+ "ORC|RE||65949^DCS||||||20120113|^NavarroAIRA^ZaylinAIRA||^NavarroAIRA^ZaylinAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\r"
					+ "OBX|4|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\r"
					+ "OBX|5|DT|29769-7^VIS presented^LN|2|20120113||||||F\r"
					+ "OBX|6|CE|69764-9^Eligibility Status^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F\r"
					+ "ORC|RE||65930^DCS||||||20120113|^NavarroAIRA^ZaylinAIRA||^NavarroAIRA^ZaylinAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\r"
					+ "RXA|0|1|20100828||10^10^CVX||ml||01^historical^NIP001|||||||||||CP|A\r"
					+ "RXR|C28161^IM^NCIT^IM^^HL70162|RT^Right Thigh^HL70163\r"
					+ "OBX|1|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\r"
					+ "OBX|2|DT|29769-7^VIS presented^LN|2|20120113||||||F\r"
					+ "OBX|3|CE|69764-9 ^Document type^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F \r"
					+ "ORC|RE||65949^DCS||||||20120113|^NavarroAIRA^ZaylinAIRA||^NavarroAIRA^ZaylinAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\r"
					+ "OBX|4|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\r"
					+ "OBX|5|DT|29769-7^VIS presented^LN|2|20120113||||||F\r"
					+ "OBX|6|CE|69764-9^Eligibility Status^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F\r"
					+ "ORC|RE||65930^DCS||||||20120113|^NavarroAIRA^ZaylinAIRA||^NavarroAIRA^ZaylinAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\r"
					+ "RXA|0|1|20101028||116^116^CVX||ml||01^historical^NIP001|||||||||||CP|A\r"
					+ "RXR|C28161^IM^NCIT^IM^^HL70162|RT^Right Thigh^HL70163\r"
					+ "OBX|1|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\r"
					+ "OBX|2|DT|29769-7^VIS presented^LN|2|20120113||||||F\r"
					+ "OBX|3|CE|69764-9 ^Document type^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F \r"
					+ "ORC|RE||65949^DCS||||||20120113|^NavarroAIRA^ZaylinAIRA||^NavarroAIRA^ZaylinAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\r"
					+ "OBX|4|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\r"
					+ "OBX|5|DT|29769-7^VIS presented^LN|2|20120113||||||F\r"
					+ "OBX|6|CE|69764-9^Eligibility Status^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F\r"
					+ "ORC|RE||65930^DCS||||||20120113|^NavarroAIRA^ZaylinAIRA||^NavarroAIRA^ZaylinAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\r"
					+ "RXA|0|1|20101028||106^106^CVX||ml||01^historical^NIP001|||||||||||CP|A\r"
					+ "RXR|C28161^IM^NCIT^IM^^HL70162|RT^Right Thigh^HL70163\r"
					+ "OBX|1|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\r"
					+ "OBX|2|DT|29769-7^VIS presented^LN|2|20120113||||||F\r"
					+ "OBX|3|CE|69764-9 ^Document type^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F \r"
					+ "ORC|RE||65949^DCS||||||20120113|^NavarroAIRA^ZaylinAIRA||^NavarroAIRA^ZaylinAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\r"
					+ "OBX|4|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\r"
					+ "OBX|5|DT|29769-7^VIS presented^LN|2|20120113||||||F\r"
					+ "OBX|6|CE|69764-9^Eligibility Status^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F\r"
					+ "ORC|RE||65930^DCS||||||20120113|^NavarroAIRA^ZaylinAIRA||^NavarroAIRA^ZaylinAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\r"
					+ "RXA|0|1|20101028||49^49^CVX||ml||01^historical^NIP001|||||||||||CP|A\r"
					+ "RXR|C28161^IM^NCIT^IM^^HL70162|RT^Right Thigh^HL70163\r"
					+ "OBX|1|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\r"
					+ "OBX|2|DT|29769-7^VIS presented^LN|2|20120113||||||F\r"
					+ "OBX|3|CE|69764-9 ^Document type^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F \r"
					+ "ORC|RE||65949^DCS||||||20120113|^NavarroAIRA^ZaylinAIRA||^NavarroAIRA^ZaylinAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\r"
					+ "OBX|4|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\r"
					+ "OBX|5|DT|29769-7^VIS presented^LN|2|20120113||||||F\r"
					+ "OBX|6|CE|69764-9^Eligibility Status^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F\r"
					+ "ORC|RE||65930^DCS||||||20120113|^NavarroAIRA^ZaylinAIRA||^NavarroAIRA^ZaylinAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\r"
					+ "RXA|0|1|20101028||10^10^CVX||ml||01^historical^NIP001|||||||||||CP|A\r"
					+ "RXR|C28161^IM^NCIT^IM^^HL70162|RT^Right Thigh^HL70163\r"
					+ "OBX|1|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\r"
					+ "OBX|2|DT|29769-7^VIS presented^LN|2|20120113||||||F\r"
					+ "OBX|3|CE|69764-9 ^Document type^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F \r"
					+ "ORC|RE||65949^DCS||||||20120113|^NavarroAIRA^ZaylinAIRA||^NavarroAIRA^ZaylinAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\r"
					+ "OBX|4|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\r"
					+ "OBX|5|DT|29769-7^VIS presented^LN|2|20120113||||||F\r"
					+ "OBX|6|CE|69764-9^Eligibility Status^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F\r"
					+ "ORC|RE||65930^DCS||||||20120113|^NavarroAIRA^ZaylinAIRA||^NavarroAIRA^ZaylinAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\r"
					+ "RXA|0|1|20101228||08^08^CVX|.05|ml||01^historical^NIP001|||||||||||CP|A\r"
					+ "RXR|C28161^IM^NCIT^IM^^HL70162|RT^Right Thigh^HL70163\r"
					+ "OBX|1|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\r"
					+ "OBX|2|DT|29769-7^VIS presented^LN|2|20120113||||||F\r"
					+ "OBX|3|CE|69764-9 ^Document type^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F \r"
					+ "ORC|RE||65949^DCS||||||20120113|^NavarroAIRA^ZaylinAIRA||^NavarroAIRA^ZaylinAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\r"
					+ "OBX|4|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\r"
					+ "OBX|5|DT|29769-7^VIS presented^LN|2|20120113||||||F\r"
					+ "OBX|6|CE|69764-9^Eligibility Status^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F\r"
					+ "ORC|RE||65930^DCS||||||20120113|^NavarroAIRA^ZaylinAIRA||^NavarroAIRA^ZaylinAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\r"
					+ "RXA|0|1|20101228||116^116^CVX||ml||01^historical^NIP001|||||||||||CP|A\r"
					+ "RXR|C28161^IM^NCIT^IM^^HL70162|RT^Right Thigh^HL70163\r"
					+ "OBX|1|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\r"
					+ "OBX|2|DT|29769-7^VIS presented^LN|2|20120113||||||F\r"
					+ "OBX|3|CE|69764-9 ^Document type^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F \r"
					+ "ORC|RE||65949^DCS||||||20120113|^NavarroAIRA^ZaylinAIRA||^NavarroAIRA^ZaylinAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\r"
					+ "OBX|4|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\r"
					+ "OBX|5|DT|29769-7^VIS presented^LN|2|20120113||||||F\r"
					+ "OBX|6|CE|69764-9^Eligibility Status^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F\r"
					+ "ORC|RE||65930^DCS||||||20120113|^NavarroAIRA^ZaylinAIRA||^NavarroAIRA^ZaylinAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\r"
					+ "RXA|0|1|20101228||106^106^CVX||ml||01^historical^NIP001|||||||||||CP|A\r"
					+ "RXR|C28161^IM^NCIT^IM^^HL70162|RT^Right Thigh^HL70163\r"
					+ "OBX|1|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\r"
					+ "OBX|2|DT|29769-7^VIS presented^LN|2|20120113||||||F\r"
					+ "OBX|3|CE|69764-9 ^Document type^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F \r"
					+ "ORC|RE||65949^DCS||||||20120113|^NavarroAIRA^ZaylinAIRA||^NavarroAIRA^ZaylinAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\r"
					+ "OBX|4|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\r"
					+ "OBX|5|DT|29769-7^VIS presented^LN|2|20120113||||||F\r"
					+ "OBX|6|CE|69764-9^Eligibility Status^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F\r"
					+ "ORC|RE||65930^DCS||||||20120113|^NavarroAIRA^ZaylinAIRA||^NavarroAIRA^ZaylinAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\r"
					+ "RXA|0|1|20101228||10^10^CVX||ml||01^historical^NIP001|||||||||||CP|A\r"
					+ "RXR|C28161^IM^NCIT^IM^^HL70162|RT^Right Thigh^HL70163\r"
					+ "OBX|1|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\r"
					+ "OBX|2|DT|29769-7^VIS presented^LN|2|20120113||||||F\r"
					+ "OBX|3|CE|69764-9 ^Document type^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F \r"
					+ "ORC|RE||65949^DCS||||||20120113|^NavarroAIRA^ZaylinAIRA||^NavarroAIRA^ZaylinAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\r"
					+ "OBX|4|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\r"
					+ "OBX|5|DT|29769-7^VIS presented^LN|2|20120113||||||F\r"
					+ "OBX|6|CE|69764-9^Eligibility Status^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F\r"
					+ "ORC|RE||65930^DCS||||||20120113|^NavarroAIRA^ZaylinAIRA||^NavarroAIRA^ZaylinAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\r"
					+ "RXA|0|1|20110628||03^03^CVX|0.5|ml||01^historical^NIP001|||||||||||CP|A\r"
					+ "RXR|C28161^IM^NCIT^IM^^HL70162|RT^Right Thigh^HL70163\r"
					+ "OBX|1|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\r"
					+ "OBX|2|DT|29769-7^VIS presented^LN|2|20120113||||||F\r"
					+ "OBX|3|CE|69764-9 ^Document type^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F \r"
					+ "ORC|RE||65949^DCS||||||20120113|^NavarroAIRA^ZaylinAIRA||^NavarroAIRA^ZaylinAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\r"
					+ "OBX|4|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\r"
					+ "OBX|5|DT|29769-7^VIS presented^LN|2|20120113||||||F\r"
					+ "OBX|6|CE|69764-9^Eligibility Status^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F\r"
					+ "ORC|RE||65930^DCS||||||20120113|^NavarroAIRA^ZaylinAIRA||^NavarroAIRA^ZaylinAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\r"
					+ "RXA|0|1|20110628||21^21^CVX||ml||01^historical^NIP001|||||||||||CP|A\r"
					+ "RXR|C28161^IM^NCIT^IM^^HL70162|RT^Right Thigh^HL70163\r"
					+ "OBX|1|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\r"
					+ "OBX|2|DT|29769-7^VIS presented^LN|2|20120113||||||F\r"
					+ "OBX|3|CE|69764-9 ^Document type^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F \r"
					+ "ORC|RE||65949^DCS||||||20120113|^NavarroAIRA^ZaylinAIRA||^NavarroAIRA^ZaylinAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\r"
					+ "OBX|4|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\r"
					+ "OBX|5|DT|29769-7^VIS presented^LN|2|20120113||||||F\r"
					+ "OBX|6|CE|69764-9^Eligibility Status^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F\r"
					+ "ORC|RE||65930^DCS||||||20120113|^NavarroAIRA^ZaylinAIRA||^NavarroAIRA^ZaylinAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\r"
					+ "RXA|0|1|20110628||83^83^CVX||ml||01^historical^NIP001|||||||||||CP|A\r"
					+ "RXR|C28161^IM^NCIT^IM^^HL70162|RT^Right Thigh^HL70163\r"
					+ "OBX|1|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\r"
					+ "OBX|2|DT|29769-7^VIS presented^LN|2|20120113||||||F\r"
					+ "OBX|3|CE|69764-9 ^Document type^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F \r"
					+ "ORC|RE||65949^DCS||||||20120113|^NavarroAIRA^ZaylinAIRA||^NavarroAIRA^ZaylinAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\r"
					+ "OBX|4|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\r"
					+ "OBX|5|DT|29769-7^VIS presented^LN|2|20120113||||||F\r"
					+ "OBX|6|CE|69764-9^Eligibility Status^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F\r"
					+ "ORC|RE||65930^DCS||||||20120113|^NavarroAIRA^ZaylinAIRA||^NavarroAIRA^ZaylinAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\r"
					+ "RXA|0|1|20110928||106^106^CVX||ml||01^historical^NIP001|||||||||||CP|A\r"
					+ "RXR|C28161^IM^NCIT^IM^^HL70162|RT^Right Thigh^HL70163\r"
					+ "OBX|1|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\r"
					+ "OBX|2|DT|29769-7^VIS presented^LN|2|20120113||||||F\r"
					+ "OBX|3|CE|69764-9 ^Document type^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F \r"
					+ "ORC|RE||65949^DCS||||||20120113|^NavarroAIRA^ZaylinAIRA||^NavarroAIRA^ZaylinAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\r"
					+ "OBX|4|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\r"
					+ "OBX|5|DT|29769-7^VIS presented^LN|2|20120113||||||F\r"
					+ "OBX|6|CE|69764-9^Eligibility Status^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F\r"
					+ "ORC|RE||65930^DCS||||||20120113|^NavarroAIRA^ZaylinAIRA||^NavarroAIRA^ZaylinAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\r"
					+ "RXA|0|1|20110928||49^49^CVX||ml||01^historical^NIP001|||||||||||CP|A\r"
					+ "RXR|C28161^IM^NCIT^IM^^HL70162|RT^Right Thigh^HL70163\r"
					+ "OBX|1|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\r"
					+ "OBX|2|DT|29769-7^VIS presented^LN|2|20120113||||||F\r"
					+ "OBX|3|CE|69764-9 ^Document type^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F \r"
					+ "ORC|RE||65949^DCS||||||20120113|^NavarroAIRA^ZaylinAIRA||^NavarroAIRA^ZaylinAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\r"
					+ "OBX|4|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\r"
					+ "OBX|5|DT|29769-7^VIS presented^LN|2|20120113||||||F\r"
					+ "OBX|6|CE|69764-9^Eligibility Status^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F\r",
			"MSH|^~\\&|TEST|MOCK|IZGW|IZGW|20240618083107-0400||RSP^K11^RSP_K11|RESULT-07|P|2.5.1|||ER|AL|||||Z32^CDCPHINVS\r"
					+ "MSA|AA|QUERY-07\r" + "QAK|QUERY-07|OK|Z34^Request Immunization History^CDCPHINVS\r"
					+ "ERR||PID^1^11^5|999^Application error^HL70357|W|1^illogical date error^HL70533|||12345 is not a valid zip code in MYIIS\r"  
					+ "QPD|Z34^Request Immunization History^CDCPHINVS|QUERY-07|234820^^^MYEHR^MR\r"
					+ "PID|1||234820^^^MYEHR^MR||CuyahogaAIRA^MarnyAIRA^MalkaAIRA^^^^L|CuyahogaAIRA^MarnyAIRA^^^^^M|19600507|F||ASIAN|1663 Persoon Ave^^Williston^ND^58801^^L||{{Telephone Number}}|||||||||not HISPANIC\r"
					+ "NK1|1|CuyahogaAIRA^MarnyAIRA^^^^^L|MTH^Mom^HL70063|1663 Persoon Ave^^Williston^ND^58801^^L\r"
					+ "ORC|RE||65930^DCS||||||20120113|^CuyahogaAIRA^MarnyAIRA||^CuyahogaAIRA^MarnyAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\r"
					+ "RXA|0|1|20210623||208^208^CVX|0.3|ml||01^historical^NIP001|||||||||||CP|A\r"
					+ "RXR|C28161^IM^NCIT^IM^^HL70162|RT^Right Thigh^HL70163\r"
					+ "OBX|1|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\r"
					+ "OBX|2|DT|29769-7^VIS presented^LN|2|20120113||||||F\r"
					+ "OBX|3|CE|69764-9 ^Document type^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F \r"
					+ "ORC|RE||65949^DCS||||||20120113|^CuyahogaAIRA^MarnyAIRA||^CuyahogaAIRA^MarnyAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\r"
					+ "OBX|4|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\r"
					+ "OBX|5|DT|29769-7^VIS presented^LN|2|20120113||||||F\r"
					+ "OBX|6|CE|69764-9^Eligibility Status^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F\r"
					+ "ORC|RE||65930^DCS||||||20120113|^CuyahogaAIRA^MarnyAIRA||^CuyahogaAIRA^MarnyAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\r"
					+ "RXA|0|1|20210726||208^208^CVX|0.3|ml||01^historical^NIP001|||||||||||CP|A\r"
					+ "RXR|C28161^IM^NCIT^IM^^HL70162|RT^Right Thigh^HL70163\r"
					+ "OBX|1|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\r"
					+ "OBX|2|DT|29769-7^VIS presented^LN|2|20120113||||||F\r"
					+ "OBX|3|CE|69764-9 ^Document type^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F \r"
					+ "ORC|RE||65949^DCS||||||20120113|^CuyahogaAIRA^MarnyAIRA||^CuyahogaAIRA^MarnyAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\r"
					+ "OBX|4|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\r"
					+ "OBX|5|DT|29769-7^VIS presented^LN|2|20120113||||||F\r"
					+ "OBX|6|CE|69764-9^Eligibility Status^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F\r",
			"MSH|^~\\&|TEST|MOCK|IZGW|IZGW|20240618083108-0400||RSP^K11^RSP_K11|RESULT-08|P|2.5.1|||ER|AL|||||Z32^CDCPHINVS\r"
					+ "MSA|AA|QUERY-08\r" + "QAK|QUERY-08|OK|Z34^Request Immunization History^CDCPHINVS\r"
					+ "ERR||RXA^1^5^^1|103^Table value not found^HL70357|W||||vaccination cvx code is unrecognized|\r"
					+ "QPD|Z34^Request Immunization History^CDCPHINVS|QUERY-08|234821^^^MYEHR^MR\r"
					+ "PID|1||234821^^^MYEHR^MR||LaurelAIRA^NyssaAIRA^^^^^L|LaurelAIRA^NyssaAIRA^^^^^M|19670703|F||ASIAN|1586 Dittenseradeel Pl^^Thompson^ND^58278^^L||{{Telephone Number}}|||||||||not HISPANIC\r"
					+ "NK1|1|LaurelAIRA^NyssaAIRA^^^^^L|MTH^Mom^HL70063|1586 Dittenseradeel Pl^^Thompson^ND^58278^^L\r"
					+ "ORC|RE||65930^DCS||||||20120113|^LaurelAIRA^NyssaAIRA||^LaurelAIRA^NyssaAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\r"
					+ "RXA|0|1|20210617||208^208^CVX|0.3|ml||01^historical^NIP001|||||||||||CP|A\r"
					+ "RXR|C28161^IM^NCIT^IM^^HL70162|RT^Right Thigh^HL70163\r"
					+ "OBX|1|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\r"
					+ "OBX|2|DT|29769-7^VIS presented^LN|2|20120113||||||F\r"
					+ "OBX|3|CE|69764-9 ^Document type^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F \r"
					+ "ORC|RE||65949^DCS||||||20120113|^LaurelAIRA^NyssaAIRA||^LaurelAIRA^NyssaAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\r"
					+ "OBX|4|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\r"
					+ "OBX|5|DT|29769-7^VIS presented^LN|2|20120113||||||F\r"
					+ "OBX|6|CE|69764-9^Eligibility Status^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F\r",
			"MSH|^~\\&|TEST|MOCK|IZGW|IZGW|20240618083109-0400||RSP^K11^RSP_K11|RESULT-09|P|2.5.1|||ER|AL|||||Z32^CDCPHINVS\r"
					+ "MSA|AA|QUERY-09\r" + "QAK|QUERY-09|OK|Z34^Request Immunization History^CDCPHINVS\r"
					+ "ERR||MSH^1^12|203^unsupported version id^HL70357|E||||Unsupported HL7 Version ID\r"
					+ "QPD|Z34^Request Immunization History^CDCPHINVS|QUERY-09|234822^^^MYEHR^MR\r"
					+ "PID|1||234822^^^MYEHR^MR||FagenAIRA^AitanAIRA^JessieAIRA^^^^L|FagenAIRA^AitanAIRA^^^^^M|19790705|M||ASIAN|1709 Eerneuzen Cir^^Williston^ND^58801^^L||{{Telephone Number}}|||||||||not HISPANIC\r"
					+ "NK1|1|FagenAIRA^AitanAIRA^^^^^L|MTH^Mom^HL70063|1709 Eerneuzen Cir^^Williston^ND^58801^^L\r"
					+ "ORC|RE||65930^DCS||||||20120113|^FagenAIRA^AitanAIRA||^FagenAIRA^AitanAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\r"
					+ "RXA|0|1|20210705||208^208^CVX|0.3|ml||01^historical^NIP001|||||||||||CP|A\r"
					+ "RXR|C28161^IM^NCIT^IM^^HL70162|RT^Right Thigh^HL70163\r"
					+ "OBX|1|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\r"
					+ "OBX|2|DT|29769-7^VIS presented^LN|2|20120113||||||F\r"
					+ "OBX|3|CE|69764-9 ^Document type^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F \r"
					+ "ORC|RE||65949^DCS||||||20120113|^FagenAIRA^AitanAIRA||^FagenAIRA^AitanAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\r"
					+ "OBX|4|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\r"
					+ "OBX|5|DT|29769-7^VIS presented^LN|2|20120113||||||F\r"
					+ "OBX|6|CE|69764-9^Eligibility Status^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F\r"
					+ "ORC|RE||65930^DCS||||||20120113|^FagenAIRA^AitanAIRA||^FagenAIRA^AitanAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\r"
					+ "RXA|0|1|20210727||208^208^CVX|0.3|ml||01^historical^NIP001|||||||||||CP|A\r"
					+ "RXR|C28161^IM^NCIT^IM^^HL70162|RT^Right Thigh^HL70163\r"
					+ "OBX|1|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\r"
					+ "OBX|2|DT|29769-7^VIS presented^LN|2|20120113||||||F\r"
					+ "OBX|3|CE|69764-9 ^Document type^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F \r"
					+ "ORC|RE||65949^DCS||||||20120113|^FagenAIRA^AitanAIRA||^FagenAIRA^AitanAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\r"
					+ "OBX|4|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\r"
					+ "OBX|5|DT|29769-7^VIS presented^LN|2|20120113||||||F\r"
					+ "OBX|6|CE|69764-9^Eligibility Status^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F\r",
			"MSH|^~\\&|TEST|MOCK|IZGW|IZGW|20240618083110-0400||RSP^K11^RSP_K11|RESULT-10|P|2.5.1|||ER|AL|||||Z32^CDCPHINVS\r"
					+ "MSA|AA|QUERY-10\r" + "QAK|QUERY-10|OK|Z34^Request Immunization History^CDCPHINVS\r"
					+ "ERR||ORC^^3|101^Required field missing^HL70357|W||||vaccination id is missing|\r" 
					+ "QPD|Z34^Request Immunization History^CDCPHINVS|QUERY-10|234823^^^MYEHR^MR\r"
					+ "PID|1||234823^^^MYEHR^MR||NavarroAIRA^SaulAIRA^LaneyAIRA^^^^L|NavarroAIRA^SaulAIRA^^^^^M|19630204|M||ASIAN|1539 Meek Pl^^Belcourt^ND^58316^^L||{{Telephone Number}}|||||||||not HISPANIC\r"
					+ "NK1|1|NavarroAIRA^SaulAIRA^^^^^L|MTH^Mom^HL70063|1539 Meek Pl^^Belcourt^ND^58316^^L\r"
					+ "ORC|RE||65930^DCS||||||20120113|^NavarroAIRA^SaulAIRA||^NavarroAIRA^SaulAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\r"
					+ "RXA|0|1|20210706||208^208^CVX|0.3|ml||01^historical^NIP001|||||||||||CP|A\r"
					+ "RXR|C28161^IM^NCIT^IM^^HL70162|RT^Right Thigh^HL70163\r"
					+ "OBX|1|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\r"
					+ "OBX|2|DT|29769-7^VIS presented^LN|2|20120113||||||F\r"
					+ "OBX|3|CE|69764-9 ^Document type^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F \r"
					+ "ORC|RE||65949^DCS||||||20120113|^NavarroAIRA^SaulAIRA||^NavarroAIRA^SaulAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\r"
					+ "OBX|4|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\r"
					+ "OBX|5|DT|29769-7^VIS presented^LN|2|20120113||||||F\r"
					+ "OBX|6|CE|69764-9^Eligibility Status^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F\r" };
}
