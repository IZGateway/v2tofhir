package gov.cdc.izgw.v2tofhir.utils;

import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Extension;

/**
 * Deprecated forwarding stub for {@link gov.cdc.izgw.v2tofhir.terminology.RaceAndEthnicity}.
 *
 * @deprecated Moved to {@link gov.cdc.izgw.v2tofhir.terminology.RaceAndEthnicity}.
 *             Use {@link gov.cdc.izgw.v2tofhir.terminology.TerminologyMapper} for new code.
 */
@Deprecated(since = "moved to gov.cdc.izgw.v2tofhir.terminology; use TerminologyMapper")
public final class RaceAndEthnicity {
    private RaceAndEthnicity() {}

    /** @see gov.cdc.izgw.v2tofhir.terminology.RaceAndEthnicity#US_CORE_RACE */
    public static final String US_CORE_RACE = gov.cdc.izgw.v2tofhir.terminology.RaceAndEthnicity.US_CORE_RACE;
    /** @see gov.cdc.izgw.v2tofhir.terminology.RaceAndEthnicity#US_CORE_ETHNICITY */
    public static final String US_CORE_ETHNICITY = gov.cdc.izgw.v2tofhir.terminology.RaceAndEthnicity.US_CORE_ETHNICITY;

    /** @see gov.cdc.izgw.v2tofhir.terminology.RaceAndEthnicity#setRaceCode(CodeableConcept, Extension) */
    public static void setRaceCode(CodeableConcept race, Extension raceExtension) {
        gov.cdc.izgw.v2tofhir.terminology.RaceAndEthnicity.setRaceCode(race, raceExtension);
    }

    /** @see gov.cdc.izgw.v2tofhir.terminology.RaceAndEthnicity#setEthnicityCode(CodeableConcept, Extension) */
    public static void setEthnicityCode(CodeableConcept ethnicity, Extension ethnicityExtension) {
        gov.cdc.izgw.v2tofhir.terminology.RaceAndEthnicity.setEthnicityCode(ethnicity, ethnicityExtension);
    }
}
