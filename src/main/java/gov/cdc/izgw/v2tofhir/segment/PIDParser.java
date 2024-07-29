package gov.cdc.izgw.v2tofhir.segment;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.instance.model.api.IBaseResource;
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
import org.hl7.fhir.r4.model.MessageHeader;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Provenance;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.RelatedPerson;
import org.hl7.fhir.r4.model.StringType;

import ca.uhn.fhir.model.api.TemporalPrecisionEnum;
import gov.cdc.izgw.v2tofhir.annotation.ComesFrom;
import gov.cdc.izgw.v2tofhir.annotation.Produces;
import gov.cdc.izgw.v2tofhir.converter.MessageParser;
import gov.cdc.izgw.v2tofhir.utils.Codes;
import gov.cdc.izgw.v2tofhir.utils.ParserUtils;
import gov.cdc.izgw.v2tofhir.utils.Systems;
import lombok.extern.slf4j.Slf4j;

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
@Produces(segment="PID", resource = Patient.class, extra = { RelatedPerson.class, Account.class })
@Slf4j
public class PIDParser extends AbstractSegmentParser {
	private Patient patient = null;
	static {
		log.debug("Loaded PIDParser");
	}
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
	private static List<FieldHandler> fieldHandlers = new ArrayList<>();
	/**
	 * Create a PID parser for the specified message parser
	 * @param p The message parser to create this PID parser for
	 */
	public PIDParser(MessageParser p) {
		super(p, "PID");
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
		patient = this.createResource(Patient.class);
		
		MessageHeader mh = getFirstResource(MessageHeader.class);
		if (mh != null) {
			Reference ref = ParserUtils.toReference(patient, mh, "focus");
			mh.addFocus(ref);
		}

		return patient;
	}
	
	/**
	 * Add an identifier to the patient
	 * @param ident	The identifier to add
	 */
	@ComesFrom(path="Patient.identifier", field = 2)
	@ComesFrom(path="Patient.identifier", field = 3)
	@ComesFrom(path="Patient.identifier", field = 4)
	@ComesFrom(path="Patient.identifier", field = 20, comment = "Driver's License Number")

	public void addIdentifier(Identifier ident) {
		patient.addIdentifier(ident);
	}
	
