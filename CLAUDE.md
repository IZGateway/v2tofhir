# CLAUDE.md

`v2tofhir` is a Java 21 / Maven library that converts HL7 V2 messages (mainly the CDC V2.5.1 immunization profile, e.g. VXU_V04, QBP/RSP) into FHIR R4 Bundles. It is used by `izgw-transform`. It is built on HAPI V2 (structures v2.1–v2.8.1) and HAPI FHIR R4.

## Rules

- **Definition of done:** run `mvn test` and report the result before you say a task is finished.
- **Dependencies:** ask before adding or changing a dependency. Versions come from the `izgw-bom` parent, so prefer BOM-managed versions over `<version>` tags.
- **`dependency-suppression.xml`:** don't add an entry without a stated justification (CVE, why it doesn't apply, ticket).
- **Test data must be synthetic.** Never put real PHI in `messages.txt`, `segments.txt`, or any other committed file.
- **Git:** work on a feature branch off `develop`, named after the Jira ticket (e.g. `IGDD-2974`). Commit only when asked, with messages like `IGDD-2974: Fix build warnings in v2tofhir`. Never push to `develop`. Never push, tag, or start a release; the user pushes and opens PRs. Releases run only through the **Release - Standard** / **Release - Hotfix** GitHub Actions (see `RELEASING.md`).
- **OpenSpec:** use the `/opsx:*` skills only when asked. Before changing behavior that a capability spec in `openspec/specs/` covers, read that spec and keep the code consistent with it.
- **New classes** get an `@author Audacious Inquiry` javadoc tag.
- **Logging:** use `@Slf4j`, never `System.out`. Never log raw PHI.

## Commands

```bash
mvn clean install                                 # full build + tests (~40s)
mvn test                                          # all tests
mvn test -Dtest=MessageParserTests                # single test class
mvn test -Dtest='FhirConverterTests#readsXml'     # single test method
mvn -o test                                       # offline; works once dependencies are cached
mvn site -DskipTests -DskipDependencyCheck=true   # reports into target/site. Run AFTER a test run, as a separate mvn invocation (~8 min)
mvn org.owasp:dependency-check-maven:check        # OWASP check (fails on CVSS >= 7). Without an NVD API key the first run is very slow
```

- The parent POM `gov.cdc.izgw:izgw-bom` is a SNAPSHOT on GitHub Packages. If Maven can't resolve it, the build can't start: GitHub Packages credentials are missing from `~/.m2/settings.xml` (repository ids `github-bom`, `v2tofhir`). Stop and tell the user; there is no workaround.
- The OWASP plugin isn't bound to any lifecycle phase, so `install` and `verify` don't run it. CI runs it only on releases, through the Dependency-Check GitHub Action.
- Surefire only includes `**/*Tests.java`. A class named `FooTest` will **silently not run**.
- `ERROR ... MSAParser -- MSH segment not parsed` lines in test output are expected. They come from tests that convert an MSA segment without an MSH.
- `Z42ForecastTests` is skipped unless `-Dv2tofhir.localMessages=<dir>` points at a directory of local messages outside the repo.

### Adding tests

Most tests live in `src/test/java/test/gov/cdc/izgateway/v2tofhir/` and extend `TestBase`. Tests that sit in the same package as the main code are under `src/test/java/gov/cdc/izgw/v2tofhir/`.

To add a data-driven case, append a message to `src/test/resources/messages.txt` or a segment to `segments.txt`. No other wiring is needed; `TestBase` loads these files through `utils/TestData`.
- Separate entries with a blank line.
- Lines starting with `//` or `#` are comments.
- Lines starting with `@` are FHIRPath assertions against the converted output, e.g. `@MessageHeader.count() = 1 // comment`.

## Error handling

Conversion follows Postel's law. New code must handle bad or empty input by returning `null` (or skipping the value) and logging a warning. Never throw for bad input.

How failures behave today:
- **An exception thrown while a segment is parsed** is caught in `BaseParser`, which logs a warning and moves on to the next segment. The rest of that segment's fields are lost, so don't use throwing as a way to report bad data.
- **A missing parser, or a parser whose constructor fails,** is logged and the segment is skipped. A bad `@ComesFrom` setter (one that doesn't take a FHIR type) makes `FieldHandler.initFieldHandlers` throw `ServiceConfigurationError` inside the constructor. That surfaces only as an ERROR log (`Unexpected ... while creating <X>Parser`), and the segment produces nothing. After adding or changing a parser, check the test log for this error.
- **Missing coding resources or a malformed mapping CSV** throw `ServiceConfigurationError` when `Mapping` loads, and that fails loudly.

## Architecture

**Parse loop:** `converter/MessageParser.createBundle(...)` (or `convert(String)`) accepts a whole `Message` or any iterable of `Structure`s. MSH initializes the `Context`, and a `DocumentReference` keeps the raw message. For each structure, `BaseParser` loads a class named `<Name>Parser` by reflection. It searches packages from the `V2TOFHIR_PARSER_PACKAGES` env var first (so callers can override or add parsers), then the built-in `segment` package. The parser needs a one-arg constructor that takes the `MessageParser`. The result is a MESSAGE `Bundle`.

**Segment parsers** (`segment/*Parser`, extending `AbstractSegmentParser`). **Read `SegmentParser.md` before you create or change a segment parser.**
- `@Produces` on the class declares the primary resource.
- Public setters annotated with `@ComesFrom` map V2 fields to FHIR paths. Non-public methods are ignored.
- The constructor lazily collects these into a static `fieldHandlers` list (`if (fieldHandlers.isEmpty()) FieldHandler.initFieldHandlers(...)`). Do this in the constructor, not in a `static {}` block.
- `@ComesFrom(map=...)` names a concept-map CSV in `src/main/resources/coding/`.

**Several segments can build one resource**, e.g. Patient ← PID+PD1+PV1 and Immunization ← ORC+RXA+RXR+OBX. A later parser looks up the in-progress resource rather than creating a new one. `IzDetail` holds the shared per-message state for Immunization and ImmunizationRecommendation. Resource ids are ULIDs (`FhirIdCodec`); use `setIdGenerator(...)` to get deterministic ids in tests. A `Provenance` resource is created for every resource by default. Turn this off with `setStoringProvenance(false)`.

**Datatypes:** `converter/DatatypeConverter` has static `toX(Type)` methods plus generic `convert(Class|String, Type, table)` dispatchers. The free-text parsers in `datatype/` implement `DatatypeParser<T>`, and they are wired in by hand. A new one needs three things in `DatatypeConverter`: a static instance, a `toX()` delegate, and a case in the generic `convert()`.

**Terminology:**
- New code gets a `TerminologyMapper` from `TerminologyMapperFactory.get()`.
- Use `utils.Systems` only for system URI constants.
- The other `utils/` classes (`Mapping`, `Units`, `ISO3166`, `RaceAndEthnicity`) are deprecated forwarding stubs. Don't add calls to them. Migrate a call you touch only if the task asks for it.
- `TerminologyPackageBoundaryTests` (ArchUnit) fails the build if code outside `terminology/` and `utils/` uses the `terminology/` implementation classes (`Systems`, `Mapping`, …) directly.

`tracking/*.csv` is the inventory of v2-to-FHIR mappings: which messages, segments, datatypes and code systems are covered.
