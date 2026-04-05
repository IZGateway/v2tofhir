package gov.cdc.izgw.v2tofhir.utils;

import java.util.List;
import java.util.Set;

import org.hl7.fhir.r4.model.NamingSystem;

/**
 * Authoritative constants for well-known coding system and namespace identifiers (URIs, OIDs,
 * and names). These constants are stable and may be used freely throughout the codebase.
 *
 * <p>The operational registry methods in this class (system-name lookup, OID conversion, etc.)
 * are deprecated. Use {@link gov.cdc.izgw.v2tofhir.terminology.TerminologyMapper} via
 * {@link gov.cdc.izgw.v2tofhir.terminology.TerminologyMapperFactory#get()} for new code.</p>
 */
public final class Systems {
    private Systems() {}

    /** @see gov.cdc.izgw.v2tofhir.terminology.Systems#ACT_CODE_OID */
    public static final String ACT_CODE_OID = gov.cdc.izgw.v2tofhir.terminology.Systems.ACT_CODE_OID;
    /** @see gov.cdc.izgw.v2tofhir.terminology.Systems#CDCGS1VIS */
    public static final String CDCGS1VIS = gov.cdc.izgw.v2tofhir.terminology.Systems.CDCGS1VIS;
    /** @see gov.cdc.izgw.v2tofhir.terminology.Systems#CDCGS1VIS_OID */
    public static final String CDCGS1VIS_OID = gov.cdc.izgw.v2tofhir.terminology.Systems.CDCGS1VIS_OID;
    /** @see gov.cdc.izgw.v2tofhir.terminology.Systems#CDCPHINVS */
    public static final String CDCPHINVS = gov.cdc.izgw.v2tofhir.terminology.Systems.CDCPHINVS;
    /** @see gov.cdc.izgw.v2tofhir.terminology.Systems#CDCPHINVS_OID */
    public static final String CDCPHINVS_OID = gov.cdc.izgw.v2tofhir.terminology.Systems.CDCPHINVS_OID;
    /** @see gov.cdc.izgw.v2tofhir.terminology.Systems#CDCREC */
    public static final String CDCREC = gov.cdc.izgw.v2tofhir.terminology.Systems.CDCREC;
    /** @see gov.cdc.izgw.v2tofhir.terminology.Systems#CDCREC_OID */
    public static final String CDCREC_OID = gov.cdc.izgw.v2tofhir.terminology.Systems.CDCREC_OID;
    /** @see gov.cdc.izgw.v2tofhir.terminology.Systems#CDT */
    public static final String CDT = gov.cdc.izgw.v2tofhir.terminology.Systems.CDT;
    /** @see gov.cdc.izgw.v2tofhir.terminology.Systems#CDT_OID */
    public static final String CDT_OID = gov.cdc.izgw.v2tofhir.terminology.Systems.CDT_OID;
    /** @see gov.cdc.izgw.v2tofhir.terminology.Systems#CPT */
    public static final String CPT = gov.cdc.izgw.v2tofhir.terminology.Systems.CPT;
    /** @see gov.cdc.izgw.v2tofhir.terminology.Systems#CPT_OID */
    public static final String CPT_OID = gov.cdc.izgw.v2tofhir.terminology.Systems.CPT_OID;
    /** @see gov.cdc.izgw.v2tofhir.terminology.Systems#CVX */
    public static final String CVX = gov.cdc.izgw.v2tofhir.terminology.Systems.CVX;
    /** @see gov.cdc.izgw.v2tofhir.terminology.Systems#CVX_OID */
    public static final String CVX_OID = gov.cdc.izgw.v2tofhir.terminology.Systems.CVX_OID;
    /** @see gov.cdc.izgw.v2tofhir.terminology.Systems#DICOM */
    public static final String DICOM = gov.cdc.izgw.v2tofhir.terminology.Systems.DICOM;
    /** @see gov.cdc.izgw.v2tofhir.terminology.Systems#DICOM_OID */
    public static final String DICOM_OID = gov.cdc.izgw.v2tofhir.terminology.Systems.DICOM_OID;
    /** @see gov.cdc.izgw.v2tofhir.terminology.Systems#IETF */
    public static final String IETF = gov.cdc.izgw.v2tofhir.terminology.Systems.IETF;
    /** @see gov.cdc.izgw.v2tofhir.terminology.Systems#IETF_OID */
    public static final String IETF_OID = gov.cdc.izgw.v2tofhir.terminology.Systems.IETF_OID;
    /** @see gov.cdc.izgw.v2tofhir.terminology.Systems#HCPCS */
    public static final String HCPCS = gov.cdc.izgw.v2tofhir.terminology.Systems.HCPCS;
    /** @see gov.cdc.izgw.v2tofhir.terminology.Systems#HCPCS_OID */
    public static final String HCPCS_OID = gov.cdc.izgw.v2tofhir.terminology.Systems.HCPCS_OID;
    /** @see gov.cdc.izgw.v2tofhir.terminology.Systems#HSLOC */
    public static final String HSLOC = gov.cdc.izgw.v2tofhir.terminology.Systems.HSLOC;
    /** @see gov.cdc.izgw.v2tofhir.terminology.Systems#HSLOC_OID */
    public static final String HSLOC_OID = gov.cdc.izgw.v2tofhir.terminology.Systems.HSLOC_OID;
    /** @see gov.cdc.izgw.v2tofhir.terminology.Systems#ICD10CM */
    public static final String ICD10CM = gov.cdc.izgw.v2tofhir.terminology.Systems.ICD10CM;
    /** @see gov.cdc.izgw.v2tofhir.terminology.Systems#ICD10CM_OID */
    public static final String ICD10CM_OID = gov.cdc.izgw.v2tofhir.terminology.Systems.ICD10CM_OID;
    /** @see gov.cdc.izgw.v2tofhir.terminology.Systems#ICD10PCS */
    public static final String ICD10PCS = gov.cdc.izgw.v2tofhir.terminology.Systems.ICD10PCS;
    /** @see gov.cdc.izgw.v2tofhir.terminology.Systems#ICD10PCS_OID */
    public static final String ICD10PCS_OID = gov.cdc.izgw.v2tofhir.terminology.Systems.ICD10PCS_OID;
    /** @see gov.cdc.izgw.v2tofhir.terminology.Systems#ICD9CM */
    public static final String ICD9CM = gov.cdc.izgw.v2tofhir.terminology.Systems.ICD9CM;
    /** @see gov.cdc.izgw.v2tofhir.terminology.Systems#ICD9CM_OID */
    public static final String ICD9CM_OID = gov.cdc.izgw.v2tofhir.terminology.Systems.ICD9CM_OID;
    /** @see gov.cdc.izgw.v2tofhir.terminology.Systems#ICD9PCS */
    public static final String ICD9PCS = gov.cdc.izgw.v2tofhir.terminology.Systems.ICD9PCS;
    /** @see gov.cdc.izgw.v2tofhir.terminology.Systems#ICD9PCS_OID */
    public static final String ICD9PCS_OID = gov.cdc.izgw.v2tofhir.terminology.Systems.ICD9PCS_OID;
    /** @see gov.cdc.izgw.v2tofhir.terminology.Systems#UNIVERSAL_ID_TYPE */
    public static final String UNIVERSAL_ID_TYPE = gov.cdc.izgw.v2tofhir.terminology.Systems.UNIVERSAL_ID_TYPE;
    /** @see gov.cdc.izgw.v2tofhir.terminology.Systems#UNIVERSAL_ID_TYPE_OID */
    public static final String UNIVERSAL_ID_TYPE_OID = gov.cdc.izgw.v2tofhir.terminology.Systems.UNIVERSAL_ID_TYPE_OID;
    /** @see gov.cdc.izgw.v2tofhir.terminology.Systems#ID_TYPE */
    public static final String ID_TYPE = gov.cdc.izgw.v2tofhir.terminology.Systems.ID_TYPE;
    /** @see gov.cdc.izgw.v2tofhir.terminology.Systems#ID_TYPE_OID */
    public static final String ID_TYPE_OID = gov.cdc.izgw.v2tofhir.terminology.Systems.ID_TYPE_OID;
    /** @see gov.cdc.izgw.v2tofhir.terminology.Systems#LOINC */
    public static final String LOINC = gov.cdc.izgw.v2tofhir.terminology.Systems.LOINC;
    /** @see gov.cdc.izgw.v2tofhir.terminology.Systems#LOINC_OID */
    public static final String LOINC_OID = gov.cdc.izgw.v2tofhir.terminology.Systems.LOINC_OID;
    /** @see gov.cdc.izgw.v2tofhir.terminology.Systems#MVX */
    public static final String MVX = gov.cdc.izgw.v2tofhir.terminology.Systems.MVX;
    /** @see gov.cdc.izgw.v2tofhir.terminology.Systems#MVX_OID */
    public static final String MVX_OID = gov.cdc.izgw.v2tofhir.terminology.Systems.MVX_OID;
    /** @see gov.cdc.izgw.v2tofhir.terminology.Systems#NDC */
    public static final String NDC = gov.cdc.izgw.v2tofhir.terminology.Systems.NDC;
    /** @see gov.cdc.izgw.v2tofhir.terminology.Systems#NDC_OID */
    public static final String NDC_OID = gov.cdc.izgw.v2tofhir.terminology.Systems.NDC_OID;
    /** @see gov.cdc.izgw.v2tofhir.terminology.Systems#NCI */
    public static final String NCI = gov.cdc.izgw.v2tofhir.terminology.Systems.NCI;
    /** @see gov.cdc.izgw.v2tofhir.terminology.Systems#NCI_OID */
    public static final String NCI_OID = gov.cdc.izgw.v2tofhir.terminology.Systems.NCI_OID;
    /** @see gov.cdc.izgw.v2tofhir.terminology.Systems#NDFRT */
    public static final String NDFRT = gov.cdc.izgw.v2tofhir.terminology.Systems.NDFRT;
    /** @see gov.cdc.izgw.v2tofhir.terminology.Systems#NDFRT_OID */
    public static final String NDFRT_OID = gov.cdc.izgw.v2tofhir.terminology.Systems.NDFRT_OID;
    /** @see gov.cdc.izgw.v2tofhir.terminology.Systems#RXNORM */
    public static final String RXNORM = gov.cdc.izgw.v2tofhir.terminology.Systems.RXNORM;
    /** @see gov.cdc.izgw.v2tofhir.terminology.Systems#RXNORM_OID */
    public static final String RXNORM_OID = gov.cdc.izgw.v2tofhir.terminology.Systems.RXNORM_OID;
    /** @see gov.cdc.izgw.v2tofhir.terminology.Systems#SNOMED */
    public static final String SNOMED = gov.cdc.izgw.v2tofhir.terminology.Systems.SNOMED;
    /** @see gov.cdc.izgw.v2tofhir.terminology.Systems#SNOMED_OID */
    public static final String SNOMED_OID = gov.cdc.izgw.v2tofhir.terminology.Systems.SNOMED_OID;
    /** @see gov.cdc.izgw.v2tofhir.terminology.Systems#SSN */
    public static final String SSN = gov.cdc.izgw.v2tofhir.terminology.Systems.SSN;
    /** @see gov.cdc.izgw.v2tofhir.terminology.Systems#SSN_OID */
    public static final String SSN_OID = gov.cdc.izgw.v2tofhir.terminology.Systems.SSN_OID;
    /** @see gov.cdc.izgw.v2tofhir.terminology.Systems#UCUM */
    public static final String UCUM = gov.cdc.izgw.v2tofhir.terminology.Systems.UCUM;
    /** @see gov.cdc.izgw.v2tofhir.terminology.Systems#UCUM_OID */
    public static final String UCUM_OID = gov.cdc.izgw.v2tofhir.terminology.Systems.UCUM_OID;
    /** @see gov.cdc.izgw.v2tofhir.terminology.Systems#V3_PARTICIPATION_TYPE */
    public static final String V3_PARTICIPATION_TYPE = gov.cdc.izgw.v2tofhir.terminology.Systems.V3_PARTICIPATION_TYPE;
    /** @see gov.cdc.izgw.v2tofhir.terminology.Systems#V3_ACT_ENCOUNTER_CODE */
    public static final String V3_ACT_ENCOUNTER_CODE = gov.cdc.izgw.v2tofhir.terminology.Systems.V3_ACT_ENCOUNTER_CODE;
    /** @see gov.cdc.izgw.v2tofhir.terminology.Systems#DATA_ABSENT */
    public static final String DATA_ABSENT = gov.cdc.izgw.v2tofhir.terminology.Systems.DATA_ABSENT;
    /** @see gov.cdc.izgw.v2tofhir.terminology.Systems#DATA_ABSENT_OID */
    public static final String DATA_ABSENT_OID = gov.cdc.izgw.v2tofhir.terminology.Systems.DATA_ABSENT_OID;
    /** @see gov.cdc.izgw.v2tofhir.terminology.Systems#NULL_FLAVOR */
    public static final String NULL_FLAVOR = gov.cdc.izgw.v2tofhir.terminology.Systems.NULL_FLAVOR;
    /** @see gov.cdc.izgw.v2tofhir.terminology.Systems#NULL_FLAVOR_OID */
    public static final String NULL_FLAVOR_OID = gov.cdc.izgw.v2tofhir.terminology.Systems.NULL_FLAVOR_OID;
    /** @see gov.cdc.izgw.v2tofhir.terminology.Systems#LOCATION_TYPE */
    public static final String LOCATION_TYPE = gov.cdc.izgw.v2tofhir.terminology.Systems.LOCATION_TYPE;
    /** @see gov.cdc.izgw.v2tofhir.terminology.Systems#LOCATION_TYPE_OID */
    public static final String LOCATION_TYPE_OID = gov.cdc.izgw.v2tofhir.terminology.Systems.LOCATION_TYPE_OID;

