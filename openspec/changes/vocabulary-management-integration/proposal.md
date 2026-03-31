# Vocabulary Management Integration

## Why

Terminology mapping in v2tofhir is implemented as a collection of static utility classes (`Systems`, `Mapping`, `Units`, `ISO3166`, `RaceAndEthnicity`) with no common abstraction. Every caller — `DatatypeConverter`, segment parsers, and downstream projects — reaches directly into these statics, making it impossible to substitute a different mapping source, customize mappings per deployment, or cache expensive lookups. This change introduces a composable `TerminologyMapper` interface family and a default implementation that wraps the existing statics, then migrates **all v2tofhir callers** to use the interface. After this change, `Systems` and `Mapping` are internal implementation details — no v2tofhir code outside the `terminology` package calls them directly.

## What Changes

### 1. New Package: `gov.cdc.izgw.v2tofhir.terminology`

Houses the interface family, result types, default implementation, and supporting utilities. All direct uses of `Systems`, `Mapping`, `Units`, `ISO3166`, and `RaceAndEthnicity` in v2tofhir are replaced with calls through the interface.

### 2. Composable Interface Family

Rather than one large interface, terminology capabilities are defined as focused sub-interfaces that can be independently implemented. `TerminologyMapper` composes all of them, making it the standard full-capability type for injection.

```
NamingSystemResolver        ← OID ↔ URI, NamingSystem registry
ConceptTranslator           ← code → code mapping ($translate equivalent)
DisplayNameResolver         ← display name lookup ($lookup equivalent)
CodeValidator               ← value set membership ($validate-code equivalent)
TerminologyLoader           ← load/retrieve ConceptMap, CodeSystem, ValueSet, NamingSystem
UnitMapper                  ← unit code → UCUM Coding
DemographicsMapper          ← race/ethnicity → US Core extensions
GeoMapper                   ← country/state lookups (ISO3166)

TerminologyMapper extends all of the above
```

This allows downstream implementations (e.g., a FHIR terminology server adapter) to implement only the sub-interfaces they support, while `DefaultTerminologyMapper` implements the full `TerminologyMapper`.

#### `NamingSystemResolver`
- `String systemUriFromOid(String oidOrAlias)` — OID or short alias → FHIR system URI
- `String oidFromSystemUri(String uri)` — URI → OID
- `NamingSystem getNamingSystem(String oidOrUriOrAlias)`
- `List<String> getSystemAliases(String oidOrUri)`
- `void registerNamingSystem(NamingSystem ns)`
- `String v2TableUri(String tableNameOrNumber)` — V2 table name/number → FHIR system URI

#### `ConceptTranslator`
- `TranslationResult translate(Coding source, String targetSystem)`
- `List<TranslationResult> translateAll(Coding source, String targetSystem)`
- `CodeableConcept translate(CodeableConcept source, String targetSystem)`
- `Coding mapCode(String sourceSystem, String code, String targetSystem)` — convenience form
- `Coding mapCode(String mappingName, Coding source)` — translate using a named concept map; `mappingName` is a context key (V2 table number, CSV map name, ConceptMap identifier, etc.) used by contextual implementations to select user/session-specific mappings

#### `DisplayNameResolver`
- `String getDisplay(String system, String code)`
- `boolean hasDisplay(String system, String code)`
- `void setDisplay(Coding coding)` — populate display on an existing Coding in-place

#### `CodeValidator`
- `boolean validateCode(String valueSetUri, String system, String code)`

#### `TerminologyLoader`
- `void load(NamingSystem ns)`
- `void load(ConceptMap cm)`
- `void load(CodeSystem cs)`
- `void load(ValueSet vs)`
- `NamingSystem getNamingSystemResource(String canonicalUrl)`
- `ConceptMap getConceptMap(String canonicalUrl)`
- `CodeSystem getCodeSystem(String canonicalUrl)`
- `ValueSet getValueSet(String canonicalUrl)`

#### `UnitMapper`
- `Coding mapUnit(String unitCode)` — converts V2 ANSI/ISO unit code to UCUM Coding

#### `DemographicsMapper`
- `void mapRace(CodeableConcept race, Extension raceExtension)` — CDC race code → US Core race extension
- `void mapEthnicity(CodeableConcept ethnicity, Extension ethnicityExtension)` — CDC ethnicity → US Core ethnicity extension

#### `GeoMapper`
- `String[] lookupCountry(String codeOrName)` — returns country data (code, alpha-3, name)
- `String normalizeCountry(String codeOrName)` — returns canonical country name

### 3. Supporting Types

- **`TranslationResult`**: target `Coding`, `ConceptMapEquivalence` code, optional comment
- **`TerminologyException`**: unchecked exception for unrecoverable mapping failures

### 4. `DefaultTerminologyMapper`

