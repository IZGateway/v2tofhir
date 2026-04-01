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

    /** @see gov.cdc.izgw.v2tofhir.terminology.Units#toUcum(String) */
    public static Coding toUcum(String unit) {
        return gov.cdc.izgw.v2tofhir.terminology.Units.toUcum(unit);
    }

    /** @see gov.cdc.izgw.v2tofhir.terminology.Units#isUcum(String) */
    public static boolean isUcum(String unit) {
        return gov.cdc.izgw.v2tofhir.terminology.Units.isUcum(unit);
    }
}
