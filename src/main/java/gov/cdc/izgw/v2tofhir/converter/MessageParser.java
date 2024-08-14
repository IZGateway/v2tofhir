package gov.cdc.izgw.v2tofhir.converter;

import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Supplier;

import org.apache.commons.codec.binary.StringUtils;
import org.hl7.fhir.instance.model.api.IBaseMetaType;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Attachment;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Bundle.BundleType;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.DocumentReference;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.Endpoint;
import org.hl7.fhir.r4.model.Enumerations.DocumentReferenceStatus;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Location;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Practitioner;
import org.hl7.fhir.r4.model.PractitionerRole;
import org.hl7.fhir.r4.model.Provenance;
import org.hl7.fhir.r4.model.Provenance.ProvenanceEntityRole;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.RelatedPerson;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.StringType;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.model.Segment;
import ca.uhn.hl7v2.model.Structure;
import ca.uhn.hl7v2.model.Type;
import ca.uhn.hl7v2.parser.PipeParser;
import gov.cdc.izgw.v2tofhir.datatype.HumanNameParser;
import gov.cdc.izgw.v2tofhir.segment.ERRParser;
import gov.cdc.izgw.v2tofhir.segment.StructureParser;
import gov.cdc.izgw.v2tofhir.utils.Codes;
import gov.cdc.izgw.v2tofhir.utils.ParserUtils;
import io.azam.ulidj.ULID;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * MessageParser provide the necessary methods to convert messages and segments into FHIR Bundles and Resources
 * respectively.
 * 
 * The methods in any instance this class are not thread safe, but multiple threads can perform conversions
 * on different messages by using different instances of the MessageParser. The MessageParser class is 
 * intentionally cheap to create.
 * 
 * Parsers and Converters in this package follow as best as possible the mapping advice provided
 * in the HL7 V2 to FHIR Implementation Guide.
 * 
 * @see <a href="https://hl7.org/fhir/uv/v2mappings/2024Jan/">V2toFHIR Jan-2024</a>
 * 
 * @author Audacious Inquiry
 */
@Slf4j
public class MessageParser {
	/** The userData key for source of the resource */
	public static final String SOURCE = "source";

	/** The originalText extension */
	public static final String ORIGINAL_TEXT = "http://hl7.org/fhir/StructureDefinition/originalText";

	private static final Context defaultContext = new Context(null);
	
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
	private final Context context;
	
	private final Set<IBaseResource> updated = new LinkedHashSet<>();
	private final Map<String, Class<StructureParser>> parsers = new LinkedHashMap<>();
	private final Map<Structure, String> processed = new LinkedHashMap<>();
	private StructureParser processor = null;
	private Supplier<String> idGenerator = ULID::random;
			
