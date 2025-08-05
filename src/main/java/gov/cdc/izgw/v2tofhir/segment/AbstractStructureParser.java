package gov.cdc.izgw.v2tofhir.segment;

import java.util.List;

import org.hl7.fhir.instance.model.api.IBaseResource;
import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.model.Segment;
import ca.uhn.hl7v2.model.Structure;
import gov.cdc.izgw.v2tofhir.converter.Parser;
import lombok.extern.slf4j.Slf4j;

import org.hl7.fhir.r4.model.Resource;

/**
 * This is an abstract implementation for StructureParser instances used by the MessageParser.
 * 
 * @author Audacious Inquiry
 */
@Slf4j
public abstract class AbstractStructureParser {
	/**
	 * This class creates an AbstractStructureParser as a Structure Processor.
	 * 
	 * @author Audacious Inquiry
	 */
	public static abstract class AbstractStructureProcessor extends AbstractStructureParser implements Processor<Message, Structure> { 
		AbstractStructureProcessor(Parser<Message, Structure> messageParser, String structureName) {
			super(messageParser, structureName);
		}
		
		/**
		 * Annotation driven parsing.  Call this method to use parsing driven
		 * by ComesFrom and Produces annotations in the parser.
		 * 
		 * This method will be called by MessageParser for each segment of the given type that
		 * appears within the method.  It will create the primary resource produced by the
		 * parser (by calling the setup() method) and then parses individual fields of the 
		 * segment and passes them to parser methods to add them to the primary resource or 
		 * to create any extra resources.
		 * 
		 * @param s The segment to be parsed
		 */
		public void parseSegment(Segment s) {
			try {
				if (s == null || s.isEmpty()) {
					return;
				}
			} catch (HL7Exception e) {
				log.warn("Unexpected HL7 Exception: {}", e.getMessage(), e);
				return;
			}

			super.segment = s;
			IBaseResource r = setup();
			if (r == null) {
				// setup() returned nothing, there must be nothing to do
				return;
			}
			List<FieldHandler> handlers = getFieldHandlers();
			for (FieldHandler fieldHandler : handlers) {
				fieldHandler.handle(this, s, r);
			}
		}
		
		@Override
		public Parser<Message, Structure> getParser() {
			return messageParser;
		}
	}
	
	/** The current segment */
	protected Segment segment;
	/**
	 * Returns the current segment being parsed.
	 * @return the current segment being parsed.
	 */
	protected Segment getSegment() {
		return segment;
	}

	/**
	 * Subclasses must implement this method to get the field handlers.
	 * The list of field handers can be stored as a static member of the Parser
	 * class, and initialized once from a concrete instance of that class
	 * using initFieldHandlers.
	 * 
	 * @return	The list of Field Handlers
	 */
	protected abstract List<FieldHandler> getFieldHandlers(); 

	protected final Parser<Message, Structure> messageParser;
	private final String structureName;

	/**
	 * Contruct a StructureParser for the given messageParser and
	 * @param messageParser
	 * @param segmentName
	 */
	AbstractStructureParser(Parser<Message, Structure> messageParser, String structureName) {
		this.messageParser = messageParser;
		this.structureName = structureName;
	}

	/**
	 * Implements the structure() operation for Processor
	 * @return The name of the structure
	 */
	public String structure() {
		return structureName;
	}
	
	/**
	 * Implement isEmpty for Parser&lt;Structure&gt;
	 * @param s	The structure to check
	 * @return true if empty or in error
	 */
	public boolean isEmpty(Structure s) {
		try {
			return s == null || s.isEmpty();
		} catch (HL7Exception e) {
			log.warn("Unexpected HL7 Exception: {}", e.getMessage(), e);
			return true;
		}
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
	 * Get the first generated resource of the specified type.
	 * 
	 * @param <R>	The type of resource
	 * @param clazz	The class of the resource
	 * @return The generated resource or null if not found.
	 */
	public <R extends Resource> R getFirstResource(Class<R> clazz) {
		return messageParser.getFirstResource(clazz);
	}

	/**
	 * Get the last generated resource of the specified type.
	 * 
	 * @param <R>	The type of resource
	 * @param clazz	The class of the resource
	 * @return The generated resource or null if not found.
	 */
	public <R extends Resource> R getLastResource(Class<R> clazz) {
		return messageParser.getLastResource(clazz);
	}
}
