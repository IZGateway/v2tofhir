package gov.cdc.izgw.v2tofhir.segment;

import java.util.ArrayList;
import java.util.List;

import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Account;
import org.hl7.fhir.r4.model.CodeType;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Encounter.EncounterLocationComponent;
import org.hl7.fhir.r4.model.Encounter.EncounterLocationStatus;
import org.hl7.fhir.r4.model.Encounter.EncounterParticipantComponent;
import org.hl7.fhir.r4.model.Encounter.EncounterStatus;
import org.hl7.fhir.r4.model.EpisodeOfCare;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Location;
import org.hl7.fhir.r4.model.Location.LocationStatus;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Practitioner;

import com.ainq.fhir.utils.PathUtils;

import gov.cdc.izgw.v2tofhir.annotation.ComesFrom;
import gov.cdc.izgw.v2tofhir.annotation.Produces;
import gov.cdc.izgw.v2tofhir.converter.MessageParser;
import gov.cdc.izgw.v2tofhir.utils.Codes;
import gov.cdc.izgw.v2tofhir.utils.ParserUtils;
import gov.cdc.izgw.v2tofhir.utils.Systems;
import lombok.extern.slf4j.Slf4j;

/**
 * Parser for PV1 Segments
 * 
 * The PV1 segment populates an encounter resource
 * @see <a href="https://build.fhir.org/ig/HL7/v2-to-fhir/ConceptMap-segment-pv1-to-encounter.html">V2 to FHIR: PV1 to Encounter Map</a>
 * @see <a href="https://build.fhir.org/ig/HL7/v2-to-fhir/ConceptMap-segment-pv1-to-patient.html">V2 to FHIR: PV1 to Patient Map</a>
 * @author Audacious Inquiry
 */
@Produces(segment = "PV1", resource = Encounter.class, 
	extra = { Patient.class, Practitioner.class, Location.class, EpisodeOfCare.class }
)
@Slf4j
public class PV1Parser extends AbstractSegmentParser {
	private static final String PARTICIPANT = "participant";
	private static final String PRACTITIONER = "practitioner";
	private Encounter encounter;
	@SuppressWarnings("unused")
	private Account account;
	private Patient patient;
	private Location location;

	private static List<FieldHandler> fieldHandlers = new ArrayList<>();

	static {
		log.debug("{} loaded", MRGParser.class.getName());
	}

	/**
	 * Construct a new PV1Parser
	 * 
	 * @param messageParser The messageParser to construct this parser for.
	 */
	public PV1Parser(MessageParser messageParser) {
		super(messageParser, "PV1");
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
		encounter = createResource(Encounter.class);
		patient = getLastResource(Patient.class);
		if (patient == null) {
			// No patient was found, create one.
			patient = createResource(Patient.class);
		}
		encounter.setSubject(ParserUtils.toReference(patient, encounter, "subject", "patient"));
		account = null;
		location = null;
		return encounter;
	}

	/**
	 * Set the patient class.
	 * Maps the class to the preferred terminology from patientClass if a value is found
	 * that maps, otherwise sets to the first coding in patientClass.
	 * Also adds the class to the Encounter.type. 
	 * @param patientClass	The patient class
	 */
	@ComesFrom(path = "Encounter.class", field = 2, table = "0004", map = "EncounterClass", comment = "Patient Class",
			   also = "Encounter.type")
	public void setPatientClass(CodeableConcept patientClass) {
		boolean notMapped = false;
		// Set to mapped value when present.
		// Encounter.class is of coding type with a preferred coding system, so it gets mapped
		encounter.setClass_(mapPatientClass(patientClass));
		
		// Otherwise set to first coding value.  It's typical that there's only one,
		// so we won't worry about the rest of them, unlike the mapping case above.
		if (!encounter.hasClass_() && patientClass.hasCoding()) {
			notMapped = true;
			encounter.setClass_(patientClass.getCodingFirstRep());
		}
		
		if (encounter.hasClass_()) {
			// We don't want to lose the original values, so we store them as the codings
			// that apply to coding.code
			CodeType code = encounter.getClass_().getCodeElement();
			for (Coding coding: patientClass.getCoding()) {
				if (notMapped) {
					// If it wasn't mapped, we stored the first one, so we skip it.
					notMapped = false;
					continue;
				}
				code.addExtension(PathUtils.FHIR_EXT_PREFIX + "/iso21090-SC-coding", coding);
			}
		}
		encounter.addType(patientClass);
	}

