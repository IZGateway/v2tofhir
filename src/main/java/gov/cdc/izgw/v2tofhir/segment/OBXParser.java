package gov.cdc.izgw.v2tofhir.segment;

import java.util.List;
import java.math.BigDecimal;
import java.util.ArrayList;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.instance.model.api.IBase;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Address;
import org.hl7.fhir.r4.model.Attachment;
import org.hl7.fhir.r4.model.BaseDateTimeType;
import org.hl7.fhir.r4.model.CodeType;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.ContactPoint;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Device;
import org.hl7.fhir.r4.model.DocumentReference;
import org.hl7.fhir.r4.model.DocumentReference.ReferredDocumentStatus;
import org.hl7.fhir.r4.model.Enumerations.DocumentReferenceStatus;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.ImmunizationRecommendation;
import org.hl7.fhir.r4.model.ImmunizationRecommendation.ImmunizationRecommendationRecommendationComponent;
import org.hl7.fhir.r4.model.ImmunizationRecommendation.ImmunizationRecommendationRecommendationDateCriterionComponent;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Immunization;
import org.hl7.fhir.r4.model.Immunization.ImmunizationEducationComponent;
import org.hl7.fhir.r4.model.Location;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Observation.ObservationStatus;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.PositiveIntType;
import org.hl7.fhir.r4.model.Practitioner;
import org.hl7.fhir.r4.model.PractitionerRole;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Range;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.RelatedPerson;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.TimeType;

import ca.uhn.hl7v2.model.Composite;
import ca.uhn.hl7v2.model.Type;
import gov.cdc.izgw.v2tofhir.annotation.ComesFrom;
import gov.cdc.izgw.v2tofhir.annotation.Produces;
import gov.cdc.izgw.v2tofhir.converter.DatatypeConverter;
import gov.cdc.izgw.v2tofhir.converter.MessageParser;
import gov.cdc.izgw.v2tofhir.utils.Codes;
import gov.cdc.izgw.v2tofhir.utils.ParserUtils;
import gov.cdc.izgw.v2tofhir.utils.Systems;
import gov.cdc.izgw.v2tofhir.utils.TextUtils;
import gov.cdc.izgw.v2tofhir.utils.Units;

/**
 * OBXParser parses any OBX segments in a message to an Immunization if the message is a VXU or an 
 * response to an immunization query.
 * 
 * It merges OBX information into the Immunization or ImmunizationRecommendation resource where 
 * appropriate, otherwise it creates new Observation resources.
 * 
 * @author Audacious Inquiry
 *
 * @see <a href="https://hl7.org/fhir/uv/v2mappings/2024Jan/ConceptMap-segment-rxr-to-immunization.html">V2 to FHIR: RXR to Immunization</a>
 */
@Produces(segment = "OBX", resource=Observation.class,
		  extra = { Immunization.class, ImmunizationRecommendation.class })
public class OBXParser extends AbstractSegmentParser {
	private static final String PERFORMER = "performer";
	private static List<FieldHandler> fieldHandlers = new ArrayList<>();
	private IzDetail izDetail;
	private VisCode redirect = null;
	private ImmunizationRecommendationRecommendationComponent recommendation;
	private String type = null;
	private Observation observation = null;
	/** The media associated with the observation if any */
	private DocumentReference docRef = null;
	private PractitionerRole performer;
	
	/**
	 * Create an RXA Parser for the specified MessageParser
	 * @param p	The message parser.
	 */
	public OBXParser(MessageParser p) {
		super(p, "OBX");
		if (fieldHandlers.isEmpty()) {
			FieldHandler.initFieldHandlers(this, fieldHandlers);
		}
	}

	@Override
	protected List<FieldHandler> getFieldHandlers() {
		return fieldHandlers;
	}

	@Override
	public IBaseResource setup() {
		izDetail = IzDetail.get(getMessageParser());
		if (izDetail.hasRecommendation()) {
			recommendation = izDetail.getRecommendation();
		}
			
		observation = createResource(Observation.class);
		return observation;
	}
	
