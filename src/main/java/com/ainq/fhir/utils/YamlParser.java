package com.ainq.fhir.utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.hl7.fhir.instance.model.api.IBase;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.instance.model.api.IIdType;

import ca.uhn.fhir.context.ConfigurationException;
import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.DataFormatException;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.parser.IParserErrorHandler;
import ca.uhn.fhir.rest.api.EncodingEnum;
import lombok.extern.slf4j.Slf4j;

/**
 * YamlParser implements the HAPI FHIR IParser interface, enabling it
 * to be used to convert FHIR Resources and elements to and from YAML
 *  
 * @author Audacious Inquiry
 * @see <a href="https://motorcycleguy.blogspot.com/2021/08/yaml-as-fhir-format.html">YAML as a FHIR Format</a>
 *
 */
@Slf4j
public class YamlParser implements IParser {
    private static final String YAML_WRITE_ERROR = "Error Converting to YAML";
	private static final String YAML_READ_ERROR = "Error Converting from YAML";
	private final IParser jsonParser;
    
    /**
     * Create a YamlParser with the default(R4) FhirContext
     */
    public YamlParser() {
    	this(FhirContext.forR4());
    }
    /**
     * Create a new Yaml Parser from a FhirContext
     * @param context The FhirContext to use to create the parser
     */
    public YamlParser(FhirContext context) {
        jsonParser = context.newJsonParser();
    }

    @Override
    public String encodeResourceToString(IBaseResource theResource) throws DataFormatException {

        try {
            return YamlUtils.toYaml(jsonParser.encodeResourceToString(theResource));
        } catch (IOException e) {
        	log.error(YAML_WRITE_ERROR + ": {}", e.getMessage(), e);
            throw new DataFormatException(YAML_WRITE_ERROR, e);
        }
    }

    @Override
    public void encodeResourceToWriter(IBaseResource theResource, Writer theWriter)
        throws IOException, DataFormatException {

        try {
            theWriter.write(YamlUtils.toYaml(jsonParser.encodeResourceToString(theResource)));
        } catch (IOException e) {
        	log.error("Error Converting to YAML: {}", e.getMessage(), e);
            throw new DataFormatException(YAML_WRITE_ERROR, e);
        }
    }

    @Override
    public IIdType getEncodeForceResourceId() {
        return jsonParser.getEncodeForceResourceId();
    }

    @Override
    public EncodingEnum getEncoding() {
        return jsonParser.getEncoding();
    }

    @Override
    public List<Class<? extends IBaseResource>> getPreferTypes() {
        return jsonParser.getPreferTypes();
    }

    @Override
    public boolean isOmitResourceId() {
        return jsonParser.isOmitResourceId();
    }

    @Override
    public Boolean getStripVersionsFromReferences() {
        return jsonParser.getStripVersionsFromReferences();
    }

    @Override
    public boolean isSummaryMode() {
        return jsonParser.isSummaryMode();
    }

    @Override
    public <T extends IBaseResource> T parseResource(Class<T> theResourceType, Reader theReader)
        throws DataFormatException {
        try {
            return jsonParser.parseResource(theResourceType, YamlUtils.fromYaml(theReader));
        } catch (IOException e) {
            throw new DataFormatException(YAML_READ_ERROR, e);
        }
    }

    @Override
    public <T extends IBaseResource> T parseResource(Class<T> theResourceType, InputStream theInputStream)
        throws DataFormatException {
        try {
            return jsonParser.parseResource(theResourceType, YamlUtils.fromYaml(theInputStream));
        } catch (IOException e) {
            throw new DataFormatException(YAML_READ_ERROR, e);
        }
    }

    @Override
    public <T extends IBaseResource> T parseResource(Class<T> theResourceType, String theString)
        throws DataFormatException {
        try {
            return jsonParser.parseResource(theResourceType, YamlUtils.fromYaml(theString));
        } catch (Exception e) {
            throw new DataFormatException(YAML_READ_ERROR, e);
        }
    }

    @Override
    public IBaseResource parseResource(Reader theReader) throws ConfigurationException, DataFormatException {
        try {
            return jsonParser.parseResource(YamlUtils.fromYaml(theReader));
        } catch (Exception e) {
            throw new DataFormatException(YAML_READ_ERROR, e);
        }
    }