Implements `TerminologyMapper` by delegating entirely to the existing static utilities:
- `NamingSystemResolver` + `TerminologyLoader` → `Systems`
- `ConceptTranslator` + `DisplayNameResolver` + `CodeValidator` → `Mapping`
- `UnitMapper` → `Units`
- `DemographicsMapper` → `RaceAndEthnicity`
- `GeoMapper` → `ISO3166`
- `NamingSystemResolver.v2TableUri()` → `Mapping.mapTableNameToSystem()` / `Mapping.v2Table()`

**No behavior change.** All existing logic stays in the static classes. `DefaultTerminologyMapper` is a thin delegation layer. `Systems`, `Mapping`, `Units`, `ISO3166`, and `RaceAndEthnicity` are annotated `@Deprecated(since="use TerminologyMapper")` to signal they are implementation details not for direct use.

### 5. `TerminologyMapperFactory` — Environment-Driven Configuration

Introduce `TerminologyMapperFactory` in the `terminology` package to control which `TerminologyMapper` implementation the system uses:

- Reads environment variable `V2TOFHIR_TERMINOLOGY_MAPPER` (consistent with the existing `V2TOFHIR_PARSER_PACKAGES` convention)
- If set, dynamically loads and instantiates the named class via reflection (must implement `TerminologyMapper` and have a no-arg constructor)
- If absent, or if loading/instantiation fails (logged as a warning), falls back to `DefaultTerminologyMapper`
- Result is cached as a lazily-initialized static so class loading happens once per JVM

`MessageParser` initializes its `terminologyMapper` field via `TerminologyMapperFactory.get()`. Callers that need to override the mapper for a specific `MessageParser` instance can still call `messageParser.setTerminologyMapper(customMapper)` directly.

### 6. Full Migration of All v2tofhir Callers

Every place in v2tofhir that currently calls `Systems`, `Mapping`, `Units`, `ISO3166`, or `RaceAndEthnicity` directly is updated to call through a `TerminologyMapper` instance. **No class outside `gov.cdc.izgw.v2tofhir.terminology` will import these static classes after this change.**

- **`AbstractSegmentParser`**: gains a `TerminologyMapper` field, defaulting to `new DefaultTerminologyMapper()`; all segment parsers inherit it
- **`MessageParser`**: gains a `TerminologyMapper` field; passes it to segment parsers on construction
- **`DatatypeConverter`**: `convertCodeableConcept()` and `convertCoding()` gain a `TerminologyMapper` parameter; existing no-arg forms delegate to `TerminologyMapperFactory.get()` so a configured custom mapper is honoured even through static call paths
- **All segment parsers** (`PIDParser`, `OBXParser`, `RXAParser`, `ORCParser`, etc.) that call `Mapping.getMapping(...)`, `Systems.toUri(...)`, `Units.toUcum(...)`, `RaceAndEthnicity.*`: replaced with calls through `this.terminologyMapper`

## Capabilities

### New Capabilities
- `terminology-mapping-interface`: The full interface family — `TerminologyMapper` and all sub-interfaces (`NamingSystemResolver`, `ConceptTranslator`, `DisplayNameResolver`, `CodeValidator`, `TerminologyLoader`, `UnitMapper`, `DemographicsMapper`, `GeoMapper`), plus `TranslationResult` and `TerminologyException`
- `default-facade`: `DefaultTerminologyMapper` wrapping all existing static utilities — no behavior change
- `terminology-mapper-factory`: `TerminologyMapperFactory` providing environment-variable-driven implementation selection via `V2TOFHIR_TERMINOLOGY_MAPPER`

### Modified Capabilities
- None — all existing behavior is preserved through the default facade

## Impact

### Code Changed
- New package `gov.cdc.izgw.v2tofhir.terminology`: 13 new files (8 sub-interfaces + `TerminologyMapper` + `TranslationResult` + `TerminologyException` + `DefaultTerminologyMapper` + `TerminologyMapperFactory`)
- `Systems`, `Mapping`, `Units`, `ISO3166`, `RaceAndEthnicity` — moved from `gov.cdc.izgw.v2tofhir.utils` into `gov.cdc.izgw.v2tofhir.terminology`; annotated `@Deprecated(since="use TerminologyMapper")`; logic unchanged
- `AbstractSegmentParser` — gains `TerminologyMapper` field initialized via `TerminologyMapperFactory.get()`
- `MessageParser` — gains `terminologyMapper` field with getter/setter; initializes via `TerminologyMapperFactory.get()`
- `DatatypeConverter` — new overloads accepting `TerminologyMapper`; existing static overloads delegate to `TerminologyMapperFactory.get()`
- All segment parsers — terminology static calls replaced with injected mapper calls

### Code Not Changed
- Public API signatures of all existing parsers (callers not injecting a mapper get identical behavior)
- `Codes` — pre-built constants, no runtime lookup, stays as-is

### Downstream

This interface is available for use by any downstream project that depends on v2tofhir.
