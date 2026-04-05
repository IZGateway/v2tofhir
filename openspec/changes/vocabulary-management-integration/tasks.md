## 1. Interface Family — Core Types ✅

- [x] 1.1 Create `gov.cdc.izgw.v2tofhir.terminology` package directory structure
- [x] 1.2 Create `TerminologyException` (unchecked, extends `RuntimeException`; message + cause constructors)
- [x] 1.3 Create `TranslationResult` immutable record: `sourceCoding`, `resultCoding`, `mappingSource` (String), `exact` (boolean); include factory methods `exact(...)` and `approximate(...)`
- [x] 1.4 Create `NamingSystemResolver` interface with default methods: `toUri(String oid)`, `toOid(String uri)`, `getNamingSystem(String oidOrUri)`, `v2TableUri(String tableNameOrNumber)`
- [x] 1.5 Create `ConceptTranslator` interface with default methods: `translate(Coding source, String targetSystem)`, `mapCode(String mappingName, Coding source)`
- [x] 1.6 Create `DisplayNameResolver` interface with default methods: `getDisplay(String system, String code)`, `hasDisplay(String system, String code)`, `setDisplay(String system, String code, String display)`
- [x] 1.7 Create `CodeValidator` interface with default method: `validateCode(String valueSetUri, String system, String code)`
- [x] 1.8 Create `TerminologyLoader` interface with default methods: `load(Resource resource)` (throws `TerminologyException`), `getNamingSystem(String id)`, `getConceptMap(String id)`, `getCodeSystem(String id)`, `getValueSet(String id)`; `load` default throws `UnsupportedOperationException`
- [x] 1.9 Create `UnitMapper` interface with default method: `mapUnit(String unitCode)`
- [x] 1.10 Create `DemographicsMapper` interface with default methods: `mapRace(String code, String codeSystem)`, `mapEthnicity(String code, String codeSystem)`
- [x] 1.11 Create `GeoMapper` interface with default methods: `lookupCountry(String code, String codeSystem)`, `normalizeCountry(String nameOrCode)`
- [x] 1.12 Create composite `TerminologyMapper` interface extending all eight sub-interfaces; no additional methods
  > **Note:** `NamingSystemResolver` and `TerminologyLoader` both declare `getNamingSystem(String)` with the same signature. `TerminologyMapper` resolves the diamond with an overriding default that returns `Optional.empty()`; concrete implementations should check both registries.

## 2. Package Migration — Static Utility Classes ✅

- [x] 2.1 Move `Systems.java` from `gov.cdc.izgw.v2tofhir.utils` to `gov.cdc.izgw.v2tofhir.terminology`; update package declaration; fix all intra-project imports; verify tests pass
- [x] 2.2 Move `Mapping.java` to `gov.cdc.izgw.v2tofhir.terminology`; update package declaration; fix all intra-project imports; verify tests pass
- [x] 2.3 Move `Units.java` to `gov.cdc.izgw.v2tofhir.terminology`; update package declaration; fix all intra-project imports; verify tests pass
- [x] 2.4 Move `ISO3166.java` to `gov.cdc.izgw.v2tofhir.terminology`; update package declaration; fix all intra-project imports; verify tests pass
- [x] 2.5 Move `RaceAndEthnicity.java` to `gov.cdc.izgw.v2tofhir.terminology`; update package declaration; fix all intra-project imports; verify tests pass
- [x] 2.6 Annotate each moved class with `@Deprecated(since="use TerminologyMapper")` and add Javadoc `@deprecated` pointing to the corresponding `TerminologyMapper` method(s)
- [x] 2.7 Create a deprecated forwarding stub in `gov.cdc.izgw.v2tofhir.utils` for `Systems`: same class name, every public static method delegates to `gov.cdc.izgw.v2tofhir.terminology.Systems`; class annotated `@Deprecated(since="moved to gov.cdc.izgw.v2tofhir.terminology; use TerminologyMapper")`
- [x] 2.8 Create a deprecated forwarding stub in `gov.cdc.izgw.v2tofhir.utils` for `Mapping`; same pattern as 2.7
- [x] 2.9 Create a deprecated forwarding stub in `gov.cdc.izgw.v2tofhir.utils` for `Units`; same pattern as 2.7
- [x] 2.10 Create a deprecated forwarding stub in `gov.cdc.izgw.v2tofhir.utils` for `ISO3166`; same pattern as 2.7
- [x] 2.11 Create a deprecated forwarding stub in `gov.cdc.izgw.v2tofhir.utils` for `RaceAndEthnicity`; same pattern as 2.7
- [x] 2.12 Verify all existing tests still pass with the forwarding stubs in place

