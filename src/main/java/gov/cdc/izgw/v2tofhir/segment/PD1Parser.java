package gov.cdc.izgw.v2tofhir.segment;

import java.util.ArrayList;
import java.util.List;

import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Practitioner;
import org.hl7.fhir.r4.model.Provenance;

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
		addResource(patientPrimaryFacility);
		patient.addGeneralPractitioner(ParserUtils.toReference(patientPrimaryFacility));
	}

	/**
	 * Set the primary care provider 
	 * @param patientPrimaryCareProviderNameIdNo the primary care provider 
	 */
	@ComesFrom(path = "Patient.generalPractitioner.Practitioner", field = 4, comment = "Patient Primary Care Provider Name & ID No.")
	public void setPatientPrimaryCareProviderNameIdNo(Practitioner patientPrimaryCareProviderNameIdNo) {
		addResource(patientPrimaryCareProviderNameIdNo);
		patient.addGeneralPractitioner(ParserUtils.toReference(patientPrimaryCareProviderNameIdNo));
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
