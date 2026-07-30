## Context

`QAKParser.setIssueDetails(Coding details)` maps the `QAK-2` query-response status (HL7 v2 table 0208) onto a single `OperationOutcome.issue`. The current `switch` handles `AE` (error/invalid) and `AR` (fatal/processing) explicitly, then groups `NF`, `OK`, and `default` into one branch producing `information` / `informational`. An absent or empty code produces `unknown` / `information`.

`TM` ("Too much data found") therefore lands in the `default` branch and is emitted as `information` / `informational` — identical to a successful `OK` or a legitimate `NF`. In the DIBBs immunization pilot (Query Connector → izgw-transform → IIS), an over-limit demographic search returns `RSP^K11` with `MSH-21 = Z33`, `QAK-2 = TM`, and no `PID` segments, so v2tofhir yields a Bundle with zero `Patient` resources and only an informational outcome. The UI cannot distinguish "too many matches" from a true no-match.

Constraints:
- Robustness (Postel's Law): `setIssueDetails` must not throw; it continues to populate `issue.details` from the source coding regardless of the status value.
- Minimize blast radius: `OK`/`NF`/`AE`/`AR`/fallback behavior must remain byte-for-byte identical.
- No new dependencies: the needed enum values already exist in the HAPI FHIR R4 in use.

## Goals / Non-Goals

**Goals:**
- Emit `severity = warning` and `code = multiple-matches` for `QAK-2 = TM`.
- Preserve the `v2-0208 / TM` coding in `OperationOutcome.issue.details` (unchanged).
- Keep all other QAK-2 mappings identical.
- Add unit-test coverage asserting the TM mapping and that the other statuses are unaffected.

**Non-Goals:**
- Changing how `RSP^K11` bundles are assembled, or synthesizing placeholder `Patient` resources for the TM case.
- Emitting the downstream "no certain match" `$match` OperationOutcome — that is a separate izgw-transform concern; this change only makes TM detectable.
- Reworking the other QAK-2 mappings or adding a general table-0208 → OperationOutcome concept map.

## Decisions

### Decision 1: Use `IssueType.MULTIPLEMATCHES` (`multiple-matches`) rather than `IssueType.TOOCOSTLY` (`too-costly`)
Both exist in the HAPI R4 in use (verified in `org.hl7.fhir.r4-6.9.7`). Resolved 2026-07-29 by investigating the DIBBs Query Connector codebase (the downstream consumer): neither code is handled there today — its discovery path drops OperationOutcome bundle entries wholesale in `processFhirResponse` — so a new UI branch is needed on their side either way, but `multiple-matches` fits their codebase better. Query Connector already models the "multiple candidates, can't narrow to one" empty state (`uncertainMatchError` → `NoCertainPatientMatch`) in its `$match` flow, currently detected by sniffing HAPI's "did not find a certain match" text; `multiple-matches` is the code a cleanup of that text-sniff would converge on, letting one branch eventually serve both flows. Semantically, R4 defines `too-costly` as "query too expensive/abandoned" (the server's reason), while `multiple-matches` states the actionable condition ("more than one record matched — narrow the search"), which is what `TM` means in this context.
- *Alternative considered:* `too-costly`. Rejected after downstream confirmation: it describes the server-side reason rather than the user-actionable condition, and has no anchor in the consumer's existing UI model.

### Decision 2: Add `TM` as a dedicated `case` immediately before the `NF, OK` / `default` group
Placing `case "TM":` ahead of the grouped `case "NF", "OK": default:` branch is the smallest possible edit, preserves the existing fall-through structure, and keeps the read order aligned with severity (error → fatal → warning → information). No refactor of the switch is warranted for a single added code.
- *Alternative considered:* extracting the whole mapping into a lookup table/concept map. Rejected as over-engineering for one added code and out of scope (would touch the unchanged branches).

### Decision 3: Severity `warning` (not `error`)
`TM` is not a processing failure — the query succeeded but returned too much data. `warning` correctly signals an actionable, non-fatal condition ("narrow your search") distinct from `AE`/`AR` errors and from informational `OK`/`NF`. Note: Query Connector currently discards OperationOutcomes regardless of severity, so the severity change alone does not alter its UI; it matters because the detection branch DIBBs plans to add keys on `severity === "warning" && code === "multiple-matches"`, and because `warning` is the semantically correct signal for any other consumer.

### Decision 4: Keep `issue.details` population unchanged
The source `QAK-2` `Coding` is already added to `issue.details` before the switch runs, satisfying the spec's "preserve `v2-0208 / TM` coding" requirement with no additional code.

## Risks / Trade-offs

- [Detection depends on the OperationOutcome riding inside the 2xx searchset Bundle] → Query Connector's discovery path only inspects bundle entries; a non-2xx top-level OperationOutcome hits its error logger and still renders "No Records Found". The QAK-produced OperationOutcome is already added to the message Bundle by v2tofhir, and Query Connector's handling matches whether or not `entry.search.mode = "outcome"` is set — but izgw-transform must keep returning the TM response as a 2xx Bundle containing the OperationOutcome, not as a bare error. Recorded in the `rsp-to-fhir.md` doc note.
- [Behavioral regression in the other QAK-2 branches] → Mitigated by adding an isolated `case` (no edits to existing branches) and by unit tests that assert `OK`/`NF`/`AE`/`AR` outcomes remain unchanged.
- [Case-sensitivity of the incoming code] → The switch already upper-cases via `toUpperCase()`, so `tm`/`Tm` are handled; the new case uses the upper-case `"TM"` literal consistently.

## Migration Plan

No data migration or config change. This is an additive code change plus tests. Rollback is reverting the single `case` (and its test). No API surface changes; consumers that previously saw `information`/`informational` for `TM` will now see `warning`/`multiple-matches` — the intended behavior.

## Open Questions

- ~~Confirm with the izgw-transform / DIBBs side which code they will key off for the "too many matches" message.~~ **Resolved 2026-07-29:** a review of the DIBBs Query Connector codebase recommended `multiple-matches` (see Decision 1); Decision 1, the spec mapping table, and the tasks were updated accordingly. Query Connector will add a branch detecting zero Patients + an OperationOutcome issue with `severity = warning` / `code = multiple-matches` in its `patientDiscoveryQuery` path, paralleling its existing `uncertainMatchError` flow.
