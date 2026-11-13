# Terminology Mapping Interface

## Purpose

Defines the composable sub-interface family that forms the `TerminologyMapper` API for
v2tofhir. Eight focused sub-interfaces cover distinct areas of terminology management;
the composite `TerminologyMapper` extends all eight. Callers accept the narrowest
applicable sub-interface.

---

## Requirements

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
The system SHALL provide an immutable `TranslationResult` record/class in `gov.cdc.izgw.v2tofhir.terminology` carrying: the translated `Coding`, the source `Coding`, the name of the concept map or mapping source used, and a boolean indicating whether the match was exact or approximate.

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
The system SHALL provide `TerminologyException` in `gov.cdc.izgw.v2tofhir.terminology` for signalling unrecoverable errors in terminology operations. Normal "not found" results SHALL be expressed as empty `Optional` returns, not exceptions.

#### Scenario: Not-found is not exceptional
- **WHEN** `NamingSystemResolver.toUri(oid)` has no matching entry
- **THEN** it SHALL return an empty `Optional<String>`, not throw `TerminologyException`

#### Scenario: Storage failure is exceptional
- **WHEN** `TerminologyLoader.load(resource)` encounters an I/O error
- **THEN** it SHALL throw `TerminologyException`

---

### Requirement: NamingSystemResolver — OID/URI bidirectional lookup
`NamingSystemResolver` SHALL provide `toUri(oid)`, `toOid(uri)`, and `getNamingSystem(oidOrUri)`.

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
`NamingSystemResolver` SHALL provide `v2TableUri(tableNameOrNumber)` returning the canonical FHIR URI for a HL7 V2 table. Input may be a bare number, prefixed name, or full URI.

#### Scenario: Bare number input
- **WHEN** `v2TableUri("0001")` is called
- **THEN** `"http://terminology.hl7.org/CodeSystem/v2-0001"` SHALL be returned

#### Scenario: Full URI passthrough
- **WHEN** `v2TableUri("http://terminology.hl7.org/CodeSystem/v2-0001")` is called
- **THEN** the value is returned unchanged

---

### Requirement: ConceptTranslator — translate by target system
`ConceptTranslator` SHALL provide `Optional<TranslationResult> translate(Coding source, String targetSystem)`.

#### Scenario: Successful translation
- **WHEN** `translate` is called with a source coding and a target system for which a concept map exists
- **THEN** a non-empty `TranslationResult` SHALL be returned

#### Scenario: No mapping available
- **WHEN** `translate` is called and no applicable concept map exists
- **THEN** `Optional.empty()` SHALL be returned

---

### Requirement: ConceptTranslator — named mapping lookup
`ConceptTranslator` SHALL provide `Optional<TranslationResult> mapCode(String mappingName, Coding source)` as the primary entry point for annotation-driven mapping from `@ComesFrom(table=...)`.

#### Scenario: V2 table mapping
- **WHEN** `mapCode("0001", coding with code="F")` is called
- **THEN** the result SHALL contain the FHIR code for administrative sex `"female"`

#### Scenario: Unknown mapping name
- **WHEN** `mapCode("nonexistent-map", coding)` is called
- **THEN** `Optional.empty()` SHALL be returned

---

### Requirement: DisplayNameResolver
`DisplayNameResolver` SHALL provide `getDisplay(system, code)`, `hasDisplay(system, code)`, and `setDisplay(system, code, display)`.

#### Scenario: Known code display
- **WHEN** `getDisplay` is called with a code that has a registered display
- **THEN** a non-empty `Optional<String>` SHALL be returned

#### Scenario: Override display
- **WHEN** `setDisplay(system, code, "Custom label")` is called followed by `getDisplay(system, code)`
- **THEN** `Optional.of("Custom label")` SHALL be returned

---

### Requirement: CodeValidator
`CodeValidator` SHALL provide `boolean validateCode(String valueSetUri, String system, String code)`.

#### Scenario: Valid code in value set
- **WHEN** `validateCode` is called with a code known to be in the value set
- **THEN** `true` SHALL be returned

#### Scenario: Invalid code
- **WHEN** `validateCode` is called with a code not in the value set
- **THEN** `false` SHALL be returned

---

### Requirement: TerminologyLoader
`TerminologyLoader` SHALL provide `load(resource)`, `getNamingSystem(id)`, `getConceptMap(id)`, `getCodeSystem(id)`, and `getValueSet(id)`.

#### Scenario: Load and retrieve ConceptMap
- **WHEN** a `ConceptMap` is passed to `load(resource)` and then retrieved via `getConceptMap(id)`
- **THEN** the same resource SHALL be returned

#### Scenario: Unsupported resource type
- **WHEN** `load` is called with a resource type other than NamingSystem, ConceptMap, CodeSystem, or ValueSet
- **THEN** `TerminologyException` SHALL be thrown

---

### Requirement: UnitMapper
`UnitMapper` SHALL provide `Optional<Coding> mapUnit(String unitCode)` converting source unit strings to UCUM.

#### Scenario: Known unit
- **WHEN** `mapUnit("mg/dL")` is called
- **THEN** a `Coding` with system `"http://unitsofmeasure.org"` and code `"mg/dL"` SHALL be returned

#### Scenario: Unknown unit
- **WHEN** `mapUnit` is called with an unrecognised unit string
- **THEN** `Optional.empty()` SHALL be returned

---

### Requirement: DemographicsMapper
`DemographicsMapper` SHALL provide `mapRace(code, codeSystem)` and `mapEthnicity(code, codeSystem)` returning US Core extensions.

#### Scenario: CDC race code mapped
- **WHEN** `mapRace("2054-5", "urn:oid:2.16.840.1.113883.6.238")` is called
- **THEN** a US Core race extension SHALL be returned

#### Scenario: Unknown race code
- **WHEN** `mapRace` is called with an unknown code
- **THEN** `Optional.empty()` SHALL be returned

---

### Requirement: GeoMapper
`GeoMapper` SHALL provide `lookupCountry(code, codeSystem)` and `normalizeCountry(nameOrCode)`.

#### Scenario: ISO 3166 alpha-2 lookup
- **WHEN** `lookupCountry("US", "urn:iso:std:iso:3166")` is called
- **THEN** a `Coding` with system `"urn:iso:std:iso:3166"` and code `"US"` SHALL be returned

#### Scenario: Country name normalization
- **WHEN** `normalizeCountry("United States")` is called
- **THEN** `Optional.of("US")` SHALL be returned
