package com.ainq.fhir.utils;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.Resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.DataFormatException;
import ca.uhn.fhir.parser.IParser;

/**
 * This is a cleaned up implementation from the original blog post that supports converting
 * FHIR to and from YAML format.  Yaml is a simpler format for viewing FHIR resources
 * 
 * This is principally used for testing V2 to FHIR conversions.
 * 
 * @see <a href="https://motorcycleguy.blogspot.com/2021/08/yaml-as-fhir-format.html">YAML as a FHIR Format</a>
 * @author Audacious Inquiry
 *
 */
public class YamlUtils {

    /**
     * Create Json from a Yaml String
     * @param yaml	The Yaml to convert
     * @return	The JSON format string
     * @throws IOException If a processing exception occurred
     */
    public static String fromYaml(String yaml) throws IOException {
        ObjectMapper yamlReader = newYAMLMapper();
        Object obj = yamlReader.readValue(yaml, Object.class);

        ObjectMapper jsonWriter = new ObjectMapper();
        return jsonWriter.writeValueAsString(obj);
    }

    /**
     * Create JSON from a Yaml input stream
     * @param in	The input stream to convert
     * @return	The JSON output
     * @throws IOException	If an IO error occured while reading.
     */
    public static String fromYaml(InputStream in) throws IOException {
        ObjectMapper yamlReader = newYAMLMapper();
        Object obj = yamlReader.readValue(in, Object.class);

        ObjectMapper jsonWriter = new ObjectMapper();
        return jsonWriter.writeValueAsString(obj);
    }

    /**
     * Convert to JSON from YAML 
     * @param r	The reader to convert from
     * @return	The JSON string
     * @throws IOException	If an error occurred while reading.
     */
    public static String fromYaml(Reader r) throws IOException {
        ObjectMapper yamlReader = newYAMLMapper();
        Object obj = yamlReader.readValue(r, Object.class);

        ObjectMapper jsonWriter = new ObjectMapper();
        return jsonWriter.writeValueAsString(obj);
    }

    /**
     * Convert a JSON string to Yaml
     * @param jsonString	The JSON string to convert
     * @return	The YAML output
     * @throws IOException if an IO error occured during mapping
     */
    public static String toYaml(String jsonString) throws IOException {
        // parse JSON
        JsonNode jsonNodeTree = new ObjectMapper().readTree(jsonString);
        // save it as YAML
        return newYAMLMapper().writeValueAsString(jsonNodeTree);
    }

    /**
     * Write Yaml to a Writer from a JSON string
     * @param jsonString	The JSON string
     * @param w	The writer
     * @throws IOException	If an IO error occurred while writing.
     */
    public static void toYaml(String jsonString, Writer w) throws IOException {
        // parse JSON
        JsonNode jsonNodeTree = new ObjectMapper().readTree(jsonString);
        newYAMLMapper().writeValue(w, jsonNodeTree);
    }

    /**
     * Write Yaml to an OutputStream from a JSON string
     * @param jsonString	The JSON string
     * @param os The OutputStream
     * @throws IOException	If an IO error occurred while writing.
     */
    public static void toYaml(String jsonString, OutputStream os) throws IOException {
        // parse JSON
        JsonNode jsonNodeTree = new ObjectMapper().readTree(jsonString);
        // save it as YAML
        newYAMLMapper().writeValue(os, jsonNodeTree);
    }


    /**
     * Construct a new YAML Mapper for YAML and JSON conversion
     * @return A new YAML Mapper
     */
    public static YAMLMapper newYAMLMapper() {
        YAMLMapper m = new YAMLMapper();
        return m.enable(YAMLGenerator.Feature.LITERAL_BLOCK_STYLE)
        .disable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
        .disable(YAMLGenerator.Feature.USE_PLATFORM_LINE_BREAKS)
        .disable(YAMLGenerator.Feature.SPLIT_LINES);
    }

    /**
     * Create a new Yaml Parser that implements the HAPI FHIR IParser interface
     * @param context	The FHIR Context to use to create the parser
     * @return	The YamlParser
     */
    public static YamlParser newYamlParser(FhirContext context) {
        return new YamlParser(context);
    }

    /**
     * This class takes the names of two files and
     * converts the first existing file to the second (non-existing) file.
     * It uses the extensions of the file to determine which parser to
     * use.
     * @param args  Names of files for conversion operation.
     */
    public static void main(String ... args) {
        FhirContext r4 = FhirContext.forR4();
        IParser p;
        if (args.length != 2) {
            System.err.printf("Usage: java %s inputfile outputfile%n", YamlUtils.class.getName()); // NOSONAR
            System.exit(5);
        }
        File inputFile = new File(args[0]);
        File outputFile = new File(args[1]);
        if (!inputFile.exists()) {
            System.err.printf("File %s does not exist.%n", inputFile); // NOSONAR
        }
        String ext = StringUtils.substringAfterLast(inputFile.getName(), ".");
        p = getParser(ext, r4);
        Resource r = null;
        try (FileReader fr = new FileReader(inputFile, StandardCharsets.UTF_8)) {
            r = (Resource) p.parseResource(fr);
        } catch (IOException e) {
            System.err.printf("Cannot read %s.%n", inputFile); // NOSONAR
            System.exit(1);
        } catch (DataFormatException e) {
            System.err.printf("Cannot parse %s.%n", inputFile); // NOSONAR
            System.exit(2);
        }

        ext = StringUtils.substringAfterLast(outputFile.getName(), ".");
        p = getParser(ext, r4);

        try (FileWriter fw = new FileWriter(outputFile, StandardCharsets.UTF_8)) {
            p.encodeResourceToWriter(r, fw);
        } catch (IOException e) {
            System.err.printf("Cannot write %s.%n", outputFile);   // NOSONAR
            System.exit(3);
        } catch (DataFormatException e) {
            System.err.printf("Cannot convert %s.%n", outputFile); // NOSONAR
            System.exit(4);
        }
    }

    private static IParser getParser(String ext, FhirContext r4) {
        switch (ext.toLowerCase(Locale.ENGLISH)) {
        case "yaml":
            return newYamlParser(r4);
        case "xml":
            return r4.newXmlParser();
        case "json":
            return r4.newJsonParser();
        case "rdf":
            throw new IllegalArgumentException("RDF parser implementation is not yet complete");
        default:
            throw new IllegalArgumentException("Unrecognized format: " + ext);
        }
    }
}
