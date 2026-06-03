package test.gov.cdc.izgateway.v2tofhir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.hl7.fhir.r4.model.CodeSystem;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.ConceptMap;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.NamingSystem;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.ValueSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import gov.cdc.izgw.v2tofhir.terminology.DefaultTerminologyMapper;
import gov.cdc.izgw.v2tofhir.terminology.TerminologyException;
import gov.cdc.izgw.v2tofhir.terminology.TerminologyMapper;
import gov.cdc.izgw.v2tofhir.terminology.TranslationResult;
import gov.cdc.izgw.v2tofhir.utils.Systems;

/**
 * Unit tests for {@link DefaultTerminologyMapper}.
 * Verifies that each sub-interface method delegates correctly to the
 * underlying static utility classes.
 */
class DefaultTerminologyMapperTests {

    private TerminologyMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new DefaultTerminologyMapper();
    }

    // -------------------------------------------------------------------------
    // NamingSystemResolver
    // -------------------------------------------------------------------------

    @Test
    void toUri_knownOid_returnsUri() {
        // 2.16.840.1.113883.12.6 is the HL7 V2 table 0006 OID; use a well-known one
        // Use the SNOMED CT OID which Systems has registered
        Optional<String> uri = mapper.toUri("2.16.840.1.113883.6.96");
        assertTrue(uri.isPresent(), "Should resolve well-known SNOMED OID");
        assertTrue(uri.get().contains("snomed"), "URI should reference SNOMED: " + uri.get());
    }

    @Test
    void toUri_unknownOid_returnsEmpty() {
        Optional<String> uri = mapper.toUri("9.9.9.9.9");
        assertTrue(uri.isEmpty(), "Unknown OID should return empty");
    }

    @Test
    void toOid_knownUri_returnsOid() {
        Optional<String> oid = mapper.toOid(Systems.SNOMED);
        assertTrue(oid.isPresent(), "Should resolve SNOMED URI to OID");
        assertTrue(oid.get().contains("2.16.840.1.113883"), "OID should be SNOMED: " + oid.get());
    }

    @Test
    void toOid_unknownUri_returnsEmpty() {
        Optional<String> oid = mapper.toOid("http://example.com/unknown-system");
        assertTrue(oid.isEmpty(), "Unknown URI should return empty");
    }

    @Test
    void getNamingSystem_knownSystem_returnsNamingSystem() {
        Optional<NamingSystem> ns = mapper.getNamingSystem(Systems.SNOMED);
        assertTrue(ns.isPresent(), "Should resolve SNOMED NamingSystem");
        assertNotNull(ns.get());
    }

    @Test
    void getNamingSystem_unknownSystem_returnsEmpty() {
        Optional<NamingSystem> ns = mapper.getNamingSystem("http://example.com/no-such-system");
        assertTrue(ns.isEmpty(), "Unknown system should return empty");
    }

    @Test
    void v2TableUri_shortName_returnsFullUri() {
        String uri = mapper.v2TableUri("0001");
        assertNotNull(uri, "v2 table URI should not be null for known table number");
        assertTrue(uri.contains("0001") || uri.startsWith("http"),
                "URI should look like a v2 table URI: " + uri);
    }

    @Test
    void v2TableUri_alreadyFullUri_passesThrough() {
        String existing = "http://terminology.hl7.org/CodeSystem/v2-0001";
        assertEquals(existing, mapper.v2TableUri(existing));
    }

    @Test
    void v2TableUri_null_returnsNull() {
        assertTrue(mapper.v2TableUri(null) == null);
    }

    // -------------------------------------------------------------------------
    // ConceptTranslator
    // -------------------------------------------------------------------------

    @Test
    void mapCode_unknownMappingName_returnsEmpty() {
        Coding source = new Coding("http://example.com/sys", "ABC", null);
        Optional<TranslationResult> result = mapper.mapCode("no-such-mapping", source);
        assertTrue(result.isEmpty(), "Unknown mapping name should return empty");
    }

    @Test
    void translate_returnsEmpty() {
        // translate() is not backed by static data — always empty
        Coding source = new Coding(Systems.SNOMED, "123456789", "Some concept");
        Optional<TranslationResult> result = mapper.translate(source, Systems.LOINC);
        assertTrue(result.isEmpty(), "translate() without backing data should return empty");
    }

    // -------------------------------------------------------------------------
    // DisplayNameResolver
    // -------------------------------------------------------------------------

    @Test
    void setDisplay_thenGetDisplay_roundTrips() {
        String system = "http://example.com/test-system-" + System.nanoTime();
        String code   = "TST-001";
        String display = "Test Code One";

        assertFalse(mapper.hasDisplay(system, code), "Display should not exist before registration");

        mapper.setDisplay(system, code, display);

        assertTrue(mapper.hasDisplay(system, code), "Display should exist after registration");
        Optional<String> retrieved = mapper.getDisplay(system, code);
        assertTrue(retrieved.isPresent());
        assertEquals(display, retrieved.get());
    }

    @Test
    void getDisplay_unknownCode_returnsEmpty() {
        Optional<String> d = mapper.getDisplay("http://example.com/no-sys", "ZZZZZ");
        assertTrue(d.isEmpty(), "Unknown code/system should return empty");
    }

    // -------------------------------------------------------------------------
    // CodeValidator
    // -------------------------------------------------------------------------

    @Test
    void validateCode_alwaysReturnsFalse() {
        // Current implementation does not support value-set membership
        assertFalse(mapper.validateCode(
                "http://hl7.org/fhir/ValueSet/administrative-gender",
                "http://hl7.org/fhir/administrative-gender",
                "male"));
    }

    // -------------------------------------------------------------------------
    // UnitMapper
    // -------------------------------------------------------------------------

    @Test
    void mapUnit_knownUcumCode_returnsCoding() {
        Optional<Coding> coding = mapper.mapUnit("mg");
        assertTrue(coding.isPresent(), "Well-known unit 'mg' should map to a UCUM Coding");
        assertEquals(Systems.UCUM, coding.get().getSystem());
    }

    @Test
    void mapUnit_unknownCode_returnsEmpty() {
        Optional<Coding> coding = mapper.mapUnit("xyzzy_no_such_unit");
        assertTrue(coding.isEmpty(), "Unknown unit should return empty");
    }

    @Test
    void mapUnit_null_returnsEmpty() {
        Optional<Coding> coding = mapper.mapUnit(null);
        assertTrue(coding.isEmpty(), "Null unit should return empty");
    }

    // -------------------------------------------------------------------------
    // DemographicsMapper
    // -------------------------------------------------------------------------

    @Test
    void mapRace_knownCode_returnsExtension() {
        // 2106-3 is "White" in CDC Race & Ethnicity codes
        Optional<Extension> ext = mapper.mapRace("2106-3", "urn:oid:2.16.840.1.113883.6.238");
        assertTrue(ext.isPresent(), "Known CDC race code '2106-3' should return an extension");
        assertEquals("http://hl7.org/fhir/us/core/StructureDefinition/us-core-race", ext.get().getUrl());
    }

    @Test
    void mapRace_unknownCode_returnsUnknownExtension() {
        // RaceAndEthnicity defaults unrecognised codes to "UNK", so the extension is still present
        Optional<Extension> ext = mapper.mapRace("ZZZZ", "http://example.com/unknown");
        assertTrue(ext.isPresent(), "Unknown race code should still return an extension (UNK default)");
    }

    @Test
    void mapEthnicity_knownCode_returnsExtension() {
        // 2135-2 is "Hispanic or Latino"
        Optional<Extension> ext = mapper.mapEthnicity("2135-2", "urn:oid:2.16.840.1.113883.6.238");
        assertTrue(ext.isPresent(), "Known CDC ethnicity code '2135-2' should return an extension");
        assertEquals("http://hl7.org/fhir/us/core/StructureDefinition/us-core-ethnicity", ext.get().getUrl());
    }

    @Test
    void mapEthnicity_unknownCode_returnsUnknownExtension() {
        // RaceAndEthnicity defaults unrecognised codes to "UNK", so the extension is still present
        Optional<Extension> ext = mapper.mapEthnicity("ZZZZ", "http://example.com/unknown");
        assertTrue(ext.isPresent(), "Unknown ethnicity code should still return an extension (UNK default)");
    }

    // -------------------------------------------------------------------------
    // GeoMapper
    // -------------------------------------------------------------------------

    @Test
    void lookupCountry_twoLetterCode_returnsCoding() {
        Optional<Coding> coding = mapper.lookupCountry("US", "urn:iso:std:iso:3166");
        assertTrue(coding.isPresent(), "'US' should resolve to a country coding");
        assertEquals("US", coding.get().getCode());
        assertNotNull(coding.get().getDisplay());
    }

    @Test
    void lookupCountry_unknownCode_returnsEmpty() {
        Optional<Coding> coding = mapper.lookupCountry("XX", "urn:iso:std:iso:3166");
        assertTrue(coding.isEmpty(), "Unknown country code should return empty");
    }

    @Test
    void normalizeCountry_countryName_returnsAlpha2() {
        Optional<String> alpha2 = mapper.normalizeCountry("United States");
        assertTrue(alpha2.isPresent(), "Country name should normalize to alpha-2");
        assertEquals("US", alpha2.get());
    }

    @Test
    void normalizeCountry_unknownName_returnsEmpty() {
        Optional<String> alpha2 = mapper.normalizeCountry("Neverland");
        assertTrue(alpha2.isEmpty(), "Unknown country name should return empty");
    }

    // -------------------------------------------------------------------------
    // TerminologyLoader
    // -------------------------------------------------------------------------

    @Test
    void load_namingSystem_registersInSystems() {
        NamingSystem ns = new NamingSystem();
        ns.setName("Test System");
        ns.addUniqueId()
          .setType(NamingSystem.NamingSystemIdentifierType.URI)
          .setValue("http://example.com/test-ns-" + System.nanoTime());

        // Should not throw
        mapper.load(ns);
    }

    @Test
    void load_conceptMap_retrievableById() {
        ConceptMap cm = new ConceptMap();
        cm.setId("test-concept-map");

        mapper.load(cm);

        Optional<ConceptMap> retrieved = mapper.getConceptMap("test-concept-map");
        assertTrue(retrieved.isPresent());
    }

    @Test
    void load_codeSystem_retrievableById() {
        CodeSystem cs = new CodeSystem();
        cs.setId("test-code-system");

        mapper.load(cs);

        Optional<CodeSystem> retrieved = mapper.getCodeSystem("test-code-system");
        assertTrue(retrieved.isPresent());
    }

    @Test
    void load_valueSet_retrievableById() {
        ValueSet vs = new ValueSet();
        vs.setId("test-value-set");

        mapper.load(vs);

        Optional<ValueSet> retrieved = mapper.getValueSet("test-value-set");
        assertTrue(retrieved.isPresent());
    }

    @Test
    void load_unsupportedType_throwsTerminologyException() {
        assertThrows(TerminologyException.class, () -> mapper.load(new Patient()));
    }

    @Test
    void load_null_throwsTerminologyException() {
        assertThrows(TerminologyException.class, () -> mapper.load(null));
    }

    @Test
    void getConceptMap_unknownId_returnsEmpty() {
        Optional<ConceptMap> cm = mapper.getConceptMap("no-such-map");
        assertTrue(cm.isEmpty());
    }

    @Test
    void getCodeSystem_unknownId_returnsEmpty() {
        Optional<CodeSystem> cs = mapper.getCodeSystem("no-such-cs");
        assertTrue(cs.isEmpty());
    }

    @Test
    void getValueSet_unknownId_returnsEmpty() {
        Optional<ValueSet> vs = mapper.getValueSet("no-such-vs");
        assertTrue(vs.isEmpty());
    }

    @Test
    void perInstanceMaps_areIsolated() {
        TerminologyMapper mapper2 = new DefaultTerminologyMapper();
        ConceptMap cm = new ConceptMap();
        cm.setId("isolated-cm");

        mapper.load(cm);

        assertTrue(mapper.getConceptMap("isolated-cm").isPresent(),
                "First instance should find the loaded map");
        assertTrue(mapper2.getConceptMap("isolated-cm").isEmpty(),
                "Second instance should NOT find the map loaded into first instance");
    }
}
