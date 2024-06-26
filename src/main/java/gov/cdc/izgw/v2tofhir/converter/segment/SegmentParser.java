package gov.cdc.izgw.v2tofhir.converter.segment;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Segment;

public interface SegmentParser {
	/** The name of the segment this parser works on */
	String segment();
	/** 
	 * Parse the segment into FHIR Resources in the bundle being
	 * prepared by a MessageParser 
	 * @throws HL7Exception If an error occurs reading the HL7 message content
	 **/
	void parse(Segment seg) throws HL7Exception;
}
