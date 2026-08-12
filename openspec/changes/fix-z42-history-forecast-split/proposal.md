## Why

When an IIS returns an RSP_K11 message conforming to CDC profile **Z42** ("Return Evaluated
History and Forecast"), the message deliberately mixes two kinds of ORC/RXA group: **evaluated
history** (real doses actually administered, e.g. `RXA-5 = 37^yellow fever live^CVX`) and
**forecast** recommendations (placeholder `RXA-5 = 998^no vaccine administered^CVX`). Per FHIR
R4, the former are `Immunization` resources (records of administration, incl. historical) and
the latter are `ImmunizationRecommendation` resources (a forecast — no dose given).

Two defects, both of which have to be fixed together for the Z42 path to be usable:

1. **Every RXA becomes an `ImmunizationRecommendation`**, including administered doses.
   Downstream consumers lose the patient's actual immunization history and receive administered
   doses mislabeled as forecasts.

2. **All forecasts in one RXA group collapse into a single recommendation.** IIS implementations
   split forecasts two different ways, and only one of them works today:

   | Shape | Forecast layout | Observed |
   |---|---|---|
   | **A** | one 998 RXA, N forecast blocks keyed by repeated `OBX-3 = 30956-7^Vaccine Type^LN` | Nevada / WebIZ; CDC IG sample at `messages.txt:1039` |
   | **B** | N separate 998 RXAs, one `30956-7` block each | Alaska / VacTrAK |

   Shape B happens to produce N resources. Shape A produces **one** resource holding one
   `recommendation` component with `vaccineCode = 998^no vaccine administered`, an arbitrary
   `forecastStatus` (whichever block came last), and every block's dates mashed into one
   `dateCriterion` list. Measured against a 16-forecast Nevada response: 16 forecasts → 1
   unusable resource, 27 merged `dateCriterion`.

   Separately, `recommendation.vaccineCode` is *always* the `998` placeholder from RXA-5 even in
   shape B, because the `FORECAST_VACCINE_CODE` branch in `OBXParser` is unreachable — `VisCode`
   declares LOINC `30956-7` twice and the VIS variant wins on enum order.

Fixing only (1) leaves `GET /ImmunizationRecommendation` returning one useless resource for
shape A and N `vaccineCode = 998` resources for shape B. (2) is therefore in scope, not a
follow-up.

## What Changes

- Decide the FHIR resource type **per ORC/RXA group** for Z42 messages, using the
  CDC-documented discriminator `RXA-5 == 998` (no vaccine administered):
  - `RXA-5 == 998` → forecast → `ImmunizationRecommendation`
  - any other CVX code → evaluated history → `Immunization`
- `IzDetail.checkForImmunization()` stops hard-setting `hasImmunizationRecommendation = true`
  for the `CDCPHINVS#Z42` profile; it delegates to the existing (currently dead)
  per-RXA discriminator `IzDetail.checkRXA()`.
- **Carry all forecasts in a Z42 message on a single `ImmunizationRecommendation` resource,
  with one `recommendation` component per `OBX-3 = 30956-7`**, taking each component's
  `vaccineCode` from the OBX-5 value rather than from the `998` placeholder in RXA-5. This is
  FHIR R4's intended model — the resource is "a patient's point-in-time set of recommendations",
  and every per-forecast field (`vaccineCode`, `forecastStatus`, `dateCriterion`) lives on the
  `recommendation` component. It normalizes shapes A and B to the same output and removes the
  downstream identifier-collision problem entirely (one resource, no colliding ids).
- Stop routing `OBX-3 = 30956-7` in an evaluated-**history** group to
  `Immunization.education` (a Vaccine Information Statement slot). In Z42 history that OBX is
  the *evaluated antigen*, not a VIS vaccine type.
- No change to Z22 (VXU_V04) or Z32 (RSP_K11 history-only) — those remain pure `Immunization`,
  and `30956-7` there keeps its VIS meaning.
- Keep the `ImmunizationRecommendation` FHIR R4-valid: populate the required `date` from
  RXA-22 when present, else from the MSH-7 message timestamp (Nevada truncates forecast RXAs
  at RXA-20, so RXA-22 is absent there); stop mapping forecast RXA-3 to a `dateCriterion`
  (it currently creates a criterion with no `code`, violating `dateCriterion.code` 1..1);
  carry the ORC-3 identifier on the resource.
- **BREAKING** (output-shape): a Z42 message that previously produced N
  `ImmunizationRecommendation` entries now produces a mix of `Immunization` entries and
  exactly one `ImmunizationRecommendation` holding one `recommendation` component per
  forecast.

### Measured effect

| Message | Before | After |
|---|---|---|
| `messages.txt:997` (CDC IG mixed) | 0 IZ, 1 IZR | 3 IZ, 1 IZR (1 component) |
| `messages.txt:1039` (CDC IG, shape A ×3) | 0 IZ, 1 IZR (merged) | 0 IZ, 1 IZR (3 components) |
| Nevada response (shape A ×16) | 0 IZ, 1 IZR (merged) | 2 IZ, 1 IZR (16 components) |
| Alaska response (shape B ×10) | 0 IZ, 13 IZR | 3 IZ, 1 IZR (10 components) |

## Capabilities

### New Capabilities
- `immunization-history-forecast-conversion`: how RSP_K11 responses (and VXU) map ORC/RXA
  groups to FHIR `Immunization` vs `ImmunizationRecommendation`, keyed by message profile
  (Z22/Z32/Z42) and, for the mixed Z42 case, by the per-RXA `RXA-5 == 998` discriminator; plus
  how multiple forecasts are split into separate `recommendation` components on a single
  `ImmunizationRecommendation` resource.

### Modified Capabilities
<!-- none: no existing spec covers RXA→Immunization/Recommendation resource selection -->

## Impact

- **Code**:
  - `segment/IzDetail.java` — `checkForImmunization()` Z42 branch delegates to `checkRXA()`
    (un-dead it); the `ImmunizationRecommendation` resource is shared across forecast groups,
    and new forecast-block bookkeeping gives each `30956-7` its own `recommendation` component.
  - `segment/OBXParser.java` — adopt/add the per-block `recommendation` component on
    `OBX-3 = 30956-7`; set its `vaccineCode` from OBX-5; stop treating `30956-7` as a VIS
    vaccine type in a history group.
  - `segment/RXAParser.java` — on the forecast path, drop `addVaccineCode(RXA-5)` (the `998`
    placeholder) and the RXA-3 `dateCriterion` write (creates a code-less criterion,
    invalid per R4 `dateCriterion.code` 1..1); RXA-22 sets the resource `date`, with the
    MSH-7 message timestamp as the default when RXA-22 is absent.
  - No signature changes to `ORCParser` — its `@ComesFrom` setters already branch on
    `hasImmunization()` / `hasRecommendation()`, so history fields (identifier, recorded,
    performer) populate automatically once a group is classed as `Immunization`.
- **Tests**: assertions on the two existing CDC-IG Z42 messages in `src/test/resources/messages.txt`
  (via the already-present but unused `@` FHIRPath assertion mechanism), plus one new
  hand-written shape-B message. Real IIS responses are exercised by an **opt-in, skipped-by-default**
  test that reads messages from a local directory — no vendor data is committed. Existing
  `testMessageConversion` only converts+logs (no resource-type assertions), so no current
  fixtures break.
- **Robustness**: honors Postel's Law — no new throwing paths; an ORC with no following RXA
  yields neither resource, and a forecast group with no `30956-7` still yields one
  `recommendation` component without a `vaccineCode` (tolerated violation of R4 invariant
  `imr-1`; garbage in, best-effort out — never observed in real IIS responses).
- **Downstream (`izgw-transform`)**:
  - `v2tofhir` is pinned **directly** in `izgw-transform/pom.xml` (not in `izgw-bom`), so the
    version bump is an `izgw-transform` commit.
  - `GET /ImmunizationRecommendation` will stop returning evaluated history (see design —
    accepted, by the two-query model).
  - `ImmunizationRecommendation` currently carries no identifier, so
    `FhirController.adjustIdentifiers` hashes a `patient|null|null` key for it. With the
    single-resource model there is nothing to collide with, but the resource still gets the
    ORC-3 identifier (sentinel `9999^NV0000` / `9999^AKA`) so the downstream deterministic id
    is stable and non-null.
