# Design

## Context

Resource-type selection for immunization ORC/RXA groups lives entirely in
`segment/IzDetail.java`. `IzDetail` is stored on the parse `Context` (one per message) and holds
the current `Immunization` / `ImmunizationRecommendation` plus the `hasImmunization` /
`hasImmunizationRecommendation` decision booleans.

### Current call path (Z42)

```
ORCParser.setup()
  → IzDetail.get(mp)
  → IzDetail.initializeResources(initialize=true, ORCseg)
      → this.segment = ORCseg
      → checkForImmunization()                       // forced because initialize=true
          → resets both booleans FALSE, both resources null
          → loops Bundle.meta.profile
          → matches "CDCPHINVS#Z42"
          → HARD-SETS hasImmunizationRecommendation = TRUE ; return   ← defect 1
      → creates ImmunizationRecommendation (patient ref, MessageHeader focus)
  → builds ServiceRequest

RXAParser.setup()
  → IzDetail.initializeResources(initialize=false, RXAseg)   // no re-decide
  → hasRecommendation() returns cached TRUE
  → immunizationRecommendation.addRecommendation()           // exactly ONE component per RXA

OBXParser.setup()
  → recommendation = izDetail.getRecommendation()            // the LAST component
  → every forecast OBX in the group writes to that one component  ← defect 2
```

Because the Z42 branch ignores RXA content, administered doses are emitted as
`ImmunizationRecommendation`. And because the recommendation component is created once per RXA,
an IIS that puts N forecasts under one RXA gets all N merged into one component.

### The two IIS forecast shapes

Verified against real (de-identified) test-system responses and the CDC IG samples already in
`src/test/resources/messages.txt`:

**Shape A — one 998 RXA, N blocks keyed by `30956-7`** (Nevada/WebIZ; `messages.txt:1039`)

```
ORC|RE||9999^NV0000
RXA|0|1|...|998^No Vaccine Administered^CVX|999|...|NA
OBX|1|CE|30956-7^Vaccine Type^LN|1|43^Hep B, adult^CVX      ← block 1 starts
OBX|2|DT|30981-5^Earliest date...^LN|1|20001001
OBX|3|DT|30980-7^Date Vaccine Due^LN|1|20001001
OBX|5|CE|59783-1^Series Status^LN|1|LA13423-1^Overdue^LN
OBX|7|CE|30956-7^Vaccine Type^LN|2|03^MMR^CVX               ← block 2 starts
...                                                          (16 blocks total)
```

**Shape B — N separate 998 RXAs, one block each** (Alaska/VacTrAK)

```
ORC|RE||9999^AKA
RXA|0|1|...|998^no vaccine administered^CVX|999|...|NA
OBX|9|CE|30956-7^vaccine type^LN|1|45^HepB^CVX
OBX|12|TS|30980-7^Date vaccination due^LN|1|19860101
...
ORC|RE||9999^AKA                                             ← next forecast
RXA|0|1|...|998^no vaccine administered^CVX|999|...|NA
OBX|17|CE|30956-7^vaccine type^LN|1|115^DTaP/Tdap/Td^CVX
...                                                          (10 RXAs total)
```

**OBX-4 sub-id is not a usable grouping key.** Shape B holds sub-id `1` for every block. Worse,
shape A's *history* groups use sub-id `2` and `3` for `64994-7` (funding eligibility) and
`30963-3` (vaccine purchased with) — non-forecast data. `30956-7` is the only reliable delimiter,
and it works for both shapes: N occurrences → N forecasts, regardless of RXA boundaries.

### `30956-7` is never a VIS vaccine type in practice

`OBXParser.VisCode` declares LOINC `30956-7` **twice** — `VIS_VACCINE_TYPE_CODE` (routes to
`Immunization.education[].documentType` extension) and `FORECAST_VACCINE_CODE` (routes to
`recommendation.vaccineCode`). Enum declaration order makes the VIS one always win, so
`FORECAST_VACCINE_CODE` is dead code and `recommendation.vaccineCode` is never populated from OBX.

Every VIS block in the committed VXU fixtures (`messages.txt:123`, `141`, `151`, …) and in the
Alaska evaluated-history groups uses `29769-7` / `29768-9` / `69764-9` and contains **no**
`30956-7`. In the Nevada evaluated-history groups, `30956-7` carries the *evaluated antigen*
(e.g. `107^DTaP, UF` on a `09^Td` dose) — semantically not a VIS at all.

So `30956-7` is treated as one thing: the evaluated/forecast vaccine type.

