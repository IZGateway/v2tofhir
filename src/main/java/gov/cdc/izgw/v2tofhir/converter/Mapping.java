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
import org.hl7.fhir.r4.model.CodeSystem.CodeSystemContentMode;
import org.hl7.fhir.r4.model.CodeSystem.ConceptDefinitionComponent;
import org.hl7.fhir.r4.model.CodeSystem.ConceptPropertyComponent;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Enumerations.PublicationStatus;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.NamingSystem;
import org.hl7.fhir.r4.model.NamingSystem.NamingSystemType;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.Type;
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
	

	private final String name;
	private Map<String, Coding> from = new LinkedHashMap<>();
	private Map<String, Coding> to = new LinkedHashMap<>();


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
		Resource[] conceptFiles;
		Resource[] codeSystemFiles;
		try {
			conceptFiles = resolver.getResources("/coding/HL7 Concept*.csv");
			codeSystemFiles = resolver.getResources("/coding/HL7 CodeSystem*.csv");
		} catch (IOException e) {
			log.error("Cannot load coding resources");
			throw new ServiceConfigurationError("Cannot load coding resources", e);

		}
		int fileno = 0;
		for (Resource file : conceptFiles) {
			fileno++;
			Mapping m = loadConceptFile(fileno, file);
			m.lock();
		}
		for (Resource file : codeSystemFiles) {
			fileno++;
			// loadCodeSystemFile(fileno, file);
		}
	}

	private static Mapping loadConceptFile(int fileno, Resource file) {
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
				addMapping(name, m, ++line, indices, fields);
			}
			log.debug("{}: Loaded {} lines from {}", fileno, line, name);

		} catch (Exception e) {
			warnException("Unexpected {} reading {}({}): {}", e.getClass().getSimpleName(), file.getFilename(), line,
					e.getMessage(), e);
		}
		return m;
	}
	
	// TODO: This
	private static void loadCodeSystemFile(int fileno, Resource file) {
		String name = file.getFilename().split("_ ")[1].split(" ")[0];
		Mapping m = new Mapping(name);
		codeMaps.put(name, m);
		int line = 0;
		try (InputStreamReader sr = new InputStreamReader(file.getInputStream());
				CSVReader reader = new CSVReader(sr);) {
			String[] metadata = reader.readNext(); // Skip first line header
			
			CodeSystem cs = new CodeSystem();
			cs.setName(metadata.length > 2 ? metadata[2] : null);
			cs.setUrl(metadata.length > 0 ? metadata[0] : null);
			cs.setContent(CodeSystemContentMode.COMPLETE);
			cs.setCaseSensitive(false);
			cs.setLanguage("en-US");
			cs.setStatus(PublicationStatus.ACTIVE);
			
			createNamingSystem(cs);
			
			line++;
			String[] headers = reader.readNext();
			int[] indices = getHeaderIndices(headers);
			line++;
			String[] fields = null;

			while ((fields = reader.readNext()) != null) {
				addCodes(cs, ++line, indices, fields);
				cs.setCount(cs.getCount()+1);
			}
			log.debug("{}: Loaded {} lines from {}", fileno, line, name);

		} catch (Exception e) {
			warnException("Unexpected {} reading {}({}): {}", e.getClass().getSimpleName(), file.getFilename(), line,
					e.getMessage(), e);
		}
	}

	private static void addCodes(CodeSystem cs, int i, int[] indices, String[] fields) {
		// TODO Auto-generated method stub
		
	}

	private static NamingSystem createNamingSystem(CodeSystem cs) {
		NamingSystem ns = new NamingSystem();
		ns.setName(cs.getName());
		ns.setUrl(cs.getUrl());
		ns.setTitle(cs.getTitle());
		ns.setText(cs.getText());
		ns.setKind(NamingSystemType.CODESYSTEM);
		ns.setLanguage(cs.getLanguage());
		ns.setStatus(cs.getStatus());
		
		// Link NamingSystem to CodeSystem
		ns.setUserData(CodeSystem.class.getName(), cs);
		// Link CodeSystem to NamingSystem
		cs.setUserData(NamingSystem.class.getName(), ns);
		return ns;
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

	private static void addMapping(String name, Mapping m, int line, int[] indices, String[] fields) {
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
		createNamingSystem(cs);
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
		createNamingSystem(cs);
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
			warnException("Unexpected {} reading {}({}): {}", e.getClass().getSimpleName(), file.getFilename(), line,
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
		if (Systems.IDENTIFIER_TYPE.equals(table)) {
			return Systems.idTypeToDisplayMap.get(code);
		}
		
		Map<String, Coding> cm = codingMaps.get(Mapping.getPreferredCodeSystem(table.trim()));
		if (cm == null) {
			warn("Unknown code system: {}", table);
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
			coding.setUserData("originalDisplay", coding.getDisplay());
			coding.setDisplay(display);
		}
	}
	
	/**
	 * The converter adds some extra data that it knows about codes
	 * for better interoperability, e.g., display names, code system URLs
	 * et cetera.  This may be why the strings are different.  Reset those
	 * changes (the original supplied values are in user data under
	 * the string "original{FieldName}"
	 * 
	 * @param type The type to reset to original values.
	 */
	public static void reset(Type type) {
		if (type instanceof Coding c) {
			reset(c);
		} else if (type instanceof CodeableConcept cc) {
			reset(cc);
		}
	}
	public static void reset(Coding coding) {
		if (coding == null) {
			return;
		}
		if (coding.hasUserData("originalDisplay")) {
			coding.setDisplay((String)coding.getUserData("originalDisplay"));
		}
		
	}
	public static void reset(CodeableConcept cc) {
		if (cc == null) {
			return;
		}
		if (cc.hasUserData("originalText")) {
			cc.setText((String)cc.getUserData("originalText"));
		}
		cc.getCoding().forEach(Mapping::reset);
	}
	
	private static void warn(String msg, Object ...args) {
		log.warn(msg, args);
	}
	private static void warnException(String msg, Object ...args) {
		log.warn(msg, args);
	}

	public static Coding mapSystem(Coding coding) {
		if (!coding.hasSystem()) {
			return coding;
		}
		String system = coding.getSystem();
		coding.setSystem(Mapping.getPreferredCodeSystem(system));
		if (!system.equals(coding.getSystem())) {
			coding.setUserData("originalSystem", system);
		}
		return coding;
	}

	public static Identifier mapSystem(Identifier ident) {
		if (!ident.hasSystem()) {
			return ident;
		}
		String system = ident.getSystem();
		ident.setSystem(Mapping.getPreferredIdSystem(system));
		if (!system.equals(ident.getSystem())) {
			ident.setUserData("originalSystem", system);
		}
		return ident;
	}

	private static String getPreferredCodeSystem(String value) {
		String system = Mapping.getPreferredIdSystem(value);
		if (system == null) {
			return null;
		}
		
		// Handle mapping for HL7 V2 tables
		if (system.startsWith("HL7") || system.startsWith("hl7")) {
			system = system.substring(3);
			if (system.startsWith("-")) {
				system = system.substring(1);
			}
			return "http://terminology.hl7.org/CodeSystem/v2-" + system;
		} else if (system.length() == 4 && StringUtils.isNumeric(system)) {
			return "http://terminology.hl7.org/CodeSystem/v2-" + system;
		}
		return value;
	}

	private static String getPreferredIdSystem(String value) {
		if (StringUtils.isBlank(value)) {
			return null;
		}
		NamingSystem ns = Systems.getNamingSystem(value);
		if (ns != null) {
			return ns.getUrl();
		}
		
		if (value.startsWith("urn:oid:")) {
			return value.substring(8);
		} else if (value.startsWith("urn:uuid:")) {
			return value.substring(9);
		} 
		return value;
	}

}