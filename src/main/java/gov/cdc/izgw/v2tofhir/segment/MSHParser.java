package gov.cdc.izgw.v2tofhir.segment;

import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.MessageHeader;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Provenance;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.MessageHeader.MessageDestinationComponent;
import org.hl7.fhir.r4.model.MessageHeader.MessageSourceComponent;
import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Segment;
import ca.uhn.hl7v2.model.Type;
import gov.cdc.izgw.v2tofhir.converter.DatatypeConverter;
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
@ComesFrom(path="MessageHeader.source.sender", source = { "MSH-22", "MSH-4", "MSH-3" })
@ComesFrom(path="MessageHeader.destination.receiver", source = { "MSH-23", "MSH-6", "MSH-5" })
@ComesFrom(path="MessageHeader.source.sender.name", source = "MSH-4")
@ComesFrom(path="MessageHeader.source.sender.endpoint", source = "MSH-3")
@ComesFrom(path="MessageHeader.destination.reciever.name", source = "MSH-6")
@ComesFrom(path="MessageHeader.destination.reciever.endpoint", source = "MSH-5")
@ComesFrom(path="Bundle.timestamp", source = "MSH-7")
@ComesFrom(path="MessageHeader.meta.security", source = { "MSH-8", "MSH-26", "MSH-28" })
@ComesFrom(path="MessageHeader.meta.tag", source = "MSH-9-1")
@ComesFrom(path="MessageHeader.event", source="MSH-9-2")
@ComesFrom(path="MessageHeader.definition", source="MSH-9-3")
@ComesFrom(path="Bundle.identifier", source="MSH-10")
@ComesFrom(path="Bundle.meta.implicitRules", source="MSH-21")
@ComesFrom(path="MessageHeader.meta.security", source="MSH-26")
@ComesFrom(path="MessageHeader.meta.security", source="MSH-27")
@ComesFrom(path="MessageHeader.meta.security", source="MSH-28")
@Slf4j
public class MSHParser extends AbstractSegmentParser {
	static {
		log.debug("{} loaded", MSHParser.class.getName());
	}
	private static final CodeableConcept AUTHOR_AGENT = new CodeableConcept().addCoding(new Coding("http://terminology.hl7.org/CodeSystem/provenance-participant-type", "author", "Author"));

	/**
	 * Construct a new MSHParser for the specified messageParser instance.
	 * @param messageParser	The messageParser this parser is associated with.
	 */
	public MSHParser(MessageParser messageParser) {
		super(messageParser, "MSG");
	}

	@Override
	/**
	 * Parse an MSH segment into MessageHeader and Organization resources.
	 */
	public void parse(Segment msh) throws HL7Exception {
		// Create a new MessageHeader for each ERR resource
		MessageHeader mh = createResource(MessageHeader.class);
		Provenance provenance = (Provenance) mh.getUserData(Provenance.class.getName());
		provenance.getActivity().addCoding(new Coding(null, "v2-FHIR transformation", "HL7 V2 to FHIR transformation"));
		
		
		Organization sourceOrg = getOrganizationFromMsh(msh, true);
		if (sourceOrg != null) {
			Reference ref = ParserUtils.toReference(sourceOrg);
			mh.setResponsible(ref);
			provenance.addAgent().addRole(AUTHOR_AGENT).setWho(ref);
		}
		
		Organization destOrg = getOrganizationFromMsh(msh, false);
		if (destOrg != null) {
			mh.getDestinationFirstRep().setReceiver(ParserUtils.toReference(destOrg));
		}
		
		MessageSourceComponent source = mh.getSource();
		source.setName(getSystem(DatatypeConverter.toCoding(ParserUtils.getField(msh, 3))));	// Sending Application
		source.setEndpoint(getSystem(DatatypeConverter.toCoding(ParserUtils.getField(msh, 4)))); // Sending Facility
		
		MessageDestinationComponent destination = mh.getDestinationFirstRep();
		destination.setName(getSystem(DatatypeConverter.toCoding(ParserUtils.getField(msh, 5))));	// Receiving Application
		destination.setEndpoint(getSystem(DatatypeConverter.toCoding(ParserUtils.getField(msh, 6))));  // Receiving Facility
		Type tm = ParserUtils.getField(msh, 7);
		
		DateTimeType ts = DatatypeConverter.toDateTimeType(ParserUtils.getComponent(tm, 0));
		if (ts != null) {
			getBundle().setTimestamp(ts.getValue());
		}
		
		Coding security = DatatypeConverter.toCoding(ParserUtils.getField(msh, 8));
		mh.getMeta().addSecurity(security);
		
		Type messageType = ParserUtils.getField(msh, 9);
		
		mh.setEvent(DatatypeConverter.toCodingFromTriggerEvent(messageType));
		// It feels like this item may be desirable to have in the message header, but there's no mapping for it.
		// Save it as a tag.
		Coding messageCode = DatatypeConverter.toCodingFromMessageCode(messageType);
		mh.getMeta().addTag(messageCode);
		Coding messageStructure = DatatypeConverter.toCodingFromMessageStructure(messageType);
		if (messageStructure != null) {
			// It's about as close as we get, and has the virtue of being a FHIR StructureDefinition
			mh.setDefinition("http://v2plus.hl7.org/2021Jan/message-structure/" + messageStructure.getCode());
		}
		
		addField(msh, 10, Identifier.class, getBundle()::setIdentifier);
		
		addField(msh, 21, Identifier.class, this::addDefinition);
		
		// Security Classification Tag 
		addField(msh, 26, CodeableConcept.class, this::addSecurity);
		// Security Handling Instructions 
		addField(msh, 27, CodeableConcept.class, this::addSecurity);
		// Special Access Restriction Instructions 
		addField(msh, 28, CodeableConcept.class, this::addSecurity);
		
	}
	