**The VIS education mapping is not deleted, it is scoped to Z42.** The evidence above covers the
committed fixtures and three real IIS captures — it does not cover the messages this library sees in
other deployments. v2tofhir is a general HL7 V2 to FHIR library, and a VXU sender following the CDC
IG's VIS OBX list could plausibly emit `30956-7` inside a VIS block. Deleting the mapping outright
would silently drop that sender's extension.

Instead `IzDetail` records whether the message declares Z42, and `OBXParser` routes on it:

- **Z42** — `30956-7` is the evaluated or forecast antigen. No `education`.
- **any other profile** — unchanged from before this change: the `iso21090-SC-coding` extension on
  `Immunization.education.documentType`.

Order-independent, no group-scoped state, and provably no behaviour change for VXU, Z22, Z32 or any
other profile. The alternative — gate on a real VIS code under the same OBX-4 sub-id — is more
precise but has to buffer a group's observations to be order-independent, which is real complexity
for a case that cannot be demonstrated to exist. Left as a `TODO` at the enum constant.

### The type discriminator already exists (dead code)

`IzDetail.checkRXA()` (currently `@SuppressWarnings("unused")`, never called) implements the
CDC-documented rule:

```java
if ("ORC".equals(segment.getName())) {
    rxa = ParserUtils.getFollowingSegment(segment, "RXA");   // ORC → its RXA
} else if ("RXA".equals(segment.getName())) {
    rxa = segment;
}
if (rxa == null) { hasImmunization = hasImmunizationRecommendation = false; return false; }
hasImmunizationRecommendation = "998".equals(ParserUtils.toString(rxa, 5));  // RXA-5 CVX id
hasImmunization = !hasImmunizationRecommendation;
```

Verified empirically, because this method has had **zero live callers** and was therefore
completely unexercised:

- HAPI parses these messages as the stock `v251.RSP_K11` (a *tabular* query response with no
  ORC/RXA groups), so ORC/RXA/RXR/OBX arrive as **nonstandard segments** at the message root,
  named `ORC, RXA, OBX, ORC2, RXA2, RXR, OBX2, ORC3, …`.
- `Segment.getName()` still returns `"ORC"` for all of them, so `checkRXA`'s name guard holds.
- `getFollowingSegment` matches on `name.startsWith(myName)` while identifying the segment by its
  unique structure name (`g.get("ORC2") == segment`), so each ORC resolves to its own RXA.
  Confirmed on the CDC IG mixed sample: `ORC→31`, `ORC2→48`, `ORC3→110`, `ORC4→998`.
- `ParserUtils.toString(segment, 5)` returns component 0 of the CWE/CE, so `"998".equals(...)`
  compares the code, not `998^no vaccine administered^CVX`.

## Goals / Non-Goals

**Goals**
- Z42 messages classify each ORC/RXA group correctly (history → `Immunization`,
  `RXA-5 == 998` → forecast).
- Both forecast shapes produce the same output: a single `ImmunizationRecommendation`
  resource with one `recommendation` component per forecast vaccine group, each carrying the
  real antigen as its `vaccineCode`. This is R4's intended model — the resource is "a
  patient's point-in-time set of recommendations", and every per-forecast field
  (`vaccineCode`, `forecastStatus`, `dateCriterion`, `series`, dose numbers) lives on the
  component.
- Reuse the existing `checkRXA()` logic rather than adding a parallel path.
- No behavior change for Z22 / Z32 / VXU.
- The emitted `ImmunizationRecommendation` satisfies R4 required elements: `date` (1..1)
  even when the IIS omits RXA-22 (Nevada truncates forecast RXAs at RXA-20), and no
  `dateCriterion` without its required `code`. It carries the ORC-3 identifier so downstream
  deterministic-id assignment hashes a non-null key.
- Every reference the converter emits is one R4 permits, and one that still resolves after a
  consumer filters the bundle to the requested resource type — in particular no
  `Observation.partOf` → `ImmunizationRecommendation` and no reference from the
  `ImmunizationRecommendation` to a discarded `Observation` (Decision 5).
- A `GET /ImmunizationRecommendation` result is interpretable on its own: the forecast's reason and
  the authority that published the schedule are on the resource, not only on `Observation`s that the
  downstream searchset filter discards (Decision 6).
- No element is created solely as a side effect of looking it up — specifically no empty
  `Immunization.education` (Decision 7).

**Non-Goals**
- No use of RXA-9 (information source) or RXA-20 (completion status) as type discriminators —
  `RXA-5 == 998` is the CDC-documented signal.
- No normalization of `forecastStatus` codings. Nevada sends LOINC answers (`LA13423-1^Overdue`,
  `LA13422-3^On Schedule`, `LA13424-9^Too Old`); Alaska sends a local system
  (`P^Past Due`, `U^Up to Date` in `99002`). Both pass through as received.
