package gov.cdc.izgw.v2tofhir.segment;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.MessageHeader;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity;
import org.hl7.fhir.r4.model.OperationOutcome.IssueType;
import org.hl7.fhir.r4.model.OperationOutcome.OperationOutcomeIssueComponent;

import gov.cdc.izgw.v2tofhir.annotation.ComesFrom;
import gov.cdc.izgw.v2tofhir.annotation.Produces;
import gov.cdc.izgw.v2tofhir.converter.MessageParser;
import gov.cdc.izgw.v2tofhir.utils.Mapping;
import gov.cdc.izgw.v2tofhir.utils.ParserUtils;
import lombok.extern.slf4j.Slf4j;

/**
 * Parser for an ERR segment.
 * 
 * @author Audacious Inquiry
 */

/**
 * Parse an ERR segment into an OperationOutcome resource.
 * 
 * If no OperationOutcome already exists for this message, a new one is created.
 * One OperationOutcome.issue is created for each ERR segment.
 * 
 * OperationOutcome.issue.location is set from ERR-2
 * OperationOutcome.issue.code is set from ERR-3
 * OperationOutcome.issue.severity is set from ERR-4
 * OperationOutcome.issue.detail is set from ERR-3 and ERR-5
 * OperationOutcome.issue.diagnostics is set from ERR-7 and ERR-8 if present.
 *  
 */
@Produces(segment="ERR", resource = OperationOutcome.class, extra = MessageHeader.class)
@Slf4j
public class ERRParser extends AbstractSegmentParser {
	private static final String SUCCESS = "success";
	private OperationOutcomeIssueComponent issue;
	
	static {
		log.debug("{} loaded", ERRParser.class.getName());
	}
	private static final LinkedHashMap<String, String[]> errorCodeMap = new LinkedHashMap<>();
	private static final String NOT_SUPPORTED = "not-supported";
	private static final String[][] errorCodeMapping = {
			{ SUCCESS, "0", "Message accepted", "Success. Optional, as the AA conveys success. Used for systems that must always return a status code." },
			{ "structure", "100", "Segment sequence error", "Error: The message segments were not in the proper order, or required segments are missing." },
			{ "required", "101", "Required field missing", "Error: A required field is missing from a segment" },
			{ "value", "102", "Data type error", "Error: The field contained data of the wrong data type, e.g. an NM field contained" },
			{ "code-invalid", "103", "Table value not found", "Error: A field of data type ID or IS was compared against the corresponding table, and no match was found." },
			{ NOT_SUPPORTED, "200", "Unsupported message type", "Rejection: The Message Type is not supported." },
			{ NOT_SUPPORTED, "201", "Unsupported event code", "Rejection: The Event Code is not supported." },
			{ NOT_SUPPORTED, "202", "Unsupported processing id", "Rejection: The Processing ID is not supported." },
			{ NOT_SUPPORTED, "203", "Unsupported version id", "Rejection:  The Version ID is not supported." },
			{ "not-found", "204", "Unknown key identifier", "Rejection: The ID of the patient, order, etc., was not found. Used for transactions other than additions, e.g. transfer of a non-existent patient." },
			{ "conflict", "205", "Duplicate key identifier", "Rejection: The ID of the patient, order, etc., already exists. Used in response to addition transactions (Admit, New Order, etc.)." },
			{ "lock-error", "206", "Application record locked", "Rejection: The transaction could not be performed at the application storage level, e.g., database locked." },
			{ "exception", "207", "Application internal error", "Rejection: A catchall for internal errors not explicitly covered by other codes." }
	};
	private static final List<FieldHandler> fieldHandlers = new ArrayList<>();
	static {
		for (String[] mapping : errorCodeMapping) {
			errorCodeMap.put(mapping[1], mapping);
		}
	}
	
