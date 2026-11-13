# v2tofhir — Project Instructions

HL7 V2 to FHIR R4 converter library. Spring Boot 3.5, Java 21, Maven.
Published to GitHub Packages (`IZGateway/v2tofhir`).

**Skills:** `java-maven-style`

**Public repo** — follow IZ Gateway Public Repo Policy (in global CLAUDE.md).

---

## Build & Test

```cmd
mvn clean install
mvn clean package
mvn clean install -DskipTests
mvn test
mvn test -Dtest=MessageParserTests
mvn test -Dtest=MessageParserTests#testTheData
mvn dependency-check:check
mvn clean site
```

Test class naming: suffix with `Tests`. Surefire matches `**/*Tests.java`.

---

## Architecture: V2 Message Flow

```
HL7 V2 String
  → MessageParser.convert()
  → MSH parsed → Context initialized (event code, profile IDs)
  → DocumentReference created with raw message as attachment
  → Segments iterated:
      → SegmentParser loaded by reflection
      → parser.setup() → createResource()
      → FieldHandlers (@ComesFrom) extract V2 fields → DatatypeConverter → FHIR types
      → Multi-segment resources accumulate (e.g., Patient from PID+PD1+PV1;
        Immunization from ORC+RXA+RXR+OBX — use getLastResource())
  → Bundle (BundleType.MESSAGE)
```

### Key Classes

| Class | Role |
|---|---|
| `MessageParser` | Entry point; orchestrates parse loop |
| `BaseParser` | Abstract base; resource lifecycle, ULID assignment, parser discovery |
| `AbstractSegmentParser` | Base for segment parsers; initializes FieldHandlers from annotations |
| `DatatypeConverter` | 100+ static conversion methods |
| `FieldHandler` | Bridges `@ComesFrom` annotations to field extraction |
| `Systems` | 150+ OID/URI constants (SNOMED, LOINC, CVX, etc.) |
| `Mapping` | Loads 100+ CSV concept maps from `src/main/resources/coding/` |

---

## Annotation-Driven Development

### `@ComesFrom`

Documents and drives V2→FHIR field mappings. Key attributes:
- `path` — FHIR path for target value
- `field` — V2 field index (1-based)
- `component` — V2 component index
- `source` — Terser path alternative
- `map` — Concept map CSV filename (without extension)
- `priority` — Processing order (higher = earlier)

```java
@Produces(segment = "PID", resource = Patient.class, extra = { RelatedPerson.class })
public class PIDParser extends AbstractSegmentParser {
    @ComesFrom(path = "Patient.identifier", field = 2)
    @ComesFrom(path = "Patient.identifier", field = 3)
    public void addIdentifier(Identifier ident) { patient.addIdentifier(ident); }

    private static final List<FieldHandler> fieldHandlers = new ArrayList<>();
    static { AbstractSegmentParser.initFieldHandlers(PIDParser.class, fieldHandlers); }
}
```

**FieldHandlers must be initialized in a static block** — they are shared across instances.

### `@Produces`

Class-level. Declares `segment`, primary `resource` class, and optional `extra` resources the parser may create.

---

## Conversion Principles (Postel's Law)

- Never throw exceptions during conversion
- Return null for invalid or empty data
- Log warnings for issues, continue processing
- Use `createResource(Class<R>)` factory — assigns identifiers, adds to bundle
- Use `getLastResource(Class<R>)` for multi-segment resources

---

## Naming Conventions

- Parsers: `*Parser.java`; converters: `*Converter.java`; utils: `*Utils.java`
- Test classes: `*Tests.java`
- Constants: `ALL_CAPS` in dedicated constants classes
- Packages: `gov.cdc.izgw.v2tofhir.*`

---

## Code Style Specifics

- No wildcard imports; order: `java.*`, `javax.*`, third-party, project
- 4-space indent; soft 120-char line limit
- Methods under ~50 lines preferred
- Return `Optional` when absence is expected; never in fields/parameters
- Don't share parser instances across threads (parsers are not thread-safe)
- Never log raw PHI/PII; redact at WARN/ERROR

---

## Writing a New DatatypeParser

Implement `DatatypeParser<T>` where T extends `org.hl7.fhir.r4.model.Type`.

Always call `DatatypeConverter.adjustIfVaries(v2Type)` first. Delegate Primitive → `fromString()`, extract Composite components directly. Register in `DatatypeConverter` with: (1) static instance field, (2) delegation method, (3) case in generic `convert()` dispatcher.

---

## Package Structure

```
src/main/java/
  gov/cdc/izgw/v2tofhir/
    annotation/   @ComesFrom, @Produces
    converter/    MessageParser, DatatypeConverter
    datatype/     FHIR datatype parsers
    segment/      V2 segment parsers (PIDParser, MSHParser, etc.)
    utils/        ParserUtils, TextUtils, Systems
  com/ainq/fhir/utils/   PathUtils, YamlParser (Audacious Inquiry)
```

### `com.ainq.fhir.utils`

- **`PathUtils`** — simplified FHIRPath over HAPI FHIR; use `GET_FIRST`/`CREATE_IF_MISSING` Actions
- **`YamlParser`** — implements HAPI `IParser`; use `YamlUtils.newYamlParser(fhirContext)` for YAML output

---

## Extensibility

Custom parser packages via `V2TOFHIR_PARSER_PACKAGES` env var (comma/semicolon/space-separated).
Custom parsers are loaded before built-ins.

---

## Key Libraries

- HAPI V2 (v2.1–v2.8.1), HAPI FHIR R4
- Apache Commons Lang3, Text, IO
- Lombok (`@Slf4j`, `@Getter`, `@Data`)
- ULID-J for identifier generation (prefer ULIDs over random UUIDs)
- izgw-bom for consistent dependency versions
