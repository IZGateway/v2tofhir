package gov.cdc.izgw.v2tofhir.utils;

import org.apache.commons.lang3.StringUtils;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Group;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.model.Segment;
import ca.uhn.hl7v2.model.Structure;
import ca.uhn.hl7v2.model.Type;

/**
 * This utility class contains methods for manipulating and converting between
 * HAPI V2 Terser and FhirPath paths (as applied over V2 models). 
 */
public class PathUtils {
	private PathUtils() {}
	/**
	 * Convert a FhirPath to a TerserPath
	 * @param path	The FhirPath expression to convert
	 * @return	A Terser path representation
	 */
	public static String fhirPathToTerserPath(String path) {
		path = "/" + path;
		// Replace . with /
		path = path.replace(".", "/");
		while (path.contains("[")) {
			String before = StringUtils.substringBefore(path, "[").trim();
			String inside = StringUtils.substringBetween(path, "[", "]").trim();
			String after = StringUtils.substringAfter("]", 0).trim();
			path = before + "[" + (StringUtils.isNumeric(inside) ? Integer.parseInt(inside)  + 1 : inside) + "]" + after; 
		}
		return path;
	}

	/**
	 * Given a structure, get a Terser path for it
	 * @param s The structure to get a Terser path for
	 * @return The terser path to the specified structure within a message.
	 * @throws HL7Exception If a path to the structure cannot be found in its message.
	 */
	public static String getTerserPath(Structure s) throws HL7Exception {
		if (s instanceof Message) {
			return "";
		}
		String myName = s.getName();
		int myRep = 1;
		Group g = s.getParent();
		if (g == null) {
			return s.getName();
		}
		Structure[] children = g.getAll(myName);
		for (myRep = 0; myRep < children.length; myRep++) {
			String path = getTerserPath(g) + "/" + myName;
			if (children[myRep] == s) {
				if (myRep == 0) {
					return path;
				} else {
					return path + "(" + (myRep+1) + ")";
				}
			}
		}
		if (s instanceof Segment) {
			return g.getName() + "/" + myName;
		}
		throw new HL7Exception("Cannot find " + s.getName() + " in " + g.getName());
	}

	/**
	 * Convert a Terser path to a FhirPath
	 * @param path	The Terser path expression to convert
	 * @return	A FhirPath representation
	 */
	public static String terserPathToFhirPath(String path) {
		// Remove initial / 
		if (path.startsWith("/")) {
			path = path.substring(1);
		}
		// Replace / with .
		path = path.replace("/", ".");
		// Replace ( and ) with [ and ]
		while (path.contains("(")) {
			String before = StringUtils.substringBefore(path, "(").trim();
			String inside = StringUtils.substringBetween(path, "(", ")").trim();
			String after = StringUtils.substringAfter(")", 0).trim();
			path = before + "[" + (StringUtils.isNumeric(inside) ? Integer.parseInt(inside)  - 1 : inside) + "]" + after; 
		}
		return path;
	}

	/**
	 * Convert a Path in V2 encoding of an ERL data type to a FhirPath
	 * @param path	The V2 encoding of an ERL data type.
	 * @return	The converted FhirPath
	 */
	public static String v2ToFHIRPath(String path) {
		
		String[] parts = path.split("^");
		int[] numbers = new int[parts.length];
		Type type = null;
		StringBuilder fhirPath = new StringBuilder();
		for (int i = 0; i < parts.length; i++) {
			if (i > 0 && StringUtils.isEmpty(parts[i])) {
				parts[i] = "1";
				numbers[i] = 1;
			} else if (StringUtils.isNotEmpty(parts[i])) {
				numbers[i] = StringUtils.isNumeric(parts[i]) ? Integer.parseInt(parts[i]): -1;
			} 
			switch (i) {
			case 0: // Segment name
				fhirPath.append(parts[0].toUpperCase());
				break;
			case 1, 3: // Repetition number
				if (numbers[i] > 0) {
					fhirPath.append("[").append(numbers[i]).append("]");
				}
				break;
			case 2: // Field position
				fhirPath.append(".").append(parts[2]);
				break;
			case 4: // Component or subcomponent number
				// Load segment from v2.8.1 structures.  If that fails, load from 2.5.1
				type = ParserUtils.getFieldType(parts[0], numbers[2]);
				fhirPath.append(type == null ? "UNKNOWN" : type.getName()).append(numbers[i]);
				break;
			case 5:
				type = ParserUtils.getComponent(type, numbers[i]);
				fhirPath.append(type == null ? "UNKNOWN" : type.getName()).append(numbers[i]);
				break;
			default:
				break;
			}
		}
		return fhirPath.toString();
	}

}
