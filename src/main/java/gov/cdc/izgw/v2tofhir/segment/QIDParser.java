package gov.cdc.izgw.v2tofhir.segment;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.codec.binary.StringUtils;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.StringType;

import ca.uhn.hl7v2.model.Segment;
import gov.cdc.izgw.v2tofhir.annotation.Produces;
import gov.cdc.izgw.v2tofhir.converter.DatatypeConverter;
import gov.cdc.izgw.v2tofhir.converter.MessageParser;
import gov.cdc.izgw.v2tofhir.utils.ParserUtils;
import lombok.extern.slf4j.Slf4j;

/**
 * The QID Parser extracts the query tag and query name from the QID segment
 * and attaches them to the Parameters resource.
 *
 * @author Audacious Inquiry
 *
 */
@Produces(segment="QID", resource=Parameters.class)
@Slf4j
public class QIDParser extends AbstractSegmentParser {
	private Parameters params;
	private static List<FieldHandler> fieldHandlers = new ArrayList<>();
	static {
		log.debug("{} loaded", RCPParser.class.getName());
	}
	/**
	 * Construct a new QPD Parser for the given messageParser.
	 * 
	 * @param messageParser The messageParser using this QPDParser
	 */
	public QIDParser(MessageParser messageParser) {
		super(messageParser, "QID");
		if (fieldHandlers.isEmpty()) {
			initFieldHandlers(this, fieldHandlers);
		}
	}
	
	@Override
	public List<FieldHandler> getFieldHandlers() {
		return fieldHandlers;
	}
	
	public IBaseResource setup() {
		params = createResource(Parameters.class);
		return params;
	}
	
	@Override
	public void parse(Segment qid) {
		setup();
		String queryTag = ParserUtils.toString(getField(qid, 1));
		Coding queryName = DatatypeConverter.toCoding(getField(qid, 2));
		params.addParameter("QueryTag", queryTag);
		params.addParameter("QueryName", queryName);
	}
}
