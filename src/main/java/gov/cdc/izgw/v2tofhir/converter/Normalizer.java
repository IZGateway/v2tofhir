package gov.cdc.izgw.v2tofhir.converter;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Endpoint;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Location;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Practitioner;
import org.hl7.fhir.r4.model.PractitionerRole;
import org.hl7.fhir.r4.model.RelatedPerson;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.StringType;

import gov.cdc.izgw.v2tofhir.datatype.HumanNameParser;
import gov.cdc.izgw.v2tofhir.utils.ParserUtils;

/**
 * This class supports basic normalization of resources within a bundle when two or more resources of the same type
 * may be created having the same identity.
 * 
 * @author Audacious Inquiry
 *
 */
public class Normalizer {
	private Normalizer() {}
	/**
	 * Merge duplicated resources in the bundle.  This is the main entry point for normalization processing.
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
		resourcesByType.values().forEach(l -> mergeResources(l, resourcesToRemove));
		
		Iterator<BundleEntryComponent> it = bundle.getEntry().iterator();
		while (it.hasNext()) {
			Resource r = it.next().getResource();
			if (r != null && resourcesToRemove.remove(r)) {
				it.remove();
			}
		}
	}

	private static void mergeResources(List<Resource> l, List<Resource> resourcesToRemove) {
		for (int i = 0; i < l.size() - 1; i++) {
			Resource first = l.get(i);
			for (int j = i + 1; j < l.size(); j++) {
				Resource later = l.get(j);
				Resource toRemove = mergeResources(first, later);
				if (toRemove != null) {
					resourcesToRemove.add(toRemove);
				}
			}
		}
	}
	
	/**
	 * Compare two codes for compatibility.  If one or the other is null
	 * but not both, they are NOT compatible (unlike identifier).
	 * 
	 * @param first	The first code
	 * @param later	The later code
	 * @return true if the two values are compatible
	 */
	public static boolean codesEqual(CodeableConcept first, CodeableConcept later) {
		if (first == null && later == null) {
			return true;
		} else if (first == null || later == null) {
			return false;
		}
		return first.equalsDeep(later);
	}

	/**
	 * Compare two identifiers for compatibility.  If one or the other is null
	 * they are compatible.
	 * 
	 * @param first	The first identifier
	 * @param later	The later identifier
	 * @return true if the two values are compatible
	 */
	public static boolean identifiersEqual(Identifier first, Identifier later) {
		if (first == null || later == null) {
			// If one asserts an identifier, and the other does not, treat as equal
			return true;
		}
		return first.equalsDeep(later);
	}

	/**
	 * Merge two endpoints if they are equal, and return the endpoint to remove
	 * 
	 * @param first	The first endpoint (which will be kept if they are merged)
	 * @param later	The later endpoint (which will be removed if they are merged)
	 * @return	The endpoint to remove, or null if the two endpoints are different.
	 */
	public static Endpoint merge(Endpoint first, Endpoint later) {
		if (StringUtils.equals(first.getName(), later.getName()) &&
			identifiersEqual(first.getIdentifierFirstRep(), later.getIdentifierFirstRep())
		) {
			ParserUtils.mergeReferences(first, later);
			return later;
		}
		return null;
	}

	/**
	 * Merge two Location resources.
	 * @param first	The first location
	 * @param later The next location
	 * @return	The location to remove
	 */
	public static Location merge(Location first, Location later) {
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

	/**
	 * Merge two Organization resources.
	 * @param first	The first organization
	 * @param later The next organization
	 * @return	The organization to remove
	 */
	public static Organization merge(Organization first, Organization later) {
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

	/**
	 * Merge two Practitioner resources.
	 * @param first	The first Practitioner 
	 * @param later The next Practitioner 
	 * @return	The Practitioner to remove
	 */
	public static Practitioner merge(Practitioner first, Practitioner later) {
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

	/**
	 * Merge two PractitionerRole resources.
	 * @param first	The first PractitionerRole 
	 * @param later The next PractitionerRole 
	 * @return	The PractitionerRole to remove
	 */
	public static PractitionerRole merge(PractitionerRole first, PractitionerRole later) {
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
	 * Merge two RelatedPerson resources.
	 * @param first	The first RelatedPerson 
	 * @param later The next RelatedPerson 
	 * @return	The RelatedPerson to remove
	 */
	public static RelatedPerson merge(RelatedPerson first, RelatedPerson later) {
		if (namesEqual(first.getNameFirstRep(), later.getNameFirstRep()) &&
			identifiersEqual(first.getIdentifierFirstRep(), later.getIdentifierFirstRep())
		) {
			ParserUtils.mergeReferences(first, later);
			return later;
		}
		return null;
	}

	/**
	 * Merge two names together than have been identified as being the same.
	 * @param firstName	The name to merge into
	 * @param laterName	The other name
	 */
	public static void mergeNames(HumanName firstName, HumanName laterName) {
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
		laterName.setPrefix(firstName.getPrefix());
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
	 * Merge two resources.
	 * @param first	The first resource
	 * @param later The next resource 
	 * @return	The resource to remove
	 */
	public static Resource mergeResources(Resource first, Resource later) {
		if (first.getClass() != later.getClass()) {
			throw new IllegalArgumentException("Cannot merge resources of different types");
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
		return toRemove;
	}

	/**
	 * Compare two human names for compatibility.  If one or the other is null
	 * but not both, they are NOT compatible (unlike identifier).
	 * 
	 * @param first	The first name
	 * @param later	The later name
	 * @return true if the two values are compatible
	 */
	public static boolean namesEqual(HumanName first, HumanName later) {
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

	/**
	 * Remove professional suffixes from a name
	 * @param f	The name
	 * @return  The suffixes removed
	 */
	public static List<StringType> removeProfessionalSuffix(HumanName f) {
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
}