	private Coding mapPatientClass(CodeableConcept patientClass) {
		for (Coding coding: patientClass.getCoding()) {
			if (!coding.hasCode()) {
				continue;
			}
			
			if (coding.hasSystem() && !coding.getSystem().endsWith("0004")) {
				continue;
			}

			switch (coding.getCode()) {
			case "E":	//	Emergency
				return new Coding(Systems.V3_ACT_ENCOUNTER_CODE, "EMER", "Emergency");
			case "I":	//	Inpatient	
				return new Coding(Systems.V3_ACT_ENCOUNTER_CODE, "IMP", "inpatient");
			case "O":	//	Outpatient	
				return new Coding(Systems.V3_ACT_ENCOUNTER_CODE, "AMB", "ambulatory");
			default:
				break;
			}
		}
		return null;
	}

	/**
	 * Sets the assigned location
	 * @param assignedPatientLocation the assigned location
	 */
	@ComesFrom(path = "Encounter.location", field = 3, comment = "Assigned Patient Location")
	public void setAssignedPatientLocation(Location assignedPatientLocation) {
		location = addResource(assignedPatientLocation);
		EncounterLocationComponent loc = encounter.addLocation()
				.setLocation(ParserUtils.toReference(assignedPatientLocation, encounter, "location"));
		if (encounter.hasStatus()) {
			if (EncounterStatus.PLANNED.equals(encounter.getStatus())) {
				loc.setStatus(EncounterLocationStatus.PLANNED);
			} else {
				loc.setStatus(EncounterLocationStatus.ACTIVE);
			}
		}
	}

	/**
	 * Set the admission type.
	 * @param admissionType the admission type
	 */
	@ComesFrom(path = "Encounter.type", field = 4, table = "0007", comment = "Admission Type")
	public void setAdmissionType(CodeableConcept admissionType) {
		encounter.addType(admissionType);
	}

	/**
	 * Set the preadmit Number.
	 * @param preadmitNumber the preadmit Number
	 */
	@ComesFrom(path = "Encounter.hospitalization.preAdmissionIdentifier", field = 5, comment = "Preadmit Number")
	public void setPreadmitNumber(Identifier preadmitNumber) {
		encounter.getHospitalization().setPreAdmissionIdentifier(preadmitNumber);
	}

	/**
	 * Set the prior location
	 * @param priorPatientLocation the prior location
	 */
	@ComesFrom(path = "Encounter.location", field = 6, comment = "Prior Patient Location")
	public void setPriorPatientLocation(Location priorPatientLocation) {
		addResource(priorPatientLocation);
		EncounterLocationComponent loc = encounter.addLocation()
				.setLocation(ParserUtils.toReference(priorPatientLocation, encounter, "location"));
		loc.setStatus(EncounterLocationStatus.COMPLETED);
	}

	/**
	 * Set the attending physician
	 * @param attendingDoctor the attending physician
	 */
	@ComesFrom(path = "Encounter.participant.individual.Practitioner", field = 7, comment = "Attending Doctor")
	public void setAttendingDoctor(Practitioner attendingDoctor) {
		addResource(attendingDoctor);
		EncounterParticipantComponent participant = encounter.addParticipant()
				.setIndividual(ParserUtils.toReference(attendingDoctor, encounter, PARTICIPANT, PRACTITIONER, 
						Codes.ATTENDING_PARTICIPANT.getCodingFirstRep().getDisplay()));
		participant.addType(Codes.ATTENDING_PARTICIPANT);
	}

	/**
	 * Set the referring physician
	 * @param referringDoctor the referring physician
	 */
	@ComesFrom(path = "Encounter.participant.individual.Practitioner", field = 8, comment = "Referring Doctor")
	public void setReferringDoctor(Practitioner referringDoctor) {
		addResource(referringDoctor);
		EncounterParticipantComponent participant = encounter.addParticipant()
				.setIndividual(ParserUtils.toReference(referringDoctor, encounter, PARTICIPANT, PRACTITIONER, 
						Codes.REFERRING_PARTICIPANT.getCodingFirstRep().getDisplay()));
		participant.addType(Codes.REFERRING_PARTICIPANT);
	}

	/**
	 * Set the consulting physician
	 * @param consultingDoctor the consulting physician
	 */
	@ComesFrom(path = "Encounter.participant.individual.Practitioner", field = 9, comment = "Consulting Doctor")
	public void setConsultingDoctor(Practitioner consultingDoctor) {
		addResource(consultingDoctor);
		EncounterParticipantComponent participant = encounter.addParticipant()
				.setIndividual(ParserUtils.toReference(consultingDoctor, encounter, PARTICIPANT, PRACTITIONER,
						Codes.CONSULTING_PARTICIPANT.getCodingFirstRep().getDisplay()));
		participant.addType(Codes.CONSULTING_PARTICIPANT);
	}

