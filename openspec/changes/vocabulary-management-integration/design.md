## Context

v2tofhir performs all terminology mapping through five static utility classes: `Systems` (OID↔URI), `Mapping` (CSV-loaded concept maps), `Units` (UCUM), `ISO3166` (countries), and `RaceAndEthnicity` (US Core demographics). These are called directly by `DatatypeConverter` and six segment parsers (`DatatypeConverter`, `ERRParser`, `IzDetail`, `NK1Parser`, `OBXParser`, `PIDParser`, `PV1Parser`, `RXAParser`).

The existing wiring is purely programmatic with no Spring injection. `MessageParser` is intentionally lightweight — callers construct `new MessageParser()` directly. Each `AbstractSegmentParser` is constructed with the `MessageParser` instance passed in:

```java
protected AbstractSegmentParser(MessageParser p, String segmentName)
```

This means `MessageParser` is the natural home for a `TerminologyMapper` field — segment parsers already hold a `getMessageParser()` reference and can reach the mapper without constructor changes.

`DatatypeConverter` is a static utility class with no instance state; its callers don't go through `MessageParser`.

## Goals / Non-Goals

**Goals:**
- Define the composable `TerminologyMapper` interface family in `gov.cdc.izgw.v2tofhir.terminology`
- Implement `DefaultTerminologyMapper` delegating to existing statics with no behavior change
- Migrate all v2tofhir callers so nothing outside `terminology` imports `Systems`, `Mapping`, `Units`, `ISO3166`, or `RaceAndEthnicity`
- Keep `MessageParser` construction cheap and no-arg-friendly; don't require callers to wire a mapper

**Non-Goals:**
- Caching, contextual mapping, or FHIR server integration (out of scope)
- Migrating callers in downstream projects
- Making `Systems`, `Mapping`, `Units`, `ISO3166`, `RaceAndEthnicity` package-private now — they remain `public` after the move; reducing visibility is deferred to a follow-up once downstream adoption is confirmed

## Decisions

### 1. `TerminologyMapper` is injected through `MessageParser`, not individual segment parsers

**Decision:** Add `TerminologyMapper terminologyMapper` to `MessageParser`. Segment parsers access it via `getMessageParser().getTerminologyMapper()`. `MessageParser()` default constructor sets `terminologyMapper = new DefaultTerminologyMapper()`.

**Alternative considered:** Add `TerminologyMapper` to `AbstractSegmentParser`'s constructor. Rejected — 15+ concrete parsers all call `super(messageParser, segmentName)`. Adding a third parameter would require changing every one of them, and it creates a redundant copy of the same mapper reference in every parser instance.

**Alternative considered:** Static `TerminologyMapper` singleton (similar to current static utility pattern). Rejected — defeats the purpose of the abstraction; makes testing harder.

### 2. `DatatypeConverter` gains overloads rather than instance methods

**Decision:** `DatatypeConverter` keeps its static methods for backward compatibility. New overloads `convertCoding(Type t, String table, TerminologyMapper m)` and `convertCodeableConcept(Type t, String table, TerminologyMapper m)` are added. Existing no-arg / two-arg forms delegate to `TerminologyMapperFactory.get()` so that a configured custom mapper is honoured even through static call paths.

**Alternative considered:** Convert `DatatypeConverter` to an instance class injected through `MessageParser`. Rejected — `DatatypeConverter` is a pure utility with no state; making it an instance class would require widespread changes to callers that use it standalone (e.g., test code, downstream type converters).

### 3. Composable sub-interfaces; `TerminologyMapper` extends all

**Decision:** Eight focused sub-interfaces (`NamingSystemResolver`, `ConceptTranslator`, `DisplayNameResolver`, `CodeValidator`, `TerminologyLoader`, `UnitMapper`, `DemographicsMapper`, `GeoMapper`); `TerminologyMapper extends` all eight. Callers that only need OID resolution can accept `NamingSystemResolver`; callers that need everything accept `TerminologyMapper`.

**Alternative considered:** Single flat `TerminologyMapper` interface. Rejected — makes partial implementations (e.g., a FHIR terminology server that has no unit mapping) require stub methods for every unrelated operation.

**Alternative considered:** Abstract base class with no-op defaults instead of sub-interfaces. Rejected — Java interfaces with `default` methods provide the same capability without constraining the inheritance hierarchy.

### 4. `v2TableUri()` lives on `NamingSystemResolver`

**Decision:** V2 table name-to-URI conversion (`Mapping.mapTableNameToSystem()`, `Mapping.v2Table()`) is exposed as `NamingSystemResolver.v2TableUri(String tableNameOrNumber)`. V2 table URIs are just a special form of system naming (`http://terminology.hl7.org/CodeSystem/v2-NNNN`); there is no semantic distinction that warrants a separate sub-interface.

### 5. `TerminologyMapperFactory` for environment-driven configuration

