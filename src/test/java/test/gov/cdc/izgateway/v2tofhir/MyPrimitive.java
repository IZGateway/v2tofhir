package test.gov.cdc.izgateway.v2tofhir;

import ca.uhn.hl7v2.model.AbstractPrimitive;
import ca.uhn.hl7v2.model.DataTypeException;

/* 
 * A basic primitive type that can be used for different kinds of conversion testing.
 * AbstractPrimitive is the base type for HAPI V2.  NOTE: class names are used in HAPI V2
 * as type names as well, and the V2 to FHIR converter knows this about HAPI V2, so if you
 * want to use this type for a certain BASE type, extend from it and use the V2 type name.   
 */
class MyPrimitive extends AbstractPrimitive {
	private static final long serialVersionUID = 1L;
	public MyPrimitive() {
		super(null);
	}
	public MyPrimitive(String value) throws DataTypeException {
		this();
		setValue(value);
	}
}