package test.gov.cdc.izgateway.v2tofhir;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.similarity.LevenshteinDistance;
import org.hl7.fhir.r4.model.BaseDateTimeType;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.InstantType;
import org.hl7.fhir.r4.model.PrimitiveType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import ca.uhn.fhir.model.api.TemporalPrecisionEnum;
import gov.cdc.izgw.v2tofhir.converter.DatatypeConverter;
import test.gov.cdc.izgateway.TestUtils;
import test.gov.cdc.izgateway.NamedArrayList;

class TestDateTimeParsing {
	private static final NamedArrayList validDates = NamedArrayList.l("validDates", "01", "08", "21", "28");

	private static final NamedArrayList shortMonths = NamedArrayList.l("shortMonths", "09", "11");
	private static final NamedArrayList longMonths = NamedArrayList.l("longMonths", "01", "12");
	private static final NamedArrayList validMonths = new NamedArrayList("validMonths");
	static {
		validMonths.addAll(shortMonths);
		validMonths.addAll(longMonths);
	}

	private static final NamedArrayList leapYears = NamedArrayList.l("leapYears", "1965", "2000", "2024", "2038");
	private static final NamedArrayList nonleapYears = NamedArrayList.l("nonLeapYears", "1965", "1900", "2039");
	private static final NamedArrayList validYears = new NamedArrayList("validYears");
	static {
		validYears.addAll(leapYears);
		validYears.addAll(nonleapYears);
		validMonths.addAll(shortMonths);
		validMonths.addAll(longMonths);
	}

	private static final NamedArrayList invalidYears = new NamedArrayList("invalidYears", "65", "-1", "0", "10001");
	private static final NamedArrayList invalidMonths = NamedArrayList.l("invalidMonths", "100", "00", "-1", "13");
	private static final NamedArrayList invalidDates = NamedArrayList.l("invalidDates", "-0", "-10", "32", "40", "100");

	private static final NamedArrayList validHours = NamedArrayList.l("validHours", "00", "01", "03", "12", "13", "23");
	private static final NamedArrayList validMinutes = NamedArrayList.l("validMinutes", "00", "15", "30", "45");
	private static final NamedArrayList validSeconds = NamedArrayList.l("validSeconds", "00", "10", "59", "60");
	private static final NamedArrayList invalidHours = NamedArrayList.l("invalidHours", "0", "1", "-0", "-1", "24",
			"121");
	private static final NamedArrayList invalidMinutes = NamedArrayList.l("invalidMinutes", "0", "1", "-0", "-1", "60",
			"77", "119");
	private static final NamedArrayList invalidSeconds = NamedArrayList.l("invalidSeconds", "0", "1", "-0", "-1", "77",
			"119");

	private static final NamedArrayList validTimeZones = NamedArrayList.l("validTimeZones", "Z", "-00:00", "+00:00",
			"-0:00", "+0:00", "-00", "+00", "+03", "+03:30", "+04", "+04:30", "+05", "+05:30", "+05:45", "+06",
			"+06:30", "+08", "+08:30", "+08:45", "+09", "+09:30", "+09:30", "+10", "+10:30", "+11", "+11:00", "+12",
			"+12:45", "+13", "+13:45", "+3", "+3:30", "+4", "+4:30", "+5", "+5:30", "+5:45", "+6", "+6:30", "+8",
			"+8:30", "+8:45", "+9", "+9:30", "+9:30", "-03", "-03:00", "-04", "-04:00", "-05", "-05:00", "-3", "-3:00",
			"-4", "-4:00", "-5", "-5:00");
	private static final NamedArrayList invalidTimeZones = NamedArrayList.l("invalidTimeZones", "T", " GMT", " EDT",
			"-23", "-23:00", "+23", "+23:00", "-25", "-25:00", "+25", "+25:00", "+06:01", "-06:99");

	private static final int TRUNC = -1;

	static NamedArrayList getValidDates(boolean withPunct) {
		String key = "getValidDates(" + withPunct + ")";
		NamedArrayList validTests = NamedArrayList.find(key);
		if (!validTests.isEmpty()) {
			return validTests;
		}
		char punct = withPunct ? '-' : (char) 0;

		validTests.addAll(cross(validYears, validMonths, validDates, punct));

		// Special cases, Date of Feb 29 in leap years
		validTests.addAll(cross(leapYears, NamedArrayList.singleton("02"), NamedArrayList.singleton("29"), punct));

		return validTests;
	}

