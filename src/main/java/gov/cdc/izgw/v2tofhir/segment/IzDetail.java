package gov.cdc.izgw.v2tofhir.segment;

import java.util.List;

import org.hl7.fhir.r4.model.CanonicalType;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.Immunization;
import org.hl7.fhir.r4.model.ImmunizationRecommendation;
import org.hl7.fhir.r4.model.ImmunizationRecommendation.ImmunizationRecommendationRecommendationComponent;
import org.hl7.fhir.r4.model.MessageHeader;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Patient;

import ca.uhn.hl7v2.model.Segment;
import gov.cdc.izgw.v2tofhir.converter.MessageParser;
import gov.cdc.izgw.v2tofhir.utils.Mapping;
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
	Immunization immunization;
	
	private Boolean hasImmunizationRecommendation = null;
	ImmunizationRecommendation immunizationRecommendation;
	
	Organization requestingOrganization;
	private Segment segment;
	
	private IzDetail(MessageParser mp) {
		this.mp = mp;
	}
	static IzDetail get(MessageParser mp) {
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

	boolean hasRecommendation() {
		if (hasImmunizationRecommendation != null) {
			return hasImmunizationRecommendation;
		}
		checkForImmunization();
		return hasImmunizationRecommendation;
	}
	
	boolean hasImmunization() {
		if (hasImmunization != null) {
			return hasImmunization;
		}
		checkForImmunization();
		return hasImmunization;
	}
	
	private void checkForImmunization() {
		// Look at the RXA, Profile and Message Header
		hasImmunization = Boolean.FALSE;
		hasImmunizationRecommendation = Boolean.FALSE;
		immunization = null;
		immunizationRecommendation = null;

		if (checkRXA()) {
			// We figured it out from the RXA
			return;
		}
		
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
				// Whereas an RSP_K11 conforming to a Z42 will have an ImmunizationRecommendation or 
				// an Immunization.  We must look at the RXA to determine which of these two cases
				// are possible.  If RXA-5 = 998^No Vaccine Administered^CVX, then it's a recommendation.
				hasImmunization = Boolean.FALSE;
				hasImmunizationRecommendation = Boolean.TRUE;
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
			if (Mapping.v2Table("0076").equals(tag.getSystem()) && "VXU".equals(tag.getCode())) {
				// VXU, so must be an Immunization
				hasImmunization = Boolean.TRUE;
				hasImmunizationRecommendation = Boolean.FALSE;
			}
		}
	}
	
	/**
	 * Looks at the RXA to see whether this is an Immunization or ImmunizationRecommendation
	 * 
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
	 * @return The recommendation from the ImmunizationRecommendation resource.
	 */
	public ImmunizationRecommendationRecommendationComponent getRecommendation() {
		if (hasRecommendation()) {
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