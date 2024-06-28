package gov.cdc.izgw.v2tofhir.converter;

import java.util.Objects;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.Address;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.PrimitiveType;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Type;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Composite;

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
	 * @return
	 */
	public static String addressToText(String country, String city, String state, String postalCode, String district, Object ... lines) {
		StringBuilder b = new StringBuilder();
		for (Object line : lines) {
			appendIfNonBlank(b, line, "\n");
		}
		appendIfNonBlank(b, district, "\n");
		
		if (StringUtils.equalsAny(StringUtils.upperCase(country), "MX", "MEX", "MEXICO")) {
			appendIfNonBlank(b, city, ", ");
			appendIfNonBlank(b, state, "  ");
			appendIfNonBlank(b, postalCode, "  ");
		} else {
			appendIfNonBlank(b, city, ", ");
			appendIfNonBlank(b, state, "  ");
			appendIfNonBlank(b, postalCode, ", ");
		}
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
	 * If the coding system is missing for any value, only the code is , this would be written as (410.41)
	 * If the code is missing, this would be written as (ICD9CM <unknown>)
	 * If both coding system and code are missing, nothing appears 
	 * 
	 * @param parts	The parts of the coding(s)
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
			if (!b.isEmpty()) {
				b.append(", ");
			}
			appendIfNonBlank(b, display, null);
			if (StringUtils.isNotBlank(code) && StringUtils.isNotBlank(system)) {
				b.append(" (").append(system).append(" ").append(code).append(")");
			} else if (StringUtils.isNotBlank(code)) {
				b.append(" (").append(code).append(")");
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
	 * @param identifier	The identifier or null if not present
	 * @param checkDigit	A check digit to append to the identifier if present
	 * @return a string in the general form: {type}# {identifier}-{checkDigit}
	 */
	public static String identifierToText(String type, String value, String checkDigit) {
		StringBuilder b = new StringBuilder();
		appendIfNonBlank(b, type, "#");
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
	 * @param unit The units
	 * @return	text in the general form {value} {unit}
	 */
	public static String quantityToText(Number value, String unit) {
		return quantityToText(value.toString(), unit);
	}

	/**
	 * Convert a quantity to text in the general form {value} {unit}
	 * 
	 * @param value	The value
	 * @param unit The units
	 * @return	text in the general form {value} {unit}
	 */
	public static String quantityToText(String value, String unit) {
		StringBuilder b = new StringBuilder();
		appendIfNonBlank(b, value, " ");
		appendIfNonBlank(b, unit, null);
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
					null,
					ParserUtils.toString(comp, 7),
					ParserUtils.toString(comp, 1)
				);
		case "XAD":
			return addressToText(
				ParserUtils.toString(comp, 5), 
				ParserUtils.toString(comp, 2),
				ParserUtils.toString(comp, 3),
				ParserUtils.toString(comp, 4),
				null,
				ParserUtils.toString(comp, 9),
				ParserUtils.toString(comp, 1)
			);
				
		case "SAD":
			return ParserUtils.toString(comp);
		case "CE", "CF", "CNE", "CWE":
			return codingToText(ParserUtils.toStrings(comp, 8, 0, 1, 2, 3, 4, 5, 9, 10, 11));
		case "CX":
			return identifierToText(ParserUtils.toString(comp, 4), ParserUtils.toString(comp, 0), ParserUtils.toString(comp, 1));
		case "CQ":
			return quantityToText(ParserUtils.toString(comp, 2), ParserUtils.toString(comp, 1));
		case "HD":
			return Objects.toString(ParserUtils.toString(comp), ParserUtils.toString(comp, 1));
		case "EI":
			return identifierToText(ParserUtils.toString(comp, 1), ParserUtils.toString(comp, 0), null);
		case "ID", "IS", "ST", "NM", "EIP", "XON":
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

	/**
	 * Convert a FHIR Type to a standardized string output: case possible with
	 * multiple lines of text representing the content of the type.
	 * 
	 * @param fhirType The type to convert
	 * @return
	 */
	public static String toString(Type fhirType) {
		if (fhirType.isEmpty()) {
			return "<unknown>";
		}
		
		if (fhirType instanceof PrimitiveType<?> pt) {
			return pt.asStringValue();
		}
		switch (fhirType.fhirType()) {
		case "Address":
			return toText((Address) fhirType);
		case "CodeableConcept":
			return toText((CodeableConcept) fhirType);
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
	 * @see addressToText
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
	 * @see codingToText
	 */
	public static String toText(CodeableConcept codeableConcept) {
		if (codeableConcept.hasCoding()) {
			Coding coding = codeableConcept.getCodingFirstRep();
			return codingToText(coding.getCode(), Objects.toString(codeableConcept.getText(), coding.getDisplay()), coding.getSystem());
		} else {
			return codeableConcept.hasText() ? codeableConcept.getText() : ""; 
		}
	}

	/**
	 * Convert a Coding to text. 
	 * @param coding The concept to convert.
	 * @return	A text representation of the coding
	 * @see codingToText
	 */
	public static String toText(Coding coding) {
		return codingToText(coding.getCode(), coding.getDisplay(), coding.getSystem());
	}

	/**
	 * Convert a HumanName to text. 
	 * @param humanName The name to convert
	 * @return	A text representation of the name
	 * @see humanNameToText
	 */
	public static String toText(HumanName name) {
		return humanNameToText(name.getPrefixAsSingleString(), name.getFamily(), name.getSuffixAsSingleString(), name.getGiven().toArray());
	}

	/**
	 * Convert an Identifier to text. 
	 * @param identifier The identifier to convert
	 * @return	A text representation of the identifier
	 * @see identifierToText
	 */
	public static String toText(Identifier identifier) {
		if (identifier == null || identifier.isEmpty()) {
			return "";
		}
		CodeableConcept cc = identifier.getType();
		Coding coding = cc.getCodingFirstRep();
		String type = Objects.toString(cc.getText(),  
						Objects.toString(coding.getClass(), 
							Systems.toTextName(identifier.getSystem())));
		return identifierToText(type, identifier.getValue(), null);
	}
	
	/**
	 * Convert a Quantity to text. 
	 * @param quantity The quantity to convert
	 * @return	A text representation of the quantity
	 * @see quantityToText
	 */
	public static String toText(Quantity quantity) {
		StringBuilder b = new StringBuilder();
		if (quantity.hasValue()) {
			b.append(quantity.getValue()).append(" ");
		}
		if (quantity.hasUnit()) {
			b.append(quantity.getUnit());
		} else if (quantity.hasCode()) {
			b.append(quantity.getCode());
		}
		return b.toString();
	}
	
	/** 
	 * Append the first string and any extra strings to b if the first string is not blank
	 * @param b	The StringBuilder to append to
	 * @param first	The string to append if non-empty
	 * @param extra	The extra text that will be appended if both first and extra are non-empty
	 */
	private static void appendIfNonBlank(StringBuilder b, Object first, String extra) {
		if (StringUtils.isNotBlank(first.toString())) {
			b.append(first);
		}
		if (extra != null) {
			b.append(extra);
		}
	}

	/**
	 * Remove any trailing whitespace from a StringBuilder
	 * @param b	The string builder
	 * @return	The adjusted string builder
	 */
	private static StringBuilder rightTrim(StringBuilder b) {
		int len = b.length();
		while (len > 0) {
			if (Character.isWhitespace(b.charAt(len - 1))) {
				--len;
			}
		}
		b.setLength(len);
		return b;
	}
}