    /** @see gov.cdc.izgw.v2tofhir.terminology.Systems#IDENTIFIER_TYPES */
    public static final Set<String> IDENTIFIER_TYPES = gov.cdc.izgw.v2tofhir.terminology.Systems.IDENTIFIER_TYPES;
    /** @see gov.cdc.izgw.v2tofhir.terminology.Systems#ID_TYPES */
    public static final Set<String> ID_TYPES = gov.cdc.izgw.v2tofhir.terminology.Systems.ID_TYPES;

    /**
     * Get a list of aliases known by the V2 Converter for different coding and identifier systems.
     *
     * <p>Returns a list of string lists containing aliases for systems. For each system,
     * the FHIR preferred system URI is first in its sublist, the commonly used V2 namespace
     * is second, and the OID for the system is last.</p>
     *
     * @return  A list of string lists containing aliases for a coding or identifier system
     * @see gov.cdc.izgw.v2tofhir.terminology.Systems#getCodeSystemAliases()
     * @deprecated Use {@link gov.cdc.izgw.v2tofhir.terminology.TerminologyMapper} instead. */
    @Deprecated(since = "use TerminologyMapper")
    public static List<List<String>> getCodeSystemAliases() {
        return gov.cdc.izgw.v2tofhir.terminology.Systems.getCodeSystemAliases();
    }

