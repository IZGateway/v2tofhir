package gov.cdc.izgw.v2tofhir.segment;

import java.util.ArrayList;
import java.util.List;

import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Address;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.ContactPoint;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Immunization;
import org.hl7.fhir.r4.model.Immunization.ImmunizationPerformerComponent;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Practitioner;
import org.hl7.fhir.r4.model.PractitionerRole;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.ServiceRequest;
import org.hl7.fhir.r4.model.ServiceRequest.ServiceRequestStatus;

import ca.uhn.hl7v2.model.Segment;
import gov.cdc.izgw.v2tofhir.annotation.ComesFrom;
import gov.cdc.izgw.v2tofhir.annotation.Produces;
import gov.cdc.izgw.v2tofhir.converter.MessageParser;
import gov.cdc.izgw.v2tofhir.utils.Codes;
import gov.cdc.izgw.v2tofhir.utils.ParserUtils;

/**
 * ORCParser parses any ORC segments in a message to a ServiceRequest
 * If the message is a VXU or an response to an immunization query, it also produces an Immunization resource.
 * 
 * @author Audacious Inquiry
 *
 * @see <a href="https://hl7.org/fhir/uv/v2mappings/2024Jan/ConceptMap-segment-orc-to-servicerequest.html">V2 to FHIR: ORC to ServiceRequest</a>
 * @see <a href="https://hl7.org/fhir/uv/v2mappings/2024Jan/ConceptMap-segment-orc-to-immunization.html">V2 to FHIR: ORC to Immunization</a>
 */
@Produces(segment = "ORC", resource=ServiceRequest.class, 
	extra = { Practitioner.class, Organization.class, Immunization.class }
)
public class ORCParser extends AbstractSegmentParser {
	private static List<FieldHandler> fieldHandlers = new ArrayList<>();
	private ServiceRequest order;
	private PractitionerRole requester;
	private IzDetail izDetail;
	
	@Override 
	public void parse(Segment orc) {
		super.parse(orc);
	}
	/**
	 * Create an ORC Parser for the specified MessageParser
	 * @param p	The message parser.
	 */
	public ORCParser(MessageParser p) {
		super(p, "ORC");
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
		order = createResource(ServiceRequest.class);
		return order;
	}
	
	/**
	 * Set Order control code from ORC-1
	 * 
	 * Also determines if this ORC is part of a VXU, or RSP_K11 response to an Immunization Query, and if so,
	 * creates the Immunization resource.
	 * 
	 * @param orderControl	The order control code from Table HL7-0119
	 */
	@ComesFrom(path = "ServiceRequest.status", field = 1, table = "0119",
			   also = { "ServiceRequest.indent = 'order'",
					    "ServiceRequest.subject = Patient",
					    "ServiceRequest.encounter = Encounter"
			   }, priority = 10)
	public void setOrderControl(Coding orderControl) {
		// ORC-1 is required, so this method should always be called
		
		if (order.hasStatus()) {
			// Status was already set by ORC-5
			return;
		}
		if (orderControl.hasCode()) {
			switch (orderControl.getCode()) {
			// For immunization messages, we are only expecting RE.
			case "RE", "CN", "OE", "OF", "OR":
				order.setStatus(ServiceRequestStatus.COMPLETED);
				break;
			case "AF", "CH", "FU", "NW", "OK", "PA", "PR", "RL", "RO", "RP", 
				 "RQ", "RR", "RU":
				order.setStatus(ServiceRequestStatus.ACTIVE);
				break;
			case "CA", "CR", "DC", "DF", "DR", "OC", "OD", "UA":
				order.setStatus(ServiceRequestStatus.REVOKED);
				break;
			case "DE":
				order.setStatus(ServiceRequestStatus.ENTEREDINERROR);
				break;
			case "HD", "HR", "OH":
				order.setStatus(ServiceRequestStatus.ONHOLD);
				break;
			default:
				order.setStatus(ServiceRequestStatus.UNKNOWN);
				break;
			}
		}
	}
	
	private void setOrderIdentifier(Identifier ident, CodeableConcept type) {
		ident.setType(type);
		order.addIdentifier(ident);
		if (izDetail.hasImmunization()) {
			izDetail.immunization.addIdentifier(ident);
		}
	}
	/**
	 * Set ServiceRequest.identifier from ORC-2 and ORC-33
	 * @param ident	The identifier
	 */
	@ComesFrom(path = "ServiceRequest.identifier", field = 2)
	@ComesFrom(path = "ServiceRequest.identifier", field = 33)
	public void setOrderPlacerIdentifier(Identifier ident) {
		setOrderIdentifier(ident, Codes.PLACER_ORDER_IDENTIFIER_TYPE);
	}
	
	/**
	 * Set ServiceRequest.identifier from ORC-3
	 * @param ident	The identifier
	 */
	@ComesFrom(path = "ServiceRequest.identifier", field = 3)
	public void setOrderFillerIdentifier(Identifier ident) {
		setOrderIdentifier(ident, Codes.FILLER_ORDER_IDENTIFIER_TYPE);
	}
	
	/**
	 * Set ServiceRequest.identifier from ORC-4
	 * @param ident	The identifier
	 */
	@ComesFrom(path = "ServiceRequest.identifier", field = 4)
	public void setOrderPlacerGroupIdentifier(Identifier ident) {
		setOrderIdentifier(ident, Codes.PLACER_ORDER_GROUP_TYPE);
	}
	

