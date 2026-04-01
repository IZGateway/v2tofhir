# Project Guidelines for V2 to FHIR Converter

## Tech Stack

**Framework:** Spring Boot (version 3.5.0)  
**Language:** Java 17  
**Build Tool:** Maven  
**Core Libraries:**
- **HL7 V2 Processing:** HAPI V2 (structures v2.1 through v2.8.1)
- **FHIR Processing:** HAPI FHIR R4 (base, structures, validation)
- **Utility Libraries:** Apache Commons (Lang3, Text, IO, Compress, Validator, BeanUtils)
- **Logging:** Logback with SLF4J
- **JSON/YAML:** Jackson (Core, Databind, YAML)
- **CSV Processing:** OpenCSV
- **Code Generation:** Lombok
- **Testing:** Spring Boot Test, JUnit

**Additional Libraries:**
- **Identifier Generation:** ULID-J
- **Caching:** HAPI FHIR Caffeine Caching

## Naming Conventions

**Classes:** Use PascalCase (e.g., `MessageParser`, `DatatypeConverter`, `PIDParser`)  
**Variables & Methods:** Use camelCase (e.g., `convertMessage`, `createResource`, `getFieldHandlers`)  
**Constants:** Use ALL_CAPS with underscores (e.g., `MOTHERS_MAIDEN_NAME`, `PATIENT_BIRTH_TIME`)  
**Packages:** Use lowercase with dots (e.g., `gov.cdc.izgw.v2tofhir.segment`)  
**Test Classes:** Suffix with `Tests` (e.g., `MessageParserTests`, `DatatypeConverterTests`)

## Code Style
 
 - Logging: Use SLF4J via Lombok `@Slf4j`; never use `System.out`/`System.err`.
 - Imports: No wildcard imports; order groups as `java.*`, `javax.*`, third-party, then project packages separated by blank lines.
 - Formatting: 4-space indentation; soft line length limit 120 chars; wrap fluent chains sensibly.
 - Method Size: Prefer methods under ~50 lines; extract helpers to preserve Single Responsibility.
 - Optional Usage: Return `Optional` when absence is expected; avoid `Optional` in fields/parameters and never serialize it.
 - Static Mutable State: Minimize; prefer immutable `Collections.unmodifiable*`; if caching is required ensure thread safety and document rationale.
 - Error Handling: Do not swallow exceptions silently; log at `warn` for recoverable data issues, `debug` for trace details.
 - Null Handling: Validate required parameters early (`Objects.requireNonNull`); return early on null/empty inputs per robustness principle.
 - Generics: Use conventional single-letter type parameters (`T`, `R`, `E`); avoid overly specific names unless clarity demands.
 - Concurrency: Parsers are not thread-safe; do not share instances. Any new shared utility with mutable state must be thread-safe.
 
## Class Design Patterns

**Parsers:** Classes that convert V2 structures to FHIR resources
- Segment parsers extend `AbstractSegmentParser`
- Structure parsers extend `AbstractStructureParser`
- Datatype parsers implement `DatatypeParser<T>` interface

**Converters:** Static utility classes for conversion operations (e.g., `DatatypeConverter`)  
**Utils:** Static utility classes for helper methods (e.g., `ParserUtils`, `TextUtils`, `FhirUtils`)  
**Annotations:** Custom annotations for metadata (e.g., `@ComesFrom`, `@Produces`)

## Component Guidelines

- Use Lombok annotations (`@Slf4j`, `@Getter`) to reduce boilerplate
- Implement parsers as non-static, thread-unsafe classes (cheap to instantiate)
- Follow the Single Responsibility Principle - each parser handles one segment type
- Use static initialization blocks for class-level setup
- Provide comprehensive JavaDoc comments for public APIs
- Thread Safety: Instantiate a new parser per message or segment batch; do not reuse parser instances across threads; utilities must remain stateless or document thread-safe caching.

## Annotation-Driven Development

**@ComesFrom:** Documents V2-to-FHIR field mappings
- Specifies source field/component indices
- Documents FHIR paths and V2 terser paths
- Supports priority-based processing order

