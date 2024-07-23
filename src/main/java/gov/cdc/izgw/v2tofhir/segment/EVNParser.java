package gov.cdc.izgw.v2tofhir.segment;

import java.util.ArrayList;
import java.util.List;

import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.InstantType;
import org.hl7.fhir.r4.model.Location;
import org.hl7.fhir.r4.model.MessageHeader;
import org.hl7.fhir.r4.model.Practitioner;
import org.hl7.fhir.r4.model.Provenance;

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
@Produces(segment = "EVN", resource = Provenance.class)
@Slf4j
public class EVNParser extends AbstractSegmentParser {
	Provenance provenance;
	private static List<FieldHandler> fieldHandlers = new ArrayList<>();
	static {
		log.debug("{} loaded", EVNParser.class.getName());
	}

	/**
	 * Construct a new DSC Parser for the given messageParser.
	 * 
	 * @param messageParser The messageParser using this QPDParser
	 */
	public EVNParser(MessageParser messageParser) {
		super(messageParser, "EVN");
		if (fieldHandlers.isEmpty()) {
			FieldHandler.initFieldHandlers(this, fieldHandlers);
		}
	}

	@Override
	public List<FieldHandler> getFieldHandlers() {
		return fieldHandlers;
	}

	public IBaseResource setup() {
		MessageHeader mh = getFirstResource(MessageHeader.class);
		if (mh != null) {
			provenance = (Provenance) mh.getUserData(Provenance.class.getName());
		} else {
			// Create a standalone provenance resource for segment testing
			provenance = createResource(Provenance.class);
		}
		return provenance;
	}

	/**
	 * Set the recorded date time.
	 * @param recordedDateTime
	 */
	@ComesFrom(path = "Provenance.recorded", field = 2, comment = "Recorded Date/Time")
	public void setRecordedDateTime(InstantType recordedDateTime) {
		provenance.setRecordedElement(recordedDateTime);
	}

	/**
	 * Set the reason for the event
	 * @param eventReasonCode	the reason for the event
	 */
	@ComesFrom(path = "Provenance.reason", field = 4, comment = "Event Reason Code")
	public void setEventReasonCode(CodeableConcept eventReasonCode) {
		provenance.addReason(eventReasonCode);
	}

	/**
	 * Set the operator
	 * @param operatorId	The operator
	 */
	@ComesFrom(path = "Provenance.agent.who.Practitioner", field = 5, comment = "Operator ID")
	public void setOperatorID(Practitioner operatorId) {
		provenance.addAgent().setWho(ParserUtils.toReference(operatorId));
	}

	/**
	 * Set the date/time of the event
	 * @param eventOccurred the occurence date/time of the event
	 */
	@ComesFrom(path = "Provenance.occurredDateTime", field = 6, comment = "Event Occurred")
	public void setEventOccurred(DateTimeType eventOccurred) {
		provenance.setOccurred(eventOccurred);
	}

	/**
	 * Set the identifier of the facility where the event occurred
	 * @param eventFacility The event facility identifier
	 */
	@ComesFrom(path = "Provenance.location", field = 7, comment = "Event Facility")
	public void setEventFacility(Identifier eventFacility) {
		if (eventFacility.hasSystem()) {
			Location location = createResource(Location.class);
			location.setName(eventFacility.getSystem());
			location.addIdentifier(eventFacility);
			provenance.setLocation(ParserUtils.toReference(location));
		}
	}
}
