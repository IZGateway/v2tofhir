package gov.cdc.izgw.v2tofhir.terminology;

import java.util.Optional;

import org.hl7.fhir.r4.model.NamingSystem;

/**
 * Resolves OIDs to canonical URIs and vice-versa, and provides URI lookups for HL7 V2 tables.
 *
 * <p>All default implementations return empty results; override to provide real resolution.</p>
 *
 * @author Audacious Inquiry
 * @see TerminologyMapper
 */
public interface NamingSystemResolver {

    /**
     * Returns the canonical URI for the given OID.
     *
     * @param oid the OID to look up (e.g., {@code "2.16.840.1.113883.6.1"})
     * @return the canonical URI, or {@link Optional#empty()} if not found
     */
    default Optional<String> toUri(String oid) {
        return Optional.empty();
    }

    /**
     * Returns the OID for the given canonical URI.
     *
     * @param uri the URI to look up (e.g., {@code "http://loinc.org"})
     * @return the OID, or {@link Optional#empty()} if not found
     */
    default Optional<String> toOid(String uri) {
        return Optional.empty();
    }

    /**
     * Returns the full {@link NamingSystem} resource for the given OID or URI.
     *
     * @param oidOrUri the OID or URI to look up
     * @return the {@link NamingSystem}, or {@link Optional#empty()} if not found
     */
    default Optional<NamingSystem> getNamingSystem(String oidOrUri) {
        return Optional.empty();
    }

    /**
     * Returns the canonical FHIR URI for an HL7 V2 table.
     *
     * <p>Input may be a bare number ({@code "0001"}), a prefixed table name ({@code "HL70001"}),
     * or the full URI (returned unchanged).
     * Example: {@code "0001"} → {@code "http://terminology.hl7.org/CodeSystem/v2-0001"}</p>
     *
     * @param tableNameOrNumber the V2 table name or number
     * @return the canonical FHIR CodeSystem URI for the table
     */
    default String v2TableUri(String tableNameOrNumber) {
        if (tableNameOrNumber == null) {
            return null;
        }
        if (tableNameOrNumber.startsWith("http://") || tableNameOrNumber.startsWith("urn:")) {
            return tableNameOrNumber;
        }
        String number = tableNameOrNumber.replaceFirst("(?i)^HL7", "");
        return "http://terminology.hl7.org/CodeSystem/v2-" + number;
    }

    /**
     * Returns the preferred system URI for an identifier's assigning authority or system name.
     *
     * <p>Converts V2 assigning authority names and OID-based system identifiers to their
     * preferred FHIR Identifier system URIs. Returns the input unchanged if no mapping is known.</p>
     *
     * @param system the raw system string from a V2 identifier field
     * @return the preferred system URI; never {@code null} if input is non-null
     */
    default String normalizeSystem(String system) {
        return system;
    }
}
