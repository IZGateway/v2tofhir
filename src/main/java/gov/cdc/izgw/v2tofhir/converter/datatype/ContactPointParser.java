package gov.cdc.izgw.v2tofhir.converter.datatype;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.validator.routines.EmailValidator;
import org.hl7.fhir.r4.model.ContactPoint;
import org.hl7.fhir.r4.model.ContactPoint.ContactPointSystem;
import org.hl7.fhir.r4.model.ContactPoint.ContactPointUse;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.IntegerType;
import org.hl7.fhir.r4.model.Period;
import org.hl7.fhir.r4.model.StringType;

import ca.uhn.hl7v2.model.Composite;
import ca.uhn.hl7v2.model.Primitive;
import ca.uhn.hl7v2.model.Type;
import gov.cdc.izgw.v2tofhir.converter.DatatypeConverter;
import gov.cdc.izgw.v2tofhir.converter.ParserUtils;
import lombok.extern.slf4j.Slf4j;

@Slf4j
/**
 * Parser that supports parsing a String, or HL7 Version into a FHIR ContactPoint.
 * 
 * @author Audacious Inquiry
 */
public class ContactPointParser implements DatatypeParser<ContactPoint> {
	static {
		log.debug("{} loaded", ContactPointParser.class.getName());
	}

	static final String AREA_CODE = "\\(\\s*\\d{3}\s*\\)";
	static final String COUNTRY_CODE = "\\+\\s*\\d{1,3}";
	static final String PHONE_CHAR = "\\-+0123456789(). []{},#";
	static final String PHONE_NUMBER = "(-|\\.|\\d+)+";
	// [NN] [(999)]999-9999[X99999][B99999][C any text]
	static final Pattern TN_PATTERN = Pattern.compile(
		"((\\d{2,3})?(\\(\\d{3}\\))?\\d{3}-?\\d{4}(X\\d{1,5})?(B\\d1,5)?)(C.*)?$"
	);
	static final String CONTACTPOINT_COMMENT = "http://hl7.org/fhir/StructureDefinition/contactpoint-comment";
	
	private static final EmailValidator EMAIL_VALIDATOR = EmailValidator.getInstance();
	static StringBuilder appendIfNotBlank(StringBuilder b, String prefix, String string, String suffix) {
		if (StringUtils.isBlank(string)) {
			return b;
		}
		return b.append(StringUtils.defaultString(prefix))
				.append(StringUtils.defaultString(string))
				.append(StringUtils.defaultString(suffix));
	}
	
	/**
	 * Convert an HL7 V2 XTN into a list of contacts.
	 * @param xtn	The HL7 V2 xtn (or similarly shaped composite).
	 * @return	A list of ContactPoint objects from the XTN.
	 */
	public List<ContactPoint> convert(Composite xtn) {
		Type[] types = xtn.getComponents();
		List<ContactPoint> cps = new ArrayList<>();
		if (types.length > 0) {
			cps.add(convert(types[0]));	// Convert XTN-1 to a phone number.
		}
		cps.add(fromEmail(ParserUtils.toString(types, 3)));
		String value = fromXTNparts(types);
		if (StringUtils.isNotBlank(value)) {
			cps.add(new ContactPoint().setValue(value).setSystem(ContactPointSystem.PHONE));
		}
		String unformatted = ParserUtils.toString(types, 11);
		if (StringUtils.isNotBlank(unformatted)) {
			cps.add(new ContactPoint().setValue(unformatted).setSystem(ContactPointSystem.PHONE));
		}
		String comment = ParserUtils.toString(types, 8);
		String use = StringUtils.defaultIfBlank(ParserUtils.toString(types, 1), "");
		String type = StringUtils.defaultIfBlank(ParserUtils.toString(types, 2), "");
		DateTimeType start = types.length > 12 ? DatatypeConverter.toDateTimeType(types[12]) : null;
		DateTimeType end = types.length > 13 ? DatatypeConverter.toDateTimeType(types[13]) : null;
		IntegerType order = types.length > 17 ? DatatypeConverter.toIntegerType(types[17]) : null;
		
		Period period = null;
		if (start != null || end != null) {
			period = new Period().setStartElement(start).setEndElement(end);
		}
		
		cleanupContactPoints(cps, comment, use, type, order, period);
		return cps;
	}

	@Override
	public Type convert(ContactPoint type) {
		// TODO Auto-generated method stub
		return null;
	}
	@Override
	public ContactPoint convert(Type type) {
		
		if ((type = DatatypeConverter.adjustIfVaries(type)) == null) {
			return null;
		}
		
		if (type instanceof Primitive) {
			return fromString(ParserUtils.toString(type)); 
		}
		
		if ("XTN".equals(type.getName())) {
			List<ContactPoint> cps = convert((Composite)type);
			return cps.isEmpty() ? null : cps.get(0);
		}
		
		return null;
	}
	
