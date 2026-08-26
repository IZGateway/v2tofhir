## 1. Resource type selection (per ORC/RXA group)

- [x] 1.1 In `segment/IzDetail.java` `checkForImmunization()`, replace the `CDCPHINVS#Z42` hard-set
      branch (currently sets `hasImmunizationRecommendation = TRUE`) with a call to
      `checkRXA(); return;` plus an explanatory comment.
- [x] 1.2 Remove `@SuppressWarnings("unused")` from `IzDetail.checkRXA()` (now reachable).
- [x] 1.3 Confirm no signature/behavior change is needed in `ORCParser` — its `@ComesFrom` setters
      already branch on `hasImmunization()` / `hasRecommendation()`.

## 2. Forecast splitting (one `recommendation` component per `30956-7`, single resource)

- [x] 2.1 In `IzDetail`, share the `ImmunizationRecommendation` across forecast groups:
      `initializeResources` reuses the message's existing resource instead of creating one per
      ORC (shape B has up to 10 forecast groups). Default the resource's required `date` from
      the MSH-7 message timestamp at creation (RXA-22 overwrites it when present — Nevada
      truncates forecast RXAs at RXA-20). Add per-group state `forecastBlockStarted`, reset in
      `checkForImmunization()` alongside the existing boolean reset.
- [x] 2.2 Add `IzDetail.startForecastBlock()` returning the
      `ImmunizationRecommendationRecommendationComponent` to write into:
      - first block in the group → adopt the empty component added by `RXAParser.setup()`;
      - later blocks → `immunizationRecommendation.addRecommendation()`, and make it the
        component `getRecommendation()` returns.
- [x] 2.3 In `OBXParser.VisCode`, collapse the two entries for LOINC `30956-7`
      (`VIS_VACCINE_TYPE_CODE`, `FORECAST_VACCINE_CODE`) into a single constant, and remove it from
      the VIS education switch in `handleVisObservations`. Add a `ponytail:` comment naming the
      ceiling: if an IIS ever sends `30956-7` inside a genuine VIS block, gate education routing on
      a VIS code (`69764-9`/`29768-9`/`29769-7`) under the same OBX-4 sub-id.
- [x] 2.4 In `OBXParser.redirectTo()` (field 3), when `izDetail.hasRecommendation()` and the code is
      `30956-7`, call `izDetail.startForecastBlock()` and keep the returned component as the
      current `recommendation`. Leave `setup()`'s existing `getRecommendation()` assignment as the
      fallback for forecast OBX arriving before any `30956-7`.
- [x] 2.5 In `OBXParser.handleRecommendationObservations()`, make the `30956-7` case set
      `recommendation.vaccineCode` from the converted `CodeableConcept` (this is the previously
      unreachable `FORECAST_VACCINE_CODE` body).
- [x] 2.6 In `RXAParser`, on the forecast path: drop `addVaccineCode(RXA-5)` (the `998`
      placeholder) and drop the RXA-3 write
      (`recommendation.getDateCriterionFirstRep().setValueElement(...)`, `RXAParser.java:92`) —
      it auto-creates a `dateCriterion` with no `code`, violating R4 `dateCriterion.code` 1..1,
      and forecast RXA-3 is only the forecast-generation timestamp. Keep
      `RXA-22 → ImmunizationRecommendation.date` (overwrites the MSH-7 default from 2.1).
- [x] 2.7 Verify field-handler ordering holds — `FieldHandler.compareComesFrom` sorts priority
      descending then field ascending, and `redirectTo` (field 3) / `setValue` (field 5) both use
      the default priority, so OBX-3 is handled before OBX-5. Add a comment at the
      `startForecastBlock()` call site recording that this ordering is load-bearing.

## 3. Resource identifier (downstream deterministic id)

- [x] 3.1 In `ORCParser.addOrderIdentifier`, copy the ORC-3 identifier onto the
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

- [x] 4.1 Add `@` assertions under the CDC IG mixed Z42 message (`messages.txt:997`):
      3 `Immunization`, 1 `ImmunizationRecommendation` with a single `recommendation`
      component whose `vaccineCode` is `31`
      (**not** `998`); at least one `Immunization` has `vaccineCode` populated and `status =
      completed` from RXA-20 `CP` — assert against the `31` or `48` dose, **not** `110`, whose
      RXA-20 is corrupt in the fixture (`CP< CR>`) and yields a null status.
