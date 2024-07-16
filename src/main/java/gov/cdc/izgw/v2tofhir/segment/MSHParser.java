package gov.cdc.izgw.v2tofhir.segment;

import java.util.ArrayList;
import java.util.List;

import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Endpoint;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.InstantType;
import org.hl7.fhir.r4.model.MessageHeader;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Provenance;
import org.hl7.fhir.r4.model.Reference;
import gov.cdc.izgw.v2tofhir.annotation.ComesFrom;
import gov.cdc.izgw.v2tofhir.annotation.Produces;
import gov.cdc.izgw.v2tofhir.converter.MessageParser;
import gov.cdc.izgw.v2tofhir.utils.ParserUtils;
import lombok.extern.slf4j.Slf4j;

/**
 * The MSH Parser creates a MessageHeader Resource for MSH segements.
 * 
 * @see <a href="https://hl7.org/fhir/uv/v2mappings/2024Jan/ConceptMap-segment-msh-to-bundle.html">V2-to-FHIR: MSH to Bundle</a>
 * @see <a href="https://hl7.org/fhir/uv/v2mappings/2024Jan/ConceptMap-segment-msh-to-messageheader.html">V2-to-FHIR: MSH to MessageHeader</a>
 * 
 * @author Audacious Inquiry
 */
@Produces(segment="MSH", resource=MessageHeader.class, extra = { Organization.class, OperationOutcome.class, Bundle.class })
@Slf4j
public class MSHParser extends AbstractSegmentParser {
	private MessageHeader mh;
	
	private static final String SENDING_ORGANIZATION = "sendingOrganization";
	private static final String DESTINATION_ORGANIZATION = "destinationOrganization";

	static {
		log.debug("{} loaded", MSHParser.class.getName());
	}
	private static final List<FieldHandler> fieldHandlers = new ArrayList<>();
	/**
	 * Construct a new MSHParser for the specified messageParser instance.
	 * @param messageParser	The messageParser this parser is associated with.
	 */
	public MSHParser(MessageParser messageParser) {
		super(messageParser, "MSG");
		if (fieldHandlers.isEmpty()) {
			initFieldHandlers(this, fieldHandlers);
		}
	}
	
	public List<FieldHandler> getFieldHandlers() {
		return fieldHandlers;
	}

	@Override
	public IBaseResource setup() {
		Provenance provenance;
		mh = createResource(MessageHeader.class);
		provenance = (Provenance) mh.getUserData(Provenance.class.getName());
		if (provenance != null) {
			provenance.getActivity().addCoding(new Coding(null, "v2-FHIR transformation", "HL7 V2 to FHIR transformation"));
		}
		return mh;
	}

	/**
	 * Create the sending organization (or update the existing one) and add a 
	 * reference to it to the MessageHeader.sender field.
	 *  
	 * @param ident	The identifier of the sending organization.
	 */
	@ComesFrom(path="MessageHeader.responsible", field = 22, comment="Sending Responsible Organization")
	public void setSourceSender(Identifier ident) {
		Organization sendingOrganization = getOrganization(SENDING_ORGANIZATION);
		sendingOrganization.addIdentifier(ident);
	}
	
	/**
	 * Create the sending organization (or update the existing one) and add a 
	 * reference to it to the MessageHeader.sender field.
	 *  
	 * @param senderEndpoint	The identifier of the sending application.
	 */
	@ComesFrom(path="MessageHeader.source.endpoint", field = 3, 
			   also={ "Organization.endpoint.identifier", "Endpoint.name", "Endpoint.identifier" }, 
			   comment="Sending Application")
	public void setSourceSenderEndpoint(Identifier senderEndpoint) {
		mh.getSource().setEndpoint(senderEndpoint.getSystem());
		Organization sendingOrganization = getOrganization(SENDING_ORGANIZATION); 
		Endpoint ep = createResource(Endpoint.class);
		ep.setName(senderEndpoint.getSystem());
		ep.addIdentifier().setValue(senderEndpoint.getSystem());
		sendingOrganization.getEndpoint().add(ParserUtils.toReference(ep));
	}
	
	/**
	 * Set the sending facility
	 * @param sendingFacility The sending facility
	 */
	@ComesFrom(path="MessageHeader.source.name", field = 4, comment="Sending Facility")
	public void setSourceSenderName(Identifier sendingFacility) {
		mh.getSource().setName(sendingFacility.getSystem());
		Organization sendingOrganization = getOrganization(SENDING_ORGANIZATION);
		sendingOrganization.setName(sendingFacility.getSystem());
	}
	
	private Organization getOrganization(String organizationType) {
		Organization organization = (Organization) mh.getUserData(organizationType);
		if (organization == null) {
			organization = createResource(Organization.class);
			mh.setSender(ParserUtils.toReference(organization));
			mh.setUserData(organizationType, organization);
		}
		return organization;
	}
	
