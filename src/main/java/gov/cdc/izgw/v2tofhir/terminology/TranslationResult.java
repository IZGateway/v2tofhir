package gov.cdc.izgw.v2tofhir.terminology;

import org.hl7.fhir.r4.model.Coding;

/**
 * Immutable value object carrying the result of a concept translation.
 *
 * <p>Use {@link #exact(Coding, Coding, String)} when the mapping is an exact code match,
 * or {@link #approximate(Coding, Coding, String)} when the match is equivalent or approximate.</p>
 *
 * @param sourceCoding  the coding that was translated
 * @param resultCoding  the translated coding (system + code + display)
 * @param mappingSource the name of the concept map or mapping table used
 * @param exact         {@code true} if the match was exact; {@code false} if approximate
 *
 * @author Audacious Inquiry
 */
public record TranslationResult(
        Coding sourceCoding,
        Coding resultCoding,
        String mappingSource,
        boolean exact) {

    /**
     * Creates a {@code TranslationResult} representing an exact code match.
     *
     * @param sourceCoding  the coding that was translated
     * @param resultCoding  the translated coding
     * @param mappingSource the name of the concept map or mapping table used
     * @return a new {@code TranslationResult} with {@code exact == true}
     */
    public static TranslationResult exact(Coding sourceCoding, Coding resultCoding, String mappingSource) {
        return new TranslationResult(sourceCoding, resultCoding, mappingSource, true);
    }

    /**
     * Creates a {@code TranslationResult} representing an approximate or equivalent match.
     *
     * @param sourceCoding  the coding that was translated
     * @param resultCoding  the translated coding
     * @param mappingSource the name of the concept map or mapping table used
     * @return a new {@code TranslationResult} with {@code exact == false}
     */
    public static TranslationResult approximate(Coding sourceCoding, Coding resultCoding, String mappingSource) {
        return new TranslationResult(sourceCoding, resultCoding, mappingSource, false);
    }

    /**
     * Returns {@code true} if this result represents an exact code match.
     *
     * @return {@code true} for exact match; {@code false} for approximate
     */
    public boolean isExact() {
        return exact;
    }
}
