package test.gov.cdc.izgateway.v2tofhir;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.text.ParseException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.Bundle;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import ca.uhn.fhir.fhirpath.IFhirPath;
import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.util.Terser;
import gov.cdc.izgw.v2tofhir.converter.MessageParser;
import gov.cdc.izgw.v2tofhir.annotation.ComesFrom;
import gov.cdc.izgw.v2tofhir.segment.StructureParser;
import gov.cdc.izgw.v2tofhir.utils.Mapping;
import lombok.extern.slf4j.Slf4j;

@Slf4j
/**
 * These tests verify FHIR Resources created by MessageParser conform to expectations. 
 */
class MessageParserTests extends TestBase {
	static {
		log.debug("{} loaded", TestBase.class.getName());
	}

	// Force load of Mapping class before any testing starts.
	@SuppressWarnings("unused")
	private static final Class<Mapping> MAPPING_CLASS = Mapping.class; 

	private static long id = 0;
	private String generateId() {
		return Long.toString(++id);
	}
	@ParameterizedTest
	@MethodSource("testTheData")
	void testTheData(TestData testData) throws Exception {
		// System.out.println(testData);
		
		MessageParser p = new MessageParser();
		p.setIdGenerator(this::generateId);
		Message msg = parse(testData.getTestData());
		
		id = 0;
		Bundle b = p.convert(msg);
		
		testData.evaluateAllAgainst(b);
		writeTheTests(testData, msg);
	}
	
	private static MessageParser MP = new MessageParser();
	private static BufferedWriter bw = null;
	
	void writeTheTests(TestData testData, Message msg) {
		if (bw == null) {
			try {
				bw = new BufferedWriter(new FileWriter("./target/messages.txt"));
				Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
			} catch (IOException e) {
				// If we cannot write, just exit.
				e.printStackTrace();
				System.exit(1);
			}
		}
		for (String segment: testData.getTestData().split("\r")) {
			String segmentName = StringUtils.left(segment, 3);
			Class<StructureParser> parser = MP.loadParser(segmentName);
			if (parser == null) {
				// Skip segments there is no parser for.
				continue;
			}
			LinkedHashMap<String, String> assertions = getExistingAssertions(testData, segmentName);
			
			for (ComesFrom ann: parser.getAnnotationsByType(ComesFrom.class)) {
				String annotation = getAssertion(segmentName, ann, msg);
				String key = StringUtils.substringBetween(annotation, "@", ".exists(");
				String exists = assertions.get(key);
				if (exists != null) {
					log.warn("Assertion ({}) exists for ({})", exists, annotation);
				} else {
					assertions.put(key, annotation);
				}
			}
			try {
				writeSegment(segment);
				writeAssertions(assertions.values());
			} catch (IOException e) {
				log.error("IO Error writing assertions, aborting test");
				// If there was an IO Error, subsequent tests will also
				// fail. Just give up.
				System.exit(1);
			}
		}
	}
	private static LinkedHashMap<String, String> getExistingAssertions(TestData testData, String segmentName) {
		LinkedHashMap<String, String> assertions = new LinkedHashMap<>();
		for (int i = 0; i < testData.getNames().size(); i++) {
			if (testData.getName(i).startsWith(segmentName)) {
				String alreadyAsserted = testData.getFhirPath(i);
				if (alreadyAsserted.contains(".exists(")) {
					String key = StringUtils.substringBefore(alreadyAsserted, ".exists(");
					if (!assertions.containsKey(key)) {
						assertions.put(key, "  @" + alreadyAsserted + "\n");
					}
					assertions.put(alreadyAsserted, "  @" + alreadyAsserted + "\n");
				}
			}
		}
		return assertions;
	}
	private static String getAssertion(String segmentName, ComesFrom ann, Message msg) {
		StringBuilder b = new StringBuilder();
		addComesFrom(ann, b);
		Terser t = new Terser(msg);
		b.append("  @").append(ann.path()).append(".exists( \\\n");
		for (String source: ann.source()) {
			if (source.length() == 0) {
				continue;
			}
			// Source is a Terser path in the form SEG-#-#
			try {
				String data = t.get(source);
				b.append("    /* ").append(source).append(" = ").append(data).append(" */ \\\n");
			} catch (HL7Exception e) {
				// Shouldn't happen, the message was already parsed.
				log.warn("Error parsing message: {}", e.getMessage());
			}
		}
		if (ann.fixed().length() != 0) {
			b.append("    /* fixed = \"").append(ann.fixed()).append("\" */ \\\n");
		}
		b.append("  ) \\\n");
		if (ann.comment().length() != 0) {
			b.append("  // ").append(ann.comment()).append("\n");
		}
		return b.toString();
	}
	private static void addComesFrom(ComesFrom ann, StringBuilder b) {
		b.append("  // ComesFrom(path=\"").append(ann.path()).append("\", ");
		if (ann.source().length == 1) {
			b.append("source = \"").append(ann.source()[0]).append("\")");
		} else if (ann.source().length > 1) {
			b.append("source = { ");
			for (int i = 0; i < ann.source().length; i++) {
				if (i != 0) b.append(", ");
				b.append("\"").append(ann.source()[i]).append("\"");
			}
			b.append("})");
		} else if (ann.fixed().length() > 0) {
			b.append("fixed = \"").append(ann.fixed()).append("\")");
		}
		b.append(" \\\n");
	}
	void shutdown() {
		if (bw != null) {
			try {
				bw.close();
			} catch (IOException e) {
			}
		}
	}
	
	void writeSegment(String segment) throws IOException {
		int max = 80;
		int pos = StringUtils.lastIndexOf(StringUtils.left(segment, max), '|');
		do {
			String left = StringUtils.left(segment, pos >= 0 ? pos + 1 : max);
			bw.write(left);
			segment = StringUtils.substring(segment, left.length());
			if (StringUtils.isNotBlank(segment)) {
				bw.write("\\\n  ");
				max = 78;
			}
		} while (StringUtils.isNotBlank(segment));
		bw.write("\n");
	}
	void writeAssertions(Iterable<String> assertions) throws IOException {
		String last = null;
		
		for (String assertion: assertions) {
			if (assertion.equals(last)) {
				// Skip values in sequence that are duplicates
				continue;
			}
			bw.write(last = assertion);
		}
	}
	IFhirPath getEngine() {
		return ctx.newFhirPath();
	}
	
	static List<TestData> testTheData() {
		return TEST_MESSAGES;
	}
	
	static List<TestData> testTheData1() {
		return Collections.singletonList(TEST_MESSAGES.get(0));
	}
	
	
	@ParameterizedTest
	@MethodSource("getTestMessages")  // should be getTestMessages, other possible values are for localized testing
	void testMessageConversion(Message hl7Message) throws IOException, ParseException {
		MessageParser p = new MessageParser();
		Bundle b = p.convert(hl7Message);
		System.out.println(yamlParser.encodeResourceToString(b));
	}
	
	static Stream<Message> getTestMessages1() {
		int[] found = { 0 }; 
		return getTestMessages().filter(t -> ++found[0] == 1);
	}
	
	@ParameterizedTest
	@MethodSource("getTestPIDs")
	void testPIDConversion(NamedSegment segment) throws HL7Exception {
		MessageParser p = new MessageParser();
		System.out.println(segment.segment().encode());
		Bundle b = p.createBundle(Collections.singleton(segment.segment()));
		System.out.println(fhirParser.encodeResourceToString(b));
	}
}
