package gov.cdc.izgw.v2tofhir.utils;

import java.util.Map;

import org.hl7.fhir.r4.model.CodeSystem;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.NamingSystem;
import org.hl7.fhir.r4.model.Type;

/**
 * Deprecated forwarding stub for {@link gov.cdc.izgw.v2tofhir.terminology.Mapping}.
 *
 * @deprecated Moved to {@link gov.cdc.izgw.v2tofhir.terminology.Mapping}.
 *             Use {@link gov.cdc.izgw.v2tofhir.terminology.TerminologyMapper} for new code.
 */
@Deprecated(since = "moved to gov.cdc.izgw.v2tofhir.terminology; use TerminologyMapper")
public class Mapping extends gov.cdc.izgw.v2tofhir.terminology.Mapping {

    /**
     * Constant used to store the original system in {@code Type.userData} for types with a system.
     *
     * @see gov.cdc.izgw.v2tofhir.terminology.Mapping#ORIGINAL_SYSTEM
     */
    public static final String ORIGINAL_SYSTEM = gov.cdc.izgw.v2tofhir.terminology.Mapping.ORIGINAL_SYSTEM;
    /**
     * Constant used to store the original display name in {@code Type.userData} for types with a display name.
     *
     * @see gov.cdc.izgw.v2tofhir.terminology.Mapping#ORIGINAL_DISPLAY
     */
    public static final String ORIGINAL_DISPLAY = gov.cdc.izgw.v2tofhir.terminology.Mapping.ORIGINAL_DISPLAY;
    /**
     * Constant used to store the original coding in {@code Coding.userData} for mapped values.
     *
     * @see gov.cdc.izgw.v2tofhir.terminology.Mapping#ORIGINAL
     */
    public static final String ORIGINAL = gov.cdc.izgw.v2tofhir.terminology.Mapping.ORIGINAL;
    /**
     * The prefix for V2 tables in HL7 Terminology.
     *
     * @see gov.cdc.izgw.v2tofhir.terminology.Mapping#V2_TABLE_PREFIX
     */
    public static final String V2_TABLE_PREFIX = gov.cdc.izgw.v2tofhir.terminology.Mapping.V2_TABLE_PREFIX;

    /**
     * Construct a new mapping with the given name.
     * @param name the name of the mapping
     * @deprecated use {@link gov.cdc.izgw.v2tofhir.terminology.Mapping}
     */
    @Deprecated(since = "moved to gov.cdc.izgw.v2tofhir.terminology; use TerminologyMapper")
    public Mapping(String name) {
        super(name);
    }

    /**
     * Get the specified mapping.
     *
     * @param name  The name of the mapping
     * @return      The specified mapping, or {@code null} if it was not found
     * @deprecated Use {@link gov.cdc.izgw.v2tofhir.terminology.TerminologyMapper#mapCode(String, org.hl7.fhir.r4.model.Coding)} instead.
     * @see gov.cdc.izgw.v2tofhir.terminology.Mapping#getMapping(String)
     */
    @Deprecated(since = "moved to gov.cdc.izgw.v2tofhir.terminology; use TerminologyMapper")
    public static Mapping getMapping(String name) {
        gov.cdc.izgw.v2tofhir.terminology.Mapping m = gov.cdc.izgw.v2tofhir.terminology.Mapping.getMapping(name);
        return m == null ? null : new Mapping(m.getName());
    }

    /**
     * Add the codes in {@code fields} to the specified {@link CodeSystem}.
     *
     * <p>Field data is expected to appear in this order:
     * Code, Display, Definition, V2 Concept Comment, V2 Concept Comment As Published,
     * HL7 Concept Usage Notes.</p>
     *
     * @param cs        The CodeSystem to add codes to
     * @param fields    The field data, in the order: code, display, definition, comment,
     *                  comment as published, usage notes
     * @deprecated Use {@link gov.cdc.izgw.v2tofhir.terminology.Mapping#addCodes(CodeSystem, String[])} directly.
     * @see gov.cdc.izgw.v2tofhir.terminology.Mapping#addCodes(CodeSystem, String[])
     */
    @Deprecated(since = "moved to gov.cdc.izgw.v2tofhir.terminology; use TerminologyMapper")
    public static void addCodes(CodeSystem cs, String[] fields) {
        gov.cdc.izgw.v2tofhir.terminology.Mapping.addCodes(cs, fields);
    }