	private enum VisCode {
		// Used with Immunization
		VACCINE_ELIGIBILITY_CODE("64994-7"),
		VIS_DOCUMENT_TYPE_CODE("69764-9"),
		VIS_VERSION_DATE_CODE("29768-9"),
		VIS_DELIVERY_DATE_CODE("29769-7"),
		VIS_VACCINE_TYPE_CODE("30956-7"),
		// Used with ImmunizationRecommendation
		FORECAST_SCHEDULE("59779-9"),
		FORECAST_VACCINE_CODE("30956-7"),
		FORECAST_SERIES_NAME("59780-7"),
		FORECAST_DOSE_NUMBER("30973-2"),
		DOSE_VALIDITY("59781-5"),
		FORECAST_NUMBER_DOSES("59782-3"),
		SERIES_STATUS("59783-1"),
		NEXT_DOSE_EARLIEST("30981-5", "Earliest date to give"),
		NEXT_DOSE_RECOMMENDED("30980-7", "Date vaccine due"),
		NEXT_DOSE_LATEST("59777-3", "Latest date to give immunization"),
		NEXT_DOSE_OVERDUE("59778-1", "Date when overdue for immunization"),
		WHY_INVALID("30982-3"),
		// None of the above
		OTHER("");
		private final String loincCode;
		private final String display;
		private VisCode(String loincCode) {
			this.loincCode = loincCode;
			this.display = null;
		}
		private VisCode(String loincCode, String display) {
			this.loincCode = loincCode;
			this.display = display;
		}
		public String getCode() {
			return loincCode;
		}
		private static VisCode match(CodeableConcept cc) {
			for (Coding coding: cc.getCoding()) {
				VisCode v = match(coding);
				if (!VisCode.OTHER.equals(v)) {
					return v;
				}
			}
			return OTHER;
		}
		private static VisCode match(Coding coding) {
			if (Systems.LOINC.equals(coding.getSystem())) {
				for (VisCode v: VisCode.values()) {
					if (coding.hasCode() && coding.getCode().equals(v.loincCode)) {
						return v;
					}
				}
			}
			return OTHER;
		}
		boolean isPresent(ImmunizationEducationComponent education) {
			switch (this) {
			case VIS_DELIVERY_DATE_CODE:
				return education.hasPresentationDate();
			case VIS_DOCUMENT_TYPE_CODE:
				return education.getDocumentTypeElement().hasValue();
			case VIS_VACCINE_TYPE_CODE:
				return education.getDocumentTypeElement().hasExtension();
			case VIS_VERSION_DATE_CODE:
				return education.hasPublicationDateElement();
			default:
				return false;
			}
		}
		String getDisplay() {
			return display;
		}
	}
	
	
	/**
	 * Save the type of value in OBX-5
	 * @param type the type of value in OBX-5
	 */
	@ComesFrom(path = "", field = 2, comment = "Observation Type") 
	public void setType(StringType type) {
		this.type = type.getValueAsString(); 
	}
	
	/**
	 * Figure out where to redirect OBX-5 information for VIS OBX types 
	 * @param observationCode the observation code in OBX-3
	 */
	@ComesFrom(path = "Observation.code", field = 3, comment = "Observation Identifier") 
	public void redirectTo(CodeableConcept observationCode) {
		observation.setCode(observationCode);
		if (izDetail.hasImmunization() || izDetail.hasRecommendation()) {
			redirect = VisCode.match(observationCode);
		} else {
			redirect = null;
		}
	}
	
