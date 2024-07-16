package gov.cdc.izgw.v2tofhir.datatype;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.HumanName.NameUse;
import org.hl7.fhir.r4.model.StringType;

import ca.uhn.hl7v2.model.Composite;
import ca.uhn.hl7v2.model.Primitive;
import ca.uhn.hl7v2.model.Type;
import gov.cdc.izgw.v2tofhir.converter.DatatypeConverter;
import gov.cdc.izgw.v2tofhir.utils.ParserUtils;

/**
 * Parser for Human Names.
 */
public class HumanNameParser implements DatatypeParser<HumanName> {
	private static Set<String> prefixes = new HashSet<>(
	Arrays.asList("mr", "mrs", "miss", "sr", "br", "fr", "dr"));
	private static Set<String> suffixes = new HashSet<>(
	Arrays.asList("jr", "sr", "i", "ii", "iii", "iv", "v"));
	// An affix is a prefix to a family name
	// See https://en.wikipedia.org/wiki/List_of_family_name_affixes
	private static Set<String> affixes = new HashSet<>(Arrays.asList("Abu",
			"al", "bet", "bint", "el", "ibn", "ter", "fer", "ait", "a\u00eft", "at",
			"ath", "de", "'s", "'t", "ter", "van", "vande", "vanden", "vander",
			"van't", "von", "bath", "bat", "ben", "bin", "del", "degli",
			"della", "di", "a", "ab", "ap", "ferch", "verch", "verch", "erch",
			"af", "alam", "\u0101lam", "bar", "ch", "chaudhary", "da", "das", "de",
			"dele", "dos", "du", "e", "fitz", "i", "ka", "kil", "gil", "mal",
			"mul", "la", "le", "lu", "m'", "mac", "mc", "mck", "mhic", "mic",
			"mala", "na", "nga", "ng\u0101", "nic", "ni", "n\u00ed", "nin", "o", "\u00f3",
			"ua", "ui", "u\u00ed", "oz", "\u00f6z", "pour", "te", "tre", "war"));
	private static Set<String> degrees = new HashSet<>(Arrays.asList("ab", "ba",
	"bs", " be", "bfa", "btech", "llb", "bsc", "ma", "ms", "mfa", "llm",
	"mla", "mba", "msc", "meng", "mbi", "jd", "md", "do", "pharmd",
	"dmin", "phd", "edd", "dphil", "dba", "lld", "engd", "esq"));

	enum NamePart {
		PREFIX,
		PREFIXANDSUFFIX,
		GIVEN,
		AFFIX,
		FAMILY,
		SUFFIX,
		NAME
	}
	
	/**
	 * Construct a HumanNameParser
	 */
	public HumanNameParser() {
		// Construct a default HumanNameParser
	}
	@Override
	public Class<HumanName> type() {
		return HumanName.class;
	}

	@Override
	/**
	 * Parse a human name from a string. The parser recognizes common prefixes and suffixes and puts
	 * them in the prefix and suffix fields of the human name.  It puts the first space separated string
	 * into the first given name, and any susbsequent strings except the last.
	 * 
	 * @param name The name to parse
	 * @return The parsed name in a HumanName object
	 */
	public HumanName fromString(String name) {
		HumanName hn = new HumanName();
		hn.setText(name);
		String[] parts = name.split("\\s");
		StringBuilder familyName = new StringBuilder();
		for (String part : parts) {
			switch (classifyNamePart(part)) {
			case PREFIXANDSUFFIX:  
				if (!hn.hasGiven() && familyName.isEmpty()) {
					hn.addPrefix(part);
					break;
				} else if (!familyName.isEmpty()) {	// Some are both a prefix and a suffix
					hn.addSuffix(part);
				} else {
					familyName.append(part).append(" ");
				}
			case PREFIX:  
				if (!hn.hasGiven() && !hn.hasFamily()) {
					hn.addPrefix(part);
					break;
				} else if (hn.hasFamily()) {	// Some are both a prefix and a suffix
					hn.addSuffix(part);
				}
				break;
			case AFFIX:
				if (!hn.hasGiven()) {
					hn.addGiven(part);
				} else {
					familyName.append(part).append(" ");
				} 
				break;
			case FAMILY, GIVEN, NAME:
				if (!familyName.isEmpty()) {
					familyName.append(part).append(" ");
				} else {
					hn.addGiven(part);
				}
				break;
			case SUFFIX:
				hn.addSuffix(part);
				break;
			default:
				break;
			
			}
		}
		List<StringType> given = hn.getGiven();
		if (familyName.isEmpty() && !given.isEmpty()) {
			String family = given.get(given.size() - 1).toString();
			hn.setFamily(family);
			given.remove(given.size() - 1);
			hn.setGiven(null);
			for (StringType g: given) {
				hn.addGiven(g.toString());
			}
		} else if (!familyName.isEmpty()) {
			familyName.setLength(familyName.length()-1);  // Remove terminal " "
			hn.setFamily(familyName.toString());
		}
		if (hn.isEmpty()) {
			return null;
		}
		return hn;
	}
	
