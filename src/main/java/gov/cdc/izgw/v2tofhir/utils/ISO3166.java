package gov.cdc.izgw.v2tofhir.utils;

/**
 * Deprecated forwarding stub for {@link gov.cdc.izgw.v2tofhir.terminology.ISO3166}.
 *
 * @deprecated Moved to {@link gov.cdc.izgw.v2tofhir.terminology.ISO3166}.
 *             Use {@link gov.cdc.izgw.v2tofhir.terminology.TerminologyMapper} for new code.
 */
@Deprecated(since = "moved to gov.cdc.izgw.v2tofhir.terminology; use TerminologyMapper")
public final class ISO3166 {
    private ISO3166() {}

    /** @see gov.cdc.izgw.v2tofhir.terminology.ISO3166#twoLetterCode(String) */
    public static String twoLetterCode(String name) {
        return gov.cdc.izgw.v2tofhir.terminology.ISO3166.twoLetterCode(name);
    }

    /** @see gov.cdc.izgw.v2tofhir.terminology.ISO3166#threeLetterCode(String) */
    public static String threeLetterCode(String name) {
        return gov.cdc.izgw.v2tofhir.terminology.ISO3166.threeLetterCode(name);
    }

    /** @see gov.cdc.izgw.v2tofhir.terminology.ISO3166#name(String) */
    public static String name(String name) {
        return gov.cdc.izgw.v2tofhir.terminology.ISO3166.name(name);
    }
}