	static NamedArrayList getInvalidDates(boolean withPunct) {
		char punct = withPunct ? '-' : (char) 0;

		String key = "getValidDates(" + withPunct + ")";
		NamedArrayList invalidTests = NamedArrayList.find(key);
		if (!invalidTests.isEmpty()) {
			return invalidTests;
		}

		// with an invalid year and w or w/o separators
		invalidTests.addAll(cross(invalidYears, choose(validMonths), choose(validDates), punct));

		// with an invalid month and w or w/o separators
		invalidTests.addAll(cross(choose(validYears), invalidMonths, choose(validDates), punct));

		// with an invalid date and w or w/o separators
		invalidTests.addAll(cross(choose(validYears), choose(validMonths), invalidDates, punct));

		// Special cases, Date of Feb 29 in non-leap years
		invalidTests.addAll(cross(nonleapYears, NamedArrayList.singleton("02"), NamedArrayList.singleton("29"), punct));

		// Special cases, Date of Feb 30 in leap years
		invalidTests.addAll(cross(leapYears, NamedArrayList.singleton("02"), NamedArrayList.singleton("30"), punct));

		// Special cases, Date of 31 on short months.
		invalidTests.addAll(cross(leapYears, shortMonths, NamedArrayList.singleton("31"), punct));

		return invalidTests;
	}

	private static Map<List<String>, Integer> choices = new TreeMap<>(TestDateTimeParsing::listComparator);

	static int listComparator(List<String> a, List<String> b) {
		if (a == b) {
			return 0;
		}
		if ((a == null || a.isEmpty()) && (b == null || b.isEmpty())) {
			return 0;
		}
		if (a == null || a.isEmpty()) {
			return -1;
		}
		if (b == null) {
			return 1;
		}
		int comp = a.getClass().getName().compareTo(b.getClass().getName());
		if (comp != 0) {
			return comp;
		}
		comp = Integer.compare(a.size(), b.size());
		if (comp != 0) {
			return comp;
		}
		// Test some arbitrary locations (first, last and middle)
		int[] tests = { 0, a.size() - 1, a.size() / 2 };
		for (int t : tests) {
			comp = a.get(t).compareTo(b.get(t));
			if (comp != 0) {
				return comp;
			}
		}
		// Two different array lists aren't equal even if they contain the same element
		// data,
		// choose an arbitrary value. We aren't sorting these keys.
		return 1;
	}

	private static NamedArrayList choose(NamedArrayList list) {
		if (list.size() == 0) {
			return NamedArrayList.find("empty");
		}
		Integer choice = choices.computeIfAbsent(list, k -> Integer.valueOf(0));
		if (choice >= list.size()) {
			choice = 0;
		}
		NamedArrayList result = NamedArrayList.l(list.name() + "[" + choice + "]", list.get(choice));
		choices.put(list, choice + 1);
		return result;
	}

	static List<String> getValidTimes(int precision, boolean withPunct) {
		String key = "getValidTime(" + precision + "," + withPunct + ").withTZ()";
		
		NamedArrayList validTests = NamedArrayList.find(key);
		if (!validTests.isEmpty()) {
			return validTests;
		}
		validTests.addAll(getValidTimesWithoutTZ(precision, withPunct));
		addTimeZones(validTests, true, withPunct);
		
		return validTests;
	}

	private static NamedArrayList getValidTimesWithoutTZ(int precision, boolean withPunct) {
		String key = "getValidTime(" + precision + "," + withPunct + ")";
		NamedArrayList validTests = NamedArrayList.find(key);
		if (!validTests.isEmpty()) {
			return validTests;
		}

		char punct = withPunct ? ':' : (char) 0;
		NamedArrayList mySeconds = validSeconds;
		if (precision < 0) {
			validTests.addAll(cross(validHours, validMinutes, punct));
		} else if (precision == 0) {
			validTests.addAll(cross(validHours, validMinutes, validSeconds, punct));
		} else {
			mySeconds = new NamedArrayList(mySeconds.name() + ".withPrecision(" + precision + ")");
			mySeconds.addAll(validSeconds.stream().flatMap(s -> withPrecision(s, precision)).toList());
			// validSeconds = validSeconds.stream().map(s -> withPrecision(s,
			// precision)).toList();
			validTests.addAll(cross(validHours, validMinutes, mySeconds, punct));
		}
		return validTests;
	}

