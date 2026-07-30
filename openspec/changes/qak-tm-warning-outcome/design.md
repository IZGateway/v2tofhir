## Context

`QAKParser.setIssueDetails(Coding details)` maps the `QAK-2` query-response status (HL7 v2 table 0208) onto a single `OperationOutcome.issue`. The current `switch` handles `AE` (error/invalid) and `AR` (fatal/processing) explicitly, then groups `NF`, `OK`, and `default` into one branch producing `information` / `informational`. A non-empty code that is absent produces `unknown` / `information`.

`TM` ("Too much data found") therefore lands in the `default` branch and is emitted as `information` / `informational` — identical to a successful `OK` or a legitimate `NF`. In the DIBBs immunization pilot (Query Connector → izgw-transform → IIS), an over-limit demographic search returns `RSP^K11` with `MSH-21 = Z33`, `QAK-2 = TM`, and no `PID` segments, so v2tofhir yields a Bundle with zero `Patient` resources and only an informational outcome. The UI cannot distinguish "too many matches" from a true no-match.

Constraints:
- Robustness (Postel's Law): `setIssueDetails` must not throw; it continues to populate `issue.details` from the source coding regardless of the status value.
- Minimize blast radius: `OK`/`NF`/`AE`/`AR`/fallback behavior must remain byte-for-byte identical.
- No new dependencies: the needed enum values already exist in the HAPI FHIR R4 in use.

## Goals / Non-Goals

**Goals:**
- Emit `severity = warning` and `code = too-costly` for `QAK-2 = TM`.
- Preserve the `v2-0208 / TM` coding in `OperationOutcome.issue.details` (unchanged).
- Keep all other QAK-2 mappings identical.
- Add unit-test coverage asserting the TM mapping and that the other statuses are unaffected.

**Non-Goals:**
- Changing how `RSP^K11` bundles are assembled, or synthesizing placeholder `Patient` resources for the TM case.
- Emitting the downstream "no certain match" `$match` OperationOutcome — that is a separate izgw-transform concern; this change only makes TM detectable.
- Reworking the other QAK-2 mappings or adding a general table-0208 → OperationOutcome concept map.

## Decisions

### Decision 1: Use `IssueType.TOOCOSTLY` (`too-costly`) rather than `IssueType.MULTIPLEMATCHES` (`multiple-matches`)
Both exist in HAPI R4. `too-costly` is the semantically closest FHIR issue type for "the query would return/matched more than the allowed limit — narrow it," which is exactly what `TM` (record limit exceeded, RCP-2) means. `multiple-matches` is oriented toward identity-resolution/`$match` "more than one candidate" semantics; that concept belongs to the downstream `$match` flow, not the raw QAK-2 translation. Choosing `too-costly` keeps the v2tofhir layer aligned with the transport-level meaning and leaves `multiple-matches` free for the transform layer.
- *Alternative considered:* `multiple-matches`. Rejected here to avoid overloading v2tofhir with `$match` semantics; the spec's mapping table can be revisited if downstream consumers prefer it.

### Decision 2: Add `TM` as a dedicated `case` immediately before the `NF, OK` / `default` group
Placing `case "TM":` ahead of the grouped `case "NF", "OK": default:` branch is the smallest possible edit, preserves the existing fall-through structure, and keeps the read order aligned with severity (error → fatal → warning → information). No refactor of the switch is warranted for a single added code.
- *Alternative considered:* extracting the whole mapping into a lookup table/concept map. Rejected as over-engineering for one added code and out of scope (would touch the unchanged branches).

### Decision 3: Severity `warning` (not `error`)
`TM` is not a processing failure — the query succeeded but returned too much data. `warning` correctly signals an actionable, non-fatal condition ("narrow your search") distinct from `AE`/`AR` errors and from informational `OK`/`NF`.

### Decision 4: Keep `issue.details` population unchanged
The source `QAK-2` `Coding` is already added to `issue.details` before the switch runs, satisfying the spec's "preserve `v2-0208 / TM` coding" requirement with no additional code.

## Risks / Trade-offs

- [A downstream consumer expects `multiple-matches` instead of `too-costly`] → The mapping lives in one `case` and is documented in the spec table; switching the enum is a one-line change. The doc note in `rsp-to-fhir.md` records the chosen code so consumers align.
- [Behavioral regression in the other QAK-2 branches] → Mitigated by adding an isolated `case` (no edits to existing branches) and by unit tests that assert `OK`/`NF`/`AE`/`AR` outcomes remain unchanged.
- [Case-sensitivity of the incoming code] → The switch already upper-cases via `toUpperCase()`, so `tm`/`Tm` are handled; the new case uses the upper-case `"TM"` literal consistently.

## Migration Plan

No data migration or config change. This is an additive code change plus tests. Rollback is reverting the single `case` (and its test). No API surface changes; consumers that previously saw `information`/`informational` for `TM` will now see `warning`/`too-costly` — the intended behavior.

## Open Questions

- Confirm with the izgw-transform / DIBBs side that `too-costly` (vs `multiple-matches`) is the code they will key off for the "too many matches" message. If they require `multiple-matches`, update Decision 1, the spec mapping table, and the doc note accordingly before implementation.
