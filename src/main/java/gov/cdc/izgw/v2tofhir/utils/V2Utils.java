package gov.cdc.izgw.v2tofhir.utils;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Segment;
import ca.uhn.hl7v2.model.Type;
import gov.cdc.izgw.v2tofhir.converter.DatatypeConverter;

/**
 * Utilities for manipulating V2 messages for FHIR Conversion
 * 
 * @author Audacious Inquiry
 */
public class V2Utils {

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
	 *  	if (ident != null &amp;&amp; !ident.isEmpty()) {
	 *  		patient.addIdentifier(ident);
	 *  	}
	 *  </pre>
	 * @param <F>	The FHIR type to add
	 * @param pid	The segment from which the value comes
	 * @param fieldNo	The field from which the value comes
	 * @param clazz	The datatype to create from the field
	 * @param adder	The adder function
	 */
	public static <F extends org.hl7.fhir.r4.model.Type> 
		void addField(Segment pid, int fieldNo, Class<F> clazz, Consumer<F> adder
	) {
		FhirUtils.ifNotEmpty(DatatypeConverter.convert(clazz, getField(pid, fieldNo), null), adder);
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
	 *  	if (dt != null &amp;&amp; !dt.isEmpty()) {
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
	public static <R extends org.hl7.fhir.r4.model.Resource, F extends org.hl7.fhir.r4.model.Type> 
	void addField(Segment pid, int fieldNo, Class<F> clazz, R resource, BiConsumer<R, F> adder) {
		FhirUtils.ifNotEmpty(DatatypeConverter.convert(clazz, getField(pid, fieldNo), null),  t -> adder.accept(resource, t));
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
	 *  		if (addr != null &amp;&amp; !addr.isEmpty()) {
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
	public static <F extends org.hl7.fhir.r4.model.Type> void addFields(Segment pid, int fieldNo, Class<F> clazz, Consumer<F> adder) {
		for (Type type: getFields(pid, fieldNo)) {
			FhirUtils.ifNotEmpty(DatatypeConverter.convert(clazz, type, null), adder);
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
	 *  		if (cc != null &amp;&amp; !cc.isEmpty()) {
	 *  			this.addRace(patient, cc);
	 *  		}
	 *  	}
	 * </pre>
	 * 
	 * @param <R>	The FHIR Resource to add
	 * @param <F>	The FHIR type to add
	 * @param pid	The segment from which the value comes
	 * @param fieldNo	The field from which the value comes
	 * @param patient	The resource to add to
	 * @param clazz	The datatype to create from the field
	 * @param adder	The adder function
	 */
	public static <R extends org.hl7.fhir.r4.model.Resource, F extends org.hl7.fhir.r4.model.Type> 
		void addFields(Segment pid, int fieldNo, Class<F> clazz, R patient, BiConsumer<R, F> adder
	) {
		for (Type type: getFields(pid, fieldNo)) {
			FhirUtils.ifNotEmpty(DatatypeConverter.convert(clazz, type, null), t -> adder.accept(patient, t));
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
	public static Type getField(Segment segment, int field) {
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
	public static Type[] getFields(Segment segment, int field) {
		if (segment == null) {
			return new Type[0];
		}
		try {
			return segment.getField(field);
		} catch (HL7Exception e) {
			return new Type[0];
		}
	}

}
