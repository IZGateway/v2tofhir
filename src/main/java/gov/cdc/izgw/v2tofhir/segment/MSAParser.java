package gov.cdc.izgw.v2tofhir.segment;

import java.util.ArrayList;
import java.util.List;

import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.MessageHeader;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.MessageHeader.MessageHeaderResponseComponent;
import org.hl7.fhir.r4.model.MessageHeader.ResponseType;
import org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity;
import org.hl7.fhir.r4.model.OperationOutcome.IssueType;
import org.hl7.fhir.r4.model.OperationOutcome.OperationOutcomeIssueComponent;

import gov.cdc.izgw.v2tofhir.annotation.ComesFrom;
import gov.cdc.izgw.v2tofhir.annotation.Produces;
import gov.cdc.izgw.v2tofhir.converter.MessageParser;
import gov.cdc.izgw.v2tofhir.utils.ParserUtils;
import lombok.extern.slf4j.Slf4j;

/**
 * Parse an MSA segment into MessageHeader and OperationOutcome resources.
 * 
 * @author Audacious Inquiry
 * @see <a href="https://hl7.org/fhir/uv/v2mappings/2024Jan/ConceptMap-segment-msa-to-messageheader.html">V2 to FHIR: MSA to MessageHeader</a>
 */
@Produces(segment="MSA", resource=OperationOutcome.class, extra=MessageHeader.class)
@Slf4j
public class MSAParser extends AbstractSegmentParser {
	private static final List<FieldHandler> fieldHandlers = new ArrayList<>();
	private MessageHeader mh = null;
	private MessageHeaderResponseComponent response = null;
	private OperationOutcomeIssueComponent issue;
	
	static {
		log.debug("{} loaded", MSAParser.class.getName());
	}
	
	/**
	 * Construct a SegmentParser for MSA Segments
	 * 
	 * @param messageParser	The messageParser using this MSAParser
	 */
	public MSAParser(MessageParser messageParser) {
		super(messageParser, "MSA");
		if (fieldHandlers.isEmpty()) {
			FieldHandler.initFieldHandlers(this, fieldHandlers);
		}
	}
	
	@Override
	public List<FieldHandler> getFieldHandlers() {
		return fieldHandlers;
	}

	@Override
	public IBaseResource setup() {
		mh = getFirstResource(MessageHeader.class);
		if (mh == null) {
			// Log but otherwise ignore this.
			log.error("MSH segment not parsed");
			mh = createResource(MessageHeader.class);
		}
		response  = mh.getResponse();
		OperationOutcome oo = createResource(OperationOutcome.class);
		issue = oo.getIssueFirstRep();
		mh.getResponse().setDetails(ParserUtils.toReference(oo, mh, "details"));
		return mh;
	}
	
	/**
	 * Set the response identifier from MSA-2
	 * @param identifier	The identifier
	 */
	@ComesFrom(path = "MessageHeader.response.identifier", field = 2)
	public void setIdentifier(IdType identifier) {
		mh.getResponse().setIdentifierElement(identifier);
	}
	/**
	 * Set the acknowledgementCode and severity from MSA-1
	 * @param acknowledgementCode	The Acknowledgement code from MSA-1
	 */
	@ComesFrom(path = "OperationOutcome.issue.details.coding.code", table = "0008", field = 1, 
			   also = { "OperationOutcome.issue.severity",
					    "OperationOutcome.issue.code",
					    "MessageHeader.response.code"
					  }
	)
	public void setAcknowledgementCode(Coding acknowledgementCode) { 
		if (acknowledgementCode == null) {
			response.setCode(ResponseType.OK);
		} else { 
			issue.getDetails().addCoding(acknowledgementCode);
			if (acknowledgementCode.hasCode()) {
				switch (acknowledgementCode.getCode()) {
				case "AA", "CA": 
					response.setCode(ResponseType.OK);
					issue.setCode(IssueType.INFORMATIONAL);
					issue.setSeverity(IssueSeverity.INFORMATION);
					break;
				case "AE", "CE": 
					response.setCode(ResponseType.TRANSIENTERROR);
					issue.setCode(IssueType.TRANSIENT);
					issue.setSeverity(IssueSeverity.ERROR);
					break;
				case "AR", "CR": 
					response.setCode(ResponseType.FATALERROR); 
					issue.setCode(IssueType.PROCESSING);
					issue.setSeverity(IssueSeverity.FATAL);
					break;
				default:
					break;
				}
			}
		}
	}
}