	/**
	 * Add a name to the patient
	 * @param name	The name to add
	 */
	@ComesFrom(path="Patient.name", field = 5)
	public void addName(HumanName name) {
		patient.addName(name);
	}
	/**
	 * Add a mothers maiden name to a patient using the mothersMaidenName extension.
	 * 
	 * @param hn	The mother's maiden name
	 */
	@ComesFrom(path="Patient.patient-mothersMaidenName.valueString", field = 6, fhir = HumanName.class)
	public void addMothersMaidenName(HumanName hn) {
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
	 * @param dt	The datetime
	 */
	@ComesFrom(path="Patient.birthDate", field = 7, fhir = DateTimeType.class,
			   also="Patient.patient-birthTime")
	public void addBirthDateTime(DateTimeType dt) {
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
	 * @param gender	A Codeable Concept representing the patient gender
	 */
	@ComesFrom(
		path="Patient.gender", field = 8, table="0001", 
		map = "Gender", 
		fhir = CodeableConcept.class
	)
	public void setGender(CodeableConcept gender) {
		for (Coding coding: gender.getCoding()) {
			if (coding.hasCode() && coding.hasSystem()) {
				String system = coding.getSystem();
				if (system.endsWith("0001")) {
					setGenderFromTable0001(patient.getGenderElement(), coding);
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
	 * @param enumeration	The enumeration to set the value for 
	 * @param gender	The gender code
	 */
	public static void setGenderFromTable0001(Enumeration<AdministrativeGender> enumeration, Coding gender) {
		switch (gender.getCode()) {
		case "A":	enumeration.setValue(AdministrativeGender.OTHER); break;
		case "F":	enumeration.setValue(AdministrativeGender.FEMALE); break;
		case "M":	enumeration.setValue(AdministrativeGender.MALE); break;
		case "N":	StructureParser.setDataAbsentReason(enumeration, "not-applicable"); break;
		case "O":	enumeration.setValue(AdministrativeGender.OTHER); break;
		case "U", "UNK":
					enumeration.setValue(AdministrativeGender.UNKNOWN); break;
		case "ASKU":
					StructureParser.setDataAbsentReason(enumeration, "asked-unknown"); break;
		case "PHC1175":
					StructureParser.setDataAbsentReason(enumeration, "asked-declined"); break;
		default:	StructureParser.setDataAbsent(enumeration); break;
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
		default:			StructureParser.setDataAbsentReason(gender, code); break;
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
	 * @param alias	The alias
	 */
	@ComesFrom(path="Patient.name", field = 9)
	public void addAlias(HumanName alias) {
		if (isNotEmpty(alias)) {
			// Aliases are NOT official names.
			if (!alias.hasUse()) { alias.setUse(NameUse.USUAL); }
			patient.addName(alias);
		}
	}

	/**
	 * Add a race code to the patient using the US Core Race extension
	 * @param race	The race to add.
	 */
	@ComesFrom(path="Patient.us-core-race.extension('ombCategory').valueCoding", table = "0005", map = "USCoreRace", field = 10,
				also= {
					"Patient.us-core-race.extension('ombDetail').valueCoding",
					"Patient.us-core-race.extension('text').valueString"
				}
	)
	public void addRace(CodeableConcept race) {
		if (isEmpty(race)) {
			return;
		}
		
		Extension raceExtension = patient.addExtension().setUrl(US_CORE_RACE);
		getContext().setProperty(raceExtension.getUrl(), raceExtension);
		Extension text = setRaceText(race, raceExtension);
		
		if (!setCDCREC(race, raceExtension) &&  	// Didn't find CDCREC
			!setLegacy(race, raceExtension, text)	// Didn't find a Legacy code
		) {
			setUnknown(raceExtension, "UNK");		// Then set value as unknown.
		}
		
	}
	
	private Extension setRaceText(CodeableConcept race, Extension raceExtension) {
		Extension text = null;
		if (race.hasText()) {
			text = new Extension("text", new StringType(race.getText()));
			raceExtension.addExtension(text);
		}
		return text;
	}
	
	private boolean setCDCREC(CodeableConcept race, Extension raceExtension) {
		for (Coding coding: race.getCoding()) {
			String code = coding.getCode();
			if (StringUtils.isBlank(code)) {
				continue;
			}
			
			List<String> systemNames = Systems.getSystemNames(coding.getSystem());
			
			if ((!coding.hasSystem() || systemNames.contains(Systems.CDCREC)) &&
				setCategoryAndDetail(raceExtension, coding, code, this::getRaceCategory)
			) {
				return true;
			} 
			if (setUnknown(raceExtension, code)) {
				return true;
			}
		}
		return false;
	}
	private boolean setLegacy(CodeableConcept race, Extension raceExtension, Extension text) {
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
				Extension category = raceExtension.getExtensionByUrl(OMB_CATEGORY);
				if (category == null) {
					raceExtension.addExtension(OMB_CATEGORY, c);
				} else {
					category.setValue(c);
				}
				return true;
			} 
		}
		return false;
	}

	/**
	 * Add an address to the patient
	 * @param address The address
	 */
	@ComesFrom(path="Patient.address", field = 11)
	public void addAddress(Address address) {
		patient.addAddress(address);
	}
	
	/**
	 * Add the county name to the patient address
	 * @param countyName The name of the county
	 */
	
	@ComesFrom(path="Patient.address.district", field = 12)
	public void addCounty(StringType countyName) {
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
	}
	
	private boolean setCategoryAndDetail(Extension extension, Coding coding, String code, UnaryOperator<String> categorize) {
		Extension category = extension.getExtensionByUrl(OMB_CATEGORY);
 
		if ("ASKU".equals(code) || "UNK".equals(code)) {
			// Set OMB category to code and quit
			extension.setExtension(null);  // Clear prior extensions, only ombCategory can be present
			if (category == null) {
				extension.addExtension(OMB_CATEGORY, new Coding(Systems.NULL_FLAVOR, code, null));
			} else {
				category.setValue(new Coding(Systems.NULL_FLAVOR, code, null));
			}
			return true;
		}
		
		// Figure out category and detail codes
		Coding categoryCode = new Coding().setCode(categorize.apply(code)).setSystem(Systems.CDCREC);
		if (categoryCode.hasCode()) {
			// if category has a code, it was a legitimate race code
			if (category == null) {
				extension.addExtension(OMB_CATEGORY, categoryCode);
			} else {
				category.setValue(categoryCode);
			}

			// If if smells enough like a CDCREC code
			if (coding.getCode().matches("^[1-2]\\d{3}-\\d$")) {
				// Add to detailed.
				extension.addExtension("detailed", coding);
			}
			if (coding.hasDisplay() && !extension.hasExtension("text")) {
				extension.addExtension("text", coding.getDisplayElement());
			}
		}
		return false;
	}

	/**
	 * Add an ethnicity code to the patient using the US Core Ethnicity extension
	 * @param ethnicity	The ethnicity to add.
	 */
	@ComesFrom(path="Patient.us-core-ethnicity.extension('ombCategory').valueCoding", table = "0189", map = "USCoreEthnicity", field = 22,
			also = {
				"Patient.us-core-ethnicity.extension('ombDetail').valueCoding",
				"Patient.us-core-ethnicity.extension('text').valueString"
			}
	)
	public void addEthnicity(CodeableConcept ethnicity) {
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
				Extension category = ethnicityExtension.getExtensionByUrl(OMB_CATEGORY);
				if (category == null) {
					ethnicityExtension.addExtension(OMB_CATEGORY, c);
				} else {
					category.setValue(c);
				}
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
		
		if (isBetween(raceCode, "1002", "2027") ||
			"INDIAN".equals(raceCode) || 
			"I".equals(raceCode)
		) {
			return "1002-5";
		}
		// Asian
		if (isBetween(raceCode, "2028", "2053") ||
			"ASIAN".equals(raceCode) || 
			"A".equals(raceCode)
		) {
			return "2028-9";
		}
		// Black or African American
		if (isBetween(raceCode, "2054", "2076") ||
			"BLACK".equals(raceCode) || 
			"B".equals(raceCode)
		) {
			return "2054-5";
		}
		// Native Hawaiian or Other Pacific Islander
		if (isBetween(raceCode, "2076", "2105") ||
			"2500-7".equals(raceCode) ||
			"HAWIIAN".equals(raceCode) || "H".equals(raceCode)
		) { 
			return "2076-8";
		}
		// White
		if (isBetween(raceCode, "2106", "2130") ||
			"WHITE".equals(raceCode) || 
			"W".equals(raceCode)
		) {
			return "2106-3";
		}
		if ("2131-1".equals(raceCode) || "OTHER".equals(raceCode) || "O".equals(raceCode)) {
			return "2131-1";
		}
		return null;
	}
	
	private boolean isBetween(String string, String low, String high) {
		return low.compareTo(string) <= 0 && high.compareTo(string) > 0;
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
		if (("2135".compareTo(ethnicityCode) >= 0 && "2186".compareTo(ethnicityCode) < 0) ||
			ethnicityCode.charAt(0) == 'H'
		) {
			return "2135-2";
		}
		if ("2186-5".equals(ethnicityCode) || ethnicityCode.charAt(0) == 'N') {
			return "2186-5";
		}
		return null;
	}

	
	/**
	 * Add a phone number to a patient
	 * @param phone	The phone number (or other telecommuncations address)
	 * @param use	The use if unspecified
	 */
	public void addPhone(ContactPoint phone, ContactPointUse use) {
		if (!phone.hasUse() && use != null) {
			phone.setUse(use);
		}
		patient.addTelecom(phone);
	}
	
	/**
	 * Add a Home contact point to the patient
	 * @param homePhone The contact point
	 */
	@ComesFrom(path="Patient.telecom", field = 13, comment = "Home Phone")
	public void addHomePhone(ContactPoint homePhone) {
		addPhone(homePhone, ContactPointUse.HOME);
	}
	/**
	 * Add a Work contact point to the patient
	 * @param workPhone		The contact point
	 */
	@ComesFrom(path="Patient.telecom", field = 14, comment = "Work Phone")
	public void addWorkPhone(ContactPoint workPhone) {
		addPhone(workPhone, ContactPointUse.WORK);
	}
	
	/**
	 * Add a contact point to the patient
	 * @param telecom The contact point
	 */
	@ComesFrom(path="Patient.telecom", field = 40, comment="Patient Telecommunication Information")
	public void addTelecom(ContactPoint telecom) {
		addPhone(telecom, null);
	}

	
	/**
	 * Add a language for the patient.
	 * @param language The language
	 */
	@ComesFrom(path="Patient.communication.language", field = 15)
	public void addLanguage(CodeableConcept language) {
		patient.addCommunication().setLanguage(language);
	}

	
	/**
	 * Set the marital status for the patient.
	 * @param maritalStatus	The marital status
	 */
	@ComesFrom(path="Patient.maritalStatus", field = 16)
	public void setMaritalStatus(CodeableConcept maritalStatus) {
		patient.setMaritalStatus(maritalStatus);
	}
	
	/**
	 * Add a religion to the patient
	 * @param religion	The religion
	 */
	@ComesFrom(path="Patient.patient-religion", field = 17)
	public void addReligion(CodeableConcept religion) {
		patient.addExtension(RELIGION, religion);
	}
	
	/**
	 * Add a patient account
	 * 
	 * Creates a new active account resource with the specified identifier, and ties it back to the 
	 * patient via a reference.
	 *  
	 * @param accountId	The account identifier
	 */
	@ComesFrom(path="Account.identifier", field = 18, comment = "Patient Account")
	public void addAccount(Identifier accountId) {
		if (isEmpty(accountId)) {
			return;
		}
		Account account = createResource(Account.class);
		account.addIdentifier(accountId);
		if (isNotEmpty(patient)) {
			account.setStatus(AccountStatus.ACTIVE);
			account.addSubject(ParserUtils.toReference(patient, account, "patient", "subject"));
		}
	}

	/**
	 * Add a social security number to a patient.
	 * @param ssn	The social security number
	 */
	@ComesFrom(path="Patient.identifier", field = 19, comment = "Social Security Number")
	public void addSSN(Identifier ssn) {
		if (isEmpty(patient) || isEmpty(ssn)) {
			return;
		}
		if (!ssn.hasType()) {
			ssn.setType(Codes.SSN_TYPE);
		}
		if (!ssn.hasSystem()) {
			ssn.setSystem(Systems.SSN);
		}
		patient.addIdentifier(ssn);
	}
	
	/**
	 * Add the mothers identifier to a RelatedPerson for this patient.
	 * @param mother	The mother's identifier
	 */
	@ComesFrom(path="RelatedPerson.identifier", field = 21, comment="Mother's identifier")
	public void addMother(Identifier mother) {
		if (isEmpty(patient) || isEmpty(mother)) {
			return;
		}
		RelatedPerson rp = (RelatedPerson) patient.getUserData("mother");
		if (rp == null) {
			rp = createResource(RelatedPerson.class);
			patient.setUserData("mother", rp);
		}
		rp.addIdentifier(mother);
		rp.setPatient(ParserUtils.toReference(patient, rp, "patient"));
		rp.addRelationship(Codes.MOTHER);
	}
	
	/**
	 * Add the patient's birth place
	 * @param birthPlace	The birthplace
	 */
	@ComesFrom(path="Patient.patient-birthPlace", field = 23)
	public void addBirthPlace(StringType birthPlace) {
		if (isEmpty(patient) || isEmpty(birthPlace)) {
			return;
		}
		patient.addExtension(PATIENT_BIRTH_PLACE, birthPlace);
	}

	/**
	 * Add the patient's multiple birth indicator
	 * @param indicator	The indicator
	 */
	@ComesFrom(path="Patient.multipleBirthBoolean", field = 24, comment="Multiple Birth Indicator")
	public void setMultipleBirthIndicator(BooleanType indicator) {
		patient.setMultipleBirth(indicator);
	}
	
	/**
	 * Add the patient's multiple birth order
	 * @param order		The birth order
	 */
	@ComesFrom(path="Patient.multipleBirthInteger", field = 25, comment="Multiple Birth Order")
	public void setMultipleBirthOrder(IntegerType order) {
		patient.setMultipleBirth(order);
	}
	
	/**
	 * Add the patient's citizenship
	 * @param cc	The citizenship
	 */
	@ComesFrom(path="Patient.patient-citizenship", field = 26, comment="Citizenship")
	@ComesFrom(path="Patient.patient-citizenship", field = 39, comment="Tribal Citizenship")
	public void addCitizenship(CodeableConcept cc) {
		patient.addExtension(PATIENT_CITIZENSHIP, new Extension("code", cc));
	}
	/**
	 * Add the patient's nationality
	 * @param cc	The nationality
	 */
	@ComesFrom(path="Patient.patient-nationality", field = 28, comment="Nationality")
	public void addNationality(CodeableConcept cc) {
		patient.addExtension(PATIENT_NATIONALITY, new Extension("code", cc));
	}

	/**
	 * Add the patient's deceased indicator
	 * @param indicator	The indicator
	 */
	@ComesFrom(path="Patient.deceasedBoolean", field = 30, comment="Deceased Indicator")
	public void setDeceasedIndicator(BooleanType indicator) {
		patient.setDeceased(indicator);
	}
	
	/**
	 * Add the patient's deceased date
	 * @param dateTime	The datetime
	 */
	@ComesFrom(path="Patient.deceasedDateTime", field = 29, comment="Deceased Date Time")
	public void setDeceasedDateTime(DateTimeType dateTime) {
		patient.setDeceased(dateTime);
	}
	/**
	 * Set the patient's last updated tyime
	 * @param instant	The last updated time
	 */
	@ComesFrom(path="Patient.meta.lastUpdated", field = 33)
	public void setLastUpdated(InstantType instant) {
		patient.getMeta().setLastUpdatedElement(instant);
	}
	/**
	 * Add the last updated facility and date time to provenance information
	 * @param ident	The last updated facility identifier
	 */
	public void addLastUpdatedFacility(Identifier ident) {
		Provenance p = (Provenance) patient.getUserData(Provenance.class.getName());
		if (p == null) {
			return;
		}
		// TODO: Figure this out
	}
}
