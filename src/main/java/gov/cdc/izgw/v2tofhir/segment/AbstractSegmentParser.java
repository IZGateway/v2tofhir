package gov.cdc.izgw.v2tofhir.segment;

import java.util.List;

import org.hl7.fhir.instance.model.api.IBase;
import org.hl7.fhir.instance.model.api.IBaseResource;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.model.Segment;
import ca.uhn.hl7v2.model.Structure;
import gov.cdc.izgw.v2tofhir.converter.MessageParser;
import gov.cdc.izgw.v2tofhir.converter.Parser;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;


/**
 * A SegmentParser parses segments of the specified type.
 * 
 * @author Audacious Inquiry
 */
@Slf4j
public abstract class AbstractSegmentParser extends AbstractStructureParser implements Processor<Message, Segment> {
	private final Parser<Message, Structure> parser;
	@Getter
	private final MessageParser messageParser;
	/**
	 * Construct a segment parser for the specified message parser and segment type
	 * @param p	The message parser
	 * @param s	The segment type name
	 */
	protected AbstractSegmentParser(MessageParser p, String s) {
		super(p, s);
		parser = messageParser = p;
	}
	
	@Override
	public boolean isEmpty(Segment segment) { // NOSONAR (Necessary for Inheritance)
		return super.isEmpty(segment);
	}
	
	/**
	 * Annotation driven parsing.  Call this method to use parsing driven
	 * by ComesFrom and Produces annotations in the parser.
	 * 
	 * This method will be called by MessageParser for each segment of the given type that
	 * appears within the method.  It will create the primary resource produced by the
	 * parser (by calling the setup() method) and then parses individual fields of the 
	 * segment and passes them to parser methods to add them to the primary resource or 
	 * to create any extra resources.
	 * 
	 * @param segment The segment to be parsed
	 */
	@Override
	public IBase parse(Segment segment) {
		if (isEmpty(segment)) {
			return null;
		}

		super.segment = segment;
		IBaseResource r = setup();
		if (r == null) {
			// setup() returned nothing, there must be nothing to do
			return null;
		}
		List<FieldHandler> handlers = getFieldHandlers();
		for (FieldHandler fieldHandler : handlers) {
			fieldHandler.handle(this, segment, r);
		}
		return r;
	}
	

	@SuppressWarnings("unchecked")
	@Override
	public Parser<Message, Segment> getParser() {
		@SuppressWarnings("rawtypes")
		Parser p = parser;
		return (Parser<Message, Segment>)p;
	}
}
