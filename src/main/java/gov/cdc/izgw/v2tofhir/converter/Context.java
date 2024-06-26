package gov.cdc.izgw.v2tofhir.converter;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.Bundle;

import lombok.Data;

/* 
 * Keeps track of important decision making variables during the parse of an HL7 message.
 * 
 * Different parsing decisions may be made depending on the type of event. For
 * example, RXA segments may be parsed into the Immunization resource instead of
 * a MedicationAdministration resource when the message is being parsed for a 
 * VXU message or a QPD response to an immunization query.
 */
@Data
public class Context {
	/**
	 * The event that generated the message, used to provide context during parsing.
	 */
	private String eventCode;
	/**
	 * The profile identifiers the message adheres to.
	 */
	private final Set<String> profileIds = new LinkedHashSet<>();
	/**
	 * The idetifier of the hl7 data object.
	 */
	private String hl7DataId;
	/**
	 * The bundle being created.
	 */
	private Bundle bundle;
	/**
	 * Whether or not Provenance resources should be created. 
	 */
	private boolean storingProvenance = true;
	/**
	 * Other properties associated with this context.
	 */
	private final Map<String, Object> properties = new LinkedHashMap<>();
	
	private final MessageParser messageParser;
	public Context(MessageParser messageParser) {
		this.messageParser = messageParser;
	}
	void addProfileId(String profileId) {
		if (profileId == null || StringUtils.isEmpty(profileId)) {
			return;
		}
		profileIds.add(profileId);
	}
	public Map<String, Object> getProperties() {
		return Collections.unmodifiableMap(properties);
	}
	public Object getProperty(String key) {
		return properties.get(key);
	}
	public <T> T getProperty(Class<T> clazz) {
		return clazz.cast(getProperty(clazz.getName()));
	}
	public <T> T getProperty(Class<T> clazz, String key) {
		return clazz.cast(getProperty(key));
	}
	public void setProperty(Object obj) {
		properties.put(obj.getClass().getName(), obj);
	}
	public void setProperty(String key, Object obj) {
		properties.put(key, obj);
	}
}