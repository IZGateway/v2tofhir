## 1. Resource type selection (per ORC/RXA group)

- [ ] 1.1 In `segment/IzDetail.java` `checkForImmunization()`, replace the `CDCPHINVS#Z42` hard-set
      branch (currently sets `hasImmunizationRecommendation = TRUE`) with a call to
      `checkRXA(); return;` plus an explanatory comment.
- [ ] 1.2 Remove `@SuppressWarnings("unused")` from `IzDetail.checkRXA()` (now reachable).
- [ ] 1.3 Confirm no signature/behavior change is needed in `ORCParser` — its `@ComesFrom` setters
      already branch on `hasImmunization()` / `hasRecommendation()`.

## 2. Forecast splitting (one resource per `30956-7`)

- [ ] 2.1 In `IzDetail`, add per-group forecast state — `forecastBlockStarted`, `groupIdentifier`
      (ORC-3), `groupDate` (RXA-22) — and reset all three in `checkForImmunization()` alongside the
      existing resource/boolean reset.
- [ ] 2.2 Add `IzDetail.startForecastBlock()` returning the
      `ImmunizationRecommendationRecommendationComponent` to write into:
      - first block in the group → adopt the `ImmunizationRecommendation` created at ORC time and
        the empty component added by `RXAParser.setup()`;
      - later blocks → `mp.createResource(ImmunizationRecommendation.class)`, set the `Patient`
        reference and add the `MessageHeader.focus` entry (same wiring as
        `initializeResources`), copy `groupDate`, add one `recommendation` component, and make it
        the current resource.
- [ ] 2.3 In `OBXParser.VisCode`, collapse the two entries for LOINC `30956-7`
      (`VIS_VACCINE_TYPE_CODE`, `FORECAST_VACCINE_CODE`) into a single constant, and remove it from
      the VIS education switch in `handleVisObservations`. Add a `ponytail:` comment naming the
      ceiling: if an IIS ever sends `30956-7` inside a genuine VIS block, gate education routing on
      a VIS code (`69764-9`/`29768-9`/`29769-7`) under the same OBX-4 sub-id.
- [ ] 2.4 In `OBXParser.redirectTo()` (field 3), when `izDetail.hasRecommendation()` and the code is
      `30956-7`, call `izDetail.startForecastBlock()` and keep the returned component as the
      current `recommendation`. Leave `setup()`'s existing `getRecommendation()` assignment as the
      fallback for forecast OBX arriving before any `30956-7`.
- [ ] 2.5 In `OBXParser.handleRecommendationObservations()`, make the `30956-7` case set
      `recommendation.vaccineCode` from the converted `CodeableConcept` (this is the previously
      unreachable `FORECAST_VACCINE_CODE` body).
- [ ] 2.6 In `RXAParser`, drop `addVaccineCode(RXA-5)` on the forecast path (the `998` placeholder),
      and route `RXA-22` into `IzDetail.groupDate` so it applies to every resource in the group
      rather than only the first.
- [ ] 2.7 Verify field-handler ordering holds — `FieldHandler.compareComesFrom` sorts priority
      descending then field ascending, and `redirectTo` (field 3) / `setValue` (field 5) both use
      the default priority, so OBX-3 is handled before OBX-5. Add a comment at the
      `startForecastBlock()` call site recording that this ordering is load-bearing.

## 3. Per-block identifier (downstream id collision)

- [ ] 3.1 In `ORCParser.addOrderIdentifier`, record the ORC-3 identifier on `IzDetail` for the
      forecast path (the history path already copies it to `Immunization`).
- [ ] 3.2 When a block's `vaccineCode` is set, give that `ImmunizationRecommendation` an
      `identifier` using the group identifier's system and value `<ORC-3 value>-<CVX code>`
      (e.g. `9999-43`). Unique within the bundle, stable across repeat queries for the same vaccine
      group.

## 4. Committed test data and assertions

No IIS-sourced message is added to this repository. Assertions use the existing `@`-prefixed
FHIRPath mechanism in `src/test/resources/messages.txt`, evaluated by the `testTheData` harness
(`MessageParserTests.java:90`) via `TestData.evaluateAllAgainst`. The file currently has zero `@`
lines, so this is the mechanism's first use — sanity-check that a deliberately wrong assertion
fails the build before relying on it.

