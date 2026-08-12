## 1. Resource type selection (per ORC/RXA group)

- [ ] 1.1 In `segment/IzDetail.java` `checkForImmunization()`, replace the `CDCPHINVS#Z42` hard-set
      branch (currently sets `hasImmunizationRecommendation = TRUE`) with a call to
      `checkRXA(); return;` plus an explanatory comment.
- [ ] 1.2 Remove `@SuppressWarnings("unused")` from `IzDetail.checkRXA()` (now reachable).
- [ ] 1.3 Confirm no signature/behavior change is needed in `ORCParser` — its `@ComesFrom` setters
      already branch on `hasImmunization()` / `hasRecommendation()`.

## 2. Forecast splitting (one `recommendation` component per `30956-7`, single resource)

- [ ] 2.1 In `IzDetail`, share the `ImmunizationRecommendation` across forecast groups:
      `initializeResources` reuses the message's existing resource instead of creating one per
      ORC (shape B has up to 10 forecast groups). Default the resource's required `date` from
      the MSH-7 message timestamp at creation (RXA-22 overwrites it when present — Nevada
      truncates forecast RXAs at RXA-20). Add per-group state `forecastBlockStarted`, reset in
      `checkForImmunization()` alongside the existing boolean reset.
- [ ] 2.2 Add `IzDetail.startForecastBlock()` returning the
      `ImmunizationRecommendationRecommendationComponent` to write into:
      - first block in the group → adopt the empty component added by `RXAParser.setup()`;
      - later blocks → `immunizationRecommendation.addRecommendation()`, and make it the
        component `getRecommendation()` returns.
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
- [ ] 2.6 In `RXAParser`, on the forecast path: drop `addVaccineCode(RXA-5)` (the `998`
      placeholder) and drop the RXA-3 write
      (`recommendation.getDateCriterionFirstRep().setValueElement(...)`, `RXAParser.java:92`) —
      it auto-creates a `dateCriterion` with no `code`, violating R4 `dateCriterion.code` 1..1,
      and forecast RXA-3 is only the forecast-generation timestamp. Keep
      `RXA-22 → ImmunizationRecommendation.date` (overwrites the MSH-7 default from 2.1).
- [ ] 2.7 Verify field-handler ordering holds — `FieldHandler.compareComesFrom` sorts priority
      descending then field ascending, and `redirectTo` (field 3) / `setValue` (field 5) both use
      the default priority, so OBX-3 is handled before OBX-5. Add a comment at the
      `startForecastBlock()` call site recording that this ordering is load-bearing.

## 3. Resource identifier (downstream deterministic id)

- [ ] 3.1 In `ORCParser.addOrderIdentifier`, copy the ORC-3 identifier onto the
      `ImmunizationRecommendation` on the forecast path (the history path already copies it to
      `Immunization`), so downstream `adjustIdentifiers` hashes a non-null key. Set it once —
      shape B repeats the same sentinel (`9999^AKA`) on every forecast ORC; don't duplicate.
      Confirm the emitted `Identifier.system` is URI-shaped (assigning authorities like `AKA`
      must not land raw in `system`).

## 4. Committed test data and assertions

No IIS-sourced message is added to this repository. Assertions use the existing `@`-prefixed
FHIRPath mechanism in `src/test/resources/messages.txt`, evaluated by the `testTheData` harness
(`MessageParserTests.java:90`) via `TestData.evaluateAllAgainst`. The file currently has zero `@`
lines, so this is the mechanism's first use — sanity-check that a deliberately wrong assertion
fails the build before relying on it.

- [ ] 4.1 Add `@` assertions under the CDC IG mixed Z42 message (`messages.txt:997`):
      3 `Immunization`, 1 `ImmunizationRecommendation` with a single `recommendation`
      component whose `vaccineCode` is `31`
      (**not** `998`); at least one `Immunization` has `vaccineCode` populated and `status =
      completed` from RXA-20 `CP` — assert against the `31` or `48` dose, **not** `110`, whose
      RXA-20 is corrupt in the fixture (`CP< CR>`) and yields a null status.
- [ ] 4.2 Add `@` assertions under the CDC IG shape-A message (`messages.txt:1039`, comment
      "Sample message for multiple recommendations"): 1 `ImmunizationRecommendation` resource
      with `recommendation.count() = 3`, component `vaccineCode`s `03`, `10`, `107`
      respectively, each component with only its own `dateCriterion`, no `dateCriterion`
      lacking a `code`, and the resource `date` populated (this fixture currently produces
      1 merged recommendation component).
- [ ] 4.3 Add one hand-written shape-B message to `messages.txt` with invented identifiers: one
      history ORC/RXA carrying genuine VIS OBX (`29769-7`, `29768-9`) plus three forecast ORC/RXAs
      with RXA-5 == 998, each with a single `30956-7` and its own dates/series status. Assert
      1 `Immunization` with one `education` element, and 1 `ImmunizationRecommendation` with
      3 `recommendation` components carrying distinct `vaccineCode`s, each with its own
      `forecastStatus`; the resource carries the ORC-3 identifier and a `date`.
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
- [ ] 5.3 The test asserts, for every file found: at most one `ImmunizationRecommendation` per
      message; no `recommendation` component has `vaccineCode` `998`; every component has a
      `forecastStatus`; no `dateCriterion` lacks a `code`; the resource has a `date` and an
      `identifier`; the `Immunization` count matches the number of non-998 RXAs and the
      `recommendation` component count matches the number of `30956-7` occurrences in forecast
      groups.
- [ ] 5.4 Run it locally against `izgw-transform/ehex-testing` and confirm the measured targets:
      Nevada → 2 `Immunization` + 1 `ImmunizationRecommendation` with 16 components (`date`
      from MSH-7 — Nevada forecast RXAs truncate at RXA-20); Alaska → 3 `Immunization` +
      1 `ImmunizationRecommendation` with 10 components (`date` from RXA-22).
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
