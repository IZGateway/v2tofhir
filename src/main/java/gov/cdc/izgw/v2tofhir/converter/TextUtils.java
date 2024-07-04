package gov.cdc.izgw.v2tofhir.converter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.Address;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.ContactPoint;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.PrimitiveType;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Type;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Composite;
import ca.uhn.hl7v2.model.Varies;
import gov.cdc.izgw.v2tofhir.converter.datatype.ContactPointParser;

/**
 * Utility class to convert FHIR and V2 Objects to and from strings used in text.
 * The toText() Methods in this class attempt to create human readable strings 
 * that contain the information in the type in a standardized form as would be commonly used in 
 * human readable text. 
 * 
 * The to{fhirType} and to{v2Type} methods convert strings created by these method (and possibly
 * other sources) to the specified type.
 * 
 * If t = TextUtils.toType(TextUtils.toText(x)), then t is generally equivalent to x but may be missing
 * some data.  
 */
public class TextUtils {
	private TextUtils() {}
	
	/**
	 * Convert parts of an address into a text string. This method is the same one used to convert both FHIR and V2 address types
	 * into a string so that there is a common string representation.
	 * 
	 * @param country	The country
	 * @param city		The city
	 * @param state		The state
	 * @param postalCode	The postalCode
	 * @param district	The district, county or other geographic designator for the address
	 * @param lines	The address lines
	 * @return A string representing the address
	 */
	public static String addressToText(String country, String city, String state, String postalCode, String district, Object ... lines) {
		StringBuilder b = new StringBuilder();
		for (Object line : lines) {
			appendIfNonBlank(b, line, "\n");
		}
		appendIfNonBlank(b, district, "\n");
		
		if (StringUtils.equalsAny(StringUtils.upperCase(country), "MX", "MEX", "MEXICO")) {
			appendIfNonBlank(b, postalCode, "  ");
			appendIfNonBlank(b, city, ", ");
			appendIfNonBlank(b, state, null);
		} else {
			appendIfNonBlank(b, city, ", ");
			appendIfNonBlank(b, state, "  ");
			appendIfNonBlank(b, postalCode, null);
		}
		if (StringUtils.endsWithAny(b, ", ", "  ")) {
			b.setLength(b.length() - 2);
		} else if (StringUtils.endsWith(b, " ")) {
			b.setLength(b.length() - 1);
		}
		if (StringUtils.isNotBlank(b)) {
			b.append("\n");
		}
		// Country goes on a line of its own.
		appendIfNonBlank(b, country, "\n");
		return b.toString();
		
	}

	/**
	 * Convert parts of a coding (or multiple codings) into a text string. This method is the same one used to convert 
	 * both FHIR and V2 address types into a string so that there is a common string representation.
	 * 
	 * The very first element in parts is the "original text" for the coding, and will be the first text that is output, then
	 * the coded strings.
	 * 
	 * The next, and each subsequent group of parts is in the form code, display, system
	 * 
	 * @param parts	The parts of the coding(s). Each coding can have three strings, any of which can be null; the display name,
	 * the code, and the system.  These are written in the form:
	 * 
	 * 	Original Text, Display Name (Coding-System Code) [, Display Name (Coding-System Code)] 
	 * 
	 * so that a concept describing a heart attack in ICD9CM and SNOMED-CT for Heart Attack would be converted to text as:
	 * 
	 * 	Heart Attack, Myocardial Infarction (ICD9CM 410.41), Myocardial infarction (SNOMEDCT 22298006)
	 * 
	 * If original text is missing, it would appear as 
	 * 
	 * 	Myocardial Infarction (ICD9CM 410.41), Myocardial infarction (SNOMEDCT 22298006)
	 * 
	 * If the Display name is missing is missing for any code only the text between () is written for each code found 
	 * e.g., (ICD9CM 410.41), (SNOMEDCT 22298006)
	 * If the coding system is missing for any value, and the code is present, this would be written as (410.41)
	 * If the code is missing, this would be written as (ICD9CM ) (note the trailing space).
	 * If both coding system and code are missing, nothing appears. 
	 * 
	 * @return	A string representation of the coding(s)
	 */

