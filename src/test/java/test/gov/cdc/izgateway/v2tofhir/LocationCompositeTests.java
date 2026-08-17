package test.gov.cdc.izgateway.v2tofhir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.hl7.fhir.r4.model.Location;
import org.hl7.fhir.r4.model.Location.LocationMode;
import org.junit.jupiter.api.Test;

import ca.uhn.hl7v2.model.Segment;
import ca.uhn.hl7v2.model.Type;
import gov.cdc.izgw.v2tofhir.converter.DatatypeConverter;

/**
 * Tests for the conversion of the HL7 V2 person-location composites (PL, LA1 and LA2) to
 * Location, covering which component names each Location, which location-physical-type code it
 * carries, how several populated components nest through Location.partOf, and which element
 * components 9 and beyond supply for each of the three datatypes.
 *
 * The physical-type codes come from the HL7 V2-to-FHIR IG ConceptMap datatype-pl-to-location:
 * PL-3 Bed = bd, PL-2 Room = ro, PL-1 Point of Care = no code, PL-8 Floor = lvl,
 * PL-7 Building = bu, PL-4 Facility = si.
 *
 * @author Audacious Inquiry
 */
class LocationCompositeTests extends TestBase {
	private static final String PHYSICAL_TYPE = "http://terminology.hl7.org/CodeSystem/location-physical-type";
	/** The RXA-11 from the eHealth Exchange pilot response the conformance review was written against */
	private static final String REVIEWED_RXA_11 =
		"IZGATEWAYART^^^IZGATEWAY-AART^^^IZ GATEWAY AART TEST^^330 C ST SW UNIT 7^^UNKNOWN^VA^20201";

	/**
	 * The reviewed RXA-11: Point of Care, Facility and Building are populated, so each names its own
	 * Location and they nest from the most specific component to the least.  The review reported
	 * bd/Bed on the Point of Care and lvl/Level on the Facility, neither of which the message sent.
	 */
	@Test
	void testReviewedRxa11IsLabelledFromItsOwnComponents() {
		List<Location> chain = chainOf(toLa2(REVIEWED_RXA_11));
		assertEquals(3, chain.size(), "One Location per populated component");

		assertEquals(Arrays.asList("IZGATEWAYART", "IZ GATEWAY AART TEST", "IZGATEWAY-AART"), namesOf(chain),
				"Point of Care, then Building, then Facility");
		assertEquals(Arrays.asList(null, "bu", "si"), codesOf(chain));
		assertFalse(codesOf(chain).contains("bd"), "Nothing sent a Bed");
		assertFalse(codesOf(chain).contains("lvl"), "Nothing sent a Floor");
		for (Location location : chain) {
			assertEquals(LocationMode.INSTANCE, location.getMode());
		}
	}

	/** The same three components in a PL, which is where most person locations arrive. */
	@Test
	void testPointOfCareFacilityAndBuilding() {
		List<Location> chain = chainOf(toPl("IZGATEWAYART^^^IZGATEWAY-AART^^^IZ GATEWAY AART TEST"));
		assertEquals(Arrays.asList("IZGATEWAYART", "IZ GATEWAY AART TEST", "IZGATEWAY-AART"), namesOf(chain));
		assertEquals(Arrays.asList(null, "bu", "si"), codesOf(chain));
	}

	/** Point of Care has no code in location-physical-type, and none is invented for it. */
	@Test
	void testPointOfCareCarriesNoPhysicalType() {
		Location location = toLocation(toPl("IZGATEWAYART"));
		assertEquals("IZGATEWAYART", location.getName());
		assertFalse(location.hasPhysicalType(), "No physicalType element, empty or otherwise");
		assertFalse(location.hasPartOf(), "A single populated component builds no containment chain");
	}

	/** Bed, Room and Floor carry their own codes, most specific first. */
	@Test
	void testBedRoomAndFloor() {
		List<Location> chain = chainOf(toPl("^RM101^B2^^^^^FL3"));
		assertEquals(Arrays.asList("B2", "RM101", "FL3"), namesOf(chain));
		assertEquals(Arrays.asList("bd", "ro", "lvl"), codesOf(chain));
	}

	/** An empty component names nothing, so it produces no Location and no code. */
	@Test
	void testEmptyComponentsProduceNoLocation() {
		List<Location> chain = chainOf(toPl("IZGATEWAYART^^^IZGATEWAY-AART"));
		assertEquals(2, chain.size());
		List<String> codes = codesOf(chain);
		assertFalse(codes.contains("ro"), "No Room was sent");
		assertFalse(codes.contains("bd"), "No Bed was sent");
		assertFalse(codes.contains("lvl"), "No Floor was sent");
	}

	/**
	 * PL-5 Location Status and PL-6 Person Location Type describe the location rather than naming a
	 * place, so each is carried once on the Location and neither produces one of its own.
	 */
	@Test
	void testLocationStatusAndPersonLocationTypeAreNotLocations() {
		Location location = toLocation(toPl("IZGATEWAYART^^^^A^E"));
		assertEquals("A", location.getOperationalStatus().getCode(), "From PL-5");
		assertEquals(1, location.getType().size(), "PL-6 once");
		assertEquals("E", location.getTypeFirstRep().getCodingFirstRep().getCode(), "From PL-6");

		List<Location> chain = chainOf(location);
		assertEquals(1, chain.size(), "Only Point of Care names a Location");
		assertFalse(codesOf(chain).contains("A"));
		assertFalse(codesOf(chain).contains("E"));
	}

