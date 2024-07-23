package gov.cdc.izgw.v2tofhir.segment;

import java.util.ArrayList;
import java.util.List;

import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Address;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.ContactPoint;
import org.hl7.fhir.r4.model.ContactPoint.ContactPointUse;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.RelatedPerson;
import org.hl7.fhir.r4.model.StringType;

import gov.cdc.izgw.v2tofhir.annotation.ComesFrom;
import gov.cdc.izgw.v2tofhir.annotation.Produces;
import gov.cdc.izgw.v2tofhir.converter.MessageParser;
import gov.cdc.izgw.v2tofhir.utils.Mapping;
import gov.cdc.izgw.v2tofhir.utils.ParserUtils;
import gov.cdc.izgw.v2tofhir.utils.Systems;
import lombok.extern.slf4j.Slf4j;

/**
 * Parser for NK1 Segments
 * 
 * The PV1 segment populates a related person resource
 * 
 * @see <a href=
 *      "https://build.fhir.org/ig/HL7/v2-to-fhir/ConceptMap-segment-nk1-to-relatedperson.html">V2
 *      to FHIR: PV1 to Related Person</a>
 * @author Audacious Inquiry
 */
@Produces(segment = "NK1", resource = RelatedPerson.class, extra = { Patient.class })
@Slf4j
public class NK1Parser extends AbstractSegmentParser {
	private RelatedPerson relatedPerson;
	private Patient patient;

	private static List<FieldHandler> fieldHandlers = new ArrayList<>();

	static {
		log.debug("{} loaded", MRGParser.class.getName());
	}

	/**
	 * Construct a new PV1Parser
	 * 
	 * @param messageParser The messageParser to construct this parser for.
	 */
	public NK1Parser(MessageParser messageParser) {
		super(messageParser, "NK1");
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
		relatedPerson = createResource(RelatedPerson.class);
		patient = getLastResource(Patient.class);
		if (patient == null) {
			// patient may not exist if PID somehow failed or wasn't parsed.
			// so create one so that it can be referenced.
			patient = createResource(Patient.class);
		}
		relatedPerson.setPatient(ParserUtils.toReference(patient));
		return relatedPerson;
	}

	/**
	 * Set the name
	 * @param name the name
	 */
	@ComesFrom(path = "RelatedPerson.name", field = 2, comment = "Name")
	@ComesFrom(path = "RelatedPerson.name", field = 30, comment = "Contact Person's Name")
	public void setName(HumanName name) {
		relatedPerson.addName(name);
	}

	/**
	 * Set the relationship
	 * @param relationship the relationship
	 */
	@ComesFrom(path = "RelatedPerson.relationship", field = 3, table = "0063", map = "Relationship", comment = "Relationship")
	@ComesFrom(path = "RelatedPerson.relationship", field = 7, table = "0131", map = "ContactRole", comment = "Contact Role")
	public void setRelationship(CodeableConcept relationship) {
		CodeableConcept mappedConcept = new CodeableConcept(); 
		for (Coding coding: relationship.getCoding()) {
			if (!coding.hasSystem() || !coding.hasCode()) {
				continue;
			}
			Mapping m = null;
			if (coding.getSystem().endsWith("0063")) {
				m = Mapping.getMapping("Relationship");
			} else if (coding.getSystem().endsWith("0131")) {
				m = Mapping.getMapping("ContactRole");
			} else {
				continue;
			}
			
			Coding mappedCode = m.mapCode(coding, true);
			mappedConcept.addCoding(mappedCode);
		}
		// Add the mapped codes first, since they are the preferred concepts.
		if (!mappedConcept.isEmpty()) {
			relatedPerson.addRelationship(mappedConcept);
		}
		// Also add the original codes.
		relatedPerson.addRelationship(relationship);
	}

	/**
	 * Set the address
	 * @param address	The address
	 */
	@ComesFrom(path = "RelatedPerson.address", field = 4, comment = "Address")
	@ComesFrom(path = "RelatedPerson.address", field = 32, comment = "Contact Person's Address")
	public void setAddress(Address address) {
		relatedPerson.addAddress(address);
	}

