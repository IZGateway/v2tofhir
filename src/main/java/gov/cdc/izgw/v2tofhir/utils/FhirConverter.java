package gov.cdc.izgw.v2tofhir.utils;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.lang.reflect.Modifier;
import java.util.List;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.Binary;
import org.hl7.fhir.r4.model.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;

import ca.uhn.fhir.parser.IParser;
import lombok.extern.slf4j.Slf4j;

/**
 * This is a converter to and from FHIR for SpringBoot applications that are
 * not using the HAPI on FHIR native web server.
 */
@Slf4j
public class FhirConverter implements HttpMessageConverter<Resource> {
	@Override
	public boolean canRead(Class<?> clazz, MediaType mediaType) {
		mediaType = mediaType == null ? null : new MediaType(StringUtils.lowerCase(mediaType.getType()), StringUtils.lowerCase(mediaType.getSubtype()));
		return Resource.class.isAssignableFrom(clazz) && (mediaType == null || ContentUtils.FHIR_MEDIA_TYPES.contains(mediaType));
	}

	@Override
	public boolean canWrite(Class<?> clazz, MediaType mediaType) {
		String subtype = mediaType != null ? StringUtils.lowerCase(mediaType.getSubtype()) : "";
		if (Binary.class.isAssignableFrom(clazz) && mediaType != null && (subtype.contains("cda") || subtype.contains("hl7v2"))) {
			return true;
		}
		return canRead(clazz, mediaType);
	}

	@Override
	public List<MediaType> getSupportedMediaTypes() {
		return ContentUtils.HL7_MEDIA_TYPES;
	}

	@Override
	public Resource read(Class<? extends Resource> clazz, HttpInputMessage inputMessage)
			throws IOException, HttpMessageNotReadableException {
		
		IParser parser = null;
		String contentType = inputMessage.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE);
		MediaType mediaType = null;
		
		BufferedInputStream bis = IOUtils.buffer(inputMessage.getBody());
		if (StringUtils.isBlank(contentType)) {
			mediaType = ContentUtils.guessMediaType(bis);
		} else {
			try {
				mediaType = MediaType.parseMediaType(contentType);
				// Simplify it, stripping any parameters such as charset.
				mediaType = new MediaType(mediaType.getType(), mediaType.getSubtype());
			} catch (InvalidMediaTypeException e) {
				log.warn("Unparseable Content-Type '{}', guessing media type from content", contentType, e);
				mediaType = ContentUtils.guessMediaType(bis);
			}
		}
		parser = ContentUtils.selectParser(mediaType);
		// HAPI's typed parseResource(Class, ...) scans the target class and rejects
		// abstract classes / interfaces (e.g. org.hl7.fhir.r4.model.Resource) with
		// "HAPI-1682: Can not scan abstract or interface class". Spring passes the
		// *declared* @RequestBody type, so a handler declaring an abstract type such
		// as Resource fails here before the controller runs. When the requested type
		// is abstract/interface, use the auto-detecting overload, which reads
		// resourceType from the payload and resolves the concrete class.
		if (Modifier.isAbstract(clazz.getModifiers()) || clazz.isInterface()) {
			Resource parsed = (Resource) parser.parseResource(bis);
			if (!clazz.isInstance(parsed)) {
				throw new HttpMessageNotReadableException(
					"Parsed resource type '" + parsed.fhirType()
						+ "' is not assignable to requested type " + clazz.getName(),
					inputMessage);
			}
			return parsed;
		}
		return parser.parseResource(clazz, bis);
	}

	@Override
	public void write(Resource resource, MediaType contentType, HttpOutputMessage outputMessage)
			throws IOException, HttpMessageNotWritableException {
		if (resource instanceof Binary b && ContentUtils.FHIR_MEDIA_TYPES.stream().noneMatch(t -> t.includes(contentType))) {
			writeBinary(b, outputMessage);
			return;
		}
		IParser parser = ContentUtils.selectParser(contentType);
		OutputStreamWriter w = null;
		try {  // NOSONAR: It's up to the caller to determine whether the body gets closed when this is done.
			w = new OutputStreamWriter(outputMessage.getBody());
			parser.encodeResourceToWriter(resource, w);
		} finally {
			if (w != null) {
				w.flush();
			}
		}
	}

	private void writeBinary(Binary b, HttpOutputMessage outputMessage) throws IOException {
		OutputStreamWriter w = null;
		try {  // NOSONAR: It's up to the caller to determine whether the body gets closed when this is done.
			w = new OutputStreamWriter(outputMessage.getBody());
			for (byte data: b.getData()) {
				w.write((char)data);
			}
		} finally {
			if (w != null) {
				w.flush();
			}
		}
		
	}
}