	/**
	 * Set the value
	 * @param v2Type	The v2 data type to convert.
	 */
	@ComesFrom(path = "Observation.value[x]", field = 5, comment = "Observation Value")
	public void setValue(Type v2Type) {
		v2Type = DatatypeConverter.adjustIfVaries(v2Type);
		if (type == null) {
			getParser().warn("Type is unknown for OBX-5");
			return;
		}
		Class<? extends IBase> target = null;
		switch (type) {
		case "AD":	target = Address.class; break;	//	Address	
		case "CE", 
			 "CF",  		
			 "CNE",		
			 "CWE":	
				 // Coded Element (With Formatted Values)
				 //	Coded with No Exceptions
				 // Coded Entry
				 target = CodeableConcept.class; break;		
		case "CK":	target = Identifier.class; break;	//	Composite ID With Check Digit	
		case "CX":	target = Identifier.class; break;	//	Extended Composite ID With Check Digit	
		case "DT":	target = DateTimeType.class; break;	//	Observation doesn't like DateType	
		case "DTM":	target = DateTimeType.class; break;	//	Time Stamp (Date & Time)
		case "ED":  target = Attachment.class; break;	//	Encapsulated Data	
		case "FT":	target = StringType.class; break; //	Formatted Text (Display)	
		case "HD":	target = Identifier.class; break; //	Hierarchic Designator
		case "ID":	target = CodeType.class; break; //	Coded Value for HL7 Defined Tables	
		case "IS":	target = CodeType.class; break; //	Coded Value for User-Defined Tables	
		case "NM":	target = Quantity.class; break; //	Numeric	(Observation only accepts Quantity)
		case "PL":	target = Location.class; break; //	Person Location
		case "PN":	target = HumanName.class; break; //	Person Name
		case "RP":	target = Attachment.class; break; //	Reference Pointer
		case "SN":	
			if (v2Type instanceof Composite comp && comp.getComponents().length > 3) {
				target = Range.class; 	
			} else {
				target = Quantity.class;
			}
			break;
		case "ST":	target = StringType.class; break; //	String Data.	
		case "TM":	target = TimeType.class; break; //	Time	
		case "TN":	target = ContactPoint.class; break; //	Telephone Number	
		case "TS":	target = DateTimeType.class; break; //	TimeStamp	 (OBX requires DateTimeType)
		case "TX":	target = StringType.class; break; //	Text Data (Display)	
		case "XAD":	target = Address.class; break; //	Extended Address	
		case "CN":	target = RelatedPerson.class; break;	//	Composite ID And Name	
		case "XCN":	target = RelatedPerson.class; break; //	Extended Composite Name And Number For Persons	
		case "XON":	target = Organization.class; break; //	Extended Composite Name And Number For Organizations	
		case "XPN":	target = HumanName.class; break; //	Extended Person Name	
		case "XTN":	target = ContactPoint.class; break; //	Extended Telecommunications Number
		case "CP",	//	Composite Price	
			 "DR",	//	Date/Time Range	
			 "MA",	//	Multiplexed Array	
			 "MO",	//	Money	
			 "NA":	//	Reference Pointer	
		default:
			break;
		}
		
		if (target == null) {
			getParser().warn("Cannot convert V2 {}", type);
			return;
		}
		
		IBase converted = DatatypeConverter.convert(target, v2Type, null);
		if (converted instanceof Organization org) {
			observation.setValue(org.getNameElement());
		} else if (converted instanceof RelatedPerson rp) {
			observation.setValue(new StringType(TextUtils.toString(rp.getNameFirstRep())));
		} else if (converted instanceof Practitioner pr) {
			observation.setValue(new StringType(TextUtils.toString(pr.getNameFirstRep())));
		} else if (converted instanceof Location loc) {
			observation.setValue(new StringType(TextUtils.toString(loc.getNameElement())));
		} else if (converted instanceof Address addr) {
			observation.setValue(new StringType(TextUtils.toString(addr)));
		} else if (converted instanceof Identifier identifier) {
			// You cannot set an identifier value directly into Observation.value
			if (identifier.hasSystem() && identifier.hasValue()) {
				observation.setValue(new StringType(identifier.getSystem() + "#" + identifier.getValue()));
			} else if (identifier.hasValue()) {
				observation.setValue(identifier.getValueElement());
			} else if (identifier.hasSystem()) {
				observation.setValue(new StringType(identifier.getSystem()));
			}
			// Save the identifier as an external identifier for the observation
			observation.addIdentifier(identifier);
		} else if (converted instanceof Attachment attachment) {
			docRef = createDocRef(attachment);
			observation.getDerivedFrom().add(ParserUtils.toReference(docRef, observation, "derivedFrom"));
		} else if (converted instanceof org.hl7.fhir.r4.model.Type t){
			observation.setValue(t);
		}
		
		if (redirect == null || VisCode.OTHER.equals(redirect)) {
			return;
		}
		
		// This observation applies to either a created Immunization or an ImmunizationRecommendation,
		// link it via Observation.partOf.
		linkObservation();

		if (izDetail.hasImmunization()) {
			handleVisObservations(converted);
		} else if (izDetail.hasRecommendation()) {
			handleRecommendationObservations(converted);
		}
	}

