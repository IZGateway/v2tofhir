package gov.cdc.izgw.v2tofhir.converter;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.Attachment;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.DocumentReference;
import org.hl7.fhir.r4.model.Enumerations.DocumentReferenceStatus;
import org.hl7.fhir.r4.model.StringType;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.GenericSegment;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.model.Segment;
import ca.uhn.hl7v2.model.Structure;
import ca.uhn.hl7v2.model.Type;
import ca.uhn.hl7v2.parser.EncodingCharacters;
import ca.uhn.hl7v2.parser.PipeParser;
import gov.cdc.izgw.v2tofhir.segment.ERRParser;
import gov.cdc.izgw.v2tofhir.utils.ErrorReporter;
import gov.cdc.izgw.v2tofhir.utils.ParserUtils;
import lombok.extern.slf4j.Slf4j;

/**
 * MessageParser provide the necessary methods to convert messages and segments into FHIR Bundles and Resources
 * respectively.
 * 
 * The methods in any instance this class are not thread safe, but multiple threads can perform conversions
 * on different messages by using different instances of the MessageParser. The MessageParser class is 
 * intentionally cheap to create.
 * 
 * Parsers and Converters in this package follow as best as possible the mapping advice provided
 * in the HL7 V2 to FHIR Implementation Guide.
 * 
 * @see <a href="https://hl7.org/fhir/uv/v2mappings/2024Jan/">V2toFHIR Jan-2024</a>
 * 
 * @author Audacious Inquiry
 */
@Slf4j
public class MessageParser extends BaseParser<Message,Structure> implements Parser<Message,Structure> {
	static {
		BaseParser.packageSources.add(ERRParser.class.getPackageName());
		String packages = System.getenv("V2TOFHIR_PARSER_PACKAGES");
		if (StringUtils.isNotEmpty(packages)) {
			Arrays.asList(packages.split("[,;\s]+")).forEach(p -> BaseParser.packageSources.add(p));
		}
	}
	
	/**
	 * Default constructor which sets this instance as the ErrorReporter for any static methods
	 */
	public MessageParser() {
		ErrorReporter.set(this);
	}
	
	/**
	 * Get a PipeParser for parsing HL7 V2 messages.
	 * Override this method to provide a custom parser (e.g., with custom validation).
	 * @return A PipeParser
	 */
	public PipeParser getParser() {
		return new PipeParser();
	}
	
	/**
	 * Convert the hl7Message to a new Message using a PipeParser and then convert it.
	 * @param hl7Message	The HL7 Message
	 * @return The converted bundle
	 * @throws HL7Exception if an error occurred while parsing.
	 */
	@Override
	public Bundle convert(String hl7Message) throws HL7Exception {
    	return convert(getParser().parse(hl7Message)); 
	}
	
	/**
	 * Convert an HL7 V2 message into a Bundle of FHIR Resources.
	 * 
	 * The message is iterated over to the segment level, and each segment is then converted
	 * into one or more resources, possibly combining information from multiple segments (e.g., PID, PD1 and PV1)
	 * in any of the returned resource.
	 * 
	 * The bundle will also contain provenance resources which show the linkage between the converted segments and
	 * their resulting resources, where each provenance record ties the data in a converted segment to the resource.
	 * 
	 * @param msg The message to convert.
	 * @return A FHIR Bundle containing the relevant resources. 
	 */
	public Bundle convert(Message msg) {
		reset();
		try {
			initContext((Segment) msg.get("MSH"));
		} catch (HL7Exception e) {
			warn("Cannot retrieve MSH segment from message");
		}
		String encoded = null;
		byte[] data = null;
		try {
			encoded = msg.encode();
			data = encoded.getBytes(StandardCharsets.UTF_8);
		} catch (HL7Exception e) {
			warn("Could not encode the message: {}", e.getMessage(), e);
		}
		DocumentReference dr = createResource(DocumentReference.class);
		dr.setUserData(Parser.SOURCE, MessageParser.class.getName()); // Mark infrastructure created resources
		dr.setStatus(DocumentReferenceStatus.CURRENT);
		// See https://confluence.hl7.org/display/V2MG/HL7+locally+registered+V2+Media+Types
		Attachment att = dr.addContent().getAttachment().setContentType("text/hl7v2; charset=utf-8");
		att.setData(data);

		// Add the encoded message as the original text for the content in both the binary
		// and document reference resources.
		if (encoded != null) {
			StringType e = new StringType(encoded);
			dr.getContentFirstRep().addExtension(BaseParser.ORIGINAL_TEXT, e);
		}

		return createBundle(msg);
	}
	
	private void initContext(Segment msh) {
		getContext().clear();
		getBundle();
		if (msh != null) {
			try {
				getContext().setEventCode(ParserUtils.toString(msh.getField(9, 0)));
			} catch (HL7Exception e) {
				warn("Unexpected HL7Exception parsing MSH-9: {}", e.getMessage());
			}
			try {
				for (Type profile : msh.getField(21)) {
					getContext().addProfileId(ParserUtils.toString(profile));
				}
			} catch (HL7Exception e) {
				warn("Unexpected HL7Exception parsing MSH-21: {}", e.getMessage());
			}
		}
	}
	
	@Override
	protected Iterable<Structure> getParts(Message msg) {
		Set<Structure> segments = new LinkedHashSet<>();
		ParserUtils.iterateStructures(msg, segments);
		return segments;
	}
	
	

	@Override
	public String encodeUnit(Message unit) {
		try {
			return unit.encode();
		} catch (HL7Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return "";
		}
	}

	@Override
	public String encode(Structure subunit) {
		if (subunit instanceof GenericSegment generic) {
			PipeParser parser = PipeParser.getInstanceWithNoValidation();
			return parser.doEncode(generic, EncodingCharacters.defaultInstance());
		} else if (subunit instanceof Segment segment) {
			try {
				return segment.encode();
			} catch (HL7Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				return "";
			}
		}
		// TODO: What do we do here?
		return "";
	}
	
	@Override
	protected String getName(Structure structure) {
		return structure.getName();
	}

	@Override
	public void warn(String message, Object... args) {
		ErrorReporter.get().warn(message, args);
	}
}
