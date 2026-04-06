package gov.cdc.izgw.v2tofhir.terminology;

import java.util.Optional;

import org.hl7.fhir.r4.model.Coding;

/**
 * Resolves and normalizes geographic codes, primarily ISO 3166 country codes.
 *
 * <p>All default implementations return empty results; override to provide real resolution.</p>
 *
 * @author Audacious Inquiry
 * @see TerminologyMapper
 */
public interface GeoMapper {

    /**
     * Returns an ISO 3166 country {@link Coding} for the given code and code system.
     *
     * @param code       the country code (e.g., {@code "US"})
     * @param codeSystem the code system (e.g., {@code "urn:iso:std:iso:3166"})
     * @return a country coding, or {@link Optional#empty()} if not found
     */
    default Optional<Coding> lookupCountry(String code, String codeSystem) {
        return Optional.empty();
    }

    /**
     * Normalizes a country name or variant spelling to an ISO 3166 alpha-2 code.
     *
     * @param nameOrCode the country name or code variant (e.g., {@code "United States"})
     * @return the ISO 3166 alpha-2 code (e.g., {@code "US"}), or {@link Optional#empty()} if not found
     */
    default Optional<String> normalizeCountry(String nameOrCode) {
        return Optional.empty();
    }
}
