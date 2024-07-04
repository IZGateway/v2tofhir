package gov.cdc.izgw.v2tofhir.converter.segment;

import java.util.LinkedHashMap;

import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.MessageHeader;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity;
import org.hl7.fhir.r4.model.OperationOutcome.IssueType;
import org.hl7.fhir.r4.model.OperationOutcome.OperationOutcomeIssueComponent;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Segment;
import ca.uhn.hl7v2.model.Type;
import gov.cdc.izgw.v2tofhir.converter.DatatypeConverter;
import gov.cdc.izgw.v2tofhir.converter.MessageParser;
import gov.cdc.izgw.v2tofhir.converter.ParserUtils;
import gov.cdc.izgw.v2tofhir.converter.PathUtils;
import lombok.extern.slf4j.Slf4j;

@Slf4j
/**
 * Parser for an ERR segment.
 * 
 * @author Audacious Inquiry
 */
public class ERRParser extends AbstractSegmentParser {
	static {
		log.debug("{} loaded", ERRParser.class.getName());
	}
	private static final LinkedHashMap<String, String[]> errorCodeMap = new LinkedHashMap<>();
	private static final String NOT_SUPPORTED = "not-supported";
	private static final String[][] errorCodeMapping = {
			{ "success", "0", "Message accepted", "Success. Optional, as the AA conveys success. Used for systems that must always return a status code." },
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
	}

	/**
	 * Parse an ERR segment into an OperationOutcome resource.
	 * 
	 * If no OperationOutcome already exists for this message, a new one is created.
	 * One OperationOutcome.issue is created for each ERR segment.
	 * 
	 * OperationOutcome.issue.location is set from ERR-2
	 * 
	 * OperationOutcome.issue.code is set from ERR-3
	 * 
	 * OperationOutcome.issue.severity is set from ERR-4
	 * 
	 * OperationOutcome.issue.detail is set from ERR-3 and ERR-5
	 * 
	 * OperationOutcome.issue.diagnostics is set from ERR-7 and ERR-8 if present.
	 *  
	 * @param err	The ERR segment to parse
	 */
	@Override
	public void parse(Segment err) throws HL7Exception {
		// Create a new OperationOutcome for each ERR resource
		OperationOutcome oo = getFirstResource(OperationOutcome.class);
		if (oo == null) {
			oo = createResource(OperationOutcome.class);
			MessageHeader mh = getFirstResource(MessageHeader.class);
			if (mh == null) {
				mh = createResource(MessageHeader.class);
			}
			mh.getResponse().setDetails(ParserUtils.toReference(oo));
		}
		
		OperationOutcomeIssueComponent issue = oo.addIssue();
		setLocation(issue, err);
		setErrorCodeAndSeverity(issue, err, setErrorCode(issue, err));
		issue.setDetails(getDetails(err, issue.getDetails()));
		issue.setDiagnostics(getDiagnostics(err));
	}

	private void setLocation(OperationOutcomeIssueComponent issue, Segment err) {
		Type[] locations = getFields(err, 2); // Location: ERL (can repeat)
		if (locations.length != 0) {
			for (Type location: locations) {
				try {
					String encoded = location.encode();
					issue.addLocation(encoded);
					issue.addExpression(PathUtils.v2ToFHIRPath(encoded));  
				} catch (HL7Exception ex) {
					warnException("parsing {}-2", "ERR", err, ex);
					// Quietly ignore this
				}
			}
		}
	}

	private void setErrorCodeAndSeverity(OperationOutcomeIssueComponent issue, Segment err, CodeableConcept errorCode) {
		Coding severity = DatatypeConverter.toCoding(getField(err, 4), "HL70516");  // "HL70516"
		if (severity != null && severity.hasCode()) {
			switch (severity.getCode().toUpperCase()) {
			
			case "I":	issue.setSeverity(IssueSeverity.INFORMATION); break;
			case "W":	issue.setSeverity(IssueSeverity.WARNING); break;
			case "E":	// Fall through
			default: 	issue.setSeverity(IssueSeverity.ERROR); break;
			}
			if (errorCode == null) {
				errorCode = new CodeableConcept();
				issue.setDetails(errorCode);
			}
			errorCode.addCoding(severity);
		}
	}

	private CodeableConcept setErrorCode(OperationOutcomeIssueComponent issue, Segment err) {
		CodeableConcept errorCode = DatatypeConverter.toCodeableConcept(getField(err, 3), "HL70357");
		if (errorCode == null) {
			return null;
		}
		issue.setDetails(errorCode);
		for (Coding c: errorCode.getCoding()) {
			// If from table 0357
			if (!"http://terminology.hl7.org/CodeSystem/v2-0357".equals(c.getSystem())) {  // Check the system.
				continue;
			}
			String[] map = errorCodeMap.get(c.getCode());
			if (map == null) {
				issue.setCode(IssueType.PROCESSING);
				continue;
			}
			try {
				issue.setCode(IssueType.fromCode(map[0]));
			} catch (FHIRException fex) {
				if ("success".equals(map[0])) {
					// Some versions of FHIR support "success", others don't.
					issue.setCode(IssueType.INFORMATIONAL);
				}
			}
		}
		return errorCode;
	}
	private CodeableConcept getDetails(Segment err, CodeableConcept details) {
		CodeableConcept appErrorCode = DatatypeConverter.toCodeableConcept(getField(err, 5)); // Application Error Code: CWE
		if (appErrorCode != null) {
			if (details == null || details.isEmpty()) {
				return appErrorCode;
			} 
			appErrorCode.getCoding().forEach(details::addCoding);
		}
		return details;
	}
	private String getDiagnostics(Segment err) {
		StringType diagnostics = DatatypeConverter.toStringType(getField(err, 7)); // Diagnostics: TX
		StringType userMessage = DatatypeConverter.toStringType(getField(err, 8)); // User Message
		if (diagnostics != null) {
			if (userMessage != null) {
				return diagnostics + "\n" + userMessage;
			} 
			return diagnostics.toString();
		} 
		return userMessage == null || userMessage.isBooleanPrimitive() ? null : userMessage.toString();
	}
}
