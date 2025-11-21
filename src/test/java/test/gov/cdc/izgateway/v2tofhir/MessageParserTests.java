package test.gov.cdc.izgateway.v2tofhir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.FastDateFormat;
import org.hl7.fhir.instance.model.api.IBase;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.InstantType;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Provenance;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.StringType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import com.ainq.fhir.utils.PathUtils;

import ca.uhn.fhir.fhirpath.IFhirPath;
import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.DataTypeException;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.model.Segment;
import ca.uhn.hl7v2.model.Structure;
import ca.uhn.hl7v2.model.Type;
import ca.uhn.hl7v2.model.v251.datatype.ST;
import ca.uhn.hl7v2.model.v251.message.QBP_Q11;
import ca.uhn.hl7v2.model.v251.segment.MSH;
import ca.uhn.hl7v2.model.v251.segment.QPD;
import ca.uhn.hl7v2.util.Terser;
import gov.cdc.izgw.v2tofhir.converter.DatatypeConverter;
import gov.cdc.izgw.v2tofhir.converter.MessageParser;
import gov.cdc.izgw.v2tofhir.annotation.ComesFrom;
import gov.cdc.izgw.v2tofhir.annotation.Produces;
import gov.cdc.izgw.v2tofhir.segment.AbstractSegmentParser;
import gov.cdc.izgw.v2tofhir.segment.FieldHandler;
import gov.cdc.izgw.v2tofhir.segment.IzDetail;
import gov.cdc.izgw.v2tofhir.segment.PIDParser;
import gov.cdc.izgw.v2tofhir.segment.Processor;
import gov.cdc.izgw.v2tofhir.utils.Mapping;
import gov.cdc.izgw.v2tofhir.utils.ParserUtils;
import gov.cdc.izgw.v2tofhir.utils.QBPUtils;
import gov.cdc.izgw.v2tofhir.utils.TestData;
import gov.cdc.izgw.v2tofhir.utils.TextUtils;
import lombok.extern.slf4j.Slf4j;

@Slf4j
/**
 * These tests verify FHIR Resources created by MessageParser conform to expectations. 
 */
class MessageParserTests extends TestBase {
	private Type[] fieldValues;
	private String fieldName;
	private MessageParser messageParser;

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
		log.debug("{}", testData);
		
		MessageParser p = new MessageParser();
		p.setIdGenerator(this::generateId);
		Message msg = parse(testData.getTestData());
		
