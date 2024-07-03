package gov.cdc.izgw.v2tofhir.converter.segment;

import java.util.List;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Resource;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Segment;
import ca.uhn.hl7v2.model.Structure;
import ca.uhn.hl7v2.model.Type;
import gov.cdc.izgw.v2tofhir.converter.Context;
import gov.cdc.izgw.v2tofhir.converter.MessageParser;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Data
@Slf4j
/**
 * This is an abstract implementation for StructureParser instances used by the MessageParser.
 */
public abstract class AbstractStructureParser implements StructureParser {
	private final MessageParser messageParser;
	private final String segmentName;

	AbstractStructureParser(MessageParser messageParser, String segmentName) {
		this.messageParser = messageParser;
		this.segmentName = segmentName;
	}

	@Override
	public String segment() {
		return segmentName;
	}

	/**
	 * @return The parsing context for the current message or structure being parsed.
	 */
	public Context getContext() {
		return messageParser.getContext();
	}

	/**
	 * Get the bundle that is being prepared.
	 * This method can be used by parsers to get additional information from the Bundle
	 * @return The bundle that is being prepared during the parse.
	 */
	public Bundle getBundle() {
		return getContext().getBundle();
	}
	
	public <R extends Resource> R getFirstResource(Class<R> clazz) {
		R r = messageParser.getFirstResource(clazz);
		if (r == null) {
			log.warn("No {} has been created", clazz.getSimpleName());
		}
		return r;
	}
	public <R extends Resource> R getLastResource(Class<R> clazz) {
		return messageParser.getLastResource(clazz);
	}
	public <R extends Resource> R getResource(Class<R> clazz, String id) {
		return messageParser.getResource(clazz, id);
	}
	public <R extends Resource> List<R> getResources(Class<R> clazz) {
		return messageParser.getResources(clazz);
	}
	
	public <R extends Resource> R createResource(Class<R> clazz) {
		return messageParser.findResource(clazz, null);
	}
	
	public <R extends Resource> R findResource(Class<R> clazz, String id) {
		return messageParser.findResource(clazz, id);
	}

	public static Type getField(Segment segment, int field) {
		if (segment == null) {
			return null;
		}
		try {
			Type[] types = segment.getField(field); 
			if (types.length == 0) {
				return null;
			}
			return types[0].isEmpty() ? null : types[0];
		} catch (HL7Exception e) {
			return null;
		}
	}

	public static Type[] getFields(Segment segment, int field) {
		if (segment == null) {
			return new Type[0];
		}
		try {
			return segment.getField(field);
		} catch (HL7Exception e) {
			return new Type[0];
		}
	}
	
	public <T> T getProperty(Class<T> t) {
		return messageParser.getContext().getProperty(t);
	}
}