	public static String codingToText(String ... parts) {
		StringBuilder b = new StringBuilder();
	
		if (parts.length > 0) {
			appendIfNonBlank(b, parts[0], null);
		}
		
		for (int i = 1; i < parts.length; i += 3) {
			String code = parts.length > i ? parts[i] : "";
			String display = parts.length > (i+1) ? parts[i+1] : ""; 
			String system = parts.length > (i+2) ? parts[i+2] : "";
			if (StringUtils.isAllEmpty(code, display, system)) {
				continue;
			}
			if (!b.isEmpty()) {
				b.append(", ");
			}
			appendIfNonBlank(b, display, null);
			if (StringUtils.isNotBlank(code) && StringUtils.isNotBlank(system)) {
				// For converting to Strings, if system is a URL, make it 
				// a more human readable name like people would expect.
				if (StringUtils.contains(system, ":")) {
					List<String> l = Systems.getSystemNames(system);
					// First name is always preferred name, second is the V2 common name
					if (l.size() > 0) {
						system = l.get(0);
						if (l.size() > 1) {
							// If there is a V2 name, use that.
							system = l.get(1);
						}
					}
				}
				b.append(" (").append(system).append(" ").append(code).append(")");
			} else if (StringUtils.isNotBlank(code)) {
				// The space after ( makes it easily parsed as a code with an empty system
				b.append(" ( ").append(code).append(")"); 
			}
		}
		return b.toString();
	}
	
	/**
	 * Convert a human name to text in the form {prefix} {givens} {family} suffix
	 * 
	 * @param prefix	The prefix or null if there is none
	 * @param family	The family name or null if there is none
	 * @param suffix	The suffix or null if there is none
	 * @param givens	The given names or null or an empty array if there are none
	 * @return a string in the form {prefix} {givens} {family} suffix
	 */
	public static String humanNameToText(String prefix, String family, String suffix, Object ... givens) {
		StringBuilder b = new StringBuilder();
		appendIfNonBlank(b, prefix, " ");
		if (givens != null) {
			for (Object given: givens) {
				appendIfNonBlank(b, given, " ");
			}
		}
		appendIfNonBlank(b, family, " ");
		appendIfNonBlank(b, suffix, null);
		return rightTrim(b).toString();
	}
	
	/**
	 * Convert an identifier to text in the general form: {type}# {identifier}-{checkDigit}
	 * 
	 * @param type	The type of identifier (e.g., DLN, SSN, MR) or null if not present
	 * @param value	The identifier or null if not present
	 * @param checkDigit	A check digit to append to the identifier if present
	 * @return a string in the general form: {type}# {identifier}-{checkDigit}
	 */
	public static String identifierToText(String type, String value, String checkDigit) {
		StringBuilder b = new StringBuilder();
		appendIfNonBlank(b, type, "# ");
		appendIfNonBlank(b, value, null);
		if (StringUtils.isNotBlank(checkDigit)) {
			b.append("-").append(checkDigit);
		}
		return b.toString();
	}
	
	/**
	 * Convert a quantity to text in the general form {value} {unit}
	 * 
	 * @param value	The value
	 * @param code	The code (presently not used)
	 * @param unit The units
	 * @return	text in the general form {value} {unit}
	 */
	public static String quantityToText(Number value, String code, String unit) {
		return quantityToText(value.toString(), code, unit);
	}

	/**
	 * Convert a quantity to text in the general form {value} {unit}
	 * 
	 * @param value	The value
	 * @param code The code (presently not used)
	 * @param unit The units
	 * @return	text in the general form {value} {unit}
	 */
	public static String quantityToText(String value, String code, String unit) {
		StringBuilder b = new StringBuilder();
		if (value != null) {
			if (value.startsWith("-.")) {
				value = "-0." + value.substring(2);
			} else if (value.startsWith("+.")) {
				value = "0." + value.substring(2);
			} else if (value.startsWith(".")) {
				value = "0." + value.substring(1);
			}
		}
		appendIfNonBlank(b, value, " ");
		if (StringUtils.isBlank(unit)) {
			appendIfNonBlank(b, code, null);
		} else {
			appendIfNonBlank(b, unit, null);
		}
		return rightTrim(b).toString();
	}
	
