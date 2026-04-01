package gov.cdc.izgw.v2tofhir.terminology;

/**
 * Composite terminology service interface extending all eight focused sub-interfaces.
 *
 * <p>Implementations provide a unified point of access for all terminology operations.
 * Callers that only need a subset of capabilities should accept the narrowest applicable
 * sub-interface rather than {@code TerminologyMapper} directly.</p>
 *
 * <p>Sub-interfaces and their responsibilities:</p>
 * <ul>
 *   <li>{@link NamingSystemResolver} — OID/URI bidirectional lookup and V2 table URI resolution</li>
 *   <li>{@link ConceptTranslator} — concept map translation by target system or named mapping</li>
 *   <li>{@link DisplayNameResolver} — preferred display text for coded concepts</li>
 *   <li>{@link CodeValidator} — value set membership validation</li>
 *   <li>{@link TerminologyLoader} — load and retrieve FHIR terminology resources</li>
 *   <li>{@link UnitMapper} — V2 unit string to UCUM coding conversion</li>
 *   <li>{@link DemographicsMapper} — race and ethnicity to US Core extension mapping</li>
 *   <li>{@link GeoMapper} — ISO 3166 country code resolution and normalization</li>
 * </ul>
 *
 * <p>All sub-interface methods provide {@code default} implementations so partial implementors
 * are not required to stub unrelated operations. See each sub-interface for default behaviour.</p>
 *
 * @author Audacious Inquiry
 * @see NamingSystemResolver
 * @see ConceptTranslator
 * @see DisplayNameResolver
 * @see CodeValidator
 * @see TerminologyLoader
 * @see UnitMapper
 * @see DemographicsMapper
 * @see GeoMapper
 */
public interface TerminologyMapper extends
        NamingSystemResolver,
        ConceptTranslator,
        DisplayNameResolver,
        CodeValidator,
        TerminologyLoader,
        UnitMapper,
        DemographicsMapper,
        GeoMapper {

    /**
     * Resolves the diamond default: both {@link NamingSystemResolver} and
     * {@link TerminologyLoader} declare {@code getNamingSystem(String)}.
     *
     * <p>Implementations should check both the OID/URI registry and the loaded-resource
     * registry and return whichever matches. The default returns empty.</p>
     *
     * @param oidOrUriOrId an OID, canonical URI, or resource id
     * @return the matching {@link org.hl7.fhir.r4.model.NamingSystem}, or empty if not found
     */
    @Override
    default java.util.Optional<org.hl7.fhir.r4.model.NamingSystem> getNamingSystem(String oidOrUriOrId) {
        return java.util.Optional.empty();
    }
}
