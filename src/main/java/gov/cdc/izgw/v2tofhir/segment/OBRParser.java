package gov.cdc.izgw.v2tofhir.segment;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.CodeType;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.ContactPoint;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Immunization;
import org.hl7.fhir.r4.model.InstantType;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Period;
import org.hl7.fhir.r4.model.Practitioner;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.ServiceRequest;
import org.hl7.fhir.r4.model.ServiceRequest.ServiceRequestPriority;
import gov.cdc.izgw.v2tofhir.annotation.ComesFrom;
import gov.cdc.izgw.v2tofhir.annotation.Produces;
import gov.cdc.izgw.v2tofhir.converter.MessageParser;
import gov.cdc.izgw.v2tofhir.utils.Codes;
import gov.cdc.izgw.v2tofhir.utils.ParserUtils;

/**
 * ORCParser parses any ORC segments in a message to a ServiceRequest If the
 * message is a VXU or an response to an immunization query, it also produces an
 * Immunization resource.
 * 
 * @author Audacious Inquiry
 *
 * @see <a href=
 *      "https://hl7.org/fhir/uv/v2mappings/2024Jan/ConceptMap-segment-orc-to-servicerequest.html">V2
 *      to FHIR: ORC to ServiceRequest</a>
 * @see <a href=
 *      "https://hl7.org/fhir/uv/v2mappings/2024Jan/ConceptMap-segment-orc-to-immunization.html">V2
 *      to FHIR: ORC to Immunization</a>
 */
@Produces(segment = "ORC", resource = ServiceRequest.class, extra = { Practitioner.class, Organization.class,
		Immunization.class })
public class OBRParser extends AbstractSegmentParser {
	private static final String REQUESTER = "requester";
	private static List<FieldHandler> fieldHandlers = new ArrayList<>();
	private ServiceRequest order;
	private Practitioner orderingProvider;
	/**
	 * Create an ORC Parser for the specified MessageParser
	 * 
	 * @param p The message parser.
	 */
	public OBRParser(MessageParser p) {
		super(p, "OBR");
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
		order = this.getLastResource(ServiceRequest.class);
		if (order == null) {
			order = createResource(ServiceRequest.class);
		}
		return order;
	}

	/**
	 * Add the place order number for the service request
	 * @param orderNumber	the order number 
	 */
	@ComesFrom(path = "ServiceRequest.identifier", field = 2, comment = "Placer Order Number")
	@ComesFrom(path = "ServiceRequest.identifier", field = 53, comment = "Alternate Placer Order Number")
	public void addPlacerOrderNumber(Identifier orderNumber) {
		addOrderIdentifier(orderNumber, Codes.PLACER_ORDER_IDENTIFIER_TYPE);
	}
	
	/**
	 * Add the filler order number for the service request
	 * @param orderNumber	the order number 
	 */
	@ComesFrom(path = "ServiceRequest.identifier", field = 3, comment = "Filler Order Number")
	public void addFillerOrderNumber(Identifier orderNumber) {
		addOrderIdentifier(orderNumber, Codes.FILLER_ORDER_IDENTIFIER_TYPE);
	}
	
	private void addOrderIdentifier(Identifier ident, CodeableConcept type) {
		// It already exists, don't add it again.
		if (order.getIdentifier().stream().anyMatch(id -> equals(id, ident))) {
			return;
		}
		// Ensures ident above is effectively final
		Identifier ident2 = ident;
		if (ident.getType() != type && ident.hasType()) {
			order.addIdentifier(ident);
			ident2 = ident.copy();
		}
		if (type != null && !type.isEmpty()) {
			ident2.setType(type);
			order.addIdentifier(ident2);
		}
	}
	
	private boolean equals(Identifier id1, Identifier id2) {
		return
			StringUtils.equals(id1.getSystem(), id2.getSystem()) &&
			StringUtils.equals(id1.getValue(), id2.getValue());
	}

	/**
	 * Set the code for the Service Request
	 * @param code	The code to set
	 */
	@ComesFrom(path = "ServiceRequest.code", field = 4, comment = "Universal Service Identifier")
	@ComesFrom(path = "ServiceRequest.code", field = 44, comment = "Procedure Code")
	public void setCode(CodeableConcept code) {
		for (Coding coding : code.getCoding()) {
			if (!coding.hasSystem() && coding.hasCode() && coding.getCode().matches("[1-9]\\d{4}")) {
				coding.setSystem("http://www.ama-assn.org/go/cpt");
			}
		}
		order.setCode(code);
	}

