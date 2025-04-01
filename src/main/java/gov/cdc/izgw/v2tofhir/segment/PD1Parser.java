package gov.cdc.izgw.v2tofhir.segment;

import java.util.ArrayList;
import java.util.List;

import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Address;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.ContactPoint;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Practitioner;
import org.hl7.fhir.r4.model.PractitionerRole;
import org.hl7.fhir.r4.model.Provenance;
import org.hl7.fhir.r4.model.Reference;

import com.ainq.fhir.utils.PathUtils;

import gov.cdc.izgw.v2tofhir.annotation.ComesFrom;
import gov.cdc.izgw.v2tofhir.annotation.Produces;
import gov.cdc.izgw.v2tofhir.converter.MessageParser;
import gov.cdc.izgw.v2tofhir.utils.ParserUtils;
import lombok.extern.slf4j.Slf4j;

/**
 * The EVNParser extracts information from the EVN segment and adds it to the
 * ProvenanceResource for the MessageHeader.
 *
 * @author Audacious Inquiry
 *
 */
@Produces(segment = "PD1", resource = Provenance.class)
@Slf4j
public class PD1Parser extends AbstractSegmentParser {
	Patient patient;
	PractitionerRole primaryCareProvider;
	Organization primaryCareOrg;
	Practitioner primaryCareDoc;
	private Reference gpRef;
	private static List<FieldHandler> fieldHandlers = new ArrayList<>();
	static {
		log.debug("{} loaded", PD1Parser.class.getName());
	}

	/**
	 * Construct a new DSC Parser for the given messageParser.
	 * 
	 * @param messageParser The messageParser using this QPDParser
	 */
	public PD1Parser(MessageParser messageParser) {
		super(messageParser, "PD1");
		if (fieldHandlers.isEmpty()) {
			FieldHandler.initFieldHandlers(this, fieldHandlers);
		}
	}

	@Override
	public List<FieldHandler> getFieldHandlers() {
		return fieldHandlers;
	}

	public IBaseResource setup() {
		patient = getFirstResource(Patient.class);
		return patient;
	}

	/**
	 * Set the primary care provider organization
	 * @param patientPrimaryFacility the primary care provider organization
	 */
	@ComesFrom(path = "Patient.generalPractitioner.Organization", field = 3, comment = "Patient Primary Facility")
	public void setPatientPrimaryFacility(Organization patientPrimaryFacility) {
		if (primaryCareProvider == null) {
			primaryCareProvider = createResource(PractitionerRole.class);
			gpRef = ParserUtils.toReference(primaryCareProvider, patient, "general-practitioner");
			patient.addGeneralPractitioner(gpRef);
		}
		if (primaryCareProvider.hasOrganization()) {
			if (patientPrimaryFacility.hasName()) {
				primaryCareOrg.addAlias(patientPrimaryFacility.getName());
			}
			for (Identifier ident : patientPrimaryFacility.getIdentifier()) {
				primaryCareOrg.addIdentifier(ident);
			}
			for (Address addr: patientPrimaryFacility.getAddress()) {
				primaryCareOrg.addAddress(addr);
			}
			for (ContactPoint telecom: patientPrimaryFacility.getTelecom()) {
				primaryCareOrg.addTelecom(telecom);
			}
		} else {
			addResource(primaryCareOrg = patientPrimaryFacility);
			primaryCareProvider.setOrganization(ParserUtils.toReference(patientPrimaryFacility, primaryCareProvider, "organization"));
		}
		ParserUtils.updateReference(primaryCareProvider, gpRef);
	}

	/**
	 * Set the primary care provider 
	 * @param patientPrimaryCareProviderNameIdNo the primary care provider 
	 */
	@ComesFrom(path = "Patient.generalPractitioner.Practitioner", field = 4, comment = "Patient Primary Care Provider Name & ID No.")
	public void setPatientPrimaryCareProviderNameIdNo(Practitioner patientPrimaryCareProviderNameIdNo) {
		if (primaryCareProvider == null) {
			primaryCareProvider = createResource(PractitionerRole.class);
			gpRef = ParserUtils.toReference(primaryCareProvider, patient, "general-practitioner");
			patient.addGeneralPractitioner(gpRef);
		}
		if (primaryCareProvider.hasPractitioner()) {
			for (HumanName name: patientPrimaryCareProviderNameIdNo.getName()) {
				primaryCareDoc.addName(name);
			}
			for (Identifier ident : patientPrimaryCareProviderNameIdNo.getIdentifier()) {
				primaryCareDoc.addIdentifier(ident);
			}
			for (Address addr: patientPrimaryCareProviderNameIdNo.getAddress()) {
				primaryCareDoc.addAddress(addr);
			}
			for (ContactPoint telecom: patientPrimaryCareProviderNameIdNo.getTelecom()) {
				primaryCareDoc.addTelecom(telecom);
			}
		} else {
			addResource(primaryCareDoc = patientPrimaryCareProviderNameIdNo);
			primaryCareProvider.setPractitioner(ParserUtils.toReference(patientPrimaryCareProviderNameIdNo, primaryCareProvider, "practitioner"));
		}
		ParserUtils.updateReference(primaryCareProvider, gpRef);
	}

	/**
	 * Add a patient disability
	 * @param handicap the patient handicap
	 */
	@ComesFrom(path = "Patient.patient-disability", field = 6, comment = "Handicap")
	public void setHandicap(CodeableConcept handicap) {
		patient.addExtension(PathUtils.FHIR_EXT_PREFIX + "patient-disability", handicap);
	}

	/**
	 * Set the place of worship
	 * V2 provides a place to record the organization, FHIR only the name.  The reality is,
	 * if this is captured, it is likely just the name.
	 * @param placeOfWorship the place of worship
	 */
	@ComesFrom(path = "Patient.patient-congregation", field = 14, comment = "Place of Worship")
	public void setPlaceofWorship(Organization placeOfWorship) {
		patient.addExtension(PathUtils.FHIR_EXT_PREFIX + "patient-congregation", placeOfWorship.getNameElement());
	}
}