	private static void addTimeZones(NamedArrayList tests, boolean isValid, boolean withPunct) {
		NamedArrayList l = NamedArrayList.find(tests.name() + "withTZ()");
		NamedArrayList tzSource = isValid ? validTimeZones : invalidTimeZones;
		if (l.isEmpty()) {
			l.addAll(tests); // Take a snapshot of tests
			
			// Modify list by adding time zones.
			int pos = 0;
			for (int i = 0; i < l.size(); i++) {
				String vt = l.get(i);
				String tz = tzSource.get(pos);
				if (!withPunct) {
					tz.replace(":", ""); // Remove colons in TZ
				}
				pos = (pos + 1) %  tzSource.size();
				l.set(i, vt + tz);
			}
		}
		tests.addAll(l);
	}

	static List<String> getInvalidTimes(int precision, boolean withPunct) {
		String key = "getInvalidTimes(" + precision + "," + withPunct + ")";
		NamedArrayList invalidTests = NamedArrayList.find(key);
		if (!invalidTests.isEmpty()) {
			return invalidTests;
		}
		char punct = withPunct ? ':' : (char) 0;
		NamedArrayList mySeconds = validSeconds;
		if (precision < 0) {
			invalidTests.addAll(cross(invalidHours, choose(validMinutes), punct));
			invalidTests.addAll(cross(choose(validHours), invalidMinutes, punct));
		} else if (precision == 0) {
			invalidTests.addAll(cross(invalidHours, choose(validMinutes), choose(validSeconds), punct));
			invalidTests.addAll(cross(choose(validHours), invalidMinutes, choose(validSeconds), punct));
			invalidTests.addAll(cross(choose(validHours), choose(validMinutes), invalidSeconds, punct));
		} else {
			mySeconds = new NamedArrayList(mySeconds.name() + "withPrecision(" + precision + ")");
			mySeconds.addAll(invalidSeconds.stream().flatMap(s -> withPrecision(s, precision)).toList());
			invalidTests.addAll(cross(invalidHours, choose(validMinutes), choose(validSeconds), punct));
			invalidTests.addAll(cross(choose(validHours), invalidMinutes, choose(validSeconds), punct));
			invalidTests.addAll(cross(choose(validHours), choose(validMinutes), mySeconds, punct));
		}
		addTimeZones(invalidTests, true, withPunct);
		// Get set of valid times, and make them invalid by adding an invalid time zone
		NamedArrayList validTests = getValidTimesWithoutTZ(precision, withPunct);
		addTimeZones(validTests, false, withPunct);
		invalidTests.addAll(validTests);
		return invalidTests;
	}

	static NamedArrayList getTimes(boolean withPunct, boolean isValid, Integer... precisions) {
		String key = "getTimes(" + withPunct + "," + isValid + "," + Arrays.asList(precisions) + ")";
		NamedArrayList allTimes = NamedArrayList.find(key);
		if (!allTimes.isEmpty()) {
			return allTimes;
		}
		BiFunction<Integer, Boolean, List<String>> f = isValid ? TestDateTimeParsing::getValidTimes
				: TestDateTimeParsing::getInvalidTimes;
		for (int prec : precisions) {
			allTimes.addAll(f.apply(prec, withPunct));
		}
		return allTimes.notEmpty();
	}

	static Stream<String> withPrecision(String s, int precision) {
		List<String> values = Arrays.asList(s + "." + StringUtils.repeat("0", precision),
				s + "." + StringUtils.repeat("0", precision - 1) + "1", s + "." + StringUtils.repeat("9", precision));
		return values.stream();
	}

	private static NamedArrayList cross(NamedArrayList first, NamedArrayList second, NamedArrayList third,
			char separator) {
		TestUtils.assertNotEmpty(first);
		TestUtils.assertNotEmpty(second);
		TestUtils.assertNotEmpty(third);
		long count = first.size() * second.size() * third.size();
		System.out.printf("Computing %d * %d * %d = %d test cases%n", first.size(), second.size(), third.size(), count);
		NamedArrayList cross = NamedArrayList.find(first.name() + " X " + second.name() + " X " + third.name());
		if (!cross.isEmpty()) {
			return cross;
		}
		for (String one : first) {
			for (String two : second) {
				for (String three : third) {
					StringBuilder b = new StringBuilder();
					b.append(one);
					if (separator != 0) {
						b.append(separator);
					}
					b.append(two);
					if (separator != 0) {
						b.append(separator);
					}
					b.append(three);
					cross.add(b.toString());
				}
			}
		}
		return cross;
	}

