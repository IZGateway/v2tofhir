package gov.cdc.izgw.v2tofhir.converter;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.NamingSystem;
import org.hl7.fhir.r4.model.NamingSystem.NamingSystemIdentifierType;
import org.hl7.fhir.r4.model.NamingSystem.NamingSystemUniqueIdComponent;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Systems {
	NamingSystem ns;
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
	public static final String LOINC = "http://loinc.org";
	public static final String LOINC_OID = "2.16.840.1.113883.6.1";
	public static final String MVX = "http://hl7.org/fhir/sid/mvx";
	public static final String MVX_OID = "2.16.840.1.113883.12.227";
	public static final String NDC = "http://hl7.org/fhir/sid/ndc";
	public static final String NDC_OID = "2.16.840.1.113883.6.69";
	public static final String NCI_OID = "2.16.840.1.113883.3.26.1.1";
	public static final String NCI = "http://ncicb.nci.nih.gov/xml/owl/EVS/Thesaurus.owl";
	public static final String NDFRT = "";
	public static final String NDFRT_OID = "";
	public static final String RXNORM = "http://www.nlm.nih.gov/research/umls/rxnorm";
	public static final String RXNORM_OID = "2.16.840.1.113883.6.88";
	public static final String SNOMED = "http://snomed.info/sct";
	public static final String SNOMED_OID = "2.16.840.1.113883.6.96";
	public static final String UCUM = "http://unitsofmeasure.org";
	public static final String UCUM_OID = "2.16.840.1.113883.6.8";

	// See https://hl7-definition.caristix.com/v2/HL7v2.8/Tables/0396
	private static final String[][] stringToUri = {
		{ CDCPHINVS, "CDCPHINVS", CDCPHINVS_OID },
		{ CDCREC, "CDCREC", CDCREC_OID },
		{ CPT, "CPT", "C4", "CPT4", CPT_OID},
		{ CVX, "CVX", CVX_OID },
		{ DICOM, "DCM", "DICOM", DICOM_OID },
		{ HSLOC, "HSLOC", HSLOC_OID },
		{ ICD10CM, "ICD10CM", ICD10CM_OID },
		{ ICD9CM, "ICD9CM", "I9CDX", "I9C", ICD9CM_OID },
		{ ICD9PCS, "ICD9PCS", "I9CP", ICD9PCS_OID },
		{ LOINC, "LOINC", "LN", "LNC", LOINC_OID },
		{ MVX, "MVX", MVX_OID },
		{ NCI, "NCI", "NCIT", NCI_OID },
		{ NDC, "NDC", NDC_OID },
		{ NDFRT, "NDFRT", NDFRT_OID },
		{ RXNORM, "RXNORM", RXNORM_OID },
		{ SNOMED, "SNOMEDCT", "SNOMED", "SNM", "SCT", SNOMED_OID },
		{ UCUM, "UCUM", UCUM_OID },
	};
	
	private static Map<String, NamingSystem> namingSystems = new LinkedHashMap<>();
	static {
		NamingSystem ns = null;
		for (String[] list: stringToUri) {
			String uri = list[0];
			ns = createNamingSystem(uri);
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
	
	private static NamingSystem createNamingSystem(String uri) {
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
	}

	public static String mapCodeSystem(String value) {
		if (StringUtils.isBlank(value)) {
			return null;
		}
		if (value.startsWith("urn:oid:")) {
			value = value.substring(8);
		} else if (value.startsWith("urn:uuid:")) {
			value = value.substring(9);
		}
		NamingSystem ns = namingSystems.get(value);
		if (ns == null) {
			// Normalize value by replacing any whitespace, hypens or underscores, and uppercasing the text.
			String normalizedValue = value.replace("[-_\\s]+", "").toUpperCase();
			ns = namingSystems.get(normalizedValue);
		}
		if (ns != null) {
			return ns.getUrl();
		}
		return value;
	}
	public static String mapIdSystem(String value) {
		return mapCodeSystem(value);
	}
	/**
	 * Given a URI, get the associated OID
	 * @param uri
	 * @return the associated OID or null if none is known (without the urn:oid: prefix).
	 */
	public static String toOid(String uri) {
		NamingSystem ns = namingSystems.get(uri);
		if (ns == null) {
			return null;
		}
		for (NamingSystemUniqueIdComponent uid : ns.getUniqueId()) {
			if (NamingSystemIdentifierType.OID.equals(ns.getType())) {
				return uid.getValue();
			}
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

	public static Collection<String> getSystemNames(String system) {
		NamingSystem ns = namingSystems.get(system);
		List<NamingSystemUniqueIdComponent> l = ns.getUniqueId();
		return l.stream().map(u -> u.getValue()).toList();
	}
	
	static NamingSystem getNamingSystem(String system) {
		return namingSystems.get(system);
	}
}