## 3. DefaultTerminologyMapper ✅

> **Implementation notes from caller analysis:**
>
> **Wrapping null-returning statics** — use `Optional.ofNullable(...)`:
> ```java
> public Optional<Coding> mapUnit(String unitCode) {
>     return Optional.ofNullable(Units.toUcum(unitCode));
> }
> public Optional<String> toUri(String oid) {
>     return Optional.ofNullable(Systems.toUri(oid));
> }
> ```
>
> **Wrapping void in-place mutation methods** — `RaceAndEthnicity.setRaceCode/setEthnicityCode` and `Mapping.map(Coding)` mutate an object passed in rather than returning a value. `DefaultTerminologyMapper` must own construction of the object, call the static mutator, then return it wrapped in `Optional`:
> ```java
> public Optional<Extension> mapRace(String code, String codeSystem) {
>     CodeableConcept race = new CodeableConcept();
>     Extension ext = new Extension().setUrl(RaceAndEthnicity.US_CORE_RACE);
>     RaceAndEthnicity.setRaceCode(race, ext);
>     return ext.hasExtension() ? Optional.of(ext) : Optional.empty();
> }
> ```
>
> **`Mapping.getMapping()` returns null** — guard before calling `mapCode` on the result:
> ```java
> public Optional<TranslationResult> mapCode(String mappingName, Coding source) {
>     Mapping m = Mapping.getMapping(mappingName);
>     if (m == null) return Optional.empty();
>     Coding result = m.mapCode(source, true);
>     return Optional.ofNullable(result)
>         .map(c -> TranslationResult.exact(source, c, mappingName));
> }
> ```

- [x] 3.1 Create `DefaultTerminologyMapper` implementing `TerminologyMapper`; delegate `NamingSystemResolver` methods to `Systems`
- [x] 3.2 Implement `ConceptTranslator` methods: `mapCode` → `Mapping.getMapping(name).mapCode(...)`, `translate` → `Mapping` lookup by source/target system pair
- [x] 3.3 Implement `DisplayNameResolver` methods: `getDisplay` / `hasDisplay` / `setDisplay` → `Systems` display registry
- [x] 3.4 Implement `UnitMapper.mapUnit` → `Units.toUcum(...)`
- [x] 3.5 Implement `DemographicsMapper.mapRace` and `mapEthnicity` → `RaceAndEthnicity`; **note:** `setRaceCode`/`setEthnicityCode` are void mutators — `DefaultTerminologyMapper` must construct the `Extension`, pass it to the mutator, then return it wrapped in `Optional` (see note above)
- [x] 3.6 Implement `GeoMapper.lookupCountry` and `normalizeCountry` → `ISO3166`
- [x] 3.7 Implement `CodeValidator.validateCode` → `Systems` value set membership check (or return `false` if not supported by current statics)
- [x] 3.8 Implement `TerminologyLoader` load/retrieve methods using the in-memory registries in `Systems` and `Mapping`; `load` for unsupported resource types throws `TerminologyException`
- [x] 3.9 Write unit tests: for each delegating method, assert result equals direct static call result
  > **Implementation notes:** `RaceAndEthnicity.setRaceCode/setEthnicityCode` default to "UNK" for unrecognised codes, so `mapRace`/`mapEthnicity` always return a present `Optional`. `translate()` returns `Optional.empty()` — no backing API to iterate concept maps by source/target system pair. Per-instance maps (ConceptMap, CodeSystem, ValueSet) are isolated between `DefaultTerminologyMapper` instances. 35/35 tests pass.

## 4. TerminologyMapperFactory ✅

