package gov.cdc.izgw.v2tofhir.converter;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Supplier;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.instance.model.api.IBaseMetaType;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.DocumentReference;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Location;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Provenance;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.StringType;

import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Bundle.BundleType;
import org.hl7.fhir.r4.model.Provenance.ProvenanceEntityRole;

import gov.cdc.izgw.v2tofhir.segment.MSHParser;
import gov.cdc.izgw.v2tofhir.segment.Processor;
import gov.cdc.izgw.v2tofhir.utils.Codes;
import gov.cdc.izgw.v2tofhir.utils.ParserUtils;
import io.azam.ulidj.ULID;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * The base parser class for V2toFHIR conversion. Abstracts the parsing from the message and message component detail.
 * 
 * @author Audacious Inquiry
 *
 * @param <U>	The message unit for conversion.
 * @param <S>	The subunits that get converted.
 */
@Slf4j
public abstract class BaseParser<U,S> implements Parser<U,S> {

	static final Context<MessageParser> defaultContext = new Context<>(null);
	/** The originalText extension */
	public static final String ORIGINAL_TEXT = "http://hl7.org/fhir/StructureDefinition/originalText";
	static List<String> packageSources = new ArrayList<>();
	/** The userData key for source of the resource */
	public static final String SOURCE = "source";

	/**
	 * Enable or disable storing of Provenance information for created resources.
	 * 
	 * When enabled, the MessageParser will create Provenance resources for each created resource
	 * that document the sources of information used to create resources in the generated bundle.
	 * These provenance records tie the information in the resource back to the specific segments
	 * or groups within the message that sourced the data in the generated resource. 
	 * 
	 * @param storingProvidence	Set to true to enable generation of Provenance resources, or false to disable them.
	 */
	public static void setStoringProvenance(boolean storingProvidence) {
		defaultContext.setStoringProvenance(storingProvidence);
	}
	/**
	 * Returns true if Provenance resources are to be created, false otherwise.
	 * @return true if Provenance resources are to be created, false otherwise.
	 */
	public static boolean isStoringProvenance() {
		return defaultContext.isStoringProvenance();
	}
	@Getter
	/** The shared context for parse of this message 
	 */
	private final Context<Parser<U,S>> context;
	protected final Set<IBaseResource> updated = new LinkedHashSet<>();
	
	@Getter
	private Supplier<String> idGenerator = ULID::random;
	protected final Map<String, Class<Processor<S>>> parsers = new LinkedHashMap<>();
	protected final Map<S, String> processed = new LinkedHashMap<>();
	protected Processor<S> processor = null;
	
	/**
	 * Construct a new parser with a freshly initialized context.
	 */
	public BaseParser() {
		context = new Context<Parser<U,S>>(this);
		// Copy default values to context on creation.
		context.setStoringProvenance(BaseParser.defaultContext.isStoringProvenance());
	}
	
	/**
	 * Reset this message parser for a new parsing session.
	 */
	public void reset() {
		getContext().clear();
		updated.clear();
		processed.clear();
		processor = null;
	}
	
	/**
	 * Set the ID Generator for this MessageParser.
	 * 
	 * This is used during testing to get uniform ID Generation during tests
	 * to enable comparison against a baseline.  If not set, ULID.random() is
	 * used to generate identifiers for the resources created by this 
	 * MessageParser. 
	 * 
	 * @param idGenerator	The idGenerator to use, or null to use the standard one.
	 */
	public void setIdGenerator(Supplier<String> idGenerator) {
		if (idGenerator == null) {
			this.idGenerator = ULID::random;
		} else {
			this.idGenerator = idGenerator;
		}
	}
	
	/**
	 * Get the bundle being constructed.
	 * @return The bundle being constructed, or a new bundle if none exists
	 */
	public Bundle getBundle() {
		Bundle b = getContext().getBundle();
		if (b == null) {
			b = new Bundle();
			b.setId(new IdType(b.fhirType() + "/" + idGenerator.get()));
			b.setType(BundleType.MESSAGE);
			getContext().setBundle(b);
		}
		return b;
	}

	/**
	 * Create a Bundle by parsing a given message
	 * @param unit	The given message
	 * @return	The generated Bundle resource
	 */
	protected Bundle createBundle(U unit) {
		return createBundle(getParts(unit));
	}
	
	/**
	 * Break the unit into parts convertible to FHIR.
	 * @param unit	The unit to break up
	 * @return	An iterable containing the parts.
	 */
	protected abstract Iterable<S> getParts(U unit);
	
