package gov.cdc.izgw.v2tofhir.segment;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Segment;
import ca.uhn.hl7v2.model.Structure;
import gov.cdc.izgw.v2tofhir.converter.MessageParser;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;


/**
 * A SegmentParser parses segments of the specified type.
 * 
 * @author Audacious Inquiry
 */
@Data
@EqualsAndHashCode(callSuper=true)
@Slf4j
public abstract class AbstractSegmentParser extends AbstractStructureParser {
	/**
	 * Construct a segment parser for the specified message parser and segment type
	 * @param p	The message parser
	 * @param s	The segment type name
	 */
	AbstractSegmentParser(MessageParser p, String s) {
		super(p, s);
	}
	
	@Override
	public void parse(Structure seg) throws HL7Exception {
		if (seg instanceof Segment s) {
			parse(s);
		}
	}
	
	/**
	 * Parse a segment for a message.
	 * This method will be called by MessageParser for each segment of the given type that
	 * appears within the method.
	 * 
	 * @param seg	The segment to be parsed
	 * @throws HL7Exception	When an HL7Exception occurs.
	 */
	public abstract void parse(Segment seg) throws HL7Exception;

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
		
}