    @Override
    public IBaseResource parseResource(InputStream theInputStream)
        throws ConfigurationException, DataFormatException {
        try {
            return jsonParser.parseResource(YamlUtils.fromYaml(theInputStream));
        } catch (Exception e) {
            throw new DataFormatException(YAML_READ_ERROR, e);
        }
    }

    @Override
    public IBaseResource parseResource(String theMessageString) throws ConfigurationException, DataFormatException {
        try {
            return jsonParser.parseResource(YamlUtils.fromYaml(theMessageString));
        } catch (Exception e) {
            throw new DataFormatException(YAML_READ_ERROR, e);
        }
    }

    @Override
    public IParser setEncodeElements(Set<String> theEncodeElements) {
        return jsonParser.setEncodeElements(theEncodeElements);
    }

    @Override
    public void setEncodeElementsAppliesToChildResourcesOnly(boolean theEncodeElementsAppliesToChildResourcesOnly) {
        jsonParser.setEncodeElementsAppliesToChildResourcesOnly(theEncodeElementsAppliesToChildResourcesOnly);
    }

    @Override
    public boolean isEncodeElementsAppliesToChildResourcesOnly() {
        return jsonParser.isEncodeElementsAppliesToChildResourcesOnly();
    }

    @Override
    public IParser setEncodeForceResourceId(IIdType theForceResourceId) {
        jsonParser.setEncodeForceResourceId(theForceResourceId);
        return this;
    }

    @Override
    public IParser setOmitResourceId(boolean theOmitResourceId) {
        jsonParser.setOmitResourceId(theOmitResourceId);
        return this;
    }

    @Override
    public IParser setParserErrorHandler(IParserErrorHandler theErrorHandler) {
        jsonParser.setParserErrorHandler(theErrorHandler);
        return this;
    }

    @Override
    public void setPreferTypes(List<Class<? extends IBaseResource>> thePreferTypes) {
        jsonParser.setPreferTypes(thePreferTypes);
    }

    @Override
    public IParser setPrettyPrint(boolean thePrettyPrint) {
        jsonParser.setPrettyPrint(thePrettyPrint);
        return this;
    }

    @Override
    public IParser setServerBaseUrl(String theUrl) {
        jsonParser.setServerBaseUrl(theUrl);
        return this;
    }

    @Override
    public IParser setStripVersionsFromReferences(Boolean theStripVersionsFromReferences) {
        jsonParser.setStripVersionsFromReferences(theStripVersionsFromReferences);
        return this;
    }

    @Override
    public IParser setOverrideResourceIdWithBundleEntryFullUrl(
        Boolean theOverrideResourceIdWithBundleEntryFullUrl) {
        jsonParser.setOverrideResourceIdWithBundleEntryFullUrl(theOverrideResourceIdWithBundleEntryFullUrl);
        return this;
    }

    @Override
    public IParser setSummaryMode(boolean theSummaryMode) {
        jsonParser.setSummaryMode(theSummaryMode);
        return this;
    }

    @Override
    public IParser setSuppressNarratives(boolean theSuppressNarratives) {
        jsonParser.setSuppressNarratives(theSuppressNarratives);
        return this;
    }

    @Override
    public IParser setDontStripVersionsFromReferencesAtPaths(String... thePaths) {
        jsonParser.setDontStripVersionsFromReferencesAtPaths(thePaths);
        return this;
    }

    @Override
    public IParser setDontStripVersionsFromReferencesAtPaths(Collection<String> thePaths) {
        jsonParser.setDontStripVersionsFromReferencesAtPaths(thePaths);
        return this;
    }

    @Override
    public Set<String> getDontStripVersionsFromReferencesAtPaths() {
        return jsonParser.getDontStripVersionsFromReferencesAtPaths();
    }

	@Override
	public String encodeToString(IBase theElement) throws DataFormatException {
        try {
            return YamlUtils.toYaml(jsonParser.encodeToString(theElement));
        } catch (IOException e) {
            throw new DataFormatException(YAML_WRITE_ERROR, e);
        }
	}

	@Override
	public void encodeToWriter(IBase theElement, Writer theWriter) throws DataFormatException, IOException {
        try {
            theWriter.write(YamlUtils.toYaml(jsonParser.encodeToString(theElement)));
        } catch (IOException e) {
            throw new DataFormatException(YAML_WRITE_ERROR, e);
        }
	}

	@Override
	public IParser setDontEncodeElements(Collection<String> theDontEncodeElements) {
		return jsonParser.setDontEncodeElements(theDontEncodeElements);
	}
}