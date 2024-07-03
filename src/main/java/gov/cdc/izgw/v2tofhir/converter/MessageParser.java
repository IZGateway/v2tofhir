package gov.cdc.izgw.v2tofhir.converter;

import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.hl7.fhir.r4.model.Attachment;
import org.hl7.fhir.r4.model.Binary;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Bundle.BundleType;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DocumentReference;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.Enumerations.DocumentReferenceStatus;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Provenance;
import org.hl7.fhir.r4.model.Provenance.ProvenanceEntityRole;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.StringType;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.model.Segment;
import ca.uhn.hl7v2.model.Structure;
import ca.uhn.hl7v2.model.Type;
import gov.cdc.izgw.v2tofhir.converter.segment.ERRParser;
import gov.cdc.izgw.v2tofhir.converter.segment.StructureParser;
import io.azam.ulidj.ULID;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
/**
 * MessageConverter provide the necessary methods to convert messages and segments into FHIR Bundles and Resources
 * respectively.
 * 
 * The methods in any instance this class are not thread safe, but multiple threads can perform conversions
 * on different messages.
 * 
 * MessageConverters are intentionally cheap to create.
 * @author boonek
 *
 */
public class MessageParser {
	
	private static final Context defaultContext = new Context(null);
	public static void setStoringProvenance(boolean storingProvidence) {
		defaultContext.setStoringProvenance(storingProvidence);
	}
	public static boolean isStoringProvenance() {
		return defaultContext.isStoringProvenance();
	}
	@Getter
	/** The shared context for parse of this message */
	private final Context context;
	
	private final Map<String, ? extends Resource> bag = new LinkedHashMap<>();
	private final Set<Resource> updated = new LinkedHashSet<>();
	private final Map<String, Class<StructureParser>> parsers = new LinkedHashMap<>();
	private final Map<Structure, String> processed = new LinkedHashMap<>();
	private StructureParser processor = null;
	public MessageParser() {
		context = new Context(this);
		// Copy default values to context on creation.
		context.setStoringProvenance(defaultContext.isStoringProvenance());
	}
	/**
	 * Convert an HL7 V2 message into a Bundle of FHIR Resources.
	 * 
	 * The message is iterated over to the segment level, and each segment is then converted
	 * into one or more resources, possibly combining information from multiple segments (e.g., PID, PD1 and PV1)
	 * in any of the returned resource.
	 * 
	 * The bundle will also contain provenance resources which show the linkage between the converted segments and
	 * their resulting resources, where each provenance record ties the data in a converted segment to the resource.
	 * 
	 * @param msg The message to convert.
	 * @return A FHIR Bundle containing the relevant resources. 
	 * @throws HL7Exception 
	 */
	public Bundle convert(Message msg) throws HL7Exception {
		initContext((Segment) msg.get("MSH"));
		Binary messageData = createResource(Binary.class);
		// See https://confluence.hl7.org/display/V2MG/HL7+locally+registered+V2+Media+Types
		messageData.setContentType("application/x.hl7v2+er7; charset=utf-8");
		byte[] data = msg.encode().getBytes(StandardCharsets.UTF_8);
		messageData.setData(data);
		messageData.setId(new IdType(messageData.fhirType(), ULID.random()));
		DocumentReference dr = createResource(DocumentReference.class);
		dr.setStatus(DocumentReferenceStatus.CURRENT);
		Attachment att = dr.addContent().getAttachment().setContentType("application/x.hl7v2+er7; charset=utf-8");
		att.setUrl(messageData.getIdElement().toString());
		att.setData(data);
		getContext().setHl7DataId(messageData.getIdElement().toString());
		return createBundle(msg);
	}
	private void initContext(Segment msh) throws HL7Exception {
		if (msh != null) {
			getContext().setEventCode(ParserUtils.toString(msh.getField(9, 0)));
			for (Type profile : msh.getField(21)) {
				getContext().addProfileId(ParserUtils.toString(profile));
			}
		}
	}
	
	public Bundle createBundle(Message msg) {
		Set<Structure> segments = new LinkedHashSet<>();
		ParserUtils.iterateStructures(msg, segments);
		return createBundle(segments);
	}
	public Bundle createBundle(Iterable<Structure> structures) {
		Bundle b = new Bundle();
		getContext().setBundle(b);
		b.setId(new IdType(b.fhirType(), ULID.random()));
		b.setType(BundleType.MESSAGE);
		for (Structure structure: structures) {
			updated.clear();
			processor = getParser(structure.getName());
			if (processor == null) {
				if (structure instanceof Segment) {
					// Unprocessed segments indicate potential data loss, report them.
					warn("Cannot parse {} segment", structure.getName(), structure);
				}
			} else if (!processed.containsKey(structure)) {
				// Don't process any structures that haven't been processed yet
				try {
					processor.parse(structure);
				} catch (Exception e) {
					warnException("Unexpected {} parsing {}: {}", e.getClass().getSimpleName(), structure.getName(), e.getMessage(), e);
				} finally {
					addProcesssed(structure);
				}
			} else {
				// Indicate processed structures that were skipped by other processors
				log.info("{} processed by {}", structure.getName(), processed.get(structure));
			}
			if (getContext().isStoringProvenance() &&
				structure instanceof Segment segment) {
				// Provenence is updated by segments 
				updateProvenance(segment);
			}
		}
		return b;
	}
	