	/**
	 * Set the phone number
	 * @param phoneNumber the phone number
	 */
	@ComesFrom(path = "relatedPerson.telecom", field = 5, comment = "Phone Number")
	@ComesFrom(path = "relatedPerson.telecom", field = 31, comment = "Contact Person's Telephone Number")
	@ComesFrom(path = "relatedPerson.telecom", field = 40, comment = "Next of Kin Telecommunication Information")
	@ComesFrom(path = "relatedPerson.telecom", field = 41, comment = "Contact Person's Telecommunication Information")
	public void setPhoneNumber(ContactPoint phoneNumber) {
		relatedPerson.addTelecom(phoneNumber);
	}

	/**
	 * Set the business phone number
	 * @param businessPhoneNumber the business phone number
	 */
	@ComesFrom(path = "relatedPerson.telecom", field = 6, comment = "Business Phone Number")
	public void setBusinessPhoneNumber(ContactPoint businessPhoneNumber) {
		businessPhoneNumber.setUse(ContactPointUse.WORK);
		relatedPerson.addTelecom(businessPhoneNumber);
	}

	/**
	 * Set the start date
	 * @param startDate the start date
	 */
	@ComesFrom(path = "relatedPerson.period.start", field = 8, comment = "Start Date")
	public void setStartDate(DateType startDate) {
		relatedPerson.getPeriod().setStart(startDate.getValue());
	}

	/**
	 * Set the end date
	 * @param endDate the end date
	 */
	@ComesFrom(path = "relatedPerson.period.end", field = 9, comment = "End Date")
	public void setEndDate(DateType endDate) {
		relatedPerson.getPeriod().setEnd(endDate.getValue());
	}

	/**
	 * Set the identifier
	 * @param nextofKinAssociatedPartiesEmployeeNumber The identifier
	 */
	@ComesFrom(path = "relatedPerson.identifier", field = 12, comment = "Next of Kin / Associated Parties Employee Number")
	@ComesFrom(path = "relatedPerson.identifier", field = 33, comment = "Next of Kin/Associated Party's Identifiers")
	public void setNextofKinAssociatedPartiesEmployeeNumber(Identifier nextofKinAssociatedPartiesEmployeeNumber) {
		relatedPerson.addIdentifier(nextofKinAssociatedPartiesEmployeeNumber);
	}

	/**
	 * Map gender from table 0001
	 * @param administrativeSex	The gender from table 0001
	 */
	@ComesFrom(path = "relatedPerson.gender", field = 15, table = "", comment = "Administrative Sex")
	public void setAdministrativeSex(Coding administrativeSex) {
		PIDParser.setGenderFromTable0001(relatedPerson.getGenderElement(), administrativeSex);
	}

	/**
	 * Set the birthDate
	 * @param dateTimeofBirth	The birth Date
	 */
	@ComesFrom(path = "relatedPerson.birthDate", field = 16, comment = "Date/Time of Birth")
	public void setDateTimeofBirth(DateTimeType dateTimeofBirth) {
		relatedPerson.setBirthDate(dateTimeofBirth.getValue());
	}

	/**
	 * Set the language
	 * @param primaryLanguage the language
	 */
	@ComesFrom(path = "relatedPerson.communication.language", field = 20, comment = "Primary Language")
	public void setPrimaryLanguage(CodeableConcept primaryLanguage) {
		relatedPerson.addCommunication().setLanguage(primaryLanguage);
	}

	/**
	 * Set the ssn
	 * @param contactPersonSocialSecurityNumber	The ssn
	 */
	@ComesFrom(path = "relatedPerson.identifier.value", field = 37, comment = "Contact Person Social Security Number")
	public void setContactPersonSocialSecurityNumber(StringType contactPersonSocialSecurityNumber) {
		Identifier ssn = new Identifier().setSystem(Systems.SSN).setValueElement(contactPersonSocialSecurityNumber);
		relatedPerson.addIdentifier(ssn);
	}
}