- [x] 4.1 Create `TerminologyMapperFactory` with `static TerminologyMapper get()` backed by a lazily-initialized `AtomicReference`
- [x] 4.2 Implement environment-variable resolution: read `V2TOFHIR_TERMINOLOGY_MAPPER`; if absent, use `DefaultTerminologyMapper`
- [x] 4.3 Implement dynamic class loading: `Class.forName(name)`, cast check to `TerminologyMapper`, no-arg constructor invocation; wrap all failures (ClassNotFoundException, ClassCastException, ReflectiveOperationException) with warning log + fallback
- [x] 4.4 Add package-private `resetForTesting()` method that clears the cached instance (used only in tests)
- [x] 4.5 Write unit tests: no env var → `DefaultTerminologyMapper`; valid class → custom instance; class-not-found → default + warning logged; wrong type → default + warning logged; constructor throws → default + warning logged; repeated `get()` calls return same reference
  > **Implementation notes:** `loadMapper(String)` is package-private so tests in `gov.cdc.izgw.v2tofhir.terminology` package can call it without env-var mutation. Test class lives in `src/test/java/gov/cdc/izgw/v2tofhir/terminology/`. Inner test classes (`ValidCustomMapper`, `NotAMapper`, `SubInterfaceOnlyMapper`, `ThrowingConstructorMapper`) provide all error-case scenarios. 10/10 tests pass.

## 5. MessageParser and AbstractSegmentParser Integration

- [x] 5.1 ~~Add `TerminologyMapper terminologyMapper` field to `MessageParser`; initialize to `TerminologyMapperFactory.get()` in all constructors~~ **Reverted** — per-instance field is unnecessary; `TerminologyMapperFactory` is the single injection point
- [x] 5.2 ~~Add `getTerminologyMapper()` and `setTerminologyMapper(TerminologyMapper)` to `MessageParser`~~ **Reverted** — same rationale as 5.1
- [x] 5.3 Verified `AbstractSegmentParser` stores a `MessageParser` reference via `@Getter`; no changes needed
- [x] 5.4 ~~Write per-instance mapper tests~~ **Deleted** — tests removed along with reverted code
  > **Design decision:** A per-instance `terminologyMapper` on `MessageParser` is redundant once `DatatypeConverter` and segment parsers call `TerminologyMapperFactory.get()` directly. The factory is the single injection point. Task 5 produced no net code change.

## 6. DatatypeConverter Factory Delegation ✅

- [x] 6.1 ~~Add `convertCoding(Type t, String table, TerminologyMapper m)` overload~~ **Dropped** — no per-instance mapper to thread through; factory covers all cases
- [x] 6.2 ~~Add `convertCodeableConcept(Type t, String table, TerminologyMapper m)` overload~~ **Dropped** — same rationale as 6.1
- [x] 6.3 Updated `toCoding(Type, String)`, `toCodeableConcept(Type, String)`, and private helpers (`toCodingFromComposite`, `toCodingFromMSG`) to use private `mapCoding(Coding)` helper that routes through `TerminologyMapperFactory.get()`; also migrated `toCode(Type, String)` V2-table→URI mapping to use `TerminologyMapperFactory.get().v2TableUri(table)`
- [x] 6.4 Tests: 7 tests covering `toCoding`/`toCodeableConcept` backward compatibility, system URI normalization, factory singleton usage; 50/50 total tests pass
  > **Implementation notes:** `Mapping.map(Coding)` = `mapSystem` + `setDisplay` — no direct `TerminologyMapper` equivalent. Solution: private `mapCoding(Coding)` helper in `DatatypeConverter` decomposes into `mapper.v2TableUri()` + `mapper.getDisplay()` with `ORIGINAL_SYSTEM`/`ORIGINAL_DISPLAY` userData preservation. `DefaultTerminologyMapper.v2TableUri()` updated to delegate to `Mapping.mapTableNameToSystem()` (not `Mapping.v2Table()`) for faithful fallback behaviour.

## 7. Caller Migration — Segment Parsers

> **Migration patterns from caller analysis:**
>
> **Null-returning lookups** — callers already null-check; migrate to `Optional` idioms:
> ```java
> // Before
> Coding unit = Units.toUcum(code);
> if (unit != null) { ... }
> // After
> mapper.mapUnit(code).ifPresent(unit -> { ... });
> ```
>
> **Void in-place mutation (race/ethnicity)** — ownership of extension construction moves to the mapper:
> ```java
> // Before
> Extension raceExtension = patient.addExtension().setUrl(RaceAndEthnicity.US_CORE_RACE);
> RaceAndEthnicity.setRaceCode(race, raceExtension);
> // After
> mapper.mapRace(code, codeSystem).ifPresent(patient::addExtension);
> ```
>
> **`Mapping.getMapping()` latent NPE** — `NK1Parser` lines 102–109 call `getMapping()` and use the result without a null check. Migrating to `mapper.mapCode(name, coding)` fixes this implicitly since the interface returns `Optional`.
>
> **Constants unchanged** — `Systems.LOINC`, `Systems.MVX`, `Systems.SSN`, etc. are referenced as plain constants, not through method calls, and require no migration.