- No `supportingImmunization` linking, and no `supportingPatientInformation` either — see
  Decision 5 for why the latter was implemented and withdrawn.
- No normalization of the `30982-3` reason text into a coded `forecastReason`. The text passes
  through as `forecastReason.text`; the binding is example-strength, so no coding is required.
- No `59781-5^Dose Validity` mapping — R4 `Immunization` has no element for it (see follow-up).
- No suppression of the redundant `Observation` resources for OBX codes that are also folded into a
  dedicated element. That is a library-wide call affecting VXU and Z32 (see follow-up).

## Decision

### 1. Per-group resource type

In `IzDetail.checkForImmunization()`, replace the Z42 hard-set branch with a delegation to
`checkRXA()`, and remove the `@SuppressWarnings("unused")` on `checkRXA()`.

```java
if ("CDCPHINVS#Z42".equals(url.getValue())) {
    // Z42 (RSP_K11 evaluated history + forecast) mixes resource types:
    // evaluated history -> Immunization, forecast (RXA-5 == 998) -> ImmunizationRecommendation.
    checkRXA();
    return;
}
```

`checkForImmunization()` already resets both resources to `null` on every `initialize=true` call
(once per ORC), so each group decides independently. `this.segment` is set to the ORC before
`checkForImmunization()` runs, so `checkRXA()` resolves the correct following RXA. The subsequent
`RXAParser.initializeResources(false, …)` call does not re-decide, so it consumes the ORC's
per-group decision.

### 2. Forecast splitting — one resource, one `recommendation` component per `30956-7`

**Model choice.** Two FHIR-valid options existed: N `ImmunizationRecommendation` resources
with one `recommendation` component each, or one resource with N components. The single
resource is chosen: it is R4's canonical model (the resource is defined as "a patient's
point-in-time set of recommendations", which is exactly what one Z42 response is), it is the
smaller diff (no new resource creation, no `MessageHeader.focus` wiring, no per-block
identifier minting), and it eliminates the downstream `adjustIdentifiers` collision outright —
there is only one forecast resource per message.

**Resource sharing across groups.** `checkForImmunization()` keeps resetting the decision
booleans per ORC, but `initializeResources` reuses the message's existing
`ImmunizationRecommendation` instead of creating a fresh one per forecast group (shape B has
up to 10 such groups). `RXAParser.setup()` continues to `addRecommendation()` one empty
component per forecast RXA — on the shared resource.

`IzDetail` gains per-group forecast bookkeeping, reset in `checkForImmunization()`:

```java
private boolean forecastBlockStarted;   // has a 30956-7 been seen in this group yet?
```

and one new method:

```java
/**
 * Start a new forecast block (OBX-3 == 30956-7 inside a forecast group).
 * The first block adopts the empty recommendation component added by RXAParser.setup();
 * each later block adds a new component to the same resource.
 * @return the recommendation component the block's OBX values should be written to
 */
public ImmunizationRecommendationRecommendationComponent startForecastBlock() { … }
```

- **First block in the group** — adopt the empty `recommendation` component added by
  `RXAParser.setup()`. Nothing new is created, so shape B (one block per RXA) follows exactly
  today's creation path.
- **Each later block** — `immunizationRecommendation.addRecommendation()`, and make it the
  component that `getRecommendation()` returns.

Shape A therefore yields one resource with 16 components; shape B yields one resource with
10 components (one per single-block RXA). Identical output shape from both.

### 3. `OBXParser` wiring

- Collapse the duplicate `VisCode` entries for `30956-7` into one constant and remove it from the
  VIS education switch.
- `redirectTo()` (`@ComesFrom(field = 3)`) starts the block. Ordering is safe:
  `FieldHandler.compareComesFrom` sorts by priority descending then **field ascending**, and both
  `redirectTo` (field 3) and `setValue` (field 5) use the default priority — so OBX-3 is always
  handled before OBX-5.

```java
if (izDetail.hasRecommendation() && VisCode.VACCINE_TYPE.equals(redirect)) {
    recommendation = izDetail.startForecastBlock();
}
```

- `setValue()` (field 5) then populates `recommendation.vaccineCode` from the converted
  `CodeableConcept` — reviving what was the unreachable `FORECAST_VACCINE_CODE` case.
- `setup()` keeps its existing `recommendation = izDetail.getRecommendation()` as a fallback so
  a forecast OBX arriving *before* any `30956-7` still has a target.
- In a history group (`hasImmunization()`), `30956-7` does nothing beyond the standard
  `Observation` + `partOf` link — no `education` element.

### 4. R4 required elements and identifier

FHIR R4 constraints verified against `hl7.org/fhir/R4/immunizationrecommendation.html`:
`date` 1..1, `recommendation.forecastStatus` 1..1, `dateCriterion.code` 1..1, and invariant
`imr-1` (`vaccineCode` or `targetDisease` SHALL be present on each component).

