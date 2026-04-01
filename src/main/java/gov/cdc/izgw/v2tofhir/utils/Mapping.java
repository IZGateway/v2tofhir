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

    /** @see gov.cdc.izgw.v2tofhir.terminology.Mapping#ORIGINAL_SYSTEM */
    public static final String ORIGINAL_SYSTEM = gov.cdc.izgw.v2tofhir.terminology.Mapping.ORIGINAL_SYSTEM;
    /** @see gov.cdc.izgw.v2tofhir.terminology.Mapping#ORIGINAL_DISPLAY */
    public static final String ORIGINAL_DISPLAY = gov.cdc.izgw.v2tofhir.terminology.Mapping.ORIGINAL_DISPLAY;
    /** @see gov.cdc.izgw.v2tofhir.terminology.Mapping#ORIGINAL */
    public static final String ORIGINAL = gov.cdc.izgw.v2tofhir.terminology.Mapping.ORIGINAL;
    /** @see gov.cdc.izgw.v2tofhir.terminology.Mapping#V2_TABLE_PREFIX */
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

    /** @see gov.cdc.izgw.v2tofhir.terminology.Mapping#getMapping(String) */
    @Deprecated(since = "moved to gov.cdc.izgw.v2tofhir.terminology; use TerminologyMapper")
    public static Mapping getMapping(String name) {
        gov.cdc.izgw.v2tofhir.terminology.Mapping m = gov.cdc.izgw.v2tofhir.terminology.Mapping.getMapping(name);
        return m == null ? null : new Mapping(m.getName());
    }

    /** @see gov.cdc.izgw.v2tofhir.terminology.Mapping#addCodes(CodeSystem, String[]) */
    public static void addCodes(CodeSystem cs, String[] fields) {
        gov.cdc.izgw.v2tofhir.terminology.Mapping.addCodes(cs, fields);
    }

    /** @see gov.cdc.izgw.v2tofhir.terminology.Mapping#createNamingSystem(CodeSystem) */
    public static NamingSystem createNamingSystem(CodeSystem cs) {
        return gov.cdc.izgw.v2tofhir.terminology.Mapping.createNamingSystem(cs);
    }

    /** @see gov.cdc.izgw.v2tofhir.terminology.Mapping#updateCodeLookup(Coding) */
    public static Map<String, Coding> updateCodeLookup(Coding coding) {
        return gov.cdc.izgw.v2tofhir.terminology.Mapping.updateCodeLookup(coding);
    }

    /** @see gov.cdc.izgw.v2tofhir.terminology.Mapping#getDisplay(Coding) */
    public static String getDisplay(Coding coding) {
        return gov.cdc.izgw.v2tofhir.terminology.Mapping.getDisplay(coding);
    }

    /** @see gov.cdc.izgw.v2tofhir.terminology.Mapping#hasDisplay(String, String) */
    public static boolean hasDisplay(String code, String table) {
        return gov.cdc.izgw.v2tofhir.terminology.Mapping.hasDisplay(code, table);
    }

    /** @see gov.cdc.izgw.v2tofhir.terminology.Mapping#getDisplay(String, String) */
    public static String getDisplay(String code, String table) {
        return gov.cdc.izgw.v2tofhir.terminology.Mapping.getDisplay(code, table);
    }

    /** @see gov.cdc.izgw.v2tofhir.terminology.Mapping#setDisplay(Coding) */
    public static void setDisplay(Coding coding) {
        gov.cdc.izgw.v2tofhir.terminology.Mapping.setDisplay(coding);
    }

    /** @see gov.cdc.izgw.v2tofhir.terminology.Mapping#reset(Type) */
    public static void reset(Type type) {
        gov.cdc.izgw.v2tofhir.terminology.Mapping.reset(type);
    }

    /** @see gov.cdc.izgw.v2tofhir.terminology.Mapping#reset(Coding) */
    public static void reset(Coding coding) {
        gov.cdc.izgw.v2tofhir.terminology.Mapping.reset(coding);
    }

    /** @see gov.cdc.izgw.v2tofhir.terminology.Mapping#reset(CodeableConcept) */
    public static void reset(CodeableConcept cc) {
        gov.cdc.izgw.v2tofhir.terminology.Mapping.reset(cc);
    }

    /** @see gov.cdc.izgw.v2tofhir.terminology.Mapping#map(Coding) */
    public static Coding map(Coding coding) {
        return gov.cdc.izgw.v2tofhir.terminology.Mapping.map(coding);
    }

    /** @see gov.cdc.izgw.v2tofhir.terminology.Mapping#mapSystem(Identifier) */
    public static Identifier mapSystem(Identifier ident) {
        return gov.cdc.izgw.v2tofhir.terminology.Mapping.mapSystem(ident);
    }

    /** @see gov.cdc.izgw.v2tofhir.terminology.Mapping#mapTableNameToSystem(String) */
    public static String mapTableNameToSystem(String value) {
        return gov.cdc.izgw.v2tofhir.terminology.Mapping.mapTableNameToSystem(value);
    }

    /** @see gov.cdc.izgw.v2tofhir.terminology.Mapping#v2Table(String) */
    public static String v2Table(String table) {
        return gov.cdc.izgw.v2tofhir.terminology.Mapping.v2Table(table);
    }

    /** @see gov.cdc.izgw.v2tofhir.terminology.Mapping#loadConceptMapFromCsv(String, String) */
    public static synchronized void loadConceptMapFromCsv(String csvContent, String name) {
        gov.cdc.izgw.v2tofhir.terminology.Mapping.loadConceptMapFromCsv(csvContent, name);
    }

    /** @see gov.cdc.izgw.v2tofhir.terminology.Mapping#loadCodeSystemFromCsv(String) */
    public static synchronized void loadCodeSystemFromCsv(String csvContent) {
        gov.cdc.izgw.v2tofhir.terminology.Mapping.loadCodeSystemFromCsv(csvContent);
    }
}
