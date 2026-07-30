## ADDED Requirements

### Requirement: QAK-2 status maps to OperationOutcome issue severity and code

When parsing a `QAK` segment, the system SHALL create an `OperationOutcome.issue` whose `severity` and `code` are derived from the `QAK-2` query-response status value (HL7 v2 table 0208), and SHALL preserve the source `QAK-2` coding (system `v2-0208`) in `OperationOutcome.issue.details`.

The mapping SHALL be:

| QAK-2 | issue.severity | issue.code |
|-------|----------------|------------|
| `AE`  | `error`        | `invalid`        |
| `AR`  | `fatal`        | `processing`     |
| `TM`  | `warning`      | `multiple-matches` |
| `OK`  | `information`  | `informational`  |
| `NF`  | `information`  | `informational`  |
| any other / unrecognized non-empty code | `information` | `informational` |
| absent / empty code | `information` | `unknown` |

Code comparison SHALL be case-insensitive.

#### Scenario: TM (too much data found) maps to a warning
- **WHEN** a `QAK` segment with `QAK-2 = TM` is parsed (e.g. an `RSP^K11` where the query matched more records than the record limit and no `PID` segments are returned)
- **THEN** the resulting `OperationOutcome.issue` has `severity = warning` and `code = multiple-matches`
- **AND** `OperationOutcome.issue.details` still contains the `v2-0208` coding with code `TM`

#### Scenario: OK maps to informational
- **WHEN** a `QAK` segment with `QAK-2 = OK` is parsed
- **THEN** the resulting `OperationOutcome.issue` has `severity = information` and `code = informational`

#### Scenario: NF (no data found) maps to informational
- **WHEN** a `QAK` segment with `QAK-2 = NF` is parsed
- **THEN** the resulting `OperationOutcome.issue` has `severity = information` and `code = informational`

#### Scenario: AE (application error) maps to error
- **WHEN** a `QAK` segment with `QAK-2 = AE` is parsed
- **THEN** the resulting `OperationOutcome.issue` has `severity = error` and `code = invalid`

#### Scenario: AR (application reject) maps to fatal
- **WHEN** a `QAK` segment with `QAK-2 = AR` is parsed
- **THEN** the resulting `OperationOutcome.issue` has `severity = fatal` and `code = processing`

#### Scenario: Unrecognized status falls back to informational
- **WHEN** a `QAK` segment with a non-empty `QAK-2` value that is not in table 0208 is parsed
- **THEN** the resulting `OperationOutcome.issue` has `severity = information` and `code = informational`

#### Scenario: Missing status code falls back to unknown
- **WHEN** a `QAK` segment is parsed whose `QAK-2` has no code value
- **THEN** the resulting `OperationOutcome.issue` has `severity = information` and `code = unknown`