- **`date` (1..1).** RXA-22 supplies it when present (Alaska populates RXA-22 on every RXA).
  Nevada truncates forecast RXAs at RXA-20, so `initializeResources` defaults `date` from the
  MSH-7 message timestamp when creating the resource; RXA-22 overwrites it when it arrives.
- **`forecastStatus` (1..1).** `59783-1` is present in every observed forecast block, so each
  component gets one. A block missing it leaves the component without a `forecastStatus` —
  tolerated (Postel), no value is synthesized.
- **`dateCriterion.code` (1..1).** The current forecast path maps RXA-3 through
  `recommendation.getDateCriterionFirstRep().setValueElement(...)` (`RXAParser.java:92`),
  auto-creating a criterion with a value but **no code** — invalid, and semantically noise
  (forecast RXA-3 is the forecast-generation timestamp). The RXA-3 write is dropped on the
  forecast path, alongside the RXA-5 drop.
- **`imr-1`.** A forecast group with no `30956-7` leaves its component with no `vaccineCode`
  and no `targetDisease` — a tolerated invariant violation for garbage input, never observed
  in real IIS responses.
- **Identifier.** `ImmunizationRecommendation` currently gets no identifier, so downstream
  `FhirController.adjustIdentifiers` hashes `patient|null|null`. Confirmed against pre-fix captures:
  all 3 Nevada resources carried the id `NV0000|3973565|null|null` and all 13 Alaska resources
  carried `AKA|2722530|null|null` — one id per response, repeated. With one resource per message
  there is no collision, but `ORCParser.addOrderIdentifier` still copies ORC-3 (sentinel
  `9999^NV0000` / `9999^AKA`) onto the resource on the forecast path — mirroring the history
  path — so the deterministic id is stable and non-null.

### 5. Observation linkage — a forecast observation gets no link

`OBXParser.linkObservation()` links every recognized OBX to the resource it belongs to. The history
direction is valid; the forecast direction was not. R4 `Observation.partOf` is typed:

```
Reference(MedicationAdministration | MedicationDispense | MedicationStatement | Procedure
          | Immunization | ImagingStudy)
```

`ImmunizationRecommendation` is not in that list, so the old forecast link emitted invalid FHIR — 80
such references in one observed Nevada response. It is removed. The history link
(`Observation.partOf` → `Immunization`) is valid and stays.

**The forecast link is not replaced.** The obvious substitute is
`ImmunizationRecommendation.recommendation.supportingPatientInformation` (`Reference(Any)` 0..*,
"Patient Information that supports the status and recommendation", search parameter `information`).
It was implemented, tried end to end against a live Transformation Service, and withdrawn, because
it makes the delivered payload worse:

- Every forecast observation's content is **already** on the component — `vaccineCode` (30956-7),
  `forecastStatus` (59783-1), `dateCriterion` (30980-7 / 30981-5 / 59777-3 / 59778-1), `doseNumber`
  (30973-2), `seriesDoses` (59782-3), `forecastReason` (30982-3), `authority` (59779-9). After
  Decision 6 there is nothing left that only the Observation carries. The reference points at a
  duplicate.
- The searchset filter downstream keeps only the requested resource type, so the Observations are
  deleted while the `ImmunizationRecommendation` survives. Measured on a live Nevada query: the
  delivered resource carried **80 references to Observations that were not in the bundle**, with no
  `identifier` or `display` to fall back on, and no endpoint that serves those ids. Before the
  change the invalid reference at least lived on the Observation, which was itself dropped, so the
  client received nothing broken.

Trading an invalid reference for 80 unresolvable ones is not an improvement. Emitting neither is
correct on both counts: the component is self-describing, and no reference dangles.

**Attribution comes from `Observation.subject`, not from a link to the recommendation.** Removing the
forecast link exposed a separate pre-existing gap: `OBXParser` never set `Observation.subject`, so a
forecast observation had no path to the patient at all once its `partOf` was gone. The V2-to-FHIR IG
already requires that mapping — the `VXU_V04` to Bundle map's OBX row states
`Observation[2].subject.reference=Patient[1].id` — and US Core requires it too. `setup()` now sets it
for every OBX, on every message type. That is the right fix: it attributes history and forecast
observations alike, satisfies a mapping this library was missing, and does not reintroduce an invalid
reference. It also revives `finish()`'s `docRef.setSubject(...)` path, which read
`observation.getSubject()` and was therefore dead.

Left as a `TODO` at the call site — if a forecast observation ever carries something a
`recommendation` component cannot hold, link it then, and build it with
`ParserUtils.toReference(observation, immunizationRecommendation, "information")`.

