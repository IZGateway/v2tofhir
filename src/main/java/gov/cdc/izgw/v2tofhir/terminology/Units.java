package gov.cdc.izgw.v2tofhir.terminology;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.ConceptMap;
import org.hl7.fhir.r4.model.ConceptMap.TargetElementComponent;
import org.hl7.fhir.r4.model.ValueSet;
import org.hl7.fhir.r4.model.ValueSet.ConceptReferenceComponent;
import org.hl7.fhir.r4.model.ValueSet.ConceptSetComponent;

import ca.uhn.fhir.context.FhirContext;
import lombok.extern.slf4j.Slf4j;

/**
 * Utility class for parsing and converting V2 ANSI, ISO+ or UCUM unit codes.
 *
 * <p>At class-load time the unit lookup tables are populated by consulting the configured
 * {@link TerminologyMapper} (via {@link TerminologyMapperFactory}) for:
 * <ul>
 *   <li>{@code ConceptMap/v2-ucum-units} — maps V2 ANSI/ISO+ codes to UCUM</li>
 *   <li>{@code ValueSet/ucum-common-units} — well-known UCUM codes and their display names</li>
 * </ul>
 * If the configured mapper does not supply either resource, the bundled classpath JSON file is
 * used as a fallback, so a custom {@link TerminologyMapper} can selectively override one or
 * both resources without replacing the defaults entirely.
 * To re-initialize at runtime call {@link #initialize(TerminologyLoader)}.
 *
 * @author Audacious Inquiry
 * @see <a href="http://hl7.org/fhir/R4/valueset-ucum-common.html">UCUM Common Units ValueSet</a>
 */
@Slf4j
public class Units {

    /** Resource id of the ConceptMap that maps V2/ANSI/ISO+ unit codes to UCUM. */
    public static final String CONCEPT_MAP_ID = "v2-ucum-units";

    /** Resource id of the ValueSet containing well-known UCUM unit codes and display names. */
    public static final String VALUE_SET_ID = "ucum-common-units";

    private static final HashMap<String, Pair<String, String>> ucumMap = new LinkedHashMap<>();
    private static final HashMap<String, String> commonUcum = new LinkedHashMap<>();

    /** The ConceptMap last used to populate {@link #ucumMap}; {@code null} until first initialization. */
    private static ConceptMap unitConceptMap;

    /** The ValueSet last used to populate {@link #commonUcum}; {@code null} until first initialization. */
    private static ValueSet unitValueSet;

    static {
        init();
    }

    private Units() {
    }

    /**
     * Initializes the unit maps at class-load time.
     *
     * <p>First asks the configured {@link TerminologyMapper} (via {@link TerminologyMapperFactory})
     * for {@code ConceptMap/v2-ucum-units} and {@code ValueSet/ucum-common-units}.
     * Any resource not supplied by the mapper is loaded from the bundled classpath JSON file,
     * so a custom mapper can selectively override one or both resources.</p>
     */
    private static void init() {
        TerminologyLoader mapper = TerminologyMapperFactory.get();
        Optional<ConceptMap> cm = mapper.getConceptMap(CONCEPT_MAP_ID);
        Optional<ValueSet> vs = mapper.getValueSet(VALUE_SET_ID);

        // Fall back to classpath for any resource the configured mapper does not supply
        if (cm.isEmpty() || vs.isEmpty()) {
            FhirContext ctx = FhirContext.forR4();
            if (cm.isEmpty()) {
                cm = Optional.ofNullable(
                        loadClasspathResource(ctx, "ConceptMap-v2-ucum-units.json", ConceptMap.class));
                // If the ConceptMap is missing from the mapper store it there
                cm.ifPresent(mapper::load);
            }
            if (vs.isEmpty()) {
                vs = Optional.ofNullable(
                        loadClasspathResource(ctx, "ValueSet-ucum-common-units.json", ValueSet.class));
                // If the ValueSet is missing from the mapper store it there
                vs.ifPresent(mapper::load);
            }
        }
        initialize(mapper);
    }

    /**
     * (Re-)initializes the unit lookup maps from the given {@link TerminologyLoader}.
     *
     * <p>Retrieves {@code ConceptMap/v2-ucum-units} (V2 &rarr; UCUM code mappings) and
     * {@code ValueSet/ucum-common-units} (well-known UCUM codes and display names) from the
     * loader.  Any previous map contents are discarded before population begins, so callers
     * can use this method to hot-swap terminology data at runtime.</p>
     *
     * <p>The {@code ValueSet} is processed first so that its display names are available when
     * building the {@code ConceptMap} entries.</p>
     *
     * @param loader the {@link TerminologyLoader} to retrieve resources from; must not be {@code null}
     */
    public static void initialize(TerminologyLoader loader) {
        ucumMap.clear();
        commonUcum.clear();

        // Populate commonUcum from ValueSet; retain the resource for external access
        loader.getValueSet(VALUE_SET_ID).ifPresent(Units::populateCommonUcum);

        // Populate ucumMap from ConceptMap; retain the resource for external access
        loader.getConceptMap(CONCEPT_MAP_ID).ifPresent(Units::populateUcumMap);
    }

