package gov.cdc.izgw.v2tofhir.converter;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.Location;
import ca.uhn.hl7v2.model.DataTypeException;
import ca.uhn.hl7v2.model.ExtraComponents;
import ca.uhn.hl7v2.model.GenericPrimitive;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.model.MessageVisitor;
import ca.uhn.hl7v2.model.Type;

/**
 * This class is used to give a Generic Primitive a name after
 * it has been parsed so that it can be processed by the V2toFHIR 
 * parser.
 * 
 * @author Audacious Inquiry
 */
public class MyGenericPrimitive extends GenericPrimitive implements Type {
	private static final long serialVersionUID = 1L;
	private final GenericPrimitive delegate;
	private final String name;

	/**
	 * Construct a MyGenericPrimitive from an existing GenericPrimitive. 
	 * @param delegate	The existing GenericPrimitive
	 * @param name The type name
	 */
	public MyGenericPrimitive(GenericPrimitive delegate, String name) {
		super(delegate.getMessage());
		this.delegate = delegate;
		this.name = name;
	}
	
	@Override
	public String getName() {
		return name;
	}

	@Override
	public String getVersion() {
		return delegate.getVersion();
	}

	@Override
	public String toString() {
		return delegate.toString();
	}

	@Override
	public String getValue() {
		return delegate.getValue();
	}

	@Override
	public void setValue(String theValue) throws DataTypeException {
		delegate.setValue(theValue);
	}

	@Override
	public String encode() throws HL7Exception {
		return delegate.encode();
	}

	@Override
	public void parse(String string) throws HL7Exception {
		delegate.parse(string);
	}

	@Override
	public void clear() {
		delegate.clear();
	}

	@Override
	public boolean isEmpty() throws HL7Exception {
		return delegate.isEmpty();
	}

	@Override
	public boolean accept(MessageVisitor visitor, Location location) throws HL7Exception {
		return delegate.accept(visitor, location);
	}

	@Override
	public ExtraComponents getExtraComponents() {
		return delegate.getExtraComponents();
	}

	@Override
	public Message getMessage() {
		return delegate.getMessage();
	}

	@Override
	public Location provideLocation(Location location, int index, int repetition) {
		return delegate.provideLocation(location, index, repetition);
	}

}