**Why the construction matters if that day comes.** The `_include` / `_revinclude` implementation in
`izgw-transform` does not use HAPI `SearchParameter` or FHIRPath. `FhirController.includeMatches`
compares the requested parameter name against search-name strings that *this library* stamps onto
`Reference.userData` through `ParserUtils.toReference(resource, source, searchNames...)`. A reference
built with `new Reference(...)` is valid FHIR but invisible to `_include`.

The same mechanism explains a separate one-word fix that is in scope: the canonical FHIR search name
for `Observation.partOf` is `part-of`, but the library registered only `partof`, so
`_revinclude=Observation:part-of` silently matched nothing. Both spellings are registered now
(`addSearchNames` accepts varargs; precedent at `PV1Parser.java:86`, `"subject", "patient"`).

### 6. Recovering the three dropped observation groups

All three were recognized `VisCode`s whose switch cases were empty `break`s.

| V2 | FHIR R4 target | Notes |
|---|---|---|
| `30982-3^Reason Code` | `recommendation.forecastReason` | `CodeableConcept` 0..*, binding **example**. Nevada sends free `ST` text, so it lands in `forecastReason.text` with no coding — legal, and the only faithful representation |
| `59779-9^Immunization Schedule Used` | `ImmunizationRecommendation.authority` (forecast) / `Immunization.protocolApplied.authority` (history) | Both `Reference(Organization)` 0..1. R4's own definition is "Indicates the authority who published the protocol (e.g. ACIP)", and the observed value is `VXC16^ACIP^CDCPHINVS`. Creates one `Organization` named from OBX-5's display, reused for the message |
| `30973-2` dose number, `59782-3` doses in series (history path only) | `Immunization.protocolApplied.doseNumber[x]` / `.seriesDoses[x]` | Already mapped on the forecast path. `protocolApplied` is 0..*; `series`/`authority`/`targetDisease`/`seriesDoses` are optional within it |

**`doseNumber[x]` is 1..1 inside `protocolApplied`.** A `protocolApplied` element without it is
invalid. Two consequences:

- `59782-3` or `59779-9` arriving in a history group with no `30973-2` would otherwise produce an
  invalid `protocolApplied`. They are written to the same single `protocolApplied` element
  (`getProtocolAppliedFirstRep()`), and when no `30973-2` ever arrives that element violates 1..1 —
  tolerated for malformed input exactly as `imr-1` is (Decision 4), never observed in real data.
  Every observed history group that carries `59782-3` also carries `30973-2`.
- Nothing is synthesized to satisfy the cardinality. Postel: convert what is there, do not invent.

`59781-5^Dose Validity` stays dropped — R4 `Immunization` has no element for an evaluated dose
validity verdict, so it needs an extension decision. It remains a follow-up. Note it is what
exposed the phantom-education defect below.

### 7. `Immunization.education` is only for VIS codes

`handleVisObservations()` called `getLastEducation()` **before** the switch that decides whether the
code is VIS material at all. HAPI's `getEducationFirstRep()` creates and appends when the list is
empty, so any recognized non-VIS OBX in a history group — `30956-7`, `59781-5`, the forecast codes —
appended a phantom empty `education` element. Measured on the Nevada capture: every history
`Immunization` came out with `education = [ {} ]` (`isEmpty() == true`), where Alaska's, which carries
genuine VIS observations, was correct.

HAPI omits empty elements when serializing, so it never reached the wire, but it was in the model:
`Immunization.education.exists()` was true and anything walking the resource saw a bogus element.
The fix returns for any code other than the three VIS codes before the education lookup, which also
subsumes the narrower `30956-7` guard from Decision 3.

This defect is pre-existing but was unreachable for Z42 before this change, because Z42 history
groups were misclassified as forecasts and never took the VIS path at all.

### Edge cases

- **ORC with no RXA**: `checkRXA()` sets both booleans false → no resource; `ServiceRequest` still
  produced. Matches the robustness principle.
- **Forecast group with no `30956-7`**: `forecastBlockStarted` stays false, the component added
  by `RXAParser.setup()` stands as today, with no `vaccineCode` (tolerated `imr-1` violation —
  see Decision 4). No throw.
- **Lazy getter before any ORC/RXA** (`segment == null`): `checkRXA()` defaults to recommendation
  (its existing behavior); acceptable because a Z42 message with no ORC/RXA produces no
  immunization resources anyway.
- **Z22 / Z32 branches unchanged** → no regression for history-only or VXU. VIS handling is
  untouched because no VIS block in any fixture or observed response uses `30956-7`.