	/**
	 * Set the orderStatus from ORC-5
	 * @param orderStatus	The orderStatus from table 0038
	 */
	@ComesFrom(path = "ServiceRequest.status", field = 5, table="0038", priority = 5)
	public void setOrderStatus(Coding orderStatus) {
		if (orderStatus.hasCode()) {
			switch (orderStatus.getCode()) {
			case "A", "IP", "SC":
				order.setStatus(ServiceRequestStatus.ACTIVE);
				break;
			case "CA", "DC":
				order.setStatus(ServiceRequestStatus.REVOKED);
				break;
			case "CM":
				order.setStatus(ServiceRequestStatus.COMPLETED);
				break;
			case "HD":
				order.setStatus(ServiceRequestStatus.ONHOLD);
				break;
			case "ER", "RP":
			default:
				break;
			}
		}
	}
	
	/**
	 * Add the Placer parent identifier
	 * @param ident The identifier
	 */
	@ComesFrom(path = "ServiceRequest.identifier", field = 8, component = 1)
	public void setOrderPlacerParentIdentifier(Identifier ident) { 
		setOrderIdentifier(ident, Codes.PLACER_ORDER_GROUP_TYPE);
	}

	/**
	 * Add the Filler parent identifier
	 * @param ident The identifier
	 */
	@ComesFrom(path = "ServiceRequest.identifier", field = 8, component = 2)
	public void setOrderFillerParentIdentifier(Identifier ident) { 
		setOrderIdentifier(ident, Codes.FILLER_ORDER_GROUP_TYPE);
	}
	
	/**
	 * Set the order creation date time
	 * @param orderDateTime the creation date time.
	 */
	@ComesFrom(path = "ServiceRequest.authoredOn", field = 9)
	public void setOrderCreationTime(DateTimeType orderDateTime) {
		order.setAuthoredOnElement(orderDateTime);
		if (izDetail.hasImmunization()) {
			izDetail.immunization.setRecordedElement(orderDateTime);
		}
	}
	
	/**
	 * Set the ordering provider
	 * @param orderingProvider the ordering provider
	 */
	@ComesFrom(path = "ServiceRequest.requester", field = 12) 
	public void setOrderingProvider(Practitioner orderingProvider) {
		Reference providerReference = ParserUtils.toReference(addResource(orderingProvider));
		getRequester().setPractitioner(providerReference);
	}
	
	/**
	 * Get the ordering PractitionerRole or create one if it
	 * does not already exist.
	 * @return the ordering PractitionerRole 
	 */
	private PractitionerRole getRequester() {
		if (requester == null) {
			requester = createResource(PractitionerRole.class);
			Reference ref = ParserUtils.toReference(requester);
			order.setRequester(ref);
			if (izDetail.hasImmunization()) {
				ImmunizationPerformerComponent perf = izDetail.immunization.addPerformer().setActor(ref);
				perf.setFunction(Codes.ORDERING_PROVIDER_FUNCTION_CODE);
			}
		}
		return requester;
	}
	
	/**
	 * Set the order effective date time
	 * @param effectiveDateTime The effective date and time
	 */
	@ComesFrom(path = "ServiceRequest.occurrenceDateTime", field = 15)
	public void setOrderDateTime(DateTimeType effectiveDateTime) {
		order.setOccurrence(effectiveDateTime);
	}
	
	/**
	 * Set the ordering organization
	 * @param organization the ordering organization
	 */
	@ComesFrom(path = "ServiceRequest.requester", field = 21) 
	public void setOrderingOrganization(Organization organization) {
		if (izDetail.requestingOrganization != null) {
			// If organization already exists, just copy name and identifier from XON
			izDetail.requestingOrganization.setNameElement(organization.getNameElement());
			izDetail.requestingOrganization.setIdentifier(organization.getIdentifier());
		} else {
			izDetail.requestingOrganization = addResource(organization);
		}
		Reference orgReference = ParserUtils.toReference(izDetail.requestingOrganization);
		getRequester().setOrganization(orgReference);
	}
	
	/**
	 * Set the ordering organization address
	 * @param orderingProviderAddress the ordering organization address
	 */
	@ComesFrom(path = "ServiceRequest.requester", field = 22) 
	public void setOrderingOrganizationAddress(Address orderingProviderAddress) {
		if (izDetail.requestingOrganization == null) {
			izDetail.requestingOrganization = createResource(Organization.class);
			getRequester().setOrganization(ParserUtils.toReference(izDetail.requestingOrganization));
		}
		izDetail.requestingOrganization.addAddress(orderingProviderAddress);
	}
	
	/**
	 * Set the ordering organization phone number
	 * @param orderingProviderContact the ordering organization phone number
	 */
	@ComesFrom(path = "ServiceRequest.requester", field = 23) 
	public void setOrderingOrganizationTelecom(ContactPoint orderingProviderContact) {
		if (izDetail.requestingOrganization == null) {
			izDetail.requestingOrganization = createResource(Organization.class);
			getRequester().setOrganization(ParserUtils.toReference(izDetail.requestingOrganization));
		}
		izDetail.requestingOrganization.addTelecom(orderingProviderContact);
	}
	
	/**
	 * Set the confidentiality code for the order
	 * @param confidentiality the confidentiality code for the order
	 */
	@ComesFrom(path = "ServiceRequest.meta.security", field = 28) 
	public void setOrderConfidentiality(CodeableConcept confidentiality) {
		for (Coding securityCode: confidentiality.getCoding()) {
			order.getMeta().addSecurity(securityCode);
			if (izDetail.hasImmunization()) {
				izDetail.immunization.getMeta().addSecurity(securityCode);
			}
		}
	}
	
	/**
	 * Set the order location type
	 * @param locationCode the order location type
	 */
	@ComesFrom(path = "ServiceRequest.locationCode", field = 29, table = "0482") 
	public void setOrderLocationCode(CodeableConcept locationCode) {
		order.addLocationCode(locationCode);
	}
	
	

}