	private void addDefinition(Identifier ident) {
		String rules = "";
		if (ident.hasSystem()) {
			rules = ident.getSystem();
		}
		if (ident.hasValue()) {
			rules = rules + "#" + ident.getValue();
		}
		getContext().getBundle().getMeta().addProfile(rules);
	}
	
	private void addSecurity(CodeableConcept cc) {
		MessageHeader mh = getFirstResource(MessageHeader.class);
		for (Coding coding: cc.getCoding()) {
			mh.getMeta().addSecurity(coding);
		}
	}
	

	private Organization getOrganizationFromMsh(Segment msh, boolean isSender) {
		Organization org = null;
		Type organization = ParserUtils.getField(msh, isSender ? 22 : 23);
		Type facility = ParserUtils.getField(msh, isSender ? 4 : 6);
		if (organization != null) {
			org = toOrganization(organization);
			if (org == null) {
				org = toOrganization(facility);
			} else {
				Identifier ident = DatatypeConverter.toIdentifier(facility);
				if (ident != null) {
					org.addIdentifier(ident);
				}
			}
		} else if (facility != null) {
			org = toOrganization(facility);
		}
		Type application = ParserUtils.getField(msh, isSender ? 3 : 5);
		if (org == null) {
			org = createResource(Organization.class);
		}
		Identifier ident = DatatypeConverter.toIdentifier(application);
		if (ident != null && !ident.isEmpty()) {
			org.addEndpoint().setIdentifier(ident);
		}
		return org.isEmpty() ? null : org;
	}
	private String getSystem(Coding coding) {
		return coding == null ? null : coding.getSystem();
	}

	/** 
	 * Construct an Organization resource
	 * 
	 * if t is an HD, it creates an Organization with just HD-1 or HD-2 as the identifier.value and no system
	 * if t is an XON it creates the Organization from the XON field.
	 * 
	 * @param t	The HL7 Version 2 type to create the organization from.
	 * @return	A new Organization resource.
	 */
	public Organization toOrganization(Type t) {
		if (t == null) {
			return null;
		}
		Organization org = null;
		Identifier ident = null;
		if ("XON".equals(t.getName())) {
			org = createResource(Organization.class);
			org.setName(ParserUtils.toString(t));
			ident = DatatypeConverter.toIdentifier(t);
		} else if ("HD".equals(t.getName())) {
			org = createResource(Organization.class);
			Identifier hd = DatatypeConverter.toIdentifier(t);
			ident = hd == null ? null : new Identifier().setValue(hd.getSystem());
		}
		if (ident != null && !ident.isEmpty()) {
			org.addIdentifier(ident);
		}
		return (org == null || org.isEmpty()) ? null : org;
	}
}
