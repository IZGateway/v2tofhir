package gov.cdc.izgw.v2tofhir.converter;

import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.DocumentReference;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Location;
import org.hl7.fhir.r4.model.Provenance;
import org.hl7.fhir.r4.model.Provenance.ProvenanceEntityRole;
import org.hl7.fhir.r4.model.Resource;

import gov.cdc.izgw.v2tofhir.utils.Codes;
import gov.cdc.izgw.v2tofhir.utils.ErrorReporter;
import gov.cdc.izgw.v2tofhir.utils.ParserUtils;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.function.Supplier;

/**
 * Interface for parsing units and subunits of information.
 *  
 * @author Audacious Inquiry
 *
 * @param <U> The unit to parse
 * @param <S> The subunit to parse
 */
public interface Parser<U,S> extends ErrorReporter {

	/** The userData key for source of the resource */
	String SOURCE = "source";

	/** 
	 * Get The idGenerator this parser uses 
	 * @return The idGenerator this parser uses
	 **/
	Supplier<String> getIdGenerator();
	
	
	/**
	 * Mark a resource that has been updated 
	 * @param r The resource to mark as updated
	 */
	void update(IBaseResource r);
	
	/**
	 * Convert a unit of information presented as a String to FHIR.
	 * @param string The unit of information to parse as a String
	 * @return	The converted bundle
	 * @throws Exception If an error occurs.
	 */
	Bundle convert(String string) throws Exception;
	
	/**
	 * Convert a unit to FHIR.
	 * @param unit The unit of information to parse
	 * @return	The converted bundle
	 * @throws Exception If an error occurs.
	 */
	Bundle convert(U unit) throws Exception;
	
	/**
	 * Encode a unit to a String for reporting in Provenance
	 * @param unit	The unit to convert
	 * @return	The unit in String format.
	 */
	String encodeUnit(U unit);
	/**
	 * Encode a subunit to a String for reporting in Provenance
	 * @param subunit	The subunit to convert
	 * @return	The subunit in String format.
	 */
	String encode(S subunit);
	
	/**
	 * Get the parsing context
	 * @return the parsing context
	 */
	Context<Parser<U,S>> getContext();
	
	/**
	 * Get the generated resource with the given identifier.
	 * @param id	The resource id
	 * @return	The resource that was generated for the bundle with that identifier, or null if no such resource exists.
	 */
	default Resource getResource(String id) {
		for (BundleEntryComponent entry: getContext().getBundle().getEntry()) {
			if (id.equals(entry.getResource().getIdPart())) {
				return entry.getResource();
			}
		}
		return null;
	}
	/**
	 * Get the generated resource of the specific class and identifier.
	 * 
	 * @param <R>	The type of resource
	 * @param theClass	The class of the resource
	 * @param id	The identifier of the resource
	 * @return	The generated resource, or null if not found.
	 * @throws ClassCastException if the resource with the specified id is not of the specified resource type.
	 */
	default <R extends IBaseResource> R getResource(Class<R> theClass, String id) {
		return theClass.cast(getResource(id));
	}
	
	/**
	 * Get all the generated resources of the specified class.
	 * 
	 * @param <R>	The type of resource
	 * @param clazz	The class of the resource
	 * @return A list containing the generated resources or an empty list if none found.
	 */
	default <R extends Resource> List<R> getResources(Class<R> clazz) {
		List<R> resources = new ArrayList<>();
		for (BundleEntryComponent entry : getContext().getBundle().getEntry()) {
			Resource r = entry.getResource();
			if (clazz.isInstance(r)) {
				resources.add(clazz.cast(r));
			}
		}
		return resources;
	}
	
	/**
	 * Get the first generated resource of the specified type.
	 * 
	 * @param <R>	The type of resource
	 * @param clazz	The class of the resource
	 * @return The generated resource or null if not found.
	 */
	default <R extends Resource> R getFirstResource(Class<R> clazz) {
		List<R> resources = getResources(clazz);
		if (resources.isEmpty()) {
			return null;
		}
		return resources.get(0);
	}

