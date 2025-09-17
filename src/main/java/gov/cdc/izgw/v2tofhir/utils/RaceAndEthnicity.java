package gov.cdc.izgw.v2tofhir.utils;

import java.util.List;
import java.util.function.UnaryOperator;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.StringType;

import gov.cdc.izgw.v2tofhir.segment.PIDParser;

/**
 * This class contains utilities for dealing with translating CDC Race and 
 * Ethnicity codes into FHIR extensions from US Core.
 * 
 * @author Audacious Inquiry
 *
 */
public class RaceAndEthnicity {
	/** Extension for US Core Ethnicity */
	public static final String US_CORE_ETHNICITY = "http://hl7.org/fhir/us/core/StructureDefinition/us-core-ethnicity";

	/** Extension for US Core Race */
	public static final String US_CORE_RACE = "http://hl7.org/fhir/us/core/StructureDefinition/us-core-race";

	/**
	 * Map a CodeableConcept containing a CDC Ethnicity code into a US Core Ethnicity extension
	 * 
	 * @param ethnicity	The ethnicity code provided
	 * @param ethnicityExtension	The extension to populate
	 */
	public static void setEthnicityCode(CodeableConcept ethnicity, Extension ethnicityExtension) {
		Extension text = null;
		if (ethnicity.hasText()) {
			text = new Extension("text", new StringType(ethnicity.getText()));
			ethnicityExtension.addExtension("text", text);
		}
		for (Coding coding: ethnicity.getCoding()) {
			String code = StringUtils.defaultString(coding.getCode());
			List<String> systemNames = Systems.getSystemNames(coding.getSystem());
			
			if (systemNames.contains(Systems.CDCREC) && 
				setCategoryAndDetail(ethnicityExtension, coding, code, RaceAndEthnicity::getEthnicityCategory)
			) {
				return;
			}
			
			if (setUnknown(ethnicityExtension, code)) {
				return;
			}
		}
	
		// Didn't find CDCREC, or UNKNOWN look in other code sets.
		for (Coding coding: ethnicity.getCoding()) {
			String code = StringUtils.defaultString(coding.getCode());
			
			if (text == null) {
				text = new Extension("text", new StringType(code));
				ethnicityExtension.addExtension(text);
			}
	
			// Deal with common legacy codes
			Coding c = new Coding(Systems.CDCREC, getEthnicityCategory(code), null);
			if (c.hasCode()) {
				Extension category = ethnicityExtension.getExtensionByUrl(PIDParser.OMB_CATEGORY);
				if (category == null) {
					ethnicityExtension.addExtension(PIDParser.OMB_CATEGORY, c);
				} else {
					category.setValue(c);
				}
				return;
			}
		}
		// Nothing found, we bail
		setUnknown(ethnicityExtension, "UNK");
	}

	/**
	 * Map a CodeableConcept containing a CDC Race Code into a US Core Race extension
	 * @param race	The race code provided
	 * @param raceExtension	The extension to populate
	 */
	public static void setRaceCode(CodeableConcept race, Extension raceExtension) {
		Extension text = setRaceText(race, raceExtension);
		if (!setCDCREC(race, raceExtension) &&  	// Didn't find CDCREC
			!setLegacy(race, raceExtension, text)	// Didn't find a Legacy code
		) {
			setUnknown(raceExtension, "UNK");		// Then set value as unknown.
		}
	}

	/**
	 * Computes the ethnic group category from the code
	 * 
	 * This uses knowledge about the structure of the CDC Ethnicity code table to compute the values.
	 * 
	 * @param ethnicityCode	The given ethnicity code
	 * @return	The OMB ethnicity category code
	 */
	private static String getEthnicityCategory(String ethnicityCode) {
		ethnicityCode = ethnicityCode.toUpperCase();
		if (("2135".compareTo(ethnicityCode) >= 0 && "2186".compareTo(ethnicityCode) < 0) ||
			ethnicityCode.charAt(0) == 'H'
		) {
			return "2135-2";
		}
		if ("2186-5".equals(ethnicityCode) || ethnicityCode.charAt(0) == 'N') {
			return "2186-5";
		}
		return null;
	}

	/**
	 * Computes the race category from the race code
	 * 
	 * This uses knowledge about the structure of the CDC Race code table to compute the values.
	 * 
	 * @param raceCode	The given race code
	 * @return	The OMB Race category code
	 */
	private static String getRaceCategory(String raceCode) {
		raceCode = raceCode.toUpperCase();
		// American Indian or Alaska Native
		
		if (RaceAndEthnicity.isBetween(raceCode, "1002", "2027") ||
			"INDIAN".equals(raceCode) || 
			"I".equals(raceCode)
		) {
			return "1002-5";
		}
		// Asian
		if (RaceAndEthnicity.isBetween(raceCode, "2028", "2053") ||
			"ASIAN".equals(raceCode) || 
			"A".equals(raceCode)
		) {
			return "2028-9";
		}
		// Black or African American
		if (RaceAndEthnicity.isBetween(raceCode, "2054", "2076") ||
			"BLACK".equals(raceCode) || 
			"B".equals(raceCode)
		) {
			return "2054-5";
		}
		// Native Hawaiian or Other Pacific Islander
		if (RaceAndEthnicity.isBetween(raceCode, "2076", "2105") ||
			"2500-7".equals(raceCode) ||
			"HAWIIAN".equals(raceCode) || "H".equals(raceCode)
		) { 
			return "2076-8";
		}
		// White
		if (RaceAndEthnicity.isBetween(raceCode, "2106", "2130") ||
			"WHITE".equals(raceCode) || 
			"W".equals(raceCode)
		) {
			return "2106-3";
		}
		if ("2131-1".equals(raceCode) || "OTHER".equals(raceCode) || "O".equals(raceCode)) {
			return "2131-1";
		}
		return null;
	}

