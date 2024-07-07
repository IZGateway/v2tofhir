package gov.cdc.izgw.v2tofhir.segment;

import java.util.List;
import java.util.function.UnaryOperator;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.Account;
import org.hl7.fhir.r4.model.Account.AccountStatus;
import org.hl7.fhir.r4.model.Address;
import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.ContactPoint;
import org.hl7.fhir.r4.model.ContactPoint.ContactPointUse;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Enumeration;
import org.hl7.fhir.r4.model.Enumerations.AdministrativeGender;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.HumanName.NameUse;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.InstantType;
import org.hl7.fhir.r4.model.IntegerType;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Provenance;
import org.hl7.fhir.r4.model.RelatedPerson;
import org.hl7.fhir.r4.model.StringType;

import ca.uhn.fhir.model.api.TemporalPrecisionEnum;
import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Segment;
import ca.uhn.hl7v2.model.Type;
import gov.cdc.izgw.v2tofhir.converter.DatatypeConverter;
import gov.cdc.izgw.v2tofhir.converter.MessageParser;
import gov.cdc.izgw.v2tofhir.utils.ParserUtils;
import gov.cdc.izgw.v2tofhir.utils.Systems;

/**
 * PIDParser handles PID segments and creates Patient, Account and Related Person resources.  It works
 * with the PD1 parser to collect additional demographics, and the NK1 parser to collect
 * contact information.
 * 
 * @see <a href="https://hl7.org/fhir/uv/v2mappings/2024Jan/ConceptMap-segment-pid-to-patient.html">V2 to FHIR PID to Patient Mapping</a>
 * @see <a href="https://hl7.org/fhir/uv/v2mappings/2024Jan/ConceptMap-segment-pid-to-account.html">V2 to FHIR PID to Account Mapping</a>
 * 
 * @author Audacious Inquiry
 *
 */
@ComesFrom(path="Patient.identifier", source = "PID-2")
@ComesFrom(path="Patient.identifier", source = "PID-3")
@ComesFrom(path="Patient.identifier", source = "PID-4")
@ComesFrom(path="Patient.name", source = "PID-5")
@ComesFrom(path="Patient.extension('"+PIDParser.MOTHERS_MAIDEN_NAME+"')", source = "PID-6")
@ComesFrom(path="Patient.birthDate", source = "PID-7")
@ComesFrom(path="Patient.extension('"+PIDParser.PATIENT_BIRTH_TIME+"')", source = "PID-7")
@ComesFrom(path="Patient.gender", source = "PID-8")
@ComesFrom(path="Patient.name", source = "PID-9")
@ComesFrom(path="Patient.extension("+PIDParser.US_CORE_RACE+")", source="PID-10")
@ComesFrom(path="Patient.address", source="PID-11")
@ComesFrom(path="Patient.address.district", source="PID-12")
@ComesFrom(path="Patient.telecom", source="PID-13", comment = "Home Phone")
@ComesFrom(path="Patient.telecom", source="PID-14", comment = "Work Phone")
@ComesFrom(path="Patient.communication.language", source="PID-15")
@ComesFrom(path="Patient.maritalStatus", source="PID-16")
@ComesFrom(path="Patient.extension('"+PIDParser.RELIGION+"')", source="PID-17")
@ComesFrom(path="Account.identifier", source="PID-18", comment = "Patient Account")
@ComesFrom(path="Patient.identifier", source="PID-19", comment = "Social Security Number")
@ComesFrom(path="Patient.identifier", source="PID-20", comment = "Driver's License Number")
@ComesFrom(path="RelatedPerson.identifier", source="PID-21", comment="Mother's identifier")
@ComesFrom(path="Patient.extension('"+PIDParser.US_CORE_ETHNICITY+"')", source="PID-22")
@ComesFrom(path="Patient.extension('http://hl7.org/fhir/StructureDefinition/patient-birthPlace')", source="PID-23")
@ComesFrom(path="Patient.multipleBirthBoolean", source="PID-24", comment="Multiple Birth Indicator")
@ComesFrom(path="Patient.multipleBirthInteger", source="PID-25", comment="Multiple Birth Order")
@ComesFrom(path="Patient.extension('"+PIDParser.PATIENT_CITIZENSHIP+"')", source="PID-26", comment="Citizenship")
@ComesFrom(path="Patient.extension('"+PIDParser.PATIENT_NATIONALITY+"')", source="PID-28", comment="Nationality")
@ComesFrom(path="Patient.deceasedBoolean", source="PID-30", comment="Deceased Indicator")
@ComesFrom(path="Patient.deceasedDateTime", source="PID-29", comment="Deceased Date Time")
@ComesFrom(path="Patient.meta.lastUpdated", source="PID-33")
@ComesFrom(path="Patient.extension('"+PIDParser.PATIENT_CITIZENSHIP+"')", source="PID-39", comment="Tribal Citizenship")
@ComesFrom(path="Patient.telecom", source="PID-40", comment="Tribal Citizenship")

