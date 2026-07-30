## Why

When an IIS finds more candidate patients than the query's record limit (RCP-2), it returns an `RSP^K11` with `QAK-2 = TM` ("Too much data found", HL7 table 0208) and no `PID` segments. Today `QAKParser` has no `TM` case, so it falls through to `default` and emits `severity=information` / `code=informational` — indistinguishable from a successful `OK`/`NF`. Downstream consumers (the DIBBs immunization pilot: Query Connector → izgw-transform → IIS) therefore render "too many matches — narrow your search" identically to a genuine "No Records Found," which is misleading. A `warning` outcome makes the result actionable and semantically correct.

## What Changes

- Add an explicit `TM` case to `QAKParser.setIssueDetails()` (before the `NF`/`OK`/`default` group) that maps `QAK-2 = TM` to `OperationOutcome.issue.severity = warning` and `issue.code = multiple-matches` (HAPI R4 `IssueType.MULTIPLEMATCHES`).
- Preserve the existing `v2-0208 / TM` `Coding` in `OperationOutcome.issue.details` (unchanged behavior — the `details` CodeableConcept is still populated from QAK-2).
- Leave the existing `OK`, `NF`, `AE`, and `AR` mappings unchanged (no behavioral regression).
- Add a unit test asserting the `TM` → `warning` / `multiple-matches` mapping (v2tofhir currently has no QAK status→severity assertion).

Not breaking: this only adds a new branch for a code that previously fell through to `default`; all other inputs behave exactly as before.

## Capabilities

### New Capabilities
- `qak-query-response-status`: Defines how `QAKParser` maps the HL7 v2 `QAK-2` query-response status (table 0208) to `OperationOutcome.issue` severity and code, including the newly-added `TM` ("too much data found") → `warning` / `multiple-matches` mapping and the preservation of the source coding in `issue.details`.

### Modified Capabilities
<!-- No existing spec files under openspec/specs/ cover QAK behavior, so this is introduced as a new capability rather than a delta. -->

## Impact

- **Code:** `gov.cdc.izgw.v2tofhir.segment.QAKParser#setIssueDetails()` — one added `case "TM"` branch. Uses HAPI FHIR R4 `OperationOutcome.IssueType`/`IssueSeverity` (already imported).
- **Tests:** New assertion(s) in the QAK/RSP parsing test coverage (test class suffixed `Tests`) verifying `TM` → `warning` / `multiple-matches` and that `OK`/`NF`/`AE`/`AR` are unaffected.
- **Docs:** A note in `izgw-transform/docs/fhir/rsp-to-fhir.md` describing the `QAK-2` code → OperationOutcome mapping (external repo; tracked as a follow-up in tasks).
- **Dependencies:** None. No new libraries; relies on existing HAPI FHIR R4 enums.
- **Downstream:** Enables izgw-transform / DIBBs to distinguish "too many matches" from "no records found" and underpins the planned `$match`-based flow.