    /**
     * Add a naming system to the map of known systems.
     *
     * <p>The first entry in {@code list} must be the URI for the system.</p>
     *
     * @param name  The name of the system
     * @param list  A list of strings containing aliases for a coding or identifier system;
     *              the first entry must be the URI
     * @see gov.cdc.izgw.v2tofhir.terminology.Systems#addNamingSystem(String, String...)
     * @deprecated Use {@link gov.cdc.izgw.v2tofhir.terminology.TerminologyMapper} instead. */
    @Deprecated(since = "use TerminologyMapper")
    public static void addNamingSystem(String name, String... list) {
        gov.cdc.izgw.v2tofhir.terminology.Systems.addNamingSystem(name, list);
    }

    /**
     * Create a naming system with the given URI and name.
     *
     * @param uri   The URI for the naming system
     * @param name  A common name for the naming system
     * @return      The created naming system
     * @see gov.cdc.izgw.v2tofhir.terminology.Systems#createNamingSystem(String, String)
     * @deprecated Use {@link gov.cdc.izgw.v2tofhir.terminology.TerminologyMapper} instead. */
    @Deprecated(since = "use TerminologyMapper")
    public static NamingSystem createNamingSystem(String uri, String name) {
        return gov.cdc.izgw.v2tofhir.terminology.Systems.createNamingSystem(uri, name);
    }

