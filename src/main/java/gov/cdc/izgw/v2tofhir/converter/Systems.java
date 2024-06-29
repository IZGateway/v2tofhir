package gov.cdc.izgw.v2tofhir.converter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.NamingSystem;
import org.hl7.fhir.r4.model.NamingSystem.NamingSystemIdentifierType;
import org.hl7.fhir.r4.model.NamingSystem.NamingSystemUniqueIdComponent;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Systems {
	public static final String CDCGS1VIS = "urn:oid:2.16.840.1.114222.4.5.307";
	public static final String CDCGS1VIS_OID = "2.16.840.1.114222.4.5.307";
	public static final String CDCPHINVS = "urn:oid:2.16.840.1.113883.12.471";
	public static final String CDCPHINVS_OID = "2.16.840.1.113883.12.471";
	public static final String CDCREC = "urn:oid:2.16.840.1.113883.6.238";
	public static final String CDCREC_OID = "2.16.840.1.113883.6.238";
	public static final String CPT = "http://www.ama-assn.org/go/cpt";
	public static final String CPT_OID = "2.16.840.1.113883.6.12";
	public static final String CVX = "http://hl7.org/fhir/sid/cvx";
	public static final String CVX_OID = "2.16.840.1.113883.12.292";
	public static final String DICOM = "http://dicom.nema.org/resources/ontology/DCM";
	public static final String DICOM_OID = "1.2.840.10008.2.16.4";
	public static final String IETF = "urn:ietf:rfc:3986";
	public static final String IETF_OID = "urn:ietf:rfc:3986";
	public static final String HSLOC = "https://www.cdc.gov/nhsn/cdaportal/terminology/codesystem/hsloc.html";
	public static final String HSLOC_OID = "2.16.840.1.113883.6.259";
	public static final String ICD10CM = "http://hl7.org/fhir/sid/icd-10";
	public static final String ICD10CM_OID = "2.16.840.1.113883.6.3";
	public static final String ICD9CM = "http://hl7.org/fhir/sid/icd-9-cm";
	public static final String ICD9CM_OID = "2.16.840.1.113883.6.103";
	public static final String ICD9PCS = "http://hl7.org/fhir/sid/icd-9-cm";
	public static final String ICD9PCS_OID = "2.16.840.1.113883.6.104";
	public static final String IDENTIFIER_TYPE = "http://terminology.hl7.org/CodeSystem/v2-0301";
	public static final String IDENTIFIER_TYPE_OID = "2.16.840.1.113883.18.108";
	public static final String IDTYPE = "http://terminology.hl7.org/CodeSystem/v2-0203";
	public static final String IDTYPE_OID = "2.16.840.1.113883.18.186";
	public static final String LOINC = "http://loinc.org";
	public static final String LOINC_OID = "2.16.840.1.113883.6.1";
	public static final String MVX = "http://hl7.org/fhir/sid/mvx";
	public static final String MVX_OID = "2.16.840.1.113883.12.227";
	public static final String NDC = "http://hl7.org/fhir/sid/ndc";
	public static final String NDC_OID = "2.16.840.1.113883.6.69";
	public static final String NCI_OID = "2.16.840.1.113883.3.26.1.1";
	public static final String NCI = "http://ncicb.nci.nih.gov/xml/owl/EVS/Thesaurus.owl";
	public static final String NDFRT = "http://hl7.org/fhir/ndfrt";
	public static final String NDFRT_OID = "2.16.840.1.113883.6.209";
	public static final String RXNORM = "http://www.nlm.nih.gov/research/umls/rxnorm";
	public static final String RXNORM_OID = "2.16.840.1.113883.6.88";
	public static final String SNOMED = "http://snomed.info/sct";
	public static final String SNOMED_OID = "2.16.840.1.113883.6.96";
	public static final String UCUM = "http://unitsofmeasure.org";
	public static final String UCUM_OID = "2.16.840.1.113883.6.8";


	static final String[] IDTYPE_NAMES =  { IDTYPE, "HL70203", "0203", "IDTYPE", "2.16.840.1.113883.12.203", IDTYPE_OID };
	static final String[] IDENTIFIER_TYPE_NAMES = { IDENTIFIER_TYPE, "HL70301", "0301", "2.16.840.1.113883.12.301", IDENTIFIER_TYPE_OID };
	
	private static final String[][] idTypeToDisplay = { { "CLIA", "Clinical Laboratory Improvement Amendments" },
			{ "CLIP", "Clinical laboratory Improvement Program" }, { "DNS", "An Internet host name" },
			{ "EUI64", "IEEE 64-bit Extended Unique Identifier" }, { "GUID", "Same as UUID" },
			{ "HCD", "The CEN Healthcare Coding Scheme Designator" }, { "HL7", "HL7 registration schemes" },
			{ "ISO", "An International Standards Organization Object Identifier (OID)" },
			{ "L", "First Locally defined coding entity identifier" },
			{ "M", "Second Locally defined coding entity identifier" },
			{ "N", "Third Locally defined coding entity identifier" },
			{ "Random", "Usually a base64 encoded string of random bits" }, { "URI", "Uniform Resource Identifier" },
			{ "UUID", "The DCE Universal Unique Identifier" }, { "x400", "An X.400 MHS identifier" },
			{ "x500", "An X.500 directory name" } };
	static Map<String, String> idTypeToDisplayMap = new LinkedHashMap<>();
	static {
		for (String[] pair : idTypeToDisplay) {
			idTypeToDisplayMap.put(pair[0], pair[1]);
		}
	}
	/** 
	 * All the types from 
	 * Official URL: http://terminology.hl7.org/CodeSystem/v2-0203	Version: 4.0.0
	 * Active as of 2022-12-07	Responsible: Health Level Seven International	Computable Name: IdentifierType
	 * Other Identifiers: urn:ietf:rfc:3986#Uniform Resource Identifier (URI)#urn:oid:2.16.840.1.113883.18.108
	 **/
	public static final Set<String> IDENTIFIER_TYPES = idTypeToDisplayMap.keySet(); 
	static final Set<String> ID_TYPES = new LinkedHashSet<>(
		Arrays.asList("AC", "ACSN", "AIN", "AM", "AMA", "AN",
			"ANC", "AND", "ANON", "ANT", "APRN", "ASID", "BA", "BC", "BCFN", "BCT", "BR", "BRN", "BSNR", "CAAI", "CC",
			"CONM", "CY", "CZ", "DC", "DCFN", "DDS", "DEA", "DFN", "DI", "DL", "DN", "DO", "DP", "DPM", "DR", "DS",
			"DSG", "EI", "EN", "ESN", "FDR", "FDRFN", "FGN", "FI", "FILL", "GI", "GIN", "GL", "GN", "HC", "IND",
			"IRISTEM", "JHN", "LACSN", "LANR", "LI", "L&I", "LN", "LR", "MA", "MB", "MC", "MCD", "MCN", "MCR", "MCT",
			"MD", "MI", "MR", "MRT", "MS", "NBSNR", "NCT", "NE", "NH", "NI", "NII", "NIIP", "NNxxx", "NP", "NPI", "OBI",
			"OD", "PA", "PC", "PCN", "PE", "PEN", "PGN", "PHC", "PHE", "PHO", "PI", "PIN", "PLAC", "PN", "PNT", "PPIN",
			"PPN", "PRC", "PRN", "PT", "QA", "RI", "RN", "RPH", "RR", "RRI", "RRP", "SAMN", "SB", "SID", "SL", "SN",
			"SNBSN", "SNO", "SP", "SR", "SRX", "SS", "STN", "TAX", "TN", "TPR", "TRL", "U", "UDI", "UPIN", "USID", "VN",
			"VP", "VS", "WC", "WCN", "WP", "XV", "XX")
	);

	// See https://hl7-definition.caristix.com/v2/HL7v2.8/Tables/0396
	private static final String[][] stringToUri = {
		{ CDCGS1VIS, "CDCGS1VIS", CDCGS1VIS_OID },
		{ CDCPHINVS, "CDCPHINVS", CDCPHINVS_OID },
		{ CDCREC, "CDCREC", CDCREC_OID },
		{ CPT, "CPT", "C4", "CPT4", CPT_OID},
		{ CVX, "CVX", CVX_OID },
		{ DICOM, "DCM", "DICOM", DICOM_OID },
		{ HSLOC, "HSLOC", HSLOC_OID },
		{ ICD10CM, "ICD10CM", ICD10CM_OID },
		{ ICD9CM, "ICD9CM", "I9CDX", "I9C", ICD9CM_OID },
		{ ICD9PCS, "ICD9PCS", "I9CP", ICD9PCS_OID },
		IDENTIFIER_TYPE_NAMES,
		IDTYPE_NAMES,
		{ LOINC, "LOINC", "LN", "LNC", LOINC_OID },
		{ MVX, "MVX", MVX_OID },
		{ NCI, "NCI", "NCIT", NCI_OID },
		{ NDC, "NDC", NDC_OID },
		{ NDFRT, "NDFRT", "NDF-RT", NDFRT_OID },
		{ RXNORM, "RXNORM", RXNORM_OID },
		{ SNOMED, "SNOMEDCT", "SNOMED", "SNM", "SCT", SNOMED_OID },
		{ UCUM, "UCUM", UCUM_OID },
	};
	
	public static List<List<String>> getCodeSystemAliases() {
		List<List<String>> a = new ArrayList<>();
		for (String[] s : stringToUri) {
			a.add(Arrays.asList(s));
		}
		return a;
	}
	
	static Map<String, NamingSystem> namingSystems = new LinkedHashMap<>();
	static {
		NamingSystem ns = null;
		for (String[] list: stringToUri) {
			String uri = list[0];
			ns = createNamingSystem(uri, list[1]);
			for (String s: list) {
				if (!uri.equals(s)) {
					updateNamingSystem(ns, s);
				}
				namingSystems.put(s, ns);
			}
		}
	}

	private Systems() {
	}
	
	private static NamingSystem createNamingSystem(String uri, String name) {
		NamingSystem ns = new NamingSystem();
		ns.setUrl(uri);
		NamingSystemUniqueIdComponent uid = ns.addUniqueId();
		uid.setType(NamingSystemIdentifierType.URI);
		uid.setValue(uri);
		uid.setPreferred(true);
		return ns;
	}
	
	private static void updateNamingSystem(NamingSystem ns, String uid) {
		for (NamingSystemUniqueIdComponent uniqueId : ns.getUniqueId()) {
			if (uid.equals(uniqueId.getValue())) {
				return;
			}
		}
		NamingSystemUniqueIdComponent uniqueId = ns.addUniqueId();
		uniqueId.setValue(uid);
		uniqueId.setPreferred(false);
		if (uid.matches("^([\\-a-z0-9]+):.*$")) {
			uniqueId.setType(NamingSystemIdentifierType.URI);
		} else if (uid.matches("^\\d+(.\\d+)+$")) {
			uniqueId.setType(NamingSystemIdentifierType.OID);
		} else if (uid.matches("^[0-9A-Fa-f]+(-[0-9A-Fa-f]+)+$")) {
			uniqueId.setType(NamingSystemIdentifierType.UUID);
		} else {
			uniqueId.setType(NamingSystemIdentifierType.OTHER);
		}
		namingSystems.put(uid, ns);
	}

	/**
	 * Given a URI, get the associated OID
	 * @param uri
	 * @return the associated OID or null if none is known (without the urn:oid: prefix).
	 */
	public static String toOid(String uri) {
		if (StringUtils.isEmpty(uri)) {
			return null;
		}
		NamingSystem ns = namingSystems.get(uri);
		if (ns == null) {
			return null;
		}
		for (NamingSystemUniqueIdComponent uid : ns.getUniqueId()) {
			if (NamingSystemIdentifierType.OID.equals(uid.getType())) {
				return uid.getValue();
			}
		}
		return null;
	}
	
	public static String toTextName(String uri) {
		if (StringUtils.isEmpty(uri)) {
			return "";
		}
		NamingSystem ns = namingSystems.get(uri);
		if (ns == null) {
			return null;
		}
		for (NamingSystemUniqueIdComponent uid : ns.getUniqueId()) {
			if (!uid.getPreferred()) {  // First non-preferred uinque id is used as name 
				return uid.getValue();
			}
		}
		if (ns.hasUniqueId()) {
			return ns.getUniqueIdFirstRep().getValue();
		}
		return null;
		
	}
	/**
	 * Given a OID, get the associated URI
	 * @param oid The oid to look up, with or without urn:oid: prefix
	 * @return the associated URI or null if none is known
	 */
	public static String toUri(String oid) {
		if (oid.startsWith("urn:oid:")) {
			oid = oid.substring(8);
		}
		NamingSystem ns = namingSystems.get(oid);
		if (ns == null) {
			return null;
		}
		return ns.getUrl();
	}

	public static List<String> getSystemNames(String system) {
		if (StringUtils.isBlank(system)) {
			return Collections.emptyList();
		}
		system = StringUtils.trim(system);
		NamingSystem ns = getNamingSystem(system);
		List<String> found = null;
		if (ns == null) {
			found = new ArrayList<>();
		} else {
			found = ns.getUniqueId().stream().map(u -> u.getValue()).collect(Collectors.toCollection(ArrayList::new));
		}
		// Return a name for things that are obviously HL7 systems.
		if (system.matches("^(?i)(HL7-?|http://terminology.hl7.org/CodeSystem/v2-|)\\d{4}$") || 
			system.startsWith("2.16.840.1.113883.12.")	// V2 Table OID
		) {
			String name = "HL7" + StringUtils.right("000" + system, 4);
			if (found.isEmpty()) {
				found.add("http://terminology.hl7.org/CodeSystem/v2-" + StringUtils.right(name, 4));
			}
			found.add(name);
		}
		return found;
	}
	
	static NamingSystem getNamingSystem(String system) {
		if (StringUtils.isBlank(system)) {
			return null;
		}
		NamingSystem ns = namingSystems.get(system);
		if (ns == null) {
			// Normalize value by replacing any whitespace, hypens or underscores, and uppercasing the text.
			String normalizedValue = system.replace("[-_\\s]+", "").toUpperCase();
			ns = namingSystems.get(normalizedValue);
		}
		return ns;
	}
}