public class PIDParser extends AbstractSegmentParser {

	/** Extension for Patient mother's maiden name */
	public static final String MOTHERS_MAIDEN_NAME = "http://hl7.org/fhir/StructureDefinition/patient-mothersMaidenName";
	/** Extension for OMB Category in US Core Race and Ethnicity Extensions */
	public static final String OMB_CATEGORY = "ombCategory";
	/** Extension for Patient birth time */
	public static final String PATIENT_BIRTH_TIME = "http://hl7.org/fhir/StructureDefinition/patient-birthTime";
	/** Extension for Patient birth place */
	public static final String PATIENT_BIRTH_PLACE = "http://hl7.org/fhir/StructureDefinition/patient-birthPlace";
	/** Extension for Religion */
	private static final String RELIGION = "http://hl7.org/fhir/StructureDefinition/patient-religion";
	/** Extension for US Core Race */
	public static final String US_CORE_RACE = "http://hl7.org/fhir/us/core/StructureDefinition/us-core-race";
	/** Extension for US Core Ethnicity */
	public static final String US_CORE_ETHNICITY = "http://hl7.org/fhir/us/core/StructureDefinition/us-core-ethnicity";
	/** Extension for Citizenship */
	public static final String PATIENT_CITIZENSHIP = "http://hl7.org/fhir/StructureDefinition/patient-citizenship";
	/** Extension for Nationality */
	public static final String PATIENT_NATIONALITY = "http://hl7.org/fhir/StructureDefinition/patient-nationality";

	/**
	 * Create a PID parser for the specified message parser
	 * @param p The message parser to create this PID parser for
	 */
	public PIDParser(MessageParser p) {
		super(p, "PID");
	}

