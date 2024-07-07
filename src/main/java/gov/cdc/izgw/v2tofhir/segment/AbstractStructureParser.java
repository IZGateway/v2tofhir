package gov.cdc.izgw.v2tofhir.segment;

import java.util.List;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Resource;

import gov.cdc.izgw.v2tofhir.converter.Context;
import gov.cdc.izgw.v2tofhir.converter.MessageParser;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * This is an abstract implementation for StructureParser instances used by the MessageParser.
 * 
 * @author Audacious Inquiry
 */
@Data
@Slf4j
public abstract class AbstractStructureParser implements StructureParser {
	private final MessageParser messageParser;
	private final String structureName;

	/**
	 * Contruct a StructureParser for the given messageParser and
	 * @param messageParser
	 * @param segmentName
	 */
	AbstractStructureParser(MessageParser messageParser, String structureName) {
		this.messageParser = messageParser;
		this.structureName = structureName;
	}

	@Override
	public String structure() {
		return structureName;
	}

	/**
	 * Returns the parsing context for the current message or structure being parsed.
	 * 
	 * @return The parsing context
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
	
	/**
	 * Get the first resource of the specified class that was created during the 
	 * parsing of a message.
	 * 
	 * This method is typically used to get resources that only appear once in a message,
	 * such as the MessageHeader, or Patient resource.
	 * 
	 * @param <R>	The resource type.
	 * @param clazz	The class for the specified resource.
	 * @return	The first resource of the specified type, or null if not found.
	 */
	public <R extends Resource> R getFirstResource(Class<R> clazz) {
		R r = messageParser.getFirstResource(clazz);
		if (r == null) {
			log.warn("No {} has been created", clazz.getSimpleName());
		}
		return r;
	}
	
	/**
	 * Get the most recently created resource of the specified type.
	 * 
	 * This method is typically uses by a structure parser to get the most recently created
	 * resource in a group, such as the Immunization or ImmunizationRecommendation in an
	 * ORDER group in an RSP_K11 message.
	 * 
	 * @param <R>	The type of resource to get.
	 * @param clazz	The class for the specified resource.
	 * @return	The last resource created of the specified type, or null if not found.
	 */
	public <R extends Resource> R getLastResource(Class<R> clazz) {
		return messageParser.getLastResource(clazz);
	}
	
	/**
	 * Get a resource of the specified type with the specified identifier.
	 * @param <R>	The type of resource
	 * @param clazz	The specified type of the resource
	 * @param id	The identifier (just the id part, no resource type or url
	 * @return	The requested resource, or null if not found.
	 */
	public <R extends Resource> R getResource(Class<R> clazz, String id) {
		return messageParser.getResource(clazz, id);
	}
	/**
	 * Get all resources of the specified type that have been created during this parsing session.
	 * @param <R>	The type of resource
	 * @param clazz	The specified type of resource to retrieve
	 * @return	A list of the requested resources, or an empty list of none were found.
	 */
	public <R extends Resource> List<R> getResources(Class<R> clazz) {
		return messageParser.getResources(clazz);
	}
	
	/**
	 * Create a resource of the specified type for this parsing session.
	 * This method is typically called by the first segment of a group that is associated with 
	 * a given resource.
	 *  
	 * @param <R>	The type of resource
	 * @param clazz	The sepecified type for the resource to create
	 * @return	A new resource of the specified type.  The id will already be populated.
	 */
	public <R extends Resource> R createResource(Class<R> clazz) {
		return messageParser.findResource(clazz, null);
	}
	
	/**
	 * Find a resource of the specified type for this parsing session, or create one if none
	 * can be found or id is null or empty.
	 * 
	 * This method is typically called by the first segment of a group that is associated with 
	 * a given resource.  It can be used to create a resource with a given id value if control
	 * is needed over the id value.
	 *  
	 * @param <R>	The type of resource
	 * @param clazz	The sepecified type for the resource to create
	 * @param id The identifier to assign.
	 * @return	A new resource of the specified type.  The id will already be populated.
	 */
	public <R extends Resource> R findResource(Class<R> clazz, String id) {
		return messageParser.findResource(clazz, id);
	}

	/**
	 * Get a property value from the context.
	 * 
	 * This is simply a convenience method for calling getContext().getProperty(t).
	 * 
	 * @param <T>	The type of property to retrieve.
	 * @param t		The class type of the property	
	 * @return		The requested property, or null if not present
	 */
	public <T> T getProperty(Class<T> t) {
		return messageParser.getContext().getProperty(t);
	}
}