	/**
	 * StructureParsers which process contained substructures (segments and groups)
	 * should call this method to avoid reprocessing the contents.
	 * 
	 * @param structure	The structure that was processed.
	 */
	public void addProcesssed(Structure structure) {
		if (structure != null) {
			processed.put(structure, processor.getClass().getSimpleName());
		}
	}
	private static final CodeableConcept CREATE_ACTIVITY = new CodeableConcept().addCoding(new Coding("http://terminology.hl7.org/CodeSystem/v3-DataOperation", "CREATE", "create"));
	private static final CodeableConcept ASSEMBLER_AGENT = new CodeableConcept().addCoding(new Coding("http://terminology.hl7.org/CodeSystem/provenance-participant-type", "assembler", "Assembler"));
	private void updateProvenance(Segment segment) {
		for (Resource r: updated) {
			Provenance p = (Provenance) r.getUserData(Provenance.class.getName());
			if (p == null) {
				continue;
			}
			Reference what = p.getEntityFirstRep().getWhat();
			try {
				what.addExtension().setUrl("http://hl7.org/fhir/StructureDefinition/originalText").setValue(new StringType(context.getHl7DataId()+"#"+PathUtils.getTerserPath(segment)));
			} catch (HL7Exception e) {
				warn("Unexpected {} updating provenance for {} segment: {}", e.getClass().getSimpleName(), segment.getName(), e.getMessage());
			}
		}
	}
	
	public Resource getResource(String id) {
		return bag.get(id);
	}
	public <R extends Resource> R getResource(Class<R> clazz, String id) {
		return clazz.cast(getResource(id));
	}
	public <R extends Resource> List<R> getResources(Class<R> clazz) {
		List<R> resources = new ArrayList<>();
		bag.values().stream().filter(clazz::isInstance).forEach(r -> resources.add(clazz.cast(resources)));
		return resources;
	}
	
	public <R extends Resource> R getFirstResource(Class<R> clazz) {
		List<R> resources = getResources(clazz);
		if (resources.isEmpty()) {
			return null;
		}
		return resources.get(0);
	}
	public <R extends Resource> R getLastResource(Class<R> clazz) {
		List<R> resources = getResources(clazz);
		if (resources.isEmpty()) {
			return null;
		}
		return resources.get(resources.size() - 1);
	}
	
	public <R extends Resource> R createResource(Class<R> clazz) {
		return findResource(clazz, null);
	}
	
	public <R extends Resource> R findResource(Class<R> clazz, String id) {
		R resource;
		if (id != null) {
			resource = getResource(clazz, id);
			if (resource != null) {
				updated.add(resource);
				return resource;
			}
		}
		try {
			if (id == null) {
				id = ULID.random();
			}
			resource = clazz.getDeclaredConstructor().newInstance();
			resource.setId(new IdType(resource.fhirType(), id));
			BundleEntryComponent entry = getContext().getBundle().addEntry().setResource(resource);
			resource.setUserData(BundleEntryComponent.class.getName(), entry);
			if (resource instanceof Provenance) {
				return resource;
			}
			updated.add(resource);
			if (getContext().isStoringProvenance() && resource instanceof DomainResource dr) { 
				Provenance p = createResource(Provenance.class);
				resource.setUserData(Provenance.class.getName(), p);
				p.addTarget(ParserUtils.toReference(dr));
				p.setRecorded(new Date());
				p.setActivity(CREATE_ACTIVITY);
				p.addAgent().setType(ASSEMBLER_AGENT);
				p.addEntity().setRole(ProvenanceEntityRole.QUOTATION).setWhat(new Reference(getContext().getHl7DataId()));
			}
			return resource;
		} catch (InstantiationException | IllegalAccessException
				| IllegalArgumentException | InvocationTargetException
				| NoSuchMethodException | SecurityException e) {
			warnException("Unexpected {} in findResource: {}", e.getClass().getSimpleName(), e.getMessage(), e);
			return null;
		}
	}

	/**
	 * Make parsers loadable 
	 * @param segment The segment to get a parser for
	 * @return The parser for that segment.
	 */
	public StructureParser getParser(String segment) {
		Class<StructureParser> p = parsers.get(segment);
		if (p == null) {
			p = loadParser(segment);
			if (p == null) {
				log.error("Cannot load parser for {}", segment);
				return null;
			}
			parsers.put(segment, p);
		}
		try {
			return p.getDeclaredConstructor(MessageParser.class).newInstance(this);
		} catch (InstantiationException | IllegalAccessException | IllegalArgumentException
				| InvocationTargetException | NoSuchMethodException | SecurityException e) {
			log.error("Unexpected {} while creating {} for {}", e.getClass().getSimpleName(), p.getName(), segment);
			return null;
		}
	}
	public Class<StructureParser> loadParser(String name) {
		String packageName = ERRParser.class.getPackageName();
		ClassLoader loader = ClassLoader.getSystemClassLoader();
		try {
			@SuppressWarnings("unchecked")
			Class<StructureParser> clazz = (Class<StructureParser>) loader.loadClass(packageName + "." + name + "Parser");
			return clazz;
		} catch (ClassNotFoundException ex) {
			return null;
		} 
	}
	private static void warn(String msg, Object ...args) {
		log.warn(msg, args);
	}
	private static void warnException(String msg, Object ...args) {
		log.warn(msg, args);
	}
}
