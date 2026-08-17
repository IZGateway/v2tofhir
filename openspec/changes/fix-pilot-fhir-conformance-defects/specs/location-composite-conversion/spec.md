## Purpose

Defines how the HL7 V2 person-location composites (`PL`, `LA1` and `LA2`) become FHIR R4 `Location`
resources — which component names each `Location`, which physical-location code each component
carries, how several populated components nest into a containment chain, and which components are
not physical locations at all — so that a consumer can trust that a `Location.physicalType` it
receives was actually asserted by the sending system. It also fixes which components 9 and beyond
supply, since `PL`, `LA1` and `LA2` agree only through component 8.

## ADDED Requirements

### Requirement: Physical location components carry the code the v2-to-FHIR IG assigns them

The converter SHALL derive `Location.physicalType` from the component that the HL7 v2-to-FHIR
Implementation Guide's `datatype-pl-to-location` ConceptMap assigns to that code, using the
`http://terminology.hl7.org/CodeSystem/location-physical-type` code system:

| Component | `physicalType` code |
| --- | --- |
| `PL`/`LA1`/`LA2`-2 Room | `ro` |
| `PL`/`LA1`/`LA2`-3 Bed | `bd` |
| `PL`/`LA1`/`LA2`-4 Facility | `si` |
| `PL`/`LA1`/`LA2`-7 Building | `bu` |
| `PL`/`LA1`/`LA2`-8 Floor | `lvl` |

The converter SHALL NOT emit a `physicalType` code for a component the message left empty, and
SHALL NOT emit a code the message does not support. A `Location` the converter produces from one of
these components SHALL take its `name` from that same component.

#### Scenario: Facility and building are labelled from their own components

- **WHEN** an `RXA-11` carries
  `IZGATEWAYART^^^IZGATEWAY-AART^^^IZ GATEWAY AART TEST^^330 C ST SW UNIT 7^^UNKNOWN^VA^20201`
- **THEN** a `Location` named `IZ GATEWAY AART TEST` carries `physicalType` `bu` (Building)
- **AND** a `Location` named `IZGATEWAY-AART` carries `physicalType` `si`

#### Scenario: An empty component produces no Location

- **WHEN** a person-location composite leaves Room, Bed and Floor empty
- **THEN** the converted bundle contains no `Location` carrying `physicalType` `ro`, `bd` or `lvl`
  derived from that composite

#### Scenario: Bed and Room are labelled from their own components

- **WHEN** a person-location composite populates component 2 with `RM101` and component 3 with `B2`
- **THEN** a `Location` named `RM101` carries `physicalType` `ro` and a `Location` named `B2` carries
  `physicalType` `bd`

### Requirement: Point of Care carries no physical type code

FHIR R4's `location-physical-type` code system has no concept for a point of care, and the
v2-to-FHIR IG leaves the code for `PL`/`LA1`/`LA2`-1 Point of Care unresolved. The converter SHALL
produce a `Location` named from the Point of Care component with no `physicalType` element at all,
rather than substituting an approximate code.

#### Scenario: Point of Care is named but unlabelled

- **WHEN** an `RXA-11` carries `IZGATEWAYART` in component 1
- **THEN** the converted bundle contains a `Location` named `IZGATEWAYART`
- **AND** that `Location` has no `physicalType` element, empty or otherwise

#### Scenario: No physical type code is invented for a point of care

- **WHEN** a person-location composite populates only the Point of Care component
- **THEN** no `Location` derived from that composite carries `physicalType` `wa`, `bd` or any other
  code from `location-physical-type`

### Requirement: Multiple populated components nest from most specific to least

When a person-location composite populates more than one physical-location component, the converter
SHALL produce one `Location` per populated component and SHALL relate them through `Location.partOf`,
running from the most specific component to the least specific in the order Bed, Room, Point of Care,
Floor, Building, Facility. Each `Location` the converter produces from these components SHALL have
`mode` `instance`.

#### Scenario: Point of care nests inside building inside facility

- **WHEN** an `RXA-11` populates Point of Care `IZGATEWAYART`, Facility `IZGATEWAY-AART` and Building
  `IZ GATEWAY AART TEST`
- **THEN** the `Location` named `IZGATEWAYART` is `partOf` the `Location` named
  `IZ GATEWAY AART TEST`, which is `partOf` the `Location` named `IZGATEWAY-AART`

#### Scenario: A single populated component produces no containment chain

- **WHEN** a person-location composite populates only the Facility component
- **THEN** exactly one `Location` is produced from that composite and it has no `partOf`

### Requirement: Location status and person location type are not physical locations

The `PL`/`LA1`/`LA2`-5 Location Status and `PL`/`LA1`/`LA2`-6 Person Location Type components describe
a location's state and kind, not a place within a containment hierarchy. The converter SHALL carry
Location Status on `Location.operationalStatus` and Person Location Type on `Location.type`, each
exactly once, and SHALL NOT produce a separate `Location` resource or a `physicalType` code from
either component.

#### Scenario: Location status is not duplicated as a nested Location

- **WHEN** a person-location composite populates the Location Status component
- **THEN** the value appears on `Location.operationalStatus`
- **AND** no additional `Location` resource is produced from that component

#### Scenario: Person location type is not duplicated as a nested Location

- **WHEN** a person-location composite populates the Person Location Type component
- **THEN** the value appears on `Location.type`
- **AND** no additional `Location` resource is produced from that component

### Requirement: Components 9 and beyond are read according to the composite's own datatype

`PL`, `LA1` and `LA2` agree on components 1 through 8 and diverge from component 9 onward. The
converter SHALL read those components according to the datatype of the field being converted, and
SHALL NOT carry an address component as `Location.description` or as `Location.identifier`:

| Datatype | Component | Element |
| --- | --- | --- |
| `PL` | PL-9 Location Description | `Location.description` |
| `PL` | PL-10 Comprehensive Location Identifier | `Location.identifier` |
| `LA1` | LA1-9 Address (`AD`) | `Location.address` |
| `LA2` | LA2-9 Street Address through LA2-16 Other Geographic Designation | `Location.address` |

`LA1` and `LA2` have no Location Description component and no Comprehensive Location Identifier
component, so a `Location` converted from either SHALL have no `description` and no `identifier`
derived from a component. The `Location` a `PL` converts to SHALL take `description` from PL-9 —
not from PL-10, whose value is the identifier.

#### Scenario: A PL carries its description and identifier

- **WHEN** a `PL` field carries `EMERGENCY ROOM ENTRANCE` in PL-9 and `4707` in PL-10
- **THEN** the resulting `Location` has `description` `EMERGENCY ROOM ENTRANCE`
- **AND** the resulting `Location` has an `identifier` with value `4707`

#### Scenario: An LA2 street address is an address, not a description

- **WHEN** an `RXA-11` carries
  `IZGATEWAYART^^^IZGATEWAY-AART^^^IZ GATEWAY AART TEST^^330 C ST SW UNIT 7^^UNKNOWN^VA^20201`
- **THEN** the resulting `Location` has `address` with line `330 C ST SW UNIT 7`, city `UNKNOWN`,
  state `VA` and postal code `20201`
- **AND** no `Location` produced from that field has a `description`
- **AND** no `Location` produced from that field has an `identifier`

#### Scenario: Absent PL description and identifier are omitted

- **WHEN** a `PL` field leaves PL-9 and PL-10 empty
- **THEN** the resulting `Location` has no `description` and no `identifier`
