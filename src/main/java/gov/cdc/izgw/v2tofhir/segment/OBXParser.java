package gov.cdc.izgw.v2tofhir.segment;

import java.util.List;

import java.util.ArrayList;

import org.hl7.fhir.instance.model.api.IBase;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Address;
import org.hl7.fhir.r4.model.BaseDateTimeType;
import org.hl7.fhir.r4.model.CodeType;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.ContactPoint;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Device;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.ImmunizationRecommendation;
import org.hl7.fhir.r4.model.ImmunizationRecommendation.ImmunizationRecommendationRecommendationComponent;
import org.hl7.fhir.r4.model.ImmunizationRecommendation.ImmunizationRecommendationRecommendationDateCriterionComponent;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Immunization;
import org.hl7.fhir.r4.model.Immunization.ImmunizationEducationComponent;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Observation.ObservationStatus;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.PositiveIntType;
import org.hl7.fhir.r4.model.Practitioner;
import org.hl7.fhir.r4.model.PractitionerRole;
import org.hl7.fhir.r4.model.PrimitiveType;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.RelatedPerson;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.TimeType;

import ca.uhn.hl7v2.model.Type;
import gov.cdc.izgw.v2tofhir.annotation.ComesFrom;
import gov.cdc.izgw.v2tofhir.annotation.Produces;
import gov.cdc.izgw.v2tofhir.converter.DatatypeConverter;
import gov.cdc.izgw.v2tofhir.converter.MessageParser;
import gov.cdc.izgw.v2tofhir.utils.Codes;
import gov.cdc.izgw.v2tofhir.utils.ParserUtils;
import gov.cdc.izgw.v2tofhir.utils.Systems;

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
	private static List<FieldHandler> fieldHandlers = new ArrayList<>();
	private IzDetail izDetail;
	private VisCode redirect = null;
	private ImmunizationRecommendationRecommendationComponent recommendation;
	private String type = null;
	private Observation observation = null;
	private PractitionerRole performer;
	
	/**
	 * Create an RXA Parser for the specified MessageParser
	 * @param p	The message parser.
	 */
	public OBXParser(MessageParser p) {
		super(p, "OBX");
		if (fieldHandlers.isEmpty()) {
			initFieldHandlers(this, fieldHandlers);
		}
	}

	@Override
	protected List<FieldHandler> getFieldHandlers() {
		return fieldHandlers;
	}

	@Override
	public IBaseResource setup() {
		izDetail = IzDetail.get(this);
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
	@ComesFrom(path = "Observation.value", field = 5, comment = "Observation Value")
	public void setValue(Type v2Type) {
		v2Type = DatatypeConverter.adjustIfVaries(v2Type);
		if (type == null) {
			warn("Type is unknown for OBX-5");
			return;
		}
		Class<? extends IBase> target = null;
		switch (type) {
		case "AD":	target = Address.class; break;	//	Address	
		case "CF":	target = CodeableConcept.class; break;	//	Coded Element With Formatted Values	
		case "CK":	target = Identifier.class; break;	//	Composite ID With Check Digit	
		case "CN":	target = Address.class; break;	//	Composite ID And Name	
		case "CNE":	target = CodeableConcept.class; break;	//	Coded with No Exceptions	
		case "CWE":	target = CodeableConcept.class; break;	//	Coded Entry	
		case "CX":	target = Identifier.class; break;	//	Extended Composite ID With Check Digit	
		case "DT":	target = DateTimeType.class; break;	//	Observation doesn't like DateType	
		case "DTM":	target = DateTimeType.class; break;	//	Time Stamp (Date & Time)	
		case "FT":	target = StringType.class; break; //	Formatted Text (Display)	
		case "ID":	target = CodeType.class; break; //	Coded Value for HL7 Defined Tables	
		case "IS":	target = CodeType.class; break; //	Coded Value for User-Defined Tables	
		case "NM":	target = Quantity.class; break; //	Numeric	(Observation only accepts Quantity)
		case "PN":	target = HumanName.class; break; //	Person Name	
		case "ST":	target = StringType.class; break; //	String Data.	
		case "TM":	target = TimeType.class; break; //	Time	
		case "TN":	target = ContactPoint.class; break; //	Telephone Number	
		case "TS":	target = DateTimeType.class; break; //	TimeStamp	 (OBX requires DateTimeType)
		case "TX":	target = StringType.class; break; //	Text Data (Display)	
		case "XAD":	target = Address.class; break; //	Extended Address	
		case "XCN":	target = RelatedPerson.class; break; //	Extended Composite Name And Number For Persons	
		case "XON":	target = Organization.class; break; //	Extended Composite Name And Number For Organizations	
		case "XPN":	target = HumanName.class; break; //	Extended Person Name	
		case "XTN":	target = ContactPoint.class; break; //	Extended Telecommunications Number
		case "CP",	//	Composite Price	
			 "DR",	//	Date/Time Range	
			 "ED",	//	Encapsulated Data	
			 "MA",	//	Multiplexed Array	
			 "MO",	//	Money	
			 "NA",	//	Numeric Array	
			 "RP",	//	Reference Pointer	
			 "SN":	//	Structured Numeric
		default:
			break;
		}
		
		if (target == null) {
			warn("Cannot convert V2 {}", type);
			return;
		}
		
		IBase converted = DatatypeConverter.convert(target, v2Type, null);
		if (converted instanceof DomainResource dr) {
			Reference r = ParserUtils.toReference(dr);
			addResource(dr);
			observation.setValue(r);
		} else if (converted instanceof org.hl7.fhir.r4.model.Type t){
			observation.setValue(t);
		}
		
		if (redirect == null || VisCode.OTHER.equals(redirect)) {
			return;
		}
		
		if (izDetail.hasImmunization()) {
			handleVisObservations(converted);
		} else if (izDetail.hasRecommendation()) {
			handleRecommendationObservations(converted);
		}
	}

	private void handleVisObservations(IBase converted) {
		if (VisCode.VACCINE_ELIGIBILITY_CODE.equals(redirect)) {
			izDetail.immunization.addProgramEligibility((CodeableConcept) converted);
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
			education.setDocumentType(((PrimitiveType<?>)converted).asStringValue());
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
	
	
	/*
	7	OBX-7	References Range	ST	0	1				Observation.referenceRange.text		Observation.string	0	-1				If OBX-7 is sufficiently parseable, then the low, high, type, appliesTo, and/or age may be used.
	8	OBX-8	Interpretation Codes	CWE	0	-1				Observation.interpretation		Observation.CodeableConcept	0	-1	CWE[CodeableConcept]	InterpretationCode		
	9	OBX-9	Probability	NM	0	1												
	10	OBX-10	Nature of Abnormal Test	ID	0	-1				Observation.extension.uri		Observation.uri	0	-1			"http://hl7.org/fhir/StructureDefinition/observation-nature-of-abnormal-test"	
	10	OBX-10	Nature of Abnormal Test	ID	0	-1				Observation.extension.valueCodeableConcept		Observation.CodeableConcept	0	-1	CWE[CodeableConcept]	NatureOfAbnormalTesting		
	11	OBX-11	Observation Result Status	ID	1	1				Observation.status		Observation.code	1	1		ObservationStatus		
	11	OBX-11	Observation Result Status	ID	1	1	IF OBX-11 IS "X"			Observation.dataAbsentReason.coding.code		Observation.CodeableConcept					/cannot-be-obtained/	Needs to be requested
	11	OBX-11	Observation Result Status	ID	1	1	IF OBX-11 IS "X"			Observation.dataAbsentReason.coding.system		Observation.uri					"http://terminology.hl7.org/CodeSystem/data-absent-reason"	
	11	OBX-11	Observation Result Status	ID	1	1	IF OBX-11 IS "N"			Observation.dataAbsentReason.coding.code							"not-asked"	
	11	OBX-11	Observation Result Status	ID	1	1	IF OBX-11 IS "N"			Observation.dataAbsentReason.coding.system							"http://terminology.hl7.org/CodeSystem/data-absent-reason"	
	12	OBX-12	Effective Date of Reference Range	DTM	0	1												
	13	OBX-13	User Defined Access Checks	ST	0	1												
	14	OBX-14	Date/Time of the Observation	DTM	0	1				Observation.effectiveDateTime		Observation.dateTime	0	1				
	15	OBX-15	Producer's ID	CWE	0	1				Observation.performer(Observation.Organization)		Observation.identifier	0	1	CWE[Organization]			We are mapping this to an identifier considering the definition (used for a unique identifier of the producer), although that does not fit the use of the CWE data type in HL7 v2. Depending on context (e.g., US CLIA or IHE LTW, or ILW) this may reflect a location (US CLIA) or an organization (IHE LTW or ILW).
	15	OBX-15	Producer's ID	CWE	0	1				Observation.performer(Observation.PractitionerRole)								
	16	OBX-16	Responsible Observer	XCN	0	-1				Observation.performer(Observation.PractitionerRole.practitioner(Observation.Practitioner)		Reference(Observation.PractitionerRole)	0	-1	XCN[Practitioner]			
	16	OBX-16	Responsible Observer	XCN	0	-1				Observation.performer(Observation.PractitionerRole.code.coding.code)		Observation.CodeableConcept	0	-1			"responsibleObserver"	
	16	OBX-16	Responsible Observer	XCN	0	-1				Observation.performer(Observation.PractitionerRole.code.coding.system)		Observation.CodeableConcept	0	-1			"http://terminology.hl7.org/CodeSystem/practitioner-role"	
	17	OBX-17	Observation Method	CWE	0	-1				Observation.method		Observation.CodeableConcept	0	1	CWE[CodeableConcept]			The cardinality of Observation.method is 0..1 while the source allows for multiple methods. As we are not aware of anybody populating multiples in HL7 v2, we did not provide further mapping guidance. If you need to support multiples, please submit a gForge to OO for the HL7 v2 to FHIR mapping Implementation Guide.
	18	OBX-18	Equipment Instance Identifier	EI	0	-1				Observation.device(Observation.Device.identifier)		Observation.Identifier	0	1	EI[Identifier-Extension]			
	19	OBX-19	Date/Time of the Analysis	DTM	0	1				Observation.extension.url		Observation.uri	0	1				"http://hl7.org/fhir/StructureDefinition/observation-analysis-date-time"
	19	OBX-19	Date/Time of the Analysis	DTM	0	1				Observation.extension.valueDateTime		Observation.dateTime	0	1				
	20	OBX-20	Observation Site	CWE	0	-1				Observation.bodySite		Observation.CodeableConcept	0	1	CWE[CodeableConcept]			The cardinality of Observation.bodySite is 0..1 while the source allows for multiple body sites. As we are not aware of anybody populating multiples in HL7 v2, we did not provide further mapping guidance. If you need to support multiples, please submit a gForge to OO for the HL7 v2 to FHIR mapping Implementation Guide.
	21	OBX-21	Observation Instance Identifier	EI	0	1				Observation.identifier		Observation.Identifier	0	-1	EI[Identifier-Extension]			
	21	OBX-21	Observation Instance Identifier	EI	0	1				Observation.identifier.type.coding.code		Observation.code	0	1			"FILL"	
	22	OBX-22	Mood Code	CNE	0	1												
	23	OBX-23	Performing Organization Name	XON	0	1	IF OBX-25 NOT VALUED			Observation.performer(Observation.Organization)		Reference(Observation.Organization)	0	-1	XON[Organization]			
	23	OBX-23	Performing Organization Name	XON	0	1	IF OBX-25 VALUED			Observation.performer(Observation.PractitionerRole.organization(Observation.Organization)		Reference(Observation.Organization)	0	-1	XON[Organization]			
	24	OBX-24	Performing Organization Address	XAD	0	1	IF OBX-25 NOT VALUED			Observation.performer(Observation.Organization.address)		Observation.Address	0	-1	XAD[Address]			
	24	OBX-24	Performing Organization Address	XAD	0	1	IF OBX-25 VALUED			Observation.performer(Observation.PractitionerRole.organization(Observation.Organization.address)		Observation.Address	0	-1	XAD[Address]			
	25	OBX-25	Performing Organization Medical Director	XCN	0	1				Observation.performer(Observation.PractitionerRole.practitioner)		Reference(Observation.PractitionerRole)	0	1	XCN[PractitionerRole]			
	25	OBX-25	Performing Organization Medical Director	XCN	0	1				Observation.performer(Observation.PractitionerRole.code.coding.code)		Observation.code					"MDIR"	
	25	OBX-25	Performing Organization Medical Director	XCN	0	1				Observation.performer(Observation.PractitionerRole.code.coding.system)							"http://terminology.hl7.org/CodeSystem/v2-0912"	
	26	OBX-26	Patient Results Release Category	ID	0	1												
	27	OBX-27	Root Cause	CWE	0	1												
	28	OBX-28	Local Process Control	CWE	0	-1												
	29	OBX-29	Observation Type	ID	0	1												
	30	OBX-30	Observation Sub-Type	ID	0	1					extension??-subType	Observation.code	0	1				The sub type was necessary in v2 to distinguish purpose of the observation when it appears in a message in the same group (e.g., answers to ask at order entry questions with actual results ). Within FHIR flagging the observation may not be necessary, but that is not yet clear. Until then, we will keep the thought of needing an extension, but not create it yet.
	31	OBX-31	Action Code	ID	0	1												
	32	OBX-32	Observation Value Absent Reason	CWE	0	-1												
	33	OBX-33	Observation Related Specimen Identifier	EIP	0	-1	IF OBX-33 COUNT>1			Observation.extension.uri		Reference	0	1			"http://hl7.org/fhir/5.0/StructureDefinition/extension-Observation.specimen	Note that in v2 messages the observations that a calculated observation is derived from on and involve multiple specimens are typically not included with the message. To enable relating the calculated observation to the correct specimens it relates to, the v2 message should include the originating observations that in turn use OBX-33 to link to the correct specimens. Without that, and if there are multiple specimens in OBX-33, there is no standard method to correctly associate the observation with the correct specimens. The implementer will have to devise an appropriate method for that in their context.
	33	OBX-33	Observation Related Specimen Identifier	EIP	0	-1	IF OBX-33 COUNT>1			Observation.extension.valueReference(Observation.Group.member.entity(Observation.Specimen.identifier)			0	1				
	33	OBX-33	Observation Related Specimen Identifier	EIP	0	-1	IF OBX-33 COUNT>1			Observation.extension.uri			0	1			"http://hl7.org/fhir/5.0/StructureDefinition/extension-Observation.specimen	
	33	OBX-33	Observation Related Specimen Identifier	EIP	0	-1	IF OBX-33 COUNT>1			Observation.extension.valueReference(Observation.Group.member.entity(Observation.Specimen.identifier)			0	1				
	33	OBX-33	Observation Related Specimen Identifier	EIP	0	-1	IF OBX-33 COUNT=1			Observation.specimen(Observation.Specimen.identifier)		Observation.Identifier			EIP[Identifier-PlacerAssignedIdentifier]			
	33	OBX-33	Observation Related Specimen Identifier	EIP	0	-1	IF OBX-33 COUNT=1			Observation.specimen(Observation.Specimen.identifier)		Observation.Identifier			EIP[Identifier-FillerAssignedIdentifier]	
	*/
	
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
	@ComesFrom(path = "Observation.interpretation", field = 8, comment = "Interpretation Code")	
	public void setInterpretationCodes(CodeableConcept interpretationCode) {
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
	@ComesFrom(path = "Observation.performer(Observation.Organization)", field = 15, comment = "Producer's ID")
	public void setProducersID(Identifier producersId) 
	{
		Organization producer = createResource(Organization.class);
		producer.addIdentifier(producersId);
		PractitionerRole pr = getPerformer();
		pr.setOrganization(ParserUtils.toReference(producer));
		observation.addPerformer(ParserUtils.toReference(producer));	}
	
	/**
	 * Get or create if necessary the performer of the observation.
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
		}
		return performer;
	}

	/**
	 * Set the responsible observer
	 * @param responsibleObserver the responsible observer
	 */
	@ComesFrom(path = "Observation.performer(Observation.PractitionerRole.practitioner(Observation.Practitioner)", field = 16, comment = "Responsible Observer")
	public void setResponsibleObserver(Practitioner responsibleObserver) 
	{
		addResource(responsibleObserver);		PractitionerRole pr = getPerformer();
		pr.setPractitioner(ParserUtils.toReference(responsibleObserver));
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
	@ComesFrom(path = "Observation.device(Observation.Device.identifier)", field = 18, comment = "Equipment Instance Identifier")
	public void setEquipmentInstanceIdentifier(Identifier equipmentInstanceIdentifier) 
	{
		Device device = createResource(Device.class);
		device.addIdentifier(equipmentInstanceIdentifier);
		observation.setDevice(ParserUtils.toReference(device));
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
	@ComesFrom(path = "Observation.performer(Observation.Organization)", field = 23, comment = "Performing Organization Name")
	public void setPerformingOrganizationName(Organization performingOrganization) 
	{	Organization org = null;
		if (getPerformer().hasOrganization()) {
			org = (Organization) performer.getOrganization().getUserData("Resource");
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
			performer.setOrganization(ParserUtils.toReference(org));
		}	}
	
	/**
	 * Set the address of the performing organization
	 * @param performingOrganizationAddress the address of the performing organization
	 */
	@ComesFrom(path = "Observation.performer(Observation.Organization.address)", field = 24, comment = "Performing Organization Address")
	public void setPerformingOrganizationAddress(Address performingOrganizationAddress) {	
		Organization org = null;
		if (!getPerformer().hasOrganization()) {
			org = createResource(Organization.class);
			performer.setOrganization(ParserUtils.toReference(org));
		} else {
			org = (Organization) performer.getOrganization().getUserData("Resource");
		}
		org.addAddress(performingOrganizationAddress);
	}
	
	/**
	 * Create a performer identifying the medical director of the performing organization
	 * @param performingOrganizationMedicalDirector the medical director 
	 */
	@ComesFrom(path = "Observation.performer(Observation.PractitionerRole.practitioner)", field = 25, comment = "Performing Organization Medical Director")
	public void setPerformingOrganizationMedicalDirector(Practitioner performingOrganizationMedicalDirector) {		// NOTE: The performingOrganizationMedicalDirector is a separate performer from the default performer
		// returned by getPerformer.
		PractitionerRole pr = createResource(PractitionerRole.class);
		addResource(performingOrganizationMedicalDirector);
		pr.setPractitioner(ParserUtils.toReference(performingOrganizationMedicalDirector));
		pr.addCode(Codes.MEDICAL_DIRECTOR_ROLE_CODE);
	}

	/**
	 * Set the specific identifier
	 * NOTE: Not present until HL7 2.9, and not widely in use
	 * @param specimenIdentifier the specific identifier
	 */
	@ComesFrom(path = "Observation.specimen(Observation.Specimen.identifier)", field = 33, comment = "Observation Related Specimen Identifier")
	public void setSpecimenIdentifier(Identifier specimenIdentifier) {
		// A degenerate reference instead of one of the usual ones.  The SPM segment if present
		// will create the Specimen resource rather than creating it here.
		observation.setSpecimen(new Reference().setIdentifier(specimenIdentifier));
	}
		

}