	private DocumentReference createDocRef(Attachment attachment) {
		// TODO Auto-generated method stub
		docRef = createResource(DocumentReference.class);
		docRef.setStatus(DocumentReferenceStatus.CURRENT);
		docRef.addContent().setAttachment(attachment);
		// Backlink the document reference to the observation
		docRef.getContext().getRelated().add(ParserUtils.toReference(observation, docRef, "relatedContext"));
		return docRef;
	}
	
	@Override
	/**
	 * Finish populating the docRef resource if present from the observation 
	 */
	public void finish() {
		if (docRef == null || docRef.isEmpty()) {
			return;
		}
		docRef.setDate(observation.getEffectiveDateTimeType().getValue());
		// The authors of the document reference are the performers of the observation.
		for (Reference r: observation.getPerformer()) {
			Resource res = (Resource) r.getResource();
			if (res != null) {
				docRef.getAuthor().add(ParserUtils.toReference(res, docRef, "author"));
			}
		}
		
		// Update status and docStatus based on observation status
		ObservationStatus status = observation.getStatus();
		if (status != null) {
			switch (status) {
			case AMENDED:
				docRef.setStatus(DocumentReferenceStatus.CURRENT);
				docRef.setDocStatus(ReferredDocumentStatus.AMENDED);
				break;
			case CANCELLED:
				docRef.setStatus(DocumentReferenceStatus.SUPERSEDED);
				docRef.setDocStatus(ReferredDocumentStatus.ENTEREDINERROR);
				break;
			case CORRECTED:
				docRef.setStatus(DocumentReferenceStatus.CURRENT);
				docRef.setDocStatus(ReferredDocumentStatus.AMENDED);
				break;
			case ENTEREDINERROR:
				docRef.setStatus(DocumentReferenceStatus.ENTEREDINERROR);
				docRef.setDocStatus(ReferredDocumentStatus.ENTEREDINERROR);
				break;
			case FINAL:
				docRef.setStatus(DocumentReferenceStatus.CURRENT);
				docRef.setDocStatus(ReferredDocumentStatus.FINAL);
				break;
			case NULL:
				docRef.setStatus(DocumentReferenceStatus.NULL);
				docRef.setDocStatus(ReferredDocumentStatus.NULL);
				break;
			case PRELIMINARY:
				docRef.setStatus(DocumentReferenceStatus.CURRENT);
				docRef.setDocStatus(ReferredDocumentStatus.PRELIMINARY);
				break;
			case REGISTERED:
				docRef.setStatus(DocumentReferenceStatus.CURRENT);
				docRef.setDocStatus(ReferredDocumentStatus.PRELIMINARY);
				break;
			case UNKNOWN:
				docRef.setStatus(null);
				docRef.setDocStatus(null);
				break;
			default:
				break;
			}
		}
		// The subject (and sourcePatientInfo) of the DocumentReference is the subject of the observation.
		Resource subject = getResource(observation.getSubject());
		if (subject != null) {
			docRef.setSubject(ParserUtils.toReference(subject, docRef, "subject"));
			if (subject instanceof Patient) {
				docRef.getContext().setSourcePatientInfo(ParserUtils.toReference(subject, docRef, "sourcePatientInfo"));
			}
		}
		
		// The encounter of the DocumentReference is the encounter of the observation.
		Resource encounter = getResource(observation.getEncounter());
		if (encounter != null) {
			docRef.getContext().getEncounter().add(ParserUtils.toReference(encounter, docRef, "encounter"));
		}
		
	}

