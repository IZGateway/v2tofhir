## 1. Interface Family — Core Types

- [ ] 1.1 Create `gov.cdc.izgw.v2tofhir.terminology` package directory structure
- [ ] 1.2 Create `TerminologyException` (unchecked, extends `RuntimeException`; message + cause constructors)
- [ ] 1.3 Create `TranslationResult` immutable record: `sourceCoding`, `resultCoding`, `mappingSource` (String), `exact` (boolean); include factory methods `exact(...)` and `approximate(...)`
- [ ] 1.4 Create `NamingSystemResolver` interface with default methods: `toUri(String oid)`, `toOid(String uri)`, `getNamingSystem(String oidOrUri)`, `v2TableUri(String tableNameOrNumber)`
- [ ] 1.5 Create `ConceptTranslator` interface with default methods: `translate(Coding source, String targetSystem)`, `mapCode(String mappingName, Coding source)`
- [ ] 1.6 Create `DisplayNameResolver` interface with default methods: `getDisplay(String system, String code)`, `hasDisplay(String system, String code)`, `setDisplay(String system, String code, String display)`
- [ ] 1.7 Create `CodeValidator` interface with default method: `validateCode(String valueSetUri, String system, String code)`
- [ ] 1.8 Create `TerminologyLoader` interface with default methods: `load(Resource resource)` (throws `TerminologyException`), `getNamingSystem(String id)`, `getConceptMap(String id)`, `getCodeSystem(String id)`, `getValueSet(String id)`; `load` default throws `UnsupportedOperationException`
- [ ] 1.9 Create `UnitMapper` interface with default method: `mapUnit(String unitCode)`
- [ ] 1.10 Create `DemographicsMapper` interface with default methods: `mapRace(String code, String codeSystem)`, `mapEthnicity(String code, String codeSystem)`
- [ ] 1.11 Create `GeoMapper` interface with default methods: `lookupCountry(String code, String codeSystem)`, `normalizeCountry(String nameOrCode)`
- [ ] 1.12 Create composite `TerminologyMapper` interface extending all eight sub-interfaces; no additional methods

## 2. Package Migration — Static Utility Classes

- [ ] 2.1 Move `Systems.java` from `gov.cdc.izgw.v2tofhir.utils` to `gov.cdc.izgw.v2tofhir.terminology`; update package declaration; fix all intra-project imports; verify tests pass
- [ ] 2.2 Move `Mapping.java` to `gov.cdc.izgw.v2tofhir.terminology`; update package declaration; fix all intra-project imports; verify tests pass
- [ ] 2.3 Move `Units.java` to `gov.cdc.izgw.v2tofhir.terminology`; update package declaration; fix all intra-project imports; verify tests pass
- [ ] 2.4 Move `ISO3166.java` to `gov.cdc.izgw.v2tofhir.terminology`; update package declaration; fix all intra-project imports; verify tests pass
- [ ] 2.5 Move `RaceAndEthnicity.java` to `gov.cdc.izgw.v2tofhir.terminology`; update package declaration; fix all intra-project imports; verify tests pass
- [ ] 2.6 Annotate each moved class with `@Deprecated(since="use TerminologyMapper")` and add Javadoc `@deprecated` pointing to the corresponding `TerminologyMapper` method(s)
- [ ] 2.7 Create a deprecated forwarding stub in `gov.cdc.izgw.v2tofhir.utils` for `Systems`: same class name, every public static method delegates to `gov.cdc.izgw.v2tofhir.terminology.Systems`; class annotated `@Deprecated(since="moved to gov.cdc.izgw.v2tofhir.terminology; use TerminologyMapper")`
- [ ] 2.8 Create a deprecated forwarding stub in `gov.cdc.izgw.v2tofhir.utils` for `Mapping`; same pattern as 2.7
- [ ] 2.9 Create a deprecated forwarding stub in `gov.cdc.izgw.v2tofhir.utils` for `Units`; same pattern as 2.7
- [ ] 2.10 Create a deprecated forwarding stub in `gov.cdc.izgw.v2tofhir.utils` for `ISO3166`; same pattern as 2.7
- [ ] 2.11 Create a deprecated forwarding stub in `gov.cdc.izgw.v2tofhir.utils` for `RaceAndEthnicity`; same pattern as 2.7
- [ ] 2.12 Verify all existing tests still pass with the forwarding stubs in place

## 3. DefaultTerminologyMapper

- [ ] 3.1 Create `DefaultTerminologyMapper` implementing `TerminologyMapper`; delegate `NamingSystemResolver` methods to `Systems`
- [ ] 3.2 Implement `ConceptTranslator` methods: `mapCode` → `Mapping.getMapping(name).mapCode(...)`, `translate` → `Mapping` lookup by source/target system pair
- [ ] 3.3 Implement `DisplayNameResolver` methods: `getDisplay` / `hasDisplay` / `setDisplay` → `Systems` display registry
- [ ] 3.4 Implement `UnitMapper.mapUnit` → `Units.toUcum(...)`
- [ ] 3.5 Implement `DemographicsMapper.mapRace` and `mapEthnicity` → `RaceAndEthnicity`
- [ ] 3.6 Implement `GeoMapper.lookupCountry` and `normalizeCountry` → `ISO3166`
- [ ] 3.7 Implement `CodeValidator.validateCode` → `Systems` value set membership check (or return `false` if not supported by current statics)
- [ ] 3.8 Implement `TerminologyLoader` load/retrieve methods using the in-memory registries in `Systems` and `Mapping`; `load` for unsupported resource types throws `TerminologyException`
- [ ] 3.9 Write unit tests: for each delegating method, assert result equals direct static call result

