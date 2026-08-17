package gov.cdc.izgw.v2tofhir.segment;

import java.util.List;

import lombok.Getter;
import lombok.Setter;
import org.hl7.fhir.r4.model.CanonicalType;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.Immunization;
import org.hl7.fhir.r4.model.ImmunizationRecommendation;
import org.hl7.fhir.r4.model.ImmunizationRecommendation.ImmunizationRecommendationRecommendationComponent;
import org.hl7.fhir.r4.model.InstantType;
import org.hl7.fhir.r4.model.MessageHeader;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Patient;

import ca.uhn.hl7v2.model.Segment;
import gov.cdc.izgw.v2tofhir.converter.DatatypeConverter;
import gov.cdc.izgw.v2tofhir.converter.MessageParser;
import gov.cdc.izgw.v2tofhir.terminology.TerminologyMapperFactory;
import gov.cdc.izgw.v2tofhir.utils.ParserUtils;

/**
 * This class is used for parsers that may generate an Immunization or ImmunizationRecommendation
 * resources based on message context clues (e.g., Profiles used or event types).
 * 
 * @author Audacious Inquiry
 */

public class IzDetail {
	private final MessageParser mp;
	
	private Boolean hasImmunization = null;
    /**
     * -- GETTER --
     *  Returns the Immunization resource for this message context, or null if none has been created.
     *
     * @return the Immunization resource, or null
     */
    @Getter
    private Immunization immunization;
	
	private Boolean hasImmunizationRecommendation = null;
	/**
     * Used by every forecast group in the message.
     * <a href="https://hl7.org/fhir/R4/immunizationrecommendation.html">ImmunizationRecommendation</a>
     * I read FHIR R4 as defining ImmunizationRecommendation as a patient's point-in-time set of recommendations,
	 * so one message produces one resource with one recommendation component per forecast.
     * -- GETTER --
     *  Returns the ImmunizationRecommendation resource for this message context, or null if none has been created.
     *
     * @return the ImmunizationRecommendation resource, or null

     */
	@Getter
    private ImmunizationRecommendation immunizationRecommendation;
	/** True once a forecast block (OBX-3 = 30956-7) has been seen in the current ORC/RXA group */
	private boolean forecastBlockStarted;
	/** True when the message declares the Z42 profile (evaluated history and forecast) */
	private boolean evaluatedHistoryAndForecast;

    /**
     * -- GETTER --
     *  Returns the requesting Organization resource for this message context, or null if none has been created.
     *
     *
     * -- SETTER --
     *  Sets the requesting Organization resource for this message context.
     *
     @return the requesting Organization resource, or null
      * @param requestingOrganization the Organization to associate with this context
     */
    @Setter
    @Getter
    private Organization requestingOrganization;
	/** The authority that published the schedule named by OBX-3 = 59779-9, one per message
     * -- GETTER --
     *  Returns the Organization that published the schedule used to evaluate or forecast, or null if
     *  no 59779-9 observation has been seen yet.
     *
     *
     * -- SETTER --
     *  Sets the Organization that published the schedule used to evaluate or forecast.
     *
     @return the publishing authority, or null
      * @param scheduleAuthority the publishing authority
     */
	@Setter
    @Getter
    private Organization scheduleAuthority;
	private Segment segment;

	private IzDetail(MessageParser mp) {
		this.mp = mp;
	}

	/**
	 * Get or create the IzDetail instance for the given MessageParser context.
	 * @param mp the message parser
	 * @return the IzDetail instance for this parse context
	 */
	public static IzDetail get(MessageParser mp) {
		IzDetail detail = mp.getContext().getProperty(IzDetail.class);
		if (detail == null) {
			detail = new IzDetail(mp);
			mp.getContext().setProperty(detail);
		}
		return detail;
	}
	
