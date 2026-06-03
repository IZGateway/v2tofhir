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

    /**
     * Get the ISO-3166 two letter code for the given country name or code.
     *
     * @param name  The country name or ISO code
     * @return      The ISO-3166 two letter code for the given country
     * @deprecated Use {@link gov.cdc.izgw.v2tofhir.terminology.TerminologyMapper#normalizeCountry(String)} instead.
     * @see gov.cdc.izgw.v2tofhir.terminology.ISO3166#twoLetterCode(String)
     */
    @Deprecated(since = "moved to gov.cdc.izgw.v2tofhir.terminology; use TerminologyMapper")
    public static String twoLetterCode(String name) {
        return gov.cdc.izgw.v2tofhir.terminology.ISO3166.twoLetterCode(name);
    }

    /**
     * Get the ISO-3166 three letter code for the given country name or code.
     *
     * @param name  The country name or ISO code
     * @return      The ISO-3166 three letter code for the given country
     * @deprecated Use {@link gov.cdc.izgw.v2tofhir.terminology.ISO3166#threeLetterCode(String)} directly.
     * @see gov.cdc.izgw.v2tofhir.terminology.ISO3166#threeLetterCode(String)
     */
    @Deprecated(since = "moved to gov.cdc.izgw.v2tofhir.terminology; use TerminologyMapper")
    public static String threeLetterCode(String name) {
        return gov.cdc.izgw.v2tofhir.terminology.ISO3166.threeLetterCode(name);
    }

    /**
     * Get the ISO-3166 name for the given country name or code.
     *
     * @param name  The country name or ISO code
     * @return      The ISO-3166 country name for the given country
     * @deprecated Use {@link gov.cdc.izgw.v2tofhir.terminology.TerminologyMapper#lookupCountry(String, String)} instead.
     * @see gov.cdc.izgw.v2tofhir.terminology.ISO3166#name(String)
     */
    @Deprecated(since = "moved to gov.cdc.izgw.v2tofhir.terminology; use TerminologyMapper")
    public static String name(String name) {
        return gov.cdc.izgw.v2tofhir.terminology.ISO3166.name(name);
    }
}