    /**
     * Create a {@link NamingSystem} for the given {@link CodeSystem}.
     *
     * @param cs    The CodeSystem to create a NamingSystem for
     * @return      The created NamingSystem
     * @deprecated Use {@link gov.cdc.izgw.v2tofhir.terminology.Mapping#createNamingSystem(CodeSystem)} directly.
     * @see gov.cdc.izgw.v2tofhir.terminology.Mapping#createNamingSystem(CodeSystem)
     */
    @Deprecated(since = "moved to gov.cdc.izgw.v2tofhir.terminology; use TerminologyMapper")
    public static NamingSystem createNamingSystem(CodeSystem cs) {
        return gov.cdc.izgw.v2tofhir.terminology.Mapping.createNamingSystem(cs);
    }

    /**
     * Updates the display-name resolution table for a coding.
     *
     * @param coding    A coding with code, display and system all populated
     * @return          The updated code-lookup map where the given coding is stored
     * @deprecated Use {@link gov.cdc.izgw.v2tofhir.terminology.TerminologyMapper#setDisplay(String, String, String)} instead.
     * @see gov.cdc.izgw.v2tofhir.terminology.Mapping#updateCodeLookup(Coding)
     */
    @Deprecated(since = "moved to gov.cdc.izgw.v2tofhir.terminology; use TerminologyMapper")
    public static Map<String, Coding> updateCodeLookup(Coding coding) {
        return gov.cdc.izgw.v2tofhir.terminology.Mapping.updateCodeLookup(coding);
    }

    /**
     * Given a coding with code and system set, get the associated display name for
     * the code if known.
     *
     * @param coding    The coding to search for
     * @return          The display name for the code
     * @deprecated Use {@link gov.cdc.izgw.v2tofhir.terminology.TerminologyMapper#getDisplay(String, String)} instead.
     * @see gov.cdc.izgw.v2tofhir.terminology.Mapping#getDisplay(Coding)
     */
    @Deprecated(since = "moved to gov.cdc.izgw.v2tofhir.terminology; use TerminologyMapper")
    public static String getDisplay(Coding coding) {
        return gov.cdc.izgw.v2tofhir.terminology.Mapping.getDisplay(coding);
    }

    /**
     * Indicates whether a display name is known for a coding with the given code
     * and table or system name.
     *
     * @param code      The code to search for
     * @param table     The HL7 V2 table or FHIR system URI to look in
     * @return          {@code true} if a display name is known for this code and table
     * @deprecated Use {@link gov.cdc.izgw.v2tofhir.terminology.TerminologyMapper#hasDisplay(String, String)} instead.
     * @see gov.cdc.izgw.v2tofhir.terminology.Mapping#hasDisplay(String, String)
     */
    @Deprecated(since = "moved to gov.cdc.izgw.v2tofhir.terminology; use TerminologyMapper")
    public static boolean hasDisplay(String code, String table) {
        return gov.cdc.izgw.v2tofhir.terminology.Mapping.hasDisplay(code, table);
    }

    /**
     * Get the display name for a coding with the given code and table or system name.
     *
     * @param code      The code to search for
     * @param table     The HL7 V2 table or FHIR system URI to look in
     * @return          The display name for this code and table, or {@code null} if not found
     * @deprecated Use {@link gov.cdc.izgw.v2tofhir.terminology.TerminologyMapper#getDisplay(String, String)} instead.
     * @see gov.cdc.izgw.v2tofhir.terminology.Mapping#getDisplay(String, String)
     */
    @Deprecated(since = "moved to gov.cdc.izgw.v2tofhir.terminology; use TerminologyMapper")
    public static String getDisplay(String code, String table) {
        return gov.cdc.izgw.v2tofhir.terminology.Mapping.getDisplay(code, table);
    }

