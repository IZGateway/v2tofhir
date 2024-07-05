package test.gov.cdc.izgateway.v2tofhir;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.ServiceConfigurationError;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Composite;
import ca.uhn.hl7v2.model.GenericSegment;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.model.Primitive;
import ca.uhn.hl7v2.model.Segment;
import ca.uhn.hl7v2.model.Type;
import ca.uhn.hl7v2.model.Varies;
import ca.uhn.hl7v2.parser.Parser;
import ca.uhn.hl7v2.parser.PipeParser;
import gov.cdc.izgw.v2tofhir.utils.ParserUtils;
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
	final static List<String> CONTACT_TYPES = Arrays.asList("TN", "XTN");

	// These are defined constants which indicate which V2 types should match when
	// converted to text.
	final static List<String> IS_CODING = CODING_TYPES;
	final static List<String> IS_CONTACT = CONTACT_TYPES;
	final static List<String> IS_ID = Arrays.asList("CX", "EI");
	final static List<String> IS_NAME = NAME_TYPES;
	final static List<String> IS_ADDR = ADDR_TYPES;
	final static List<String> IS_QUANTITY = QUANTITY_TYPES;

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
				if (segment instanceof GenericSegment generic) {
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
		for (Message msg : testV2Messages().toList()) {
			ParserUtils.iterateSegments(msg, testSegments);
		}
		return testSegments.stream().map(s -> new NamedSegment(s)).toList();
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

	static List<String> testMessages() {
		return Arrays.asList(TEST_MESSAGES);
	}

	static Stream<Message> testV2Messages() {
		return testMessages().stream().map(TestBase::parse);
	}
	
	private static final String[] TEST_MESSAGES = loadTestMessages();
	static String[] loadTestMessages() {
		return loadTestData("messages.txt", true);
	}
	
	static String[] loadTestSegments() {
		return loadTestData("segments.txt", false);
	}
	
	static String[] loadTestData(String name, boolean isMessageFile) {
		List<String> data = new ArrayList<>();
		try (
			BufferedReader br = new BufferedReader(new InputStreamReader(getResource(name), StandardCharsets.UTF_8))
		) {
			StringBuilder b = new StringBuilder();
			String line = null;
			int lineno = 0;
			while ((line = br.readLine()) != null) {
				lineno ++;
				line = line.trim();
				if (line.startsWith("#")) {
					continue;	// Ignore comment lines
				}
				if (isMessageFile) {
					if (StringUtils.isBlank(line)) {
						if (!b.isEmpty()) {
							String message = b.toString();
							if (message.startsWith("MSH|")) {
								data.add(message);	// Add the message.
							} else {
								log.error("{}({}) is not a valid message: {}", name, lineno, StringUtils.left(message, 40));
							}
						}
						b.setLength(0);
					} else {
						if (line.matches("^[A-Z123]{3}\\|.*$")) {
							b.append(line).append("\r");	// Append the segment
						} else {
							log.error("{}({}) is not a valid segment: {}", name, lineno, line);
						}
					}
				} else {
					if (line.matches("^[A-Z123]{3}\\|.*$")) {
						data.add(line);	// Append the segment
					} else {
						log.error("{}({}) is not a valid segment: {}", name, lineno, line);
					}
				}
			}
			if (isMessageFile && !b.isEmpty()) {
				data.add(b.toString());
			}
			return data.toArray(new String[0]);
		} catch (Exception ioex) {
			log.error("Error loading test file " + name, ioex);
			ioex.printStackTrace();
			throw new ServiceConfigurationError("Cannot load test file " + name);
		}
	}

	private static InputStream getResource(String name) throws IOException {
		InputStream s = TestBase.class.getClassLoader().getResourceAsStream(name);
		if (s == null) {
			throw new IOException("Cannot find " + name);
		}
		return s;
	}

}
