## Context

See `proposal.md` — Why, and the spec delta under `specs/location-composite-conversion/` for the behaviour being
contracted.

`DatatypeConverter.toLocationFromComposite` holds the IG's component mapping in a local `int[] c` and then never
reads it — the loop indexes the composite with its own counter. The array and the method's Javadoc agree with the
IG's `datatype-pl-to-location` ConceptMap, so the mapping is already correct and only the access is wrong.
Everything else in the method (the `partOf` nesting, `mode`, the `operationalStatus` / `type` assignments) is
sound.

The reviewed Nevada bundle is the worked example throughout: `RXA-11` is
`IZGATEWAYART^^^IZGATEWAY-AART^^^IZ GATEWAY AART TEST^^330 C ST SW UNIT 7^^UNKNOWN^VA^20201`, an `LA2`, and it
converted to `IZGATEWAYART` labelled `bd`/Bed, `IZGATEWAY-AART` labelled `lvl`/Level, no Building at all, and
`description` = `330 C ST SW UNIT 7`.

## Goals / Non-Goals

**Goals:**

- Make `Location.physicalType` a fact the sending system asserted, never one the converter inferred.
- Preserve the IG mapping as the readable source of truth in the code, rather than encoding it
  implicitly in loop order.
- Read components 9 and beyond according to the datatype of the field being converted.

**Non-Goals:**

- Re-designing `Location` conversion beyond these two defects. The nesting behaviour, `mode`, address handling
  and the `operationalStatus` / `type` assignments are unchanged.
- Producing `ImmunizationEvaluation`, or moving `59781-5`, `30982-3`, `30956-7` or `30973-2` anywhere. See
  proposal, Out of Scope.
- Introducing the terminology package's injectable abstractions into this call site. It uses the static
  `Systems` utility today; this change stays on that path so it does not collide with the in-flight
  `vocabulary-management-integration` work.

## Decisions

### Index the composite by the mapping array, and let a component opt out of a code

Replace the three parallel locals with class-level constants and index by the mapping array:

```java
private static final int[]    LOC_COMPONENTS = {    2,     1,     0,      7,          6,      3 };
private static final String[] LOC_CODES      = { "bd",  "ro",  null,  "lvl",       "bu",   "si" };
private static final String[] LOC_DISPLAYS   = { "Bed", "Room", null, "Level", "Building", "Site" };
```

The component order is Keith's original and is left alone: it runs Bed, Room, Point of Care, Floor,
Building, Facility, which is most specific to least, so the `partOf` chain it builds nests correctly.
The `si` display stays "Site", the code system's own display for that code, rather than being changed
to "Facility" to match the source component — the value set's display is the conventional choice and
changing it buys nothing.

A `null` code means "produce the named `Location`, emit no `physicalType`". That is how Point of Care
is handled: FHIR R4's `location-physical-type` has no point-of-care concept, and the IG leaves that
cell unresolved.

*Alternatives considered.* Renumbering the code arrays so the existing `i` indexing becomes correct
would work but discards the IG mapping as documentation — the next reader would have no way to see
which component each code came from. Defining our own point-of-care extension follows the IG's stated
intent, but the IG never finished defining it, so we would be inventing a canonical and repeating the
mistake this same review flagged in our use of the core `originalText` extension. Keeping `wa`/Ward is
rejected outright: it is the invented-data problem the review reported, merely relabelled.

### Components 9 and beyond move out of the shared helper

`toLocationFromComposite` is shared by `PL`, `LA1` and `LA2`, which agree only through component 8.
The helper currently sets `description` from component 9 and `identifier` from component 10 for all
three, which is right only for `PL`:

- `LA1`-9 is an `AD` Address, and `LA2`-9 through -16 are the address components. So today an `LA1` or
  `LA2` gets its address text stuffed into `description` as well as into `Location.address`, which
  `AddressParser` already parses from component 9 onward. `LA1` has no component 10 at all.
- For `PL`, `DatatypeConverter.toLocation`'s own `case "PL"` then overwrites `description` with
  component 10 — the `EI` identifier — so PL-9 Location Description never reaches FHIR, and the
  identifier text lands in `description` beside the parsed `identifier`.

So the `description` and `identifier` assignments move out of the helper and into the `PL` case, and the
redundant `setDescription` line in that case goes away. `LA1` and `LA2` keep only their address, which
they already get. This is a second defect in the same method, found while contracting its behaviour, and
it is fixed here because the spec for this capability has to state which component supplies
`description` either way.

## Risks / Trade-offs

- **`Location` output changes for every existing consumer, not just the pilot.** `PL`, `LA1` and `LA2`
  are reached by any message with a person-location field. → The proposal marks this BREAKING, and the
  version it ships under says so. The change is a correction toward the IG,
  so no consumer has a conformant reason to depend on the old values.
- **`Normalizer` dedupes Locations on `physicalType` + `name` + `mode`, so the number of `Location`
  resources in a bundle can change.** No test asserts on `Location` at all and there are no
  expected-output fixtures, but a test that counts bundle entries would move. → Run the full suite as its
  own task before writing new tests, so any movement is attributed to the fix rather than to new coverage.
- **`Location.description` changes for `PL`, `LA1` and `LA2` alike.** `PL` gains PL-9 where it used to
  carry PL-10's identifier text; `LA1` and `LA2` lose a `description` that duplicated their address. →
  Part of the same breaking `Location` correction, so it ships under the same version.

## Migration Plan

No data migration and no configuration change. The library is a build-time dependency, so the change
ships by version.

The work is carried on `2.5.2-SNAPSHOT` for pilot testing and released as `2.6.0`, because the `Location`
corrections are breaking and the project states it follows SemVer. `izgw-transform` pins
`2.5.1-SNAPSHOT`, so it has to move to `2.5.2-SNAPSHOT` to pick this up.

`RELEASE_NOTES.md` is not edited by hand in this repository — a GitHub Actions workflow writes it when a
release is cut — so no task here touches it.

Rollback is a dependency-version revert in the consuming service.

## Open Questions

- Whether the `location-physical-type` gap for a point of care is worth raising with the v2-to-FHIR IG
  authors, given their own ConceptMap leaves the cell unresolved. Answering it later does not change
  what we emit now, which is no code.