	/**
	 * Strictly to support testing
	 * @param mp	The message parser used for testing
	 */
	public static void testWith(MessageParser mp) {
		IzDetail detail = mp.getContext().getProperty(IzDetail.class);
		if (detail == null) {
			detail = new IzDetail(mp);
			detail.hasImmunization = true;
			detail.hasImmunizationRecommendation = false;
			detail.immunization = mp.createResource(Immunization.class);
			MessageHeader mh = mp.getFirstResource(MessageHeader.class);
			if (mh != null && detail.hasImmunization()) {
				mh.addFocus(ParserUtils.toReference(detail.immunization, mh, "focus"));
			}
			Patient p = mp.getLastResource(Patient.class);
			if (p != null) {
				detail.immunization.setPatient(ParserUtils.toReference(detail.immunization, p, "patient"));
			}
			mp.getContext().setProperty(detail);
		}
	}

	/**
	 * Returns true if this message context contains an ImmunizationRecommendation.
	 * @return true if an ImmunizationRecommendation is expected/present for this message
	 */
	public boolean hasRecommendation() {
		if (hasImmunizationRecommendation != null) {
			return hasImmunizationRecommendation;
		}
		checkForImmunization();
		return hasImmunizationRecommendation;
	}
	
	/**
	 * Returns true if this message context contains an Immunization.
	 * @return true if an Immunization is expected/present for this message
	 */
	public boolean hasImmunization() {
		if (hasImmunization != null) {
			return hasImmunization;
		}
		checkForImmunization();
		return hasImmunization;
	}

    /**
	 * Returns true when this message declares the Z42 profile, an RSP_K11 carrying evaluated history
	 * and forecast.  Some observation codes mean something different there than in a VXU or a
	 * history-only response, so the mapping has to know which it is.
	 * @return true if the message is a Z42 evaluated history and forecast response
	 */
	public boolean isEvaluatedHistoryAndForecast() {
		if (hasImmunization == null) {
			checkForImmunization();
		}
		return evaluatedHistoryAndForecast;
	}

    private void checkForImmunization() {
		// Look at the RXA, Profile and Message Header
		hasImmunization = Boolean.FALSE;
		hasImmunizationRecommendation = Boolean.FALSE;
		immunization = null;
		// NOTE: immunizationRecommendation is deliberately NOT reset.  It is shared by all forecast
		// groups in the message but each history group gets its own Immunization.
		forecastBlockStarted = false;
		evaluatedHistoryAndForecast = false;

		// RXA couldn't be checked, fall back to MSH profile.
		for (CanonicalType url: mp.getBundle().getMeta().getProfile()) {
			if ("CDCPHINVS#Z32".equals(url.getValue()) || "CDCPHINVS#Z22".equals(url.getValue())) {
				// An RSP_K11 conforming to Z32, or a VXU_V04 conforming to Z22 
				// will always have Immunization resources
				hasImmunization = Boolean.TRUE;
				hasImmunizationRecommendation = Boolean.FALSE;
				return;
			} 
			
			if ("CDCPHINVS#Z42".equals(url.getValue())) {
				evaluatedHistoryAndForecast = true;
				// An RSP_K11 Z42 has both resource types: evaluated history
				// (a dose really administered) becomes an Immunization, and a forecast becomes an
				// ImmunizationRecommendation. The decision is per ORC/RXA group, and RXA-5 carries
				// the discriminator: 998^No Vaccine Administered^CVX means forecast.
				checkRXA();
				return;
			}
		}
		
		// Nope, still haven't figured it out.
		MessageHeader mh = mp.getFirstResource(MessageHeader.class);
		if (mh == null) {
			// there is no message header, so not an immunization or recommendation.
			hasImmunization = Boolean.FALSE;
			hasImmunizationRecommendation = Boolean.FALSE;
			return;
		}
		
		for (Coding tag: mh.getMeta().getTag()) {
			if (TerminologyMapperFactory.get().v2TableUri("0076").equals(tag.getSystem()) && "VXU".equals(tag.getCode())) {
				// VXU, so must be an Immunization
				hasImmunization = Boolean.TRUE;
				hasImmunizationRecommendation = Boolean.FALSE;
			}
		}
	}
	
