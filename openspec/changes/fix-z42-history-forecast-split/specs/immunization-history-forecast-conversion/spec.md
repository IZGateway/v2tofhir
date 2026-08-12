## Purpose

Defines how HL7 V2 immunization messages (VXU_V04 and RSP_K11 query responses) map each ORC/RXA
group to a FHIR R4 `Immunization` (a dose administered) or `ImmunizationRecommendation` (a
forecast), and how multiple forecasts carried in one message are split into separate resources, so
that a mixed "evaluated history and forecast" response preserves both the patient's real
immunization history and each individual forecast.

## ADDED Requirements

### Requirement: Resource type selection by message profile

The converter SHALL select the FHIR resource produced for ORC/RXA groups based on the message
profile declared in MSH-21, as reflected on `Bundle.meta.profile`.

- A message conforming to profile `Z22` (VXU_V04) SHALL produce `Immunization` resources.
- A message conforming to profile `Z32` (RSP_K11, immunization history) SHALL produce
  `Immunization` resources.
- A message conforming to profile `Z42` (RSP_K11, evaluated history and forecast) SHALL select the
  resource type per ORC/RXA group (see "Per-RXA history vs forecast selection").
- When no immunization profile is present but the message is a VXU, the converter SHALL produce
  `Immunization` resources.

#### Scenario: VXU produces Immunization

- **WHEN** a VXU_V04 message (profile Z22) containing an administered RXA is converted
- **THEN** the RXA is represented as an `Immunization` resource

#### Scenario: History-only response produces Immunization

- **WHEN** an RSP_K11 message conforming to profile Z32 is converted
- **THEN** each ORC/RXA group is represented as an `Immunization` resource

### Requirement: Per-RXA history vs forecast selection for Z42

For a message conforming to profile `Z42`, the converter SHALL decide the resource type
independently for each ORC/RXA group using the administered vaccine code in RXA-5:

- WHEN the RXA-5 vaccine code identifier equals `998` ("no vaccine administered"), the group SHALL
  be represented as one or more `ImmunizationRecommendation` resources (a forecast group).
- OTHERWISE (any other CVX code, i.e. a real administered/historical dose), the group SHALL be
  represented as an `Immunization`.

A single Z42 message therefore MAY yield a mixture of `Immunization` and
`ImmunizationRecommendation` resources.

#### Scenario: Historical dose in a Z42 response becomes Immunization

- **WHEN** a Z42 response contains an ORC/RXA group whose RXA-5 is a real CVX code
  (e.g. `37^yellow fever live^CVX`), reported as historical information
- **THEN** that group is represented as an `Immunization` resource
- **AND** administration details present in the RXA (e.g. vaccine code, dose quantity, lot number,
  manufacturer, completion status) are populated on the `Immunization`
- **AND** the ORC-3 filler order number is populated as an `Immunization.identifier`

#### Scenario: Forecast entry in a Z42 response becomes ImmunizationRecommendation

- **WHEN** a Z42 response contains an ORC/RXA group whose RXA-5 is `998^no vaccine administered^CVX`
  with forecast OBX segments (e.g. date due, series status)
- **THEN** that group is represented as `ImmunizationRecommendation` resources

#### Scenario: Mixed Z42 response yields both resource types

- **WHEN** a Z42 response contains three historical ORC/RXA groups (real CVX codes) followed by one
  forecast ORC/RXA group (RXA-5 == 998) carrying a single forecast
- **THEN** the resulting bundle contains three `Immunization` resources and one
  `ImmunizationRecommendation` resource

### Requirement: One ImmunizationRecommendation per forecast vaccine group

Within a forecast group (RXA-5 == 998), the converter SHALL treat each
`OBX-3 = 30956-7^Vaccine Type^LN` observation as the start of a distinct forecast, and SHALL
produce one `ImmunizationRecommendation` resource per such observation.

- The OBX-5 value of the `30956-7` observation SHALL be that resource's
  `recommendation.vaccineCode`. The `998` placeholder in RXA-5 SHALL NOT be used as a
  `recommendation.vaccineCode`.
- Forecast observations following a `30956-7` (e.g. `30980-7` date due, `30981-5` earliest date,
  `59777-3` latest date, `59778-1` overdue date, `59783-1` series status, `30973-2` dose number,
  `59782-3` doses in series) SHALL apply to the most recently started forecast, until the next
  `30956-7` or the end of the group.
