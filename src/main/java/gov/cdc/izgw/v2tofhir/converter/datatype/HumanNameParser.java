package gov.cdc.izgw.v2tofhir.converter.datatype;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.HumanName.NameUse;

import ca.uhn.hl7v2.model.Composite;
import ca.uhn.hl7v2.model.Primitive;
import ca.uhn.hl7v2.model.Type;
import gov.cdc.izgw.v2tofhir.converter.ParserUtils;

public class HumanNameParser implements DatatypeParser<HumanName> {
	private static Set<String> prefixes = new HashSet<>(
	Arrays.asList("mr", "mrs", "miss", "sr", "br", "fr", "dr"));
	private static Set<String> suffixes = new HashSet<>(
	Arrays.asList("jr", "sr", "i", "ii", "iii", "iv", "v"));
	// An affix is a prefix to a family name
	// See https://en.wikipedia.org/wiki/List_of_family_name_affixes
	private static Set<String> affixes = new HashSet<>(Arrays.asList("Abu",
			"al", "bet", "bint", "el", "ibn", "ter", "fer", "ait", "aït", "at",
			"ath", "de", "'s", "'t", "ter", "van", "vande", "vanden", "vander",
			"van't", "von", "bath", "bat", "ben", "bin", "del", "degli",
			"della", "di", "a", "ab", "ap", "ferch", "verch", "verch", "erch",
			"af", "alam", "ālam", "bar", "ch", "chaudhary", "da", "das", "de",
			"dele", "dos", "du", "e", "fitz", "i", "ka", "kil", "gil", "mal",
			"mul", "la", "le", "lu", "m'", "mac", "mc", "mck", "mhic", "mic",
			"mala", "na", "nga", "ngā", "nic", "ni", "ní", "nin", "o", "ó",
			"ua", "ui", "uí", "oz", "öz", "pour", "te", "tre", "war"));
	private static Set<String> degrees = new HashSet<>(Arrays.asList("ab", "ba",
	"bs", " be", "bfa", "btech", "llb", "bsc", "ma", "ms", "mfa", "llm",
	"mla", "mba", "msc", "meng", "mbi", "jd", "md", "do", "pharmd",
	"dmin", "phd", "edd", "dphil", "dba", "lld", "engd", "esq"));

	@Override
	public Class<HumanName> type() {
		return HumanName.class;
	}

	@Override
	public HumanName fromString(String name) {
		HumanName hn = new HumanName();
		hn.setText(name);
		String[] parts = name.split("\\s");
		boolean hasGiven = false;
		boolean hasPrefix = false;
		boolean hasSuffix = false;
		StringBuilder familyName = new StringBuilder();
		for (String part : parts) {
			if (!hasGiven && !hasPrefix && isPrefix(part)) {
				hn.addPrefix(part);
				hasPrefix = true;
			} else if (!hasGiven && (hasPrefix || !isPrefix(part))) {
				hn.addGiven(part);
				hasGiven = true;
				hasPrefix = true;
			} else if (hasGiven && familyName.isEmpty() && isAffix(part)) {
				familyName.append(part);
			} else if (!familyName.isEmpty() && (isSuffix(part) || isDegree(part))) {
				hn.addSuffix(part);
				hasSuffix = true;
			} else if (hasSuffix) {
				hn.addSuffix(part);
			}
		}
		if (!familyName.isEmpty()) {
			hn.setFamily(familyName.toString());
		}
		if (hn.isEmpty()) {
			return null;
		}
		return hn;
	}
	
	@Override
	public HumanName convert(Type t) {
		if (t instanceof Primitive pt) {
			return fromString(pt.getValue());
		} 
		
		if (t instanceof Composite comp) {
			Type[] types = comp.getComponents();
			switch (t.getName()) {
				case "CNN" :
					return HumanNameParser.parse(types, 1, -1);
				case "XCN" :
					return HumanNameParser.parse(types, 1, 9);
				case "XPN" :
					return HumanNameParser.parse(types, 0, 9);
				default :
					break;
			}
		}
		return null;
	}


	static HumanName parse(Type[] types, int offset, int nameTypeLoc) {
		HumanName hn = new HumanName();
		for (int i = 0; i < 6; i++) {
			if (types[i + offset] instanceof Primitive pt) {
				if (ParserUtils.isEmpty(pt)) {
					continue;
				}
				String part = pt.getValue();
				if (StringUtils.isBlank(part)) {
					continue;
				}
				switch (i) {
					case 0 : // Family Name
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
		}
		if (nameTypeLoc >= 0 && nameTypeLoc < types.length
				&& types[nameTypeLoc] instanceof Primitive pt) {
			String nameType = pt.getValue();
			hn.setUse(toNameUse(nameType));
		}
		return null;
	}

	/*
	 */
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