		id = 0;
		Bundle b = p.convert(msg);
		System.out.println(yamlParser.encodeResourceToString(b));
		testData.evaluateAllAgainst(b);
		writeTheTests(testData, msg);
	}
	
	private static final MessageParser MP = new MessageParser();
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
			Class<Processor<Message, Structure>> parser = MP.loadParser(segmentName);
			if (parser == null) {
				// Skip segments there is no parser for.
				continue;
			}
			LinkedHashMap<String, String> assertions = getExistingAssertions(testData, segmentName);
			
			for (ComesFrom ann: parser.getAnnotationsByType(ComesFrom.class)) {
				String annotation = getAssertion(ann, msg);
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
	private static String getAssertion(ComesFrom ann, Message msg) {
		StringBuilder b = new StringBuilder();
		addComesFrom(ann, b);
		Terser t = new Terser(msg);
		b.append("  @").append(ann.path()).append(".exists( \\\n");
		for (String source: ann.source()) {
			if (source.isEmpty()) {
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
		if (!ann.fixed().isEmpty()) {
			b.append("    /* fixed = \"").append(ann.fixed()).append("\" */ \\\n");
		}
		b.append("  ) \\\n");
		if (!ann.comment().isEmpty()) {
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
		} else if (!ann.fixed().isEmpty()) {
			b.append("fixed = \"").append(ann.fixed()).append("\")");
		}
		b.append(" \\\n");
	}
	void shutdown() {
		if (bw != null) {
			try {
				bw.close();
			} catch (IOException e) {
				// Do nothing
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
		log.debug(yamlParser.encodeResourceToString(b));
	}
	
	static int segmentCounter = 0;
	@ParameterizedTest
	@MethodSource("getTestSegments") // should be getTestSegments, other possible values are for localized testing 
	void testSegmentConversions(NamedSegment segment) throws HL7Exception, ClassNotFoundException {
		messageParser = new MessageParser();
		segment.segment();
		messageParser.getBundle();
		log.info("[{}] segment={}", ++segmentCounter, segment.toString());
		
		// Add some prep data for it for some tests.
		Class<? extends AbstractSegmentParser> parser = prepareForTest(messageParser, segment);
		Produces produces = parser.getAnnotation(Produces.class);
		
		Bundle b1 = messageParser.createBundle(Collections.singleton(segment.segment()));
		
		if (!b1.hasEntry()) {
			log.info("No parsers for {}", segment.segment().getName());
		}
		if (segment.segment().getName().equals("RCP")) {
			// Verify that there is a count if the segment as RCP-2
			Parameters params = messageParser.getFirstResource(Parameters.class);
			String value = ParserUtils.toString(segment.segment(), 2, 0);
			if (value != null && params.getParameter("_search").getValue() instanceof StringType uri) {
				assertTrue(uri.getValueAsString().endsWith("&_count=" + value));
			}
		} else {
			boolean found = false;
			for (BundleEntryComponent entry: b1.getEntry()) {
				if (produces.resource().isInstance(entry.getResource())) {
					found = true;
					break;
				}
			}
			if (!messageParser.getContext().isStoringProvenance() || !(produces.resource().isInstance(Provenance.class))) {
				// Don't error on lack of provenance if we aren't storing it!
				assertTrue(found, "The bundle should have entries of type "  + produces.resource().getSimpleName());
			}
		}
		log.debug(segment.segment().encode());
		if (log.isDebugEnabled()) {
			// save the expensive operation if not logging
			log.debug(yamlParser.encodeResourceToString(b1));
		}
		List<Resource> resources = b1.getEntry().stream()
				.filter(e -> produces.resource().isInstance(e.getResource()))
				.map(e -> e.getResource()).toList();

		assertFalse(resources.isEmpty(), "Could not find a " + produces.resource().getSimpleName());
		assertEquals(1, resources.size(), "There can only be one " + produces.resource().getSimpleName());
		
		Resource res = null;
		// Go through the resource and verify that values are set in methods declared
		// with ComesFrom.
		for (ComesFrom from: FieldHandler.getComesFrom(parser)) {
			String fhirType = StringUtils.substringBefore(from.path(), ".");
			if (fhirType.endsWith("]")) {
				fhirType = StringUtils.substringBefore(fhirType, "[");
			}
			
			if ("Bundle".equals(fhirType)) {
				res = b1;
			} else {
				String type = fhirType;
				res = b1.getEntry().stream()
					.filter(e -> e.hasResource() && e.getResource().fhirType().equals(type))
					.map(e -> e.getResource()).findFirst().orElse(null);
			}
			// Some conversions are just to capture data for future
			// processing, such as OBX-2 to get the type in OBX-5
			if (!from.path().isEmpty()) {
				String error = verify(segment.segment(), res, from);
				assertNull(error);
			}
		}
	}
	
	@ParameterizedTest
	@MethodSource("getTestZIMs") 
	void testQBPUtils(NamedSegment segment) throws HL7Exception {
		Type messageQueryName = segment.segment().getField(1, 0);
		String zim = ParserUtils.toString(messageQueryName);
		
		if (!"Z34".equals(zim) && !"Z44".equals(zim)) {
			return;  // This test is only for specific message profiles
		}

		Bundle b1 = new MessageParser().createBundle(Collections.singleton(segment.segment()));
		Parameters parameters = b1.getEntry().stream()
			.map(e -> e.getResource())
			.filter(Parameters.class::isInstance)
			.map(r -> (Parameters)r)
			.findFirst().orElse(null);
		
		StringType search = (StringType)parameters.getParameter("_search").getValue();
		
		QBP_Q11 qbp = QBPUtils.createMessage(zim);
		MSH msh = qbp.getMSH();
		
		QBPUtils.setSendingApplication(qbp, "SendingApp");
		assertEquals("SendingApp", msh.getSendingApplication().getNamespaceID().getValue());
		
		QBPUtils.setSendingFacility(qbp, "SendingFacility");
		assertEquals("SendingFacility", msh.getSendingFacility().getNamespaceID().getValue());
		
		QBPUtils.setReceivingApplication(qbp, "ReceivingApp");
		assertEquals("ReceivingApp", msh.getReceivingApplication().getNamespaceID().getValue());
		
		QBPUtils.setReceivingFacility(qbp, "ReceivingFacility");
		assertEquals("ReceivingFacility", msh.getReceivingFacility().getNamespaceID().getValue());
		
		String[] searchParams = StringUtils.substringAfter(search.asStringValue(), "?").split("&");
		Map<String, List<String>> m = new LinkedHashMap<>();
		for (String searchParam: searchParams) {
			String name = StringUtils.substringBefore(searchParam, "=");
			String value = StringUtils.substringAfter(searchParam, "=");
			List<String> values = m.computeIfAbsent(name, k -> new ArrayList<>());
			values.add(URLDecoder.decode(value, StandardCharsets.UTF_8));
		}
		
		QBPUtils.addParamsToQPD(qbp, m, false);

		// Copy over the query tag.
		Type queryTag = segment.segment().getField(2, 0);
		QPD qpd = qbp.getQPD();
		qpd.getQueryTag().setValue(ParserUtils.toString(queryTag));
		if (!StringUtils.equals(segment.segment().encode(), qpd.encode())) {
			log.info("=============== FAILURE ===============");
			log.info("Search: \n{}", parameters.getParameter("_search").getValue());
			log.info("Comparing: \nORIGINAL: {}\n REBUILT: {}", segment.segment().encode(), qpd.encode());
		}
		// Assert that the round trip worked.
		assertEquals(segment.segment().encode().replace("|", "|\n "), qpd.encode().replace("|", "|\n "));
	}
	
	
	private String verify(Segment segment, Resource res, ComesFrom from) {
		// So now we have a path and a segment and field.
		// We should be able to compare these using TextUtils.toString() methods.
		getFields(segment, from);
		List<IBase> l = getProducedItems(res, from);
		for (Type type: fieldValues) {
			type = DatatypeConverter.adjustIfVaries(type, from.type());
			String value = getMappedValue(from, type).replace("#", " ");
			if (StringUtils.isEmpty(value)) {
				continue;
			}
			String produced = getProducedValue(l, value, type.getName());
			
			// For invalid datetime, check to see if value is actually a valid
			// date time, and let it be ok if that nothing was produced if 
			// value is invalid.
			if (produced == null && DatatypeConverter.DATETIME_TYPES.contains(type.getName())) {
				produced = checkDatetime(value);
			}
			
			String error = null;
			if (produced == null) {
				error = "Did not find " + fieldName + " = '" + value + " in " + l + "' at " + from.path();
				log.info(error);
				return error;
			} else {
				log.info("Found " + fieldName + " = '" + value + "' in '" + produced + "' at " + from.path());
			}
		}
		return null;
	}
	
	private String checkDatetime(String value) {
		String[] parts = value.split("[\\.\\-+]");
		if (!StringUtils.isNumeric(				// All Digits must be numeric
				StringUtils.replace(value, ".+-", "")) ||
			(parts[0].length() % 2) == 1 ||		// First part Must have YYYY[MM[DD[HH[MM[SS]]]]] and even length
			parts[0].length() < 4 || 			// at least 4, 
			parts[0].length() > 14 ||			// and at most 14
			(value.contains(".") && 			// decimal part can have 1-4 digits
				(parts[1].isEmpty() || parts[1].length() > 4)) ||
			(!value.contains(".") && parts.length > 1 && parts[1].length() != 4) ||	// TZ must be 4 digits if present
			(value.contains(".") && 
				(parts.length < 2 || parts[2].length() != 4))   // TZ must be 4 digits if present
		) {
			return "null due to invalid input";
		}
		FastDateFormat ft = FastDateFormat.getInstance("yyyyMMddhhmmss".substring(0, parts[0].length()));
		try {
			Date d = ft.parse(parts[0]);
			if (!ft.format(d).equals(value)) {
				return "invalid date time value";
			}
		} catch (ParseException e) {
			return "null due to incorrect time/date input";
		}
		
		String tz = (parts.length > 2) || (!value.contains(".") && parts.length > 1) ? StringUtils.right(value,  5) : null;
		if (tz != null) {
			try {
				int tzi = Integer.parseInt(tz);
				if (tzi < -1400 || tzi > 1500 || (tzi % 15 != 0)) {
					return "invalid timezone";
				}
			} catch (NumberFormatException ex) {
				return "non-numeric timezone";
			}
		}
		return null;
	}
	
	private String getProducedValue(List<IBase> l, String value, String v2Type) {
		String produced = null;
		
		for (IBase base: l) {
			// "#" for " " replacement fixes identifier and reference types.
			produced = TextUtils.toString(base).replace("#", " ");
			// produced can be CodeableConcept or Coding, and value 
			// can be from a simpler entity, such as an ST or ID.
			if (StringUtils.containsIgnoreCase(produced, value) ||
				StringUtils.containsIgnoreCase(value, produced)) {
				return produced;
			} else if (base instanceof CodeableConcept cc) {
				for (Coding coding: cc.getCoding()) {
					// Substring.between doesn't work because display can have ().
					produced = "(" +
						StringUtils.substringAfterLast(TextUtils.toString(coding), "(");
					if (StringUtils.containsIgnoreCase(value, produced)) {
						return produced;
					}
				}
				log.info("{} <> {}", value, produced);
			} else if (base instanceof InstantType) {
				// Deal with missing data in V2 timestamps.
				String[] parts = value.split("[\\-+]");
				if (parts.length > 1) {
					parts[1] = StringUtils.right(value, parts[1].length() + 1);
				}
				if (produced.startsWith(parts[0]) && parts.length == 1 || produced.endsWith(parts[1])) {
					return produced;
				}
			} else if (base instanceof Organization org && "CE".equals(v2Type)) {
				// Deal with special case of CE -> Organization for MVX Codes
				produced = TextUtils.toString(org.getIdentifierFirstRep()).replace("#", " ");
				if (StringUtils.containsIgnoreCase(value, produced)) {
					return produced;
				}
			}
		}
		log.info("Produced {} <> value {}", produced, value);
		return null;
	}
	
	private void getFields(Segment segment, ComesFrom from) {
		fieldValues = null;
		fieldName = segment.getName() + "-" + from.field();
		
		if (from.fixed().isEmpty()) {
			fieldValues = getFieldValues(segment, from);
			if (from.component() > 0) {
				fieldName += "-" + from.component();
			}
		} else {
			ST st = new ST(null);
			try {
				st.setValue(from.fixed());
			} catch (DataTypeException e) {
				// Won't happen because message is null for ST.
			}
			fieldValues = new ST[] { st };
			fieldName = "fixed value '" + from.fixed() + "'"; 
		}
	}
	
	private String getMappedValue(ComesFrom from, Type type) {
		String value;
		Mapping m = null;
		if (!from.map().isEmpty()) {
			m = Mapping.getMapping(from.map());
			value = ParserUtils.toString(type);
			String oldValue = value;
			Coding coding = m.mapCode(value);
			if (coding != null) {
				value = coding.hasCode() ? coding.getCode() : null;
			}
			fieldName += " mapped from " + oldValue + " by " + from.map();
		} else {
			value = TextUtils.toString(type);
		}
		return value;
	}
	

	private List<IBase> getProducedItems(Resource res, ComesFrom from) {
		List<IBase> l = null;
		try {
			String path = StringUtils.substringAfter(from.path(), ".");
			// It's legit for the resource to be missing if the field is empty, which
			// we don't know yet.
			l = (res == null) ? Collections.emptyList() : PathUtils.getAll(res, path);
		} catch (Exception e) {
			log.warn("{} processing {}: {}", e.getClass().getSimpleName(), from.path(), e.getMessage());
			throw e;
		}
		return l;
	}
	
	private Type[] getFieldValues(Segment segment, ComesFrom from) {
		Type[] t = null;
		if (from.field() > 0) {
			t = ParserUtils.getFields(segment, from.field());
			if (from.component() > 0) {
				for (int i = 0; i < t.length; i++) {
					t[i] = DatatypeConverter.adjustIfVaries(t[i]);
					t[i] = ParserUtils.getComponent(t[i], from.component()-1);
				}
			}
		}
		return t;
	}
	private Class<? extends AbstractSegmentParser> prepareForTest(MessageParser mp, NamedSegment segment) throws ClassNotFoundException {
		switch (segment.segment().getName()) {
		case "RCP":
			Parameters params = mp.createResource(Parameters.class);
			params.addParameter("_search", "");
			break;
		case "ORC", "RXA", "RXR":
			IzDetail.testWith(mp);
			break;
		case "PD1":
			mp.createResource(Patient.class);
			break;
		default:
			break;
		}
		String parserClass = PIDParser.class.getPackageName() + "." + segment.segment().getName() + "Parser";
		@SuppressWarnings("unchecked")
		Class<? extends AbstractSegmentParser> parser = (Class<? extends AbstractSegmentParser>) PIDParser.class.getClassLoader().loadClass(parserClass);
		return parser;
	}
	static Stream<Message> getTestMessages1() {
		int[] found = { 0 }; 
		return getTestMessages().filter(t -> ++found[0] == 1);
	}
}
