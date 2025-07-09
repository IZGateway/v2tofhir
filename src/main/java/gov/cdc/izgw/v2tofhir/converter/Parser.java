package gov.cdc.izgw.v2tofhir.converter;

import org.hl7.fhir.r4.model.Bundle;

/**
 * Interface for parsing units and subunits of information.
 *  
 * @author Audacious Inquiry
 *
 * @param <U> The unit to parse
 * @param <S> The subunit to parse
 */
public interface Parser<U,S> {

	/**
	 * Convert a unit of information presented as a String to FHIR.
	 * @param string The unit of information to parse as a String
	 * @return	The converted bundle
	 * @throws Exception If an error occurs.
	 */
	Bundle convert(String string) throws Exception;
	
	/**
	 * Convert a unit to FHIR.
	 * @param unit The unit of information to parse
	 * @return	The converted bundle
	 * @throws Exception If an error occurs.
	 */
	Bundle convert(U unit) throws Exception;
	
	/**
	 * Encode a unit to a String for reporting in Provenance
	 * @param unit	The unit to convert
	 * @return	The unit in String format.
	 */
	String encodeUnit(U unit);
	/**
	 * Encode a subunit to a String for reporting in Provenance
	 * @param subunit	The subunit to convert
	 * @return	The subunit in String format.
	 */
	String encode(S subunit);
}
