package gov.cdc.izgw.v2tofhir.utils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.ainq.fhir.utils.YamlParser;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Utilities for Content Negotiation of FHIR Responses
 * @author Audacious Inquiry
 */
public class ContentUtils {
	static final boolean PRETTY = true;
	/** Unicode Byte Order Mark is NOT considered to be whitespace */
	public static final char BOM = '\uFEFF';
	static final FhirContext R4 = FhirContext.forR4();
	/** FHIR R4 Parser for JSON Content */
	public static final IParser FHIR_JSON_PARSER = ContentUtils.R4.newJsonParser().setPrettyPrint(ContentUtils.PRETTY);
	/** Mime Type for FHIR in JSON format */
	public static final String FHIR_PLUS_JSON_VALUE = "application/fhir+json";
	/** Mime Type for FHIR in XML format */
	public static final String FHIR_PLUS_XML_VALUE = "application/fhir+xml";
	/** Mime Type for FHIR in YAML format */
	public static final String FHIR_PLUS_YAML_VALUE = "application/fhir+yaml";
	/** Mime Type for YAML format */
	public static final String YAML_VALUE = "application/yaml";
	
	/** Media Type for FHIR in JSON format */
	public static final MediaType FHIR_PLUS_JSON = parseMediaType(FHIR_PLUS_JSON_VALUE);
	/** Media Type for FHIR in XML format */
	public static final MediaType FHIR_PLUS_XML = parseMediaType(FHIR_PLUS_XML_VALUE);
	/** Media Type for FHIR in YAML format */
	public static final MediaType FHIR_PLUS_YAML = parseMediaType(FHIR_PLUS_YAML_VALUE);
	/** Media Type for YAML format */
	public static final MediaType YAML = parseMediaType(YAML_VALUE);
	/** Media Type for CDA Format */
	public static final String CDA_VALUE = "application/cda+xml";
	/** Media Type for HL7 V2 ER7 Format */
	public static final String HL7V2_TEXT_VALUE = "text/hl7v2";
	/** Media Type for HL7 V2 XML Format */
	public static final String HL7V2_XML_VALUE = "application/hl7v2+xml";
	
	/** FHIR R4 Parser for XML Content */
	public static final IParser FHIR_XML_PARSER = ContentUtils.R4.newXmlParser().setPrettyPrint(ContentUtils.PRETTY);
	/** FHIR R4 Parser for YAML Content */
	public static final IParser FHIR_YAML_PARSER = new YamlParser(ContentUtils.R4).setPrettyPrint(ContentUtils.PRETTY);
	/** Supported FHIR Media Types */
	protected static final List<MediaType> FHIR_MEDIA_TYPES = Arrays.asList(
		FHIR_PLUS_JSON,
		FHIR_PLUS_XML,
		FHIR_PLUS_YAML,
		YAML,
		MediaType.APPLICATION_JSON,
		MediaType.APPLICATION_XML,
		MediaType.TEXT_XML
	);
	protected static final List<MediaType> HL7_MEDIA_TYPES = Arrays.asList(
		FHIR_PLUS_JSON,
		FHIR_PLUS_XML,
		FHIR_PLUS_YAML,
		YAML,
		MediaType.APPLICATION_JSON,
		MediaType.APPLICATION_XML,
		MediaType.TEXT_XML,
		MediaType.valueOf(HL7V2_TEXT_VALUE),
		MediaType.valueOf(HL7V2_XML_VALUE),
		MediaType.valueOf(CDA_VALUE)
	);

	private ContentUtils() {}

	/**
	 * Compare two mime types by Quality
	 * @param m1	First Mime Type
	 * @param m2	Second Mime Type
	 * @return The comparison result
	 */
	public static int compareByQvalue(String m1, String m2) {
		String q1 = StringUtils.defaultIfEmpty(StringUtils.substringAfter(m1, "q="),"1");
		String q2 = StringUtils.defaultIfEmpty(StringUtils.substringAfter(m2, "q="),"1");
		return q2.compareTo(q1);
	}

	/**
	 * This enables content negotiation for the FhirController
	 * @param req	The HttpServletRequest used to determine acceptable content types
	 * @return	An HttpHeaders with the Content-Type header set appropriately.
	 */
	public static HttpHeaders getHeaders(HttpServletRequest req) {
		HttpHeaders h = new HttpHeaders();
		String accept = req.getParameter("_format");
		if (accept == null) {
			accept = req.getHeader(HttpHeaders.ACCEPT);
		}
		String contentType = null;
		if (accept == null || "json".equals(accept)) {
			contentType = MediaType.APPLICATION_JSON_VALUE;
		} else if ("xml".equals(accept)) {	
			contentType = MediaType.APPLICATION_XML_VALUE;
		} else if ("yaml".equals(accept)) {	
			contentType = ContentUtils.YAML_VALUE;
		} else {
			String[] types = accept.toLowerCase().split(",");
			Arrays.sort(types, ContentUtils::compareByQvalue);
			for (String type: types) {
				String t = StringUtils.substringBefore(type, ";");
				MediaType match = 
						FHIR_MEDIA_TYPES
							.stream()
							.filter(m -> t.startsWith(m.toString()))
							.findFirst().orElse(null);
				if (match != null) {
					contentType = match.toString();
					break;
				}
			}
			if (contentType == null) {
				contentType = "application/json";
			}
		}
		h.add(HttpHeaders.CONTENT_TYPE, contentType);
		return h;
	}