	private static NamedArrayList cross(NamedArrayList first, NamedArrayList second, char separator) {
		TestUtils.assertNotEmpty(first);
		TestUtils.assertNotEmpty(second);
		long count = first.size() * second.size();
		System.out.printf("Computing %d * %d = %d test cases%n", first.size(), second.size(), count);
		NamedArrayList cross = NamedArrayList.find(first.name() + " X " + second.name());
		if (!cross.isEmpty()) {
			return cross;
		}
		for (String one : first) {
			for (String two : second) {
				StringBuilder b = new StringBuilder();
				b.append(one);
				if (separator != 0) {
					b.append(separator);
				}
				b.append(two);
				cross.add(b.toString());
			}
		}
		return cross;
	}

	@SafeVarargs
	static NamedArrayList fuzzStrings(NamedArrayList strings, BiFunction<String, Integer, String>... fuzzers) {

		/** Relies on the assumption that fuzzers in use are ALWAYS the same */
		NamedArrayList fuzzed = NamedArrayList.find("fuzzStrings(" + strings.name() + ")");
		if (!fuzzed.isEmpty()) {
			return fuzzed;
		}

		for (String string : strings) {
			int i = strings.size() % string.length();
			for (BiFunction<String, Integer, String> fuzzer : fuzzers) {
				fuzzed.add(fuzzer.apply(string, i));
			}
		}
		return fuzzed.notEmpty();
	}

	static String deleteOne(String s, int position) {
		if (s.length() <= 1) {
			return "";
		}
		if (position == 0) {
			return s.length() == 1 ? "" : s.substring(position);
		}
		String newString = s.substring(0, position);
		if (position + 1 < s.length()) {
			newString += s.substring(position + 1);
		}
		return newString;
	}

	static String doubleUp(String s, int position) {
		if (s.length() <= 1) {
			return s + s;
		}
		if (position == 0) {
			return s.charAt(position) + s;
		}

		return s.substring(0, position) + s.charAt(position) + s.substring(position);
	}

	static String transpose(String s, int position) {
		if (s.length() < 2) {
			return "" + s.charAt(s.length() - 1) + s.charAt(0);
		}
		position %= s.length();
		StringBuffer b = new StringBuffer(s);
		char l = b.charAt(position);
		char r = b.charAt((position + 1) % s.length());
		b.setCharAt(position, r);
		b.setCharAt((position + 1) % s.length(), l);
		return b.toString();
	}

	private enum Combo {
		VALID_DATE_AND_TIME, INVALID_DATE_AND_VALID_TIME, VALID_DATE_AND_INVALID_TIME, FUZZED_DATE_AND_VALID_TIME,
		VALID_DATE_AND_FUZZED_TIME
	};

	/**
	 * Get timestamps combine valid times and dates invalid times with valid dates
	 * valid times with invalid dates and fuzzed valid times with valid dates and
	 * valid times with fuzzed dates
	 * 
	 * @param isValid
	 * @return
	 */
	static NamedArrayList getTimestamps(Combo combo, boolean withPunct) {
		String key = "getTimestamps(" + combo + "," + withPunct + ")";
		NamedArrayList result = NamedArrayList.find(key);
		if (!result.isEmpty()) {
			return result;
		}
		char punct = withPunct ? 'T' : (char) 0;
		NamedArrayList dates;
		switch (combo) {
		case VALID_DATE_AND_TIME, VALID_DATE_AND_INVALID_TIME, VALID_DATE_AND_FUZZED_TIME:
			dates = choose(getValidDates(withPunct));
			break;
		case INVALID_DATE_AND_VALID_TIME:
			dates = getInvalidDates(withPunct);
			break;
		case FUZZED_DATE_AND_VALID_TIME:
			dates = fuzzStrings(getValidDates(withPunct), TestDateTimeParsing::deleteOne, TestDateTimeParsing::doubleUp,
					TestDateTimeParsing::transpose);
			break;
		default:
			dates = null;
			break;
		}
		TestUtils.assertNotEmpty(dates);
		NamedArrayList times;
		switch (combo) {
		case VALID_DATE_AND_TIME:
		case INVALID_DATE_AND_VALID_TIME:
		case FUZZED_DATE_AND_VALID_TIME:
			times = choose(getTimes(withPunct, true, -1, 0, 1, 2, 3, 4));
			break;
		case VALID_DATE_AND_INVALID_TIME:
			times = getTimes(withPunct, false, -1, 0, 1, 2, 3, 4);
			break;
		case VALID_DATE_AND_FUZZED_TIME:
			times = fuzzStrings(getTimes(withPunct, true, -1, 0, 1, 2, 3, 4), TestDateTimeParsing::deleteOne,
					TestDateTimeParsing::doubleUp, TestDateTimeParsing::transpose);
			break;
		default:
			times = null;
			break;
		}
		TestUtils.assertNotEmpty(times);

		NamedArrayList crossedResult = cross(dates, times, punct);
		System.out.println("Computed " + crossedResult.size() + " test cases");
		return crossedResult.notEmpty();
	}
	
