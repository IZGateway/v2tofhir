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

So `30956-7` is treated as one thing: the evaluated/forecast vaccine type. It is removed from
the VIS education mapping. Left as a `ponytail:` ceiling — if an IIS ever sends `30956-7` inside
a genuine VIS block, gate the education routing on a VIS code (`69764-9`/`29768-9`/`29769-7`)
appearing under the same OBX-4 sub-id.

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

**Non-Goals**
- No use of RXA-9 (information source) or RXA-20 (completion status) as type discriminators —
  `RXA-5 == 998` is the CDC-documented signal.
- No normalization of `forecastStatus` codings. Nevada sends LOINC answers (`LA13423-1^Overdue`,
  `LA13422-3^On Schedule`, `LA13424-9^Too Old`); Alaska sends a local system
  (`P^Past Due`, `U^Up to Date` in `99002`). Both pass through as received.
- No `supportingImmunization` linking (see follow-up).
- No `forecastReason` mapping from `30982-3^Reason Code` (see follow-up).

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
  `FhirController.adjustIdentifiers` hashes `patient|null|null`. With one resource per message
  there is no collision, but `ORCParser.addOrderIdentifier` still copies ORC-3 (sentinel
  `9999^NV0000` / `9999^AKA`) onto the resource on the forecast path — mirroring the history
  path — so the deterministic id is stable and non-null.

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

### Accepted: the searchset filter drops evaluated history

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

**Consequence**: today the Z42 bug labels every entry `ImmunizationRecommendation`, so a
`GET .../ImmunizationRecommendation` query marks them all `match` — including administered doses
that the two-query design intends to be served by the separate `/Immunization` (Z34) path. After
the fix, evaluated-history rows become `Immunization` and the transform correctly excludes them
from the recommendation searchset. **Accepted as by-design**: the history is delivered via the
`/Immunization` → Z34 → Z32 path. The current bug is fighting the design by stuffing administered
doses into the recommendation result.

Positive side effects of the fix on the transform, no code change needed:

- History rows now carry the real ORC-3 identifier (`NV0000|41348935`, `AKA|2722530.32.20250730`),
  so `adjustIdentifiers` mints stable, distinct ids for them.
- `64994-7^Vaccine funding program eligibility` now reaches `Immunization.programEligibility`; on
  the recommendation path it was silently discarded.

If a single query returning both types is ever desired, that is an `izgw-transform` concern (a
combined operation, or `_include`/`_revinclude` once `supportingImmunization` exists) — see the
follow-up below.

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

## Follow-ups (out of scope, tracked here)

- **`supportingImmunization` linking.** `ImmunizationRecommendation.recommendation.supportingImmunization`
  references the `Immunization` history a forecast was computed from. The V2 Z42 message does not
  explicitly link forecast rows to specific history RXAs — the forecast block names a vaccine
  *type*, not an administered dose — so building it needs a heuristic (match forecast vaccine
  group to history `Immunization.vaccineCode`) and its own validation. With it, a client could
  `GET .../ImmunizationRecommendation?_include=…` and have `izgw-transform` pull the history in as
  `INCLUDE`d resources, resolving the drop described above without two queries. Recommend
  `link-forecast-supporting-immunization`.
- **`forecastReason` from `30982-3^Reason Code`.** Nevada sends free text
  ("Patient has exceeded the maximum age") for `Too Old` forecasts; the `WHY_INVALID` case
  currently `break`s and the text is lost. R4 has
  `recommendation.forecastReason` / `.description` as targets.
- **`59781-5^Dose Validity`** in evaluated-history groups is likewise dropped (`DOSE_VALIDITY`
  case `break`s). R4 `Immunization` has no native slot; needs an extension decision.
- **`forecastStatus` value-set normalization** across IIS (LOINC answers vs. local codes).
