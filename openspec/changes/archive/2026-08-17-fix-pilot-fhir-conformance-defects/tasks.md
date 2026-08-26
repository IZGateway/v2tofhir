## 1. Baseline

- [x] 1.1 Run `mvn clean test` on the unmodified branch and record the passing test count, so any later movement in `Location` counts is attributable to the fix rather than to new coverage — baseline: 24517 tests, 0 failures, 3 skipped, BUILD SUCCESS
- [x] 1.2 Confirm no existing test asserts on `Location.physicalType`, `Location.description` or `Location.identifier`, or counts bundle entries in a way the fix will move; note any that do — no test in `src/test` references `Location` at all

## 2. Location physicalType fix

- [x] 2.1 In `DatatypeConverter`, promote the three parallel locals in `toLocationFromComposite` to class-level constants `LOC_COMPONENTS`, `LOC_CODES`, `LOC_DISPLAYS`, keeping the existing component order (Bed, Room, Point of Care, Floor, Building, Facility) and the existing `si`/"Site" display
- [x] 2.2 Change the loop to read `ParserUtils.getComponent(comp, LOC_COMPONENTS[i])` instead of `getComponent(comp, i)`
- [x] 2.3 Set the Point of Care entry's code and display to `null`, and skip `setPhysicalType` when the code is `null`, so Point of Care yields a named `Location` with no `physicalType` element
- [x] 2.4 Update the method Javadoc so it documents the mapping as implemented and cites the IG's `datatype-pl-to-location` ConceptMap as its source
- [x] 2.5 Verify PL-5 and PL-6 are no longer emitted as nested `Location` resources and are still carried once each on `operationalStatus` and `type`
- [x] 2.6 Verify PL-7 Building and PL-8 Floor are now read, using the reviewed Nevada `RXA-11` as the worked example
- [x] 2.7 Move the `setDescription` and `addIdentifier` calls out of `toLocationFromComposite` into `toLocation`'s `case "PL"`, so only a `PL` reads components 9 and 10
- [x] 2.8 In `case "PL"`, set `description` from component index 8 (PL-9) and `identifier` from index 9 (PL-10), replacing the existing `setDescription(ParserUtils.toString(comp, 9))` that overwrote the description with the identifier
- [x] 2.9 Verify an `LA1` and an `LA2` now produce no `description` and no `identifier` from components 9 and 10, and still produce the same `Location.address` as before

## 3. Location tests

- [x] 3.1 Add a test converting a person-location composite that populates Point of Care, Facility and Building, asserting the codes, the names, and the `partOf` chain order
- [x] 3.2 Add a test asserting a Point of Care `Location` has no `physicalType` element, empty or otherwise, and carries no code from `location-physical-type`
- [x] 3.3 Add a test asserting empty Room, Bed and Floor components produce no `Location` carrying `ro`, `bd` or `lvl`
- [x] 3.4 Add a test asserting Location Status and Person Location Type reach `operationalStatus` and `type` exactly once and produce no additional `Location`
- [x] 3.5 Add a test asserting a `PL` carries PL-9 in `description` and PL-10 in `identifier`, and that both are absent when those components are empty
- [x] 3.6 Add a test asserting an `LA2` `RXA-11` puts its street address, city, state and postal code on `Location.address` and produces no `description` and no `identifier`
- [x] 3.7 Add a test asserting an `LA1` produces its `Location.address` from LA1-9 and no `description`

## 4. Verification and version

- [x] 4.1 Run `mvn clean install` and compare the test count and results against the 1.1 baseline, accounting for every difference — 24529 tests, 0 failures, 3 skipped: baseline 24517 plus the 12 new Location tests
- [x] 4.2 Convert the reviewed Nevada capture and confirm each `RXA-11` yields `IZGATEWAYART` with no `physicalType`, `IZ GATEWAY AART TEST` with `bu`, and `IZGATEWAY-AART` with `si`, that no `bd` or `lvl` remains, and that no `Location` carries `330 C ST SW UNIT 7` as a `description` — confirmed, and the address stays on the Point of Care Location
- [x] 4.3 Settle the version the breaking `Location` corrections ship under — pom moved to `2.5.2-SNAPSHOT` for pilot testing, to be released as `2.6.0`. `RELEASE_NOTES.md` is written by the release workflow, not by hand, so it is not touched here
- [x] 4.4 Move `izgw-transform`'s `v2tofhir` dependency from `2.5.1-SNAPSHOT` to `2.5.2-SNAPSHOT` so the service picks this up (separate repo) — `izgw-transform/pom.xml:111` now pins `2.5.2-SNAPSHOT`, verified end to end by converting the Nevada Z42 through the service
