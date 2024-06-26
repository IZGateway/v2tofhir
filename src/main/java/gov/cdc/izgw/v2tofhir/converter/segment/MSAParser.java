package gov.cdc.izgw.v2tofhir.converter.segment;

import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.MessageHeader;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.MessageHeader.MessageHeaderResponseComponent;
import org.hl7.fhir.r4.model.MessageHeader.ResponseType;
import org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity;
import org.hl7.fhir.r4.model.OperationOutcome.IssueType;
import org.hl7.fhir.r4.model.OperationOutcome.OperationOutcomeIssueComponent;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Segment;
import gov.cdc.izgw.v2tofhir.converter.DatatypeConverter;
import gov.cdc.izgw.v2tofhir.converter.MessageParser;
import gov.cdc.izgw.v2tofhir.converter.ParserUtils;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MSAParser extends AbstractSegmentParser {

	public MSAParser(MessageParser messageParser) {
		super(messageParser, "MSA");
	}

	@Override
	public void parse(Segment msa) throws HL7Exception {
		MessageHeader mh = getFirstResource(MessageHeader.class);
		if (mh == null) {
			// Log but otherwise ignore this.
			log.error("MSH segment not parsed");
			mh = createResource(MessageHeader.class);
		}
		Coding acknowledgementCode = DatatypeConverter.toCoding(ParserUtils.getField(msa, 1), "0008");
		MessageHeaderResponseComponent response = mh.getResponse();
		
		OperationOutcomeIssueComponent issue = createIssue();
		
		if (acknowledgementCode == null) {
			response.setCode(ResponseType.OK);
		} else { 
			issue.getDetails().addCoding(acknowledgementCode);
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
		response.setIdentifierElement(DatatypeConverter.toIdType(ParserUtils.getField(msa, 2)));
	}
	
	private OperationOutcomeIssueComponent createIssue() {
		OperationOutcomeIssueComponent issue = null;
		OperationOutcome oo = getFirstResource(OperationOutcome.class);
		if (oo == null) {
			oo = createResource(OperationOutcome.class);
		}
		issue = oo.getIssueFirstRep();
		getMessageParser().getContext().setProperty(issue);
		return issue;
	}
}