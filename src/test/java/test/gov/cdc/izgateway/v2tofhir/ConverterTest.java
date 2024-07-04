package test.gov.cdc.izgateway.v2tofhir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StreamTokenizer;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeType;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.InstantType;
import org.hl7.fhir.r4.model.IntegerType;
import org.hl7.fhir.r4.model.PositiveIntType;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.TimeType;
import org.hl7.fhir.r4.model.UnsignedIntType;
import org.hl7.fhir.r4.model.UriType;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Composite;
import ca.uhn.hl7v2.model.Primitive;
import ca.uhn.hl7v2.model.Type;
import ca.uhn.hl7v2.model.Varies;
import gov.cdc.izgw.v2tofhir.converter.DatatypeConverter;
import gov.cdc.izgw.v2tofhir.converter.MessageParser;
import gov.cdc.izgw.v2tofhir.utils.Mapping;
import gov.cdc.izgw.v2tofhir.utils.ParserUtils;
import gov.cdc.izgw.v2tofhir.utils.Systems;
import lombok.extern.slf4j.Slf4j;
import test.gov.cdc.izgateway.TestUtils;

@Slf4j
/**
 * This test checks a V2 conversion for FHIR Parsing and validates the result against the FHIR US Core
 * It requires a running CDC / Microsoft generated FHIR Converter at localhost:8080 
 */
class ConverterTest extends TestBase {
	private static final String base = "http://localhost:8080/fhir-converter/convert-to-fhir";
	private static final String messageTemplate = 
			  "{"
 			  + "\n \"input_data\": \"%s\","
			  + "\n \"input_type\": \"vxu\","
			  + "\n \"root_template\": \"VXU_V04\","
			  + "\n \"rr_data\": null"
			+ "\n}";
	private static final FhirContext ctx = FhirContext.forR4();
	private static final IParser fhirParser = ctx.newJsonParser().setPrettyPrint(true);
	// Force load of Mapping class before any testing starts.
	@SuppressWarnings("unused")
	private static final Class<Mapping> MAPPING_CLASS = Mapping.class; 
	@ParameterizedTest
	@MethodSource("getTestMessages")
	@Disabled("Used for testing a local microsoft V2 converter")
	void testConversion(String hl7Message) throws IOException, ParseException {
		HttpURLConnection con = getUrlConnection();
		String value = messageTemplate.formatted(StringEscapeUtils.escapeJson(hl7Message)); 
		con.setRequestProperty("Content-Type", "application/json");
		OutputStream os = con.getOutputStream();
		log.info("Request: {}", value);
		
		os.write(value.getBytes(StandardCharsets.UTF_8));
		os.flush();
		os.close();
		int statusCode = con.getResponseCode();
		try {
			assertEquals(HttpURLConnection.HTTP_OK, statusCode);
		} catch (Error e) {
			String es = IOUtils.toString(con.getErrorStream(), StandardCharsets.UTF_8);
			log.error("Error: {}", es);
			throw e;
		}
		InputStream is = con.getInputStream();
		String fhirResult = testMicrosoftConverterResponse(is);
		log.info("HL7 V2: {}", hl7Message);
		log.info("FHIR: {}", fhirResult);
		IParser p = ctx.newJsonParser();
		@SuppressWarnings("unused")
		Bundle b = p.parseResource(Bundle.class, fhirResult);
	}
	