	static List<String> getTimestampsVDVT() {
		return getTimestamps(Combo.VALID_DATE_AND_TIME, true).truncate(TRUNC);
	}

	static List<String> getTimestampsVDIT() {
		return getTimestamps(Combo.VALID_DATE_AND_INVALID_TIME, true).truncate(TRUNC);
	}

	static List<String> getTimestampsVDFT() {
		return getTimestamps(Combo.VALID_DATE_AND_FUZZED_TIME, true).truncate(TRUNC);
	}

	static List<String> getTimestampsIDVT() {
		return getTimestamps(Combo.INVALID_DATE_AND_VALID_TIME, true).truncate(TRUNC);
	}

	static List<String> getTimestampsFDVT() {
		return getTimestamps(Combo.FUZZED_DATE_AND_VALID_TIME, true).truncate(TRUNC);
	}

	private static String myTZ = String.format("%tz", new Date());
	private static String isoTZ = StringUtils.substring(myTZ, 0, 3) + (myTZ.length() > 3 ? ":" : "")
			+ StringUtils.substring(myTZ, 3);
	static {
		if (isoTZ.endsWith("00:00")) {
			isoTZ = "Z";
		}
	}

	@ParameterizedTest
	@MethodSource("getTimestampsVDVT")
	void testDateTimeConversionVDVT(String input) {
		InstantType actual = DatatypeConverter.toInstantType(input);
		try {
			compareToFhir(input, actual, Date.class);
		} catch (ca.uhn.fhir.parser.DataFormatException ex) {
			compareToString(input, actual, true);
		}
	}

	private <T> void compareToFhir(String input, PrimitiveType<?> actual, Class<T> primitive) {
		String expected = null;
		try {
			BaseDateTimeType fhirType = new DateTimeType();
			fhirType.setValueAsString(input);
			Date date = fhirType.getValue();
			TemporalPrecisionEnum prec = fhirType.getPrecision();
			TimeZone tz = fhirType.getTimeZone();
			fhirType = new InstantType();
			fhirType.setValue(date);
			fhirType.setTimeZone(tz);
			fhirType.setPrecision(prec);
			expected = fhirType.getValueAsString();
		} catch (Exception ex) {
			// Ignore FHIR exception.
		}
		if (expected != null) {
			String actualValue = actual == null ? null : actual.asStringValue();
			assertNotNull(actual);
			assertTrue(timesAreEquivalent(input, actualValue), input + " is not equivalent to " + actualValue);
			assertTrue(timesAreEquivalent(expected, actualValue), input + " is not equivalent to original FHIR string " + actualValue);
		}
	}

	private boolean timesAreEquivalent(String expected, String actual) {
		// Let -0: == 00: be OK 
		expected = expected.replace("-0:", "00:").replace(":-0", ":00");
		
		if (expected.equals(actual)) {  // strings are equal
			return true;
		}
		if (expected.contains(".") && actual.contains(".")) {  // Check for precision enhancement
			expected = adjustPrecision(expected);
			actual = adjustPrecision(actual);
			if (expected.contains(".999") && actual.contains(".000") &&
				LevenshteinDistance.getDefaultInstance().apply(expected, actual) >= 10
			) {
				// Truncated to 3-4 digits of precision and rolled over to next second
				return true;
			}
			if (expected.equals(actual)) {
				return true;
			}
		}
		if (hasEquivalentTimeZones(expected, actual)) { // timestamp equal, time zones equivalent
			return true;
		}
		if ((expected+isoTZ).equals(actual)) {	// original without TZ has local TZ added. 
			return true;
		}
		if (StringUtils.containsAny(expected + "+", ":60+", ":60-", ":60Z", ":60." ) &&
			StringUtils.containsAny(actual + "+", ":00+", ":00-", ":00Z", "00.")
		) {
			// Roll-over on :60 seconds found.
			return true;
		}
		return false;
	}