	@Override
	public void parse(Segment pid) throws HL7Exception {
		if (isEmpty(pid)) {
			return;
		}
		
		Patient patient = this.createResource(Patient.class);
		
		// Patient identifier
		addField(pid, 2, Identifier.class, patient::addIdentifier);
		
		// Patient identifier list
		addFields(pid, 3, Identifier.class, patient::addIdentifier);
		
		// Patient alternate identifier
		addField(pid, 4, Identifier.class, patient::addIdentifier);
		
		// Patient name 
		addFields(pid, 5, HumanName.class, patient::addName);

		// Mother's maiden name
		addFields(pid, 6, patient, HumanName.class, this::addMothersMaidenName);
		
		// Patient Birth Time
		addField(pid, 7, patient, DateTimeType.class, this::addBirthDateTime);
		
		// Gender (using HL7 0001 table)
		CodeableConcept gender = DatatypeConverter.toCodeableConcept(getField(pid, 8), "0001"); 
		setGender(patient, gender);

		// Alias
		addFields(pid, 9, patient, HumanName.class, this::addAlias);
		
		// Race
		addFields(pid, 10, patient, CodeableConcept.class, this::addRace);
		
		// Address
		addFields(pid, 11, Address.class, patient::addAddress);
		
		Type county = getField(pid, 12);
		StringType countyName = DatatypeConverter.toStringType(county);
		if (patient.hasAddress()) {
			if (!patient.getAddressFirstRep().hasDistrict()) {
				// Set county on first address
				patient.getAddressFirstRep().setDistrictElement(countyName);
			} else if (!patient.getAddressFirstRep().getDistrict().equals(countyName.getValue()) ) {
				// The county doesn't match district of first address, add a new address containing only the county
				patient.addAddress().setDistrictElement(countyName);
			} else {
				// They match so we can ignore this case.
			}
		}
		
		for (Type homePhone: getFields(pid, 13)) {
			addPhone(homePhone, ContactPointUse.HOME, patient);
		}
		for (Type workPhone: getFields(pid, 14)) {
			addPhone(workPhone, ContactPointUse.WORK, patient);
		}
		
		// Language
		addField(pid, 15, CodeableConcept.class, cc -> patient.addCommunication().setLanguage(cc));
		
		// Marital Status
		addField(pid, 16, CodeableConcept.class, patient::setMaritalStatus);
		
		// Religion
		addField(pid, 17, patient, CodeableConcept.class, this::addReligion);
		
		// Account number
		addField(pid, 18, patient, Identifier.class, this::addAccount);
		
		// Social Security Number
		addField(pid, 19, patient, Identifier.class, this::addSSN);
		
		// Drivers License Number
		addField(pid, 20, Identifier.class, patient::addIdentifier);
		
		// Mother's identifier
		addFields(pid, 21, patient, Identifier.class, this::addMother);
		
		// Ethnicity
		addFields(pid, 22, patient, CodeableConcept.class, this::addEthnicity);
		
		// Birthplace
		addField(pid, 23, patient, StringType.class, this::addBirthPlace);
		
		// Multiple Birth indicator
		addField(pid, 24, BooleanType.class, patient::setMultipleBirth);
		
		// Multiple Birth Order.  NOTE: If PID-25 is set, it will overwrite multiBirthBoolean, which should be fine.
		addField(pid, 25, IntegerType.class, patient::setMultipleBirth);

		// Citizenship
		addFields(pid, 26, CodeableConcept.class, cc -> 
			patient.addExtension(PATIENT_CITIZENSHIP, new Extension("code", cc)));

		// Veteran Status (Not done yet in V2toFHIR)
		
		// Nationality
		addField(pid, 28, CodeableConcept.class, cc -> patient.addExtension(PATIENT_NATIONALITY, 
				new Extension("code", cc)));

		// Deceased Indicator, NOTE: Take this one first.  
		addField(pid, 30, BooleanType.class, patient::setDeceased);
		
		// Deceased Date Time, NOTE: This overwrites the indicator above
		addField(pid, 29, DateTimeType.class, patient::setDeceased);
		
		// Last Updated Time
		addField(pid, 33, InstantType.class, patient.getMeta()::setLastUpdatedElement);

		// Last Updated Facility
		addField(pid, 34, patient, Identifier.class, this::addLastUpdatedFacility);

		// Tribal Citizenship
		addFields(pid, 39, CodeableConcept.class, cc -> 
			patient.addExtension(PATIENT_CITIZENSHIP, new Extension("code", cc)));
	
		// Patient Telecommunication Information
		addFields(pid, 40, ContactPoint.class, patient::addTelecom);
		
	}
	
	/**
	 * Add a mothers maiden name to a patient using the mothersMaidenName extension.
	 * 
	 * @param patient	The patient to add the name to
	 * @param hn	The mother's maiden name
	 */
	public void addMothersMaidenName(Patient patient, HumanName hn) {
		if (isNotEmpty(hn) && hn.hasFamily()) {
			patient.addExtension()
				.setUrl(MOTHERS_MAIDEN_NAME)
				.setValue(hn.getFamilyElement());
		}
	}
	
