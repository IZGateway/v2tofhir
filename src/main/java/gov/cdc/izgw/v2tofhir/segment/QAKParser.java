package gov.cdc.izgw.v2tofhir.segment;

import java.util.ArrayList;
import java.util.List;

import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity;
import org.hl7.fhir.r4.model.OperationOutcome.IssueType;
import org.hl7.fhir.r4.model.OperationOutcome.OperationOutcomeIssueComponent;
import org.hl7.fhir.r4.model.StringType;

import gov.cdc.izgw.v2tofhir.annotation.ComesFrom;
import gov.cdc.izgw.v2tofhir.annotation.Produces;
import gov.cdc.izgw.v2tofhir.converter.MessageParser;
import lombok.extern.slf4j.Slf4j;

/**
 * Parser for QAK Segments
 * 
 * The QAK segment populates OperationOutcome.issue referenced by any generated MessageHeader.response.details
 * with the code from QAK-2 field.
 * 
 * @author Audacious Inquiry
 */
@Produces(segment="QAK", resource=OperationOutcome.class)
@Slf4j
public class QAKParser extends AbstractSegmentParser {
	private OperationOutcome oo;
	
	private static List<FieldHandler> fieldHandlers = new ArrayList<>();
	/** The coding system to use for query tags in metadata */
	public static final String QUERY_TAG_SYSTEM = "QueryTag";

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
		if (fieldHandlers.isEmpty()) {
			initFieldHandlers(this, fieldHandlers);
		}
	}

	
	@Override
	public List<FieldHandler> getFieldHandlers() {
		return fieldHandlers;
	}
	
	@Override
	public IBaseResource setup() {
		// QAK Creates its own OperationOutcome resource.
		oo = createResource(OperationOutcome.class);
		return oo;
	}
	
	/**
	 * Sets OperationOutcome[1].meta.tag to the Query Tag in QAK-1
	 * @param queryTag	The query tag
	 */
	@ComesFrom(path="OperationOutcome[1].meta.tag", field = 1, comment="Sets meta.tag.system to QueryTag, and code to value of QAK-1")
	public void setMetaTag(StringType queryTag) {
		oo.getMeta().addTag(new Coding(QUERY_TAG_SYSTEM, queryTag.getValue(), null));
	}
	
	/**
	 * Set OperationOutcome.issue.details, issue.code, and issue.severity
	 * 
	 * Sets issue.details to details
	 * Maps issue.code and issue.severity from details
	 * 
	 * @param details	The coding found in QAK-2 from table 0208
	 */
	@ComesFrom(path="OperationOutcome[1].issue.details", field = 2, table = "0208", 
			   also={ "OperationOutcome[1].issue.code", 
					  "OperationOutcome[1].issue.severity"})
	public void setIssueDetails(Coding details) {
		OperationOutcomeIssueComponent issue = oo.addIssue().setDetails(new CodeableConcept().addCoding(details));

		if (details.hasCode()) {
			switch (details.getCode().toUpperCase()) {
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
		} else {
			issue.setCode(IssueType.UNKNOWN);
			issue.setSeverity(IssueSeverity.INFORMATION);
		}
	}
}