	@Override
	public ContactPoint fromString(String value) {
		ContactPoint cp = fromPhone(value);
		if (cp != null && !cp.isEmpty()) {
			return cp;
		}
		cp = fromEmail(value);
		if (cp != null && !cp.isEmpty()) {
			return cp;
		}
		cp = fromUrl(value);
		if (cp != null && !cp.isEmpty()) {
			return cp;
		}
		return null;
	}
	
	/**
	 * Create a ContactPoint from a string representing an e-mail address.
	 * 
	 * This method recognizes mailto: urls, and RFC-2822 email addresses
	 * @see #fromEmail(String)
	 * 
	 * @param value	The email address.
	 * @return	A ContactPoint object representing this e-mail address. 
	 */
	public ContactPoint fromEmail(String value) {
		if (StringUtils.isBlank(value)) {
			return null;
		}
		value = StringUtils.strip(value).split("[;,]")[0];
		String name = null;
		String email = null;
		if (value.contains("<")) {
			name = StringUtils.substringBefore(value, "<");
			email = StringUtils.substringBetween(value, "<", ">");
		} else {
			email = value; 
		}
		if (StringUtils.startsWith(email, "mailto:")) {
			email = email.substring(7);
		}
		/*
		 * Simplfied RFC-2822 grammar
		 * mailbox         = name-addr / addr-spec
		 * name-addr       = [display-name] angle-addr
		 * angle-addr      = [CFWS] "<" addr-spec ">" [CFWS]
		 * display-name	   = word | quoted-string
		 * addr-spec       = local-part "@" domain
		 * local-part      = dot-atom / quoted-string / obs-local-part
		 * domain          = dot-atom / domain-literal / obs-domain
		 * domain-literal  = [CFWS] "[" *([FWS] dcontent) [FWS] "]" [CFWS]
		 * dcontent        = dtext / quoted-pair
		 * dtext           = NO-WS-CTL /     ; Non white space controls
	     *                   %d33-90 /       ; The rest of the US-ASCII
	     *                   %d94-126        ;  characters not including "[", "]", or "\"
		 */
		if (EMAIL_VALIDATOR.isValid(email)) {
			ContactPoint cp = new ContactPoint();
			cp.setSystem(ContactPointSystem.EMAIL);
			cp.setValue(email);
			if (StringUtils.isNotBlank(name)) {
				cp.addExtension()
					.setUrl(CONTACTPOINT_COMMENT)
					.setValue(new StringType(name));
			}
			return cp;
		}
		return null;
	}
	
	/**
	 * Create a list of ContactPoints 
	 * @param values	A string providing the list of email addresses, separated by semi-colons, commas, or newlines.
	 * @return	The list of contact points.
	 */
	public List<ContactPoint> fromEmails(String values) {
		if (StringUtils.isBlank(values)) {
			return Collections.emptyList();
		}
		List<ContactPoint> cps = new ArrayList<>();
		for (String value: StringUtils.strip(values).split("[;,\n\r]+")) {
			ContactPoint cp = fromEmail(value);
			if (cp != null && !cp.isEmpty()) {
				cps.add(cp);
			}
		}
		return cps;
	}

	/**
	 * Create a ContactPoint from a string representing a phone number.
	 * 
	 * This method recognizes tel: urls and other strings containing phone dialing
	 * characters and punctuation.
	 * 
	 * @param value	The phone number.
	 * @return	A ContactPoint object representing the phone number. 
	 */
	public ContactPoint fromPhone(String value) {
		if (StringUtils.isBlank(value)) {
			return null;
		}
		ContactPoint cp = null;
		value = StringUtils.strip(value);
		if (value.startsWith("tel:")) {
			return new ContactPoint().setValue(value.substring(4)).setSystem(ContactPointSystem.PHONE);
		}
		if (value.startsWith("fax:")) {
			return new ContactPoint().setValue(value.substring(4)).setSystem(ContactPointSystem.FAX);
		}
		if (value.startsWith("sms:")) {
			return new ContactPoint().setValue(value.substring(4)).setSystem(ContactPointSystem.SMS);
		}
		
		Matcher m = TN_PATTERN.matcher(value);
		if (m.matches()) {
			cp = new ContactPoint();
			// Could be fax or pager or SMS or other but we won't know without context.
			cp.setSystem(ContactPointSystem.PHONE);
			cp.setValue(m.group(1));
			String comment = StringUtils.substringAfter(value, "C");
			if (StringUtils.isNotBlank(comment)) {
				cp.addExtension()
					.setUrl(CONTACTPOINT_COMMENT)
					.setValue(new StringType(comment));
			}
			return cp;
		}
		if (StringUtils.containsOnly(value, PHONE_CHAR) ||
			value.matches(COUNTRY_CODE) || value.matches(AREA_CODE) || value.matches(PHONE_NUMBER)
		) {
			cp = new ContactPoint();
			cp.setSystem(ContactPointSystem.PHONE);
			cp.setValue(value.replace(" ", ""));
			return cp;
		}
		return null;
	}
	