	/**
	 * Convert a V2 Composite Type to a standardize string output: case possible
	 * with multiple lines of text representing the content of the type.
	 * 
	 * @param v2Type The type to convert
	 * @return A string representation of that type
	 */
	public static String toString(ca.uhn.hl7v2.model.Type v2Type) {
		Composite comp = null;
		if (v2Type instanceof Varies v) {
			v2Type = v.getData();
		}
		try {
			if (v2Type == null || v2Type.isEmpty()) {
				return "";
			}
		} catch (HL7Exception e1) {
			return "";
		}
		if (v2Type instanceof Composite c) {
			comp = c;
		}
		int offset = 0;
		switch (v2Type.getName()) {
		case "AD":
			return addressToText(
					ParserUtils.toString(comp, 5), 
					ParserUtils.toString(comp, 2),
					ParserUtils.toString(comp, 3),
					ParserUtils.toString(comp, 4),
					ParserUtils.toString(comp, 7),
					ParserUtils.toString(comp, 1)
				);
		case "XAD":
			return addressToText(
				ParserUtils.toString(comp, 5), 
				ParserUtils.toString(comp, 2),
				ParserUtils.toString(comp, 3),
				ParserUtils.toString(comp, 4),
				ParserUtils.toString(comp, 8),
				ParserUtils.toString(comp, 0),
				ParserUtils.toString(comp, 1)
			);
				
		case "SAD":
			return ParserUtils.toString(comp);
		case "TN":
			return ParserUtils.toString(comp);
		case "XTN":
			return xtnToText(comp.getComponents());
		case "CE", "CF", "CNE", "CWE":
			return codingToText(ParserUtils.toStrings(comp, 8, 0, 1, 2, 3, 4, 5, 9, 10, 11));
		case "CX":
			String type = ParserUtils.toString(comp, 4);
			if (StringUtils.isBlank(type)) {
				type = ParserUtils.toString(comp, 3);
			}
			return identifierToText(type, ParserUtils.toString(comp, 0), ParserUtils.toString(comp, 1));
		case "NM":
			return quantityToText(ParserUtils.toString(v2Type), null, null);
		case "CQ":
			return quantityToText(ParserUtils.toString(comp, 0), ParserUtils.toString(comp, 1), ParserUtils.toString(comp, 1, 1));
		case "HD":
			return Objects.toString(ParserUtils.toString(comp), ParserUtils.toString(comp, 1));
		case "EI":
			return identifierToText(ParserUtils.toString(comp, 1), ParserUtils.toString(comp, 0), null);
		case "ID", "IS", "ST", "EIP", "XON":
			return ParserUtils.toString(v2Type);
		
		case "CNN", "XCN":
			offset = 1;
			// fall through, CNN and XCN are like XPN but start one component later
		case "XPN":
			String[] sufixes = { ParserUtils.toString(comp, offset + 3), ParserUtils.toString(comp, offset + 5) };
			Object[] givens = { ParserUtils.toString(comp, offset + 1), ParserUtils.toString(comp, offset + 2) };
			return TextUtils.humanNameToText(ParserUtils.toString(comp, offset + 4), ParserUtils.toString(comp, offset + 0), StringUtils.join(sufixes, " "), givens);
		case "ERL", "MSG":
		default:
			try {
				return v2Type.encode();
			} catch (HL7Exception e) {
				return null;
			}
		}
	}

	private static String xtnToText(ca.uhn.hl7v2.model.Type[] types) {
		for (int i : Arrays.asList(1, 4, 5, 12)) {
			String value = i == 5 ? ContactPointParser.fromXTNparts(types) : ParserUtils.toString(types, i-1);
			if (StringUtils.isNotBlank(value)) {
				return value;
			}
		}
		return "";
	}

	/**
	 * Convert a FHIR Type to a standardized string output: case possible with
	 * multiple lines of text representing the content of the type.
	 * 
	 * @param fhirType The type to convert
	 * @return A string representation of the FHIR type.
	 */
	public static String toString(Type fhirType) {
		if (fhirType == null || fhirType.isEmpty()) {
			return "";
		}
		if (fhirType instanceof PrimitiveType<?> pt) {
			return StringUtils.defaultIfBlank(pt.asStringValue(), "");
		}
		switch (fhirType.fhirType()) {
		case "Address":
			return toText((Address) fhirType);
		case "CodeableConcept":
			return toText((CodeableConcept) fhirType);
		case "ContactPoint":
			return toText((ContactPoint) fhirType);
		case "Coding":
			return toText((Coding)fhirType);
		case "HumanName":
			return toText((HumanName)fhirType);
		case "Identifier":
			return toText((Identifier)fhirType);
		case "Quantity":
			Quantity quantity = (Quantity)fhirType;
			return toText(quantity);
		default:
			return "";
		}
	}
	
	/**
	 * Convert an Address to text. 
	 * @param addr	The address to convert
	 * @return	A text representation of the address
	 * @see #addressToText(String,String,String,String,String,Object...)
	 */
	public static String toText(Address addr) {
		if (addr == null || addr.isEmpty()) {
			return "";
		}
		return addressToText(addr.getCountry(), addr.getCity(), addr.getState(), addr.getPostalCode(), addr.getDistrict(), addr.getLine().toArray());
	}
	