**@Produces:** Declares resources created by parsers
- Specifies primary resource type
- Lists additional resource types in `extra` array

**Field Handlers:** Annotation-driven parsing using `FieldHandler` class
- Initialize in static blocks
- Process fields based on `@ComesFrom` annotations

## Conversion Principles

**Robustness (Postel's Law):** Be forgiving in what you accept
- Never throw exceptions during conversion
- Return null for invalid or empty data
- Convert as much as possible rather than failing completely
- Log warnings for issues but continue processing

**Resource Creation:**
- Use `createResource(Class<R> clazz)` factory method
- Automatically assigns identifiers and adds to bundle
- Creates Provenance resources when enabled
- Alternative: create resources manually and call `addResource(r)`

**Resource Access:**
- `getResource(String id)` - Get by identifier
- `getResource(Class<R> clazz, String id)` - Get by type and identifier
- `getFirstResource(Class<R> clazz)` - Get first created of type
- `getLastResource(Class<R> clazz)` - Get most recent of type
- `findResource(Class<R> clazz, String id)` - Get or create if missing

## Package Structure

```
src/
├── main/
│   └── java/
│       ├── gov/cdc/izgw/v2tofhir/
│       │   ├── annotation/      # Custom annotations (@ComesFrom, @Produces)
│       │   ├── converter/       # Core conversion logic (MessageParser, DatatypeConverter)
│       │   ├── datatype/        # FHIR datatype parsers (AddressParser, HumanNameParser)
│       │   ├── segment/         # V2 segment parsers (PIDParser, MSHParser, etc.)
│       │   └── utils/           # Utility classes (ParserUtils, TextUtils, Systems)
│       ├── com/ainq/fhir/utils/ # FHIR utility classes (PathUtils, YamlParser)
│       └── SimpleV2ToFhirConverter.java  # Simple example converter
└── test/
    └── java/
        └── test/gov/cdc/izgateway/
            ├── v2tofhir/        # Unit tests for parsers and converters
            └── TestUtils.java   # Test utilities
```

## Error Handling

- Use `ErrorReporter` for consistent error reporting
- Log errors using SLF4J (`@Slf4j` annotation)
- Never fail conversions on individual field errors
- Return partial results when possible
- Use `try-catch` blocks to handle `HL7Exception` gracefully

## Extensibility

**Custom Parsers:** Support for custom parser packages
- Configure via `V2TOFHIR_PARSER_PACKAGES` environment variable
- Custom parsers loaded before built-in parsers
- Allows overriding default behavior

**Package Sources:** Dynamic parser discovery
- Uses `BaseParser.packageSources` for parser lookup
- Supports multiple parser packages
- Comma, semicolon, or space-separated list

## Configuration

- Environment Variables: `V2TOFHIR_PARSER_PACKAGES` for custom parser package discovery (multiple packages separated by comma/semicolon/space);
- Centralized Constants: Define env/config keys in a dedicated final utility (e.g., `ConfigKeys`) to avoid typos.
- Defaults: Parser package list falls back to built-ins when variable unset/blank.
- Future: Additional feature toggles (e.g., provenance enablement) should use consistent `V2TOFHIR_*` prefix.
- Validation: On startup, log effective configuration at INFO with sensitive values (keys/secrets) redacted.

## Code Quality

**Documentation:**
- Provide JavaDoc for all public classes and methods
- Include `@see` links to relevant HL7 V2 to FHIR mapping specifications
- Document conversion principles and edge cases
- Reference official implementation guides in comments

**Testing:**
- Write unit tests for all parsers and converters
- Test with real-world HL7 V2 messages
- Validate FHIR output against R4 specifications
- Use descriptive test class names with `Tests` suffix
- Isolate: Avoid external network/file dependencies; use in-memory data and mocks
- Deterministic: No reliance on system clock except through injectable time helpers
- Coverage: Focus on edge cases (empty/null datatypes, malformed segments) and mapping correctness
- Performance: Keep unit tests fast (<250ms each); heavier integration tests may be separately profiled

**Dependencies:**
- Use Maven for dependency management
- Follow spring-boot-starter-parent versions
- Import izgw-bom for consistent version management
- Keep dependencies up-to-date for security

**Deprecation Policy:** Mark obsolete APIs with `@Deprecated` and JavaDoc `@deprecated` tag including replacement; retain for at least 2 minor versions before removal; do not introduce new usages of deprecated APIs; note planned removal version in JavaDoc.

## Resource Mapping Patterns

**Multi-Segment Resources:** Some FHIR resources combine data from multiple V2 segments
- Patient resource: PID + PD1 + PV1 segments
- Immunization resource: ORC + RXA + RXR + OBX segments
- Use `getLastResource()` to access resources being built

**Reference Management:**
- Use `ParserUtils.toReference()` to create references
- Store references in resource user data
- Support bidirectional references
- Maintain search names for reference lookup
- Use consistent userData keys (define constants) when attaching references or metadata to resources to avoid collisions

**Identifier Assignment:**
- Use ULID for generated identifiers (prefer ULIDs over random UUIDs for lexicographic sortability and reduced collision domain)
- Support both V2 identifiers and generated ones
- Encode identifiers using `FhirIdCodec`

## Contribution Workflow
- Fork or create a feature branch from main (naming: feature/<short-description> or fix/<issue-id>).
- Include mapping documentation updates for any new segment/datatype/resource logic.
- Add/adjust tests covering new behavior and edge cases; ensure existing tests pass.
- Run OWASP and Jacoco locally before PR; address high CVSS findings.
- Submit PR with summary, rationale, and relevant implementation guide references.
- Require at least one maintainer review; no self-merge.
- Squash commits unless preserving logical history adds clarity.

## Maven Build Configuration

**Compiler:** Java 17 with Lombok annotation processing  
**Testing:** Surefire plugin with JaCoCo code coverage  
**Security:** OWASP dependency check (fails on CVSS >= 7)  
**Reporting:** JavaDoc, project info, test reports, dependency check  
**Distribution:** GitHub Packages (`IZGateway/v2tofhir`)

## Best Practices

**Immutability:** Prefer immutable collections where possible (e.g., `Collections.unmodifiableSet()`)

**Null Safety:** Always check for null or empty values before processing

**Performance:** 
- Parser instances are cheap - create new ones per conversion
- Use static caches for lookup tables (codes, systems, mappings)
- Lazy-initialize field handlers
- Performance Considerations: Avoid reflection in hot paths; cache Method/Field lookups if needed; precompute code system/Coding lookups; reuse a single parser instance per conversion only (do not share globally); prefer streaming over building large intermediate collections; measure with JMH before micro-optimizing.

**Standards Compliance:**
- Follow CDC HL7 Version 2 Immunization Implementation Guide
- Conform to HL7 V2 to FHIR Implementation Guide mappings
- Support V2 versions 2.1 through 2.8.1
- Generate valid FHIR R4 resources

**Logging:**
- Use appropriate log levels (debug, info, warn, error)
- Log conversion issues as warnings
- Include contextual information in log messages
- Use `log.debug()` for detailed trace information
- Security: Never log raw PHI/PII; redact names, addresses, identifiers (retain last 4 if needed); avoid dumping full HL7 segments or FHIR resources at WARN/ERROR; use DEBUG with masking helpers.

## Data Type Conversion

**Primitive Types:** Use `DatatypeConverter.to*()` static methods
- `toInstantType()`, `toDateTimeType()`, `toDateType()` for temporal types
- `toCodeableConcept()`, `toCoding()`, `toCodeType()` for coded values
- `toHumanName()`, `toAddress()`, `toContactPoint()` for demographic data

**String Parsing:** Use specialized parsers implementing `DatatypeParser<T>`
- `HumanNameParser.fromString()` for natural language name parsing
- `AddressParser.fromString()` for address parsing
- `ContactPointParser.fromString()` for contact information

**Text Representation:** Use `TextUtils.toString()` for consistent string conversion
- Works with both V2 and FHIR types
- Produces identical output for converted data
- Supports validation and comparison

## License

MIT License - See LICENSE file for details