	/**
	 * Add a birth date and optionally the time of birth to a patient.
	 * 
	 * If the precision of the datetime is greater than to the day, a birthTime extension
	 * will be added to the patient.birthDate element.
	 * 
	 * @param patient	The patient to add the datetime to.
	 * @param dt	The datetime
	 */
	public void addBirthDateTime(Patient patient, DateTimeType dt) {
		if (isNotEmpty(dt)) {
			patient.setBirthDate(dt.getValue());
			if (dt.getPrecision().compareTo(TemporalPrecisionEnum.DAY) > 0) {
				patient.getBirthDateElement().addExtension()
					.setUrl(PATIENT_BIRTH_TIME)
					.setValue(dt);
			}
		}
	}

	/**
	 * Set a gender on a patient based on a Codeable Concept
	 * @param patient	The patient to set the gender for
	 * @param gender	A Codeable Concept representing the patient gender
	 */
	public void setGender(Patient patient, CodeableConcept gender) {
		for (Coding coding: gender.getCoding()) {
			if (coding.hasCode() && coding.hasSystem()) {
				String system = coding.getSystem();
				if (system.endsWith("0001")) {
					setGenderFromTable0001(patient, coding);
				} else if (Systems.getSystemNames(system).contains(Systems.DATA_ABSENT)) {
					setDataAbsentReasonFromDataAbsent(patient.getGenderElement(), coding.getCode());
				} else if (Systems.getSystemNames(system).contains(Systems.NULL_FLAVOR)) {
					setDataAbsentFromNullFlavor(patient.getGenderElement(), coding.getCode());
				}
			}
			if (patient.hasGender()) { break; }
		}
	}

	/**
	 * Set gender from HL7 Table 0001
	 * @param patient
	 * @param coding
	 */
	public void setGenderFromTable0001(Patient patient, Coding coding) {
		switch (coding.getCode()) {
		case "A":	patient.setGender(AdministrativeGender.OTHER); break;
		case "F":	patient.setGender(AdministrativeGender.FEMALE); break;
		case "M":	patient.setGender(AdministrativeGender.MALE); break;
		case "N":	setDataAbsentReason(patient.getGenderElement(), "not-applicable"); break;
		case "O":	patient.setGender(AdministrativeGender.OTHER); break;
		case "U", "UNK":
					patient.setGender(AdministrativeGender.UNKNOWN); break;
		case "ASKU":
					setDataAbsentReason(patient.getGenderElement(), "asked-unknown"); break;
		case "PHC1175":
					setDataAbsentReason(patient.getGenderElement(), "asked-declined"); break;
		default:	setDataAbsent(patient.getGenderElement()); break;
		}
	}
	
	/**
	 * Set the reason why gender is not present from a code in DataAbsentReason
	 * @param gender	The gender value to update
	 * @param code		A code from DataAbsentReason
	 */
	public void setDataAbsentReasonFromDataAbsent(Enumeration<AdministrativeGender> gender, String code) {
		switch (code) {
		case "unsupported": gender.setValue(AdministrativeGender.OTHER); break;
		case "unknown": 	gender.setValue(AdministrativeGender.UNKNOWN); break;
		case "temp-unknown":gender.setValue(AdministrativeGender.UNKNOWN); break;
		default:			setDataAbsentReason(gender, code); break;
		}
	}

	/**
	 * Set the reason why gender is not present from a code in NulLFlavor
	 * @param gender	The gender value to update
	 * @param code		A code from NullFlavor
	 */
	public void setDataAbsentFromNullFlavor(Enumeration<AdministrativeGender> gender, String code) {
		switch (code) {
		case "OTH": gender.setValue(AdministrativeGender.OTHER); break;
		case "UNK": gender.setValue(AdministrativeGender.UNKNOWN); break;
		case "NAVU":gender.setValue(AdministrativeGender.UNKNOWN); break;
		default:	setDataAbsentFromNullFlavor(gender, code); break;
		}
	}
	
	/**
	 * Add an alias to the list of patient names.
	 * @param patient	The patient to add the alias for
	 * @param alias	The alias
	 */
	public void addAlias(Patient patient, HumanName alias) {
		if (isNotEmpty(alias)) {
			// Aliases are NOT official names.
			if (!alias.hasUse()) { alias.setUse(NameUse.USUAL); }
			patient.addName(alias);
		}
	}