	@ParameterizedTest
	@MethodSource("getTestDataForCoding")
	void testCompositeConversionsForCodings(Type t) throws HL7Exception {
		CodeableConcept cc = DatatypeConverter.toCodeableConcept(t);
		Coding coding = DatatypeConverter.toCoding(t);
		if (cc != null && coding != null) {
			assertEquals(TestUtils.toString(cc.getCodingFirstRep()), TestUtils.toString(coding));
		}
		
		String first = ParserUtils.toString(t);
		if ("HD".equals(t.getName())) {
			// HD has System but not code
			assertFalse(coding.hasCode());
			assertTrue(coding.hasSystem());
		} else {
			String code = coding == null ? null : coding.getCode();
			assertEquals(StringUtils.isBlank(first), StringUtils.isBlank(code));
			if (!StringUtils.isBlank(first)) {
				assertEquals(first, code);
			}
		}
		
		if (coding != null) {
			String display = coding.hasUserData("originalDisplay") ? (String) coding.getUserData("originalDisplay") : coding.getDisplay();
			assertEquals(StringUtils.isBlank(getComponent(t, 2)), StringUtils.isBlank(display));
		
			if (hasDisplay(t) && hasComponent(t, 2)) {
				// Either both are blank or both are filled.
				if (!StringUtils.isBlank(first)) {
					// If both are filled, check values.
					String[] a = { getComponent(t, 2), Mapping.getDisplay(coding) };
					List<String> l = Arrays.asList(a);
					Supplier<Boolean> test = null;
					
					if (StringUtils.isNotBlank(coding.getDisplay())) {
						// Display came from component 2, or it was properly mapped.
						test = () -> l.contains(coding.getDisplay());
					} else {
						// Display is empty and there are no good values to use.
						test = () -> StringUtils.isAllEmpty(a);
					}
					assertEquals(Boolean.TRUE, test.get(),
						"Display value " + coding.getDisplay() + " expected to be from " + l);
				}
			}
		}
		String encoded = ParserUtils.unescapeV2Chars(t.encode());
		if (coding != null && coding.hasSystem()) {
			if (coding.getSystem().contains(":")) {
				// There is a proper URI, verify we got it correctly.
				Collection<String> names = Systems.getSystemNames(coding.getSystem());
				if (coding.hasUserData("originalSystem")) {
					String originalSystem = (String)coding.getUserData("originalSystem");
					originalSystem = StringUtils.trim(originalSystem); 
					if (!names.contains(originalSystem)) {
						names.add(originalSystem);
					}
				}
				names = names.stream().map(StringUtils::upperCase).toList();
				// We know about this URI
				assertNotNull(names);
				List<String> fields = Arrays.asList(encoded.split("\\^")).stream()
						.map(StringUtils::trim).map(StringUtils::upperCase).toList();
				List<String> f = names.stream().filter(n -> fields.contains(n)).toList();
				// At least one of the names for the system is in the message fields.
				assertTrue(f.size() > 0, "Could not find any of " + names + " in " + fields);
				
			} else {
				assertTrue(
					StringUtils.contains(encoded, coding.getSystem()), 
					t.encode() + " does not contain " + coding.getSystem()
				);
			}
		}
		System.out.println(encoded);
		System.out.println(TestUtils.toString(coding));
	}
	private boolean hasDisplay(Type t) {
		return Arrays.asList("CE", "CNE", "CWE").contains(t.getName());
	}
	private boolean hasComponent(Type t, int index) {
		if (t instanceof Composite comp) {
			Type types[] = comp.getComponents();
			try {
				return types.length >= index && !types[index].isEmpty();
			} catch (HL7Exception e) {
				return false;
			}
		}
		return false;
	}
	private String getComponent(Type t, int index) throws HL7Exception {
		if (t instanceof Varies v) {
			t = v.getData();
		}
		if (t instanceof Composite comp) {
			Type types[] = comp.getComponents();
			return ParserUtils.unescapeV2Chars(types[index-1].encode());
		}
		return null;
	}
	
	@ParameterizedTest
	@MethodSource("getTestDataForIdentifier")
	void testCompositeConversionsForIdentifier(Type t) throws HL7Exception {
		Coding coding = DatatypeConverter.toCoding(t);
		Identifier id = DatatypeConverter.toIdentifier(t);
		if (id != null && coding != null) {
			assertEquals(coding.getCode(), id.getValue());
			assertEquals(coding.getSystem(), id.getSystem());
		}
		String first = ParserUtils.toString(t);
		if ("HD".equals(t.getName())) {
			assertEquals(first, id.getSystem());
		} else {
			assertEquals(first, id.getValue());
		}
	}
	
	@ParameterizedTest
	@MethodSource("getTestPrimitives")
	void testPrimitiveConversionsDataCheck(Type t) throws HL7Exception {
		assertNotNull(t);
		assertTrue(t instanceof Primitive || 
				  (t instanceof Varies v && v.getData() instanceof Primitive));
	}
	
	@ParameterizedTest
	@MethodSource("getTestPrimitives")
	void testPrimitiveConversionsIntegerType(Type t) throws HL7Exception {
		TestUtils.compareStringValues(DatatypeConverter::toIntegerType, IntegerType::new, ConverterTest::normalizeNumbers, t, IntegerType.class);
	}
	@ParameterizedTest
	@MethodSource("getTestPrimitives")
	void testPrimitiveConversionsPositiveIntType(Type t) throws HL7Exception {
		TestUtils.compareStringValues(DatatypeConverter::toPositiveIntType, PositiveIntType::new, ConverterTest::normalizeNumbers, t, PositiveIntType.class);
	}
	@ParameterizedTest
	@MethodSource("getTestPrimitives")
	void testPrimitiveConversionsUnsignedIntType(Type t) throws HL7Exception {
		TestUtils.compareStringValues(DatatypeConverter::toUnsignedIntType, UnsignedIntType::new, ConverterTest::normalizeNumbers, t, UnsignedIntType.class); 
	}
	