    /**
     * Given a coding with code and system set, set the associated display name for
     * the code if one is known.
     *
     * <p>If no display name is known the coding is unchanged. A default display name
     * may therefore be provided before calling this method.</p>
     *
     * @param coding    The coding to search for and potentially update
     * @deprecated Use {@link gov.cdc.izgw.v2tofhir.terminology.Mapping#setDisplay(Coding)} directly.
     * @see gov.cdc.izgw.v2tofhir.terminology.Mapping#setDisplay(Coding)
     */
    @Deprecated(since = "moved to gov.cdc.izgw.v2tofhir.terminology; use TerminologyMapper")
    public static void setDisplay(Coding coding) {
        gov.cdc.izgw.v2tofhir.terminology.Mapping.setDisplay(coding);
    }

    /**
     * Reset a {@link org.hl7.fhir.r4.model.Type} produced during conversion to its original,
     * unmapped values for display and system.
     *
     * <p>The converter adds extra data for better interoperability (e.g., display names, code
     * system URLs). Original supplied values are stored in user data under keys such as
     * {@code "original{FieldName}"}. This method restores those original values.</p>
     *
     * @param type  The type to reset to original values
     * @deprecated Use {@link gov.cdc.izgw.v2tofhir.terminology.Mapping#reset(org.hl7.fhir.r4.model.Type)} directly.
     * @see gov.cdc.izgw.v2tofhir.terminology.Mapping#reset(org.hl7.fhir.r4.model.Type)
     */
    @Deprecated(since = "moved to gov.cdc.izgw.v2tofhir.terminology; use TerminologyMapper")
    public static void reset(Type type) {
        gov.cdc.izgw.v2tofhir.terminology.Mapping.reset(type);
    }

    /**
     * Reset a {@link Coding} produced during conversion to its original, unmapped values
     * for display and system.
     *
     * <p>During the conversion process the V2 to FHIR converter stores the original V2 values
     * in user data on the datatype. This method restores those original values.</p>
     *
     * @param coding    A coding produced by DatatypeConverter to reset
     * @deprecated Use {@link gov.cdc.izgw.v2tofhir.terminology.Mapping#reset(Coding)} directly.
     * @see gov.cdc.izgw.v2tofhir.terminology.Mapping#reset(Coding)
     */
    @Deprecated(since = "moved to gov.cdc.izgw.v2tofhir.terminology; use TerminologyMapper")
    public static void reset(Coding coding) {
        gov.cdc.izgw.v2tofhir.terminology.Mapping.reset(coding);
    }

    /**
     * Reset a {@link CodeableConcept} produced during conversion to its original, unmapped
     * values for display and system.
     *
     * <p>During the conversion process the V2 to FHIR converter stores the original V2 values
     * in user data on the datatype. This method restores those original values.</p>
     *
     * @param cc    A CodeableConcept produced by DatatypeConverter to reset
     * @deprecated Use {@link gov.cdc.izgw.v2tofhir.terminology.Mapping#reset(CodeableConcept)} directly.
     * @see gov.cdc.izgw.v2tofhir.terminology.Mapping#reset(CodeableConcept)
     */
    @Deprecated(since = "moved to gov.cdc.izgw.v2tofhir.terminology; use TerminologyMapper")
    public static void reset(CodeableConcept cc) {
        gov.cdc.izgw.v2tofhir.terminology.Mapping.reset(cc);
    }

    /**
     * Given a coding and system, set the system and display values to the expected
     * values in FHIR.
     *
     * @param coding    The coding to adjust
     * @return          The mapped coding
     * @deprecated Use {@link gov.cdc.izgw.v2tofhir.terminology.Mapping#map(Coding)} directly.
     * @see gov.cdc.izgw.v2tofhir.terminology.Mapping#map(Coding)
     */
    @Deprecated(since = "moved to gov.cdc.izgw.v2tofhir.terminology; use TerminologyMapper")
    public static Coding map(Coding coding) {
        return gov.cdc.izgw.v2tofhir.terminology.Mapping.map(coding);
    }