**Decision:** Introduce `TerminologyMapperFactory` with a `get()` static method. It reads `V2TOFHIR_TERMINOLOGY_MAPPER` from the environment (consistent with `V2TOFHIR_PARSER_PACKAGES`), uses `Class.forName()` + no-arg constructor reflection to instantiate the named class, and falls back to `DefaultTerminologyMapper` on any failure (with a logged warning). The resolved instance is cached in a static field (lazy init). `MessageParser` uses `TerminologyMapperFactory.get()` to initialize its `terminologyMapper` field; a `setTerminologyMapper()` setter allows per-instance override.

`TerminologyMapperFactory` also exposes `setDefaultSupplier(Supplier<TerminologyMapper>)` for downstream libraries that embed v2tofhir and provide their own `TerminologyMapper` extension. A downstream library can call `setDefaultSupplier(() -> new MyTerminologyMapper())` during its own initialization so that the factory uses the library's implementation when `V2TOFHIR_TERMINOLOGY_MAPPER` is not set — without requiring the end-user to configure any environment variable. `setDefaultSupplier` is a no-op (with a `WARN` log) if called after the factory has already resolved its instance. It also resets to `DefaultTerminologyMapper::new` inside `resetForTesting()` to maintain test isolation.

**Alternative considered:** Spring `@Bean` / `@ConditionalOnProperty`. Rejected — v2tofhir has no Spring context in the parser path; `MessageParser` is instantiated directly with `new MessageParser()`.

**Alternative considered:** `ServiceLoader` (Java SPI). Considered — cleaner than raw reflection, discoverable via `META-INF/services`. Rejected for now: requires the custom implementation to package a service descriptor file, adding friction for simple deployments. Can be added as a secondary discovery mechanism in a future change.

### 6. `@Deprecated` on static utility classes

**Decision:** Annotate `Systems`, `Mapping`, `Units`, `ISO3166`, `RaceAndEthnicity` with `@Deprecated(since="use TerminologyMapper")` and a Javadoc `@deprecated` note pointing to the interface. No access modifier changes — these classes remain `public` to avoid breaking downstream code that may depend on them directly.

### 7. Move static utility classes into `gov.cdc.izgw.v2tofhir.terminology`

**Decision:** Move `Systems`, `Mapping`, `Units`, `ISO3166`, and `RaceAndEthnicity` from `gov.cdc.izgw.v2tofhir.utils` into `gov.cdc.izgw.v2tofhir.terminology`. `DefaultTerminologyMapper` and `TerminologyMapperFactory` are created directly in `gov.cdc.izgw.v2tofhir.terminology` as well. This consolidates all terminology-related code in one package, makes the package boundary self-documenting, and brings `DefaultTerminologyMapper` into the same package as the classes it delegates to (enabling future reduction of visibility on those statics without changing the public API).

The move is a package rename — all intra-package references are updated in the same commit. All external callers within v2tofhir are migrated to the `TerminologyMapper` interface as part of this change, so no v2tofhir code will need to import the old `utils` paths after the migration. A deprecated forwarding stub is left in `gov.cdc.izgw.v2tofhir.utils` for each moved class so that downstream projects continue to compile; each stub delegates every static method to the class in the new package. `@Deprecated(since="use TerminologyMapper")` is applied to both the stub and the moved class.

**Alternative considered:** Leave the static utilities in `utils` and only put interfaces + `DefaultTerminologyMapper` in `terminology`. Rejected — `DefaultTerminologyMapper` would need cross-package access to `utils` internals, and the package split would undercut the ability to make the statics package-private in the future.

### 8. `ConceptTranslator.mapCodeableConcept` — multi-coding, multi-system lookup

**Decision:** Add `default CodeType mapCodeableConcept(String mappingName, CodeableConcept cc)` to the `ConceptTranslator` sub-interface. The method iterates the codings of a `CodeableConcept` against a named ConceptMap using a two-pass strategy that correctly distinguishes explicit matches from `unmapped.mode = "provided"` pass-throughs:

- **Pass 1** — iterate all codings; call `mapCode(mappingName, coding)`; return the first result where `TranslationResult.exact() == true` (explicit ConceptMap entry hit).
- **Pass 2** — iterate all codings again; return the first non-empty `TranslationResult` regardless of `exact` (accepts a pass-through if no explicit match exists anywhere in the concept).
- **Pass 3** — return `cc.getCodingFirstRep().getCodeElement()` unchanged as a last resort.

**Rationale:** Single-pass acceptance of the first non-empty result would always return a code when `unmapped.mode = "provided"` is in effect, because every coding produces a result. The `TranslationResult.exact()` flag (already present on the immutable record) is the correct discriminator: it is `true` only when the source code was explicitly listed in a ConceptMap group element, and `false` when the result came from an `unmapped` clause. The two-pass design maximises the chance that an explicitly mapped coding is preferred over an unmapped one, even when the explicitly mapped coding is not first in the list.