	private static String normalizeNumbers(String nm) {
		nm = StringUtils.substringBeforeLast(nm, ".");
		return nm.split("\\s+")[0];
	}
	@ParameterizedTest
	@MethodSource("getTestPrimitives")
	void testPrimitiveConversionsDateTimeType(Type t) throws HL7Exception {
		TestUtils.compareStringValues(DatatypeConverter::toDateTimeType, DateTimeType::new, DatatypeConverter::removeIsoPunct, t, DateTimeType.class);
	}
	@ParameterizedTest
	@MethodSource("getTestPrimitives")
	void testPrimitiveConversionsDateType(Type t) throws HL7Exception {
		TestUtils.compareStringValues(DatatypeConverter::toDateType, DateType::new, DatatypeConverter::removeIsoPunct, t, DateType.class);
	}
	@ParameterizedTest
	@MethodSource("getTestPrimitives")
	void testPrimitiveConversionsInstantType(Type t) throws HL7Exception {
		TestUtils.compareStringValues(DatatypeConverter::toInstantType, InstantType::new, DatatypeConverter::removeIsoPunct, t, InstantType.class); 
	}
	@ParameterizedTest
	@MethodSource("getTestPrimitives")
	// @Disabled(value="HAPI's TimeType has a bug in which they don't parse the actual value, just store it")
	void testPrimitiveConversionsTimeType(Type t) throws HL7Exception {
		TestUtils.compareStringValues(DatatypeConverter::toTimeType, TimeType::new, DatatypeConverter::removeIsoPunct, t, TimeType.class); 
	}
	@ParameterizedTest
	@MethodSource("getTestPrimitives")
	void testPrimitiveConversionsStringType(Type t) throws HL7Exception {
		TestUtils.compareStringValues(DatatypeConverter::toStringType, StringType::new, null, t, StringType.class);
	}
	@ParameterizedTest
	@MethodSource("getTestPrimitives")
	void testPrimitiveConversionsUriType(Type t) throws HL7Exception {
		TestUtils.compareStringValues(DatatypeConverter::toUriType, UriType::new, StringUtils::strip, t, UriType.class);
	}
	@ParameterizedTest
	@MethodSource("getTestPrimitives")
	void testPrimitiveConversionsCodeType(Type t) throws HL7Exception {
		TestUtils.compareStringValues(
				DatatypeConverter::toCodeType, 
				s -> new CodeType(StringUtils.strip(s)), 
				StringUtils::strip, t, 
				CodeType.class
		);
	}
	
	@ParameterizedTest
	@MethodSource("getTestSegments")
	void testSegmentConversions(NamedSegment segment) throws HL7Exception {
		MessageParser p = new MessageParser();
		System.out.println(segment.segment());
		Bundle b = p.createBundle(Collections.singleton(segment.segment()));
		System.out.println(fhirParser.encodeResourceToString(b));
	}
	
	private String testMicrosoftConverterResponse(InputStream is) throws IOException, ParseException {
		String response = IOUtils.toString(is, StandardCharsets.UTF_8);
		StreamTokenizer t = new StreamTokenizer(new StringReader(response));
		t.lowerCaseMode(false);
		t.quoteChar('"');
		t.whitespaceChars(0, ' ');
		t.wordChars('!', 0xFF);
		t.eolIsSignificant(false);
		
		StringBuilder b = new StringBuilder();
		String fhirResult = null;
		while (t.nextToken() != StreamTokenizer.TT_EOF) {
			if (t.ttype == '"') { 
				switch (t.sval) {
				case "FhirResource":
					skipColon(t);
					fhirResult = getJsonContent(t, b);
					break;
				case "Status":
					skipColon(t);
					assertEquals('"', t.nextToken());
					assertEquals("OK", t.sval);
					break;
				}
			}
		}
		return fhirResult;
	}

	private void skipColon(StreamTokenizer t) throws IOException, ParseException {
		if (t.nextToken() == StreamTokenizer.TT_WORD) {
			if (":".equals(t.sval))
				return;
		}
		throw new ParseException("A colon ':' was expected.", t.lineno());
	}
	private String getJsonContent(StreamTokenizer t, StringBuilder b) throws IOException, ParseException {
		int braceCount = 0;
		while (t.nextToken() != StreamTokenizer.TT_EOF) {
			switch (t.ttype) {
			case StreamTokenizer.TT_WORD:
				b.append(t.sval);
				if ("{".equals(t.sval)) {
					braceCount++;
				} else if ("}".equals(t.sval)) {
					if (--braceCount == 0) {
						return b.toString();
					}
				}
				break;
			case '"':
				b.append('"');
				b.append(StringEscapeUtils.escapeJava(t.sval));
				b.append('"');
				break;
			}
			// Insert whitespace between each token
			b.append(' ');
		}
		throw new ParseException("Unexpected EOF", t.lineno());
	}

	private HttpURLConnection getUrlConnection() throws IOException {
		URL url = new URL(base);
		HttpURLConnection con = (HttpURLConnection) url.openConnection();
		con.setRequestMethod("POST");
		con.setDoInput(true);
		con.setDoOutput(true);
		return con;
	}
}
