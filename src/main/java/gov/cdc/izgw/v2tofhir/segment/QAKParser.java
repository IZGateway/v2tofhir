package gov.cdc.izgw.v2tofhir.segment;

import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity;
import org.hl7.fhir.r4.model.OperationOutcome.IssueType;
import org.hl7.fhir.r4.model.OperationOutcome.OperationOutcomeIssueComponent;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Segment;
import gov.cdc.izgw.v2tofhir.converter.DatatypeConverter;
import gov.cdc.izgw.v2tofhir.converter.MessageParser;
import gov.cdc.izgw.v2tofhir.utils.ParserUtils;
import lombok.extern.slf4j.Slf4j;

/**
 * Parser for QAK Segments
 * 
 * The QAK segment populates OperationOutcome.issue referenced by any generated MessageHeader.response.details
 * with the code from QAK-2 field.
 * 
 * @author Audacious Inquiry
 */
@ComesFrom(path="OperationOutcome[1].meta.tag.code", source="QAK-1")
@ComesFrom(path="OperationOutcome[1].meta.tag.system", fixed="QueryTag")
@ComesFrom(path="OperationOutcome[1].issue.code", source="QAK-2")
@ComesFrom(path="OperationOutcome[1].issue.severity", source="QAK-2")
@ComesFrom(path="OperationOutcome[1].issue.details", source="QAK-2")
@Slf4j
public class QAKParser extends AbstractSegmentParser {
	static {
		log.debug("{} loaded", QAKParser.class.getName());
	}
	/**
	 * Construct a new QAKParser
	 * 
	 * @param messageParser	The messageParser to construct this parser for.
	 */
	public QAKParser(MessageParser messageParser) {
		super(messageParser, "QAK");
	}

	@Override
	public void parse(Segment qak) throws HL7Exception {
		Coding responseStatus = DatatypeConverter.toCoding(ParserUtils.getField(qak, 2), "0208");
		if (responseStatus != null) {
			// QAK Creates its own OperationOutcome resource.
			OperationOutcome oo = createResource(OperationOutcome.class);
			
			String queryTag = ParserUtils.toString(getField(qak, 1));
			oo.getMeta().addTag("QueryTag", queryTag, null);
			
			OperationOutcomeIssueComponent issue = oo.addIssue();
			if (issue != null) {
				if (responseStatus.hasCode()) {
					switch (responseStatus.getCode().toUpperCase()) {
					case "AE":
						issue.setCode(IssueType.INVALID);
						issue.setSeverity(IssueSeverity.ERROR);
						break;
					case "AR":
						issue.setCode(IssueType.PROCESSING);
						issue.setSeverity(IssueSeverity.FATAL);
						break;
					case "NF", "OK":
					default:
						issue.setCode(IssueType.INFORMATIONAL);
						issue.setSeverity(IssueSeverity.INFORMATION);
						break;
					}
					issue.setDetails(new CodeableConcept().addCoding(responseStatus));
				} else {
					issue.setCode(IssueType.UNKNOWN);
					issue.setSeverity(IssueSeverity.INFORMATION);
				}
			} else {
				warn("Missing {} segment in message while processing {}", "MSA", qak);
			}
		}
	}
}
