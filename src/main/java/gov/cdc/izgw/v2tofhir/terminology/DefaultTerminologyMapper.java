package gov.cdc.izgw.v2tofhir.terminology;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.hl7.fhir.r4.model.CodeSystem;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.ConceptMap;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.NamingSystem;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.ValueSet;

import lombok.extern.slf4j.Slf4j;

/**
 * Default implementation of {@link TerminologyMapper} that delegates every method to the
 * existing static utility classes ({@link Systems}, {@link Mapping}, {@link Units},
 * {@link ISO3166}, {@link RaceAndEthnicity}).
 *
 * <p>The result of every call through {@code DefaultTerminologyMapper} is identical to calling
 * the equivalent static method directly. This class acts as the bridge between the new
 * injectable {@link TerminologyMapper} interface and the legacy static utilities.</p>
 *
 * <p>Dynamically loaded resources (via {@link #load(Resource)}) are stored in per-instance
 * maps and do not affect the shared static caches.</p>
 *
 * @author Audacious Inquiry
 * @see TerminologyMapper
 * @see TerminologyMapperFactory
 */
@Slf4j
public class DefaultTerminologyMapper implements TerminologyMapper {

    private final Map<String, ConceptMap> loadedConceptMaps = new LinkedHashMap<>();
    private final Map<String, CodeSystem> loadedCodeSystems = new LinkedHashMap<>();
    private final Map<String, ValueSet> loadedValueSets = new LinkedHashMap<>();

    // -------------------------------------------------------------------------
    // NamingSystemResolver
    // -------------------------------------------------------------------------

    /**
     * {@inheritDoc}
     * <p>Delegates to {@link Systems#toUri(String)}.</p>
     */
    @Override
    public Optional<String> toUri(String oid) {
        return Optional.ofNullable(Systems.toUri(oid));
    }

    /**
     * {@inheritDoc}
     * <p>Delegates to {@link Systems#toOid(String)}.</p>
     */
    @Override
    public Optional<String> toOid(String uri) {
        return Optional.ofNullable(Systems.toOid(uri));
    }

    /**
     * {@inheritDoc}
     * <p>Delegates to {@link Systems#getNamingSystem(String)}.</p>
     */
    @Override
    public Optional<NamingSystem> getNamingSystem(String oidOrUriOrId) {
        return Optional.ofNullable(Systems.getNamingSystem(oidOrUriOrId));
    }

