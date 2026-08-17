## Purpose

Defines how HL7 V2 immunization messages (VXU_V04 and RSP_K11 query responses) map each ORC/RXA
group to a FHIR R4 `Immunization` (a dose administered) or `ImmunizationRecommendation` (a
forecast), and how multiple forecasts carried in one message are split into separate
`recommendation` components of a single `ImmunizationRecommendation` resource, so that a mixed
"evaluated history and forecast" response preserves both the patient's real immunization history
and each individual forecast.

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
  contribute `recommendation` component(s) to the message's single `ImmunizationRecommendation`
  resource (a forecast group).
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
- **THEN** that group is represented as `recommendation` component(s) of an
  `ImmunizationRecommendation` resource

#### Scenario: Mixed Z42 response yields both resource types

- **WHEN** a Z42 response contains three historical ORC/RXA groups (real CVX codes) followed by one
  forecast ORC/RXA group (RXA-5 == 998) carrying a single forecast
- **THEN** the resulting bundle contains three `Immunization` resources and one
  `ImmunizationRecommendation` resource

### Requirement: One recommendation component per forecast vaccine group

All forecast groups in a Z42 message SHALL contribute to a single `ImmunizationRecommendation`
resource (FHIR R4 defines the resource as a patient's point-in-time set of recommendations).
Within a forecast group (RXA-5 == 998), the converter SHALL treat each
`OBX-3 = 30956-7^Vaccine Type^LN` observation as the start of a distinct forecast, and SHALL
produce one `recommendation` component per such observation.

- The OBX-5 value of the `30956-7` observation SHALL be that component's
  `vaccineCode`. The `998` placeholder in RXA-5 SHALL NOT be used as a
  `recommendation.vaccineCode`.
- Forecast observations following a `30956-7` (e.g. `30980-7` date due, `30981-5` earliest date,
  `59777-3` latest date, `59778-1` overdue date, `59783-1` series status, `30973-2` dose number,
  `59782-3` doses in series) SHALL apply to the most recently started component, until the next
  `30956-7` or the end of the group.
- OBX-4 (Observation Sub-ID) SHALL NOT be used to delimit forecasts. It is not reliably
  incremented across IIS implementations, and evaluated-history groups use it for unrelated
  observations.
- The converter SHALL NOT produce a `dateCriterion` without a `code` (R4 requires
  `dateCriterion.code` 1..1). In particular, forecast RXA-3 (the forecast-generation
  timestamp) SHALL NOT be mapped to a `dateCriterion`.

This requirement makes the two observed IIS layouts produce equivalent output: one where a single
998 RXA carries every forecast, and one where each forecast has its own 998 RXA.

#### Scenario: Many forecasts under a single forecast RXA

- **WHEN** a Z42 response contains one ORC/RXA group with RXA-5 == 998 followed by sixteen
  `30956-7^Vaccine Type^LN` observations, each with its own date and series-status observations
- **THEN** the bundle contains one `ImmunizationRecommendation` resource with sixteen
  `recommendation` components
- **AND** each component carries the `vaccineCode` from its own `30956-7` observation
- **AND** each component carries only the date criteria and series status that followed its own
  `30956-7`

#### Scenario: One forecast per forecast RXA

- **WHEN** a Z42 response contains ten ORC/RXA groups, each with RXA-5 == 998 and a single
  `30956-7^Vaccine Type^LN` observation
- **THEN** the bundle contains one `ImmunizationRecommendation` resource with ten
  `recommendation` components
- **AND** each component carries the `vaccineCode` from its own `30956-7` observation

#### Scenario: OBX-4 sub-id is not treated as a forecast delimiter

- **WHEN** a forecast group's observations all carry the same OBX-4 Observation Sub-ID but include
  more than one `30956-7^Vaccine Type^LN`
- **THEN** one `recommendation` component is produced per `30956-7`, not one per sub-id

#### Scenario: Forecast status is passed through as received

- **WHEN** a forecast's `59783-1` series-status observation uses a LOINC answer code
  (e.g. `LA13423-1^Overdue^LN`) or an IIS-local code (e.g. `P^Past Due^99002`)
- **THEN** the code is placed in `recommendation.forecastStatus` as received, without translation

### Requirement: The ImmunizationRecommendation satisfies R4 required elements

The single `ImmunizationRecommendation` produced for a Z42 message SHALL populate:

- `date` (required 1..1): from RXA-22 when present; otherwise from the MSH-7 message
  date/time. (Some IIS truncate forecast RXAs before RXA-22.)
- `identifier`: the forecast group's ORC-3 filler order number, so downstream deterministic
  identifier assignment hashes a non-null, stable key.
- `recommendation.forecastStatus` (required 1..1): from the block's `59783-1` series-status
  observation, passed through as received. When the input omits `59783-1`, the component is
  emitted without a `forecastStatus` (tolerated invalid input; conversion never throws).

#### Scenario: Forecast RXA truncated before RXA-22

- **WHEN** a Z42 forecast group's RXA ends at RXA-20 (no RXA-22 system entry date)
- **THEN** the `ImmunizationRecommendation.date` is populated from the MSH-7 message date/time

#### Scenario: RXA-22 supplies the recommendation date

- **WHEN** a Z42 forecast group's RXA carries RXA-22
- **THEN** the `ImmunizationRecommendation.date` is populated from RXA-22

### Requirement: Vaccine type in a Z42 message is not a VIS document

In a message conforming to profile `Z42`, the `30956-7^Vaccine Type^LN` observation reports the
antigen a dose was evaluated against (history group) or the antigen being forecast (forecast group).
The converter SHALL NOT record it as Vaccine Information Statement material on
`Immunization.education`.

For a message NOT conforming to `Z42` — a VXU, a history-only response, or any other profile — the
converter SHALL continue to record `30956-7` as it did previously, as an
`iso21090-SC-coding` extension on `Immunization.education.documentType`. No observed message of those
kinds carries `30956-7`, but the behaviour is preserved because this library serves consumers whose
message content is not known here.

Vaccine Information Statement observations (`69764-9` document type, `29768-9` publication date,
`29769-7` presentation date) SHALL continue to populate `Immunization.education` for every profile.

#### Scenario: Evaluated antigen does not create an education element

- **WHEN** a Z42 evaluated-history group for a `09^Td (adult)^CVX` dose is followed by
  `OBX|1|CE|30956-7^Vaccine Type^LN|1|107^DTaP, UF^CVX`
- **THEN** the resulting `Immunization` has no `education` element created from that observation

#### Scenario: Vaccine type outside a Z42 keeps its VIS reading

- **WHEN** a VXU contains a VIS block (`69764-9`, `29768-9`, `29769-7`) followed by
  `OBX|4|CE|30956-7^vaccine type^LN|1|110^DTaP-HepB-IPV^CVX`
- **THEN** the resulting `Immunization` has one `education` element whose `documentType` carries an
  `iso21090-SC-coding` extension holding that vaccine type

#### Scenario: Genuine VIS observations still populate education

- **WHEN** an ORC/RXA group is followed by `29769-7` (VIS presentation date) and `29768-9`
  (VIS publication date) observations
- **THEN** the resulting `Immunization` has an `education` element with the corresponding
  presentation and publication dates

#### Scenario: Evaluated antigen creates no education element even with no VIS present

- **WHEN** an evaluated-history group carries `30956-7` and no Vaccine Information Statement
  observation at all
- **THEN** the resulting `Immunization` has no `education` element

#### Scenario: Vaccine funding eligibility reaches the Immunization

- **WHEN** an evaluated-history group is followed by a
  `64994-7^Vaccine funding program eligibility category^LN` observation
- **THEN** the value is populated on `Immunization.programEligibility`

### Requirement: A forecast observation carries no reference to the recommendation

The converter SHALL NOT place an `ImmunizationRecommendation` reference in `Observation.partOf`. FHIR
R4 restricts `Observation.partOf` to `MedicationAdministration`, `MedicationDispense`,
`MedicationStatement`, `Procedure`, `Immunization` and `ImagingStudy`.

The converter SHALL NOT populate
`ImmunizationRecommendation.recommendation.supportingPatientInformation` from forecast observations
either. Each forecast observation's content is already carried by the `recommendation` component, so
such a reference would identify a duplicate, and it would not resolve for any consumer that filters
the bundle down to the requested resource type.

An `Observation` produced from a **recognized** immunization observation code in an evaluated-history
group SHALL use `Observation.partOf` referencing the `Immunization`, which R4 permits. Recognized
codes are those the converter maps: the Vaccine Information Statement codes, `64994-7`, `30956-7`,
`59779-9`, `30973-2`, `59782-3`, `59781-5` and the forecast codes.

An observation carrying any other code is still produced as an `Observation`, but currently gets no
`partOf`, because an unrecognized code returns before the linking step. This spec deliberately states
no requirement for that case and asserts no scenario over it. `30963-3^Vaccine Purchased With` is the
one observed instance, and it is logged as a pre-existing deviation to be fixed (design, deviation 4:
map it to `Immunization.fundingSource` and link it), so pinning today's behaviour in a test would
cement the defect.

#### Scenario: Forecast observation is unlinked

- **WHEN** a forecast group's observations follow a `30956-7^Vaccine Type^LN`
- **THEN** each resulting `Observation` has no `partOf`
- **AND** no `recommendation` component has a `supportingPatientInformation`

#### Scenario: Every reference the ImmunizationRecommendation emits is resolvable in the bundle

- **WHEN** a Z42 response is converted
- **THEN** the `ImmunizationRecommendation` references only resources present in the bundle

An `Observation` produced from any OBX SHALL carry `Observation.subject` referencing the message's
`Patient`, as the V2-to-FHIR IG requires (`Observation[2].subject.reference=Patient[1].id`). This is
the only path by which a forecast observation can be attributed to a patient, since it carries no
`partOf`.

#### Scenario: Every observation is attributable to the patient

- **WHEN** any message containing OBX segments is converted
- **THEN** every resulting `Observation` has `subject` referencing the `Patient`

#### Scenario: History observation for a recognized code keeps partOf

- **WHEN** an evaluated-history group is followed by an observation whose code the converter maps
  (e.g. `64994-7^Vaccine funding program eligibility category^LN`)
- **THEN** the resulting `Observation` has `partOf` referencing the `Immunization`

### Requirement: Forecast reason and schedule authority are preserved

- The converter SHALL map `OBX-3 = 30982-3^Reason Code^LN` in a forecast group to
  `recommendation.forecastReason` on the block's component. When the value is free text, it SHALL be
  carried as the `CodeableConcept` text with no coding.
- The converter SHALL map `OBX-3 = 59779-9^Immunization Schedule Used^LN` to the publishing
  authority: `ImmunizationRecommendation.authority` in a forecast group, and
  `Immunization.protocolApplied.authority` in an evaluated-history group. Both are
  `Reference(Organization)`, and the referenced `Organization` SHALL take its name from the
  observation value.

#### Scenario: Too Old forecast carries its reason

- **WHEN** a forecast block has `59783-1` series status `LA13424-9^Too Old^LN` and
  `OBX|n|ST|30982-3^Reason Code^LN|k|Patient has exceeded the maximum age`
- **THEN** that component's `forecastReason` carries the text "Patient has exceeded the maximum age"

#### Scenario: Schedule used becomes the recommendation authority

- **WHEN** a forecast group contains `OBX|n|CE|59779-9^Immunization Schedule Used^LN|k|VXC16^ACIP^CDCPHINVS`
- **THEN** `ImmunizationRecommendation.authority` references an `Organization` named for that value

### Requirement: Dose number and series size reach an administered dose

The converter SHALL map, in an evaluated-history group, `OBX-3 = 30973-2^Dose Number in Series^LN` to
`Immunization.protocolApplied.doseNumber[x]` and `OBX-3 = 59782-3^Number of Doses in Series^LN` to
`Immunization.protocolApplied.seriesDoses[x]`, on a single `protocolApplied` element shared with the
`59779-9` authority.

`Immunization.protocolApplied.doseNumber[x]` is required 1..1. The converter SHALL NOT synthesize a
value to satisfy it; a group supplying only `59782-3` or only `59779-9` yields a `protocolApplied`
without a `doseNumber`, a tolerated violation for incomplete input.

#### Scenario: Historical dose carries its position in the series

- **WHEN** an evaluated-history group contains `30973-2` with value 1 and `59782-3` with value 3
- **THEN** the `Immunization` has one `protocolApplied` with `doseNumber` 1 and `seriesDoses` 3

### Requirement: Only Vaccine Information Statement codes create an education element

The converter SHALL create or select an `Immunization.education` element only when handling
`69764-9` (document type), `29768-9` (publication date) or `29769-7` (presentation date).

No other observation — including `30956-7` (evaluated antigen), `59781-5` (dose validity) and the
forecast codes — SHALL cause an `education` element to be created, including as a side effect of
looking one up.

#### Scenario: Dose validity in a history group creates no education element

- **WHEN** an evaluated-history group with no Vaccine Information Statement observations is followed
  by `OBX|n|ID|59781-5^Dose Validity^LN|k|Y`
- **THEN** the resulting `Immunization` has no `education` element, empty or otherwise

### Requirement: Robust handling of incomplete groups

Resource selection and forecast splitting SHALL follow the library's robustness principle and never
throw during conversion.

#### Scenario: Z42 ORC with no following RXA

- **WHEN** a Z42 response contains an ORC segment with no following RXA segment in its group
- **THEN** the converter produces neither an `Immunization` nor an `ImmunizationRecommendation` for
  that group, and conversion continues without error

The per-group discriminator only runs for Z42, so this does not extend to other profiles. A Z22, Z32
or VXU message decides from the profile alone, and an ORC with no following RXA there still yields an
`Immunization`. That is existing behaviour and is unchanged.

#### Scenario: Forecast group with no vaccine type observation

- **WHEN** an ORC/RXA group has RXA-5 == 998 but no `30956-7^Vaccine Type^LN` observation
- **THEN** a single `recommendation` component is produced for the group with no `vaccineCode`
  (a tolerated violation of R4 invariant `imr-1` for malformed input), and conversion
  continues without error

#### Scenario: Forecast observation before any vaccine type observation

- **WHEN** a forecast group's first observation is a date or series-status observation appearing
  before any `30956-7^Vaccine Type^LN`
- **THEN** it is applied to the group's initial `recommendation` component, and conversion
  continues without error