	/**
	 * Looks at the RXA to see whether this is an Immunization or ImmunizationRecommendation
	 * @return	true if RXA was checked, false if it could not be checked.
	 */
	private boolean checkRXA() {
		// Set default value
		boolean defaulted = true;
		hasImmunizationRecommendation = true;
		if (segment != null) {
			Segment rxa = null;
			if ("ORC".equals(segment.getName())) {
				rxa = ParserUtils.getFollowingSegment(segment, "RXA");
			} else if ("RXA".equals(segment.getName())) {
				rxa = segment;
			}
			if (rxa == null) {
				// ORC but NO RXA
				hasImmunization = hasImmunizationRecommendation = false;
				return false;
			}
			hasImmunizationRecommendation = "998".equals(ParserUtils.toString(rxa, 5));
			defaulted = false;
		} 
		// else No segment, but known to be a Z42, assume testing for ImmunizationRecommendation
		hasImmunization = !hasImmunizationRecommendation;
		return !defaulted;
	}
	/**
	 * Get the recommendation component.
	 * @return The recommendation from the ImmunizationRecommendation resource, or null if not yet created.
	 */
	public ImmunizationRecommendationRecommendationComponent getRecommendation() {
		if (hasRecommendation() && immunizationRecommendation != null) {
			// Get the recommendation created by the last RXA.
			List<ImmunizationRecommendationRecommendationComponent> l = immunizationRecommendation.getRecommendation();
			if (l.isEmpty()) {
				return null;
			}
			return l.get(l.size() - 1);
		}
		return null;
	}

	/**
	 * Start a new forecast block (an OBX-3 = 30956-7^Vaccine Type^LN inside a forecast group).
	 *
	 * The first block in the group adopts the empty recommendation component already added by
	 * RXAParser.setup(), so a message sending one forecast per RXA behaves as it always has.
	 * Each later block adds another component to the same resource, which is what splits a
	 * group carrying many forecasts under a single RXA.
	 *
	 * @return the recommendation component the block's OBX values belong in, or null if there is
	 * no ImmunizationRecommendation to add it to.
	 */
	public ImmunizationRecommendationRecommendationComponent startForecastBlock() {
		if (immunizationRecommendation == null) {
			return null;
		}
		ImmunizationRecommendationRecommendationComponent first = forecastBlockStarted ? null : getRecommendation();
		forecastBlockStarted = true;
		// A new component is added at the end of the list, so getRecommendation() returns it.
		return first != null ? first : immunizationRecommendation.addRecommendation();
	}

	/**
	 * Initialize the resources on receipt of a new ORC or RXA segment. 
	 * @param initialize If true, created new resource always, not just if missing
	 * @param segment The segment currently being parsed
	 */
	public void initializeResources(boolean initialize, Segment segment) {
		// Create the necessary resources.
		DomainResource created = null;
		Patient p = mp.getLastResource(Patient.class);
		this.segment = segment;
		if (initialize) {
			checkForImmunization();
		}
		if (hasRecommendation() && immunizationRecommendation == null) {
			created = immunizationRecommendation = mp.createResource(ImmunizationRecommendation.class);
			if (p != null) {
				immunizationRecommendation.setPatient(ParserUtils.toReference(p, immunizationRecommendation, "patient"));
			}
			// ImmunizationRecommendation.date is required (1..1).  RXA-22 supplies it when present,
			// but some IIS truncate forecast RXAs before RXA-22, so default it to the message
			// timestamp from MSH-7.
			InstantType timestamp = mp.getBundle().getTimestampElement();
			if (!timestamp.isEmpty()) {
				immunizationRecommendation.setDateElement(DatatypeConverter.castInto(timestamp, new DateTimeType()));
			}
		}
		if (hasImmunization() && immunization == null) {
			created = immunization = mp.createResource(Immunization.class);
			if (p != null) {
				immunization.setPatient(ParserUtils.toReference(p, immunization, "patient"));
			}
		}
		if (created != null) {
			MessageHeader mh = mp.getFirstResource(MessageHeader.class);
			if (mh != null) {
				mh.addFocus(ParserUtils.toReference(created, mh, "focus"));
			}
		}
	}
}
