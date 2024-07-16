package gov.cdc.izgw.v2tofhir.segment;

import java.util.List;

import org.hl7.fhir.instance.model.api.IBaseResource;

import java.util.ArrayList;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Immunization;
import gov.cdc.izgw.v2tofhir.annotation.ComesFrom;
import gov.cdc.izgw.v2tofhir.annotation.Produces;
import gov.cdc.izgw.v2tofhir.converter.MessageParser;

/**
 * RXRParser parses any RXR segments in a message to an Immunization if the message is a VXU or an 
 * response to an immunization query.
 * 
 * @author Audacious Inquiry
 *
 * @see <a href="https://hl7.org/fhir/uv/v2mappings/2024Jan/ConceptMap-segment-rxr-to-immunization.html">V2 to FHIR: RXR to Immunization</a>
 */
@Produces(segment = "RXR", resource=Immunization.class)
public class RXRParser extends AbstractSegmentParser {
	private static List<FieldHandler> fieldHandlers = new ArrayList<>();
	private IzDetail izDetail;
	
	/**
	 * Create an RXA Parser for the specified MessageParser
	 * @param p	The message parser.
	 */
	public RXRParser(MessageParser p) {
		super(p, "RXR");
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
		return izDetail.immunization;
	}
	
	/*
	 * 1	RXR-1	Route	CWE	1	1				Immunization.route		Immunization.CodeableConcept	0	1	CWE[CodeableConcept]	RouteOfAdministration
	 */
	/**
	 * Set the route of administration
	 * @param route the route of administration
	 */
	@ComesFrom(path = "Immunization.route", field = 1, table = "0162", comment = "Route")
	public void setRoute(CodeableConcept route) {
		if (izDetail.hasImmunization()) {
			izDetail.immunization.setRoute(route);
		}
	}
	/*
	 * 2	RXR-2	Administration Site	CWE	0	1				Immunization.site		Immunization.CodeableConcept	0	1	CWE[CodeableConcept]	AdministrationSite		
	 */
	/**
	 * Set the site of administration
	 * @param administrationSite the route of administration
	 */
	@ComesFrom(path = "Immunization.site", field = 2, table = "0163", comment = "Administration Site")
	public void setAdministrationSite(CodeableConcept administrationSite) {
		if (izDetail.hasImmunization()) {
			izDetail.immunization.setRoute(administrationSite);
		}
	}
}