	/**
	 * Get the last generated resource of the specified type.
	 * 
	 * @param <R>	The type of resource
	 * @param clazz	The class of the resource
	 * @return The generated resource or null if not found.
	 */
	default <R extends Resource> R getLastResource(Class<R> clazz) {
		List<R> resources = getResources(clazz);
		if (resources.isEmpty()) {
			return null;
		}
		return resources.get(resources.size() - 1);
	}

	/**
	 * Create a resource of the specified type
	 * @param <R>	The type of resource
	 * @param clazz	The class of the resource
	 * @return The newly created resource.
	 */
	default <R extends IBaseResource> R createResource(Class<R> clazz) {
		return createResource(clazz, null);
	}

	/**
	 * Create a resource of the specified type with the given id
	 * @param <R>	The type of resource
	 * @param theClass	The class of the resource
	 * @param id	The id for the resource or null to assign a default id
	 * @return The newly created resource.
	 */
	default <R extends IBaseResource> R createResource(Class<R> theClass, String id) {
		try {
			return addResource(id, theClass.getDeclaredConstructor().newInstance());
		} catch (InstantiationException | IllegalAccessException
				| IllegalArgumentException | InvocationTargetException
				| NoSuchMethodException | SecurityException e) {
			warn("Unexpected {} in findResource: {}", e.getClass().getSimpleName(), e.getMessage(), e);
			return null;
		}

	}

	/**
	 * Adds an externally created Resource to the Bundle.
	 * This method is used when DatatypeConverter.to* methods return a resource
	 * instead of a type to add the returned resource to the bundle.
	 * 
	 * @param <R>	The type of resource to add
	 * @param id	The identifier of the resource
	 * @param resource	The resource.
	 * @return	The resource
	 */
	default <R extends IBaseResource> R addResource(String id, R resource) {
		// See if it already exists in the bundle
		if (getContext().getBundle().getEntry().stream().anyMatch(e -> e.getResource() == resource)) {
			// if it does, just return it. Nothing more is necessary.
			return resource;
		}
		if (id == null) {
			id = getIdGenerator().get();
		}
		IdType theId = new IdType(resource.fhirType(), id);
		resource.setId(theId);
		BundleEntryComponent entry = getContext().getBundle().addEntry().setResource((Resource) resource);
		resource.setUserData(BundleEntryComponent.class.getName(), entry);
		if (resource instanceof Provenance) {
			return resource;
		}
		update(resource);
		if (getContext().isStoringProvenance() && resource instanceof DomainResource dr) { 
			Provenance p = new Provenance();
			p.setId(getIdGenerator().get());
			getContext().getBundle().addEntry().setResource(p);
			
			p.setUserData(SOURCE, MessageParser.class.getName());	// Mark infrastructure created resources
			resource.setUserData(Provenance.class.getName(), p);
			p.addTarget(ParserUtils.toReference(dr, p, "target"));
			p.setRecorded(new Date());
			// Copy constants so that they are not modified.
			p.setActivity(Codes.CREATE_ACTIVITY.copy());
			// Copy constants so that they are not modified.
			p.addAgent().setType(Codes.ASSEMBLER_AGENT.copy());
			DocumentReference doc = getFirstResource(DocumentReference.class);
			if (doc != null) {
				p.addEntity().setRole(ProvenanceEntityRole.QUOTATION).setWhat(ParserUtils.toReference(doc, p, "entity"));
			}
		}
		if (resource instanceof Location location && location.hasPartOf()) {
			// Location resources can be created by the DatatypeConverter
			// in hierarchical form.  Add any partOf resources as well.
			addResource(
				null, 
				ParserUtils.getResource(
					Location.class, location.getPartOf()
				)
			);
		}
		return resource;
	}
	
	/**
	 * Find or create a resource of the specified type and identifier
	 * @param <R>	The type of resource
	 * @param theClass	The class of the resource
	 * @param id	The identifier of the resource, or null to just create a new resource.
	 * @return The existing or a newly created resource if none already exists.
	 */
	default <R extends IBaseResource> R findResource(Class<R> theClass, String id) {
		R resource;
		if (id != null) {
			resource = getResource(theClass, id);
			if (resource != null) {
				update(resource);
				return resource;
			}
		}
		return createResource(theClass, id);
	}
	
	/**
	 * Write a warning message
	 * @param message The warning message
	 * @param args The arguments
	 */
	void warn(String message, Object ... args);
}