    /**
     * Map the V2 system value found in {@code ident.system} to the system URI expected in FHIR.
     *
     * <p>If the system is changed, the original value is stored and can later be retrieved via
     * {@code ident.getUserData(Mapping.ORIGINAL_SYSTEM)}.</p>
     *
     * @param ident     The identifier to adjust the system for
     * @return          The updated Identifier
     * @deprecated Use {@link gov.cdc.izgw.v2tofhir.terminology.TerminologyMapper#normalizeSystem(String)} instead.
     * @see gov.cdc.izgw.v2tofhir.terminology.Mapping#mapSystem(Identifier)
     */
    @Deprecated(since = "moved to gov.cdc.izgw.v2tofhir.terminology; use TerminologyMapper")
    public static Identifier mapSystem(Identifier ident) {
        return gov.cdc.izgw.v2tofhir.terminology.Mapping.mapSystem(ident);
    }

    /**
     * Given a coding system name or URL, get the preferred {@link CodeSystem} URL for FHIR.
     *
     * @param value     A coding system name or URL
     * @return          The preferred name as a URL
     * @deprecated Use {@link gov.cdc.izgw.v2tofhir.terminology.TerminologyMapper#v2TableUri(String)} instead.
     * @see gov.cdc.izgw.v2tofhir.terminology.Mapping#mapTableNameToSystem(String)
     */
    @Deprecated(since = "moved to gov.cdc.izgw.v2tofhir.terminology; use TerminologyMapper")
    public static String mapTableNameToSystem(String value) {
        return gov.cdc.izgw.v2tofhir.terminology.Mapping.mapTableNameToSystem(value);
    }

    /**
     * Return the normalized FHIR system name for the given V2 table.
     *
     * @param table     The V2 table name
     * @return          The normalized FHIR system name for that table
     * @deprecated Use {@link gov.cdc.izgw.v2tofhir.terminology.TerminologyMapper#v2TableUri(String)} instead.
     * @see gov.cdc.izgw.v2tofhir.terminology.Mapping#v2Table(String)
     */
    @Deprecated(since = "moved to gov.cdc.izgw.v2tofhir.terminology; use TerminologyMapper")
    public static String v2Table(String table) {
        return gov.cdc.izgw.v2tofhir.terminology.Mapping.v2Table(table);
    }

    /**
     * Load a ConceptMap from CSV content and add it to the mapping lookup tables.
     *
     * <p>CSV format expected:</p>
     * <ul>
     *   <li>Line 1: Header row (ignored)</li>
     *   <li>Line 2: Column headers</li>
     *   <li>All other lines: Mapping data rows</li>
     * </ul>
     * <p>If a mapping with the same name already exists it will be overwritten.
     * Invalid rows are logged and skipped.</p>
     *
     * @param csvContent    The CSV content as a string
     * @param name          The name for this mapping (used as the map identifier)
     * @deprecated Use {@link gov.cdc.izgw.v2tofhir.terminology.Mapping#loadConceptMapFromCsv(String, String)} directly.
     * @see gov.cdc.izgw.v2tofhir.terminology.Mapping#loadConceptMapFromCsv(String, String)
     */
    @Deprecated(since = "moved to gov.cdc.izgw.v2tofhir.terminology; use TerminologyMapper")
    public static synchronized void loadConceptMapFromCsv(String csvContent, String name) {
        gov.cdc.izgw.v2tofhir.terminology.Mapping.loadConceptMapFromCsv(csvContent, name);
    }

    /**
     * Load a CodeSystem from CSV content and add it to the coding lookup tables.
     *
     * <p>CSV format expected:</p>
     * <ul>
     *   <li>Line 1: Metadata (url, oid, name)</li>
     *   <li>Line 2: Column headers (Code, Display, Definition, V2 Concept Comment, …)</li>
     *   <li>All other lines: Code definition rows</li>
     * </ul>
     * <p>If a CodeSystem with the same name already exists it will be overwritten.
     * Invalid rows are logged and skipped.</p>
     *
     * @param csvContent    The CSV content as a string
     * @deprecated Use {@link gov.cdc.izgw.v2tofhir.terminology.Mapping#loadCodeSystemFromCsv(String)} directly.
     * @see gov.cdc.izgw.v2tofhir.terminology.Mapping#loadCodeSystemFromCsv(String)
     */
    @Deprecated(since = "moved to gov.cdc.izgw.v2tofhir.terminology; use TerminologyMapper")
    public static synchronized void loadCodeSystemFromCsv(String csvContent) {
        gov.cdc.izgw.v2tofhir.terminology.Mapping.loadCodeSystemFromCsv(csvContent);
    }
}
