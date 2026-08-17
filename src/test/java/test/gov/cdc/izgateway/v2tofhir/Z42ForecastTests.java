package test.gov.cdc.izgateway.v2tofhir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Immunization;
import org.hl7.fhir.r4.model.ImmunizationRecommendation;
import org.hl7.fhir.r4.model.ImmunizationRecommendation.ImmunizationRecommendationRecommendationComponent;
import org.hl7.fhir.r4.model.ImmunizationRecommendation.ImmunizationRecommendationRecommendationDateCriterionComponent;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Resource;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.model.Segment;
import gov.cdc.izgw.v2tofhir.converter.MessageParser;
import gov.cdc.izgw.v2tofhir.utils.ParserUtils;
import gov.cdc.izgw.v2tofhir.utils.TestData;
import lombok.extern.slf4j.Slf4j;

/**
 * Tests for the conversion of RSP_K11 responses conforming to CDC profile Z42 (evaluated history
 * and forecast), where each ORC/RXA group is either a dose administered (Immunization) or a
 * forecast (a recommendation component of the message's single ImmunizationRecommendation).
 *
 * The tests over real IIS responses are opt in, because no IIS-sourced message is committed to this
 * repository.  They read *.txt HL7 files from a local directory and are skipped when it is not
 * given:
 * <pre>
 * mvn test -Dtest=Z42ForecastTests -Dv2tofhir.localMessages=/path/to/ehex-testing
 * </pre>
 *
 * @author Audacious Inquiry
 */
@Slf4j
class Z42ForecastTests extends TestBase {
	/** System property naming a directory of local (uncommitted) HL7 messages to convert */
	private static final String LOCAL_MESSAGES = "v2tofhir.localMessages";
	private static final String VACCINE_TYPE = "30956-7";
	private static final String NO_VACCINE_ADMINISTERED = "998";

	/**
	 * The CDC IG "Complete Example Of Evaluation And Forecasting" message: three evaluated history
	 * ORC/RXA groups (31, 48, 110) followed by one forecast group (998).
	 *
	 * IzDetail.checkRXA() resolves each ORC to its own RXA using
	 * ParserUtils.getFollowingSegment().  Both were unreachable before the per-group decision was
	 * wired up, and the resolution relies on HAPI naming the nonstandard segments ORC, ORC2, ORC3,
	 * ... because these messages parse as the stock tabular v251.RSP_K11, which has no ORC/RXA
	 * groups.
	 */
	@Test
	void testEachOrcResolvesItsOwnRxa() {
		Message msg = parse(getMixedZ42Message());
		assertNotNull(msg);
		List<String> found = new ArrayList<>();
		for (String name : Arrays.asList("ORC", "ORC2", "ORC3", "ORC4")) {
			Segment orc = getSegment(msg, name);
			assertNotNull(orc, name + " is present");
			Segment rxa = ParserUtils.getFollowingSegment(orc, "RXA");
			assertNotNull(rxa, name + " has a following RXA");
			found.add(ParserUtils.toString(rxa, 5));
		}
		assertEquals(Arrays.asList("31", "48", "110", NO_VACCINE_ADMINISTERED), found);
	}

	/**
	 * The same message converted: the three real doses are Immunizations, and the forecast group
	 * contributes to a single ImmunizationRecommendation.
	 */
	@Test
	void testMixedZ42YieldsBothResourceTypes() {
		Bundle bundle = convert(getMixedZ42Message());
		assertEquals(3, resourcesOf(bundle, Immunization.class).size());
		List<ImmunizationRecommendation> recommendations = resourcesOf(bundle, ImmunizationRecommendation.class);
		assertEquals(1, recommendations.size());
		assertEquals(1, recommendations.get(0).getRecommendation().size());
		checkRecommendation(recommendations.get(0), "mixed Z42", false);
	}

	/**
	 * Convert every message found in the directory named by the {@value #LOCAL_MESSAGES} system
	 * property, and check the shape of the forecast output.  Skipped when the property is unset.
	 */
	@Test
	void testLocalIisResponses() throws IOException {
		String dir = System.getProperty(LOCAL_MESSAGES);
		Assumptions.assumeTrue(StringUtils.isNotBlank(dir), LOCAL_MESSAGES + " is not set");
		File[] files = new File(dir).listFiles((d, name) -> name.endsWith(".txt"));
		assertNotNull(files, dir + " is a directory");
		assertFalse(files.length == 0, dir + " contains messages");

		for (File file : files) {
			String message = readMessage(file);
			if (!message.contains("Z42^CDCPHINVS")) {
				log.info("{} is not a Z42 response, skipping", file.getName());
				continue;
			}
			Bundle bundle = convert(message);
			for (Immunization iz : resourcesOf(bundle, Immunization.class)) {
				assertFalse(iz.getEducation().stream().anyMatch(Base::isEmpty),
					file.getName() + " Immunization has no empty education element");
			}
			// R4 restricts Observation.partOf to MedicationAdministration | MedicationDispense |
			// MedicationStatement | Procedure | Immunization | ImagingStudy.
			for (Observation obs : resourcesOf(bundle, Observation.class)) {
				assertFalse(
					obs.getPartOf().stream().anyMatch(
						ref -> StringUtils.startsWith(ref.getReference(), "ImmunizationRecommendation")
					),
					file.getName() + " no Observation.partOf references an ImmunizationRecommendation"
				);
			}
			List<ImmunizationRecommendation> recommendations =
				resourcesOf(bundle, ImmunizationRecommendation.class);
			assertTrue(recommendations.size() <= 1, file.getName() + " has at most one ImmunizationRecommendation");

			assertEquals(countAdministeredRxas(message), resourcesOf(bundle, Immunization.class).size(),
				file.getName() + " Immunization count matches the number of non-998 RXAs");

			int forecasts = countForecastVaccineTypes(message);
			if (forecasts == 0) {
				continue;
			}
			assertEquals(1, recommendations.size(), file.getName() + " has an ImmunizationRecommendation");
			ImmunizationRecommendation recommendation = recommendations.get(0);
			assertEquals(forecasts, recommendation.getRecommendation().size(),
				file.getName() + " recommendation count matches the number of " + VACCINE_TYPE + " observations");
			checkRecommendation(recommendation, file.getName(), true);
			log.info("{}: {} Immunization, 1 ImmunizationRecommendation with {} recommendations",
				file.getName(), resourcesOf(bundle, Immunization.class).size(), forecasts);
		}
	}

