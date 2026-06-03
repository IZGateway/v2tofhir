package gov.cdc.izgw.v2tofhir.utils;

import org.hl7.fhir.r4.model.Coding;

/**
 * Deprecated forwarding stub for {@link gov.cdc.izgw.v2tofhir.terminology.Units}.
 *
 * @deprecated Moved to {@link gov.cdc.izgw.v2tofhir.terminology.Units}.
 *             Use {@link gov.cdc.izgw.v2tofhir.terminology.TerminologyMapper} for new code.
 */
@Deprecated(since = "moved to gov.cdc.izgw.v2tofhir.terminology; use TerminologyMapper")
public final class Units {
    private Units() {}

    /**
     * Convert a string in ANSI, ISO+ or UCUM units to UCUM.
     *
     * @param unit  The string to convert to a UCUM code
     * @return      The Coding representing the UCUM unit
     * @deprecated Use {@link gov.cdc.izgw.v2tofhir.terminology.TerminologyMapper#mapUnit(String)} instead.
     * @see gov.cdc.izgw.v2tofhir.terminology.Units#toUcum(String)
     */
    @Deprecated(since = "moved to gov.cdc.izgw.v2tofhir.terminology; use TerminologyMapper")
    public static Coding toUcum(String unit) {
        return gov.cdc.izgw.v2tofhir.terminology.Units.toUcum(unit);
    }

    /**
     * Check whether a unit string is an actual UCUM code.
     *
     * <p>NOTE: UCUM is a code system with a grammar, which means that codes are effectively infinite
     * in number. This method only checks for UCUM units commonly used in medicine. The lists are extensive,
     * but it is also possible that some valid UCUM codes will be missed by this method.</p>
     *
     * @param unit  A string to check
     * @return      {@code true} if the unit is known to be UCUM, {@code false} otherwise
     * @deprecated Use {@link gov.cdc.izgw.v2tofhir.terminology.Units#isUcum(String)} directly.
     * @see gov.cdc.izgw.v2tofhir.terminology.Units#isUcum(String)
     */
    @Deprecated(since = "moved to gov.cdc.izgw.v2tofhir.terminology; use TerminologyMapper")
    public static boolean isUcum(String unit) {
        return gov.cdc.izgw.v2tofhir.terminology.Units.isUcum(unit);
    }
}
