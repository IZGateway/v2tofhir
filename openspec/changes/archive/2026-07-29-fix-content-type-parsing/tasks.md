## 1. Fix FhirConverter.read()

- [x] 1.1 In `src/main/java/gov/cdc/izgw/v2tofhir/utils/FhirConverter.java`, replace `new MediaType(contentType)` with `MediaType.parseMediaType(contentType)` so parameters such as `;charset=UTF-8` are accepted.
- [x] 1.2 Change the simplify step to build the media type from `getType()` and `getSubtype()` (not `getType()` twice) so the result is `application/fhir+json`, not `application/application`.
- [x] 1.3 Make the non-blank `Content-Type` path robust: catch `InvalidMediaTypeException` (the `IllegalArgumentException` subclass thrown by `MediaType.parseMediaType(...)` — do not catch more broadly) and fall back to `ContentUtils.guessMediaType(bis)` instead of propagating the exception (Postel's Law), logging at `warn`. `FhirConverter` has no logger today: add Lombok's `@Slf4j` annotation to the class (Lombok is already a project dependency).
- [x] 1.4 Confirm no other change is needed in `ContentUtils.selectParser()` (it already simplifies with `getType(), getSubtype()`).

## 2. Regression Tests

- [x] 2.1 Add `FhirConverterTests` under `src/test/java/...` (suffix `Tests` so Surefire runs it).
- [x] 2.2 Test: `read()` with `Content-Type: application/fhir+json;charset=UTF-8` and a valid FHIR JSON body parses into the target resource without throwing.
- [x] 2.3 Test: `read()` with `Content-Type: application/fhir+xml` selects the XML parser and reads an XML body.
- [x] 2.4 Test: `read()` with a blank/missing `Content-Type` falls back to body sniffing and parses the body.
- [x] 2.5 Test: `read()` with an unparseable `Content-Type` falls back to sniffing rather than throwing.
- [x] 2.6 Test: `read()` with `Content-Type: application/fhir+yaml` selects the YAML parser and reads a YAML body.
- [x] 2.7 Test: `read()` with an unrecognized FHIR media type (e.g. `application/fhir`) defaults to the JSON parser.

## 3. Validation

- [x] 3.1 Run `mvn test -Dtest=FhirConverterTests` and confirm all new tests pass.
- [x] 3.2 Run `mvn clean install` (or `mvn test`) to confirm the full suite still passes with no regressions.