## 4. TerminologyMapperFactory

- [ ] 4.1 Create `TerminologyMapperFactory` with `static TerminologyMapper get()` backed by a lazily-initialized `AtomicReference`
- [ ] 4.2 Implement environment-variable resolution: read `V2TOFHIR_TERMINOLOGY_MAPPER`; if absent, use `DefaultTerminologyMapper`
- [ ] 4.3 Implement dynamic class loading: `Class.forName(name)`, cast check to `TerminologyMapper`, no-arg constructor invocation; wrap all failures (ClassNotFoundException, ClassCastException, ReflectiveOperationException) with warning log + fallback
- [ ] 4.4 Add package-private `resetForTesting()` method that clears the cached instance (used only in tests)
- [ ] 4.5 Write unit tests: no env var → `DefaultTerminologyMapper`; valid class → custom instance; class-not-found → default + warning logged; wrong type → default + warning logged; constructor throws → default + warning logged; repeated `get()` calls return same reference

## 5. MessageParser and AbstractSegmentParser Integration

- [ ] 5.1 Add `TerminologyMapper terminologyMapper` field to `MessageParser`; initialize to `TerminologyMapperFactory.get()` in all constructors
- [ ] 5.2 Add `getTerminologyMapper()` and `setTerminologyMapper(TerminologyMapper)` to `MessageParser`
- [ ] 5.3 Verify `AbstractSegmentParser` already stores a `MessageParser` reference and that `getMessageParser().getTerminologyMapper()` compiles correctly (no code change expected)
- [ ] 5.4 Write unit tests: default mapper non-null after construction; `setTerminologyMapper` overrides; segment parser resolves mapper via `getMessageParser()`

## 6. DatatypeConverter Overloads

- [ ] 6.1 Add `convertCoding(Type t, String table, TerminologyMapper m)` overload to `DatatypeConverter`
- [ ] 6.2 Add `convertCodeableConcept(Type t, String table, TerminologyMapper m)` overload to `DatatypeConverter`
- [ ] 6.3 Update existing `convertCoding(Type t, String table)` and `convertCodeableConcept(Type t, String table)` to delegate to `TerminologyMapperFactory.get()` instead of direct static calls
- [ ] 6.4 Write unit tests: existing two-arg form produces identical output before and after; mapper-arg form uses the supplied mapper; static fallback uses factory instance

## 7. Caller Migration — Segment Parsers

- [ ] 7.1 Migrate `DatatypeConverter` remaining direct static calls (other than the overloads updated in task 6) to use injected mapper
- [ ] 7.2 Migrate `ERRParser`: replace `Systems`/`Mapping` calls with `getMessageParser().getTerminologyMapper()`
- [ ] 7.3 Migrate `IzDetail`: replace `Systems`/`Mapping` calls with `getMessageParser().getTerminologyMapper()`
- [ ] 7.4 Migrate `NK1Parser`: replace `Systems`/`Mapping`/`RaceAndEthnicity` calls with `getMessageParser().getTerminologyMapper()`
- [ ] 7.5 Migrate `OBXParser`: replace `Systems`/`Mapping`/`Units` calls with `getMessageParser().getTerminologyMapper()`
- [ ] 7.6 Migrate `PIDParser`: replace `Systems`/`Mapping`/`RaceAndEthnicity` calls with `getMessageParser().getTerminologyMapper()`
- [ ] 7.7 Migrate `PV1Parser`: replace `Systems`/`Mapping` calls with `getMessageParser().getTerminologyMapper()`
- [ ] 7.8 Migrate `RXAParser`: replace `Systems`/`Mapping`/`Units` calls with `getMessageParser().getTerminologyMapper()`
- [ ] 7.9 Run full test suite after all migrations; assert zero regressions

## 8. ArchUnit Boundary Enforcement

- [ ] 8.1 Add `archunit` dependency to `pom.xml` (test scope): `com.tngtech.archunit:archunit-junit5`
- [ ] 8.2 Create `TerminologyPackageBoundaryTest` in `src/test/java`: assert no class outside `gov.cdc.izgw.v2tofhir.terminology` imports `Systems`, `Mapping`, `Units`, `ISO3166`, or `RaceAndEthnicity` from the `terminology` package, with the exception of the five forwarding stubs in `gov.cdc.izgw.v2tofhir.utils`
- [ ] 8.3 Run `TerminologyPackageBoundaryTest` and confirm it passes after all migrations in task group 7
