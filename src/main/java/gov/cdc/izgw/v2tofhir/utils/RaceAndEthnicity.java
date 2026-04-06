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

    /**
     * Extension URL for US Core Ethnicity.
     *
     * @deprecated Use {@link gov.cdc.izgw.v2tofhir.terminology.RaceAndEthnicity#US_CORE_ETHNICITY} directly.
     * @see gov.cdc.izgw.v2tofhir.terminology.RaceAndEthnicity#US_CORE_ETHNICITY
     */
    public static final String US_CORE_ETHNICITY = gov.cdc.izgw.v2tofhir.terminology.RaceAndEthnicity.US_CORE_ETHNICITY;
    /**
     * Extension URL for US Core Race.
     *
     * @deprecated Use {@link gov.cdc.izgw.v2tofhir.terminology.RaceAndEthnicity#US_CORE_RACE} directly.
     * @see gov.cdc.izgw.v2tofhir.terminology.RaceAndEthnicity#US_CORE_RACE
     */
    public static final String US_CORE_RACE = gov.cdc.izgw.v2tofhir.terminology.RaceAndEthnicity.US_CORE_RACE;

    /**
     * Map a CodeableConcept containing a CDC Race Code into a US Core Race extension.
     *
     * @param race              The race code provided
     * @param raceExtension     The extension to populate
     * @deprecated Use {@link gov.cdc.izgw.v2tofhir.terminology.TerminologyMapper#mapRace(String, String)} instead.
     * @see gov.cdc.izgw.v2tofhir.terminology.RaceAndEthnicity#setRaceCode(CodeableConcept, Extension)
     */
    @Deprecated(since = "moved to gov.cdc.izgw.v2tofhir.terminology; use TerminologyMapper")
    public static void setRaceCode(CodeableConcept race, Extension raceExtension) {
        gov.cdc.izgw.v2tofhir.terminology.RaceAndEthnicity.setRaceCode(race, raceExtension);
    }

    /**
     * Map a CodeableConcept containing a CDC Ethnicity code into a US Core Ethnicity extension.
     *
     * @param ethnicity             The ethnicity code provided
     * @param ethnicityExtension    The extension to populate
     * @deprecated Use {@link gov.cdc.izgw.v2tofhir.terminology.TerminologyMapper#mapEthnicity(String, String)} instead.
     * @see gov.cdc.izgw.v2tofhir.terminology.RaceAndEthnicity#setEthnicityCode(CodeableConcept, Extension)
     */
    @Deprecated(since = "moved to gov.cdc.izgw.v2tofhir.terminology; use TerminologyMapper")
    public static void setEthnicityCode(CodeableConcept ethnicity, Extension ethnicityExtension) {
        gov.cdc.izgw.v2tofhir.terminology.RaceAndEthnicity.setEthnicityCode(ethnicity, ethnicityExtension);
    }
}
