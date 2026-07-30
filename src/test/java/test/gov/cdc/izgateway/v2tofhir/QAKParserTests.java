package test.gov.cdc.izgateway.v2tofhir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity;
import org.hl7.fhir.r4.model.OperationOutcome.IssueType;
import org.hl7.fhir.r4.model.OperationOutcome.OperationOutcomeIssueComponent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import ca.uhn.hl7v2.model.Segment;
import gov.cdc.izgw.v2tofhir.converter.MessageParser;

/**
 * Tests for QAKParser, verifying the QAK-2 query response status (HL7 table 0208)
 * to OperationOutcome.issue severity and code mapping.
 *
 * @author Audacious Inquiry
 */
class QAKParserTests extends TestBase {

	/**
	 * Parse a single QAK segment and return the OperationOutcome it produces.
	 * @param qakSegment	The QAK segment as a pipe-delimited string
	 * @return	The first OperationOutcome in the resulting bundle
	 * @throws Exception	If the segment cannot be parsed
	 */
	private OperationOutcome parseQak(String qakSegment) throws Exception {
		Segment segment = parseSegment(qakSegment);
		Bundle b = new MessageParser().createBundle(Collections.singleton(segment));
		return b.getEntry().stream()
				.map(Bundle.BundleEntryComponent::getResource)
				.filter(OperationOutcome.class::isInstance)
				.map(OperationOutcome.class::cast)
				.findFirst().orElse(null);
	}

	private OperationOutcomeIssueComponent getOnlyIssue(OperationOutcome oo) {
		assertNotNull(oo, "The bundle should contain an OperationOutcome");
		assertEquals(1, oo.getIssue().size(), "The OperationOutcome should have exactly one issue");
		return oo.getIssueFirstRep();
	}

	@ParameterizedTest
	@CsvSource({
		"TM, warning, multiple-matches",
		"tm, warning, multiple-matches",
		"OK, information, informational",
		"NF, information, informational",
		"AE, error, invalid",
		"AR, fatal, processing",
		"ZZ, information, informational"
	})
	void testQak2StatusMapsToSeverityAndCode(String status, String severity, String code) throws Exception {
		OperationOutcomeIssueComponent issue = getOnlyIssue(parseQak("QAK|Q123|" + status));
		assertEquals(severity, issue.getSeverity().toCode(), "Wrong severity for QAK-2 = " + status);
		assertEquals(code, issue.getCode().toCode(), "Wrong code for QAK-2 = " + status);
	}

	@Test
	void testMissingStatusCodeMapsToUnknown() throws Exception {
		// QAK-2 is present but has no code component, only text.
		OperationOutcomeIssueComponent issue = getOnlyIssue(parseQak("QAK|Q123|^No code here"));
		assertEquals(IssueSeverity.INFORMATION, issue.getSeverity());
		assertEquals(IssueType.UNKNOWN, issue.getCode());
	}

	@Test
	void testTmPreservesDetailsCoding() throws Exception {
		OperationOutcomeIssueComponent issue = getOnlyIssue(parseQak("QAK|Q123|TM"));
		assertTrue(issue.hasDetails(), "issue.details should be populated from QAK-2");
		Coding coding = issue.getDetails().getCodingFirstRep();
		assertEquals("TM", coding.getCode(), "issue.details should preserve the TM code");
		assertNotNull(coding.getSystem(), "issue.details coding should have a system");
		assertTrue(coding.getSystem().contains("0208"),
				"issue.details coding system should reference table 0208 but was " + coding.getSystem());
	}
}
