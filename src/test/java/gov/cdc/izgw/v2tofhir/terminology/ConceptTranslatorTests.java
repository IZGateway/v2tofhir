package gov.cdc.izgw.v2tofhir.terminology;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Optional;

import org.hl7.fhir.r4.model.CodeType;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ConceptTranslator#mapCodeableConcept(String, CodeableConcept)}.
 *
 * <p>All tests use a simple anonymous-inner-class stub of {@link ConceptTranslator} that
 * returns controlled {@link TranslationResult} values so the two-pass logic in the
 * {@code default} method can be verified in isolation.</p>
 */
class ConceptTranslatorTests {

    private static final String MAPPING = "testMap";

    /** Stub that always returns {@link Optional#empty()} for every code. */
    private static final ConceptTranslator EMPTY_TRANSLATOR = new ConceptTranslator() {
        @Override
        public Optional<TranslationResult> mapCode(String mappingName, Coding source) {
            return Optional.empty();
        }
    };

    // -------------------------------------------------------------------------
    // (a) No recognized coding → Pass 3 returns first code unchanged
    // -------------------------------------------------------------------------

    @Test
    void mapCodeableConcept_noRecognizedCoding_returnsFirstCodeUnchanged() {
        CodeableConcept cc = new CodeableConcept()
                .addCoding(new Coding("http://example.com", "A", "Alpha"))
                .addCoding(new Coding("http://example.com", "B", "Beta"));

        CodeType result = EMPTY_TRANSLATOR.mapCodeableConcept(MAPPING, cc);

        assertNotNull(result);
        assertEquals("A", result.getValue(), "Pass 3 must return the first coding's code");
    }

    // -------------------------------------------------------------------------
    // (b) One pass-through coding and one explicit match → explicit match wins
    // -------------------------------------------------------------------------

    @Test
    void mapCodeableConcept_explicitMatchWinsOverPassThrough() {
        // Coding "A" → pass-through (exact=false), Coding "B" → explicit match (exact=true)
        ConceptTranslator translator = new ConceptTranslator() {
            @Override
            public Optional<TranslationResult> mapCode(String mappingName, Coding source) {
                if ("A".equals(source.getCode())) {
                    Coding passThrough = new Coding(source.getSystem(), "A-PT", "Alpha PassThrough");
                    return Optional.of(TranslationResult.approximate(source, passThrough, mappingName));
                }
                if ("B".equals(source.getCode())) {
                    Coding mapped = new Coding("http://mapped.com", "B-MAPPED", "Beta Mapped");
                    return Optional.of(TranslationResult.exact(source, mapped, mappingName));
                }
                return Optional.empty();
            }
        };

        // "A" is first in the list but only pass-through; "B" has an exact match
        CodeableConcept cc = new CodeableConcept()
                .addCoding(new Coding("http://example.com", "A", "Alpha"))
                .addCoding(new Coding("http://example.com", "B", "Beta"));

        CodeType result = translator.mapCodeableConcept(MAPPING, cc);

        assertNotNull(result);
        assertEquals("B-MAPPED", result.getValue(),
                "Pass 1 must return the explicit match even when it is not the first coding");
    }

    // -------------------------------------------------------------------------
    // (c) Only pass-through codings → first pass-through returned (Pass 2)
    // -------------------------------------------------------------------------

    @Test
    void mapCodeableConcept_onlyPassThroughCodings_returnsFirstPassThrough() {
        // Both codings map to approximate results; Pass 2 must return the first one
        ConceptTranslator translator = new ConceptTranslator() {
            @Override
            public Optional<TranslationResult> mapCode(String mappingName, Coding source) {
                Coding passThrough = new Coding(source.getSystem(), source.getCode() + "-PT", null);
                return Optional.of(TranslationResult.approximate(source, passThrough, mappingName));
            }
        };

        CodeableConcept cc = new CodeableConcept()
                .addCoding(new Coding("http://example.com", "X", "X Display"))
                .addCoding(new Coding("http://example.com", "Y", "Y Display"));

        CodeType result = translator.mapCodeableConcept(MAPPING, cc);

        assertNotNull(result);
        assertEquals("X-PT", result.getValue(),
                "Pass 2 must return the first pass-through result");
    }

    // -------------------------------------------------------------------------
    // (d) Null / empty CodeableConcept → null
    // -------------------------------------------------------------------------

    @Test
    void mapCodeableConcept_null_returnsNull() {
        assertNull(EMPTY_TRANSLATOR.mapCodeableConcept(MAPPING, null));
    }

    @Test
    void mapCodeableConcept_emptyConcept_returnsNull() {
        // CodeableConcept with no codings
        assertNull(EMPTY_TRANSLATOR.mapCodeableConcept(MAPPING, new CodeableConcept()));
    }

    // -------------------------------------------------------------------------
    // (e) ConceptMap absent → Pass 3 fallback
    // -------------------------------------------------------------------------

    @Test
    void mapCodeableConcept_conceptMapAbsent_pass3Fallback() {
        CodeableConcept cc = new CodeableConcept()
                .addCoding(new Coding("http://example.com", "FIRST", "First Code"))
                .addCoding(new Coding("http://example.com", "SECOND", "Second Code"));

        CodeType result = EMPTY_TRANSLATOR.mapCodeableConcept(MAPPING, cc);

        assertNotNull(result);
        assertEquals("FIRST", result.getValue(),
                "When no concept map exists Pass 3 must return the first coding's code");
    }
}