	/**
	 * Add a race code to the patient using the US Core Race extension
	 * @param patient	The patient to add the race to.
	 * @param race	The race to add.
	 */
	public void addRace(Patient patient, CodeableConcept race) {
		if (isEmpty(race)) {
			return;
		}
		
		Extension raceExtension = patient.addExtension().setUrl(US_CORE_RACE);
		getContext().setProperty(raceExtension.getUrl(), raceExtension);
		Extension text = null;
		if (race.hasText()) {
			text = new Extension("text", new StringType(race.getText()));
			raceExtension.addExtension(text);
		}
		for (Coding coding: race.getCoding()) {
			String code = coding.getCode();
			if (StringUtils.isBlank(code)) {
				continue;
			}
			
			List<String> systemNames = Systems.getSystemNames(coding.getSystem());
			
			if (systemNames.contains(Systems.CDCREC) &&
				setCategoryAndDetail(raceExtension, coding, code, this::getRaceCategory)
			) {
				return;
			} 
			if (setUnknown(raceExtension, code)) {
				return;
			}
		}
		// Didn't find CDCREC
		for (Coding coding: race.getCoding()) {
			String code = coding.getCode();
			
			if (StringUtils.isBlank(code)) {
				continue;
			}
			
			if (text == null) {
				text = new Extension("text", new StringType(code));
				raceExtension.addExtension(text);
			}

			// Deal with common legacy codes
			Coding c = new Coding(Systems.CDCREC, getRaceCategory(code), null);
			if (c.hasCode()) {
				raceExtension.addExtension("ombCategory", c);
				return;
			} 
		}
		setUnknown(raceExtension, "UNK");

	}

	private boolean setCategoryAndDetail(Extension extension, Coding coding, String code, UnaryOperator<String> categorize) {
		if ("ASKU".equals(code) || "UNK".equals(code)) {
			// Set OMB category to code and quit
			extension.setExtension(null);  // Clear prior extensions, only ombCategory can be present
			extension.addExtension(OMB_CATEGORY, new Coding(Systems.NULL_FLAVOR, code, null));
			return true;
		}
		
		// Figure out category and detail codes
		Coding category = new Coding().setCode(categorize.apply(code)).setSystem(Systems.CDCREC);
		if (category.hasCode()) {
			// if category has a code, it was a legitimate race code
			extension.addExtension(OMB_CATEGORY, category);
			extension.addExtension("detailed", coding);
			if (coding.hasDisplay() && !extension.hasExtension("text")) {
				extension.addExtension("text", coding.getDisplayElement());
			}
		}
		return false;
	}

	/**
	 * Add an ethnicity code to the patient using the US Core Ethnicity extension
	 * @param patient	The patient to add the ethnicity to.
	 * @param ethnicity	The ethnicity to add.
	 */
	public void addEthnicity(Patient patient, CodeableConcept ethnicity) {
		if (isEmpty(ethnicity)) {
			return;
		}
		
		Extension ethnicityExtension = patient.addExtension().setUrl(US_CORE_ETHNICITY);
		getContext().setProperty(ethnicityExtension.getUrl(), ethnicityExtension);
		Extension text = null;
		if (ethnicity.hasText()) {
			text = new Extension("text", new StringType(ethnicity.getText()));
			ethnicityExtension.addExtension("text", text);
		}
		for (Coding coding: ethnicity.getCoding()) {
			String code = StringUtils.defaultString(coding.getCode());
			List<String> systemNames = Systems.getSystemNames(coding.getSystem());
			
			if (systemNames.contains(Systems.CDCREC) && 
				setCategoryAndDetail(ethnicityExtension, coding, code, this::getEthnicityCategory)
			) {
				return;
			}
			
			if (setUnknown(ethnicityExtension, code)) {
				return;
			}
		}

		// Didn't find CDCREC, or UNKNOWN look in other code sets.
		for (Coding coding: ethnicity.getCoding()) {
			String code = StringUtils.defaultString(coding.getCode());
			
			if (text == null) {
				text = new Extension("text", new StringType(code));
				ethnicityExtension.addExtension(text);
			}

			// Deal with common legacy codes
			Coding c = new Coding(Systems.CDCREC, getEthnicityCategory(code), null);
			if (c.hasCode()) {
				ethnicityExtension.addExtension("ombCategory", c);
				return;
			}
		}
		// Nothing found, we bail
		setUnknown(ethnicityExtension, "UNK");

	}