- [x] 7.1 Migrate `DatatypeConverter` remaining direct static calls to use `TerminologyMapperFactory.get()`
- [x] 7.2 Migrate `ERRParser`: replace `Systems`/`Mapping` calls with `TerminologyMapperFactory.get()`
- [x] 7.3 Migrate `IzDetail`: replace `Systems`/`Mapping` calls with `TerminologyMapperFactory.get()`
- [x] 7.4 Migrate `NK1Parser`: replace `Systems`/`Mapping`/`RaceAndEthnicity` calls with `TerminologyMapperFactory.get()`; latent NPE on `getMapping()` fixed implicitly via `mapCode(name, coding)`
- [x] 7.5 Migrate `OBXParser`: replace `Systems`/`Mapping`/`Units` calls with `TerminologyMapperFactory.get()`
- [x] 7.6 Migrate `PIDParser`: replace `Systems`/`Mapping`/`RaceAndEthnicity` calls with `TerminologyMapperFactory.get()`; uses `mapper.mapRace/mapEthnicity(firstCoding.getCode(), firstCoding.getSystem()).ifPresent(...)` pattern
- [x] 7.7 `PV1Parser`: no method calls to migrate (only constant references); no changes needed
- [x] 7.8 Migrate `RXAParser`: replace `Systems`/`Mapping`/`Units` calls with `TerminologyMapperFactory.get()`
- [x] 7.9 Full test suite passed: 23,448 tests, 0 failures, 0 errors
  > **Implementation notes:**
  > - `Mapping.setDisplay(Coding)` replaced by `mapper.getDisplay(system, code).ifPresent(coding::setDisplay)` — system normalization is a no-op for already-normalized URIs (Systems.ID_TYPE etc.)
  > - `Mapping.mapSystem(Identifier)` at DatatypeConverter line 709 retained — uses `getPreferredIdSystem()` which has no TerminologyMapper equivalent
  > - `Systems.getSystemNames(system)` in PIDParser.setGender retained — system alias lookup has no TerminologyMapper equivalent
  > - `Units.toUcum()` replaced by `mapper.mapUnit(code).ifPresentOrElse(...)` preserving full if/else branch logic
  > - `Mapping.getMapping(name).mapCode(coding, true)` replaced by `mapper.mapCode(name, coding).ifPresent(r -> mappedConcept.addCoding(r.resultCoding()))` — also fixes latent NPE in NK1Parser

## 8. ArchUnit Boundary Enforcement

- [x] 8.1 Add `archunit` dependency to `pom.xml` (test scope): `com.tngtech.archunit:archunit-junit5`
- [x] 8.2 Create `TerminologyPackageBoundaryTests` in `src/test/java`: assert no class outside `gov.cdc.izgw.v2tofhir.terminology` imports `Systems`, `Mapping`, `Units`, `ISO3166`, or `RaceAndEthnicity` from the `terminology` package, with the exception of the five forwarding stubs in `gov.cdc.izgw.v2tofhir.utils`
- [x] 8.3 Run `TerminologyPackageBoundaryTests` and confirm it passes after all migrations in task group 7

## 9. `TerminologyMapperFactory` — Default Supplier Override

- [x] 9.1 Add `static void setDefaultSupplier(Supplier<TerminologyMapper> supplier)` to `TerminologyMapperFactory`; implementation: if the `INSTANCE` `AtomicReference` already holds a resolved value, log a `WARN` and return (no-op); otherwise store the supplier for use when `V2TOFHIR_TERMINOLOGY_MAPPER` is absent
- [x] 9.2 Update `resolve(String className)` to call `defaultSupplier.get()` instead of `new DefaultTerminologyMapper()` when `className` is null or blank, so that a supplier registered via `setDefaultSupplier` takes effect; error-fallback paths also use `defaultSupplier.get()`
- [x] 9.3 Write unit tests: (a) supplier is called when env var is absent and supplier was pre-registered; (b) supplier is ignored when env var is set and names a valid class; (c) second call to `setDefaultSupplier` after resolution is a no-op and logs WARN; (d) `resetForTesting()` restores the default supplier; existing 10 tests continue to pass (14 total)
- [x] 9.4 Update `resetForTesting()` to also reset the stored supplier to `DefaultTerminologyMapper::new` so that test isolation is maintained
