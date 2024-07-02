package gov.cdc.izgw.v2tofhir.converter.segment;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Segment;
import ca.uhn.hl7v2.model.Structure;
import gov.cdc.izgw.v2tofhir.converter.MessageParser;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Data
@Slf4j
public abstract class AbstractSegmentParser extends AbstractStructureParser {
	AbstractSegmentParser(MessageParser p, String s) {
		super(p, s);
	}
	
	@Override
	public void parse(Structure seg) throws HL7Exception {
		if (seg instanceof Segment s) {
			parse(s);
		}
	}
	
	public abstract void parse(Segment seg) throws HL7Exception;

	void warn(String msg, Object ...args) {
		log.warn(msg, args);
	}

	void warnException(String msg, Object ...args) {
		log.warn(msg, args);
	}
		
}