    /**
     * Add a unique id to a naming system if it does not already exist.
     *
     * @param ns    The naming system to update
     * @param uid   The unique id to add
     * @see gov.cdc.izgw.v2tofhir.terminology.Systems#updateNamingSystem(NamingSystem, String)
     * @deprecated Use {@link gov.cdc.izgw.v2tofhir.terminology.TerminologyMapper} instead. */
    @Deprecated(since = "use TerminologyMapper")
    public static void updateNamingSystem(NamingSystem ns, String uid) {
        gov.cdc.izgw.v2tofhir.terminology.Systems.updateNamingSystem(ns, uid);
    }

    /**
     * Given a URI, get the associated OID (without the {@code urn:oid:} prefix).
     *
     * @param uri   The URI to look up
     * @return      The associated OID, or {@code null} if none is known
     * @see gov.cdc.izgw.v2tofhir.terminology.Systems#toOid(String)
     * @deprecated Use {@link gov.cdc.izgw.v2tofhir.terminology.TerminologyMapper#toOid(String)} instead. */
    @Deprecated(since = "use TerminologyMapper")
    public static String toOid(String uri) {
        return gov.cdc.izgw.v2tofhir.terminology.Systems.toOid(uri);
    }

    /**
     * Get the commonly used V2 name for a coding or identifier system.
     *
     * @param uri   The system URI
     * @return      The commonly used V2 name, or {@code null} if the name is unknown
     * @see gov.cdc.izgw.v2tofhir.terminology.Systems#toTextName(String)
     * @deprecated Use {@link gov.cdc.izgw.v2tofhir.terminology.TerminologyMapper} instead. */
    @Deprecated(since = "use TerminologyMapper")
    public static String toTextName(String uri) {
        return gov.cdc.izgw.v2tofhir.terminology.Systems.toTextName(uri);
    }

