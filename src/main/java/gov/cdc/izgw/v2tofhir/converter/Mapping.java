package gov.cdc.izgw.v2tofhir.converter;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ServiceConfigurationError;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.CodeSystem;
import org.hl7.fhir.r4.model.CodeSystem.ConceptDefinitionComponent;
import org.hl7.fhir.r4.model.CodeSystem.ConceptPropertyComponent;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.StringType;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.enums.CSVReaderNullFieldIndicator;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
public class Mapping {
	private static final PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

	private static Map<String, Mapping> codeMaps = new LinkedHashMap<>();
	private static Map<String, Map<String, Coding>> codingMaps = new LinkedHashMap<>();
	static {
		initConceptMaps();
		initVocabulary();
	}
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
	private static Map<String, String> idTypeToDisplayMap = new LinkedHashMap<>();
	static {
		for (String[] pair : idTypeToDisplay) {
			idTypeToDisplayMap.put(pair[0], pair[1]);
		}
	}

	private final String name;
	private Map<String, Coding> from = new LinkedHashMap<>();
	private Map<String, Coding> to = new LinkedHashMap<>();
	public static final String IDENTIFIER_TYPE = "http://terminology.hl7.org/CodeSystem/v2-0301";

	public Mapping(final String name) {
		this.name = name;
	}

	void lock() {
		from = Collections.unmodifiableMap(from);
		to = Collections.unmodifiableMap(to);
	}

	/**
	 * Initialize concept maps from V2-to-fhir CSV tables.
	 */
	private static void initConceptMaps() {
		Resource[] files;
		try {
			files = resolver.getResources("/coding/*.csv");
		} catch (IOException e) {
			log.error("Cannot load coding resources");
			throw new ServiceConfigurationError("Cannot load coding resources", e);

		}
		int fileno = 0;
		for (Resource file : files) {
			fileno++;
			String name = file.getFilename().split("_ ")[1].split(" ")[0];
			Mapping m = new Mapping(name);
			codeMaps.put(name, m);
			int line = 0;
			try (InputStreamReader sr = new InputStreamReader(file.getInputStream());
					CSVReader reader = new CSVReader(sr);) {
				reader.readNext(); // Skip first line header
				line++;
				String[] headers = reader.readNext();
				int[] indices = getHeaderIndices(headers);
				line++;
				String[] fields = null;

				while ((fields = reader.readNext()) != null) {
					addMappings(name, m, ++line, indices, fields);
				}
				log.debug("{}: Loaded {} lines from {}", fileno, line, name);

			} catch (Exception e) {
				log.warn("Unexpected {} reading {}({}): {}", e.getClass().getSimpleName(), file.getFilename(), line,
						e.getMessage(), e);
			}
			m.lock();
		}
	}

	private static void updateMaps(Mapping m, Coding here, Coding there, String altLookupName) {
		if (there != null && there.hasCode()) {
			m.getTo().put(there.getCode(), here);
			if (there.hasSystem()) {
				// Enable lookup of any from codes by system and code
				Map<String, Coding> cm = updateCodeLookup(there); 
				// Enable lookup also by HL7 table number.
				codingMaps.computeIfAbsent(altLookupName, k -> cm);
			}
		}
	}
	
	public static Map<String, Coding> updateCodeLookup(Coding coding) {
		Map<String, Coding> cm = codingMaps.computeIfAbsent(coding.getSystem(), k -> new LinkedHashMap<>());
		cm.put(coding.getCode(), coding);
		return cm;
	}

	private static void addMappings(String name, Mapping m, int line, int[] indices, String[] fields) {
		String table = get(fields, indices[2]);
		if (table == null) {
			log.trace("Missing table reading {}({}): {}", name, line, Arrays.asList(fields));
		}
		Coding from = new Coding(toFhirUri(table), get(fields, indices[0]), get(fields, indices[1]));
		if (from.isEmpty()) {
			from = null;
		}
		Coding to = new Coding(get(fields, indices[5]), get(fields, indices[3]), get(fields, indices[4]));
		if (to.isEmpty()) {
			to = null;
		}

		updateMaps(m, to, from, table);
		updateMaps(m, from, to, name);
	}

	private static void checkAllHeadersPresent(String[] headers, String[] headerStrings, int[] headerIndices)
			throws IOException {
		for (int i = 0; i < headerIndices.length - 1; i++) {
			if (headerIndices[i] >= headerIndices[i + 1]) {
				log.error("Missing headers, expcected {} but found {}", Arrays.asList(headerStrings),
						Arrays.asList(headers));
				throw new IOException("Missing Headers");
			}
		}
	}

	private static String get(String[] fields, int i) {
		if (i < fields.length && StringUtils.isNotBlank(fields[i])) {
			return fields[i].trim();
		}
		return null;
	}