	/**
	 * Set the hospital service
	 * @param hospitalService The hospital service
	 */
	@ComesFrom(path = "Encounter.serviceType", field = 10, table = "0069", comment = "Hospital Service")
	public void setHospitalService(CodeableConcept hospitalService) {
		encounter.setServiceType(hospitalService);
	}

	/**
	 * Set the temporary location
	 * @param temporaryLocation the temporary location
	 */
	@ComesFrom(path = "Encounter.location", field = 11, comment = "Temporary Location")
	public void setTemporaryLocation(Location temporaryLocation) {
		addResource(temporaryLocation);
		EncounterLocationComponent loc = encounter.addLocation()
				.setLocation(ParserUtils.toReference(temporaryLocation, encounter, "location"));
		loc.setStatus(EncounterLocationStatus.ACTIVE);
		// TODO: Add a temporary indicator to location
	}

	/**
	 * Set the readmission indicator
	 * @param readmissionIndicator the readmission indicator
	 */
	@ComesFrom(path = "Encounter.hospitalization.reAdmission", field = 13, table = "0092", comment = "Re-admission Indicator")
	public void setReadmissionIndicator(CodeableConcept readmissionIndicator) {
		encounter.getHospitalization().setReAdmission(readmissionIndicator);
	}

	/**
	 * Set the admit source
	 * @param admitSource the admit source
	 */
	@ComesFrom(path = "Encounter.hospitalization.admitSource", field = 14, table = "0023", comment = "Admit Source")
	public void setAdmitSource(CodeableConcept admitSource) {
		encounter.getHospitalization().setAdmitSource(admitSource);
	}

	/**
	 * Set the ambulatory status
	 * Sets the ambulatory status, a code reflecting needs for special arrangements, such
	 * as wheelchair, disability or other patient status requiring additional care or support.
	 * @param ambulatoryStatus the ambulatory status
	 */
	@ComesFrom(path = "Encounter.hospitalization.specialArrangement", field = 15, comment = "Ambulatory Status")
	public void setAmbulatoryStatus(CodeableConcept ambulatoryStatus) {
		encounter.getHospitalization().addSpecialArrangement(ambulatoryStatus);
	}
	
	/**
	 * Add the vipIndicator
	 * @param vipIndicator the vipIndicator
	 */
	@ComesFrom(path = "Encounter.hospitalization.specialCourtesy", field = 16, comment = "VIP Indicator")
	public void setVIPIndicator(CodeableConcept vipIndicator) {
		encounter.getHospitalization().addSpecialCourtesy(vipIndicator);
	}

	/**
	 * Set the admitting physician
	 * @param admittingDoctor the admitting physician
	 */
	@ComesFrom(path = "Encounter.participant.individual.Practitioner", field = 17, comment = "Admitting Doctor")
	public void setAdmittingDoctor(Practitioner admittingDoctor) {
		addResource(admittingDoctor);
		EncounterParticipantComponent participant = encounter.addParticipant()
				.setIndividual(ParserUtils.toReference(admittingDoctor, encounter, PARTICIPANT, PRACTITIONER, "admitting"));
		participant.addType(Codes.ADMITTING_PARTICIPANT);
	}

	/**
	 * Set the visit number
	 * @param visitNumber the visit number
	 */
	@ComesFrom(path = "Encounter.identifier", field = 19, comment = "Visit Number")
	public void setVisitNumber(Identifier visitNumber) {
		visitNumber.setType(Codes.VISIT_NUMBER);
		encounter.addIdentifier(visitNumber);
	}

	/**
	 * Set the discharge disposition
	 * @param dischargeDisposition the discharge disposition
	 */
	@ComesFrom(path = "Encounter.hospitalization.dischargeDisposition", field = 36, table="0112", comment = "Discharge Disposition")
	public void setDischargeDisposition(CodeableConcept dischargeDisposition) {
		encounter.getHospitalization().setDischargeDisposition(dischargeDisposition);
	}

	/**
	 * Set the discharge location
	 * @param dischargedToLocation the discharge location
	 */
	@ComesFrom(path = "Encounter.hospitalization.destination.Location", field = 37, comment = "Discharged to Location")
	public void setDischargedToLocation(Location dischargedToLocation) {
		addResource(dischargedToLocation);
		encounter.getHospitalization().setDestination(ParserUtils.toReference(dischargedToLocation, encounter, "destination"));
	}