	private boolean setUnknown(Extension raceExtension, String code) {
		// Don't both looking at systems, just codes.
		
		// Two switches are merged. This catches errors when DATA_ABSENT codes are used in
		// the NULL_FLAVOR system and vice-versa.
		switch (code) {
		case "asked-unknown", // Set OMB category to ASKU and quit  
			 "ASKU": 
			raceExtension.setExtension(null);  // Clear prior extensions
			raceExtension.addExtension(OMB_CATEGORY, new Coding(Systems.NULL_FLAVOR, "ASKU", null));
			return true;
		case "unknown", // Set OMB category to UNK and quit
			 "UNK":  
			raceExtension.setExtension(null);  // Clear prior extensions
			raceExtension.addExtension(OMB_CATEGORY, new Coding(Systems.NULL_FLAVOR, "UNK", null));
			return true;
		default:
			return false;
		}
	}
	
	/**
	 * Computes the race category from the race code
	 * 
	 * This uses knowledge about the structure of the CDC Race code table to compute the values.
	 * 
	 * @param raceCode	The given race code
	 * @return	The OMB Race category code
	 */
	public String getRaceCategory(String raceCode) {
		raceCode = raceCode.toUpperCase();
		// American Indian or Alaska Native
		if ("1002".compareTo(raceCode) >= 0 && "2027".compareTo(raceCode) < 0) {
			return "1002-5";
		}
		if ("INDIAN".equals(raceCode) || "I".equals(raceCode)) {
			return "1002-5";
		}
		// Asian
		if ("2028".compareTo(raceCode) >= 0 && "2053".compareTo(raceCode) < 0) {
			return "2028-9";
		}
		if ("ASIAN".equals(raceCode) || "A".equals(raceCode)) {
			return "2028-9";
		}
		// Black or African American
		if ("2054".compareTo(raceCode) >= 0 && "2076".compareTo(raceCode) < 0) {
			return "2054-5";
		}
		if ("BLACK".equals(raceCode) || "B".equals(raceCode)) {
			return "2028-9";
		}
		// Native Hawaiian or Other Pacific Islander
		if ("2076".compareTo(raceCode) >= 0 && "2105".compareTo(raceCode) < 0) {
			return "2076-8";
		}
		if ("2500-7".equals(raceCode)) { 
			return "2076-8";
		}
		if ("HAWIIAN".equals(raceCode) || "H".equals(raceCode)) {
			return "2076-8";
		}
		// White
		if ("2106".compareTo(raceCode) >= 0 && "2130".compareTo(raceCode) < 0) {
			return "2106-3";
		}
		if ("WHITE".equals(raceCode) || "W".equals(raceCode)) {
			return "2106-3";
		}
		return null;
	}
	/**
	 * Computes the ethnic group category from the race code
	 * 
	 * This uses knowledge about the structure of the CDC Ethnicity code table to compute the values.
	 * 
	 * @param ethnicityCode	The given ethnicity code
	 * @return	The OMB ethnicity category code
	 */
	public String getEthnicityCategory(String ethnicityCode) {
		ethnicityCode = ethnicityCode.toUpperCase();
		if ("2135".compareTo(ethnicityCode) >= 0 && "2186".compareTo(ethnicityCode) < 0) {
			return "2135-2";
		}
		if (ethnicityCode.charAt(0) == 'H') {
			return "2135-2";
		}
		if ("2186-5".equals(ethnicityCode)) {
			return "2186-5";
		}
		if (ethnicityCode.charAt(0) == 'N') {
			return "2186-5";
		}
		return null;
	}

	
	/**
	 * Add a phone number to a patient
	 * @param phone	The phone number (or other telecommmuncations address)
	 * @param use	The use if unspecified
	 * @param patient	The patient to add it to.
	 */
	public void addPhone(Type phone, ContactPointUse use, Patient patient) {
		List<ContactPoint> l = DatatypeConverter.toContactPoints(phone);
		for (ContactPoint cp: l) {
			if (!cp.hasUse()) {
				cp.setUse(use);
			}
			patient.addTelecom(cp);
		}
	}

