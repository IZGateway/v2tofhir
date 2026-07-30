package gov.cdc.izgw.v2tofhir.utils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.nio.charset.StandardCharsets;

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
}