	/**
	 * Copy information from the reciever organization to the destination organization
	 * @param receiver The receiving organization from an XON
	 */
	@ComesFrom(path="MessageHeader.destination.receiver", field = 23, comment="Receiving Responsible Organization")
	public void setDestinationReceiver(Organization receiver) {
		Organization receivingOrganization = getOrganization(DESTINATION_ORGANIZATION);
		if (receiver.hasName()) {
			receivingOrganization.setName(receiver.getName());
		}
		if (receiver.hasIdentifier()) {
			for (Identifier ident: receiver.getIdentifier()) {
				receivingOrganization.addIdentifier(ident);
			}
		}
		if (receiver.hasEndpoint()) {
			for (Reference endpoint: receiver.getEndpoint()) {
				receivingOrganization.addEndpoint(endpoint);
			}
		}
	}
	
	
	/**
	 * Set the receiver endpoint from MSH-5
	 * @param receiverEndpoint	The receiver endpoint
	 */
	@ComesFrom(path="MessageHeader.destination.endpoint", field = 5, comment="Receiving Application",
			   also="MessageHeader.destination.receiver.resolve().Organization.endpoint")
	public void setDestinationReceiverEndpoint(Identifier receiverEndpoint) {
		mh.getDestinationFirstRep().setEndpoint(receiverEndpoint.getSystem());
		
		Organization organization = getOrganization(DESTINATION_ORGANIZATION);
		Endpoint endpoint = createResource(Endpoint.class);
		endpoint.addIdentifier(receiverEndpoint);
		organization.addEndpoint(ParserUtils.toReference(endpoint));
				
	}
	
	/**
	 * Set the destination name from MSH-6 Receiving Facility
	 * @param receiverName	The receiving facility name
	 */
	@ComesFrom(path="MessageHeader.destination.name", field = 6, comment="Receiving Facility",
			   also="MessageHeader.destination.receiver.resolve().Organization.identifier")
	public void setDestinationReceiverName(Identifier receiverName) {
		mh.getDestinationFirstRep().setName(receiverName.getSystem());
		
		Organization organization = getOrganization(DESTINATION_ORGANIZATION);
		organization.addIdentifier(new Identifier().setValue(receiverName.getSystem()));
	}
	
	
	/**
	 * Set the bundle timestamp from MSH-7
	 * @param instant The timestamp
	 */
	@ComesFrom(path="Bundle.timestamp", field = 7, component = 0)
	public void setBundleTimestamp(InstantType instant) {
		getBundle().setTimestampElement(instant);
	}
	
	/**
	 * Add a tag for the messageCode from MSH-9-1
	 * @param messageCode	The message code
	 */
	@ComesFrom(path="MessageHeader.meta.tag", field = 9, component = 1, table="0076", comment="Message Code")
	public void setMetaTag(Coding messageCode) {
		mh.getMeta().addTag(messageCode);
	}
	
	/**
	 * Add a tag for the trigger event in MSH-9-2
	 * @param triggerEvent The trigger event.
	 */
	@ComesFrom(path="MessageHeader.eventCoding", field = 9, component = 2, table="0003", comment="Trigger Event")
	public void setEvent(Coding triggerEvent) {
		mh.setEvent(triggerEvent);
	}
	
	/**
	 * Set the message structure in MSH-9-3
	 * @param messageStructure The message structure.
	 */
	@ComesFrom(path="Bundle.implicitRules", field = 9, component = 3, table="0354", comment="Message Structure",
			   also="MessageHeader.definition")
	public void setMessageStructure(Coding messageStructure) {
		String rules = "http://v2plus.hl7.org/2021Jan/message-structure/" + messageStructure.getCode();
		getBundle().setImplicitRules(rules);
		// It's about as close as we get, and has the virtue of being a pretty close to a FHIR StructureDefinition
		mh.setDefinition(rules);
	}
	
	/**
	 * Set the bundle identifier from MSH-10 Message Identifier
	 * @param ident	The message identifier
	 */
	@ComesFrom(path="Bundle.identifier", field = 10)
	public void setBundleIdentifier(Identifier ident) {
		getBundle().setIdentifier(ident);
	}
	
	/**
	 * Set the Message Profile from MSH-21
	 * @param ident	The message profile id
	 */
	@ComesFrom(path="Bundle.meta.profile", field = 21)
	public void setMetaProfile(Identifier ident) {
		String rules = "";
		if (ident.hasSystem()) {
			rules = ident.getSystem();
		}
		if (ident.hasValue()) {
			rules = rules + "#" + ident.getValue();
		}
		getBundle().getMeta().addProfile(rules);
	}
	
	/**
	 * Set security tags on the message
	 * @param securityCode	The security code
	 */
	@ComesFrom(path="MessageHeader.meta.security", field=8)
	@ComesFrom(path="MessageHeader.meta.security", field=26)
	@ComesFrom(path="MessageHeader.meta.security", field=27)
	@ComesFrom(path="MessageHeader.meta.security", field=28)
	public void addSecurity(CodeableConcept securityCode) {
		for (Coding coding: securityCode.getCoding()) {
			mh.getMeta().addSecurity(coding);
		}
	}
}
