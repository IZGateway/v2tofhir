## 1. Fix FhirConverter.read()

- [ ] 1.1 In `src/main/java/gov/cdc/izgw/v2tofhir/utils/FhirConverter.java`, replace `new MediaType(contentType)` with `MediaType.parseMediaType(contentType)` so parameters such as `;charset=UTF-8` are accepted.
- [ ] 1.2 Change the simplify step to build the media type from `getType()` and `getSubtype()` (not `getType()` twice) so the result is `application/fhir+json`, not `application/application`.
- [ ] 1.3 Make the non-blank `Content-Type` path robust: if the header cannot be parsed, fall back to `ContentUtils.guessMediaType(bis)` instead of propagating an exception (Postel's Law), logging at `warn` via `@Slf4j`.
- [ ] 1.4 Confirm no other change is needed in `ContentUtils.selectParser()` (it already simplifies with `getType(), getSubtype()`).

## 2. Regression Tests

- [ ] 2.1 Add `FhirConverterTests` under `src/test/java/...` (suffix `Tests` so Surefire runs it).
- [ ] 2.2 Test: `read()` with `Content-Type: application/fhir+json;charset=UTF-8` and a valid FHIR JSON body parses into the target resource without throwing.
- [ ] 2.3 Test: `read()` with `Content-Type: application/fhir+xml` selects the XML parser and reads an XML body.
- [ ] 2.4 Test: `read()` with a blank/missing `Content-Type` falls back to body sniffing and parses the body.
- [ ] 2.5 Test: `read()` with an unparseable `Content-Type` falls back to sniffing rather than throwing.

## 3. Validation

- [ ] 3.1 Run `mvn test -Dtest=FhirConverterTests` and confirm all new tests pass.
- [ ] 3.2 Run `mvn clean install` (or `mvn test`) to confirm the full suite still passes with no regressions.