	/**
	 * Set the dietary accomodations
	 * @param dietType the dietary accomodations
	 */
	@ComesFrom(path = "Encounter.hospitalization.dietPreference", table="0114", field = 38, comment = "Diet Type")
	public void setDietType(CodeableConcept dietType) {
		encounter.getHospitalization().addDietPreference(dietType);
	}

	/**
	 * Set the status of the bed 
	 * @param bedStatus the status of the bed 
	 */
	@ComesFrom(path = "Encounter.location.location(Encounter.location.Location.operationalStatus)", field = 40, table = "0116", comment = "Bed Status")
	public void setBedStatus(Coding bedStatus) {
		if (location != null) {
			location.setStatus(mapBedStatus(bedStatus));
		}
	}

	private LocationStatus mapBedStatus(Coding bedStatus) {
		// TODO Auto-generated method stub
		if (!bedStatus.hasCode()) {
			return null;
		}
		if (bedStatus.hasSystem() && bedStatus.getSystem().endsWith("0116")) {
			switch (bedStatus.getCode()) {
			case "O", "U", "I":
				return LocationStatus.ACTIVE;
			case "H", "K":
				return LocationStatus.SUSPENDED;
			case "C":
				return LocationStatus.INACTIVE;
			default:
				return null;
			}
		}
		return null;
	}

	/**
	 * Set the pending location
	 * @param pendingLocation the pending location
	 */
	@ComesFrom(path = "Encounter.location", field = 42, comment = "Pending Location")
	public void setPendingLocation(Location pendingLocation) {
		location = addResource(pendingLocation);
		EncounterLocationComponent loc = encounter.addLocation()
				.setLocation(ParserUtils.toReference(pendingLocation, encounter, "location"));
		loc.setStatus(EncounterLocationStatus.PLANNED);
	}
	
	/**
	 * Set the prior temp location
	 * @param priorTemporaryLocation the prior temp location
	 */
	@ComesFrom(path = "Encounter.location", field = 43, comment = "Prior Temporary Location")
	public void setPriorTemporaryLocation(Location priorTemporaryLocation) {
		location = addResource(priorTemporaryLocation);
		EncounterLocationComponent loc = encounter.addLocation()
				.setLocation(ParserUtils.toReference(priorTemporaryLocation, encounter, "location"));
		loc.setStatus(EncounterLocationStatus.COMPLETED);
	}
		
	/**
	 * Set the admit date
	 * @param admitDateTime the admit date
	 */
	@ComesFrom(path = "Encounter.period.start", field = 44, comment = "Admit Date/Time")	
	public void setAdmitDateTime(DateTimeType admitDateTime) {
		encounter.getPeriod().setStartElement(admitDateTime);
	}

	/**
	 * Set the discharge date
	 * @param dischargeDateTime the discharge date
	 */
	@ComesFrom(path = "Encounter.period.end", field = 45, comment = "Discharge Date/Time")
	public void setDischargeDateTime(DateTimeType dischargeDateTime) {
		encounter.getPeriod().setEndElement(dischargeDateTime);
	}

	/**
	 * Set an alternate visit id
	 * @param alternateVisitID the alternate visit id
	 */
	@ComesFrom(path = "Encounter.identifier", field = 50, comment = "Alternate Visit ID")
	public void setAlternateVisitID(Identifier alternateVisitID) {
		encounter.addIdentifier(alternateVisitID);
	}

	/**
	 * Add other healthcare providers
	 * @param otherHealthcareProvider the other healthcare provider
	 */
	@ComesFrom(path = "Encounter.participant.individual.Practitioner", field = 52, comment = "Other Healthcare Provider")
	public void setOtherHealthcareProvider(Practitioner otherHealthcareProvider) {
		addResource(otherHealthcareProvider);
		EncounterParticipantComponent part = encounter.addParticipant()
				.setIndividual(ParserUtils.toReference(otherHealthcareProvider, encounter, PRACTITIONER));
		part.addType(Codes.PARTICIPANT);
	}

	/**
	 * Set the episode of care identifier
	 * Create an episode of care and add the identifier.
	 * @param serviceEpisodeIdentifier the episode of care identifier
	 */
	@ComesFrom(path = "Encounter.episodeOfCare", field = 54, comment = "Service Episode Identifier")
	public void setServiceEpisodeIdentifier(Identifier serviceEpisodeIdentifier) {
		EpisodeOfCare episodeOfCare = createResource(EpisodeOfCare.class);
		episodeOfCare.setPatient(ParserUtils.toReference(patient, episodeOfCare, "patient"));
		episodeOfCare.addIdentifier(serviceEpisodeIdentifier);
		encounter.addEpisodeOfCare(ParserUtils.toReference(episodeOfCare, encounter, "episode-of-care"));
	}
}