	/**
	 * Create a ContactPoint from a URL
	 * @param url
	 * @return A ContactPoint object representing the URL.
	 */
	public ContactPoint fromUrl(String url) {
		url = StringUtils.strip(url);
		if (StringUtils.isEmpty(url)) {
			return null;
		}
		String scheme = StringUtils.substringBefore(url, ":");
		ContactPointSystem system = ContactPointSystem.URL;
		if (StringUtils.isNotEmpty(scheme)) {
			if ("mailto".equalsIgnoreCase(scheme))
				system = ContactPointSystem.EMAIL;
			else if ("tel".equalsIgnoreCase(scheme))
				system = ContactPointSystem.PHONE;
			else if ("fax".equalsIgnoreCase(scheme))
				system = ContactPointSystem.FAX;
			else if ("sms".equalsIgnoreCase(scheme))
				system = ContactPointSystem.SMS;
			return new ContactPoint()
					.setValue(url)
					.setSystem(system);
		}
		if (url.matches("^([\\-a-zA-Z0-9]+|[0-9]{1,3})([.\\-a-zA-Z0-9]+|[0-9]{1-3})+(/.*)?$")) {
			if (url.startsWith("www") || url.contains("/")) {
				url = "http://" + url;
				system = ContactPointSystem.URL;
			} else {
				system = ContactPointSystem.OTHER;
			}
			return new ContactPoint()
					.setValue(url)
					.setSystem(system);
		}
		return null;
	}
	
	@Override
	public Class<? extends ContactPoint> type() {
		return ContactPoint.class;
	}
	
	private void cleanupContactPoints(List<ContactPoint> cps, String comment, String use, String type,
			IntegerType order, Period period) {
		// Cleanup list after parsing.
		Iterator<ContactPoint> it = cps.iterator();
		while (it.hasNext()) {
			ContactPoint cp = it.next();
			if (cp == null || cp.isEmpty() || StringUtils.isBlank(cp.getValue())) {
				it.remove();
				continue;
			}
			if (StringUtils.isNotBlank(comment)) {
				cp.addExtension()
					.setUrl(CONTACTPOINT_COMMENT)
					.setValue(new StringType(comment));
			}
			mapUseCode(use, cp);
			mapTypeCode(type, cp);
			if (period != null && !period.isEmpty()) {
				cp.setPeriod(period);
			}
			if (order != null && !order.isEmpty()) {
				cp.setRank(order.getValue());
			}
		}
	}
	
	/**
	 * Create a phone number as a String from the components of XTN datatype.
	 * @param types	The components of the XTN datatype
	 * @return	The phone number as a string.
	 */
	public static String fromXTNparts(Type[] types) {
		String country = ParserUtils.toString(types, 4);
		String area = ParserUtils.toString(types, 5);
		String local = ParserUtils.toString(types, 6);
		String extension  = ParserUtils.toString(types, 7);
		String extPrefix = ParserUtils.toString(types, 9);
		
		if (!StringUtils.isAllBlank(country, area, local, extension)) {
			StringBuilder b = new StringBuilder();
			appendIfNotBlank(b, "+", country, " ");
			appendIfNotBlank(b, "(", area, ") ");
			appendIfNotBlank(b, null, local, null);
			appendIfNotBlank(b, StringUtils.defaultIfBlank(extPrefix, "#"), extension, null);
			return b.toString();
		}
		return null;
	}
	
	private void mapTypeCode(String type, ContactPoint cp) {
		switch (type) {
		case "BP": cp.setSystem(ContactPointSystem.PAGER); break;
		case "CP": 
			cp.setSystem(ContactPointSystem.PHONE);
			cp.setUse(ContactPointUse.MOBILE);
			break;
		case "FX": cp.setSystem(ContactPointSystem.FAX); break;
		case "Internet": cp.setSystem(ContactPointSystem.URL); break;
		case "MD": cp.setSystem(ContactPointSystem.OTHER); break;
		case "PH", "SAT": 
			cp.setSystem(ContactPointSystem.PHONE); break;
		case "TDD", "TTY": cp.setSystem(ContactPointSystem.OTHER); break;
		case "X.400": cp.setSystem(ContactPointSystem.EMAIL); break;
		default: break;
		}
	}

	private void mapUseCode(String use, ContactPoint cp) {
		switch (use) {
		case "PRS": // Personal Number
			cp.setUse(ContactPointUse.MOBILE); break;
		case "ORN",	// Other Residence Number
			 "PRN", // Primary Residence Number
			 "VHN": // Vacation Home Number
			cp.setUse(ContactPointUse.HOME); break;
		case "WPN": // Work Number
			cp.setUse(ContactPointUse.WORK); break;
		case "NET", // Network (email) Address
			 "ASN",	// Answering Service Number
			 "BPN",	// Beeper Number
			 "EMR": // Emergency Number
		default: break;
		}
	}
}
