package gov.cdc.izgw.v2tofhir.terminology;

import java.util.Optional;

import org.hl7.fhir.r4.model.Extension;

/**
 * Maps demographic codes (race and ethnicity) to US Core FHIR extensions.
 *
 * <p>All default implementations return {@link Optional#empty()}; override to provide real mapping.</p>
 *
 * @author Audacious Inquiry
 * @see TerminologyMapper
 */
public interface DemographicsMapper {

    /**
     * Maps a race code to a US Core race {@link Extension}.
     *
     * @param code       the race code (e.g., {@code "2054-5"})
     * @param codeSystem the code system for the race code (e.g., {@code "urn:oid:2.16.840.1.113883.6.238"})
     * @return a US Core race extension, or {@link Optional#empty()} if the code is unknown
     */
    default Optional<Extension> mapRace(String code, String codeSystem) {
        return Optional.empty();
    }

    /**
     * Maps an ethnicity code to a US Core ethnicity {@link Extension}.
     *
     * @param code       the ethnicity code
     * @param codeSystem the code system for the ethnicity code
     * @return a US Core ethnicity extension, or {@link Optional#empty()} if the code is unknown
     */
    default Optional<Extension> mapEthnicity(String code, String codeSystem) {
        return Optional.empty();
    }
}
