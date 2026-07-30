## Why

`FhirConverter.read()` cannot parse a `Content-Type` header that carries parameters
(e.g. `application/fhir+json;charset=UTF-8`), so every body-bearing FHIR POST from a
standards-compliant client fails with `400` and an `IllegalArgumentException`
("Invalid token character '/' in token …"). This blocks `POST /{destinationId}/Patient/$match`
and is a prerequisite for the `$match`-based patient-matching flow (IGDD-3164).

## What Changes

- Fix `FhirConverter.read()` to parse the incoming `Content-Type` with a parser that
  tolerates parameters (`MediaType.parseMediaType(...)`) instead of the single-argument
  `MediaType(String)` constructor that rejects the `/` in `type/subtype`.
- Fix the "simplify" step so it preserves the subtype: build the simplified media type
  from `getType()` **and** `getSubtype()` (currently uses `getType()` twice, yielding
  `application/application`).
- Add a regression test covering `Content-Type` values with a `charset` parameter so the
  correct FHIR parser is selected and the body is read.
- No public API signatures change; behavior is corrected for previously-failing inputs.

## Capabilities

### New Capabilities
- `fhir-content-negotiation`: Reading FHIR resource bodies over HTTP for non-HAPI-native
  Spring Boot applications — selecting the correct FHIR parser from the request
  `Content-Type` (including parameters such as `charset`), with body-sniffing fallback
  when the header is absent or unparseable. (The write/response side of the converter is
  unchanged by this fix and can be specified when it is next touched.)

### Modified Capabilities
<!-- None: no existing spec in openspec/specs/ describes this behavior yet. -->

## Impact

- **Code:** `src/main/java/gov/cdc/izgw/v2tofhir/utils/FhirConverter.java` (`read()` method only).
  `ContentUtils.selectParser()` already simplifies correctly with `getType(), getSubtype()`,
  so no change is needed there.
- **Tests:** New regression test for `FhirConverter` (no existing coverage today for
  `FhirConverter`/`ContentUtils`).
- **APIs/Consumers:** Unblocks `POST /{destinationId}/Patient/$match` and any other
  body-bearing FHIR POST in the IZ Gateway Transformation Service (izgw-transform) for
  clients that send a `charset` parameter (effectively all of them, e.g. DIBBs Query
  Connector v1.2.0).
- **Dependencies:** None added; uses existing Spring `org.springframework.http.MediaType`.