- [x] 4.2 Add `@` assertions under the CDC IG shape-A message (`messages.txt:1039`, comment
      "Sample message for multiple recommendations"): 1 `ImmunizationRecommendation` resource
      with `recommendation.count() = 3`, component `vaccineCode`s `03`, `10`, `107`
      respectively, each component with only its own `dateCriterion`, no `dateCriterion`
      lacking a `code`, and the resource `date` populated (this fixture currently produces
      1 merged recommendation component).
- [x] 4.3 Add one hand-written shape-B message to `messages.txt` with invented identifiers: one
      history ORC/RXA carrying genuine VIS OBX (`29769-7`, `29768-9`) plus three forecast ORC/RXAs
      with RXA-5 == 998, each with a single `30956-7` and its own dates/series status. Assert
      1 `Immunization` with one `education` element, and 1 `ImmunizationRecommendation` with
      3 `recommendation` components carrying distinct `vaccineCode`s, each with its own
      `forecastStatus`; the resource carries the ORC-3 identifier and a `date`.
- [x] 4.4 Add an assertion that an evaluated-history `Immunization` whose group carries `30956-7`
      has **no** `education` element (guards the regression this change would otherwise introduce).
- [x] 4.5 Add an assertion that `64994-7` populates `Immunization.programEligibility` (silently
      dropped before this change).
- [x] 4.6 Add an assertion that a Z32 or VXU fixture still yields only `Immunization` and that VIS
      `education` still populates there (no regression).
- [x] 4.7 Add a direct unit test for `IzDetail.checkRXA()` / `ParserUtils.getFollowingSegment()`
      over the mixed Z42 message, asserting each of the four ORCs resolves to its own RXA
      (`31`, `48`, `110`, `998`). Both were unreachable/unexercised before this change, and the
      resolution depends on HAPI's nonstandard-segment naming (`ORC`, `ORC2`, …) because HAPI parses
      these as the stock tabular `v251.RSP_K11`.

## 5. Local-only verification against real IIS responses

- [x] 5.1 Add `Z42ForecastTests` (or equivalent `*Tests` class — Surefire matches `**/*Tests.java`,
      so `*Test` will silently not run) with an opt-in test that reads `*.txt` HL7 files from the
      directory named by the `v2tofhir.localMessages` system property and
      `Assumptions.assumeTrue`-skips when it is unset. Nothing is committed; CI skips it.
- [x] 5.2 The loader must split each file at the **second** `MSH` (these captures prepend the QBP
      request to the RSP response) and tolerate `\r`-only line endings.
- [x] 5.3 The test asserts, for every file found: at most one `ImmunizationRecommendation` per
      message; no `recommendation` component has `vaccineCode` `998`; every component has a
      `forecastStatus`; no `dateCriterion` lacks a `code`; the resource has a `date` and an
      `identifier`; the `Immunization` count matches the number of non-998 RXAs and the
      `recommendation` component count matches the number of `30956-7` occurrences in forecast
      groups.
- [x] 5.4 Run it locally against `izgw-transform/ehex-testing` and confirm the measured targets:
      Nevada → 2 `Immunization` + 1 `ImmunizationRecommendation` with 16 components (`date`
      from MSH-7 — Nevada forecast RXAs truncate at RXA-20); Alaska → 3 `Immunization` +
      1 `ImmunizationRecommendation` with 10 components (`date` from RXA-22).
- [x] 5.5 Document the invocation in the test class Javadoc:
      `mvn test -Dtest=Z42ForecastTests -Dv2tofhir.localMessages=/path/to/ehex-testing`

## 6. Verification

- [x] 6.1 `mvn test -Dtest=MessageParserTests` green.
- [x] 6.2 `mvn clean install` green (full suite + build).
- [x] 6.3 `openspec validate --changes fix-z42-history-forecast-split --strict` passes.

## 7. Downstream (`izgw-transform`) — separate repo, separate commit

- [x] 7.1 Confirm no `izgw-transform` code change is needed **for this change to be correct**:
      `preFilter` / `cleanupBundleOfUnmarkedResources` already filter the searchset by requested
      resource type, and `FhirControllerTests` has no Z42 fixture containing administered doses, so
      no test breaks. Record the consequence — `GET /ImmunizationRecommendation` returns no
      evaluated history, which the two-query (Z34/Z44) design serves via `GET /Immunization`.
      **Superseded downstream, outside this change's scope:** that consequence turned out to hide
      the `protocolApplied` data measured as unreachable via Z32, so `izgw-transform` now marks Z42
      `Immunization` entries `include` on the recommendation query. Owned by
      `izgw-transform/openspec/changes/fix-fhir-searchset-include-mode`; no `v2tofhir` change
      followed from it. See design, "The searchset filter used to drop evaluated history".

