package gov.cdc.izgw.v2tofhir.converter.segment;

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
import gov.cdc.izgw.v2tofhir.converter.ParserUtils;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MSHParser extends AbstractSegmentParser {

	private static final CodeableConcept AUTHOR_AGENT = new CodeableConcept().addCoding(new Coding("http://terminology.hl7.org/CodeSystem/provenance-participant-type", "author", "Author"));

	public MSHParser(MessageParser messageParser) {
		super(messageParser, "MSG");
	}

	@Override
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
		DateTimeType ts = DatatypeConverter.toDateTimeType(ParserUtils.getField(msh, 7));
		if (ts != null) {
			getBundle().setTimestamp(ts.getValue());
		}
		Type messageType = ParserUtils.getField(msh, 9);
		mh.setEvent(DatatypeConverter.toCodingFromTriggerEvent(messageType));
		// It feels like this item may be desirable to have in the message header, but there's no mapping for it.
		// Save it as a tage.
		Coding messageCode = DatatypeConverter.toCodingFromMessageCode(messageType);
		mh.getMeta().addTag(messageCode);
		Coding messageStructure = DatatypeConverter.toCodingFromMessageStructure(messageType);
		if (messageStructure != null) {
			// It's about as close as we get, and has the virtue of being a FHIR StructureDefinition
			mh.setDefinition("http://v2plus.hl7.org/2021Jan/message-structure/" + messageStructure.getCode());
		}
		Identifier messageId = DatatypeConverter.toIdentifier(ParserUtils.getField(msh, 10));
		getBundle().setIdentifier(messageId);
		// TODO: Deal with MSH-24 and MSH-25 when https://jira.hl7.org/browse/V2-25792 is resolved
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
		if (org != null) {
			Identifier ident = DatatypeConverter.toIdentifier(application);
			if (ident != null) {
				org.addEndpoint().setIdentifier(ident);
			}
		}
		return org;
	}
	private String getSystem(Coding coding) {
		return coding == null ? null : coding.getSystem();
	}

	public Organization toOrganization(Type t) {
		if (t == null) {
			return null;
		}
		if ("XON".equals(t.getName())) {
			Organization org = createResource(Organization.class);
			org.setName(ParserUtils.toString(t));
			Identifier ident = DatatypeConverter.toIdentifier(t);
			if (ident != null) {
				org.addIdentifier(ident);
			}
		}
		return null;
	}
}