	/**
	 * Create a Bundle by parsing a group of structures
	 * @param structures	The structures to parse.
	 * @return	The generated Bundle
	 */
	public Bundle createBundle(Iterable<S> structures) {
		Bundle b = getBundle();
		boolean didWork = false;
		processed.clear();
		for (S structure: structures) {
			updated.clear();
			processor = getParser(getName(structure));
			if (processor == null && !processed.containsKey(structure)) {
				continue;
			} else if (!processed.containsKey(structure)) { // Process any structures that haven't been processed yet
				didWork = true;	// We did some work. It may have failed, but we did something.
				try {
					processor.parse(structure);
				} catch (Exception e) {
					warnException("Unexpected {} parsing {}: {}", e.getClass().getSimpleName(), getName(structure), e.getMessage(), e);
				} finally {
					addProcessed(structure);
				}
			} else {
				// Indicate processed structures that were skipped by other processors
				log.info("{} processed by {}", getName(structure), processed.get(structure));
			}
			if (didWork && getContext().isStoringProvenance()) {
				// Provenance is updated by segments when we did work and are storing provenance
				updateProvenance(structure);
			}
		}
		Normalizer.normalizeResources(b);
		return sortProvenance(b);
	}
	
	/**
	 * Get the name of a structure
	 * @param structure	The structure
	 * @return	The name of it.
	 */
	protected abstract String getName(S structure);
	
	/**
	 * StructureParsers which process contained substructures (segments and groups)
	 * should call this method to avoid reprocessing the contents.
	 * 
	 * @param structure	The structure that was processed.
	 */
	public void addProcessed(S structure) {
		if (structure != null) {
			processed.put(structure, processor.getClass().getSimpleName());
		}
	}	
	
	/**
	 * Update the provenance of a component 
	 * @param segment The component whose provenance needs to be updated
	 */
	protected void updateProvenance(S segment) {
		for (IBaseResource r: updated) {
			Provenance p = (Provenance) r.getUserData(Provenance.class.getName());
			if (p == null) {
				continue;
			}
			Reference what = p.getEntityFirstRep().getWhat();
			try {
				StringType whatText = new StringType(encode(segment)); // Was new StringType(context.getHl7DataId()+"#"+PathUtils.getTerserPath(segment))
				what.addExtension().setUrl(BaseParser.ORIGINAL_TEXT).setValue(whatText);
				IBaseMetaType meta = r.getMeta();
				if (meta instanceof Meta m4) {
					m4.getSourceElement().addExtension().setUrl(BaseParser.ORIGINAL_TEXT).setValue(whatText);
				} else if (meta instanceof org.hl7.fhir.r4b.model.Meta m4b) {
					m4b.getSourceElement().addExtension().setUrl(BaseParser.ORIGINAL_TEXT).setValue(whatText);
				} else if (meta instanceof org.hl7.fhir.r5.model.Meta m5) {
					m5.getSourceElement().addExtension().setUrl(BaseParser.ORIGINAL_TEXT).setValue(whatText);
				} else if (meta instanceof org.hl7.fhir.dstu2.model.Meta m2) {
					m2.addExtension().setUrl(BaseParser.ORIGINAL_TEXT).setValue(whatText);
				} else if (meta instanceof org.hl7.fhir.dstu3.model.Meta m3) {
					m3.addExtension().setUrl(BaseParser.ORIGINAL_TEXT).setValue(whatText);
				}
			} catch (Exception e) {
				warn("Unexpected {} updating provenance for {} segment: {}", e.getClass().getSimpleName(), getName(segment), e.getMessage());
			}
		}
	}
	
	/**
	 * This method sorts Provenance resources to the end.
	 * @param b	The bundle to sort
	 * @return	The sorted bundle
	 */
	public Bundle sortProvenance(Bundle b) {
		if (getContext().isStoringProvenance()) {
			List<BundleEntryComponent> list = b.getEntry();
			int len = list.size();
			for (int i = 0; i < len; ++i) {
				BundleEntryComponent c = list.get(i);
				if (c == null || !c.hasResource()) {
					continue;
				}
				if ("Provenance".equals(c.getResource().fhirType())) {
					list.add(c);	// Add this provenance to the end
					list.remove(i);	// And remove it from the current position
					--i;			// NOSONAR: Backup a step to reprocess the new resource in this position.
					--len;			// We don't need to look at entries we just shuffled around to the end
				}
			}
		}
		return b;
	}
	/**
	 * Load a parser for a specified segment type.
	 * @param segment The segment to get a parser for
	 * @return The parser for that segment.
	 */
	public Processor<S> getParser(String segment) {
		Class<Processor<S>> p = parsers.get(segment);
		@SuppressWarnings("unchecked")
		Class<Processor<S>> safeNull = (Class<Processor<S>>) Processor.NULL_PROCESSOR.getClass();
		if (p == null) {
			p = loadParser(segment);
			if (p == null) {
				// Report inability to load ONCE if context says to.
				if (getContext().isWarnOnFailedLoad()) {
					log.warn("Cannot load parser for {}", segment);
				}
				// Save the fact that there is no parser for this segment, have to do this dynamically
				// to store a do-nothing marker class.
				parsers.put(segment, safeNull);
				return null;
			}
			parsers.put(segment, p);
		} else if (Processor.NULL_PROCESSOR_CLASS.equals(p)) {
			return null;
		}
		
		try {
			Constructor<?>[] ctors = p.getDeclaredConstructors();
			for (Constructor<?> ctor : ctors) {
				if (ctor.getParameterCount() == 1 && ctor.getParameterTypes()[0].isInstance(this)) {
					Object x = ctor.newInstance(this);
					@SuppressWarnings("unchecked")
					Processor<S> instance = (Processor<S>)x;
					return instance;
				}
			}
			log.error("No constructor found using {} while creating {} for {}", this.getClass().getSimpleName(), p.getName(), segment);
			parsers.put(segment, safeNull);
			return null;
		} catch (InstantiationException | IllegalAccessException | IllegalArgumentException
				| InvocationTargetException | SecurityException | ClassCastException e) {
			log.error("Unexpected {} while creating {} for {}", e.getClass().getSimpleName(), p.getName(), segment, e);
			return null;
		}
	}
	