	private static boolean isBetween(String string, String low, String high) {
		return low.compareTo(string) <= 0 && high.compareTo(string) > 0;
	}

	private static boolean setCategoryAndDetail(Extension extension, Coding coding, String code, UnaryOperator<String> categorize) {
		Extension category = extension.getExtensionByUrl(PIDParser.OMB_CATEGORY);
	
		if ("ASKU".equals(code) || "UNK".equals(code)) {
			// Set OMB category to code and quit
			extension.setExtension(null);  // Clear prior extensions, only ombCategory can be present
			if (category == null) {
				extension.addExtension(PIDParser.OMB_CATEGORY, new Coding(Systems.NULL_FLAVOR, code, null));
			} else {
				category.setValue(new Coding(Systems.NULL_FLAVOR, code, null));
			}
			return true;
		}
		
		// Figure out category and detail codes
		Coding categoryCode = new Coding().setCode(categorize.apply(code)).setSystem(Systems.CDCREC);
		if (categoryCode.hasCode()) {
			// if category has a code, it was a legitimate race code
			if (category == null) {
				extension.addExtension(PIDParser.OMB_CATEGORY, categoryCode);
			} else {
				category.setValue(categoryCode);
			}
	
			// If if smells enough like a CDCREC code
			if (coding.getCode().matches("^[1-2]\\d{3}-\\d$")) {
				// Add to detailed.
				extension.addExtension("detailed", coding);
			}
			if (coding.hasDisplay() && !extension.hasExtension("text")) {
				extension.addExtension("text", coding.getDisplayElement());
			}
		}
		return false;
	}

	/**
	 * Map a CodeableConcept containing a CDC Race code into a US Core extension
	 * containing OMB Category and Detailed codes where possible.
	 * 
	 * @param race	The race code provided
	 * @param raceExtension	The extension to populate
	 * @return	true if a valid code was found and set
	 */
	private static boolean setCDCREC(CodeableConcept race, Extension raceExtension) {
		for (Coding coding: race.getCoding()) {
			String code = coding.getCode();
			if (StringUtils.isBlank(code)) {
				continue;
			}
			
			List<String> systemNames = Systems.getSystemNames(coding.getSystem());
			
			if ((!coding.hasSystem() || systemNames.contains(Systems.CDCREC)) &&
				setCategoryAndDetail(raceExtension, coding, code, RaceAndEthnicity::getRaceCategory)
			) {
				return true;
			} 
			if (RaceAndEthnicity.setUnknown(raceExtension, code)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Map a CodeableConcept containing a legacy race codes into a US Core extension
	 * containing OMB Category where possible.
	 * 
	 * @param race	The code provided
	 * @param raceExtension	The extension to populate
	 * @param text	An existing text extension, if any
	 * @return	true if a valid code was found and set
	 */
	private static boolean setLegacy(CodeableConcept race, Extension raceExtension, Extension text) {
		for (Coding coding: race.getCoding()) {
			String code = coding.getCode();
			
			if (StringUtils.isBlank(code)) {
				continue;
			}
			
			if (text == null) {
				text = new Extension("text", new StringType(code));
				raceExtension.addExtension(text);
			}
	
			// Deal with common legacy codes
			Coding c = new Coding(Systems.CDCREC, RaceAndEthnicity.getRaceCategory(code), null);
			if (c.hasCode()) {
				Extension category = raceExtension.getExtensionByUrl(PIDParser.OMB_CATEGORY);
				if (category == null) {
					raceExtension.addExtension(PIDParser.OMB_CATEGORY, c);
				} else {
					category.setValue(c);
				}
				return true;
			} 
		}
		return false;
	}

	/**
	 * Set the text extension if there is text in the CodeableConcept
	 * @param race	The race code
	 * @param raceExtension	The extension to populate
	 * @return	The text extension, if any
	 */
	private static Extension setRaceText(CodeableConcept race, Extension raceExtension) {
		Extension text = null;
		if (race.hasText()) {
			text = new Extension("text", new StringType(race.getText()));
			raceExtension.addExtension(text);
		}
		return text;
	}

	/**
	 * Sets unknown values where appropriate
	 */
	private static boolean setUnknown(Extension raceExtension, String code) {
		// Don't bother looking at systems, just codes.
		
		// Two switches are merged. This catches errors when DATA_ABSENT codes are used in
		// the NULL_FLAVOR system and vice-versa.
		switch (code) {
		case "asked-unknown", // Set OMB category to ASKU and quit  
			 "ASKU": 
			raceExtension.setExtension(null);  // Clear prior extensions
			raceExtension.addExtension(PIDParser.OMB_CATEGORY, new Coding(Systems.NULL_FLAVOR, "ASKU", null));
			return true;
		case "unknown", // Set OMB category to UNK and quit
			 "UNK":  
			raceExtension.setExtension(null);  // Clear prior extensions
			raceExtension.addExtension(PIDParser.OMB_CATEGORY, new Coding(Systems.NULL_FLAVOR, "UNK", null));
			return true;
		default:
			return false;
		}
	}
	private RaceAndEthnicity() {
		// No instantiation
	}

}
