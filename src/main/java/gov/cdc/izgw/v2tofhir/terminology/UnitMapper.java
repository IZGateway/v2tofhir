package gov.cdc.izgw.v2tofhir.terminology;

import java.util.Optional;

import org.hl7.fhir.r4.model.Coding;

/**
 * Maps source unit codes (e.g., V2 unit strings) to UCUM {@link Coding} values.
 *
 * <p>The default implementation returns {@link Optional#empty()}; override to provide real mapping.</p>
 *
 * @author Audacious Inquiry
 * @see TerminologyMapper
 */
public interface UnitMapper {

    /**
     * Maps a source unit code to a UCUM {@link Coding} with system
     * {@code "http://unitsofmeasure.org"}.
     *
     * @param unitCode the source unit string (e.g., {@code "mg/dL"})
     * @return a UCUM coding, or {@link Optional#empty()} if the unit is unknown
     */
    default Optional<Coding> mapUnit(String unitCode) {
        return Optional.empty();
    }
}
