# Default Facade

## Purpose

Defines `DefaultTerminologyMapper`, the built-in `TerminologyMapper` implementation for
v2tofhir. Delegates every method to the existing static utility classes (`Systems`,
`Mapping`, `Units`, `ISO3166`, `RaceAndEthnicity`), which are relocated to the
`terminology` package and deprecated in favour of the interface.

---

## Requirements

### Requirement: DefaultTerminologyMapper wraps existing static utilities
`DefaultTerminologyMapper` in `gov.cdc.izgw.v2tofhir.terminology` SHALL implement `TerminologyMapper` by delegating every method to the existing static utility classes. No existing mapping logic SHALL be changed. The result of every call through `DefaultTerminologyMapper` SHALL be identical to calling the equivalent static method directly.

#### Scenario: OID-to-URI delegation
- **WHEN** `DefaultTerminologyMapper.toUri("2.16.840.1.113883.6.1")` is called
- **THEN** the result SHALL equal the result of calling `Systems.toUri("2.16.840.1.113883.6.1")` directly

#### Scenario: Concept map lookup delegation
- **WHEN** `DefaultTerminologyMapper.mapCode("0001", coding)` is called
- **THEN** the result SHALL equal the result of calling `Mapping.getMapping("0001").mapCode(coding)` directly

#### Scenario: Unit mapping delegation
- **WHEN** `DefaultTerminologyMapper.mapUnit("mg/dL")` is called
- **THEN** the result SHALL equal the result of calling `Units.toUcum("mg/dL")` directly

---

### Requirement: Static utility classes moved to terminology package
`Systems`, `Mapping`, `Units`, `ISO3166`, and `RaceAndEthnicity` SHALL be moved from `gov.cdc.izgw.v2tofhir.utils` into `gov.cdc.izgw.v2tofhir.terminology`. Their class names, public API signatures, and all logic SHALL remain unchanged. Each class SHALL be annotated `@Deprecated` with a Javadoc `@deprecated` note pointing to the corresponding `TerminologyMapper` method. No class outside `gov.cdc.izgw.v2tofhir.terminology` SHALL import these classes directly after migration.

#### Scenario: Same public API after move
- **WHEN** code calls `Systems.toUri(oid)` after the package move (with updated import)
- **THEN** the result SHALL be identical to the result before the move

#### Scenario: Deprecation warning visible
- **WHEN** a class outside the `terminology` package imports `Systems` directly
- **THEN** the Java compiler SHALL emit a deprecation warning

#### Scenario: ArchUnit boundary enforced
- **WHEN** the ArchUnit test suite runs
- **THEN** any class outside `gov.cdc.izgw.v2tofhir.terminology` that imports the deprecated utility classes SHALL cause a test failure

---

### Requirement: DatatypeConverter factory delegation
`DatatypeConverter`'s static `convertCoding` and `convertCodeableConcept` methods SHALL delegate terminology lookups to `TerminologyMapperFactory.get()` instead of calling the static utility classes directly, ensuring a custom mapper installed via `V2TOFHIR_TERMINOLOGY_MAPPER` is honoured even through static call paths.

#### Scenario: Static fallback uses factory
- **WHEN** `DatatypeConverter.convertCoding(t, "0001")` is called and `V2TOFHIR_TERMINOLOGY_MAPPER` is set to a custom class
- **THEN** the custom mapper's methods SHALL be invoked, not `DefaultTerminologyMapper`'s

#### Scenario: Backward-compatible behavior when no env var set
- **WHEN** `DatatypeConverter.convertCoding(t, "0001")` is called and `V2TOFHIR_TERMINOLOGY_MAPPER` is not set
- **THEN** the result SHALL be identical to the pre-change behavior

---

### Requirement: Full caller migration — no static imports outside terminology package
Every class in v2tofhir that previously called `Systems`, `Mapping`, `Units`, `ISO3166`, or `RaceAndEthnicity` directly SHALL be updated to call through `TerminologyMapperFactory.get()` or through `DatatypeConverter`. After migration, no class outside `gov.cdc.izgw.v2tofhir.terminology` SHALL import any of those five static utility classes.

Affected callers: `DatatypeConverter`, `ERRParser`, `IzDetail`, `NK1Parser`, `OBXParser`, `PIDParser`, `PV1Parser`, `RXAParser`.

#### Scenario: Segment parser calls mapper not static
- **WHEN** `PIDParser` processes a race field
- **THEN** it SHALL call `TerminologyMapperFactory.get().mapRace(...)` rather than `RaceAndEthnicity.*`