    /**
     * {@inheritDoc}
     * <p>Delegates to {@link Mapping#mapTableNameToSystem(String)}, which resolves V2 table
     * names, numeric table numbers, OIDs, and aliased URIs to their canonical FHIR system
     * URI. Already-canonical URIs are returned unchanged. Returns {@code null} for blank
     * input.</p>
     */
    @Override
    public String v2TableUri(String tableNameOrNumber) {
        return Mapping.mapTableNameToSystem(tableNameOrNumber);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Delegates to {@link Mapping#mapSystem(org.hl7.fhir.r4.model.Identifier)} via a
     * temporary {@link org.hl7.fhir.r4.model.Identifier}, which resolves V2 assigning
     * authority names and OID-based system identifiers to their preferred FHIR Identifier
     * system URIs. Returns the input unchanged if no mapping is known.</p>
     */
    @Override
    public String normalizeSystem(String system) {
        if (system == null) {
            return null;
        }
        org.hl7.fhir.r4.model.Identifier temp = new org.hl7.fhir.r4.model.Identifier().setSystem(system);
        Mapping.mapSystem(temp);
        return temp.getSystem();
    }

    // -------------------------------------------------------------------------
    // ConceptTranslator
    // -------------------------------------------------------------------------

    /**
     * {@inheritDoc}
     *
     * <p>Delegates to {@link Mapping#getMapping(String)} then {@link Mapping#mapCode(Coding, boolean)}.
     * Returns {@link Optional#empty()} if the mapping name is unknown or no mapping exists
     * for the source code.</p>
     */
    @Override
    public Optional<TranslationResult> mapCode(String mappingName, Coding source) {
        Mapping m = Mapping.getMapping(mappingName);
        if (m == null) {
            return Optional.empty();
        }
        Coding result = m.mapCode(source, true);
        return Optional.ofNullable(result)
                .map(c -> TranslationResult.exact(source, c, mappingName));
    }

    /**
     * {@inheritDoc}
     *
     * <p>Translation by source/target system pair is not directly supported by the current
     * static mapping tables; this implementation always returns {@link Optional#empty()}.
     * Override in a subclass or custom {@link TerminologyMapper} to provide real translation.</p>
     */
    @Override
    public Optional<TranslationResult> translate(Coding source, String targetSystem) {
        return Optional.empty();
    }

    // -------------------------------------------------------------------------
    // DisplayNameResolver
    // -------------------------------------------------------------------------

    /**
     * {@inheritDoc}
     * <p>Delegates to {@link Mapping#getDisplay(String, String)}.</p>
     */
    @Override
    public Optional<String> getDisplay(String system, String code) {
        return Optional.ofNullable(Mapping.getDisplay(code, system));
    }

    /**
     * {@inheritDoc}
     * <p>Delegates to {@link Mapping#hasDisplay(String, String)}.</p>
     */
    @Override
    public boolean hasDisplay(String system, String code) {
        return Mapping.hasDisplay(code, system);
    }

    /**
     * {@inheritDoc}
     * <p>Registers the display by calling {@link Mapping#updateCodeLookup(Coding)}.</p>
     */
    @Override
    public void setDisplay(String system, String code, String display) {
        Mapping.updateCodeLookup(new Coding(system, code, display));
    }

    // -------------------------------------------------------------------------
    // CodeValidator
    // -------------------------------------------------------------------------

    /**
     * {@inheritDoc}
     *
     * <p>Value-set membership validation is not supported by the current static utilities;
     * this implementation always returns {@code false}. Override in a custom
     * {@link TerminologyMapper} to provide real validation.</p>
     */
    @Override
    public boolean validateCode(String valueSetUri, String system, String code) {
        return false;
    }

    // -------------------------------------------------------------------------
    // UnitMapper
    // -------------------------------------------------------------------------

    /**
     * {@inheritDoc}
     * <p>Delegates to {@link Units#toUcum(String)}.</p>
     */
    @Override
    public Optional<Coding> mapUnit(String unitCode) {
        return Optional.ofNullable(Units.toUcum(unitCode));
    }

    // -------------------------------------------------------------------------
    // DemographicsMapper
    // -------------------------------------------------------------------------

    /**
     * {@inheritDoc}
     *
     * <p>Constructs a {@link CodeableConcept} from the supplied code and system, delegates
     * to {@link RaceAndEthnicity#setRaceCode(CodeableConcept, Extension)}, and returns the
     * populated extension. Returns {@link Optional#empty()} if the code is not recognised.</p>
     */
    @Override
    public Optional<Extension> mapRace(String code, String codeSystem) {
        CodeableConcept race = new CodeableConcept().addCoding(new Coding(codeSystem, code, null));
        Extension ext = new Extension().setUrl(RaceAndEthnicity.US_CORE_RACE);
        RaceAndEthnicity.setRaceCode(race, ext);
        return ext.hasExtension() ? Optional.of(ext) : Optional.empty();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Constructs a {@link CodeableConcept} from the supplied code and system, delegates
     * to {@link RaceAndEthnicity#setEthnicityCode(CodeableConcept, Extension)}, and returns
     * the populated extension. Returns {@link Optional#empty()} if the code is not recognised.</p>
     */
    @Override
    public Optional<Extension> mapEthnicity(String code, String codeSystem) {
        CodeableConcept ethnicity = new CodeableConcept().addCoding(new Coding(codeSystem, code, null));
        Extension ext = new Extension().setUrl(RaceAndEthnicity.US_CORE_ETHNICITY);
        RaceAndEthnicity.setEthnicityCode(ethnicity, ext);
        return ext.hasExtension() ? Optional.of(ext) : Optional.empty();
    }

    // -------------------------------------------------------------------------
    // GeoMapper
    // -------------------------------------------------------------------------

    /**
     * {@inheritDoc}
     *
     * <p>Delegates to {@link ISO3166#twoLetterCode(String)} and {@link ISO3166#name(String)}.
     * Returns {@link Optional#empty()} if the code is not recognised.</p>
     */
    @Override
    public Optional<Coding> lookupCountry(String code, String codeSystem) {
        String name = ISO3166.name(code);
        if (name == null) {
            return Optional.empty();
        }
        String alpha2 = ISO3166.twoLetterCode(code);
        return Optional.of(new Coding(codeSystem, alpha2 != null ? alpha2 : code, name));
    }

    /**
     * {@inheritDoc}
     * <p>Delegates to {@link ISO3166#twoLetterCode(String)}.</p>
     */
    @Override
    public Optional<String> normalizeCountry(String nameOrCode) {
        return Optional.ofNullable(ISO3166.twoLetterCode(nameOrCode));
    }

    // -------------------------------------------------------------------------
    // TerminologyLoader
    // -------------------------------------------------------------------------

    /**
     * {@inheritDoc}
     *
     * <p>Supported resource types and their handling:</p>
     * <ul>
     *   <li>{@link NamingSystem} — registered in the shared {@link Systems} naming system registry</li>
     *   <li>{@link ConceptMap} — stored in a per-instance map; retrievable via {@link #getConceptMap(String)}</li>
     *   <li>{@link CodeSystem} — stored in a per-instance map; retrievable via {@link #getCodeSystem(String)}</li>
     *   <li>{@link ValueSet} — stored in a per-instance map; retrievable via {@link #getValueSet(String)}</li>
     * </ul>
     *
     * @throws TerminologyException if {@code resource} is {@code null} or an unsupported type
     */
    @Override
    public void load(Resource resource) {
        if (resource == null) {
            throw new TerminologyException("Cannot load a null resource");
        }
        switch (resource) {
        case NamingSystem ns -> {
            List<String> identifiers = new ArrayList<>();
            ns.getUniqueId().forEach(uid -> identifiers.add(uid.getValue()));
            if (!identifiers.isEmpty()) {
                Systems.addNamingSystem(ns.getName(), identifiers.toArray(new String[0]));
            } else {
                log.warn("NamingSystem '{}' has no unique identifiers; skipping registration", ns.getName());
            }
        }
        case ConceptMap cm -> loadedConceptMaps.put(cm.getIdElement().getIdPart(), cm);
        case CodeSystem cs -> loadedCodeSystems.put(cs.getIdElement().getIdPart(), cs);
        case ValueSet vs -> loadedValueSets.put(vs.getIdElement().getIdPart(), vs);
        default -> throw new TerminologyException(
				"Unsupported resource type for load(): " + resource.getResourceType());
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Checks the per-instance loaded map first, then falls back to the
     * {@link Units#CONCEPT_MAP_ID} resource owned by {@link Units} (if the id matches),
     * and finally to the static {@link Mapping} registry via {@link Mapping#getMapping(String)}
     * and {@link Mapping#asConceptMap()}.</p>
     */
    @Override
    public Optional<ConceptMap> getConceptMap(String id) {
        ConceptMap cm = loadedConceptMaps.get(id);
        if (cm != null) {
            return Optional.of(cm);
        }
        if (Units.CONCEPT_MAP_ID.equals(id)) {
            return Units.getConceptMap();
        }
        return Optional.ofNullable(Mapping.getMapping(id))
                .map(Mapping::asConceptMap);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Checks the per-instance loaded map first, then attempts to retrieve via the
     * {@link NamingSystem} user-data cache in {@link Systems}.</p>
     */
    @Override
    public Optional<CodeSystem> getCodeSystem(String id) {
        CodeSystem cs = loadedCodeSystems.get(id);
        if (cs != null) {
            return Optional.of(cs);
        }
        NamingSystem ns = Systems.getNamingSystem(id);
        if (ns != null) {
            Object userData = ns.getUserData(CodeSystem.class.getName());
            if (userData instanceof CodeSystem cachedCs) {
                return Optional.of(cachedCs);
            }
        }
        return Optional.empty();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Checks the per-instance loaded map first, then falls back to the
     * {@link Units#VALUE_SET_ID} resource owned by {@link Units} (if the id matches).</p>
     */
    @Override
    public Optional<ValueSet> getValueSet(String id) {
        ValueSet vs = loadedValueSets.get(id);
        if (vs != null) {
            return Optional.of(vs);
        }
        if (Units.VALUE_SET_ID.equals(id)) {
            return Units.getValueSet();
        }
        return Optional.empty();
    }
}
