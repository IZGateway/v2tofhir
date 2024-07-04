package gov.cdc.izgw.v2tofhir.converter.segment;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Structure;

/**
 * This is the base interface for parsers of HL7 Structures (segments and groups).
 */
public interface StructureParser {
	/** The name of the segment this parser works on */
	
	/**
	 * The name of the segment this parser works on.
	 * @return The name of the segment this parser works on
	 */
	String structure();

	/**
	 * Parses an HL7 Message structure into FHIR Resources.
	 * @param structure	The structure to parse
	 * @throws HL7Exception A structure to parse.
	 */
	void parse(Structure structure) throws HL7Exception;
}