	/** Only a PL has a Location Description and a Comprehensive Location Identifier. */
	@Test
	void testPlDescriptionAndIdentifier() {
		Location location = toLocation(toPl("IZGATEWAYART^^^^^^^^EMERGENCY ROOM ENTRANCE^4707"));
		assertEquals("EMERGENCY ROOM ENTRANCE", location.getDescription(), "From PL-9, not PL-10");
		assertEquals("4707", location.getIdentifierFirstRep().getValue(), "From PL-10");
	}

	/** Absent PL-9 and PL-10 produce no description and no identifier. */
	@Test
	void testPlWithoutDescriptionOrIdentifier() {
		Location location = toLocation(toPl("IZGATEWAYART^^^IZGATEWAY-AART"));
		assertFalse(location.hasDescription());
		assertFalse(location.hasIdentifier());
	}

	/**
	 * LA2-9 through LA2-16 are the address, which is where they go.  An LA2 has no Location
	 * Description component and no Comprehensive Location Identifier component, so it produces
	 * neither -- the review saw the street address of the reviewed RXA-11 as a description.
	 */
	@Test
	void testLa2CarriesAnAddressAndNoDescription() {
		Location location = toLocation(toLa2(REVIEWED_RXA_11));
		assertEquals("330 C ST SW UNIT 7", location.getAddress().getLine().get(0).getValue());
		assertEquals("UNKNOWN", location.getAddress().getCity());
		assertEquals("VA", location.getAddress().getState());
		assertEquals("20201", location.getAddress().getPostalCode());
		for (Location inChain : chainOf(location)) {
			assertFalse(inChain.hasDescription(), "LA2-9 is a street address, not a description");
			assertFalse(inChain.hasIdentifier(), "LA2-10 is another designation, not an identifier");
		}
	}

	/** LA1-9 is an Address, so it becomes Location.address and never a description. */
	@Test
	void testLa1CarriesAnAddressAndNoDescription() {
		Location location = toLocation(toLa1(
			"IZGATEWAYART^^^IZGATEWAY-AART^^^^^123 MAIN ST&APT 2&SPRINGFIELD&IL&62701"));
		assertEquals("SPRINGFIELD", location.getAddress().getCity());
		for (Location inChain : chainOf(location)) {
			assertFalse(inChain.hasDescription(), "LA1-9 is an address, not a description");
			assertFalse(inChain.hasIdentifier(), "LA1 has no identifier component");
		}
	}

	/** The physical type codes come from the standard code system. */
	@Test
	void testPhysicalTypeUsesTheStandardCodeSystem() {
		for (Location location : chainOf(toPl("^RM101^B2^FAC^^^BLDG^FL3"))) {
			if (location.hasPhysicalType()) {
				assertEquals(PHYSICAL_TYPE, location.getPhysicalType().getCodingFirstRep().getSystem());
			}
		}
	}

	/** Every named Location in a chain is an instance, not a kind */
	@Test
	void testEveryLocationInAChainIsAnInstance() {
		for (Location location : chainOf(toPl("POC^RM101^B2^FAC^^^BLDG^FL3"))) {
			assertTrue(LocationMode.INSTANCE.equals(location.getMode()), "mode is instance");
		}
	}

	private static Location toLocation(Type type) {
		Location location = DatatypeConverter.toLocation(type);
		assertNotNull(location, "The composite converts to a Location");
		return location;
	}

	/** PL is PV1-3 Assigned Patient Location */
	private static Type toPl(String pl) {
		return fieldOf("PV1|1|I|" + pl, 3);
	}

	/** LA2 is RXA-11 Administered-at Location */
	private static Type toLa2(String la2) {
		return fieldOf("RXA|0|1|20220810|20220810|09^Td (adult), adsorbed^CVX|0.5|mL^MilliLiter^UCUM|||"
			+ "01^Historical Information - Source Unspecified^NIP001|" + la2, 11);
	}

	/** LA1 is RXE-8 Deliver-To Location */
	private static Type toLa1(String la1) {
		return fieldOf("RXE||||||||" + la1, 8);
	}

	private static Type fieldOf(String segment, int field) {
		try {
			Segment seg = parseSegment(segment);
			Type type = seg.getField(field, 0);
			assertNotNull(type);
			return type;
		} catch (Exception e) {
			throw new AssertionError("Cannot parse " + segment, e);
		}
	}

	/** The Location and everything it is partOf, in order */
	private static List<Location> chainOf(Type type) {
		return chainOf(toLocation(type));
	}

	private static List<Location> chainOf(Location location) {
		List<Location> chain = new ArrayList<>();
		Location current = location;
		while (current != null) {
			chain.add(current);
			current = current.hasPartOf() ? (Location) current.getPartOf().getResource() : null;
		}
		return chain;
	}

	private static List<String> namesOf(List<Location> chain) {
		return chain.stream().map(Location::getName).toList();
	}

	/** The physicalType code of each Location in the chain, null where it carries none */
	private static List<String> codesOf(List<Location> chain) {
		List<String> codes = new ArrayList<>();
		for (Location location : chain) {
			codes.add(location.hasPhysicalType() ? location.getPhysicalType().getCodingFirstRep().getCode() : null);
		}
		return codes;
	}
}