	/**
	 * Add a religion to the patient
	 * @param patient	The patient
	 * @param religion	The religion
	 */
	public void addReligion(Patient patient, CodeableConcept religion) {
		patient.addExtension(RELIGION, religion);
	}
	
	/**
	 * Add a patient account
	 * 
	 * Creates a new active account resource with the specified identifier, and ties it back to the 
	 * patient via a reference.
	 *  
	 * @param patient	The patient to add the account to
	 * @param accountId	The account identifier
	 */
	public void addAccount(Patient patient, Identifier accountId) {
		if (isEmpty(accountId)) {
			return;
		}
		Account account = createResource(Account.class);
		account.addIdentifier(accountId);
		if (isNotEmpty(patient)) {
			account.setStatus(AccountStatus.ACTIVE);
			account.addSubject(ParserUtils.toReference(patient));
		}
	}

	/** The code for a Social Security Number found on Identifier.type */
	public static final CodeableConcept SSN_TYPE = new CodeableConcept().addCoding(
			new Coding("http://terminology.hl7.org/CodeSystem/v2-0203", "SS", "Social Security Number")
		);

	/**
	 * Add a social security number to a patient.
	 * @param patient	The patient to add the SSN to
	 * @param ssn	The social security number
	 */
	public void addSSN(Patient patient, Identifier ssn) {
		if (isEmpty(patient) || isEmpty(ssn)) {
			return;
		}
		if (!ssn.hasType()) {
			ssn.setType(SSN_TYPE);
		}
		if (!ssn.hasSystem()) {
			ssn.setSystem(Systems.SSN);
		}
		patient.addIdentifier(ssn);
	}
	
	/** The concept of MOTHER */
	public static final CodeableConcept MOTHER = new CodeableConcept().addCoding(
			new Coding("http://terminology.hl7.org/CodeSystem/v3-RoleCode", "MTH", "Mother")
		);

	/**
	 * Add the mothers identifier to a RelatedPerson for this patient.
	 * @param patient	The patient to add the mothers identifier to
	 * @param mother	The mother's identifier
	 */
	public void addMother(Patient patient, Identifier mother) {
		if (isEmpty(patient) || isEmpty(mother)) {
			return;
		}
		RelatedPerson rp = (RelatedPerson) patient.getUserData("mother");
		if (rp == null) {
			rp = createResource(RelatedPerson.class);
			patient.setUserData("mother", rp);
		}
		rp.addIdentifier(mother);
		rp.setPatient(ParserUtils.toReference(patient));
		rp.addRelationship(MOTHER);
	}
	
	/**
	 * Add the patient's birth place
	 * @param patient	The patient
	 * @param birthPlace	The birthplace
	 */
	public void addBirthPlace(Patient patient, StringType birthPlace) {
		if (isEmpty(patient) || isEmpty(birthPlace)) {
			return;
		}
		patient.addExtension(PATIENT_BIRTH_PLACE, birthPlace);
	}
	
	/**
	 * Add the last updated facility and date time to provenance information
	 * @param patient	The patient to update
	 * @param ident	The last updated facility identifier
	 */
	public void addLastUpdatedFacility(Patient patient, Identifier ident) {
		Provenance p = (Provenance) patient.getUserData(Provenance.class.getName());
		if (p == null) {
			return;
		}
		// TODO: Figure this out
	}
}
