package gov.cdc.izgw.v2tofhir.segment;

import java.lang.reflect.Method;
import java.util.List;

import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.OperationOutcome.OperationOutcomeIssueComponent;

import ca.uhn.hl7v2.model.Segment;
import gov.cdc.izgw.v2tofhir.annotation.ComesFrom;
import gov.cdc.izgw.v2tofhir.annotation.Produces;
import gov.cdc.izgw.v2tofhir.converter.Context;
import gov.cdc.izgw.v2tofhir.converter.MessageParser;
import lombok.extern.slf4j.Slf4j;

/**
 * This is an abstract implementation for StructureParser instances used by the MessageParser.
 * 
 * @author Audacious Inquiry
 */
@Slf4j
public abstract class AbstractStructureParser implements StructureParser {
	private Segment segment;
	/**
	 * initFieldHandlers must be called by a parser before calling parse(segment, parser)
	 * 
	 * @see #getFieldHandlers
	 * @param p	The structure parser to compute the field handlers for.
	 * @param fieldHandlers	The list to update with all of the field handlers 
	 */
	protected static void initFieldHandlers(AbstractStructureParser p, List<FieldHandler> fieldHandlers) {
		// Synchronize on the list in case two callers are trying to to initialize it at the 
		// same time.  This avoids one thread from trying to use fieldHandlers while another 
		// potentially overwrites it.
		synchronized (fieldHandlers) { // NOSONAR This use of a parameter for synchronization is OK
			// We got the lock, recheck the entry condition.
			if (!fieldHandlers.isEmpty()) {
				// Another thread initialized the list.
				return;
			}
			/**
			 * Go through and find all methods with a ComesFrom annotation.
			 */
			for (Method method: p.getClass().getMethods()) {
				for (ComesFrom from: method.getAnnotationsByType(ComesFrom.class)) {
					fieldHandlers.add(new FieldHandler(method, from, p));
				}
			}
		}
	}
	
	/**
	 * Returns the current segment being parsed.
	 * @return the current segment being parsed.
	 */
	protected Segment getSegment() {
		return segment;
	}
	/**
	 * Return what this parser produces.
	 * @return what this parser produces
	 */
	protected Produces getProduces() {
		return this.getClass().getAnnotation(Produces.class);
	}
	
	/**
	 * Subclasses must implement this method to get the field handlers.
	 * The list of field handers can be stored as a static member of the Parser
	 * class, and initialized once from a concrete instance of that class
	 * using initFieldHandlers.
	 * 
	 * @see AbstractStructureParser#initFieldHandlers(StructureParser)
	 * @return	The list of Field Handlers
	 */
	protected abstract List<FieldHandler> getFieldHandlers(); 

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
	 * Return the message parser
	 * @return the message parser
	 */
	public final MessageParser getMessageParser() {
		return messageParser;
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
	 * Get the last issue in an OperationOutcome, adding one if there are none
	 * @param oo	The OperationOutcome
	 * @return	The issue
	 */
	public OperationOutcomeIssueComponent getLastIssue(OperationOutcome oo) {
		List<OperationOutcomeIssueComponent> l = oo.getIssue();
		if (l.isEmpty()) {
			return oo.getIssueFirstRep();
		}
		return l.get(l.size() - 1);
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
	 * @param theClass	The specified type for the resource to create
	 * @return	A new resource of the specified type.  The id will already be populated.
	 */
	public <R extends IBaseResource> R createResource(Class<R> theClass) {
		return messageParser.createResource(theClass, null);
	}
	
	/**
	 * Add a resource for this parsing session.
	 * This method is typically called to add resources that were created in some other
	 * way than from createResource(), e.g., from DatatypeConverter.to* methods
	 * that return a standalone resource.
	 * 
	 * @param <R>	The type of resource
	 * @param resource	The resource
	 * @return	The resource
	 */
	public <R extends IBaseResource> R addResource(R resource) {
		return messageParser.addResource(null, resource);
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
	
	/**
	 * Annotation driven parsing.  Call this method to use parsing driven
	 * by ComesFrom and Produces annotations in the parser.
	 * 
	 * @param segment	The segment to parse
	 */
	public void parse(Segment segment) {
		this.segment = segment;
		IBaseResource r = setup();
		if (r == null) {
			// setup() returned nothing, there must be nothing to do
			return;
		}
		for (FieldHandler fieldHandler : getFieldHandlers()) {
			fieldHandler.handle(this, segment, r);
		}
	}
	
	/** 
	 * Set up any resources that field handlers will use.  Used during
	 * initialization of field handlers as well as during a normal 
	 * parse operation.
	 * 
	 * NOTE: Setup can look at previously parsed items to determine if there is 
	 * any work to do in the context of the current message.
	 * 
	 * @return the primary resource being created by this parser, or 
	 * null if parsing in this context should do nothing.
	 */
	public abstract IBaseResource setup();
}