	private static void populateUcumMap(ConceptMap cm) {
		unitConceptMap = cm;
		cm.getGroup().forEach(group -> 
			group.getElement().forEach(element -> {
		        String v2Code = element.getCode();
		        if (!StringUtils.isBlank(v2Code) && !element.getTarget().isEmpty()) {
			        TargetElementComponent target = element.getTarget().get(0);
			        String ucumCode = target.getCode();
			        String display = 
			            	StringUtils.defaultIfEmpty(target.getDisplay(),
			            		StringUtils.defaultIfEmpty(commonUcum.get(ucumCode), ucumCode)
			            	);
			        Pair<String, String> value = Pair.of(ucumCode, display);
			        // V2 codes in the ConceptMap are already uppercase; uppercase defensively
			        ucumMap.put(v2Code.toUpperCase(), value);
			        // Also index by uppercase UCUM code so that UCUM codes map to themselves
			        ucumMap.put(ucumCode.toUpperCase(), value);
		        }
			})
		);
	}

	private static void populateCommonUcum(ValueSet vs) {
		unitValueSet = vs;
		for (ConceptSetComponent include : vs.getCompose().getInclude()) {
		    for (ConceptReferenceComponent concept : include.getConcept()) {
		        if (StringUtils.isNotBlank(concept.getCode())) {
		            commonUcum.put(concept.getCode(), concept.getDisplay());
		        }
		    }
		}
	}

    /**
     * Returns the {@link ConceptMap} that was last used to populate the V2-to-UCUM lookup table,
     * or {@link java.util.Optional#empty()} if initialization has not yet occurred or failed.
     *
     * @return the {@code ConceptMap/v2-ucum-units} resource, or empty
     */
    public static Optional<ConceptMap> getConceptMap() {
        return Optional.ofNullable(unitConceptMap);
    }

    /**
     * Returns the {@link ValueSet} that was last used to populate the common-UCUM display-name
     * table, or {@link Optional#empty()} if initialization has not yet occurred or failed.
     *
     * @return the {@code ValueSet/ucum-common-units} resource, or empty
     */
    public static Optional<ValueSet> getValueSet() {
        return Optional.ofNullable(unitValueSet);
    }

    /**
     * Loads and parses a FHIR JSON resource from the classpath.
     *
     * @param <T>          expected FHIR resource type
     * @param ctx          the {@link FhirContext} to use for parsing
     * @param resourceName classpath-relative filename (e.g., {@code "ConceptMap-v2-ucum-units.json"})
     * @param type         expected resource class
     * @return the parsed resource, or {@code null} if the resource was not found or parsing failed
     */
    private static <T extends org.hl7.fhir.r4.model.Resource> T loadClasspathResource(
            FhirContext ctx, String resourceName, Class<T> type) {
        try (InputStream is = Units.class.getClassLoader().getResourceAsStream(resourceName)) {
            if (is == null) {
                log.error("Classpath resource not found: {}", resourceName);
                return null;
            }
            return ctx.newJsonParser().parseResource(type, is);
        } catch (IOException e) {
            log.error("Failed to close stream for classpath resource {}: {}", resourceName, e.getMessage());
            return null;
        }
    }

    /**
     * Convert a string in ANSI, ISO+ or UCUM units to UCUM.
     *
     * @param unit  The string to convert to a UCUM code
     * @return      The Coding representing the UCUM unit
     */
    public static Coding toUcum(String unit) {
        if (unit == null || StringUtils.isBlank(unit)) {
            return null;
        }
        unit = unit.replace("\s+", "");  // Remove any whitespace for lookup
        String display = commonUcum.get(unit);
        if (display != null) {
            return new Coding(Systems.UCUM, unit, display);
        }
        unit = StringUtils.upperCase(unit);  // Convert to Uppercase for lookup
        Pair<String,String> ucumValue = ucumMap.get(unit);
        if (ucumValue == null) {
            return null;
        }
        Coding coding = new Coding(Systems.UCUM, ucumValue.getKey(), ucumValue.getValue());
        // If found in commonUcum, set display name.
        display = commonUcum.get(coding.getCode());
        if (display != null) {
            coding.setDisplay(display);
        }
        return coding;
    }

    /**
     * Check whether a unit string is an actual UCUM code
     * 
     * NOTE: UCUM is a code system with a grammar, which means that codes are effectively infinite
     * in number. This method only checks for UCUM units commonly used in medicine. The lists are extensive,
     * but it's also possible that some value UCUM codes will be missed by this method.
     *   
     * @param unit  A string to check
     * @return      true if the unit is known to be UCUM, or false otherwise.
     */
    public static boolean isUcum(String unit) {
        if (StringUtils.isBlank(unit)) {
            return false;
        }
        if (commonUcum.containsKey(unit)) {
            return true;
        }
        Coding coding = toUcum(unit);
        if (coding == null) {
            return false;
        }
        return unit.equals(coding.getCode()); 
    }
}