- [ ] 4.1 Add `@` assertions under the CDC IG mixed Z42 message (`messages.txt:997`):
      3 `Immunization`, 1 `ImmunizationRecommendation`; the recommendation's `vaccineCode` is `31`
      (**not** `998`); at least one `Immunization` has `vaccineCode` populated and `status =
      completed` from RXA-20 `CP` — assert against the `31` or `48` dose, **not** `110`, whose
      RXA-20 is corrupt in the fixture (`CP< CR>`) and yields a null status.
- [ ] 4.2 Add `@` assertions under the CDC IG shape-A message (`messages.txt:1039`, comment
      "Sample message for multiple recommendations"): 3 `ImmunizationRecommendation` resources with
      `vaccineCode` `03`, `10`, `107` respectively, and each with only its own `dateCriterion`
      (this fixture currently produces 1 merged recommendation).
- [ ] 4.3 Add one hand-written shape-B message to `messages.txt` with invented identifiers: one
      history ORC/RXA carrying genuine VIS OBX (`29769-7`, `29768-9`) plus three forecast ORC/RXAs
      with RXA-5 == 998, each with a single `30956-7` and its own dates/series status. Assert
      1 `Immunization` with one `education` element, and 3 `ImmunizationRecommendation` with
      distinct `vaccineCode`s and distinct `identifier`s.
- [ ] 4.4 Add an assertion that an evaluated-history `Immunization` whose group carries `30956-7`
      has **no** `education` element (guards the regression this change would otherwise introduce).
- [ ] 4.5 Add an assertion that `64994-7` populates `Immunization.programEligibility` (silently
      dropped before this change).
- [ ] 4.6 Add an assertion that a Z32 or VXU fixture still yields only `Immunization` and that VIS
      `education` still populates there (no regression).
- [ ] 4.7 Add a direct unit test for `IzDetail.checkRXA()` / `ParserUtils.getFollowingSegment()`
      over the mixed Z42 message, asserting each of the four ORCs resolves to its own RXA
      (`31`, `48`, `110`, `998`). Both were unreachable/unexercised before this change, and the
      resolution depends on HAPI's nonstandard-segment naming (`ORC`, `ORC2`, …) because HAPI parses
      these as the stock tabular `v251.RSP_K11`.

## 5. Local-only verification against real IIS responses

- [ ] 5.1 Add `Z42ForecastTests` (or equivalent `*Tests` class — Surefire matches `**/*Tests.java`,
      so `*Test` will silently not run) with an opt-in test that reads `*.txt` HL7 files from the
      directory named by the `v2tofhir.localMessages` system property and
      `Assumptions.assumeTrue`-skips when it is unset. Nothing is committed; CI skips it.
- [ ] 5.2 The loader must split each file at the **second** `MSH` (these captures prepend the QBP
      request to the RSP response) and tolerate `\r`-only line endings.
- [ ] 5.3 The test asserts, for every file found: no `ImmunizationRecommendation` has
      `vaccineCode` `998`; every `ImmunizationRecommendation` has a distinct `identifier`; and the
      `Immunization` + `ImmunizationRecommendation` counts match the number of non-998 and `30956-7`
      occurrences in the source message respectively.
- [ ] 5.4 Run it locally against `izgw-transform/ehex-testing` and confirm the measured targets:
      Nevada → 2 `Immunization` + 16 `ImmunizationRecommendation`; Alaska → 3 `Immunization` +
      10 `ImmunizationRecommendation`.
- [ ] 5.5 Document the invocation in the test class Javadoc:
      `mvn test -Dtest=Z42ForecastTests -Dv2tofhir.localMessages=/path/to/ehex-testing`

## 6. Verification

- [ ] 6.1 `mvn test -Dtest=MessageParserTests` green.
- [ ] 6.2 `mvn clean install` green (full suite + build).
- [ ] 6.3 `openspec validate --changes fix-z42-history-forecast-split --strict` passes.

## 7. Downstream (`izgw-transform`) — separate repo, separate commit

- [ ] 7.1 Bump the `v2tofhir` version in `izgw-transform/pom.xml` (pinned directly there at
      `2.4.0`, **not** in `izgw-bom`) once this change is released.
- [ ] 7.2 Add `ehex-testing/` to `izgw-transform/.gitignore` — it currently holds real IIS
      test-system responses, untracked but not ignored.
- [ ] 7.3 Confirm no `izgw-transform` code change is needed: `preFilter` /
      `cleanupBundleOfUnmarkedResources` already filter the searchset by requested resource type,
      and `FhirControllerTests` has no Z42 fixture containing administered doses, so no test
      breaks. Record the accepted consequence — `GET /ImmunizationRecommendation` no longer returns
      evaluated history, which the two-query (Z34/Z44) design serves via `GET /Immunization`.