	/**
	 * Check the R4 required elements and the absence of the 998 placeholder.
	 * @param recommendation The resource to check
	 * @param name Identifies the message in assertion messages
	 * @param expectForecastStatus True if every component is expected to carry a forecastStatus.
	 * The CDC IG samples omit 59783-1 Series Status, real IIS responses do not.
	 */
	private void checkRecommendation(
		ImmunizationRecommendation recommendation, String name, boolean expectForecastStatus
	) {
		assertTrue(recommendation.hasDate(), name + " recommendation has a date");
		assertTrue(recommendation.hasIdentifier(), name + " recommendation has an identifier");
		for (ImmunizationRecommendationRecommendationComponent component : recommendation.getRecommendation()) {
			assertTrue(component.hasVaccineCode(), name + " component has a vaccineCode");
			assertFalse(
				component.getVaccineCode().stream()
					.flatMap(cc -> cc.getCoding().stream())
					.anyMatch(coding -> NO_VACCINE_ADMINISTERED.equals(coding.getCode())),
				name + " component vaccineCode is not " + NO_VACCINE_ADMINISTERED
			);
			if (expectForecastStatus) {
				assertTrue(component.hasForecastStatus(), name + " component has a forecastStatus");
			}
			if (isTooOld(component)) {
				// The IIS explains a Too Old forecast in 30982-3, which must reach forecastReason
				// rather than being stranded on a discarded Observation.
				assertTrue(component.hasForecastReason(), name + " Too Old component has a forecastReason");
			}
			// The forecast observations duplicate this component, so nothing references them.  A
			// reference would dangle once a searchset filter drops the Observation resources.
			assertFalse(component.hasSupportingPatientInformation(),
				name + " component emits no supportingPatientInformation");
			for (ImmunizationRecommendationRecommendationDateCriterionComponent criterion : component.getDateCriterion()) {
				assertTrue(criterion.hasCode(), name + " dateCriterion has a code");
			}
		}
	}

	/**
	 * Read a message from a local capture.  These captures hold the QBP request followed by the RSP
	 * response, so everything before the second MSH is dropped.  Line endings can be \r only.
	 * @param file The file to read
	 * @return The message to convert
	 * @throws IOException If the file cannot be read
	 */
	private static String readMessage(File file) throws IOException {
		String content = Files.readString(file.toPath(), StandardCharsets.UTF_8)
			.replace("\r\n", "\r").replace("\n", "\r").trim();
		int second = content.indexOf("\rMSH|", content.indexOf("\rMSH|") + 1);
		if (second > 0) {
			content = content.substring(second + 1);
		}
		return content;
	}

	private static int countAdministeredRxas(String message) {
		int count = 0;
		for (String line : message.split("\r")) {
			if (line.startsWith("RXA|") && !NO_VACCINE_ADMINISTERED.equals(vaccineCode(line))) {
				count++;
			}
		}
		return count;
	}

	private static int countForecastVaccineTypes(String message) {
		int count = 0;
		boolean forecast = false;
		for (String line : message.split("\r")) {
			if (line.startsWith("RXA|")) {
				forecast = NO_VACCINE_ADMINISTERED.equals(vaccineCode(line));
			} else if (forecast && line.startsWith("OBX|") && field(line, 3).startsWith(VACCINE_TYPE)) {
				count++;
			}
		}
		return count;
	}

	private static String vaccineCode(String rxa) {
		return StringUtils.substringBefore(field(rxa, 5), "^");
	}

	private static String field(String segment, int field) {
		String[] fields = segment.split("\\|", -1);
		return fields.length > field ? fields[field].trim() : "";
	}

	private static Segment getSegment(Message msg, String name) {
		try {
			return (Segment) msg.get(name);
		} catch (Exception e) {
			return null;
		}
	}

	/** LOINC answer LA13424-9 = Too Old, the status whose reason arrives in 30982-3 */
	private static boolean isTooOld(ImmunizationRecommendationRecommendationComponent component) {
		return component.getForecastStatus().getCoding().stream()
			.anyMatch(coding -> "LA13424-9".equals(coding.getCode()));
	}

	private static <T extends Resource> List<T> resourcesOf(Bundle bundle, Class<T> type) {
		return bundle.getEntry().stream()
			.map(Bundle.BundleEntryComponent::getResource)
			.filter(type::isInstance)
			.map(type::cast)
			.toList();
	}

	private static Bundle convert(String message) {
		Message msg = parse(message);
		assertNotNull(msg);
		return new MessageParser().convert(msg);
	}

	/** The CDC IG mixed Z42 example from messages.txt, the one with four ORC/RXA groups. */
	private static String getMixedZ42Message() {
		for (TestData data : TEST_MESSAGES) {
			String message = data.getTestData();
			if (message != null && message.contains("Z42^CDCPHINVS") && message.contains("197028^DCS")) {
				return message;
			}
		}
		throw new AssertionError("Cannot find the mixed Z42 message in messages.txt");
	}
}