- **`RXAParser` forecast writes**: `addVaccineCode(RXA-5)` is dropped on the forecast path (it is
  what stamps the useless `998`), and so is the RXA-3 `dateCriterion` write (code-less criterion,
  see Decision 4). `RXA-22 → ImmunizationRecommendation.date` stays; when absent, the MSH-7
  default from resource creation stands.

## Risks / Trade-offs

- **Output-shape change** for Z42 consumers: history rows become `Immunization`, and forecast
  output becomes exactly one `ImmunizationRecommendation` with one component per forecast —
  shape-A components go from 1 merged to N, shape-B resources go from N to 1. This is the
  intended correction; documented as BREAKING.
- `getFollowingSegment` depends on HAPI's nonstandard-segment naming (`ORC`, `ORC2`, …). Verified
  empirically above, and now covered by a direct unit test rather than only a message-level one.
- Dropping `30956-7` from VIS education is right for all observed data but not literally what the
  CDC IG's VIS OBX list implies. Marked with a `ponytail:` comment naming the upgrade path.

## Migration

None required within the library. Downstream consumers of Z42 bundles must handle `Immunization`
entries for evaluated-history rows, and multiple `ImmunizationRecommendation` entries where one
previously appeared.

## Downstream interaction (`izgw-transform`)

### Required: version bump

`v2tofhir` is pinned **directly** in `izgw-transform/pom.xml` (currently `2.4.0`), **not** in
`izgw-bom`. Releasing this change requires an `izgw-transform` commit to bump that version.

### The searchset filter used to drop evaluated history — since resolved downstream

v2tofhir only emits a `Bundle.type = MESSAGE` with no `search` components
(`BaseParser.java:128`); it never sets `Bundle.entry.search.mode`. The searchset framing is
applied downstream by `izgw-transform` `FhirController` (`preFilter`, ~line 1083):

- `Bundle.type` is rewritten to `SEARCHSET` (`FhirController.java:1064`).
- `requested` = the resource type from the query URL (last path segment; `$match` → Patient).
- Per entry: `r.fhirType() == requested` → `search.mode = MATCH`; `OperationOutcome` → `OUTCOME`;
  anything else falls through.
- `cleanupBundleOfUnmarkedResources` (`FhirController.java:1175`) then **removes** any entry left
  with `mode == null` that was not `_include`-marked.

**This is a two-query design, and the fix aligns v2tofhir with it.** `izgw-transform` selects the
V2 query profile from the requested FHIR resource type (`FhirController.java:890`):

```java
String queryType = Strings.CS.contains(req.getRequestURI(), "ImmunizationRecommendation")
    ? IzQuery.RECOMMENDATION : IzQuery.HISTORY;   // Z44 vs Z34
```

| FHIR query | V2 request | V2 response | Transform keeps (mode=match) |
|---|---|---|---|
| `GET /Immunization` | Z34 (history) | Z32 (history only) | `Immunization` |
| `GET /ImmunizationRecommendation` | Z44 (eval history + forecast) | Z42 (both) | `ImmunizationRecommendation` only |

**Consequence at the time this change was written**: the Z42 bug labelled every entry
`ImmunizationRecommendation`, so a `GET .../ImmunizationRecommendation` query marked them all
`match` — including administered doses that the two-query design intended to be served by the
separate `/Immunization` (Z34) path. After the fix, evaluated-history rows become `Immunization`,
which the transform then excluded from the recommendation searchset. The bug had been fighting the
design by stuffing administered doses into the recommendation result.

Positive side effects of the fix on the transform, no code change needed:

- History rows now carry the real ORC-3 identifier (`NV0000|41348935`, `AKA|2722530.32.20250730`),
  so `adjustIdentifiers` mints stable, distinct ids for them.
- `64994-7^Vaccine funding program eligibility` now reaches `Immunization.programEligibility`; on
  the recommendation path it was silently discarded.

**Measured limit of the two-query model.** A live Nevada `GET /Immunization` capture shows the Z32
response carrying only `64994-7` and `30963-3` — no `30973-2`, `59782-3`, `59779-9`, `59781-5` or
`30956-7`. The evaluation of each dose exists only in Z42 history groups. So the
`Immunization.protocolApplied` mapping added by this change (Decision 6) was, on the two-query model,
correct but unreachable by any client call: the only path carrying it served
`/ImmunizationRecommendation`, which discarded `Immunization`. `programEligibility` was unaffected —
`64994-7` is in Z32, so it reached clients via `/Immunization`.

**Resolved downstream after this design was written.** That measurement is what motivated the
transform to stop discarding them. `izgw-transform` now marks a Z42 `Immunization` as `include`
rather than removing it, on the recommendation query only:

```java
// FhirController.preFilter
} else if (r instanceof Immunization && IMMUNIZATION_RECOMMENDATION.equals(requested)) {
    markEntry(entry, resources, SearchEntryMode.INCLUDE);
```

`include`, not `match`, so a client can still isolate the forecast it asked for by filtering on
`mode = 'match'`. No `_include` parameter is needed — the behaviour is unconditional.

That work is **owned by a separate OpenSpec change in that repo**,
`izgw-transform/openspec/changes/fix-fhir-searchset-include-mode`, not by this change. It is recorded
here only because it removes the limitation described above: `protocolApplied.doseNumber`,
`protocolApplied.authority` and `programEligibility` on evaluated-history doses are now reachable by
a client call, verified against a live Nevada Z42 response. Nothing in `v2tofhir` changed to enable
it; this change simply produces the `Immunization` resources the transform now keeps.

A single query returning both types was an `izgw-transform` concern, and that is where it was
settled — see above. Consumers can now drop to one call: `GET /ImmunizationRecommendation` returns
the forecast as `match` and the evaluated history as `include`, which is strictly more per dose than
`GET /Immunization` (Z34/Z32) yields.

### Housekeeping

`izgw-transform/ehex-testing/` holds real IIS test-system responses. It is untracked but **not**
git-ignored. Add it to that repo's `.gitignore` so vendor responses cannot be committed
accidentally.

## Test data policy

**No IIS-sourced message is committed to this repo.** Two tiers:

**Tier 1 — committed, CDC-IG or hand-written only.** The CDC IG samples already in
`src/test/resources/messages.txt` cover the mixed case and shape A:

| Fixture | Contents | Expected after this change |
|---|---|---|
| `messages.txt:997` | 3 history ORC/RXA (`31`, `48`, `110`) + 1 forecast block | 3 `Immunization`, 1 `ImmunizationRecommendation` with 1 component (`vaccineCode = 31`) |
| `messages.txt:1039` | 1 forecast RXA, 3 `30956-7` blocks (`03`, `10`, `107`) — **shape A** | 0 `Immunization`, 1 `ImmunizationRecommendation` with 3 components |
| *new, hand-written* | 1 history ORC/RXA with real VIS OBX (`29769-7`/`29768-9`) + 3 forecast ORC/RXAs, one `30956-7` each — **shape B** | 1 `Immunization` (1 `education`), 1 `ImmunizationRecommendation` with 3 components |

Assertions are `@`-prefixed FHIRPath lines placed under each message in `messages.txt`. The
`testTheData` harness (`MessageParserTests.java:90`) already loads and evaluates them via
`TestData.evaluateAllAgainst`; the file currently contains **zero** `@` lines, so the mechanism is
present and unused.

**Tier 2 — local-only, never committed.** An opt-in test reads `*.txt` HL7 responses from a
directory named by a system property and `Assumptions.assumeTrue`-skips when it is unset, so CI is
unaffected:

```bash
mvn test -Dtest=Z42ForecastTests \
  -Dv2tofhir.localMessages=/path/to/izgw-transform/ehex-testing
```

The loader must split each file at the **second** `MSH` (these captures prepend the QBP request to
the RSP response) and tolerate `\r`-only line endings.

## Downstream consequence of Decisions 5–7

With `30982-3` on `forecastReason` and `59779-9` on `authority`, every forecast OBX's data reaches the
`ImmunizationRecommendation` itself. A plain `GET /ImmunizationRecommendation` is then self-contained
and the searchset filter dropping the `Observation` resources costs the caller nothing — no
`_include`, no `_revinclude`, and no `izgw-transform` change to let Observations through. That
non-change is deliberate and recorded in the companion downstream notes.

Two downstream defects were noted here while validating: included resources labelled
`search.mode = match` instead of `include`, and `ImmunizationRecommendation.patient` (1..1) dangling
because the searchset filter also removed the `Patient`. Both were unrelated to this change and both
have since been fixed downstream — a live Nevada Z42 response now returns the `Patient` as `include`
and reserves `match` for the requested type alone. Recorded as resolved so nobody re-opens them here.

## Follow-ups (out of scope, tracked here)

- **`supportingImmunization` linking.** `ImmunizationRecommendation.recommendation.supportingImmunization`
  references the `Immunization` history a forecast was computed from. The V2 Z42 message does not
  explicitly link forecast rows to specific history RXAs — the forecast block names a vaccine
  *type*, not an administered dose — so building it needs a heuristic (match forecast vaccine
  group to history `Immunization.vaccineCode`) and its own validation. Recommend
  `link-forecast-supporting-immunization`.

  Note this is **no longer needed to get the history into the bundle** — the transform's
  `include` marking already does that (see above). What it would add is the *link* saying which doses
  a given forecast was computed from. Measured obstacle: exact CVX matching links **zero** forecasts
  to doses on the live Nevada response, because the forecast names an unspecified-formulation or
  successor code while the dose names the product given — `115^Tdap, Adsorbed` forecast against an
  administered `09^Td (adult)`, and `140^Influenza, P-Free` against `88`/`150`. So this is a
  vocabulary problem (CVX group/successor relationships) before it is a mapping problem.
