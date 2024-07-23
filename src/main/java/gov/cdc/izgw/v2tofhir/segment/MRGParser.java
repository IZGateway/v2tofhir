package gov.cdc.izgw.v2tofhir.segment;

import java.util.ArrayList;
import java.util.List;

import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Account;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Patient;
import gov.cdc.izgw.v2tofhir.annotation.ComesFrom;
import gov.cdc.izgw.v2tofhir.annotation.Produces;
import gov.cdc.izgw.v2tofhir.converter.MessageParser;
import gov.cdc.izgw.v2tofhir.utils.ParserUtils;
import lombok.extern.slf4j.Slf4j;

/**
 * Parser for MRG Segments
 * 
 * The MRG segment populates a Patient resource with identifiers and demographics for
 * a patient which would need to be merged into a single patiennt. 
 * 
 * @author Audacious Inquiry
 */
@Produces(segment="MRG", resource=Patient.class, extra = { Account.class, Encounter.class })
@Slf4j
public class MRGParser extends AbstractSegmentParser {
	private Patient patient;
	private Account account;
	private Encounter encounter;
	
	private static List<FieldHandler> fieldHandlers = new ArrayList<>();

	static {
		log.debug("{} loaded", MRGParser.class.getName());
	}
	
	/**
	 * Construct a new MRGParser
	 * 
	 * @param messageParser	The messageParser to construct this parser for.
	 */
	public MRGParser(MessageParser messageParser) {
		super(messageParser, "MRG");
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
		// The MRG segment is used with an existing patient recorded
		// in the PID segment, so get the most recently created Patient
		// resource.
		patient = getLastResource(Patient.class);
		if (patient == null) {
			patient = createResource(Patient.class);
		}
		account = null;
		encounter = null;
		return patient;
	}
	
	/*
	The MRG segment provides receiving applications with information necessary to initiate the merging of patient data as well as groups of records. It is intended that this segment be used throughout the Standard to allow the merging of registration, accounting, and clinical records within specific applications.

	FIELD	LENGTH	DATA TYPE	OPTIONALITY	REPEATABILITY	TABLE	
	MRG.1 - Prior Patient Identifier List	250	CX	R	∞		
	MRG.2 - Prior Alternate Patient ID	250	CX	B	∞		
	MRG.3 - Prior Patient Account Number	250	CX	O	-		
	MRG.4 - Prior Patient ID	250	CX	B	-		
	MRG.5 - Prior Visit Number	250	CX	O	-		
	MRG.6 - Prior Alternate Visit ID	250	CX	O	-		
	MRG.7 - Prior Patient Name	250	XPN
	*/
	/**
	 * Add a patient identifier
	 * @param ident the patient identifier
	 */
	@ComesFrom(path="Patient.identifier", field = 1, comment = "Prior Patient Identifier List")
	@ComesFrom(path="Patient.identifier", field = 2, comment = "Prior Alternate Patient ID")
	@ComesFrom(path="Patient.identifier", field = 4, comment = "Prior Patient ID")
	public void addPatientIdentifier(Identifier ident) {
		patient.addIdentifier(ident);
	}
	
	/**
	 * Add a patient account identifier
	 * Creates a new account.  Adds a reference from the Encounter to the account.
	 * Adds a reference from the encounter and the account to the patient as the subject.
	 * @param accountId	The account identifier
	 */
	@ComesFrom(path="Account.identifier", field = 3, comment = "Prior Patient Account Number")
	public void addPatientAccount(Identifier accountId) {
		account = createResource(Account.class);
		account.addIdentifier(accountId);
		account.addSubject(ParserUtils.toReference(patient));
		account.getMeta().addTag();
		encounter = getEncounter();
		encounter.addAccount(ParserUtils.toReference(account));
	}

	/**
	 * Create or return the encounter associated with this segment.
	 * If an encounter has already been created, returns it. Otherwise
	 * creates the encounter and connects the encounter to the patient.
	 * @return The encounter associated with this segment
	 */
	private Encounter getEncounter() {
		if (encounter == null) {
			encounter = createResource(Encounter.class);
			encounter.setSubject(ParserUtils.toReference(patient));
		}
		return encounter;
	}
	
	/**
	 * Add an identifier to the encounter.
	 * Creates or gets the existing encounter and adds the identifier to it.
	 * @param encounterId The encounter identifier.
	 */
	@ComesFrom(path="Encounter.identifier", field = 5, comment = "Prior Visit Number")
	@ComesFrom(path="Encounter.identifier", field = 6, comment = "Prior Alternate Visit ID")
	public void addEncounterIdentifier(Identifier encounterId) {
		encounter = getEncounter();
		encounter.addIdentifier(encounterId);
	}
		
	/**
	 * Add the name to the patient.
	 * @param humanName
	 */
	@ComesFrom(path="Patient.name", field = 7, comment = "Patient Name")
	public void addPatientName(HumanName humanName) {
		patient.addName();
	}
}
