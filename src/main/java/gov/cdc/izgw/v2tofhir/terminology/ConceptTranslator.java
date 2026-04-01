package gov.cdc.izgw.v2tofhir.terminology;

import java.util.Optional;

import org.hl7.fhir.r4.model.Coding;

/**
 * Translates coded concepts between code systems using concept maps or named mapping tables.
 *
 * <p>All default implementations return {@link Optional#empty()}; override to provide real translation.</p>
 *
 * @author Audacious Inquiry
 * @see TerminologyMapper
 */
public interface ConceptTranslator {

    /**
     * Translates the given source coding to the target code system using the best available concept map.
     *
     * @param source       the coding to translate
     * @param targetSystem the target code system URI
     * @return the translation result, or {@link Optional#empty()} if no mapping is available
     */
    default Optional<TranslationResult> translate(Coding source, String targetSystem) {
        return Optional.empty();
    }

    /**
     * Applies the named concept map or mapping table to the source coding.
     *
     * <p>This is the primary entry point for annotation-driven mapping from
     * {@code @ComesFrom(table=...)} or {@code @ComesFrom(map=...)}.</p>
     *
     * @param mappingName the name of the concept map or V2 table (e.g., {@code "0001"}, {@code "USCoreRace"})
     * @param source      the coding to map
     * @return the translation result, or {@link Optional#empty()} if no mapping is available
     */
    default Optional<TranslationResult> mapCode(String mappingName, Coding source) {
        return Optional.empty();
    }
}