**Use in downstream libraries:** A downstream library that extends `CdaTerminologyMapper` (or any `TerminologyMapper` subclass) can call `mapCodeableConcept("my-priority-" + resourceType.getSimpleName(), cc)` to route per-resource-type priority ConceptMaps through the same two-pass logic, with sparse override via the `unmapped.other-map` chain described in that library's own design.

**Alternative considered:** A new `translateCodeableConcept` method returning `Optional<TranslationResult>`. Discarded — callers need a `CodeType` directly; wrapping in Optional adds noise at every call site for a method whose Pass 3 always produces a value.

## Risks / Trade-offs

**Risk: Segment parsers not yet updated call statics alongside new mapper calls** → Mitigation: Migration is done in a single pass per file in the tasks phase; CI import checks (or a checkstyle rule forbidding direct import of the statics outside `terminology`) prevent regression.

**Risk: `DefaultTerminologyMapper` delegates to statics that themselves call each other (e.g., `RaceAndEthnicity` calls `Mapping`), creating subtle double-delegation** → Mitigation: `DefaultTerminologyMapper` delegates to the top-level static entry points only; it does not attempt to route inter-utility calls through the interface. This is acceptable because the statics are internal.

**Risk: Downstream projects import `Systems` / `Mapping` etc. from `gov.cdc.izgw.v2tofhir.utils` and will get compile errors after the package move** → Mitigation: After moving each class, leave a forwarding stub in `gov.cdc.izgw.v2tofhir.utils` with the same class name. Each stub is annotated `@Deprecated(since="moved to gov.cdc.izgw.v2tofhir.terminology; use TerminologyMapper")` and delegates every static method to the class in the new package. Downstream code continues to compile without changes but receives deprecation warnings, giving consumers time to migrate on their own schedule.

**Risk: `MessageParser` is cheap to construct (`new MessageParser()`) but `DefaultTerminologyMapper()` initialization loads static class data on first construction** → Mitigation: Static class data (`Systems`, `Mapping` CSV files) is already loaded at class initialization time before any `MessageParser` is constructed; `DefaultTerminologyMapper` constructor is effectively free.

## Migration Plan

1. Create `gov.cdc.izgw.v2tofhir.terminology` package with all interfaces, `TranslationResult`, `TerminologyException`, `DefaultTerminologyMapper`, and `TerminologyMapperFactory` — no callers changed yet; all tests pass
2. Move `Systems`, `Mapping`, `Units`, `ISO3166`, `RaceAndEthnicity` from `gov.cdc.izgw.v2tofhir.utils` into `gov.cdc.izgw.v2tofhir.terminology`; update all intra-v2tofhir imports; verify all tests pass
3. Add `terminologyMapper` field to `MessageParser` (initialized via `TerminologyMapperFactory.get()`, with `setTerminologyMapper()` setter); add `TerminologyMapper`-accepting overloads to `DatatypeConverter`; update existing static overloads in `DatatypeConverter` to delegate to `TerminologyMapperFactory.get()` — all tests still pass
4. Migrate segment parsers one file at a time: replace static imports with `getMessageParser().getTerminologyMapper()` calls; verify tests pass after each file
5. Annotate `Systems`, `Mapping`, `Units`, `ISO3166`, `RaceAndEthnicity` as `@Deprecated(since="use TerminologyMapper")`
6. Add an [ArchUnit](https://www.archunit.org/) rule as a JUnit test to enforce the package boundary. ArchUnit is a Java library that lets you express architectural constraints as ordinary unit tests — for example, "no class outside `gov.cdc.izgw.v2tofhir.terminology` may import `Systems`, `Mapping`, `Units`, `ISO3166`, or `RaceAndEthnicity`." If anyone later reintroduces a direct static import, the test fails at CI time. This locks in the migration boundary automatically.

**Rollback:** Each step is independently revertable. No data migration required (all in-memory).

## Open Questions

- **`FieldHandler` and annotation-driven mapping**: Resolved. `@ComesFrom(table=...)` annotation values are passed through `DatatypeConverter` to `ConceptTranslator.mapCode(String mappingName, Coding source)` on the injected `TerminologyMapper`. The `mappingName` is a first-class context key identifying the kind of mapping being sought — V2 table number (`"0001"`), named concept map (`"USCoreRace"`, `"ImmunizationStatus"`), or any other named mapping registered in the mapper. This design enables a `ContextualTerminologyMapper` to intercept `mapCode("0001", coding)` and return a user- or session-specific mapping before falling through to the default, without any changes to `FieldHandler` or `DatatypeConverter` call sites. `DefaultTerminologyMapper` implements this as `Mapping.getMapping(mappingName).mapCode(source)`. `DatatypeConverter` gains a `TerminologyMapper`-accepting overload; existing static overloads delegate to `TerminologyMapperFactory.get()`.
- **ArchUnit enforcement**: Resolved. An ArchUnit rule will be added in the same PR (migration step 5) to enforce the package boundary immediately.
