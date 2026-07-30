## Context

`FhirConverter` is a Spring `HttpMessageConverter<Resource>` used by non-HAPI-native
Spring Boot apps (the IZ Gateway Transformation Service, izgw-transform) to read and write
FHIR resource bodies. Its `read()` method turns the request `Content-Type` header into a
Spring `MediaType`, then asks `ContentUtils.selectParser(...)` for the matching HAPI FHIR
parser.

Today `read()` builds the media type incorrectly:

```java
mediaType = new MediaType(contentType);                                // (1)
// Simplify it.
mediaType = new MediaType(mediaType.getType(), mediaType.getType());   // (2)
```

- **(1)** `MediaType(String)` treats the whole argument as a single *type token*. A real
  header like `application/fhir+json;charset=UTF-8` contains `/`, which is not a legal
  token character, so the constructor throws `IllegalArgumentException`
  ("Invalid token character '/' in token …"). The `read()` method has no catch, so the
  request fails with `400` and an `OperationOutcome`.
- **(2)** Even if (1) were reached with a bare type, the "simplify" step passes
  `getType(), getType()`, producing `application/application` and losing the subtype.

`ContentUtils.selectParser(...)` already performs the correct simplify
(`new MediaType(getType(), getSubtype())`) internally, so the defect is confined to
`FhirConverter.read()`. There is currently no automated test exercising `FhirConverter`.

## Goals / Non-Goals

**Goals:**
- Read FHIR request bodies whose `Content-Type` includes parameters (notably `charset`)
  without error, selecting the correct FHIR parser.
- Preserve the subtype when simplifying the media type.
- Add regression coverage so the defect cannot silently return.

**Non-Goals:**
- No changes to `write()` / content-negotiation output, `ContentUtils.selectParser()`,
  `guessMediaType()`, or the supported media-type lists.
- No new public API, method signatures, or dependencies.
- No behavior change for requests that were already succeeding (blank `Content-Type`
  still falls back to body sniffing; bare `type/subtype` values still work).

## Decisions

### Decision 1: Parse with `MediaType.parseMediaType(...)` instead of `new MediaType(String)`
Spring's static `MediaType.parseMediaType(String)` is purpose-built to parse a full
`type/subtype;param=value` header, tolerating parameters. Replace the single-arg
constructor call with it.

- **Chosen:**
  ```java
  MediaType parsed = MediaType.parseMediaType(contentType);
  mediaType = new MediaType(parsed.getType(), parsed.getSubtype());
  ```
- **Alternatives considered:**
  - *Reuse the existing private `ContentUtils.parseMediaType(String)`* — it splits on `/`
    but does not strip parameters (`fhir+json;charset=UTF-8` would become the subtype), so
    it is unsuitable for header values and only safe for the parameter-free constants it is
    used with. Rejected.
  - *Strip parameters manually (`substringBefore(contentType, ";")`)* — reinvents parsing
    Spring already does correctly (quoted params, whitespace, casing). Rejected.

### Decision 2: Fix the simplify step to use `getSubtype()`
Build the simplified media type from `parsed.getType()` and `parsed.getSubtype()` so the
result is `application/fhir+json`, not `application/application`. This mirrors the existing
simplification already performed inside `ContentUtils.selectParser(...)`.

### Decision 3: Robustness — keep read tolerant
Consistent with the library's Postel's-Law principle, a malformed/unparseable
`Content-Type` should not hard-fail body reading. Catch `InvalidMediaTypeException` — the
`IllegalArgumentException` subclass that `MediaType.parseMediaType(...)` throws — and treat
the value the same as a missing `Content-Type` by falling back to
`ContentUtils.guessMediaType(bis)` (body sniffing), rather than propagating the exception.
Catch only `InvalidMediaTypeException`, not `IllegalArgumentException` or broader, so
unrelated failures still surface. Log the fallback at `warn`; `FhirConverter` has no logger
today, so add Lombok's `@Slf4j` to the class (Lombok is already a project dependency).

- **Alternative considered:** let a bad header throw. Rejected — inconsistent with the
  rest of the converter and with the blank-header path, and it is exactly the failure
  mode this change removes.

### Decision 4: Regression test at the converter level
Add `FhirConverterTests` (Surefire matches `*Tests.java`) that drives `read()` with a
`MockHttpInputMessage`-style input carrying `Content-Type: application/fhir+json;charset=UTF-8`
and asserts a resource is parsed. Include XML, YAML, blank/missing-header,
unparseable-header, and unrecognized-FHIR-media-type cases so every spec scenario
(parser selection, default-to-JSON, and the sniffing fallback) is locked in by a test.

## Risks / Trade-offs

- **[Silent fallback masks a genuinely bad header]** → Only unparseable headers fall back
  to sniffing; well-formed headers still drive parser selection. The sniffing path already
  exists and is exercised for blank headers, so behavior is predictable. Acceptable.
- **[Case/whitespace variants of media types]** → `parseMediaType` normalizes structure;
  `canRead`/`selectParser` already lower-case type/subtype, so no new casing handling is
  needed.
- **[Charset parameter is stripped, not honored]** → Simplifying to `type/subtype` discards
  `charset`, so the body is always read with HAPI's default (UTF-8) decoding; a hypothetical
  non-UTF-8 body would be misread. Accepted deliberately: FHIR mandates UTF-8 for its
  media types, so honoring other charsets is out of scope.
- **[Very small blast radius]** → Change is one method; low risk of regressing other
  converter behavior. The new tests are the primary guard.

## Migration Plan

- Pure bug fix; no data, schema, or API migration.
- Deploy with the library release; consumers (izgw-transform) pick it up on dependency
  bump. Rollback is reverting the single-method change.

## Open Questions

- None. The fix, scope, and test approach are confirmed against IGDD-3164 and the current
  source.