	private Resource getResource(Reference ref) {
		if (ref == null) {
			return null;
		}
		return (Resource) ref.getResource();
	}

	/**
	 * Set the units of the Observation 
	 * @param units the units of the Observation 
	 */
	@ComesFrom(path = "Observation.valueQuantity.unit", field = 6, comment = "Observation Units")
	public void setUnits(CodeableConcept units) {
		org.hl7.fhir.r4.model.Type value = observation.getValue();
		if (value instanceof org.hl7.fhir.r4.model.PrimitiveType<?> t) {
			String v = t.asStringValue();
			value = new Quantity().setValue(new BigDecimal(v));
		}
		if (value instanceof Quantity qty) {
			String code = units.getCodingFirstRep().getCode();
			Coding unit = Units.toUcum(code);
			if (unit != null) {
				if (unit.hasDisplay()) {
					qty.setUnit(unit.getDisplay());
				} else {
					qty.setUnit(code);
				}
				qty.setCode(unit.getCode());
				qty.setSystem(unit.getSystem());
			} else {
				qty.setUnit(units.getCodingFirstRep().getCode());
			}
		}
	}
	
	private void handleVisObservations(IBase converted) {
		if (VisCode.VACCINE_ELIGIBILITY_CODE.equals(redirect)) {
			izDetail.immunization.addProgramEligibility((CodeableConcept) converted);
			observation.addPartOf(ParserUtils.toReference(izDetail.immunization, observation, "partOf"));
			return;
		} 
		ImmunizationEducationComponent education = getLastEducation(izDetail.immunization);
		// If the observation is already present in education
		if (redirect.isPresent(education)) {
			// Create a new education element.
			education = izDetail.immunization.addEducation();
		}
		switch (redirect) {
		case VIS_DELIVERY_DATE_CODE:
			education.setPresentationDateElement(
					DatatypeConverter.castInto((BaseDateTimeType)converted, new DateTimeType()));
			break;
		case VIS_DOCUMENT_TYPE_CODE:
			if (converted instanceof StringType sv) {
				education.setDocumentTypeElement(sv);
			} else if (converted instanceof CodeableConcept cc) {
				education.setDocumentType(TextUtils.toString(cc));
			}
			break;
		case VIS_VACCINE_TYPE_CODE:
			education.getDocumentTypeElement()
				.addExtension()
					.setUrl("http://hl7.org/fhir/StructureDefinition/iso21090-SC-coding")
					.setValue(((CodeableConcept)converted).getCodingFirstRep());
			break;
		case VIS_VERSION_DATE_CODE:
			education.setPublicationDateElement(
					DatatypeConverter.castInto((BaseDateTimeType)converted, new DateTimeType()));
			break;
		default:
			break;
		}
	}

	/**
	 * Link the Observation to the Immunization or ImmunizationRecommendation to
	 * which it applies.
	 */
	private void linkObservation() {
		if (!VisCode.OTHER.equals(redirect)) {
			Reference ref = null;
			if (izDetail.hasImmunization()) {
				ref = ParserUtils.toReference(izDetail.immunization, observation, "partof");
			} else if (izDetail.hasRecommendation()) {
				ref = ParserUtils.toReference(izDetail.immunizationRecommendation, observation, "partof");
			}
			if (ref != null && !observation.getPartOf().contains(ref)) {
				observation.addPartOf(ref);
			}
		}
	}

