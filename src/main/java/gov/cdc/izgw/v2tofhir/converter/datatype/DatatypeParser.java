package gov.cdc.izgw.v2tofhir.converter.datatype;

import ca.uhn.hl7v2.model.Type;

public interface DatatypeParser<T extends org.hl7.fhir.r4.model.Type> {
	/**
	 * The name of the type this parser works on.
	 * @return The name of the type this parser works on.
	 */
	Class<? extends T> type();
	/**
	 * Convert a string to a FHIR type.
	 * @param value	The string to convert.
	 * @return	An item of the specified type or null if the conversion could not be performed or when value is null or empty.
	 */
	T fromString(String value);
	/**
	 * Convert a V2 type to a FHIR type.
	 * @param value	The string to convert.
	 * @return	An item of the required type or null if the conversion could not be performed or value is null or empty.
	 */
	T convert(Type type);
	/**
	 * Convert a FHIR type to a V2 type
	 * @param fhirType	The FHIR type to convert
	 * @return The HL7 v2 type.
	 */
}