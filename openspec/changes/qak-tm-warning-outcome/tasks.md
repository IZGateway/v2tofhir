## 1. Implementation

- [x] 1.1 In `gov.cdc.izgw.v2tofhir.segment.QAKParser#setIssueDetails(Coding)`, add a `case "TM":` branch immediately before the grouped `case "NF", "OK": default:` branch that sets `issue.setCode(IssueType.MULTIPLEMATCHES)` and `issue.setSeverity(IssueSeverity.WARNING)`, then `break`. Add a brief comment referencing HL7 table 0208 ("Too much data found").
- [x] 1.2 Confirm no new imports are needed (`IssueType` and `IssueSeverity` are already imported) and that the existing `AE`, `AR`, `NF`, `OK`, empty-code, and `default` branches are left unchanged.

## 2. Tests

- [x] 2.1 Add a `QAKParserTests` class under `src/test/java/test/gov/cdc/izgateway/v2tofhir/` (suffix `Tests` so Surefire runs it). Include `@author Audacious Inquiry` and use `@Slf4j` if logging is needed.
- [x] 2.2 Add a helper that parses a single `QAK` segment (build the segment, then `new MessageParser().createBundle(Collections.singleton(segment))`) and returns the first `OperationOutcome` from the bundle.
- [x] 2.3 Add a parameterized test covering the QAK-2 → (severity, code) mapping from the spec: `TM`→(`warning`,`multiple-matches`), `OK`→(`information`,`informational`), `NF`→(`information`,`informational`), `AE`→(`error`,`invalid`), `AR`→(`fatal`,`processing`), an unrecognized code (e.g. `ZZ`)→(`information`,`informational`), and an empty QAK-2 →(`information`,`unknown`).
- [x] 2.4 Assert that for `QAK-2 = TM` the `OperationOutcome.issue.details` still contains the `v2-0208` coding with code `TM` (details preservation).
- [x] 2.5 Run `mvn test -Dtest=QAKParserTests` and confirm all new assertions pass.

## 3. Documentation

- [x] 3.1 Update the JavaDoc on `QAKParser` / `setIssueDetails` to note the QAK-2 → OperationOutcome severity+code mapping, including the new `TM` → warning / multiple-matches entry.
- [ ] 3.2 (Cross-repo follow-up) Add a note to `izgw-transform/docs/fhir/rsp-to-fhir.md` documenting the QAK-2 (table 0208) → OperationOutcome mapping and the `TM` → `warning` / `multiple-matches` behavior, including that the TM response must remain a 2xx searchset Bundle containing the OperationOutcome (Query Connector's error path ignores non-2xx top-level OperationOutcomes). Track separately since it lives in the `izgw-transform` repository, not `v2tofhir`.

## 4. Validation

- [x] 4.1 Run `mvn test` (full suite) to confirm no regression in existing QAK/RSP/message conversion behavior.
- [x] 4.2 Run `openspec validate qak-tm-warning-outcome` and confirm the change is valid.
- [x] 4.3 Resolve the design Open Question — resolved 2026-07-29: a DIBBs Query Connector codebase review recommended `multiple-matches` (their existing `uncertainMatchError`/`NoCertainPatientMatch` flow is the natural sibling state, and neither code was previously handled). Task 1.1, the spec mapping table, and the doc note (3.2) were updated to `multiple-matches`; see design.md Decision 1.
