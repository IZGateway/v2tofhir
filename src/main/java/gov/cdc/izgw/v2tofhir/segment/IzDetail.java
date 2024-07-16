package gov.cdc.izgw.v2tofhir.segment;

import java.util.List;

import org.hl7.fhir.r4.model.CanonicalType;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Immunization;
import org.hl7.fhir.r4.model.ImmunizationRecommendation;
import org.hl7.fhir.r4.model.ImmunizationRecommendation.ImmunizationRecommendationRecommendationComponent;
import org.hl7.fhir.r4.model.MessageHeader;
import org.hl7.fhir.r4.model.Organization;

import gov.cdc.izgw.v2tofhir.utils.Mapping;

/**
 * This class is used for parsers that may generate an Immunization or ImmunizationRecommendation
 * resources based on message context clues (e.g., Profiles used or event types).
 * 
 * @author Audacious Inquiry
 */

class IzDetail {
	private final AbstractSegmentParser p;
	private IzDetail(AbstractSegmentParser p) {
		this.p = p;
	}
	static IzDetail get(AbstractSegmentParser p) {
		IzDetail detail = p.getContext().getProperty(IzDetail.class);
		if (detail == null) {
			detail = new IzDetail(p);
			p.getContext().setProperty(detail);
		}
		return detail;
	}
	
	private Boolean hasImmunization = null;
	Immunization immunization;
	
	private Boolean hasImmunizationRecommendation = null;
	ImmunizationRecommendation immunizationRecommendation;
	
	Organization requestingOrganization;

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
		// Look at the message Header.
		hasImmunization = Boolean.FALSE;
		hasImmunizationRecommendation = Boolean.FALSE;
		
		MessageHeader mh = p.getFirstResource(MessageHeader.class);
		if (mh == null) {
			// there is no message header, so not an immunization or recommendation.
			return;
		}
		for (CanonicalType url: p.getBundle().getMeta().getProfile()) {
			if ("CDCPHINVS#Z32".equals(url.getValue()) || "CDCPHINVS#Z22".equals(url.getValue())) {
				// An RSP_K11 conforming to Z32, or a VXU_V04 conforming to Z22 
				// will have Immunization resources
				hasImmunization = Boolean.TRUE;
			} else if ("CDCPHINVS#Z42".equals(url.getValue())) {
				// Whereas an RSP_K11 conforming to a Z42 will have an ImmunizationRecommendation
				hasImmunizationRecommendation = Boolean.TRUE;
			}
		}
		for (Coding tag: mh.getMeta().getTag()) {
			if (Mapping.v2Table("0076").equals(tag.getSystem()) && "VXU".equals(tag.getCode())) {
				hasImmunization = Boolean.TRUE;
			}
		}
		
		// Create the necessary resources.
		if (hasImmunizationRecommendation) {
			immunizationRecommendation = p.createResource(ImmunizationRecommendation.class);
		}
		if (hasImmunization) {
			immunization = p.createResource(Immunization.class);
		}
	}
	
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
}