	/**
	 * This enables content negotiation for the ModernizationController
	 * @param req	The HttpServletRequest used to determine acceptable content types
	 * @return	An HttpHeaders with the Content-Type header set appropriately.
	 */
	public static HttpHeaders getHeaders2(HttpServletRequest req) {
		HttpHeaders h = new HttpHeaders();
		String accept = req.getParameter("_format");
		if (accept == null) {
			accept = req.getHeader(HttpHeaders.ACCEPT);
		}
		accept = accept.toLowerCase();
		h.remove(HttpHeaders.ACCEPT);
		String contentType = null;
		if (accept == null || accept.contains("json")) {
			contentType = ContentUtils.FHIR_PLUS_JSON_VALUE;
		} else if (accept.contains("hl7v2+xml") || accept.contains("hl7v2 xml")) {  // Second option addresses inadvertent use of + in URL parameter
			contentType = ContentUtils.HL7V2_XML_VALUE;
		} else if (accept.contains("xml")) {	
			contentType = ContentUtils.FHIR_PLUS_XML_VALUE;
		} else if (accept.contains("yaml")) {	
			contentType = ContentUtils.FHIR_PLUS_YAML_VALUE;
		} else if (accept.contains("cda")) {
			contentType = ContentUtils.CDA_VALUE;
		} else if (accept.contains("hl7v2")) {
			contentType = ContentUtils.HL7V2_TEXT_VALUE;
		} else {
			String[] types = accept.toLowerCase().split(",");
			Arrays.sort(types, ContentUtils::compareByQvalue);
			for (String type: types) {
				String t = StringUtils.substringBefore(type, ";");
				MediaType match = 
						FHIR_MEDIA_TYPES
							.stream()
							.filter(m -> t.startsWith(m.toString()))
							.findFirst().orElse(null);
				if (match != null) {
					contentType = match.toString();
					break;
				}
			}
			if (contentType == null) {
				contentType = ContentUtils.FHIR_PLUS_JSON_VALUE;
			}
		}
		h.add(HttpHeaders.ACCEPT, contentType);
		return h;
	}
	/**
	 * Guess the media type of an input stream and return it.
	 * @param inputStream	The input stream (must be a buffered input stream 
	 * to address the issue of read-ahead.
	 * @return	The best guess at the media type for this resource.
	 * @throws IOException	If an IO Error occurs.
	 */
	public static MediaType guessMediaType(InputStream inputStream) throws IOException {
		inputStream.mark(512);
		try {
			for (int i = 0; i < 512; i++) {
				int c = inputStream.read();
				if (c < 0) {
					break;
				} 
				if (c == '<') {
					return FHIR_PLUS_XML;
				} 
				if (c == '{') {
					return FHIR_PLUS_JSON;
				} 
				if (c == '-') {
					return FHIR_PLUS_YAML;
				} 
				if (!Character.isWhitespace(c) && c != ContentUtils.BOM) {
					// It's a good guess.
					return FHIR_PLUS_YAML;
				}
			}
		} finally {
			inputStream.reset();
		}
		return FHIR_PLUS_JSON;
	}

	/**
	 * Given a media type, return the appropriate parser for reading and generating it
	 * @param mediaType	The media type
	 * @return	A parser for reading and generating FHIR from th specified media type
	 */
	public static IParser selectParser(MediaType mediaType) {
		// Simplify the media type.
		mediaType = new MediaType(mediaType.getType(), mediaType.getSubtype());
		// Convert to string for switch
		String mimeType = StringUtils.defaultString(mediaType.toString());
		switch (mimeType) {
		case FHIR_PLUS_XML_VALUE, MediaType.APPLICATION_XML_VALUE, MediaType.TEXT_XML_VALUE:
			return FHIR_XML_PARSER;
			
		case FHIR_PLUS_YAML_VALUE, YAML_VALUE:
			return FHIR_YAML_PARSER;
			
		case FHIR_PLUS_JSON_VALUE, MediaType.APPLICATION_JSON_VALUE:
		default:
			return FHIR_JSON_PARSER;
		}
	}

	private static MediaType parseMediaType(String contentType) {
		String[] parts = contentType.split("/");
		return new MediaType(parts[0], parts[1]);
	}
}
