package gov.cdc.izgw.v2tofhir.converter;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.Bundle;

import lombok.Data;

/**
 * Context keeps track of important decision making variables during the parse of an HL7 message.
 * 
 * Different parsing decisions may be made depending on the type of event. For
 * example, RXA segments may be parsed into the Immunization resource instead of
 * a MedicationAdministration resource when the message is being parsed for a 
 * VXU message or a QPD response to an immunization query.
 * 
 * In order to make these decisions, Context provides a place to store information
 * about previously created objects.
 * @param <P> The parser to use
 */
@Data
public class Context<P extends Parser<?, ?>> {
	/**
	 * The event that generated the message, used to provide context during parsing.
	 */
	private String eventCode;
	/**
	 * The profile identifiers the message adheres to.
	 */
	private final Set<String> profileIds = new LinkedHashSet<>();
	/**
	 * The bundle being created.
	 */
	private Bundle bundle;
	/**
	 * Whether or not Provenance resources should be created. 
	 */
	private boolean storingProvenance = true;
	/**
	 * Whether or not to log a message on a processor load failure
	 */
	private boolean warnOnFailedLoad = false;
	/**
	 * Other properties associated with this context.
	 */
	private final Map<String, Object> properties = new LinkedHashMap<>();
	
	private final P parser;
	
	/** 
	 * Create a new Context for use with the given message parser.
	 * 
	 * @param parser	The message parser using this context.
	 */
	public Context(P parser) {
		this.parser = parser;
	}
	
	/**
	 * Reset the context for a new message parse.
	 */
	public void clear() {
		properties.clear();
		setBundle(null);
		setEventCode(null);
	}
	
	void addProfileId(String profileId) {
		if (profileId == null || StringUtils.isEmpty(profileId)) {
			return;
		}
		profileIds.add(profileId);
	}
	/** 
	 * Get the properties for the context as an unmodifiable map.
	 * 
	 * @return The properties set in this context.
	 */
	public Map<String, Object> getProperties() {
		return Collections.unmodifiableMap(properties);
	}
	
	/**
	 * Get a given property
	 * @param key	The property to find.
	 * @return	The property value, or null if not found.
	 */
	public Object getProperty(String key) {
		return properties.get(key);
	}
	
	/**
	 * Get a property of the given type. 
	 * 
	 * The key will be the class name of the property type.
	 * @see #getProperty(Class,String) 
	 *
	 * @param <T>	The property type
	 * @param clazz	The class representing the property type
	 * @return	The property that was found, or null if not found.
	 */
	public <T> T getProperty(Class<T> clazz) {
		return clazz.cast(getProperty(clazz.getName()));
	}
	
	/**
	 * Get a property of the given type. 
	 * 
	 * The key will be the class name of the property type.
	 * 
	 * @param <T>	The property type
	 * @param clazz	The class representing the property type
	 * @param key The lookup key for the property
	 * @return	The property that was found, or null if not found.
	 */
	public <T> T getProperty(Class<T> clazz, String key) {
		return clazz.cast(getProperty(key));
	}
	
	/**
	 * Add or replace a property in the context with a new value.
	 * 
	 * The property will be created with the key obj.getClass().getName().
	 * @see #setProperty(String,Object) 
	 * @param obj	The object containing the property value	
	 */
	public void setProperty(Object obj) {
		properties.put(obj.getClass().getName(), obj);
	}
	
	/**
	 * Set the property with the specified key to the value of the specified object.
	 * 
	 * @param key	The key to store the property under 
	 * @param obj	The object containing the property value	
	 */
	public void setProperty(String key, Object obj) {
		properties.put(key, obj);
	}
}