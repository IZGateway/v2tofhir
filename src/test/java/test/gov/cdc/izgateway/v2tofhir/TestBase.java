package test.gov.cdc.izgateway.v2tofhir;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;

import com.ainq.fhir.utils.YamlParser;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.support.DefaultProfileValidationSupport;
import ca.uhn.fhir.context.support.IValidationSupport;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Composite;
import ca.uhn.hl7v2.model.GenericMessage;
import ca.uhn.hl7v2.model.GenericSegment;
import ca.uhn.hl7v2.model.Group;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.model.Primitive;
import ca.uhn.hl7v2.model.Segment;
import ca.uhn.hl7v2.model.Type;
import ca.uhn.hl7v2.model.Varies;
import ca.uhn.hl7v2.parser.EncodingCharacters;
import ca.uhn.hl7v2.parser.ModelClassFactory;
import ca.uhn.hl7v2.parser.Parser;
import ca.uhn.hl7v2.parser.PipeParser;
import gov.cdc.izgw.v2tofhir.utils.ParserUtils;
import gov.cdc.izgw.v2tofhir.utils.TestData;
import lombok.extern.slf4j.Slf4j;
import test.gov.cdc.izgateway.TestUtils;

/**
 * Base class for many tests, providing support methods to get test data.
 * 
 * @author Audacious Inquiry
 *
 */
@Slf4j
public class TestBase {
	static {
		log.debug("{} loaded", TestBase.class.getName());
	}
	static final Parser v2Parser = new PipeParser();
	/** A list of test messages for unit tests */
	protected static final List<TestData> TEST_MESSAGES = loadTestMessages();
	/** A list of test segments for unit tests */
	protected static final List<TestData> TEST_SEGMENTS = loadTestSegments();

	// These are defined constants which indicate which V2 types should be used for
	// testing.
	static final List<String> CODING_TYPES = Arrays.asList("CE", "CWE", "CNE");
	static final List<String> ID_TYPES = Arrays.asList("CX", "EI", "CNN", "XCN", "XON");
	static final List<String> NAME_TYPES = Arrays.asList("CNN", "XCN", "XPN");
	static final List<String> ADDR_TYPES = Arrays.asList("AD", "SAD", "XAD");
	static final List<String> QUANTITY_TYPES = Arrays.asList("CQ", "NM");
	static final List<String> CONTACT_TYPES = Arrays.asList("TN", "XTN");

	// These are defined constants which indicate which V2 types should match when
	// converted to text.
	static final List<String> IS_CODING = CODING_TYPES;
	static final List<String> IS_CONTACT = CONTACT_TYPES;
	static final List<String> IS_ID = Arrays.asList("CX", "EI");
	static final List<String> IS_NAME = NAME_TYPES;
	static final List<String> IS_ADDR = ADDR_TYPES;
	static final List<String> IS_QUANTITY = QUANTITY_TYPES;

	/** The FhirContext for R4 */
	protected static final FhirContext ctx = FhirContext.forR4();
	/** A validation Support for R4 */
	protected static final IValidationSupport support = new DefaultProfileValidationSupport(ctx);
	static {
		ctx.setValidationSupport(support);
	}
	/** A fhirParser to use to generate FHIR in JSON */
	protected static final IParser fhirParser = ctx.newJsonParser().setPrettyPrint(true);
	/** A yamlParser to use to generate FHIR in YAML */
	protected static final YamlParser yamlParser = new YamlParser(ctx);
	/** The default V2 Version to use for parsing */
	protected static final String DEFAULT_V2_VERSION = "2.5.1";
	/** The default V2 encoding characters */
	protected static final String ENCODING_CHARS = "|^~\\&";

	static Set<Type> getTestDataForCoding() {
		return getTestData(t -> CODING_TYPES.contains(t.getName()));
	}

