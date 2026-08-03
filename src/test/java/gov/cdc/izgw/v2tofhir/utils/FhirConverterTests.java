package gov.cdc.izgw.v2tofhir.utils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.nio.charset.StandardCharsets;

import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.MockHttpInputMessage;

/**
 * Regression tests for {@link FhirConverter#read}, notably parsing of
 * {@code Content-Type} headers that carry parameters such as
 * {@code ;charset=UTF-8} (IGDD-3164).
 */
class FhirConverterTests {

    private static final String PATIENT_JSON = "{\"resourceType\":\"Patient\",\"id\":\"example\"}";
    private static final String PATIENT_XML =
            "<Patient xmlns=\"http://hl7.org/fhir\"><id value=\"example\"/></Patient>";
    private static final String PATIENT_YAML = "resourceType: Patient\nid: example\n";

    private final FhirConverter converter = new FhirConverter();

    private Patient read(String body, String contentType) {
        MockHttpInputMessage message = new MockHttpInputMessage(body.getBytes(StandardCharsets.UTF_8));
        if (contentType != null) {
            message.getHeaders().set(HttpHeaders.CONTENT_TYPE, contentType);
        }
        Resource resource = assertDoesNotThrow(() -> converter.read(Patient.class, message));
        Patient patient = assertInstanceOf(Patient.class, resource);
        assertEquals("example", patient.getIdElement().getIdPart());
        return patient;
    }

    @Test
    void readsJsonWithCharsetParameter() {
        read(PATIENT_JSON, "application/fhir+json;charset=UTF-8");
    }

    @Test
    void readsXml() {
        // The JSON parser would reject an XML body, so success shows the XML parser was selected.
        read(PATIENT_XML, "application/fhir+xml");
    }

    @Test
    void readsXmlWithCharsetParameter() {
        read(PATIENT_XML, "application/fhir+xml;charset=UTF-8");
    }

    @Test
    void readsYaml() {
        // The JSON and XML parsers would reject a YAML body, so success shows the YAML parser was selected.
        read(PATIENT_YAML, "application/fhir+yaml");
    }

    @Test
    void missingContentTypeFallsBackToSniffing() {
        read(PATIENT_JSON, null);
        read(PATIENT_XML, null);
    }

    @Test
    void unparseableContentTypeFallsBackToSniffing() {
        read(PATIENT_JSON, "not-a-media-type");
        read(PATIENT_XML, "not-a-media-type");
    }

    @Test
    void unrecognizedFhirMediaTypeDefaultsToJsonParser() {
        read(PATIENT_JSON, "application/fhir");
    }

    // IGDD-3168: Spring passes the declared @RequestBody type to read(). A handler
    // declaring an abstract type such as org.hl7.fhir.r4.model.Resource previously
    // failed with "HAPI-1682: Can not scan abstract or interface class".

    private static final String MATCH_PARAMETERS_JSON =
            "{\"resourceType\":\"Parameters\",\"parameter\":[{\"name\":\"resource\",\"resource\":"
            + "{\"resourceType\":\"Patient\",\"name\":[{\"family\":\"Smith\",\"given\":[\"John\"]}],"
            + "\"birthDate\":\"1970-01-01\"}}]}";
    private static final String MATCH_PARAMETERS_XML =
            "<Parameters xmlns=\"http://hl7.org/fhir\"><parameter><name value=\"resource\"/>"
            + "<resource><Patient><name><family value=\"Smith\"/><given value=\"John\"/></name>"
            + "<birthDate value=\"1970-01-01\"/></Patient></resource></parameter></Parameters>";
    private static final String NAMED_PATIENT_JSON =
            "{\"resourceType\":\"Patient\",\"name\":[{\"family\":\"Smith\",\"given\":[\"John\"]}],"
            + "\"birthDate\":\"1970-01-01\"}";

    private Resource readAs(Class<? extends Resource> clazz, String body, String contentType) {
        MockHttpInputMessage message = new MockHttpInputMessage(body.getBytes(StandardCharsets.UTF_8));
        if (contentType != null) {
            message.getHeaders().set(HttpHeaders.CONTENT_TYPE, contentType);
        }
        return assertDoesNotThrow(() -> converter.read(clazz, message));
    }

    @Test
    void readsParametersWhenRequestedTypeIsAbstractResource() {
        // Case A: the $match bug - declared @RequestBody type is the abstract base Resource.
        Resource resource = readAs(Resource.class, MATCH_PARAMETERS_JSON, "application/fhir+json");
        Parameters parameters = assertInstanceOf(Parameters.class, resource);
        assertEquals("Parameters", parameters.fhirType());
    }

    @Test
    void readsBarePatientWhenRequestedTypeIsAbstractResource() {
        // Case B: a bare Patient payload resolves to the concrete Patient type.
        Resource resource = readAs(Resource.class, NAMED_PATIENT_JSON, "application/fhir+json");
        Patient patient = assertInstanceOf(Patient.class, resource);
        assertEquals("Patient", patient.fhirType());
    }

    @Test
    void readsPatientViaConcreteTypePath() {
        // Case C: concrete requested type still uses the existing typed parseResource path.
        Resource resource = readAs(Patient.class, NAMED_PATIENT_JSON, "application/fhir+json");
        Patient patient = assertInstanceOf(Patient.class, resource);
        assertEquals("Smith", patient.getNameFirstRep().getFamily());
    }

    @Test
    void readsParametersXmlWhenRequestedTypeIsAbstractResource() {
        // Case D: the XML parser path also works through the abstract branch.
        Resource resource = readAs(Resource.class, MATCH_PARAMETERS_XML, "application/fhir+xml");
        Parameters parameters = assertInstanceOf(Parameters.class, resource);
        assertEquals("Parameters", parameters.fhirType());
    }
}