	private NamePart classifyNamePart(String part) {
		if (isPrefix(part)) {
			return isSuffix(part) ? NamePart.PREFIXANDSUFFIX : NamePart.PREFIX;
		}
		if (isAffix(part)) {
			return NamePart.AFFIX;
		}
		if (isSuffix(part) || isDegree(part)) {
			return NamePart.SUFFIX;
		}
		return NamePart.NAME;
	}
	
	@Override
	public HumanName convert(Type t) {
		t = DatatypeConverter.adjustIfVaries(t);
		if (t instanceof Primitive pt) {
			return fromString(pt.getValue());
		} 
		
		if (t instanceof Composite comp) {
			Type[] types = comp.getComponents();
			switch (t.getName()) {
				case "CNN" :
					return HumanNameParser.parse(types, 1, -1);
				case "XCN" :
					HumanName hn = HumanNameParser.parse(types, 1, 9);
					if (hn != null && types.length > 20 && types[20] != null) {
						String suffix = ParserUtils.toString(types[20]);
						hn.addSuffix(suffix);
					}
					return hn;
				case "XPN" :
					return HumanNameParser.parse(types, 0, 6);
				default :
					break;
			}
		}
		return null;
	}

	@Override 
	public Type convert(HumanName name) {
		return null;
	}

	static HumanName parse(Type[] types, int offset, int nameTypeLoc) {
		HumanName hn = new HumanName();
		for (int i = 0; i < 6; i++) {
			String part = ParserUtils.toString(types[i + offset]);
			if (StringUtils.isBlank(part)) {
				continue;
			}
			switch (i) {
				case 0 : // Family Name (as ST in HL7 2.3)
					hn.setFamily(part);
					break;
				case 1 : // Given Name
					hn.addGiven(part);
					break;
				case 2 : // Second and Further Given Name or Initials
							// Thereof
					hn.addGiven(part);
					break;
				case 3 : // Suffix
					hn.addSuffix(part);
					break;
				case 4 : // Prefix
					hn.addPrefix(part);
					break;
				case 5 : // Degree
					hn.addSuffix(part);
					break;
				default :
					break;
			}
		}
		Type type = DatatypeConverter.adjustIfVaries(types, nameTypeLoc); 
				
		if (type instanceof Primitive pt) {
			String nameType = pt.getValue();
			hn.setUse(toNameUse(nameType));
		}
		if (hn.isEmpty()) {
			return null;
		}
		return hn;
	}

	private static NameUse toNameUse(String nameType) {
		if (nameType == null) {
			return null;
		}
		switch (StringUtils.upperCase(nameType)) {
			case "A" : // Assigned
				return NameUse.USUAL;
			case "D" : // Customary Name
				return NameUse.USUAL;
			case "L" : // Official Registry Name
				return NameUse.OFFICIAL;
			case "M" : // Maiden Name
				return NameUse.MAIDEN;
			case "N" : // Nickname
				return NameUse.NICKNAME;
			case "NOUSE" : // No Longer To Be Used
				return NameUse.OLD;
			case "R" : // Registered Name
				return NameUse.OFFICIAL;
			case "TEMP" : // Temporary Name
				return NameUse.TEMP;
			case "B", // Birth name
				 "BAD", // Bad Name
				 "C", // Adopted Name
				 "I", // Licensing Name
				 "K", // Business name
				 "MSK", // Masked
				 "NAV", // Temporarily Unavailable
				 "NB", // Newborn Name
				 "P", // Name of Partner/Spouse
				 "REL", // Religious
				 "S", // Pseudonym
				 "T", // Indigenous/Tribal
				 "U" : // Unknown
			default :
				return null;
		}
	}

	private static boolean isAffix(String part) {
		return affixes.contains(part.toLowerCase());
	}

	private static boolean isPrefix(String part) {
		return prefixes.contains(part.toLowerCase().replace(".", ""));
	}

	private static boolean isSuffix(String part) {
		return suffixes.contains(part.toLowerCase().replace(".", ""));
	}

	private static boolean isDegree(String part) {
		return degrees.contains(part.toLowerCase().replace(".", ""));
	}

}