	/**
	 * Construct a new MessageParser.
	 */
	public MessageParser() {
		context = new Context(this);
		// Copy default values to context on creation.
		context.setStoringProvenance(defaultContext.isStoringProvenance());
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
	 * Convert the hl7Message to a new Message using a PipeParser and then convert it.
	 * @param hl7Message	The HL7 Message
	 * @return The converted bundle
	 * @throws HL7Exception if an error occurred while parsing.
	 */
	public Bundle convert(String hl7Message) throws HL7Exception {
    	return convert(new PipeParser().parse(hl7Message)); 
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
	 */
	public Bundle convert(Message msg) {
		reset();
		try {
			initContext((Segment) msg.get("MSH"));
		} catch (HL7Exception e) {
			warn("Cannot retrieve MSH segment from message");
		}
		byte[] data;
		try {
			data = msg.encode().getBytes(StandardCharsets.UTF_8);
		} catch (HL7Exception e) {
			warn("Cannnot get encoded message");
			data = new byte[0];
		}
		String encoded = null;
		try {
			encoded = msg.encode();
		} catch (HL7Exception e) {
			warnException("Could not encode the message: {}", e.getMessage(), e);
		}
		DocumentReference dr = createResource(DocumentReference.class);
		dr.setUserData(SOURCE, MessageParser.class.getName()); // Mark infrastructure created resources
		dr.setStatus(DocumentReferenceStatus.CURRENT);
		// See https://confluence.hl7.org/display/V2MG/HL7+locally+registered+V2+Media+Types
		Attachment att = dr.addContent().getAttachment().setContentType("application/x.hl7v2+er7; charset=utf-8");
		att.setData(data);

		// Add the encoded message as the original text for the content in both the binary
		// and document reference resources.
		if (encoded != null) {
			StringType e = new StringType(encoded);
			dr.getContentFirstRep().addExtension(ORIGINAL_TEXT, e);
		}

		return createBundle(msg);
	}
	
	private void initContext(Segment msh) {
		getContext().clear();
		getBundle();
		if (msh != null) {
			try {
				getContext().setEventCode(ParserUtils.toString(msh.getField(9, 0)));
			} catch (HL7Exception e) {
				warn("Unexpected HL7Exception parsing MSH-9: {}", e.getMessage());
			}
			try {
				for (Type profile : msh.getField(21)) {
					getContext().addProfileId(ParserUtils.toString(profile));
				}
			} catch (HL7Exception e) {
				warn("Unexpected HL7Exception parsing MSH-21: {}", e.getMessage());
			}
		}
	}
	
	/**
	 * Create a Bundle by parsing a given message
	 * @param msg	The given message
	 * @return	The generated Bundle resource
	 */
	protected Bundle createBundle(Message msg) {
		Set<Structure> segments = new LinkedHashSet<>();
		ParserUtils.iterateStructures(msg, segments);
		return createBundle(segments);
	}
	
	/**
	 * Create a Bundle by parsing a group of structures
	 * @param structures	The structures to parse.
	 * @return	The generated Bundle
	 */
	public Bundle createBundle(Iterable<Structure> structures) {
		Bundle b = getBundle();
		boolean didWork = false;
		processed.clear();
		for (Structure structure: structures) {
			updated.clear();
			processor = getParser(structure.getName());
			if (processor == null && !processed.containsKey(structure)) {
				if (structure instanceof Segment) {
					// Unprocessed segments indicate potential data loss, report them.
					// warn("Cannot parse {} segment", structure.getName(), structure)
				}
			} else if (!processed.containsKey(structure)) { // Process any structures that haven't been processed yet
				didWork = true;	// We did some work. It may have failed, but we did something.
				try {
					processor.parse(structure);
				} catch (Exception e) {
					warnException("Unexpected {} parsing {}: {}", e.getClass().getSimpleName(), structure.getName(), e.getMessage(), e);
				} finally {
					addProcessed(structure);
				}
			} else {
				// Indicate processed structures that were skipped by other processors
				log.info("{} processed by {}", structure.getName(), processed.get(structure));
			}
			if (didWork && getContext().isStoringProvenance() && structure instanceof Segment segment) {
				// Provenance is updated by segments when we did work and are storing provenance
				updateProvenance(segment);
			}
		}
		normalizeResources(b);
		return sortProvenance(b);
	}
	
	/**
	 * Merge duplicated resources in the bundle.
	 * 
	 * In creating resources, V2toFHIR library may create duplicates for resources created from
	 * names, identifiers, or other V2 datatypes.  This method will combine duplicates and references
	 * into a single resource that represents the object.
	 *  
	 * @param bundle	The bundle to normalize.
	 */
	public static void normalizeResources(Bundle bundle) {
		// The resources should be normalized in a particular order, so that what references
		// them is normalized after AFTER the resources that are referenced have been normalized.
		// Fortunately, the normal alpha order on Endpoint, Location, Organization, Practitioner
		// PractitionerRole, RelatedPerson works just fine, so we don't need to 
		// specify any special comparator for the TreeMap.
		
		Map<String, List<Resource>> resourcesByType = new TreeMap<>();
		bundle.getEntry().stream().map(e -> e.getResource()).forEach(r -> 
			resourcesByType.computeIfAbsent(r.fhirType(), k -> new ArrayList<>()).add(r)
		);
		List<Resource> resourcesToRemove = new ArrayList<>(); 
		for (List<Resource> l : resourcesByType.values()) {
			for (int i = 0; i < l.size() - 1; i++) {
				for (int j = i + 1; j < l.size(); j++) {
					Resource first = l.get(i);
					Resource later = l.get(j);
					if (!first.fhirType().equals(later.fhirType())) {
						// Skip cases where resources aren't of the same type.
						continue;
					}
					Resource toRemove = null;
					switch (first.fhirType()) {
					case "Endpoint":
						toRemove = merge((Endpoint)first, (Endpoint)later);
						break;
					case "Location":
						toRemove = merge((Location)first, (Location)later);
						break;
					case "Organization":
						toRemove = merge((Organization)first, (Organization)later);
						break;
					case "Practitioner":
						toRemove = merge((Practitioner)first, (Practitioner)later);
						break;
					case "PractitionerRole":
						toRemove = merge((PractitionerRole)first, (PractitionerRole)later);
						break;
					case "RelatedPerson":
						toRemove = merge((RelatedPerson)first, (RelatedPerson)later);
						break;
					default:
						break;
					}
					if (toRemove != null) {
						resourcesToRemove.add(toRemove);
					}
				}
			}
		}
		
		Iterator<BundleEntryComponent> it = bundle.getEntry().iterator();
		while (it.hasNext()) {
			Resource r = it.next().getResource();
			if (r != null && resourcesToRemove.remove(r)) {
				it.remove();
			}
		}
	}
	
	/**
	 * Compare two identifiers for compatibility.  If one or the other is null
	 * they are compatible.
	 * 
	 * @param first	The first identifier
	 * @param later	The later identifier
	 * @return true if the two values are compatiable
	 */
	private static boolean identifiersEqual(Identifier first, Identifier later) {
		if (first == null || later == null) {
			// If one asserts an identifier, and the other does not, treat as equal
			return true;
		}
		return first.equalsDeep(later);
	}
	
	/**
	 * Compare two codes for compatibility.  If one or the other is null
	 * but not both, they are NOT compatible (unlike identifier).
	 * 
	 * @param first	The first code
	 * @param later	The later code
	 * @return true if the two values are compatiable
	 */
	private static boolean codesEqual(CodeableConcept first, CodeableConcept later) {
		if (first == null && later == null) {
			return true;
		} else if (first == null || later == null) {
			return false;
		}
		return first.equalsDeep(later);
	}
	
	/**
	 * Compare two human names for compatibility.  If one or the other is null
	 * but not both, they are NOT compatible (unlike identifier).
	 * 
	 * @param first	The first name
	 * @param later	The later name
	 * @return true if the two values are compatible
	 */
	private static boolean namesEqual(HumanName first, HumanName later) {
		if (first == null && later == null) {
			return true;
		} else if (first == null || later == null) {
			return false;
		} else if (first.isEmpty() && later.isEmpty()) {
			// Both names are empty, a pretty degenerate case
			return true;
		} else if (first.isEmpty() || later.isEmpty()) {
			return false;
		}
		
		// Make a copy since we are going to "normalize" the names for comparison
		HumanName f = first.copy();
		HumanName l = later.copy();
		
		// We remove prefixes since they don't make a different.
		// Omitting a prefix won't change the identity. They can change over time.
		f.setPrefix(null);
		l.setPrefix(null);

		// We don't care about professional suffixes either. Sadly, FHIR doesn't distinguish these,
		// so we must use some NLP to find them.
		removeProfessionalSuffix(f);
		removeProfessionalSuffix(l);
		
		// Suffixes do make a difference.  Jr ~= Sr ~= III, et cetera.
		// But if one doesn't have suffixes and the other does, that's OK, so make them both empty.
		if (!f.hasSuffix() || f.getSuffix().isEmpty() || !l.hasSuffix() || l.getSuffix().isEmpty()) {
			f.setSuffix(null);
			l.setSuffix(null);
		}
		
		// We don't care about use.
		f.setUse(null);
		l.setUse(null);
		
		// We don't care about the text representation, just the parts.
		f.setText(null);
		l.setText(null);
		
		return f.equalsShallow(l);
	}
	
	private static int compareStringType(StringType s1, StringType s2) {
		if (Objects.equals(s1,  s2)) {
			return 0;
		}
		if (s1 == null) {
			return -1;
		}
		if (s2 == null) {
			return 1;
		}
		return s1.asStringValue().compareTo(s2.asStringValue());
	}
	
	/**
	 * Merge two names together than have been identified as being the same.
	 * @param firstName	The name to merge into
	 * @param laterName	The other name
	 */
	private static void mergeNames(HumanName firstName, HumanName laterName) {
		// Merge suffixes
		for (StringType suffix: laterName.getSuffix()) {
			if (!firstName.getSuffix().contains(suffix)) {
				firstName.getSuffix().add(suffix);
			}
		}
		laterName.setSuffix(firstName.getSuffix());
		// Merge prefixes
		for (StringType prefix: laterName.getPrefix()) {
			if (!firstName.getPrefix().contains(prefix)) {
				firstName.getPrefix().add(prefix);
			}
		}
		// Ensure both have same use, taken from first if present, otherwise later
		if (firstName.hasUse()) {
			laterName.setUse(firstName.getUse());
		} else if (laterName.hasUse()) {
			firstName.setUse(laterName.getUse());
		}
		
		// Ensure both have same text, taken from first if present, otherwise later
		if (firstName.hasText()) {
			laterName.setText(firstName.getText());
		} else if (laterName.hasText()) {
			firstName.setText(laterName.getText());
		}
	}

	/**
	 * Remove professional suffixes from a name
	 * @param f	The name
	 * @return 
	 */
	private static List<StringType> removeProfessionalSuffix(HumanName f) {
		List<StringType> toRemove = new ArrayList<>();
		for (StringType suffix : f.getSuffix()) {
			if (HumanNameParser.isDegree(suffix.asStringValue())) {
				toRemove.add(suffix);
			}
		}
		for (StringType suffix : toRemove) {
			f.getSuffix().remove(suffix);
		}
		return toRemove;
	}

	/**
	 * Merge two endpoints if they are equal, and return the endpoint to remove
	 * 
	 * @param first	The first endpoint (which will be kept if they are merged)
	 * @param later	The later endpoint (which will be removed if they are merged)
	 * @return	The endpoint to remove, or null if the two endpoints are different.
	 */
	private static Endpoint merge(Endpoint first, Endpoint later) {
		if (StringUtils.equals(first.getName(), later.getName()) &&
			identifiersEqual(first.getIdentifierFirstRep(), later.getIdentifierFirstRep())
		) {
			ParserUtils.mergeReferences(first, later);
			return later;
		}
		return null;
	}

	private static Organization merge(Organization first, Organization later) {
		if (first == null || later == null) {
			return null;
		}
		// If both have an endpoint, verify the endpoints are the same
		if (first.hasEndpoint() && later.hasEndpoint()) {
			Endpoint f = ParserUtils.getResource(Endpoint.class, first.getEndpointFirstRep());
			Endpoint l = ParserUtils.getResource(Endpoint.class, later.getEndpointFirstRep());
			if (merge(f, l) == null) {
				// When endpoints are not the same the organizations need to be dealt with separately.
				return null;
			}
		}
		
		if (StringUtils.equals(first.getName(), later.getName()) &&
			identifiersEqual(first.getIdentifierFirstRep(), later.getIdentifierFirstRep())
		) {
			// Ensure both resources reference the same endpoint
			if (later.hasEndpoint() && !first.hasEndpoint()) {
				first.setEndpoint(later.getEndpoint());
			}
			if (first.hasEndpoint() && !later.hasEndpoint()) {
				later.setEndpoint(first.getEndpoint());
			}
			ParserUtils.mergeReferences(first, later);
			return later;
		}
		return null;
	}
	
	private static Location merge(Location first, Location later) {
		if (first == null || later == null) {
			return null;
		}
		// Two locations are equal if a) their name, mode and physical types are equal

		if (!Objects.equals(first.getName(), later.getName()) ||
			!Objects.equals(first.getMode(), later.getMode()) ||
			!codesEqual(first.getPhysicalType(), later.getPhysicalType())
		) {
			return null;
		}
		// and b) their partOf values are equal
		if (merge(
				ParserUtils.getResource(Location.class, first.getPartOf()),
				ParserUtils.getResource(Location.class, first.getPartOf())
			) == null
		) {
			return null;
		}
		ParserUtils.mergeReferences(first, later);
		return later;
	}

	
	private static RelatedPerson merge(RelatedPerson first, RelatedPerson later) {
		if (namesEqual(first.getNameFirstRep(), later.getNameFirstRep()) &&
			identifiersEqual(first.getIdentifierFirstRep(), later.getIdentifierFirstRep())
		) {
			ParserUtils.mergeReferences(first, later);
			return later;
		}
		return null;
	}

	private static Practitioner merge(Practitioner first, Practitioner later) {
		if (first == null || later == null) {
			return null;
		}
		if (namesEqual(first.getNameFirstRep(), later.getNameFirstRep()) &&
			identifiersEqual(first.getIdentifierFirstRep(), later.getIdentifierFirstRep())
		) {
			mergeNames(first.getNameFirstRep(), later.getNameFirstRep());
			ParserUtils.mergeReferences(first, later);
			return later;
		}
		return null;
	}

	private static PractitionerRole merge(PractitionerRole first, PractitionerRole later) {
		if (merge(
				ParserUtils.getResource(Practitioner.class, first.getPractitioner()),
				ParserUtils.getResource(Practitioner.class, later.getPractitioner())
				) == null
		) {
			return null;
		}
		if (merge(
			ParserUtils.getResource(Organization.class, first.getOrganization()),
			ParserUtils.getResource(Organization.class, later.getOrganization())
			) == null
		) {
			return null;
		}
		
		ParserUtils.mergeReferences(first, later);
		return later;
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
	 * StructureParsers which process contained substructures (segments and groups)
	 * should call this method to avoid reprocessing the contents.
	 * 
	 * @param structure	The structure that was processed.
	 */
	public void addProcessed(Structure structure) {
		if (structure != null) {
			processed.put(structure, processor.getClass().getSimpleName());
		}
	}
	private static final Class<StructureParser> NULL_PARSER = StructureParser.class;
	private void updateProvenance(Segment segment) {
		for (IBaseResource r: updated) {
			Provenance p = (Provenance) r.getUserData(Provenance.class.getName());
			if (p == null) {
				continue;
			}
			Reference what = p.getEntityFirstRep().getWhat();
			try {
				StringType whatText = new StringType(segment.encode()); // Was new StringType(context.getHl7DataId()+"#"+PathUtils.getTerserPath(segment))
				what.addExtension().setUrl(ORIGINAL_TEXT).setValue(whatText);
				IBaseMetaType meta = r.getMeta();
				if (meta instanceof Meta m4) {
					m4.getSourceElement().addExtension().setUrl(ORIGINAL_TEXT).setValue(whatText);
				} else if (meta instanceof org.hl7.fhir.r4b.model.Meta m4b) {
					m4b.getSourceElement().addExtension().setUrl(ORIGINAL_TEXT).setValue(whatText);
				} else if (meta instanceof org.hl7.fhir.r5.model.Meta m5) {
					m5.getSourceElement().addExtension().setUrl(ORIGINAL_TEXT).setValue(whatText);
				} else if (meta instanceof org.hl7.fhir.dstu2.model.Meta m2) {
					m2.addExtension().setUrl(ORIGINAL_TEXT).setValue(whatText);
				} else if (meta instanceof org.hl7.fhir.dstu3.model.Meta m3) {
					m3.addExtension().setUrl(ORIGINAL_TEXT).setValue(whatText);
				}
			} catch (HL7Exception e) {
				warn("Unexpected {} updating provenance for {} segment: {}", e.getClass().getSimpleName(), segment.getName(), e.getMessage());
			}
		}
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
			id = idGenerator.get();
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

	/**
	 * Load a parser for a specified segment type.
	 * @param segment The segment to get a parser for
	 * @return The parser for that segment.
	 */
	public StructureParser getParser(String segment) {
		Class<StructureParser> p = parsers.get(segment);
		if (p == null) {
			p = loadParser(segment);
			if (p == null) {
				// Report inability to load ONCE
				log.error("Cannot load parser for {}", segment);
				// Save the fact that there is no parser for this segment
				parsers.put(segment, NULL_PARSER);
				return null;
			}
			parsers.put(segment, p);
		}
		if (p.equals(NULL_PARSER)) {
			return null;
		}
		try {
			return p.getDeclaredConstructor(MessageParser.class).newInstance(this);
		} catch (InstantiationException | IllegalAccessException | IllegalArgumentException
				| InvocationTargetException | NoSuchMethodException | SecurityException e) {
			log.error("Unexpected {} while creating {} for {}", e.getClass().getSimpleName(), p.getName(), segment, e);
			return null;
		}
	}
	/**
	 * Load a parser for the specified segment or group
	 * @param name	The name of the segment or group to find a parser for
	 * @return	A StructureParser for the segment or group, or null if none found. 
	 */
	public Class<StructureParser> loadParser(String name) {
		String packageName = ERRParser.class.getPackageName();
		ClassLoader loader = MessageParser.class.getClassLoader();
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