	static Set<Type> getTestDataForContactPoint() {
		return getTestData(t -> IS_CONTACT.contains(t.getName()));
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
	
	/**
	 *	This class is use to enable better reporting in JUnit tests
	 *	giving each segment being tested a name and string representation
	 *	better than the existing Segment.toString() implementation. 
	 * 
	 * @author Audacious Inquiry
	 */
	public static class NamedSegment {
		private final Segment segment;
		/**
		 * Construct a NamedSegment from an existing segment
		 * @param segment The segment to use
		 */
		public NamedSegment(Segment segment) {
			this.segment = segment;
		}
		public String toString() {
			String encoded = null;
			try {
				if (segment instanceof GenericSegment) {
					encoded = "Cannot encode from " + segment.getMessage().getVersion();
				} else {
					encoded = segment.encode();
				}
			} catch (Exception ex) {
				encoded = "Cannot encode " + segment.toString();
			}
			return String.format("%s[%s]", segment.getName(), encoded);
		}
		/**
		 * Get the segment
		 * @return the segment
		 */
		public Segment segment() {
			return segment;
		}
	}

	static List<NamedSegment> getTestSegments() {
		Set<Segment> testSegments = new TreeSet<Segment>(TestUtils::compare);
		for (Message msg : getTestMessages().toList()) {
			ParserUtils.iterateSegments(msg, testSegments);
		}

		for (TestData data: TestData.load("segments.txt", false)) {
			try {
				testSegments.add(parseSegment(StringUtils.trim(data.getTestData())));
			} catch (Exception e) {
				// Just swallow this.
				log.error("Error parsing segment {}: {}", data.getTestData(), e.getMessage());
			}
		}
		return testSegments.stream().map(NamedSegment::new).toList();
	}
	
	static List<NamedSegment> getTestSegments(String ...names) {
		List<String> l = Arrays.asList(names);
		return getTestSegments().stream().filter(n -> l.contains(n.segment().getName())).toList();
	}

	/* Test Segment generators for all segment types in the test data */ 
	static List<NamedSegment> getTestDSCs() { return getTestSegments("DSC"); }
	static List<NamedSegment> getTestERRs() { return getTestSegments("ERR"); }
	static List<NamedSegment> getTestEVNs() { return getTestSegments("EVN"); }
	static List<NamedSegment> getTestMRGs() { return getTestSegments("MRG"); }
	static List<NamedSegment> getTestMSAs() { return getTestSegments("MSA"); }
	static List<NamedSegment> getTestMSHs() { return getTestSegments("MSH"); }
	static List<NamedSegment> getTestNK1s() { return getTestSegments("NK1"); }
	static List<NamedSegment> getTestOBXs() { return getTestSegments("OBX"); }
	static List<NamedSegment> getTestORCs() { return getTestSegments("ORC"); }
	static List<NamedSegment> getTestPIDs() { return getTestSegments("PID"); }
	static List<NamedSegment> getTestQAKs() { return getTestSegments("QAK"); }
	static List<NamedSegment> getTestQIDs() { return getTestSegments("QID"); }
	static List<NamedSegment> getTestQPDs() { return getTestSegments("QPD"); }
	static List<NamedSegment> getTestRCPs() { return getTestSegments("RCP"); }
	static List<NamedSegment> getTestRXAs() { return getTestSegments("RXA"); }
	static List<NamedSegment> getTestRXRs() { return getTestSegments("RXR"); }
	static Stream<NamedSegment> getTestZIMs() {
		List<String> allowed = Arrays.asList("Z34", "Z44");
		return getTestSegments("QPD").stream()
			.filter(n -> allowed.contains(ParserUtils.toString(n.segment(), 1))); 
	}

	
	static Message parse(String message) {
		try {
			return v2Parser.parse(message);
		} catch (Exception e) {
			System.err.println("Error parsing : " + StringUtils.left(message, 90));
			e.printStackTrace();
			return null;
		}
	}
	
	static Segment parseSegment(String segment) throws Exception {
		ModelClassFactory factory = v2Parser.getHapiContext().getModelClassFactory();
		String segName = StringUtils.substringBefore(segment, "|");
		@SuppressWarnings("serial")
		Message message = new GenericMessage.V251(factory) {
			@Override
		    public String getEncodingCharactersValue() throws HL7Exception {
		    	return "^~\\&";
		    }
			@Override
		    public Character getFieldSeparatorValue() throws HL7Exception {
		    	return '|';
		    }
		};
		message.setParser(v2Parser);
		Class<? extends Segment> segClass = factory.getSegmentClass(segName, DEFAULT_V2_VERSION);
		Segment seg = segClass
				.getDeclaredConstructor(Group.class, ModelClassFactory.class)
				.newInstance(message, factory);
		v2Parser.parse(seg, segment, EncodingCharacters.defaultInstance());
		return seg;
	}

	static Stream<String> testMessages() {
		return TEST_MESSAGES.stream().map(TestData::getTestData);
	}

	static Stream<Message> getTestMessages() {
		return testMessages().map(TestBase::parse);
	}
	
	private static List<TestData> loadTestMessages() {
		return TestData.load("messages.txt", true);
	}
	
	private static List<TestData> loadTestSegments() {
		// Load test messages as segments.
		List<TestData> data = TestData.load("messages.txt", false);
		// Add test Segments to the list
		data.addAll(TestData.load("segments.txt", false));
		
		return data;
	}
	
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

}