## 8. Defects found validating live IIS responses through the Transformation Service

Added after sections 1–5 were complete and green. All in `segment/OBXParser.java` unless noted.
R4 targets verified against `hl7.org/fhir/R4` — see design Decisions 5–7.

- [x] 8.10 Scope the `30956-7` education change to Z42 instead of removing it for every profile
      (review finding — v2tofhir serves consumers whose message content is not known here, and a VXU
      sender following the CDC IG's VIS OBX list could emit `30956-7` in a VIS block). `IzDetail`
      records whether the message declared Z42; `OBXParser.handleVisObservations` returns early for
      `VACCINE_TYPE` only in that case and otherwise keeps the original
      `education.documentType` `iso21090-SC-coding` routing. Add a committed VXU fixture carrying
      `30956-7` inside a genuine VIS block and assert the extension is still produced; confirm the
      guard is load-bearing by forcing the Z42 branch and watching only that assertion fail.
- [x] 8.11 Set `Observation.subject` from the message's `Patient` in `OBXParser.setup()` (review
      finding — removing the forecast `partOf` left forecast observations with no path to the patient
      at all). The V2-to-FHIR IG already requires this: the `VXU_V04` to Bundle map's OBX row states
      `Observation[2].subject.reference=Patient[1].id`. Applies to every OBX on every message type, so
      it fixes history and forecast alike and revives `finish()`'s dead `docRef.setSubject(...)` path.
      Assert the subject count on both the shape-B Z42 and the VXU fixtures.
- [x] 8.1 `handleVisObservations` called `getLastEducation()` before deciding whether the code is VIS
      material, and HAPI's `getEducationFirstRep()` creates-and-appends, so any recognized non-VIS
      OBX in a history group appended a phantom empty `Immunization.education`. Return for anything
      other than `69764-9` / `29768-9` / `29769-7` before the education lookup. This subsumes the
      narrower `30956-7` guard from 2.3. Guard it with a committed fixture (a history group with
      `30956-7` + `59781-5` and no VIS) and an assertion in the local IIS test that no
      `Immunization` carries an empty education element. Measured before the fix: every Nevada
      history `Immunization` had `education = [ {} ]`.
- [x] 8.2 Remove the forecast-path `Observation.partOf` → `ImmunizationRecommendation` link. R4
      restricts `Observation.partOf` to
      `MedicationAdministration | MedicationDispense | MedicationStatement | Procedure | Immunization | ImagingStudy`,
      so it is invalid FHIR (80 occurrences in one Nevada response). Leave the history path
      (`partOf` → `Immunization`) alone — that one is valid.
- [x] 8.3 Do **not** replace it with `recommendation.supportingPatientInformation`. That was
      implemented, tried against a live Transformation Service, and withdrawn: after 8.5–8.7 the
      component already carries every forecast observation's content, and the searchset filter
      deletes the Observations, so the delivered resource held 80 references to resources absent from
      the bundle, with no identifier or display to fall back on. Record the rationale in the design
      and leave a `ponytail:` comment naming the construction to use
      (`ParserUtils.toReference(observation, immunizationRecommendation, "information")`) if a
      forecast observation ever carries something the component cannot hold.
- [x] 8.4 Register FHIR's canonical `part-of` alongside the existing `partof` on the history link, so
      `_revinclude=Observation:part-of` matches. `addSearchNames` already accepts varargs; precedent
      is `PV1Parser.java:86` (`"subject", "patient"`). Also reconcile the two spellings in this repo:
      `"partOf"` at `OBXParser.java:445` vs `"partof"` at `:503`/`:505` and
      `DatatypeConverter.java:1258`.
- [x] 8.5 Map `30982-3^Reason Code` (`WHY_INVALID`, currently an empty `break`) to
      `recommendation.forecastReason` on the block's component. Nevada sends free `ST` text, so carry
      it as `CodeableConcept.text` with no coding — the binding is example-strength. Six occurrences
      in one observed response.
- [x] 8.6 Map `59779-9^Immunization Schedule Used` (`FORECAST_SCHEDULE`, currently an empty `break`)
      to the publishing authority: `ImmunizationRecommendation.authority` on the forecast path,
      `Immunization.protocolApplied.authority` on the history path. Both `Reference(Organization)`
      0..1; R4's definition is literally "Indicates the authority who published the protocol
      (e.g. ACIP)" and the observed value is `VXC16^ACIP^CDCPHINVS`. Create one `Organization` named
      from OBX-5 and reuse it for the message.
