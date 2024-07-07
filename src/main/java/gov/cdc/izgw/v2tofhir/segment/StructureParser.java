package gov.cdc.izgw.v2tofhir.segment;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.hl7.fhir.r4.model.CodeType;
import org.hl7.fhir.r4.model.PrimitiveType;
import org.hl7.fhir.r4.model.Resource;
import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Segment;
import ca.uhn.hl7v2.model.Structure;
import ca.uhn.hl7v2.model.Type;
import gov.cdc.izgw.v2tofhir.converter.DatatypeConverter;

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
	
	/**
	 * Returns true if the structure is empty or cannot be parsed
	 * @param structure The structure to check.
	 * @return true  if the structure is empty or cannot be parsed, false otherwise
	 */
	default boolean isEmpty(Structure structure) {
		try {
			return structure != null && structure.isEmpty();
		} catch (HL7Exception e) {
			return true;
		}
	}

	/**
	 * Returns true if the structure is not empty or cannot be parsed
	 * @param structure The structure to check.
	 * @return true  if the structure is not empty or cannot be parsed, false otherwise
	 */
	default boolean isNotEmpty(Structure structure) {
		try {
			return structure == null || structure.isEmpty();
		} catch (HL7Exception e) {
			return false;
		}
	}

	/**
	 * Returns true if the given FHIR type is not empty or null
	 * @param fhirType	The FHIR datatype to check
	 * @return	true if the given FHIR type is not empty or null, false otherwise
	 */
	default boolean isNotEmpty(org.hl7.fhir.r4.model.Type fhirType) {
		return fhirType != null && !fhirType.isEmpty();
	}
	
	/**
	 * Returns true if the given Resource is not empty or null
	 * @param resource	The FHIR Resource to check
	 * @return	true if the given FHIR resource is not empty or null, false otherwise
	 */
	default boolean isNotEmpty(Resource resource) {
		return resource != null && !resource.isEmpty();
	}
	
	/**
	 * Returns true if the given Resource is empty or null
	 * @param resource	The FHIR Resource to check
	 * @return	true if the given FHIR resource is empty or null, false otherwise
	 */
	default boolean isEmpty(Resource resource) {
		return resource == null || resource.isEmpty();
	}
	/**
	 * Returns true if the given FHIR type is empty or null
	 * @param fhirType	The FHIR datatype to check
	 * @return	true if the given FHIR type is empty or null, false otherwise
	 */
	default boolean isEmpty(org.hl7.fhir.r4.model.Type fhirType) {
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
	 * 
	 * @param <T>	The FHIR type to check
	 * @param type	The type object
	 * @param action	The action to perform
	 */
	default <T extends org.hl7.fhir.r4.model.Type> void ifNotEmpty(T type, Consumer<T> action) {
		if (isNotEmpty(type)) {
			action.accept(type);
		}
	}
	
	/**
	 * Add a DataAbsent reason to a FHIR primitive type
	 * @param element	The FHIR type to set as absent
	 * @param reason	The reason the data is absent
	 */
	default void setDataAbsentReason(PrimitiveType<?> element, String reason) {
		element.addExtension()
			.setUrl("http://hl7.org/fhir/StructureDefinition/data-absent-reason")
			.setValue(new CodeType(reason));
	}
	/**
	 * Add a DataAbsent reason of "unknown" to a FHIR primitive type
	 * This is used when data is not provided within a V2 message.
	 * 
	 * @param element	The FHIR type to set as absent
	 * @param reason	The reason the data is absent
	 */
	default void setDataAbsent(PrimitiveType<?> element) {
		setDataAbsentReason(element, "unknown");
	}
	
	/**
	 * Set a Data Absent Reason extension on a primitive type based on coding from
	 * the NullFlavor vocabulary.
	 * @param type	The type to add the extension to
	 * @param code	The Null Flavor code
	 */
	default void setDataAbsentFromNullFlavor(PrimitiveType<?> type, String code) {
		switch (code) {
		case "NI":	setDataAbsent(type); break;
		case "INV":	setDataAbsentReason(type, "not-applicable"); break;
		case "DER": setDataAbsent(type); break;
		case "OTH": setDataAbsentReason(type, "unsupported"); break;
		case "MSK": setDataAbsentReason(type, "masked"); break;
		case "NA":  setDataAbsentReason(type, "not-applicable"); break;
		case "UNK": setDataAbsentReason(type, "unknown"); break;
		case "NASK":setDataAbsentReason(type, "not-asked"); break;
		case "NAV": setDataAbsentReason(type, "temp-unknown"); break;
		case "NAVU":setDataAbsentReason(type, "unknown"); break;
		case "NP":  setDataAbsentReason(type, "asked-unknown"); break;
		default: 
			break;
		}
	}
	
	/**
	 * Get the first occurrence of the specified field from an HL7 V2 segment.
	 * @param segment	The segment to get the field for.
	 * 
	 * This is a convenience method to get a field that does not throw exceptions
	 * the way that segment.getField() does.
	 * 
	 * @param field	The field number
	 * @return	The specified field, or null of the field does not exist or is empty.
	 */
	default Type getField(Segment segment, int field) {
		if (segment == null) {
			return null;
		}
		try {
			Type[] types = segment.getField(field); 
			if (types.length == 0) {
				return null;
			}
			return types[0].isEmpty() ? null : types[0];
		} catch (HL7Exception e) {
			return null;
		}
	}

	/**
	 * Get all occurrences of the specified field from an HL7 V2 segment.
	 * @param segment	The segment to get the field for.
	 * 
	 * This is a convenience method to get a field that does not throw exceptions
	 * the way that segment.getField() does.
	 * 
	 * @param field	The field number
	 * @return	An array containing all occurrences of the specified field. 
	 */
	default Type[] getFields(Segment segment, int field) {
		if (segment == null) {
			return new Type[0];
		}
		try {
			return segment.getField(field);
		} catch (HL7Exception e) {
			return new Type[0];
		}
	}
		
	/** 
	 * Convenience method to add a single field to a resource
	 * 
	 * 	Use:
	 * <pre>
	 * 		addField(pid, 4, patient, Identifier.class, patient::addIdentifier);
	 * </pre>
	 *  Instead of:
	 *  <pre>
	 *  	Type t = getField(pid, 4);
	 *  	Identifier ident = DatatypeConverter.toIdentifier(t);
	 *  	if (ident != null && !ident.isEmpty()) {
	 *  		patient.addIdentifier(ident);
	 *  	}
	 *  </pre>
	 * @param <F>	The FHIR type to add
	 * @param pid	The segment from which the value comes
	 * @param fieldNo	The field from which the value comes
	 * @param clazz	The datatype to create from the field
	 * @param adder	The adder function
	 */
	default <F extends org.hl7.fhir.r4.model.Type> 
		void addField(Segment pid, int fieldNo, Class<F> clazz, Consumer<F> adder
	) {
		ifNotEmpty(DatatypeConverter.convert(clazz, getField(pid, fieldNo)), adder);
	}
	
	/** 
	 * Convenience method to add a single field to a resource
	 * 
	 * 	Use:
	 * <pre>
	 * 		addField(pid, 7, patient, DateTimeType.class, this::addBirthDateTime);
	 * </pre>
	 *  Instead of:
	 *  <pre>
	 *  	Type t = getField(pid, 7);
	 *  	DateTimeType dt = DatatypeConverter.toDateTimeType(t);
	 *  	if (dt != null && !dt.isEmpty()) {
	 *  		this.addBirthDateTime(patient, dt);
	 *  	}
	 *  </pre>
	 * @param <F>	The FHIR type to add
	 * @param <R>	The Resource to add it to
	 * @param pid	The segment from which the value comes
	 * @param fieldNo	The field from which the value comes
	 * @param resource	The resource to add to
	 * @param clazz	The datatype to create from the field
	 * @param adder	The adder function
	 */
	default <R extends org.hl7.fhir.r4.model.Resource, F extends org.hl7.fhir.r4.model.Type> void addField(Segment pid, int fieldNo, R resource, Class<F> clazz, BiConsumer<R, F> adder) {
		ifNotEmpty(DatatypeConverter.convert(clazz, getField(pid, fieldNo)),  t -> adder.accept(resource, t));
	}

	/** 
	 * Convenience method to add a multiple fields to a resource
	 * 
	 * Use:
	 * <pre>
	 * 		addFields(pid, 11, patient, Address.class, patient::addAddress);
	 * </pre>
	 * 
	 * Instead of:
	 *  <pre>
	 *  	Type[] types = getFields(pid, 11);
	 *  	for (Type t: types) {
	 *  		Address addr = DatatypeConverter.toAddress(t);
	 *  		if (addr != null && !addr.isEmpty()) {
	 *  			patient.addAddress(ident);
	 *  		}
	 *  	}
	 *  </pre>
	 * @param <F>	The FHIR type to add
	 * @param pid	The segment from which the value comes
	 * @param fieldNo	The field from which the value comes
	 * @param clazz	The datatype to create from the field
	 * @param adder	The adder function
	 */
	default <F extends org.hl7.fhir.r4.model.Type> void addFields(Segment pid, int fieldNo, Class<F> clazz, Consumer<F> adder) {
		for (Type type: getFields(pid, fieldNo)) {
			ifNotEmpty(DatatypeConverter.convert(clazz, type), adder);
		}
	}

	/** 
	 * Convenience method to add a multiple fields to a resource
	 * 
	 * Use:
	 * <pre>
	 * 		addFields(pid, 10, patient, CodeableConcept.class, this::addRace);
	 * </pre>
	 * 
	 * Instead of:
	 * <pre>
	 *  	Type[] types = getField(pid, 10);
	 *  	for (Type t: types) {
	 *  		CodeableConcept cc = DatatypeConverter.toCodeableConcept(t);
	 *  		if (cc != null && !cc.isEmpty()) {
	 *  			this.addRace(patient, cc);
	 *  		}
	 *  	}
	 * </pre>
	 * 
	 * @param <F>	The FHIR type to add
	 * @param pid	The segment from which the value comes
	 * @param fieldNo	The field from which the value comes
	 * @param patient	The resource to add to
	 * @param clazz	The datatype to create from the field
	 * @param adder	The adder function
	 */
	default <R extends org.hl7.fhir.r4.model.Resource, F extends org.hl7.fhir.r4.model.Type> 
		void addFields(Segment pid, int fieldNo, R patient, Class<F> clazz, BiConsumer<R, F> adder
	) {
		for (Type type: getFields(pid, fieldNo)) {
			ifNotEmpty(DatatypeConverter.convert(clazz, type), t -> adder.accept(patient, t));
		}
	}	
}
