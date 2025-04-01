package gov.cdc.izgw.v2tofhir.segment;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Address;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.DecimalType;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Immunization;
import org.hl7.fhir.r4.model.Immunization.ImmunizationPerformerComponent;
import org.hl7.fhir.r4.model.Immunization.ImmunizationStatus;
import org.hl7.fhir.r4.model.ImmunizationRecommendation.ImmunizationRecommendationRecommendationComponent;
import org.hl7.fhir.r4.model.Location;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Practitioner;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.StringType;

import gov.cdc.izgw.v2tofhir.annotation.ComesFrom;
import gov.cdc.izgw.v2tofhir.annotation.Produces;
import gov.cdc.izgw.v2tofhir.converter.DatatypeConverter;
import gov.cdc.izgw.v2tofhir.converter.MessageParser;
import gov.cdc.izgw.v2tofhir.utils.Codes;
import gov.cdc.izgw.v2tofhir.utils.Mapping;
import gov.cdc.izgw.v2tofhir.utils.ParserUtils;
import gov.cdc.izgw.v2tofhir.utils.Systems;

/**
 * RXAParser parses any RXA segments in a message to an Immunization if the message is a VXU or an 
 * response to an immunization query.
 * 
 * @author Audacious Inquiry
 *
 * @see <a href="https://hl7.org/fhir/uv/v2mappings/2024Jan/ConceptMap-segment-rxa-to-immunization.html">V2 to FHIR: RXA to Immunization</a>
 */
@Produces(segment = "RXA", resource=Immunization.class, 
	extra = { Practitioner.class, Organization.class }
)
public class RXAParser extends AbstractSegmentParser {
	private static List<FieldHandler> fieldHandlers = new ArrayList<>();
	private IzDetail izDetail;
	private ImmunizationRecommendationRecommendationComponent recommendation;
	
	/**
	 * Create an RXA Parser for the specified MessageParser
	 * @param p	The message parser.
	 */
	public RXAParser(MessageParser p) {
		super(p, "RXA");
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
		izDetail.initializeResources(false, getSegment());
		if (izDetail.hasImmunization()) {
			return izDetail.immunization;
		} else if (izDetail.hasRecommendation()) {
			recommendation = izDetail.immunizationRecommendation.addRecommendation();
			return izDetail.immunizationRecommendation;
		}
		// Until we support RXA more generally, we don't know what to do with this segment 
		return null;
	}
	
	/*
	3	RXA-3	Date/Time Start of Administration	DTM	1	1				Immunization.occurrenceDateTime		Immunization.dateTime	1	1				
	*/
	/**
	 * Set the administration date time.
	 * @param administrationDateTime the administration date time	
	 */
	@ComesFrom(path = "Immunization.occurrenceDateTime", field = 3, comment = "Date/Time Start of Administration")
	public void setAdministrationDateTime(DateTimeType administrationDateTime) {
		if (izDetail.hasImmunization()) {
			izDetail.immunization.setOccurrence(administrationDateTime);
		} else if (izDetail.hasRecommendation()) {
			recommendation.getDateCriterionFirstRep().setValueElement(administrationDateTime);
		}
	}
	/*
	5	RXA-5	Administered Code	CWE	1	1				Immunization.vaccineCode		Immunization.CodeableConcept	1	1	CWE[CodeableConcept]			
	*/
	/**
	 * Set the vaccine code
	 * @param vaccineCode	the vaccine code
	 */
	@ComesFrom(path = "Immunization.vaccineCode", field = 5, table = "0292", comment = "Administered Code")
	public void setVaccineCode(CodeableConcept vaccineCode) {
		// CDC says RXA-5 cannot repeat, but STC example shows a repeat, handle the
		// repeat as additional codes.
		if (izDetail.hasImmunization()) {
			if (izDetail.immunization.hasVaccineCode()) {
				for (Coding coding: vaccineCode.getCoding()) {
					izDetail.immunization.getVaccineCode().addCoding(coding);
				}
			} else {
				izDetail.immunization.setVaccineCode(vaccineCode);
			}
		} else if (izDetail.hasRecommendation()) {
			if (recommendation.hasVaccineCode()) {
				for (Coding coding: vaccineCode.getCoding()) {
					recommendation.getVaccineCodeFirstRep().addCoding(coding);
				}
			} else {
				recommendation.addVaccineCode(vaccineCode);
			}
		}
	}
	/*
	6	RXA-6	Administered Amount	NM	1	1				Immunization.doseQuantity.value		Immunization.decimal	0	1				
	*/
	/**
	 * Set the dose amount
	 * @param doseQuantity the dose amount
	 */
	@ComesFrom(path = "Immunization.doseQuantity.value", field = 6, comment = "Administered Amount")
	public void setDoseAmount(DecimalType doseQuantity) {
		if (izDetail.hasImmunization()) {
			izDetail.immunization.getDoseQuantity().setValueElement(doseQuantity);
		}
	}
	/*
	7	RXA-7	Administered Units	CWE	0	1				Immunization.doseQuantity		Immunization.SimpleQuantity	0	1	CWE[Quantity]			
	*/
	/**
	 * Set the dose units
	 * @param doseUnits the dose units
	 */
	@ComesFrom(path = "Immunization.doseQuantity.code", field = 7, comment = "Administered Units",
			also = "Immunization.doseQuantity.unit")
	public void setDoseAmount(CodeableConcept doseUnits) {
		if (izDetail.hasImmunization()) {
			DatatypeConverter.setUnits(izDetail.immunization.getDoseQuantity(), doseUnits);
		}
	}
	