- [x] 8.7 Map `30973-2` and `59782-3` on the **history** path to
      `Immunization.protocolApplied.doseNumber[x]` / `.seriesDoses[x]` (both currently reach only the
      forecast component). Use one shared `protocolApplied` element, the same one 8.6 writes
      `authority` to. Note `doseNumber[x]` is **1..1** inside `protocolApplied`: do not synthesize a
      value, and accept that a group supplying only `59782-3`/`59779-9` leaves the element short a
      `doseNumber` (tolerated, as with `imr-1`).
- [x] 8.8 Assertions in `messages.txt`: `forecastReason` text on a Too Old component;
      `ImmunizationRecommendation.authority` resolving to an `Organization`;
      `Immunization.protocolApplied.doseNumber` on a history dose; that no `Observation` has an
      `ImmunizationRecommendation` in `partOf`; and that no `recommendation` component emits a
      `supportingPatientInformation`.
- [x] 8.9 Extend `Z42ForecastTests` local assertions: every forecast component with a
      `59783-1 = LA13424-9^Too Old` has a `forecastReason`; no `Observation.partOf` references an
      `ImmunizationRecommendation`; no component emits `supportingPatientInformation`. Re-run against
      `ehex-testing` and confirm the Nevada and Alaska targets still hold (2/16 and 3/10).

## 9. Scenario coverage gaps closed after `/opsx:verify`

Verify found nine spec scenarios with no committed fixture. These four guard code this change wrote,
so they are closed here. The rest are left as recorded gaps: an ORC with no following RXA
(three-line branch), "every reference resolvable" (broad to express in FHIRPath), RXA-22 supplying
the recommendation date (pre-existing code path, and the shape-B fixture's MSH-7 and RXA-22 carry
the same instant so no assertion can distinguish them), and `forecastStatus` with an IIS-local code
(blocked by the pre-existing `testSegmentConversions` OBX-5 text roundtrip).

- [x] 9.1 Add one hand-written Z42 fixture to `messages.txt` closing three scenarios at once — a
      forecast group whose three `30956-7` observations all carry OBX-4 = 1 (sub-id is not a
      delimiter), a `30980-7` arriving before any `30956-7` (lands on the component the first vaccine
      type then adopts), and a second forecast group with no `30956-7` at all (one component, no
      `vaccineCode`). Four components from two groups, with per-component date assertions proving no
      leakage between them.
- [x] 9.2 Assert the positive half of the `partOf` rule on the shape-B fixture: a recognized history
      code (`64994-7`, and `30973-2` whose value is also folded into `protocolApplied`) links to
      `Immunization/`, plus a total `partOf` count. The pre-existing assertion only proved no
      `partOf` points at an `ImmunizationRecommendation`, which would still pass if `partOf`
      disappeared entirely.
- [x] 9.3 Drop the "History observation for an unrecognized code has no partOf" scenario from the
      spec rather than test it. `30963-3` having no `partOf` is logged as deviation 4 to be fixed by
      mapping it to `Immunization.fundingSource`; asserting today's behaviour would cement the
      defect. The requirement now states explicitly that the case is left unspecified, and why.
- [x] 9.4 Verify the new assertions fail when wrong, not just pass — mutated
      `recommendation.count()` and a `dateCriterion.value` date comparison, confirmed
      `MessageParserTests` goes red, then restored. Suite: 1109 tests (was 1087), 0 failures.
- [x] 9.5 Assert the Z32 path (`messages.txt:355`, the `RESULT-03` history response): no
      `ImmunizationRecommendation`, four `Immunization` (profile alone decides for Z32, so the two
      ORCs with no following RXA still yield one each — the carve-out the spec states but nothing
      asserted), two of them carrying the RXA vaccine code, plus `education` and
      `programEligibility`. Eleven Z32 fixtures existed and none carried a single assertion, so a
      regression in the Z32 branch of `checkForImmunization()` would have been silent.
- [x] 9.6 Make every `ImmunizationRecommendation.date` assertion distinguish its source instead of
      only proving presence. Give the shape-B forecast RXA-22 an instant different from its MSH-7
      (`20250201080000-0500`) and assert the exact value, so the RXA-22 path is proven rather than
      coincidentally equal to the fallback; assert the exact MSH-7-derived value on the three
      fixtures whose forecast RXA stops before RXA-22. Each asserted instant carries an explicit
      offset taken from the message, so none of them depends on the JVM default time zone — the
      first attempt used an offsetless RXA-22 and serialized against the local zone.
