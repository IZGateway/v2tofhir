package gov.cdc.izgw.v2tofhir.utils;

import java.util.function.Consumer;

import org.hl7.fhir.r4.model.CodeType;
import org.hl7.fhir.r4.model.PrimitiveType;
import org.hl7.fhir.r4.model.Resource;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Structure;

/**
 * This static class implements a number of utility functions for working with FHIR Data types
 * 
 * @author Audacious Inquiry
 */
public class FhirUtils {
	private static final String UNKNOWN = "unknown";

	private FhirUtils() {}
	
	/**
	 * Returns true if the given Resource is empty or null
	 * @param resource	The FHIR Resource to check
	 * @return	true if the given FHIR resource is empty or null, false otherwise
	 */
	public static boolean isEmpty(Resource resource) {
		return resource == null || resource.isEmpty();
	}
	/**
	 * Returns true if the given FHIR type is empty or null
	 * @param fhirType	The FHIR datatype to check
	 * @return	true if the given FHIR type is empty or null, false otherwise
	 */
	public static boolean isEmpty(org.hl7.fhir.r4.model.Type fhirType) {
		return fhirType == null || fhirType.isEmpty();
	}
	
	/**
	 * This is a convenience method to enabling actions to be written more simply.
	 * <pre>
	 * 		// Instead of
	 * 		if (isNotEmpty(identifier) {
	 * 			patient.addIdentifier(identifier);
	 * 		}
	 * 		// Use
	 * 		ifNotEmpty(identifier, patient::addIdentifier);
	 * </pre>
	 * 
	 * @param <T>	The FHIR type to check
	 * @param type	The type object
	 * @param action	The action to perform
	 */
	public static <T extends org.hl7.fhir.r4.model.Type> void ifNotEmpty(T type, Consumer<T> action) {
		if (!isEmpty(type)) {
			action.accept(type);
		}
	}
	
	/**
	 * Add a DataAbsent reason to a FHIR primitive type
	 * @param element	The FHIR type to set as absent
	 * @param reason	The reason the data is absent
	 */
	public static void setDataAbsentReason(PrimitiveType<?> element, String reason) {
		element.addExtension()
			.setUrl("http://hl7.org/fhir/StructureDefinition/data-absent-reason")
			.setValue(new CodeType(reason));
	}
		
	/**
	 * Add a DataAbsent reason of "unknown" to a FHIR primitive type
	 * This is used when data is not provided within a V2 message.
	 * 
	 * @param reason	The reason the data is absent
	 */
	public static void setDataAbsent(PrimitiveType<?> reason) {
		setDataAbsentReason(reason, UNKNOWN);
	}
	
	/**
	 * Set a Data Absent Reason extension on a primitive type based on coding from
	 * the NullFlavor vocabulary.
	 * @param type	The type to add the extension to
	 * @param code	The Null Flavor code
	 */
	public static void setDataAbsentFromNullFlavor(PrimitiveType<?> type, String code) {
		switch (code) {
		case "NI":	setDataAbsent(type); break;
		case "INV":	setDataAbsentReason(type, "not-applicable"); break;
		case "DER": setDataAbsent(type); break;
		case "OTH": setDataAbsentReason(type, "unsupported"); break;
		case "MSK": setDataAbsentReason(type, "masked"); break;
		case "NA":  setDataAbsentReason(type, "not-applicable"); break;
		case "UNK": setDataAbsentReason(type, UNKNOWN); break;
		case "NASK":setDataAbsentReason(type, "not-asked"); break;
		case "NAV": setDataAbsentReason(type, "temp-unknown"); break;
		case "NAVU":setDataAbsentReason(type, UNKNOWN); break;
		case "NP":  setDataAbsentReason(type, "asked-unknown"); break;
		default: 
			break;
		}
	}

	/**
	 * Returns true if the structure is empty or cannot be parsed
	 * @param structure The structure to check.
	 * @return true  if the structure is empty or cannot be parsed, false otherwise
	 */
	public static boolean isEmpty(Structure structure) {
		try {
			return structure == null || structure.isEmpty();
		} catch (HL7Exception e) {
			return true;
		}
	}
	
}