	/*
	10	RXA-10	Administering Provider	XCN	0	-1				Immunization.performer.actor(Immunization.Practitioner)		Reference(Immunization.Practitioner)	0	-1	XCN[Practitioner]			
	*/
	/*
	10	RXA-10	Administering Provider	XCN	0	-1				Immunization.performer.function.coding.code		Immunization.code					"AP"	
	*/
	/*
	10	RXA-10	Administering Provider	XCN	0	-1				Immunization.performer.function.coding.system		Immunization.uri					"http://terminology.hl7.org/CodeSystem/v2-0443"	
	*/
	// CVX 48, 49, MVX MSD
	/**
	 * Set the administering provider
	 * @param adminProvider the administering provider
	 */
	@ComesFrom(path = "Immunization.performer.actor.Practitioner", field = 10, comment = "Administering Provider")
	public void setAdministeringProvider(Practitioner adminProvider) {
		if (izDetail.hasImmunization()) {
			addResource(adminProvider);
			Reference ref = ParserUtils.toReference(adminProvider, izDetail.immunization, "performer", "practitioner");
			ImmunizationPerformerComponent perf = izDetail.immunization.addPerformer().setActor(ref);
			perf.setFunction(Codes.ADMIN_PROVIDER_FUNCTION_CODE);
		}
	}

	/*
	11	RXA-11	Administered-at Location	LA2	0	1												
	27	RXA-27	Administer-at	PL	0	1				Immunization.location(Immunization.Location)		Reference(Immunization.Location)	0	1	PL[Location]			
	*/
	/**
	 * Set the administered at location
	 * @param administerAt the administered at location
	 */
	@ComesFrom(path = "Immunization.location", field = 11, comment = "Administered-at Location")
	@ComesFrom(path = "Immunization.location", field = 27, comment = "Administer-at")
	public void setAdministerAt(Location administerAt) {
		if (izDetail.hasImmunization()) {
			// We don't need to check for location already being created.
			// A use of RXA-27 will likely not use, or just duplicate value in RXA-11
			// since few HL7 V2 versions have both (RXA-11 withdrawl started 
			// when RXA-27 introduced in V2.6).
			addResource(administerAt);
			izDetail.immunization.setLocation(ParserUtils.toReference(administerAt, izDetail.immunization, "location"));
		}
	}

	/*
	28	RXA-28	Administered-at Address	XAD	0	1				Immunization.location(Immunization.Location.address)		Immunization.Address	0	1	XAD[Address]			
	*/
	/**
	 * Set the administered at location address
	 * @param administerAtAddress the administered at location address
	 */
	@ComesFrom(path = "Immunization.location.Location.address", field = 28, comment = "Administered-at Address")
	public void setAdministerAtAddress(Address administerAtAddress) {
		if (izDetail.hasImmunization()) {
			Reference locRef = izDetail.immunization.getLocation();
			Location location = null;
			if (locRef == null) {
				location = createResource(Location.class);
				izDetail.immunization.setLocation(ParserUtils.toReference(location, izDetail.immunization, "location"));
			} else {
				location = ParserUtils.getResource(Location.class, locRef);
			}
			location.setAddress(administerAtAddress);
		}
	}

	/*
	15	RXA-15	Substance Lot Number	ST	0	-1				Immunization.lotNumber		Immunization.string	0	1				
	*/
	/**
	 * Set the lot number
	 * @param lotNumber lot number
	 */
	@ComesFrom(path = "Immunization.lotNumber", field = 15, comment = "Substance Lot Number")
	public void setLotNumber(StringType lotNumber) {
		if (izDetail.hasImmunization()) {
			izDetail.immunization.setLotNumberElement(lotNumber);
		}
	}
	
	/*
	16	RXA-16	Substance Expiration Date	DTM	0	-1				Immunization.expirationDate		Immunization.date	0	1				
	*/
	/**
	 * Set the expiration date
	 * @param expiration the expiration date 
	 */
	@ComesFrom(path = "Immunization.expirationDate", field = 16, comment = "Substance Expiration Date")
	public void setExpirationDate(DateType expiration) {
		if (izDetail.hasImmunization()) {
			izDetail.immunization.setExpirationDateElement(expiration);
		}
	}
	
