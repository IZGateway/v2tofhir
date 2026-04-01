## ADDED Requirements

### Requirement: Composable sub-interface family
The system SHALL define eight focused sub-interfaces in `gov.cdc.izgw.v2tofhir.terminology`, each covering one area of terminology management: `NamingSystemResolver`, `ConceptTranslator`, `DisplayNameResolver`, `CodeValidator`, `TerminologyLoader`, `UnitMapper`, `DemographicsMapper`, and `GeoMapper`. A composite `TerminologyMapper` interface SHALL extend all eight. Callers that only need a subset of capabilities SHALL accept the narrowest applicable sub-interface rather than `TerminologyMapper`.

#### Scenario: Narrow parameter typing accepted
- **WHEN** a method parameter is declared as `NamingSystemResolver`
- **THEN** any `TerminologyMapper` implementation SHALL be passable to it without casting

#### Scenario: Partial implementations possible
- **WHEN** a class implements only `UnitMapper` and `NamingSystemResolver`
- **THEN** it SHALL compile without implementing methods from the other six sub-interfaces

---

### Requirement: Sub-interface default methods
Each sub-interface SHALL provide `default` implementations for all its methods so that partial implementors are not required to stub unrelated operations. The default behavior for lookup/translate methods SHALL return an empty `Optional` or an empty result rather than throwing. The default behavior for load/store methods that have no meaningful no-op SHALL throw `UnsupportedOperationException`.

#### Scenario: Default translate returns empty
- **WHEN** a class implements only `NamingSystemResolver` (no `ConceptTranslator`)
- **AND** `translate(coding, targetSystem)` is called on it via the `ConceptTranslator` default method
- **THEN** an empty `Optional` is returned without exception

#### Scenario: Default load throws
- **WHEN** a class does not override `TerminologyLoader.load(resource)`
- **AND** `load(resource)` is called on it
- **THEN** `UnsupportedOperationException` is thrown

---

### Requirement: TranslationResult value object
The system SHALL provide an immutable `TranslationResult` record/class in `gov.cdc.izgw.v2tofhir.terminology` carrying: the translated `Coding` (system + code + display), the source `Coding`, the name of the concept map or mapping source used, and a boolean indicating whether the match was exact or approximate.

#### Scenario: Translation result carries provenance
- **WHEN** `ConceptTranslator.translate(sourceCoding, targetSystem)` returns a result
- **THEN** the returned `TranslationResult` SHALL expose the source coding, the result coding, and the mapping source name

#### Scenario: Exact vs approximate flag
- **WHEN** a mapping produces an exact code match
- **THEN** `TranslationResult.isExact()` SHALL return `true`
- **WHEN** a mapping produces an approximate or equivalent match
- **THEN** `TranslationResult.isExact()` SHALL return `false`

---

### Requirement: TerminologyException
The system SHALL provide a checked or unchecked `TerminologyException` in `gov.cdc.izgw.v2tofhir.terminology` for signalling unrecoverable errors in terminology operations (e.g., malformed resource, storage failure). Normal "not found" results SHALL be expressed as empty `Optional` returns, not exceptions.

#### Scenario: Not-found is not exceptional
- **WHEN** `NamingSystemResolver.toUri(oid)` has no matching entry
- **THEN** it SHALL return an empty `Optional<String>`, not throw `TerminologyException`

#### Scenario: Storage failure is exceptional
- **WHEN** `TerminologyLoader.load(resource)` encounters an I/O error
- **THEN** it SHALL throw `TerminologyException`

---

### Requirement: NamingSystemResolver — OID/URI bidirectional lookup
`NamingSystemResolver` SHALL provide:
- `Optional<String> toUri(String oid)` — returns the canonical URI for a given OID
- `Optional<String> toOid(String uri)` — returns the OID for a given URI
- `Optional<NamingSystem> getNamingSystem(String oidOrUri)` — returns the full `NamingSystem` resource if available

#### Scenario: OID to URI round-trip
- **WHEN** `toUri("2.16.840.1.113883.6.1")` is called
- **THEN** `Optional.of("http://loinc.org")` SHALL be returned

#### Scenario: URI to OID round-trip
- **WHEN** `toOid("http://loinc.org")` is called
- **THEN** `Optional.of("2.16.840.1.113883.6.1")` SHALL be returned

#### Scenario: Unknown OID returns empty
- **WHEN** `toUri` is called with an OID not registered in the resolver
- **THEN** `Optional.empty()` SHALL be returned

---

### Requirement: NamingSystemResolver — V2 table URI
`NamingSystemResolver` SHALL provide `String v2TableUri(String tableNameOrNumber)` that returns the canonical FHIR URI for a HL7 V2 table (e.g., `"0001"` → `"http://terminology.hl7.org/CodeSystem/v2-0001"`). Input MAY be a bare number (`"0001"`), a prefixed table name (`"HL70001"`), or the full URI (returned as-is).

#### Scenario: Bare number input
- **WHEN** `v2TableUri("0001")` is called
- **THEN** `"http://terminology.hl7.org/CodeSystem/v2-0001"` SHALL be returned

#### Scenario: Full URI passthrough
- **WHEN** `v2TableUri("http://terminology.hl7.org/CodeSystem/v2-0001")` is called
- **THEN** `"http://terminology.hl7.org/CodeSystem/v2-0001"` SHALL be returned unchanged

---

### Requirement: ConceptTranslator — translate by target system
`ConceptTranslator` SHALL provide `Optional<TranslationResult> translate(Coding source, String targetSystem)` that looks up the best available concept map translating `source.system` → `targetSystem` and returns the mapped code.