	private String removeTz(String s) {
		// Remove any TZ suffix
		s = StringUtils.substringBeforeLast(s, "-");
		s = StringUtils.substringBeforeLast(s, "+");
		s = StringUtils.substringBeforeLast(s, "Z");
		return s;
	}
	
	private String adjustPrecision(String s) {
		if (!s.contains(".")) {
			return s;
		}
		String l = StringUtils.substringBefore(s, ".");
		String r = StringUtils.substringAfter(s, ".");
		String rwotz = removeTz(r);
		String tz = r.substring(rwotz.length());
		return l + "." + StringUtils.right(rwotz + "000", 3) + tz;
	}
	private boolean hasEquivalentTimeZones(String s, String actualValue) {
		String[] endingStrings = { "Z", "+00:00", "-00:00" };
		
		// There are four ways the time could end:
		// With TZ of Z, +00:00, or -00:00, or without any of them.  If one of them
		// is present, remove it on both sides.
		if (StringUtils.endsWithAny(s, endingStrings)) {
			s = removeEnding(s, endingStrings);
		}
		if (StringUtils.endsWithAny(actualValue, endingStrings)) {
			actualValue = removeEnding(actualValue, endingStrings);
		}
		// Then compare the times without a time zone.
		// This addresses the special case of an environment in UTC time zone,
		// which is where the CI/CD build lives.
		return s.equals(actualValue);
	}

	private String removeEnding(String s, String[] endingStrings) {
		for (String ending : endingStrings) {
			if (StringUtils.endsWith(s, ending)) {
				return s.substring(0, s.length() - ending.length());
			}
		}
		return s;
	}

	private void compareToString(String s, PrimitiveType<?> actual, boolean isValid) {
		if (actual != null) {
			// We got a value, but FHIR couldn't.
			if (s.length() + 6 == actual.asStringValue().length()) {
				assertEquals(s + isoTZ, actual.asStringValue(), actual.asStringValue()
						+ " didn't match the original string " + s + " with default timezone " + isoTZ);
			} else {
				assertEquals(s, actual.asStringValue(),
						actual.asStringValue() + " didn't match the original string " + s);
			}
		} else if (isValid) {
			assertNotNull(actual, String.format("%s is supposed to be a valid Timestamp", s));
		}
	}

	@ParameterizedTest
	@MethodSource("getTimestampsVDIT")
	void testDateTimeConversionVDIT(String s) {
		InstantType actual = null;
		try {
			actual = DatatypeConverter.toInstantType(s);
			compareToFhir(s, actual, Date.class);
		} catch (Exception ex) {
			// We didn't parse it successfully, OK to ignore this failure.
		}
		if (actual == null) {
			compareToString(s, actual, false);
		}
	}

	@ParameterizedTest
	@MethodSource("getTimestampsVDFT")
	void testDateTimeConversionVDFT(String s) {
		InstantType actual = DatatypeConverter.toInstantType(s);
		try {
			compareToFhir(s, actual, Date.class);
		} catch (ca.uhn.fhir.parser.DataFormatException ex) {
			// FHIR didn't parse it successfully.
			compareToString(s, actual, false);
		}
	}

	@ParameterizedTest
	@MethodSource("getTimestampsIDVT")
	void testDateTimeConversionIDVT(String s) {
		InstantType actual = DatatypeConverter.toInstantType(s);
		try {
			compareToFhir(s, actual, Date.class);
		} catch (ca.uhn.fhir.parser.DataFormatException ex) {
			// FHIR didn't parse it successfully.
			compareToString(s, actual, false);
		}
	}

	@ParameterizedTest
	@MethodSource("getTimestampsFDVT")
	void testDateTimeConversionFDVT(String s) {
		InstantType actual = DatatypeConverter.toInstantType(s);
		try {
			compareToFhir(s, actual, Date.class);
		} catch (ca.uhn.fhir.parser.DataFormatException ex) {
			// FHIR didn't parse it successfully.
			compareToString(s, actual, false);
		}
	}
}