- **`59781-5^Dose Validity`** in evaluated-history groups is dropped (`DOSE_VALIDITY` case
  `break`s). R4 `Immunization` has no native slot for an evaluated dose-validity verdict; needs an
  extension decision. See Decision 6.
- **Duplicate representation.** Every OBX becomes an `Observation` *and*, where a dedicated element
  exists, is folded into `Immunization` / `ImmunizationRecommendation`. The V2-to-FHIR IG's
  `VXU_V04` map (row 12.6.1) targets `Observation[2]` with
  `Observation[2].partOf.reference = Immunization[1].id`, and comments "Some observations about the
  immunization may map to elements within the Immuniation resource **rather than** an independent
  Observation resource" — implying either/or, not both. Emitting both is lossless but redundant.
  Whether to suppress the Observation for fully-absorbed OBX codes is a library-wide decision
  affecting VXU and Z32 as well, so it is not made here.
- **`forecastStatus` value-set normalization** across IIS (LOINC answers vs. local codes).

### Pre-existing deviations found while auditing this change against the V2-to-FHIR IG

Audited against the IG CI build (v1.0.0, generated 2025-10-07,
`build.fhir.org/ig/HL7/v2-to-fhir`). None of these are caused by this change and none are fixed by
it. Each alters output for consumers beyond the eHealth Exchange work, so each needs its own
decision.

1. **`RXA-22` ignores its IG condition.** The IG maps `RXA-22 → Immunization.recorded` only
   `IF RXA-21 EQUALS "A"`. `RXAParser.setSystemEntryDateTime` (`RXAParser.java:345`) sets it
   unconditionally.
2. **Two segments write `Immunization.recorded`.** `ORC-9` (`ORCParser.java:241`) and `RXA-22`
   (`RXAParser.java:345`) are both IG mappings to the same element. ORC is parsed first, so RXA-22
   silently overwrites it. The IG does not say which should win.
3. **`RXA-9` has no handler.** The IG gives no target but comments "In the US, the CDC Immunization
   Implementation Guide would map this to Immunization.reportOrigin." `RXAParser` handles fields 3,
   5, 6, 7, 10, 11, 15–22, 27, 28 — not 9 — so `reportOrigin` is never populated. That field is what
   distinguishes `01^Historical Information` from an administered dose in CDC-profile messages.
4. **`30963-3^Vaccine Purchased With` is neither mapped nor linked.** It appears nowhere in
   `src/main` (`grep 30963-3` and `grep fundingSource` are both empty). R4
   `Immunization.fundingSource` is the right target — "Indicates the source of the vaccine actually
   administered. This may be different than the patient eligibility" — and it is distinct from
   `programEligibility`, which `64994-7` already populates. Because the code is not in `VisCode`,
   `redirect` resolves to `OTHER` and `setValue` returns before `linkObservation()`, so the
   `Observation` also gets no `partOf`. It is the one dose-level observation a consumer can neither
   read from the `Immunization` nor attribute to it.
   Note for accuracy: this target is **not** in the V2-to-FHIR IG. `fundingSource` does not appear in
   the RXA-to-Immunization map, and the IG has no OBX-to-Immunization map at all. The mapping is
   correct on R4 semantics and CDC IG intent, not on IG authority.
5. **`PV1Parser` emits a malformed extension URL.** `PV1Parser.java:124` concatenates
   `PathUtils.FHIR_EXT_PREFIX` (which already ends in `/`) with `"/iso21090-SC-coding"`, producing
   `http://hl7.org/fhir/StructureDefinition//iso21090-SC-coding`. One-character fix, but it changes
   output for every consumer of PV1 conversions.

Also worth recording from that audit, because it bounds how much of this library the IG actually
governs: the IG has **no** OBX-to-Immunization map, **no** OBX-to-ImmunizationRecommendation map,
**no** RXA-to-ImmunizationRecommendation map and **no RSP_K11 message map at all**. The whole
`ImmunizationRecommendation` layer and all OBX-to-`Immunization`-element folding
(`education`, `programEligibility`, `protocolApplied`) are hand-written beyond the IG. The IG's own
mapping guidelines page is marked "Informative" and states: "If your local implementation does need a
mapping you may add that locally and are encouraged to submit a JIRA to add your proposed mapping to
the v2-FHIR implementation guide formally."
