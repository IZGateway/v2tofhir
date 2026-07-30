## 1. Implementation

- [ ] 1.1 In `gov.cdc.izgw.v2tofhir.segment.QAKParser#setIssueDetails(Coding)`, add a `case "TM":` branch immediately before the grouped `case "NF", "OK": default:` branch that sets `issue.setCode(IssueType.TOOCOSTLY)` and `issue.setSeverity(IssueSeverity.WARNING)`, then `break`. Add a brief comment referencing HL7 table 0208 ("Too much data found").
- [ ] 1.2 Confirm no new imports are needed (`IssueType` and `IssueSeverity` are already imported) and that the existing `AE`, `AR`, `NF`, `OK`, empty-code, and `default` branches are left unchanged.

## 2. Tests

- [ ] 2.1 Add a `QAKParserTests` class under `src/test/java/test/gov/cdc/izgateway/v2tofhir/` (suffix `Tests` so Surefire runs it). Include `@author Audacious Inquiry` and use `@Slf4j` if logging is needed.
- [ ] 2.2 Add a helper that parses a single `QAK` segment (build the segment, then `new MessageParser().createBundle(Collections.singleton(segment))`) and returns the first `OperationOutcome` from the bundle.
- [ ] 2.3 Add a parameterized test covering the QAK-2 → (severity, code) mapping from the spec: `TM`→(`warning`,`too-costly`), `OK`→(`information`,`informational`), `NF`→(`information`,`informational`), `AE`→(`error`,`invalid`), `AR`→(`fatal`,`processing`), an unrecognized code (e.g. `ZZ`)→(`information`,`informational`), and an empty QAK-2 →(`information`,`unknown`).
- [ ] 2.4 Assert that for `QAK-2 = TM` the `OperationOutcome.issue.details` still contains the `v2-0208` coding with code `TM` (details preservation).
- [ ] 2.5 Run `mvn test -Dtest=QAKParserTests` and confirm all new assertions pass.

## 3. Documentation

- [ ] 3.1 Update the JavaDoc on `QAKParser` / `setIssueDetails` to note the QAK-2 → OperationOutcome severity+code mapping, including the new `TM` → warning / too-costly entry.
- [ ] 3.2 (Cross-repo follow-up) Add a note to `izgw-transform/docs/fhir/rsp-to-fhir.md` documenting the QAK-2 (table 0208) → OperationOutcome mapping and the `TM` → `warning` / `too-costly` behavior. Track separately since it lives in the `izgw-transform` repository, not `v2tofhir`.

## 4. Validation

- [ ] 4.1 Run `mvn test` (full suite) to confirm no regression in existing QAK/RSP/message conversion behavior.
- [ ] 4.2 Run `openspec validate qak-tm-warning-outcome` and confirm the change is valid.
- [ ] 4.3 Resolve the design Open Question — confirm with izgw-transform/DIBBs that `too-costly` (vs `multiple-matches`) is the code they will key off; if they require `multiple-matches`, update task 1.1, the spec mapping table, and the doc note before archiving.