	/**
	 * Construct the ERR Segment parser for the given MessageParser
	 * 
	 * @param messageParser	The messageParser that is using this segment parser.
	 */
	public ERRParser(MessageParser messageParser) {
		super(messageParser, "ERR");
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
		OperationOutcome oo;
		// Create a new OperationOutcome.issue for each ERR resource
		oo = getFirstResource(OperationOutcome.class);
		if (oo == null) {
			oo = createResource(OperationOutcome.class);
			MessageHeader mh = getFirstResource(MessageHeader.class);
			if (mh == null) {
				mh = createResource(MessageHeader.class);
			}
			// If MessageHeader or OperationOutcome has to be created, 
			// link them in MessageHeader.response.details
			// Normally this is already done in MSAParser, but if processing
			// an incomplete message (e.g., ERR segment but w/o MSA), this ensures 
			// that the parser has the expected objects to work with.
			mh.getResponse().setDetails(ParserUtils.toReference(oo, mh, "details"));
		}
		issue = oo.addIssue();
		return oo;
	}
	
	/**
	 * Add the error location to the operation outcome.
	 * @param location	The location
	 */
	@ComesFrom(path = "OperationOutcome.issue.location", field = 2)
	public void setLocation(StringType location) {
		issue.getLocation().add(location);
	}
	
	/**
	 * Set the details on an operation outcome
	 * @param errorCode	The error code
	 */
	@ComesFrom(path="OperationOutcome.issue.details", table="0357", field=3, comment="Also sets issue.code")
	@ComesFrom(path="OperationOutcome.issue.details", table="0533", field=5)
	public void setDetails(CodeableConcept errorCode) {
		if (errorCode == null) {
			return;
		}
		for (Coding coding: errorCode.getCoding()) {
			issue.getDetails().addCoding(coding);
		}
		for (Coding c: errorCode.getCoding()) {
			// If from table 0357
			if (!Mapping.v2Table("0357").equals(c.getSystem())) {  // Check the system.
				continue;
			}
			String[] map = errorCodeMap.get(c.getCode());
			if (map == null) {
				issue.setCode(IssueType.PROCESSING);
				continue;
			}
			try {
				if (SUCCESS.equals(map[0])) {
					// Some versions of FHIR support "success", others don't.
					issue.setCode(IssueType.INFORMATIONAL);
				} else {
					IssueType type = IssueType.fromCode(map[0]); 
					issue.setCode(type);
				}
			} catch (FHIRException fex) {
				warnException("Unexpected FHIRException: {}", fex.getMessage(), fex);
			}
		}
	}
	
	/**
	 * Set the severity
	 * @param severity	Severity
	 */
	@ComesFrom(path="OperationOutcome.issue.severity", field = 4, table = "0516", map = "ErrorSeverity")
	public void setSeverity(CodeableConcept severity) {
		for (Coding coding: severity.getCoding()) {
			issue.getDetails().addCoding(coding);
		}
		for (Coding sev : severity.getCoding()) {
			if (sev == null || !sev.hasCode()) {
				continue;
			}
			switch (sev.getCode().toUpperCase()) {
			// F is not really a code in table 0516, but if it shows up, treat it as if it meant fatal.
			case "F":	issue.setSeverity(IssueSeverity.FATAL); break;
			// These are the legit codes.
			case "I":	issue.setSeverity(IssueSeverity.INFORMATION); break;
			case "W":	issue.setSeverity(IssueSeverity.WARNING); break;
			case "E":	// Fall through
			// Everything else maps to error
			default: 	issue.setSeverity(IssueSeverity.ERROR); break;
			}
		}
	}
	
	/**
	 * Set the diagnostic messages in MSA-7 and MSA-8 in the OperationOutcome
	 * @param diagnostics	The diagnostics to report
	 */
	@ComesFrom(path="OperationOutcome.issue.diagnostics", field=7, comment="Diagnostics")
	@ComesFrom(path="OperationOutcome.issue.diagnostics", field=8, comment="User Message")
	public void setDiagnostics(StringType diagnostics) {
		if (issue.hasDiagnostics()) {
			issue.setDiagnostics(issue.getDiagnostics() + "\n" + diagnostics);
		} else {
			issue.setDiagnosticsElement(diagnostics);
		}
	}
}