	/** A cache of loaded parser classes */
	private Map<String, Class<Processor<S>>> parserClasses = new TreeMap<>();
	/**
	 * Load a parser for the specified segment or group
	 * @param name	The name of the segment or group to find a parser for
	 * @return	A StructureParser for the segment or group, or null if none found. 
	 */
	public Class<Processor<S>> loadParser(String name) {
		Class<Processor<S>> clazz = parserClasses.get(name);
		if (clazz != null) {
			return clazz;
		}
		
		ClassLoader loader = MessageParser.class.getClassLoader();
		for (String packageName : BaseParser.packageSources) {
			try {
				@SuppressWarnings("unchecked") 
				Class<Processor<S>> clazz2 = (Class<Processor<S>>) loader.loadClass(packageName + "." + name + "Parser");
				parserClasses.put(name, clazz2);
				return clazz2;
			} catch (ClassNotFoundException ex) {
				// Keep looking
			} catch (Exception ex) {
				log.error("{}.{} cannot be loaded: {}", packageName, name, ex.getMessage(), ex);
			}
		}
		return null;
	}
	
	/**
	 * Get the generated resource with the given identifier.
	 * @param id	The resource id
	 * @return	The resource that was generated for the bundle with that identifier, or null if no such resource exists.
	 */
	public Resource getResource(String id) {
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
	public <R extends IBaseResource> R getResource(Class<R> theClass, String id) {
		return theClass.cast(getResource(id));
	}
	
	/**
	 * Get all the generated resources of the specified class.
	 * 
	 * @param <R>	The type of resource
	 * @param clazz	The class of the resource
	 * @return A list containing the generated resources or an empty list if none found.
	 */
	public <R extends Resource> List<R> getResources(Class<R> clazz) {
		List<R> resources = new ArrayList<>();
		for (BundleEntryComponent entry : getBundle().getEntry()) {
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
	public <R extends Resource> R getFirstResource(Class<R> clazz) {
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
	public <R extends Resource> R getLastResource(Class<R> clazz) {
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
	public <R extends IBaseResource> R createResource(Class<R> clazz) {
		return createResource(clazz, null);
	}
	
	/**
	 * Create a resource of the specified type with the given id
	 * @param <R>	The type of resource
	 * @param theClass	The class of the resource
	 * @param id	The id for the resource or null to assign a default id
	 * @return The newly created resource.
	 */
	public <R extends IBaseResource> R createResource(Class<R> theClass, String id) {
		try {
			return addResource(id, theClass.getDeclaredConstructor().newInstance());
		} catch (InstantiationException | IllegalAccessException
				| IllegalArgumentException | InvocationTargetException
				| NoSuchMethodException | SecurityException e) {
			warnException("Unexpected {} in findResource: {}", e.getClass().getSimpleName(), e.getMessage(), e);
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
	public <R extends IBaseResource> R addResource(String id, R resource) {
		// See if it already exists in the bundle
		if (getContext().getBundle().getEntry().stream()
				.anyMatch(e -> e.getResource() == resource)) {
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
		updated.add(resource);
		if (getContext().isStoringProvenance() && resource instanceof DomainResource dr) { 
			Provenance p = createResource(Provenance.class);
			p.setUserData(BaseParser.SOURCE, MessageParser.class.getName());	// Mark infrastructure created resources
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
	public <R extends IBaseResource> R findResource(Class<R> theClass, String id) {
		R resource;
		if (id != null) {
			resource = getResource(theClass, id);
			if (resource != null) {
				updated.add(resource);
				return resource;
			}
		}
		return createResource(theClass, id);
	}
	
	protected static void warn(String msg, Object ...args) {
		log.warn(msg, args);
	}
	protected static void warnException(String msg, Object ...args) {
		log.warn(msg, args);
	}
}