	private void handleRecommendationObservations(IBase converted) {
		if (redirect == null) {
			return;
		}
		
		ImmunizationRecommendationRecommendationDateCriterionComponent criterion = null;
		int value = 0;
		switch (redirect) {
		case DOSE_VALIDITY:
			break;
		case FORECAST_DOSE_NUMBER:
			value = ((Quantity)converted).getValue().intValue(); 
			recommendation.setDoseNumber(new PositiveIntType(value));
			break;
		case FORECAST_NUMBER_DOSES:
			value = ((Quantity)converted).getValue().intValue(); 
			recommendation.setSeriesDoses(new PositiveIntType(value));
			break;
		case FORECAST_SCHEDULE:
			break;
		case FORECAST_SERIES_NAME:
			recommendation.setSeriesElement((StringType)converted);
			break;
		case FORECAST_VACCINE_CODE:
			recommendation.addVaccineCode((CodeableConcept)converted);
			break;
		case NEXT_DOSE_OVERDUE,
			 NEXT_DOSE_EARLIEST,
			 NEXT_DOSE_LATEST,
			 NEXT_DOSE_RECOMMENDED:
			criterion = recommendation.addDateCriterion();
			criterion.setCode(new CodeableConcept().addCoding(new Coding(Systems.LOINC, redirect.getCode(), redirect.getDisplay())));
			criterion.setValueElement(DatatypeConverter.castInto((BaseDateTimeType)converted, new DateTimeType()));
			break;
		case SERIES_STATUS:
			recommendation.setForecastStatus((CodeableConcept)converted);
			break;
		case WHY_INVALID:
			break;
		default:
			break;
		}
	}
		
	private ImmunizationEducationComponent getLastEducation(Immunization immunization) {
		if (!immunization.hasEducation()) {
			return immunization.getEducationFirstRep();
		}
		List<ImmunizationEducationComponent> l = immunization.getEducation();
		return l.get(l.size() - 1);
	}
	
	/**
	 * Set the reference range
	 * @param referenceRange	the reference range
	 */
	@ComesFrom(path = "Observation.referenceRange.text", field = 7, comment = "Reference Range")	
	public void setReferenceRange(StringType referenceRange) {
		observation.addReferenceRange().setTextElement(referenceRange); 
	}
	
	/**
	 * Set the interpretation
	 * @param interpretationCode the interpretation
	 */
	@ComesFrom(path = "Observation.interpretation", field = 8, table="0078", comment = "Interpretation Code")	
	public void setInterpretationCodes(CodeableConcept interpretationCode) {
		for (Coding coding: interpretationCode.getCoding()) {
			if (coding.hasSystem() && StringUtils.endsWith(coding.getSystem(), "0078")) {
				coding.setSystem("http://terminology.hl7.org/CodeSystem/v3-ObservationInterpretation");
			}
		}
		observation.addInterpretation(interpretationCode); 
	}
		
	/**
	 * Set the abnormal test code
	 * @param natureofAbnormalTest the abnormal test code
	 */
	@ComesFrom(path = "Observation.observation-nature-of-abnormal-test", table = "0080", field = 10, comment = "Nature of Abnormal Test")	
	public void setNatureofAbnormalTest(CodeType natureofAbnormalTest) 
	{	observation.addExtension()
			.setUrl("http://hl7.org/fhir/StructureDefinition/observation-nature-of-abnormal-test")
			.setValue(natureofAbnormalTest);
	}
	
	/**
	 * Set the status code from V2 Table 0085
	 * @param observationResultStatus the status code from V2 Table 0085
	 */
	@ComesFrom(path = "Observation.status", field = 11, table = "0085", comment = "Observation Result Status")	
	public void setObservationResultStatus(CodeType observationResultStatus) 
	{		observation.setStatus(toObservationStatus(observationResultStatus)); 
	}
		
	/**
	 * Convert from table 0085 to ObservationStatus
	 * @param observationResultStatus a code from table 0085 
	 * @return the converted ObservationStatus value
	 */
	private ObservationStatus toObservationStatus(CodeType observationResultStatus) {
		if (!observationResultStatus.hasCode()) {
			return null;
		}
		switch (observationResultStatus.getCode()) {
		case "C":	return ObservationStatus.CORRECTED;
		case "D":	return ObservationStatus.ENTEREDINERROR;
		case "F":	return ObservationStatus.FINAL;
		case "I":	return ObservationStatus.REGISTERED;
		case "P":	return ObservationStatus.PRELIMINARY;
		case "R":	return ObservationStatus.PRELIMINARY;
		case "S":	return ObservationStatus.PRELIMINARY;
		case "U":	return ObservationStatus.FINAL;
		case "W":	return ObservationStatus.ENTEREDINERROR;
		case "X":	return ObservationStatus.CANCELLED;
		default:	return null;
		}
	}