	/*
	17	RXA-17	Substance Manufacturer Name	CWE	0	-1				Immunization.manufacturer(Immunization.Organization)		Reference(Immunization.Organization)	0	1	CWE[Organization]			
	*/
	/**
	 * Set the manufacturing organization from the MVX or Table 0227 code
	 * @param manufacturer	The manufacturing organization
	 */
	@ComesFrom(path = "Immunization.manufacturer.Organization", field = 17, table = "0227", comment = "Substance Manufacturer Name")
	public void setManufacturer(CodeableConcept manufacturer) {
		if (izDetail.hasImmunization()) {
			Organization mOrg = new Organization();
			// Convert a code to an Organization name and identifier
			for (Coding coding: manufacturer.getCoding()) {
				String system = coding.getSystem();
				if (Systems.MVX.equals(system) || Mapping.v2Table("0227").equals(system)) {
					if (coding.hasDisplay()) {
						mOrg.setName(coding.getDisplay());
					}
					mOrg.addIdentifier(new Identifier().setSystem(system).setValue(coding.getCode()));
				}
			}
			if (mOrg.hasName() || mOrg.hasIdentifier()) {
				addResource(mOrg);
			}
			izDetail.immunization.setManufacturer(ParserUtils.toReference(mOrg, izDetail.immunization, "manufacturer"));
		}
	}
	
	/*
	18	RXA-18	Substance/Treatment Refusal Reason	CWE	0	-1				Immunization.statusReason		Immunization.CodeableConcept	0	1	CWE[CodeableConcept]			
	*/
	/**
	 * Set the reason for refusal
	 * @param substanceTreatmentRefusalReason the reason for refusal
	 */
	@ComesFrom(path = "Immunization.statusReason", field = 18, comment = "Substance/Treatment Refusal Reason")
	public void setSubstanceTreatmentRefusalReason(CodeableConcept substanceTreatmentRefusalReason) {
		if (izDetail.hasImmunization()) {
			izDetail.immunization.setStatus(ImmunizationStatus.NOTDONE);
			izDetail.immunization.setStatusReason(substanceTreatmentRefusalReason);
		}
	}
	
	/*
	19	RXA-19	Indication	CWE	0	-1				Immunization.reasonCode		Immunization.CodeableConcept	0	-1	CWE[CodeableConcept]			
	*/
	/**
	 * Set the indication for vaccination
	 * @param indication the indication for vaccination
	 */
	@ComesFrom(path = "Immunization.reasonCode", field = 19, comment = "Indication")
	public void setIndication(CodeableConcept indication) {
		if (izDetail.hasImmunization()) {
			izDetail.immunization.addReasonCode(indication);
		} else if (izDetail.hasRecommendation()) {
			recommendation.addForecastReason(indication);
		}
	}

	/*
	20	RXA-20	Completion Status	ID	0	1	IF RXA-21 NOT EQUALS "D"			Immunization.status		Immunization.code	1	1		CompletionStatus		
	20	RXA-20	Completion Status	ID	0	1	IF NOT VALUED AND RXA-21 NOT EQUALS "D"			Immunization.status		Immunization.code	1	1			"completed"	
	21	RXA-21	Action Code – RXA	ID	0	1	IF RXA-21 EQUALS "D"			Immunization.status		Immunization.code	1	1			"entered-in-error"	
	*/
	/**
	 * Set the completion status
	 * @param completionStatus the completion status
	 */
	@ComesFrom(path = "Immunization.status", field = 20, table = "0322", map = "ImmunizationStatus", comment = "Completion Status")
	@ComesFrom(path = "Immunization.status", field = 21, table = "0323", map = "ImmunizationStatus", comment = "Action Code – RXA")
	public void setCompletionStatus(Coding completionStatus) {
		if (izDetail.hasImmunization()) {
			if (!completionStatus.hasCode()) {
				return;
			}
			String value = StringUtils.right(completionStatus.getSystem(), 4) + "|" + completionStatus.getCode();
			ImmunizationStatus code = null;
			switch (value) {
			case "0322|CP", "0322|PA", "0323|A", "0323|U":
				code = ImmunizationStatus.COMPLETED;
				break;
			case "0322|NA", "0322|RE", "0323|D":
				code = ImmunizationStatus.NOTDONE;
				break;
			default:
				break;
			}
			// RXA-22 field in can be D, which is a delete (entered-in-error), 
			// that overrides first, so we don't check for conflicts
			izDetail.immunization.setStatus(code);
		}
	}

	/*
	22	RXA-22	System Entry Date/Time	DTM	0	1	IF RXA-21 EQUALS "A"			Immunization.recorded		Immunization.dateTime	0	1				
	*/
	/**
	 * Set the recorded date time
	 * @param systemEntryDateTime the recorded date time
	 */
	@ComesFrom(path = "Immunization.recorded", field = 22, comment = "System Entry Date/Time")
	public void setSystemEntryDateTime(DateTimeType systemEntryDateTime) {
		if (izDetail.hasImmunization()) {
			izDetail.immunization.setRecordedElement(systemEntryDateTime);
		} else if (izDetail.hasRecommendation()) {
			izDetail.immunizationRecommendation.setDateElement(systemEntryDateTime);
		}
	}
}
