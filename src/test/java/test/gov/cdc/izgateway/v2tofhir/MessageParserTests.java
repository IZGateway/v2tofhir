package test.gov.cdc.izgateway.v2tofhir;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.text.ParseException;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.instance.model.api.IBase;
import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.PrimitiveType;
import org.hl7.fhir.r4.utils.FHIRPathEngine;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import com.ainq.fhir.utils.YamlParser;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.support.DefaultProfileValidationSupport;
import ca.uhn.fhir.context.support.IValidationSupport;
import ca.uhn.fhir.fhirpath.IFhirPath;
import ca.uhn.fhir.fhirpath.IFhirPath.IParsedExpression;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Message;
import gov.cdc.izgw.v2tofhir.converter.MessageParser;
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

	private static final FhirContext ctx = FhirContext.forR4();
	private static final IValidationSupport support = new DefaultProfileValidationSupport(ctx);
	static {
		ctx.setValidationSupport(support);
	}
	private static final IParser fhirParser = ctx.newJsonParser().setPrettyPrint(true);
	private static final YamlParser yamlParser = new YamlParser(ctx);
	

	// Force load of Mapping class before any testing starts.
	@SuppressWarnings("unused")
	private static final Class<Mapping> MAPPING_CLASS = Mapping.class; 

	@ParameterizedTest
	@MethodSource("testTheData")
	void testTheData(TestData testData) throws Exception {
		System.out.println(testData);
		
		MessageParser p = new MessageParser();
		Bundle b = p.convert(parse(testData.getTestData()));
		testData.evaluateAllAgainst(b);
	}

	IFhirPath getEngine() {
		return ctx.newFhirPath();
	}
	
	static List<TestData> testTheData() {
		return TEST_MESSAGES;
	}
	
	
	@ParameterizedTest
	@MethodSource("getTestMessages")
	void testMessageConversion(Message hl7Message) throws IOException, ParseException {
		MessageParser p = new MessageParser();
		Bundle b = p.convert(hl7Message);
		System.out.println(yamlParser.encodeResourceToString(b));
	}
	
	@ParameterizedTest
	@MethodSource("getTestPIDs")
	void testPIDConversion(NamedSegment segment) throws HL7Exception {
		MessageParser p = new MessageParser();
		System.out.println(segment.segment().encode());
		Bundle b = p.createBundle(Collections.singleton(segment.segment()));
		System.out.println(fhirParser.encodeResourceToString(b));
	}
	
	static List<NamedSegment> getTestPIDs() {
		return getTestSegments("PID");
	}
	
}