	/**
	 * Convert a CodeableConcept to text. 
	 * @param codeableConcept The concept to convert
	 * @return	A text representation of the concept
	 * @see #codingToText
	 */
	public static String toText(CodeableConcept codeableConcept) {
		if (codeableConcept == null || codeableConcept.isEmpty()) {
			return "";
		}
		List<String> l = new ArrayList<>();
		l.add(codeableConcept.hasText() ? codeableConcept.getText() : null);
		for (Coding coding : codeableConcept.getCoding()) {
			l.add(coding.hasCode() ? coding.getCode() : null);
			l.add(coding.hasDisplay() ? coding.getDisplay() : null);
			l.add(coding.hasSystem() ? coding.getSystem() : null);
		}
		return codingToText(l.toArray(new String[0]));
	}

	/**
	 * Convert a Coding to text. 
	 * @param coding The concept to convert.
	 * @return	A text representation of the coding
	 * @see #codingToText
	 */
	public static String toText(Coding coding) {
		return codingToText(coding.getCode(), coding.getDisplay(), coding.getSystem());
	}

	/**
	 * Convert a contact point to a text string
	 * @param cp	The contact point to convert
	 * @return	The text string representing that contact point.
	 */
	public static String toText(ContactPoint cp) {
		return cp.getValue();
	}
	/**
	 * Convert a HumanName to text. 
	 * @param name The name to convert
	 * @return	A text representation of the name
	 * @see #humanNameToText
	 */
	public static String toText(HumanName name) {
		return humanNameToText(name.getPrefixAsSingleString(), name.getFamily(), name.getSuffixAsSingleString(), name.getGiven().toArray());
	}

	/**
	 * Convert an Identifier to text. 
	 * @param identifier The identifier to convert
	 * @return	A text representation of the identifier
	 * @see #identifierToText
	 */
	public static String toText(Identifier identifier) {
		if (identifier == null || identifier.isEmpty()) {
			return "";
		}
		String type = null;
		if (identifier.hasType()) {
			CodeableConcept cc = identifier.getType();
			Coding coding = cc.getCodingFirstRep();
			type = Objects.toString(cc.getText(),  
							Objects.toString(coding.getCode(), 
								Systems.toTextName(identifier.getSystem())));
		} else {
			List<String> types = Systems.getSystemNames(identifier.getSystem());
			if (!types.isEmpty()) {
				type = types.get(0);
				if (types.size() > 1) {
					type = types.get(1);
				}
			} else {
				type = identifier.getSystem();
			}
		}
		return identifierToText(type, identifier.getValue(), null);
	}
	
	/**
	 * Convert a Quantity to text. 
	 * @param quantity The quantity to convert
	 * @return	A text representation of the quantity
	 * @see #quantityToText(String,String,String)
	 */
	public static String toText(Quantity quantity) {
		return TextUtils.quantityToText(quantity.getValue(), quantity.getCode(), quantity.getUnit());
	}
	
	/** 
	 * Append the first string and any extra strings to b if the first string is not blank
	 * @param b	The StringBuilder to append to
	 * @param first	The string to append if non-empty
	 * @param extra	The extra text that will be appended if both first and extra are non-empty
	 */
	private static void appendIfNonBlank(StringBuilder b, Object first, String extra) {
		if (first != null && StringUtils.isNotBlank(first.toString())) {
			b.append(first);
			if (extra != null) {
				b.append(extra);
			}
		}
	}

	/**
	 * Remove any trailing whitespace from a StringBuilder
	 * @param b	The string builder
	 * @return	The adjusted string builder
	 */
	private static StringBuilder rightTrim(StringBuilder b) {
		int len = b.length();
		while (len > 0 && Character.isWhitespace(b.charAt(len - 1))) {
			--len;
		}
		b.setLength(len);
		return b;
	}

	/** 
	 * Convert a string generated from {@link #toText(CodeableConcept)} back to
	 * a CodeableConcept
	 *   
	 * @param actualString	The string to convert
	 * @return	A CodeableConcept created by parsing the string
	 */
	public static CodeableConcept toCodeableConcept(String actualString) {
		String[] parts = actualString.split(", ");
		CodeableConcept cc = new CodeableConcept();
		
		for (String part: parts) {
			String display = StringUtils.substringBefore(part, "(");
			String codeAndSystem = StringUtils.substringBetween(part, "(", ")");
			String system = StringUtils.substringBefore(codeAndSystem, " ");
			String code = StringUtils.substringAfter(codeAndSystem, " ");
			if (!cc.hasText() && 
				StringUtils.isNotEmpty(display) && 
				StringUtils.isAllEmpty(code, system)
			) {
				cc.setText(display);
			} else {
				Coding coding = new Coding(system, code, display);
				if (!coding.isEmpty()) {
					cc.addCoding(coding);
				}
			}
		}
		return cc;
	}
}