- OBX-4 (Observation Sub-ID) SHALL NOT be used to delimit forecasts. It is not reliably
  incremented across IIS implementations, and evaluated-history groups use it for unrelated
  observations.
- Each `ImmunizationRecommendation` produced for a forecast group SHALL carry an `identifier` that
  is unique within the bundle and derived from the group's ORC-3 order number together with the
  forecast vaccine code, so that downstream deterministic identifier assignment does not collide.

This requirement makes the two observed IIS layouts produce equivalent output: one where a single
998 RXA carries every forecast, and one where each forecast has its own 998 RXA.

#### Scenario: Many forecasts under a single forecast RXA

- **WHEN** a Z42 response contains one ORC/RXA group with RXA-5 == 998 followed by sixteen
  `30956-7^Vaccine Type^LN` observations, each with its own date and series-status observations
- **THEN** the bundle contains sixteen `ImmunizationRecommendation` resources
- **AND** each carries the `vaccineCode` from its own `30956-7` observation
- **AND** each carries only the date criteria and series status that followed its own `30956-7`

#### Scenario: One forecast per forecast RXA

- **WHEN** a Z42 response contains ten ORC/RXA groups, each with RXA-5 == 998 and a single
  `30956-7^Vaccine Type^LN` observation
- **THEN** the bundle contains ten `ImmunizationRecommendation` resources
- **AND** each carries the `vaccineCode` from its own `30956-7` observation

#### Scenario: OBX-4 sub-id is not treated as a forecast delimiter

- **WHEN** a forecast group's observations all carry the same OBX-4 Observation Sub-ID but include
  more than one `30956-7^Vaccine Type^LN`
- **THEN** one `ImmunizationRecommendation` is produced per `30956-7`, not one per sub-id

#### Scenario: Forecast status is passed through as received

- **WHEN** a forecast's `59783-1` series-status observation uses a LOINC answer code
  (e.g. `LA13423-1^Overdue^LN`) or an IIS-local code (e.g. `P^Past Due^99002`)
- **THEN** the code is placed in `recommendation.forecastStatus` as received, without translation

### Requirement: Vaccine type in an evaluated-history group is not a VIS document

The `30956-7^Vaccine Type^LN` observation appearing in an evaluated-**history** ORC/RXA group
reports the antigen the dose was evaluated against. The converter SHALL NOT record it as Vaccine
Information Statement material on `Immunization.education`.

Vaccine Information Statement observations (`69764-9` document type, `29768-9` publication date,
`29769-7` presentation date) SHALL continue to populate `Immunization.education`.

#### Scenario: Evaluated antigen does not create an education element

- **WHEN** a Z42 evaluated-history group for a `09^Td (adult)^CVX` dose is followed by
  `OBX|1|CE|30956-7^Vaccine Type^LN|1|107^DTaP, UF^CVX`
- **THEN** the resulting `Immunization` has no `education` element created from that observation

#### Scenario: Genuine VIS observations still populate education

- **WHEN** an ORC/RXA group is followed by `29769-7` (VIS presentation date) and `29768-9`
  (VIS publication date) observations
- **THEN** the resulting `Immunization` has an `education` element with the corresponding
  presentation and publication dates

#### Scenario: Vaccine funding eligibility reaches the Immunization

- **WHEN** an evaluated-history group is followed by a
  `64994-7^Vaccine funding program eligibility category^LN` observation
- **THEN** the value is populated on `Immunization.programEligibility`

### Requirement: Robust handling of incomplete groups

Resource selection and forecast splitting SHALL follow the library's robustness principle and never
throw during conversion.

#### Scenario: ORC with no following RXA

- **WHEN** an ORC segment has no following RXA segment in its group
- **THEN** the converter produces neither an `Immunization` nor an `ImmunizationRecommendation` for
  that group, and conversion continues without error

#### Scenario: Forecast group with no vaccine type observation

- **WHEN** an ORC/RXA group has RXA-5 == 998 but no `30956-7^Vaccine Type^LN` observation
- **THEN** a single `ImmunizationRecommendation` is produced for the group with no
  `recommendation.vaccineCode`, and conversion continues without error

#### Scenario: Forecast observation before any vaccine type observation

- **WHEN** a forecast group's first observation is a date or series-status observation appearing
  before any `30956-7^Vaccine Type^LN`
- **THEN** it is applied to a single `ImmunizationRecommendation` for the group, and conversion
  continues without error
