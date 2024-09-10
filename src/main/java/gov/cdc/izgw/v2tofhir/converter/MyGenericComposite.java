package gov.cdc.izgw.v2tofhir.converter;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.Location;
import ca.uhn.hl7v2.model.ExtraComponents;
import ca.uhn.hl7v2.model.GenericComposite;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.model.MessageVisitor;
import ca.uhn.hl7v2.model.Type;

/**
 * 
 * This class enables a GenericComposite to be given a type name
 * after it has been parsed that allows it to be analyzed correctly
 * by V2toFHIR parser components.
 * 
 * @author Audacious Inquiry
 *
 */
class MyGenericComposite extends GenericComposite {
	private static final long serialVersionUID = 1L;
	private final GenericComposite delegate;
	private final String name;
	public MyGenericComposite(GenericComposite delegate, String name) {
		super(delegate.getMessage());
		this.delegate = delegate;
		this.name = name;
	}
	@Override
	public Type getComponent(int number) {
		return delegate.getComponent(number);
	}
	@Override
    public Type[] getComponents() {
    	return delegate.getComponents();
    }
	@Override
	public String getName() {
		return name;
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
	public Location provideLocation(Location location, int index, int repetition) {
		return delegate.provideLocation(location, index, repetition);
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
	public void parse(String string) throws HL7Exception {
		delegate.parse(string);
	}
	@Override
	public String encode() throws HL7Exception {
		return delegate.encode();
	}
	@Override
	public String toString() {
		return delegate.toString();
	}
}