	/**
	 * Set the priority of the order
	 * @param priority the priority
	 */
	@ComesFrom(path = "ServiceRequest.priority", field = 5, comment = "Priority")
	public void setPriority(CodeType priority) {
		if (priority.hasCode()) {
			switch (priority.getCode()) {
			case "S": // Stat With highest priority
				order.setPriority(ServiceRequestPriority.STAT);
				break;
			case "A": // ASAP Fill after S orders
				order.setPriority(ServiceRequestPriority.ASAP);
				break;
			case "R": // Routine Default
				order.setPriority(ServiceRequestPriority.ROUTINE);
				break;
			case "P", // Preop
				"C", // Callback
				"T", // Timing critical A request implying that it is critical to come as close as possible to the requested time, e.g., for a trough antimicrobial level.
				"PRN": // As needed
			default:
				break;
			}
		}
	}

	/**
	 * Set the time the order was placed 
	 * @param requestedDateTime the time the order was placed
	 */
	@ComesFrom(path = "ServiceRequest.occurrenceDateTime", field = 6, comment = "Requested Date/Time")
	public void setRequestedDateTime(DateTimeType requestedDateTime) {
		order.setOccurrence(requestedDateTime);
	}

	/**
	 * The the action code on the specimen
	 * @param specimenActionCode	The action code
	 */
	@ComesFrom(path = "ServiceRequest.intent", field = 11, comment = "Specimen Action Code")
	public void setSpecimenActionCode(CodeType specimenActionCode) {
		// TODO: Figure this out
	}

	/**
	 * The the ordering provider 
	 * @param orderingProvider the ordering provider
	 */
	@ComesFrom(path = "ServiceRequest.requester(ServiceRequest.Practitioner)", field = 16, comment = "Ordering Provider")
	public void setOrderingProvider(Practitioner orderingProvider) {
		this.orderingProvider = orderingProvider;
		Reference ref = ParserUtils.toReference(orderingProvider, order, REQUESTER);
		order.setRequester(ref);
	}

	/**
	 * Set the phone number to call to reach the ordering provider 
	 * @param orderCallbackPhoneNumber the phone number to call to reach the ordering provider 
	 */
	@ComesFrom(path = "ServiceRequest.requester.telecom", field = 17, comment = "Order Callback Phone Number")
	public void setOrderCallbackPhoneNumber(ContactPoint orderCallbackPhoneNumber) {
		if (orderingProvider != null) {
			orderingProvider.getTelecom().add(orderCallbackPhoneNumber);
		}
	}

	/**
	 * Set the quantity and timing for a repeating order
	 * @param quantityTiming the quantity and timing 
	 */
	@ComesFrom(path = "ServiceRequest.occurence", field = 27, comment = "Quantity/Timing")
	public void setQuantityTiming(Period quantityTiming) {
		order.setOccurrence(quantityTiming);
	}

	/**
	 * Set the identifier of the result the result is based on.
	 * @param parentResultsObservationIdentifier the identifier of the parent result 
	 */
	@ComesFrom(path = "ServiceRequest.basedOn.identifier", field = 29, comment = "ParentResults Observation Identifier")
	public void setParentResultsObservationIdentifier(Identifier parentResultsObservationIdentifier) {
		order.addBasedOn().setIdentifier(parentResultsObservationIdentifier);
	}

	/**
	 * Set the reason for the order
	 * @param reasonforStudy the reason for the order
	 */
	@ComesFrom(path = "ServiceRequest.reasonCode", field = 31, comment = "Reason for Study")
	public void setReasonforStudy(CodeableConcept reasonforStudy) {
		order.addReasonCode(reasonforStudy);
	}

	/**
	 * Set placer supplemental information
	 * @param placerSupplementalServiceInformation placer supplemental information
	 */
	@ComesFrom(path = "ServiceRequest.orderDetail", field = 46, comment = "Placer Supplemental Service Information")
	public void setPlacerSupplementalServiceInformation(CodeableConcept placerSupplementalServiceInformation) {
		// TODO: Figure this out.
	}

	/**
	 * Set filler supplemental information
	 * @param fillerSupplementalServiceInformation filler supplemental information
	 */
	@ComesFrom(path = "ServiceRequest.orderDetail", field = 47, comment = "Filler Supplemental Service Information")
	public void setFillerSupplementalServiceInformation(CodeableConcept fillerSupplementalServiceInformation) {
		// TODO: Figure this out.
	}
}