	/**
	 * Set the effectiveTime
	 * @param dateTimeoftheObservation the effectiveTime
	 */
	@ComesFrom(path = "Observation.effectiveDateTime", field = 14, comment = "Date/Time of the Observation")
	public void setDateTimeoftheObservation(DateTimeType dateTimeoftheObservation) {		observation.setEffective(dateTimeoftheObservation); 
	}
	
	
	/**
	 * Set the producing organization identifier
	 * @param producersId	The producer organization's identifier
	 */
	@ComesFrom(path = "Observation.performer.PractitionerRole.organization.Organization.identifier", field = 15, comment = "Producer's ID")
	public void setProducersID(Identifier producersId) 
	{
		Organization producer = null;
		if (!getPerformer().hasOrganization()) {
			producer = createResource(Organization.class);
			producer.addIdentifier(producersId);
			performer.setOrganization(ParserUtils.toReference(producer, performer, PERFORMER));
		} else {
			producer = ParserUtils.getResource(Organization.class, performer.getOrganization()); 
			producer.addIdentifier(producersId);
			ParserUtils.toReference(producer, performer, PERFORMER);  // Force reference update with identifier
		}
	}
	
	/**
	 * Get or create if necessary the performer of the observation.
	 * Attaches the reference to the performer to Observation.performer.
	 * @return the performer of the observation.
	 */
	private PractitionerRole getPerformer() {
		if (performer == null) {
			performer = createResource(PractitionerRole.class);
			performer.getCodeFirstRep()
				.getCodingFirstRep()
					.setSystem("http://terminology.hl7.org/CodeSystem/practitioner-role")
					.setCode("responsibleObserver")
					.setDisplay("Responsible Observer");
			observation.addPerformer(ParserUtils.toReference(performer, observation, "performer"));
		}
		return performer;
	}

	/**
	 * Set the responsible observer
	 * @param responsibleObserver the responsible observer
	 */
	@ComesFrom(path = "Observation.performer.PractitionerRole.practitioner", field = 16, comment = "Responsible Observer")
	public void setResponsibleObserver(Practitioner responsibleObserver) 
	{
		addResource(responsibleObserver);		getPerformer();
		performer.setPractitioner(ParserUtils.toReference(responsibleObserver, performer, PERFORMER));
	}
	
	/**
	 * Set the method of observation
	 * @param observationMethod the method of observation
	 */
	@ComesFrom(path = "Observation.method", field = 17, comment = "Observation Method")
	public void setObservationMethod(CodeableConcept observationMethod) 
	{		observation.setMethod(observationMethod);
	}
	
	/**
	 * Set the device identifier
	 * @param equipmentInstanceIdentifier the device identifier
	 */
	@ComesFrom(path = "Observation.device.Device.identifier", field = 18, comment = "Equipment Instance Identifier")
	public void setEquipmentInstanceIdentifier(Identifier equipmentInstanceIdentifier) 
	{
		Device device = createResource(Device.class);
		device.addIdentifier(equipmentInstanceIdentifier);
		observation.setDevice(ParserUtils.toReference(device, observation, "device"));
	}
	
	/**
	 * Set the date time of the analysis
	 * @param dateTimeoftheAnalysis the date time of the analysis
	 */
	@ComesFrom(path = "Observation.observation-analysis-date-time", field = 19, comment = "Date/Time of the Analysis")
	public void setDateTimeoftheAnalysis(DateTimeType dateTimeoftheAnalysis) 
	{
		observation.addExtension()
			.setUrl("http://hl7.org/fhir/StructureDefinition/observation-analysis-date-time")
			.setValue(dateTimeoftheAnalysis);
	}
	
