package gov.cdc.izgw.v2tofhir.terminology;

import java.util.Optional;

import org.hl7.fhir.r4.model.CodeType;
import org.hl7.fhir.r4.model.CodeableConcept;
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

    /**
     * Maps the best coding within a {@link CodeableConcept} using the named concept map or
     * mapping table, applying a two-pass priority algorithm.
     *
     * <p><b>Pass 1 — explicit match:</b> iterates all codings calling
     * {@link #mapCode(String, Coding)}; returns the result code element of the first result
     * where {@link TranslationResult#exact()} is {@code true}.</p>
     *
     * <p><b>Pass 2 — pass-through / approximate match:</b> iterates all codings again and
     * returns the result code element of the first non-empty result (e.g. when
     * {@code unmapped.mode = "provided"} is in effect and {@code exact} is {@code false}).</p>
     *
     * <p><b>Pass 3 — last resort:</b> returns the code element of the first coding in the
     * {@link CodeableConcept} without any translation.</p>
     *
     * <p>Returns {@code null} if {@code cc} is {@code null} or has no codings.</p>
     *
     * @param mappingName the name of the concept map or V2 table
     * @param cc          the {@link CodeableConcept} to map
     * @return the best available {@link CodeType}, or {@code null} if the concept is empty
     */
    default CodeType mapCodeableConcept(String mappingName, CodeableConcept cc) {
        if (cc == null || !cc.hasCoding()) {
            return null;
        }

        // Pass 1: explicit (exact) match wins
        for (Coding coding : cc.getCoding()) {
            Optional<TranslationResult> result = mapCode(mappingName, coding);
            if (result.isPresent() && result.get().exact()) {
                return result.get().resultCoding().getCodeElement();
            }
        }

        // Pass 2: any non-empty result (pass-through / approximate)
        for (Coding coding : cc.getCoding()) {
            Optional<TranslationResult> result = mapCode(mappingName, coding);
            if (result.isPresent()) {
                return result.get().resultCoding().getCodeElement();
            }
        }

        // Pass 3: last resort — return the first coding unchanged
        return cc.getCodingFirstRep().getCodeElement();
    }
}