    /**
     * Given an OID (with or without the {@code urn:oid:} prefix), get the associated URI.
     *
     * @param oid   The OID to look up
     * @return      The associated URI, or {@code null} if none is known
     * @see gov.cdc.izgw.v2tofhir.terminology.Systems#toUri(String)
     * @deprecated Use {@link gov.cdc.izgw.v2tofhir.terminology.TerminologyMapper#toUri(String)} instead. */
    @Deprecated(since = "use TerminologyMapper")
    public static String toUri(String oid) {
        return gov.cdc.izgw.v2tofhir.terminology.Systems.toUri(oid);
    }

    /**
     * Get all aliases by which the given system is known.
     *
     * @param system    The system URI, OID, or name to look up
     * @return          A list of names the system is known by
     * @see gov.cdc.izgw.v2tofhir.terminology.Systems#getSystemNames(String)
     * @deprecated Use {@link gov.cdc.izgw.v2tofhir.terminology.TerminologyMapper} instead. */
    @Deprecated(since = "use TerminologyMapper")
    public static List<String> getSystemNames(String system) {
        return gov.cdc.izgw.v2tofhir.terminology.Systems.getSystemNames(system);
    }

    /**
     * Get the {@link NamingSystem} for a given name, system URI, or other alias.
     *
     * @param system    The search value (name, URI, or OID)
     * @return          The NamingSystem, or {@code null} if not found
     * @see gov.cdc.izgw.v2tofhir.terminology.Systems#getNamingSystem(String)
     * @deprecated Use {@link gov.cdc.izgw.v2tofhir.terminology.TerminologyMapper#getNamingSystem(String)} instead. */
    @Deprecated(since = "use TerminologyMapper")
    public static NamingSystem getNamingSystem(String system) {
        return gov.cdc.izgw.v2tofhir.terminology.Systems.getNamingSystem(system);
    }
}
