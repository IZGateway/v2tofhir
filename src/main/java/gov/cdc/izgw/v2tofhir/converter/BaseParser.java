package gov.cdc.izgw.v2tofhir.converter;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Supplier;

import org.hl7.fhir.instance.model.api.IBase;
import org.hl7.fhir.instance.model.api.IBaseMetaType;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Provenance;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.StringType;

import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Bundle.BundleType;
import org.apache.commons.text.WordUtils;

import gov.cdc.izgw.v2tofhir.segment.Processor;
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

	static final Context<Parser<?, ?>> defaultContext = new Context<>(null);
	/** The originalText extension */
	public static final String ORIGINAL_TEXT = "http://hl7.org/fhir/StructureDefinition/originalText";
	/** Locations to look for parser packages */
	protected static List<String> packageSources = new ArrayList<>();
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
	protected final Map<String, Class<Processor<U, S>>> parsers = new LinkedHashMap<>();
	protected final Map<S, String> processed = new LinkedHashMap<>();
	protected Processor<U, S> processor = null;
	
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
		processed.clear();
		for (S structure: structures) {
			updated.clear();
			processUnit(structure);
		}
		Normalizer.normalizeResources(b);
		return sortProvenance(b);
	}
	
	/**
	 * Process the content of a unit
	 * @param structure The structure to parse
	 * @return the constructed FHIR Resource or type
	 */
	public IBase processUnit(S structure) {
		if (processed.containsKey(structure)) {
			// This structure was already processed
			log.info("{} processed by {}", getName(structure), processed.get(structure));
			return null;
		}
		
		processor = getParser(getName(structure));
		if (processor == null) {
			return null;
		} 
		
		IBase b = null;
		try {
			beforeParsing(structure);
			b = processor.parse(structure);
		} catch (Exception e) {
			warn("Unexpected {} parsing {}: {}", e.getClass().getSimpleName(), getName(structure), e.getMessage(), e);
		} finally {
			afterParsing(structure);
			addProcessed(structure);
			processor.finish();
		}

		if (getContext().isStoringProvenance()) {
			// Provenance is updated by segments when we did work and are storing provenance
			updateProvenance(structure);
		}
		return b;
	}
	
	private void beforeParsing(S structure) {
		// Do Nothing
		
	}
	protected void afterParsing(S structure) {
		// Do nothing
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
	public Processor<U, S> getParser(String segment) {
		Class<Processor<U, S>> p = parsers.get(segment);
		@SuppressWarnings("unchecked")
		Class<Processor<U, S>> safeNull = (Class<Processor<U,S>>) Processor.NULL_PROCESSOR.getClass();
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
					Processor<U,S> instance = (Processor<U,S>)x;
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
	private Map<String, Class<Processor<U,S>>> parserClasses = new TreeMap<>();

	/**
	 * Load a parser for the specified segment or group
	 * @param name	The name of the segment or group to find a parser for
	 * @return	A StructureParser for the segment or group, or null if none found. 
	 */
	public Class<Processor<U,S>> loadParser(String name) {
		name = WordUtils.capitalize(name);
		Class<Processor<U,S>> clazz = parserClasses.get(name);
		if (clazz != null) {
			return clazz;
		}
		
		ClassLoader loader = MessageParser.class.getClassLoader();
		for (String packageName : BaseParser.packageSources) {
			try {
				@SuppressWarnings("unchecked") 
				Class<Processor<U,S>> clazz2 = (Class<Processor<U,S>>) loader.loadClass(packageName + "." + name + "Parser");
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
	

	@Override
	public void update(IBaseResource r) {
		updated.add(r);	
	}
}
