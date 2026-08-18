## Why

Independent review of a real Z42 (`RSP^K11`, evaluated history + forecast) response from the Nevada IIS test system,
for the eHealth Exchange pilot, reported "extra data not returned in the Z42: physicalType: BD Bed, lvl Level".
That is a real defect, it is not specific to Z42, and it is pre-existing — it affects every message carrying a
person-location field.

`DatatypeConverter.toLocationFromComposite` transcribes the v2-to-FHIR IG's `datatype-pl-to-location` ConceptMap
into `int[] c = { 2, 1, 0, 7, 6, 3 }`, and the method's own Javadoc restates the same mapping — but the loop reads
the composite with the loop counter instead of `c[i]`, so the array is never used. In the reviewed bundle that puts
`bd`/Bed on `IZGATEWAYART` (LA2-1 Point of Care) and `lvl`/Level on `IZGATEWAY-AART` (LA2-4 Facility), while
`IZ GATEWAY AART TEST` (LA2-7 Building) is never read at all. Both codes the reviewer saw were invented by the
converter. PL-5 and PL-6 are also emitted twice — once as a mislabelled nested `Location`, once correctly as
`operationalStatus` and `type`.

The same method reads components 9 and 10 as Location Description and Comprehensive Location Identifier for all
three datatypes, which holds only for `PL`. `LA1`-9 is an Address and `LA2`-9 onward are the address components, so
an `LA1` or `LA2` duplicates its address text into `Location.description` — visible in the reviewed bundle as
`"description": "330 C ST SW UNIT 7"`. For `PL`, `toLocation`'s own `case "PL"` then overwrites `description` with
component 10, the identifier, so PL-9 Location Description never reaches FHIR at all.

## What Changes

- **BREAKING** `Location.physicalType` is corrected to the component mapping the IG specifies:
  `bd` from PL-3, `ro` from PL-2, `lvl` from PL-8, `bu` from PL-7, `si` from PL-4. Existing consumers see
  different `physicalType` codes and different `Location.name` values on the same input, because both are set
  from the component now read correctly. PL-5 and PL-6 stop being emitted as nested `Location` resources and are
  carried only as `operationalStatus` and `type`.
- PL-1 Point of Care has no standard `physicalType` code — the IG specifies an extension there rather than a
  code from `location-physical-type`. The current `wa`/Ward is an approximation with no basis in the IG, so no
  code is emitted for that component.
- **BREAKING** `Location.description` and `Location.identifier` are read per datatype: from PL-9 and PL-10 for a
  `PL`, and from neither for an `LA1` or `LA2`, whose components 9 and beyond are the address they already
  populate. A `PL` now carries PL-9 in `description` instead of PL-10's identifier text, and an `LA1`/`LA2` no
  longer carries a `description` that duplicated its address.

## Out of Scope

`ImmunizationEvaluation` is **not** produced. The review asked for `59781-5^Dose Validity^LN` and
`30982-3^Reason for invalid dose^LN` on an `ImmunizationEvaluation`, and an earlier draft of this change planned
exactly that. Our FHIR lead ruled it out: the resource is maturity level 0 in R4, the HL7 v2-to-FHIR spec does not
map it, and it needs substantial work in the Public Health Workgroup. It will not be supported by v2-to-FHIR until
R6 progresses, and IZ Gateway targets R4 only. All `ImmunizationEvaluation` work — the resource, `doseStatus`,
`doseStatusReason`, `targetDisease` from `30956-7`, and
`ImmunizationRecommendation.recommendation.supportingImmunization` — is therefore dropped from this change rather
than deferred to a later one.

Two review items need no converter change:

- `59781-5` and `30982-3` are already carried in the converted bundle as `Observation` resources, with
  `Observation.code` holding the LOINC code, the value in `Observation.value[x]`, `subject` referencing the
  `Patient` and `partOf` referencing the group's `Immunization`. They are absent from the reviewed bundle because
  the Transformation Service's FHIR searchset filter removes resources it has not marked, not because the
  converter drops them. Making them visible is an `izgw-transform` filter decision.
- `30973-2^Dose Number in Series^LN` is present in the Nevada sample and already maps to
  `Immunization.protocolApplied.doseNumber[x]` — the reviewed bundle shows `"doseNumberPositiveInt": 1` on both
  history groups.

## Capabilities

### New Capabilities
- `location-composite-conversion`: conversion of the HL7 v2 `PL`, `LA1` and `LA2` composites to `Location`,
  covering which component supplies `name`, the `physicalType` code per component, the `partOf` nesting of
  multiple populated components, which components are carried as `operationalStatus` and `type` instead, and
  which element components 9 and beyond supply for each of the three datatypes. No existing spec covers
  `Location`.

## Impact

- `converter/DatatypeConverter.toLocationFromComposite` and its `case "PL"` caller — the indexing fix and the
  per-datatype reading of components 9 and 10. Reached by every `PL`, `LA1` and `LA2` conversion, so the blast
  radius is every message with a person-location field, not only immunization messages.
- New `Location` coverage. No test asserts on any part of `Location` today, so coverage is added rather than
  adjusted.
- `Normalizer` dedupes Locations on `physicalType` + `name` + `mode`, so the number of `Location` resources in a
  bundle can change for the same input.
- Consumers of the emitted bundle see changed `Location` content: different `physicalType` codes, a `Location`
  per component that the message actually populated, and no `description` on an `LA1`/`LA2`.
- No new dependency, no configuration change, and no change to any immunization resource.