	private static int[] getHeaderIndices(String[] headers) throws IOException {
		String[] headerStrings = { "Code", "Text", "Code System", "Code", "Display", "Code System" };
		int[] headerIndices = new int[headerStrings.length];
		int startLoc = 0;
		for (int i = 0; i < headerStrings.length; i++) {
			for (int j = startLoc; j < headers.length; j++) {
				if (headerStrings[i].equalsIgnoreCase(headers[j])) {
					headerIndices[i] = j;
					startLoc = j + 1;
					break;
				}
			}
		}
		checkAllHeadersPresent(headers, headerStrings, headerIndices);
		return headerIndices;
	}

	private static void initVocabulary() {
		initCVX();
		initMVX();
	}
	private static void initCVX() {
		CodeSystem cs = new CodeSystem();
		cs.setUrl("http://hl7.org/fhir/sid/cvx");
		cs.setTitle("Vaccines Administered");
		cs.setName("CVX");
		Identifier identifier = new Identifier();
		identifier.setSystem(Systems.IETF);
		identifier.setValue("urn:oid:2.16.840.1.113883.12.292");
		cs.addIdentifier(identifier);
		loadData(resolver.getResource("/coding/cvx.txt"), cs, true);
	}

	private static void initMVX() {
		CodeSystem cs = new CodeSystem();
		cs.setUrl("http://hl7.org/fhir/sid/mvx");
		cs.setTitle("Manufacturers of Vaccines");
		cs.setName("MVX");
		Identifier identifier = new Identifier();
		identifier.setSystem(Systems.IETF);
		identifier.setValue("urn:oid:2.16.840.1.113883.12.227");
		cs.addIdentifier(identifier);
		loadData(resolver.getResource("/coding/mvx.txt"), cs, false);
	}

	private static void loadData(Resource file, CodeSystem cs, boolean hasComment) {
		int line = 0;
		try (InputStreamReader sr = new InputStreamReader(file.getInputStream());
			 CSVReader reader = new CSVReaderBuilder(sr).withCSVParser(
					new CSVParserBuilder()
						.withSeparator('|')
						.withFieldAsNull(CSVReaderNullFieldIndicator.EMPTY_SEPARATORS)
						.withIgnoreQuotations(false).build()).build();
		) {
			String[] fields = null;
			int expectedLength = hasComment ? 5 : 4;
			while ((fields = reader.readNext()) != null) {
				if (fields.length < expectedLength) {
					fields = Arrays.copyOf(fields, expectedLength);
				}
				line++;
				ConceptDefinitionComponent concept = cs.addConcept();
				concept.setCode(StringUtils.trim(fields[0]));
				concept.setDisplay(StringUtils.trim(fields[1]));
				concept.setDefinition(StringUtils.trim(fields[2]));
				ConceptPropertyComponent property = concept.addProperty();
				property.setCode("Active");
				property.setValue(new StringType(StringUtils.trim(fields[3])));
				if (hasComment) {
					property.setCode("Comment");
					property.setValue(new StringType(StringUtils.trim(fields[4])));
				}
				Coding coding = new Coding(cs.getUrl(), concept.getCode(), concept.getDisplay());
				concept.setUserData(coding.getClass().getName(), coding);
				updateCodeLookup(coding);
			}
		} catch (Exception e) {
			log.warn("Unexpected {} reading {}({}): {}", e.getClass().getSimpleName(), file.getFilename(), line,
					e.getMessage(), e);
		}
		
		Systems.getNamingSystem(cs.getUrl()).setUserData(cs.getClass().getName(), cs);
	}
	

	private static String toFhirUri(String string) {
		if (StringUtils.isEmpty(string)) {
			return null;
		}
		if (string.startsWith("HL7")) {
			string = string.substring(3);
		}
		return "http://terminology.hl7.org/CodeSystem/v2-" + string;
	}

	/**
	 * Given a coding with code and system set, get the associated display name for
	 * the code if known.
	 * 
	 * @param coding The coding to search for.
	 * @return The display name for the code.
	 */
	public static String getDisplay(Coding coding) {
		return getDisplay(coding.getCode(), coding.getSystem());
	}
	
	public static boolean hasDisplay(String code, String table) {
		return getDisplay(code, table) != null;
	}

	public static String getDisplay(String code, String table) {
		if (StringUtils.isBlank(table)) {
			return null;
		}
		if (IDENTIFIER_TYPE.equals(table)) {
			return idTypeToDisplayMap.get(code);
		}
		Map<String, Coding> cm = codingMaps.get(table.trim());
		if (cm == null) {
			log.warn("Unknow code system: {}", table);
			return null;
		}
		Coding coding = cm.get(code);
		return coding != null ? coding.getDisplay() : null;
	}

	/**
	 * Given a coding with code and system set, set the associated display name for
	 * the code if known.
	 * 
	 * @param coding The coding to search for and potentially adjust. If no display
	 *               name is set, then the coding is unchanged. Thus, a default
	 *               display name can be provided before making this call.
	 */
	public static void setDisplay(Coding coding) {
		String display = getDisplay(coding);
		if (display != null) {
			coding.setDisplay(display);
		}
	}

	public static void main(String... strings) {
	}
}