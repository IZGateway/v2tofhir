package gov.cdc.izgw.v2tofhir.segment;

import java.util.List;
import java.util.ServiceConfigurationError;

import org.hl7.fhir.r4.model.Type;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Segment;
import ca.uhn.hl7v2.model.Structure;
import gov.cdc.izgw.v2tofhir.converter.MessageParser;
import lombok.extern.slf4j.Slf4j;


/**
 * A SegmentParser parses segments of the specified type.
 * 
 * @author Audacious Inquiry
 */
@Slf4j
public abstract class AbstractSegmentParser extends AbstractStructureParser {
	/**
	 * Construct a segment parser for the specified message parser and segment type
	 * @param p	The message parser
	 * @param s	The segment type name
	 */
	protected AbstractSegmentParser(MessageParser p, String s) {
		super(p, s);
	}
	
	@Override
	public void parse(Structure seg) throws HL7Exception {
		if (seg instanceof Segment s) {
			parse(s);
		}
	}
	
	@Override
	public void parse(Segment segment) {
		if (isEmpty(segment)) {
			return;
		}
		
		if (getProduces() == null) {
			throw new ServiceConfigurationError(
				"Missing @Produces on " + this.getClass().getSimpleName()
			);
		}
		super.parse(segment);
	}

	/**
	 * Warn about a specific problem found while parsing.
	 * 
	 * @see warnException
	 * 
	 * @param msg	The message format to use for the warning.
	 * @param args	Arguments for the message.  The last argument should still be part of the message.
	 */
	void warn(String msg, Object ...args) {
		log.warn(msg, args);
	}

	/**
	 * Warn about an exception found while parsing.
	 * 
	 * @param msg	The message format to use for the warning.
	 * @param args	Arguments for the message. The last argument should be the exception found.
	 */
	void warnException(String msg, Object ...args) {
		log.warn(msg, args);
	}
	
	<T extends Type> T mapVia(T type, String mapName) {
		return type;
	}
	
	<T> T last(List<T> a) {
		return a.isEmpty() ? null : a.get(a.size()-1);
	}
		
}
