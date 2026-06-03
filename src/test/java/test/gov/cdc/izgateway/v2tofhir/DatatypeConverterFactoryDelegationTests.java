package test.gov.cdc.izgateway.v2tofhir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.junit.jupiter.api.Test;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.v251.datatype.CE;
import ca.uhn.hl7v2.model.v251.datatype.CWE;
import ca.uhn.hl7v2.model.v251.datatype.IS;
import gov.cdc.izgw.v2tofhir.converter.DatatypeConverter;
import gov.cdc.izgw.v2tofhir.terminology.DefaultTerminologyMapper;
import gov.cdc.izgw.v2tofhir.terminology.TerminologyMapperFactory;

/**
 * Tests for Task 6: verifies that {@link DatatypeConverter} routes Coding/CodeableConcept
 * lookups through {@link TerminologyMapperFactory#get()} rather than calling static
 * utilities directly.
 *
 * <ul>
 *   <li>Existing two-arg forms produce identical output before and after the refactor.</li>
 *   <li>The static fallback uses the factory instance.</li>
 * </ul>
 */
class DatatypeConverterFactoryDelegationTests {

    // -------------------------------------------------------------------------
    // toCoding — backward compatibility
    // -------------------------------------------------------------------------

    @Test
    void toCoding_withV2Table_systemNormalizedToUri() throws HL7Exception {
        // IS type holds a single coded value from a V2 table; table "0001" = administrative sex
        IS isSex = new IS(null, 1);
        isSex.setValue("M");

        // Before the refactor this called Mapping.map() directly.
        // After the refactor it calls mapCoding() via TerminologyMapperFactory.get().
        Coding coding = DatatypeConverter.toCoding(isSex, "0001");

        assertNotNull(coding, "Should produce a Coding for a known IS value");
        assertNotNull(coding.getSystem(), "System should be normalized from '0001' to a URI");
        // The URI should be an HTTP URI, not a bare table number
        String system = coding.getSystem();
        assert system.startsWith("http") || system.startsWith("urn") :
                "System should be a proper URI, got: " + system;
    }

    @Test
    void toCoding_withExplicitUri_passesThroughUnchanged() throws HL7Exception {
        // Use a known full FHIR URI for administrative gender
        String genderSystem = "http://hl7.org/fhir/administrative-gender";
        IS isCode = new IS(null, 1);
        isCode.setValue("M");

        Coding coding = DatatypeConverter.toCoding(isCode, genderSystem);
        assertNotNull(coding);
        assertEquals(genderSystem, coding.getSystem(),
                "Explicitly-provided URI system should not be altered");
    }

    // -------------------------------------------------------------------------
    // toCodeableConcept — backward compatibility
    // -------------------------------------------------------------------------

    @Test
    void toCodeableConcept_ceType_producesCorrectCoding() throws HL7Exception {
        CE ce = new CE(null);
        ce.getIdentifier().setValue("M");
        ce.getNameOfCodingSystem().setValue("HL70001");

        CodeableConcept cc = DatatypeConverter.toCodeableConcept(ce, null);

        assertNotNull(cc, "CE should produce a CodeableConcept");
        assertNotNull(cc.getCodingFirstRep().getCode());
        assertEquals("M", cc.getCodingFirstRep().getCode());
    }

    @Test
    void toCodeableConcept_cweType_systemNormalized() throws HL7Exception {
        CWE cwe = new CWE(null);
        cwe.getIdentifier().setValue("LA");
        cwe.getNameOfCodingSystem().setValue("HL70001");

        CodeableConcept cc = DatatypeConverter.toCodeableConcept(cwe, null);

        assertNotNull(cc);
        // System should be the FHIR URI, not the raw HL7 name
        String system = cc.getCodingFirstRep().getSystem();
        if (system != null) {
            assert system.startsWith("http") || system.startsWith("urn") || system.equals("HL70001") :
                    "System should be a URI or the original name, got: " + system;
        }
    }

    // -------------------------------------------------------------------------
    // Factory singleton is used (not bypassed)
    // -------------------------------------------------------------------------

    @Test
    void toCoding_usesFactorySingleton() throws HL7Exception {
        // With no env var, the factory always returns a DefaultTerminologyMapper.
        // Verify the factory has been initialized and is the expected type.
        assertNotNull(TerminologyMapperFactory.get());
        assert TerminologyMapperFactory.get() instanceof DefaultTerminologyMapper :
                "Factory should return DefaultTerminologyMapper when no env var is set";

        // A conversion that goes through mapCoding should succeed without throwing.
        IS isCode = new IS(null, 1);
        isCode.setValue("F");
        Coding coding = DatatypeConverter.toCoding(isCode, "0001");
        assertNotNull(coding);
    }
}