#### Scenario: Successful translation
- **WHEN** `translate(coding with system="http://loinc.org" code="59408-5", targetSystem="http://snomed.info/sct")` is called
- **AND** a concept map exists for that pair
- **THEN** a non-empty `TranslationResult` SHALL be returned with the SNOMED code

#### Scenario: No mapping available
- **WHEN** `translate` is called and no applicable concept map exists
- **THEN** `Optional.empty()` SHALL be returned

---

### Requirement: ConceptTranslator — named mapping lookup
`ConceptTranslator` SHALL provide `Optional<TranslationResult> mapCode(String mappingName, Coding source)` that applies the named concept map or mapping table (e.g., V2 table `"0001"`, named map `"USCoreRace"`) to the source coding. This method is the primary entry point for annotation-driven mapping from `@ComesFrom(table=...)`.

#### Scenario: V2 table mapping
- **WHEN** `mapCode("0001", coding with code="F")` is called
- **THEN** the result SHALL contain the FHIR code for administrative sex `"female"` from the appropriate FHIR CodeSystem

#### Scenario: Named concept map
- **WHEN** `mapCode("USCoreRace", coding with code="2054-5")` is called
- **THEN** the result SHALL contain the US Core race extension coding for that CDC race code

#### Scenario: Unknown mapping name
- **WHEN** `mapCode("nonexistent-map", coding)` is called
- **THEN** `Optional.empty()` SHALL be returned

---

### Requirement: DisplayNameResolver
`DisplayNameResolver` SHALL provide:
- `Optional<String> getDisplay(String system, String code)` — preferred display text for a code
- `boolean hasDisplay(String system, String code)` — true if display is registered
- `void setDisplay(String system, String code, String display)` — register or override a display

#### Scenario: Known code display
- **WHEN** `getDisplay("http://loinc.org", "59408-5")` is called
- **AND** a display is registered for that code
- **THEN** `Optional.of("Oxygen saturation in Arterial blood by Pulse oximetry")` (or equivalent) SHALL be returned

#### Scenario: Override display
- **WHEN** `setDisplay(system, code, "Custom label")` is called
- **AND** `getDisplay(system, code)` is subsequently called
- **THEN** `Optional.of("Custom label")` SHALL be returned

---

### Requirement: CodeValidator
`CodeValidator` SHALL provide `boolean validateCode(String valueSetUri, String system, String code)` that returns `true` if `code` is a valid member of `system` within the named value set.

#### Scenario: Valid code in value set
- **WHEN** `validateCode` is called with a code known to be in the value set
- **THEN** `true` SHALL be returned

#### Scenario: Invalid code
- **WHEN** `validateCode` is called with a code not in the value set
- **THEN** `false` SHALL be returned

---

### Requirement: TerminologyLoader
`TerminologyLoader` SHALL provide methods to load and retrieve FHIR terminology resources:
- `void load(Resource resource)` — registers a `NamingSystem`, `ConceptMap`, `CodeSystem`, or `ValueSet`
- `Optional<NamingSystem> getNamingSystem(String id)`
- `Optional<ConceptMap> getConceptMap(String id)`
- `Optional<CodeSystem> getCodeSystem(String id)`
- `Optional<ValueSet> getValueSet(String id)`

#### Scenario: Load and retrieve ConceptMap
- **WHEN** a `ConceptMap` resource is passed to `load(resource)`
- **AND** `getConceptMap(resource.getId())` is called
- **THEN** the same resource SHALL be returned

#### Scenario: Unsupported resource type
- **WHEN** `load` is called with a resource type that is not `NamingSystem`, `ConceptMap`, `CodeSystem`, or `ValueSet`
- **THEN** `TerminologyException` SHALL be thrown with a descriptive message

---

### Requirement: UnitMapper
`UnitMapper` SHALL provide `Optional<Coding> mapUnit(String unitCode)` that converts a source unit code (e.g., V2 unit string) to a UCUM `Coding` with system `"http://unitsofmeasure.org"`.

#### Scenario: Known unit
- **WHEN** `mapUnit("mg/dL")` is called
- **THEN** a `Coding` with system `"http://unitsofmeasure.org"` and code `"mg/dL"` SHALL be returned

#### Scenario: Unknown unit
- **WHEN** `mapUnit` is called with a unit string not in the mapping table
- **THEN** `Optional.empty()` SHALL be returned

---

### Requirement: DemographicsMapper
`DemographicsMapper` SHALL provide:
- `Optional<Extension> mapRace(String code, String codeSystem)` — returns a US Core race extension
- `Optional<Extension> mapEthnicity(String code, String codeSystem)` — returns a US Core ethnicity extension

#### Scenario: CDC race code mapped
- **WHEN** `mapRace("2054-5", "urn:oid:2.16.840.1.113883.6.238")` is called
- **THEN** a US Core race extension SHALL be returned with the appropriate ombCategory or detailed coding

#### Scenario: Unknown race code
- **WHEN** `mapRace` is called with a code not in the mapping table
- **THEN** `Optional.empty()` SHALL be returned

---

### Requirement: GeoMapper
`GeoMapper` SHALL provide:
- `Optional<Coding> lookupCountry(String code, String codeSystem)` — returns an ISO 3166 country `Coding`
- `Optional<String> normalizeCountry(String nameOrCode)` — returns the ISO 3166 alpha-2 code for a country name or variant spelling

#### Scenario: ISO 3166 alpha-2 lookup
- **WHEN** `lookupCountry("US", "urn:iso:std:iso:3166")` is called
- **THEN** a `Coding` with system `"urn:iso:std:iso:3166"` and code `"US"` SHALL be returned

#### Scenario: Country name normalization
- **WHEN** `normalizeCountry("United States")` is called
- **THEN** `Optional.of("US")` SHALL be returned
