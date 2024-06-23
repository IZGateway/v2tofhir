package gov.cdc.izgw.v2tofhir.converter;
import java.io.IOException;
import java.io.StringReader;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Composite;
import ca.uhn.hl7v2.model.Primitive;
import ca.uhn.hl7v2.model.Type;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ParserUtils {
	private ParserUtils() {}
	public static String[] removeArrayElement(String[] lineParts,
			int position) {
		String[] newArray = new String[lineParts.length - 1];
		if (position == 1) {
			System.arraycopy(lineParts, 0, newArray, 1, lineParts.length - 1);
		} else {
			System.arraycopy(lineParts, 0, newArray, 0, position - 1);
			if (lineParts.length > position) {
				System.arraycopy(lineParts, position, newArray, position - 1,
						lineParts.length - position);
			}
		}
		return newArray;
	}
	public static Pattern toPattern(String... values) {
		StringBuilder b = new StringBuilder();
		for (String value : values) {
			b.append("^").append(value).append("$|");
		}
		b.setLength(b.length() - 1);
		return Pattern.compile(b.toString(), Pattern.CASE_INSENSITIVE);
	}
	public static String toString(Type type) {
		if (type == null) {
			return null;
		}
		if (type instanceof Composite comp) {
			Type[] types = comp.getComponents();
			return types.length > 0 ? toString(types[0]) : null;
		} else if (type instanceof Primitive pt) {
			return pt.getValue();
		}
		return null;
	}
	public static boolean isEmpty(Type type) {
		try {
			return type.isEmpty();
		} catch (HL7Exception e) {
			log.warn("Unexpected HL7Exception: {}", e.getMessage());
			return true;
		}
	}
	
	public static String cleanupIsoDateTime(String value, boolean hasDate) {
		if (StringUtils.substringBefore(value, ".").length() < (hasDate ? 4 : 2)) {
			return null;
		}
		if (!hasLegalIso8601Chars(value, hasDate)) {
			return null;
		}
		StringReader r = new StringReader(value.trim());
		StringBuilder b = new StringBuilder();
		int[] data = { 4, '-', 2, '-', 2, 'T', 2, ':', 2, ':', 2, '.', 6, '+', 2, ':', 2 };
		int c = 0;
		int state = hasDate ? 0 : 6;
		try {
			while (state < data.length && (c = r.read()) >= 0) {
				if (Character.isWhitespace(c)) {
					continue;
				}
				if (data[state] < '@') {  
					state = getIsoDigits(b, data, (char)c, state);
				} else {
					state = getIsoPunct(b, data, (char)c, state);
				}
			}
			if (c < 0 || r.read() < 0) {
				return b.toString();
			}
		} catch (IOException e) {
			// Not going to happen on StringReader.
		}
		// We finished reading, but there was more data.
		return null;
	}
	private static boolean hasLegalIso8601Chars(String value, boolean hasDate) {
		// Check for legal ISO characters
		String legalChars = hasDate ? "0123456789T:-+.Z" : "0123456789:-+.Z";
		if (!StringUtils.containsOnly(value, legalChars)) {
			return false;
		}
		// Check for legal number of ISO punctuation characters
		if (StringUtils.countMatches(value, 'T') > 1 || 
			StringUtils.countMatches(value, '.') > 1 ||
			StringUtils.countMatches(value, '+') > 1 ||
			StringUtils.countMatches(value, '-') > 3 ||
			StringUtils.countMatches(value, ':') > 3) {
			return false;
		}
		return true;
	}

	private static int getIsoDigits(StringBuilder b, int[] data, char c, int state) {
		// We are Looking for digits
		if (Character.isDigit(c)) {
			b.append(c);
			if (--data[state] == 0) {
				state++;
			}
		} else if (state + 1 == data.length) {
			// We've finished this, but we have an unexpected character
			b.append(c);
			state++;
		} else if (data[state + 1] == c || (data[state + 1] == '+' && c == '-')) {
			// We got the separator early, append it and advance two states
			b.append(c);
			state += 2;
		}
		return state;
	}
	
	private static int getIsoPunct(StringBuilder b, int[] data, char c, int state) {
		if (c == data[state] || 
			(data[state] == '+' && "-Z".indexOf(c) >= 0 )
		) {
			// Waiting on punctuation and we got it
			b.append(c);
			if (c == 'Z') {
				state = data.length; // We're done.
			}
			state++;
		} else if (c == '.') {
			// Decimal can show up early
			b.append(c);
			state = 12;
		} else if (Character.isDigit(c)) {
			// We got a digit while waiting on punctuation
			++state;
			b.append(c);
			--data[state];
		}
		return state;
	}
	public static String unescapeV2Chars(String value) {
		value = value.replace("\\F\\", "|");
		value = value.replace("\\S\\", "^");
		value = value.replace("\\R\\", "~");
		value = value.replace("\\T\\", "&");
		value = value.replace("\\E\\", "\\");
		return value;
	}
	public static String escapeV2Chars(String value) {
		value = value.replace("\\", "\\E\\");
		value = value.replace("|", "\\F\\");
		value = value.replace("^", "\\S\\");
		value = value.replace("~", "\\R\\");
		value = value.replace("&", "\\T\\");
		return value;
	}

}