	/**
	 * Set the body site
	 * @param observationSite the body site
	 */
	@ComesFrom(path = "Observation.bodySite", field = 20, comment = "Observation Site")
	public void setObservationSite(CodeableConcept observationSite) 
	{		observation.setBodySite(observationSite);
	}
	
	/**
	 * Set the observation identifier
	 * @param observationInstanceIdentifier	the observation identifier
	 */
	@ComesFrom(path = "Observation.identifier", field = 21, comment = "Observation Instance Identifier")
	public void setObservationInstanceIdentifier(Identifier observationInstanceIdentifier) {
		if (!observationInstanceIdentifier.hasType()) {
			observationInstanceIdentifier.setType(Codes.FILLER_ORDER_IDENTIFIER_TYPE);
		}
		observation.addIdentifier(observationInstanceIdentifier);
	}
	
	
	/**
	 * Set the performing organization
	 * @param performingOrganization the performing organization
	 */
	@ComesFrom(path = "Observation.performer", field = 23, comment = "Performing Organization Name")
	public void setPerformingOrganizationName(Organization performingOrganization) 
	{	Organization org = null;
		if (getPerformer().hasOrganization()) {
			org = ParserUtils.getResource(Organization.class, performer.getOrganization());
			if (performingOrganization.hasName()) {
				org.setName(performingOrganization.getName());
			}
			if (performingOrganization.hasIdentifier()) {
				for (Identifier ident: performingOrganization.getIdentifier()) {
					org.addIdentifier(ident);
				}
			}
		} else {
			addResource(performingOrganization);
			org = performingOrganization;
			performer.setOrganization(ParserUtils.toReference(org, performer, PERFORMER));
		}	}
	
	/**
	 * Set the address of the performing organization
	 * @param performingOrganizationAddress the address of the performing organization
	 */
	@ComesFrom(path = "Observation.performer.Organization.address", field = 24, comment = "Performing Organization Address")
	public void setPerformingOrganizationAddress(Address performingOrganizationAddress) {	
		Organization org = null;
		if (!getPerformer().hasOrganization()) {
			org = createResource(Organization.class);
			performer.setOrganization(ParserUtils.toReference(org, performer, PERFORMER));
		} else {
			org = ParserUtils.getResource(Organization.class, performer.getOrganization());
		}
		org.addAddress(performingOrganizationAddress);
	}
	
	/**
	 * Create a performer identifying the medical director of the performing organization
	 * @param performingOrganizationMedicalDirector the medical director 
	 */
	@ComesFrom(path = "Observation.performer.PractitionerRole.practitioner", field = 25, comment = "Performing Organization Medical Director")
	public void setPerformingOrganizationMedicalDirector(Practitioner performingOrganizationMedicalDirector) {		// NOTE: The performingOrganizationMedicalDirector is a separate performer from the default performer
		// returned by getPerformer.
		getPerformer();
		if (performer.hasPractitioner()) {
			// If there is already a practitioner in the performer, add a new one.
			performer = createResource(PractitionerRole.class);
			observation.addPerformer(ParserUtils.toReference(performer, observation, PERFORMER));
		}
		addResource(performingOrganizationMedicalDirector);
		performer.addCode(Codes.MEDICAL_DIRECTOR_ROLE_CODE);
		performer.setPractitioner(ParserUtils.toReference(performingOrganizationMedicalDirector, performer, PERFORMER));
	}

	/**
	 * Set the specific identifier
	 * NOTE: Not present until HL7 2.9, and not widely in use
	 * @param specimenIdentifier the specific identifier
	 */
	@ComesFrom(path = "Observation.specimen.Specimen.identifier", field = 33, comment = "Observation Related Specimen Identifier")
	public void setSpecimenIdentifier(Identifier specimenIdentifier) {
		// A degenerate reference instead of one of the usual ones.  The SPM segment if present
		// will create the Specimen resource rather than creating it here.
		observation.setSpecimen(new Reference().setIdentifier(specimenIdentifier));
	}
}
