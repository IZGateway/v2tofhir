package gov.cdc.izgw.v2tofhir.converter;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.function.Consumer;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.Address;
import org.hl7.fhir.r4.model.BaseDateTimeType;
import org.hl7.fhir.r4.model.CodeType;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.DecimalType;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.InstantType;
import org.hl7.fhir.r4.model.IntegerType;
import org.hl7.fhir.r4.model.PositiveIntType;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.TimeType;
import org.hl7.fhir.r4.model.UnsignedIntType;
import org.hl7.fhir.r4.model.UriType;
import ca.uhn.fhir.model.api.TemporalPrecisionEnum;
import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Composite;
import ca.uhn.hl7v2.model.DataTypeException;
import ca.uhn.hl7v2.model.Primitive;
import ca.uhn.hl7v2.model.Type;
import ca.uhn.hl7v2.model.primitive.TSComponentOne;
import gov.cdc.izgw.v2tofhir.converter.datatype.AddressParser;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DatatypeConverter {
	private static final String UNEXPECTED_EXCEPTION_MESSAGE = "Unexpected {}: {}";
	private static final BigDecimal MAX_UNSIGNED_VALUE = new BigDecimal(Integer.MAX_VALUE);
	private static final AddressParser addressParser = new AddressParser();
	
	private DatatypeConverter() {
	}
	
	public static Address toAddress(Type codedElement) {
		return addressParser.convert(codedElement);
	}
	public static CodeableConcept toCodeableConcept(Type codedElement) {
		return toCodeableConcept(codedElement, null);
	}
	public static CodeableConcept toCodeableConcept(Type codedElement, String table) {
		if (codedElement == null) {
			return null;
		}
		CodeableConcept cc = new CodeableConcept();
		Primitive st = null;
		Composite comp = null;
		switch (codedElement.getName()) {
			case "CE", "CF", "CNE", "CWE" :
				comp = (Composite) codedElement;
				for (int i = 0; i <= 3; i += 3) {
					addCoding(cc, getCoding(comp, i, true));
				}
				setValue(cc::setText, comp.getComponents(), 8);
				break;
			case "CX" :
				comp = (Composite) codedElement;
				Identifier ident = extractAsIdentifier(comp, 0, 1, 4, 3, 9, 8);
				if (ident != null && !ident.isEmpty()) {
					addCoding(cc, new Coding(ident.getSystem(),
							ident.getValue(), null));
				}
				break;
			case "CQ" :
				comp = (Composite) codedElement;
				Type[] types = comp.getComponents();
				if (types.length > 1) {
					return toCodeableConcept(types[1]);
				}
				break;
			case "HD" :
				comp = (Composite) codedElement;
				ident = extractAsIdentifier(comp, -1, -1, -1, 1, 0);
				if (ident == null) {
					return null;
				}
				addCoding(cc, new Coding(ident.getSystem(), ident.getValue(), null));
				break;
			case "EI" :
				// EI is a simple code plus an HD
				comp = (Composite) codedElement;
				ident = extractAsIdentifier(comp, 0, -1, -1, 2, 1);
				if (ident == null) {
					return null;
				}
				addCoding(cc, new Coding(ident.getSystem(), ident.getValue(), null));
				break;
			case "ID" :
				st = (Primitive) codedElement;
				addCoding(cc,
						new Coding(
								"http://terminology.hl7.org/CodeSystem/v2-0301",
								st.getValue(), null));
				break;

			case "IS", "ST" :
				st = (Primitive) codedElement;
				addCoding(cc, new Coding(Systems.mapCodeSystem(table), st.getValue(), null));
				break;
			default :
				break;
		}
		if (cc.isEmpty()) {
			return null;
		}
		return cc;
	}

	private static Identifier extractAsIdentifier(Composite comp,
			int idLocation, int checkDigitLoc, int idTypeLoc, int... systemValues) {
		if (comp == null) {
			return null;
		}
		Type[] types = comp.getComponents();
		Identifier id = new Identifier();
		String value = getValueOfIdentifier(idLocation, checkDigitLoc, types);
		id.setValue(value);
		
		for (int v : systemValues) {
			if (types.length > v && !ParserUtils.isEmpty(types[v])) {
				String system = getSystemOfIdentifier(types[v]);
				if (system != null) {
					id.setSystem(Systems.mapIdSystem(system));
					break;
				}
			}
		}
		if (idTypeLoc >= 0 && types.length > idTypeLoc
				&& types[idTypeLoc] instanceof Primitive pt && !ParserUtils.isEmpty(pt)) {
			Coding coding = new Coding(Mapping.IDENTIFIER_TYPE, pt.getValue(),
					null);
			Mapping.setDisplay(coding);
			CodeableConcept cc = new CodeableConcept();
			cc.addCoding(coding);
			id.setType(cc);
		}
		if (id.isEmpty()) {
			return null;
		}
		return id;
	}

	private static String getSystemOfIdentifier(Type type) {
		if (type instanceof Primitive pt) {
			return pt.getValue();
		} else if (type instanceof Composite comp2 && 
				   "HD".equals(comp2.getName()) // NOSONAR Name check is correct here
		) {  
			Identifier id2 = getHDasIdentifier(comp2.getComponents());
			return id2 == null ? null : id2.getSystem();
		}
		return null;
	}

	private static String getValueOfIdentifier(int idLocation, int checkDigitLoc, Type[] types) {
		if (idLocation >= 0 &&
			types.length > idLocation && 
			types[idLocation] instanceof Primitive pt && 
			!ParserUtils.isEmpty(pt)
		) {
			if (checkDigitLoc > 0 && types.length > checkDigitLoc && types[checkDigitLoc] instanceof Primitive pt2
					&& !ParserUtils.isEmpty(pt2)) {
				return pt.getValue() + "-" + pt2.getValue();
			} else {
				return pt.getValue();
			}
		}
		return null;
	}

	public static CodeType toCodeType(Type codedElement) {
		if (codedElement == null) {
			return null;
		}
		if (codedElement instanceof Primitive pt) {
			return new CodeType(pt.getValue()); 
		}
		Coding coding = toCoding(codedElement);
		if (coding != null && !coding.isEmpty()) {
			return new CodeType(coding.getCode());
		}
		return null;
	}

	public static Coding toCoding(Type type) {
		return toCoding(type, null);
	}
	
	public static Coding toCoding(Type type, String table) {
		CodeableConcept cc = toCodeableConcept(type, table);
		if (cc == null || cc.isEmpty()) {
			return null;
		}
		Coding coding = cc.getCodingFirstRep();
		if (coding == null || coding.isEmpty()) {
			return null;
		}
		if (table != null && !coding.hasSystem()) {
			coding.setSystem(Systems.mapCodeSystem(table));
		}
		return coding;
	}
	
	public static Coding toCodingFromMessageCode(Type type) {
		return toCodingFromMSG(type, 0);
	}
	public static Coding toCodingFromTriggerEvent(Type type) {
		return toCodingFromMSG(type, 1);
	}
	public static Coding toCodingFromMessageStructure(Type type) {
		return toCodingFromMSG(type, 2);
	}
	private static Coding toCodingFromMSG(Type type, int field) {
		String table = null;
		switch (field) {
			case 0: table = "0076"; break;
			case 1: table = "0003"; break;
			case 2: table = "0254"; break;
			default: return null;
		}
		if (type instanceof Composite comp) {
			Type[] types = comp.getComponents();
			if (field < types.length) {
				String code = ParserUtils.toString(types[field]);
				if (StringUtils.isNotBlank(code)) {
					return new Coding(Systems.mapCodeSystem(table), code, Mapping.getDisplay(code, "HL7" + table));
				}
			}
		}
		return null;
	}

	public static DateTimeType toDateTimeType(Type type) {
		InstantType instant = toInstantType(type);
		if (instant == null || instant.isEmpty()) {
			return null;
		}
		return new DateTimeType(instant.getValue(), instant.getPrecision());
	}
	
	public static DateType toDateType(Type type) {
		InstantType instant = toInstantType(type);
		if (instant == null || instant.isEmpty()) {
			return null;
		}
		return new DateType(instant.getValue(), instant.getPrecision());
	}
	
	public static DecimalType toDecimalType(Type pt) {
		Quantity qt = toQuantity(pt);
		if (qt == null || qt.isEmpty()) {
			return null;
		}
		return qt.getValueElement();
	}

	public static Identifier toIdentifier(Type t) {
		if (t == null) {
			return null;
		}

		Identifier id = null;
		if (t instanceof Primitive pt) {
			id = new Identifier().setValue(pt.getValue());
		} else if (t instanceof Composite comp) {
			Type[] types = comp.getComponents();
			if (types.length < 1) {
				return null;
			}
			switch (t.getName()) {
				case "EIP" :
					t = types[0];
					// Fall through
				case "CE", "CF", "CNE", "CWE", "EI" :
					Coding coding = toCoding(t);
					if (coding != null) {
						id = new Identifier();
						id.setValue(coding.getCode());
						id.setSystem(coding.getSystem());
					}
					break;
				case "CX" :
					id = extractAsIdentifier(comp, 0, 1, 4, 3, 9, 8);
					break;
				case "CNN" :
					id = extractAsIdentifier(comp, 0, -1, 7, 9, 8);
					break;
				case "XCN" :
					id = extractAsIdentifier(comp, 0, 10, 12, 22, 21, 8);
					break;
				case "XPN":
					break;
				case "XON":
					id = extractAsIdentifier(comp, 0, 9, 3, 6, 8, 6);
					break;
				case "HD" :
					Identifier id2 = getHDasIdentifier(types);
					if (id2 != null) {
						id = new Identifier().setSystem(id2.getValue());
					}
					break;
				default :
					break;
			}
		}
		if (id == null || id.isEmpty()) {
			return null;
		}
		return id;
	}
	
	public static IdType toIdType(Type type) {
		return new IdType(ParserUtils.toString(type));
	}

	private static Identifier getHDasIdentifier(Type[] types) {
		if (types.length == 0) {
			return null;
		}
		Identifier id = new Identifier();
		List<String> values = new ArrayList<>(types.length);
		Arrays.asList(types).forEach(t1 -> values.add(
				t1 instanceof Primitive pt ? pt.getValue() : t1.toString()));
		id.setValue(values.get(0));
		if (values.size() > 2 && StringUtils.isNotEmpty(values.get(1))) {
			switch (StringUtils.upperCase(StringUtils.defaultString(values.get(2)))) {
				case "ISO" :
					id.setValue("urn:oid:" + values.get(1));
					id.setSystem(Systems.IETF);
					break;
				case "GUID", "UUID" :
					id.setValue("urn:uuid:" + values.get(1));
					id.setSystem(Systems.IETF);
					break;
				case "URI", "URL" :
					id.setValue(values.get(1));
					id.setSystem(Systems.IETF);
					break;
				default :
					id.setValue(values.get(1));
					break;
			}
		}
		return id;
	}
	
	/**
	 * Let HAPI V2 do the parsing work for use in TSComponentOne, which is
	 * independent of any HL7 Version.
	 */
	private static class MyTSComponentOne extends TSComponentOne {
		private static final long serialVersionUID = 1L;
		public MyTSComponentOne() {
			super(null);
		}
	}
	/**
	 * Convert a string to an instant.  This method converts a String to an InstantType.  It uses both the
	 * HAPI V2 and the FHIR Parsers to attempt to convert the input.  First it tries the HAPI V2 parser using 
	 * the date without any ISO Punctuation.  If that fails it uses the FHIR Parser.  The two parsers operate
	 * differently and have overlapping coverage on their input string ranges, so this provides the highest
	 * level of compatibility.
	 * 
	 * @param value	The value to convert
	 * @return An InstantType set to the precision of the timestamp. NOTE: This is
	 * a small abuse of InstantType.
	 */
	public static InstantType toInstantType(String value) {
		if (value == null || value.length() == 0) {
			return null;
		}
		value = value.trim();
		String original = value; // Save trimmed string for use with FHIR Parser.
		
		if (value.length() == 0) {
			return null;
		}
		value = removeIsoPunct(value); 
		
		TSComponentOne ts1 = new MyTSComponentOne();
		try {
			ts1.setValue(value);
			Calendar cal = ts1.getValueAsCalendar();
			String valueWithoutZone = StringUtils.substringBefore(value.replace("+", "-"),"+");
			TemporalPrecisionEnum prec = null;
			InstantType t = new InstantType(cal);
			String valueWithoutDecimal = StringUtils.substringBefore(value, ".");
			int len = valueWithoutDecimal.length();
			if (len < 5) {
				prec = TemporalPrecisionEnum.YEAR;
			} else if (len < 7) {
				prec = TemporalPrecisionEnum.MONTH;
			} else if (len < 9) {
				prec = TemporalPrecisionEnum.DAY;
			} else if (len < 13) {
				prec = TemporalPrecisionEnum.MINUTE;
			} else if (len < 15) {
				prec = TemporalPrecisionEnum.SECOND;
			}
			if (valueWithoutDecimal.length() < valueWithoutZone.length()) {
				prec = TemporalPrecisionEnum.MILLI;
			}
			t.setPrecision(prec);
			return t;
		} catch (Exception e) {
			try {
				// We failed to convert, try as FHIR
				BaseDateTimeType fhirType = new DateTimeType();
				fhirType.setValueAsString(original);
				Date date = fhirType.getValue();
				TemporalPrecisionEnum prec = fhirType.getPrecision();
				InstantType instant = new InstantType();
				TimeZone tz = fhirType.getTimeZone();
				instant.setValue(date);
				instant.setPrecision(prec);
				instant.setTimeZone(tz);
				return instant;
			} catch (Exception ex) {
				debugException("Unexpected FHIR {} parsing {} as InstantType: {}",
						e.getClass().getSimpleName(), original, ex.getMessage(), ex);
			}
			debugException("Unexpected   V2 {} parsing {} as InstantType: {}",
					e.getClass().getSimpleName(), original, e.getMessage());
			return null;
		}
	}
	private static String removeIsoPunct(String value) {
		value = value.toUpperCase();
		String left = value.substring(0, Math.min(11, value.length()));
		String right = value.length() == left.length() ? "" : value.substring(left.length());
		left = left.replace("-", "").replace("T", ""); // Remove - and T from date part
		right = right.replace("-", "+"); // Change any - to +, and remove :
		String tz = StringUtils.substringAfter(right, "+");  // Get TZ length after +
		right = StringUtils.substringBefore(right, "+");
		if (tz.length() != 0) {
			tz = value.substring(value.length() - tz.length() - 1);	// adjust TZ
		} else if (right.endsWith("Z")) {	// Check for ZULU time
			right = right.substring(0, right.length() - 1);
			tz = "Z";
		}
		right = right.replace(":", "");
		value = left + right + tz;
		return value;
	}
	public static InstantType oldToInstantType(String value) {
		if (StringUtils.isBlank(value)) {
			return null;
		}
		value = ParserUtils.cleanupIsoDateTime(value, true);
		if (StringUtils.isBlank(value)) {
			return null;
		}
		try {
			return new InstantType(value);
		} catch (Exception ex) {
			log.trace(UNEXPECTED_EXCEPTION_MESSAGE, ex.getClass().getSimpleName(), ex);
			// Fall through and try types with lower resolution and upconvert
		}
		try {
			DateTimeType dtt = new DateTimeType(value);
			InstantType it = new InstantType(dtt.getValue());
			it.setPrecision(dtt.getPrecision());
			return it;
		} catch (Exception ex) {
			log.trace(UNEXPECTED_EXCEPTION_MESSAGE, ex.getClass().getSimpleName(), ex);
			// Fall through and try one more type
		}
		try {
			DateType dt = new DateType(value);
			InstantType it = new InstantType(dt.getValue());
			it.setPrecision(dt.getPrecision());
			return it;
		} catch (Exception ex) {
			// Now we bail.			
			log.debug(UNEXPECTED_EXCEPTION_MESSAGE, ex.getClass().getSimpleName(), ex);
			return null;
		}
	}
	
	public static InstantType toInstantType(Type type) {
		// This will convert the first primitive component of anything to an instant.
		if (type instanceof TSComponentOne ts1) {
			try {
				Calendar cal = ts1.getValueAsCalendar();
				return new InstantType(cal.getTime());
			} catch (DataTypeException e) {
				warnException("Unexpected {} parsing {} as InstantType: {}",
						e.getClass().getSimpleName(), type, e.getMessage(), e);
			}
		} 
		return toInstantType(ParserUtils.toString(type));
	}

	public static IntegerType toIntegerType(Type pt) {
		DecimalType dt = toDecimalType(pt);
		if (dt == null || dt.isEmpty()) {
			return null;
		}
		BigDecimal decimal = dt.getValue();
		BigInteger bigInt = decimal.toBigInteger();
		try {
			int value = bigInt.intValueExact();
			return new IntegerType(value);
		} catch (ArithmeticException ex) {
			warnException("Integer overflow value in field {}", pt.toString(), ex);
			return null;
		}
	}

	public static PositiveIntType toPositiveIntType(Type pt) {
		IntegerType dt = toIntegerType(pt);
		if (dt == null || dt.isEmpty()) {
			return null;
		}
		if (dt.getValue() < 0) {
			warn("Illegal negative value in field {}", pt.toString());
			return null;
		}
		return new PositiveIntType(dt.getValue());
	}

	public static Quantity toQuantity(Type type) {
		Quantity qt = null;
		if (type instanceof Primitive pt) {
			qt = getQuantity(pt);
		}
		if (type instanceof Composite comp) {
			Type[] types = comp.getComponents();
			if ("CQ".equals(type.getName()) // NOSONAR name check is OK here
					&& types.length > 0) { // NOSONAR name compare is correct
				qt = getQuantity((Primitive) types[0]);
				if (types.length > 1) {
					if (qt == null) {
						qt = new Quantity();
					}
					setUnits(qt, types[1]);
				}
			}
		}

		if (qt == null || qt.isEmpty()) {
			return null;
		}
		return qt;
	}

	public static Quantity toQuantityLengthOfStay(Type pt) {
		Quantity qt = toQuantity(pt);
		if (qt == null || qt.isEmpty()) {
			return null;
		}
		qt.setCode("d");
		qt.setUnit("days");
		qt.setSystem(Units.UCUM);
		return qt;
	}

	public static StringType toStringType(Type type) {
		if (type == null) {
			return null;
		}
		if (type instanceof Primitive pt) {
			return new StringType(pt.getValue());
		}
		String s = null;
		switch (type.getName()) {
			case "CE", "CF", "CNE", "CWE" :
				CodeableConcept cc = toCodeableConcept(type);
				s = toString(cc);
				break;
			case "ERL":
				// TODO: Consider converting this type to a FHIRPath expression  
				try {
					s = type.encode();
				} catch (HL7Exception e) {
					// ignore this error.
				}
				break;
			case "CQ" :
				Quantity qt = toQuantity(type);
				s = toString(qt);
				break;
			default :
				break;
		}
		if (s == null) {
			return null;
		}
		return new StringType(s);
	}
	
	public static TimeType toTimeType(String value) {
		if (StringUtils.isBlank(value)) {
			return null;
		}
		value = ParserUtils.cleanupIsoDateTime(value, false);
		if (StringUtils.isEmpty(value)) {
			return null;
		}
		try {
			TimeType tt = new TimeType();
			tt.setValue(value);
			return tt;
		} catch (Exception ex) {
			log.debug(UNEXPECTED_EXCEPTION_MESSAGE, ex.getClass().getSimpleName(), ex);
			return null;
		}
	}
	
	public static TimeType toTimeType(Type type) {
		// This will convert the first primitive component of anything to a time. 
		return toTimeType(ParserUtils.toString(type));
	}

	public static UnsignedIntType toUnsignedIntType(Type pt) {
		DecimalType dt = toDecimalType(pt);
		if (dt == null || dt.isEmpty()) {
			return null;
		}
		if (dt.getValue().compareTo(BigDecimal.ZERO) < 0) {
			warn("Illegal negative value in field {}", pt.toString(), pt);
			return null;
		}
		if (dt.getValue().compareTo(MAX_UNSIGNED_VALUE) > 0) {
			warn("Unsigned Integer overflow value in field {}",
					pt.toString(), pt);
			return null;
		}
		return new UnsignedIntType(dt.getValueAsInteger());
	}

	public static UriType toUriType(Type type) {
		if (type == null) {
			return null;
		}
		if (type instanceof Primitive pt) {
			return new UriType(pt.getValue());
		}
		Type[] types = ((Composite) type).getComponents();
		if (types.length == 0) {
			return null;
		}
		if ("HD".equals(type.getName())) { // NOSONAR Name
															// comparison is
															// correct
			Identifier id = toIdentifier(type);
			if (id != null && !id.isEmpty()) {
				return new UriType(id.getValue());
			}
		}
		return null;
	}

	private static void addCoding(CodeableConcept cc, Coding coding) {
		if (coding == null || coding.isEmpty()) {
			return;
		}
		cc.addCoding(coding);
	}
	private static int compareUnitsBySystem(Coding c1, Coding c2) {
		if (Units.UCUM.equals(c1.getSystem())) {
			return -1;
		} else if (Units.UCUM.equals(c2.getSystem())) {
			return 1;
		}
		return StringUtils.compare(c1.getSystem(), c2.getSystem());
	}

	private static Coding getCoding(Composite composite, int index,
			boolean hasDisplay) {
		Type[] types = composite.getComponents();
		int versionIndex = 6;
		int codeSystemOID = 13;
		if ("EI".equals(composite.getName())) { // NOSONAR Use of string comparison is correct
			versionIndex = 99;
			codeSystemOID = 2;
			hasDisplay = false;
		} else if (index == 3) {
			versionIndex = 7;
			codeSystemOID = 16;
		} else if (index == 9) {
			versionIndex = 12;
			codeSystemOID = 20;
		}
		try {
			Coding coding = new Coding();
			if (index >= types.length) {
				return null;
			}
			setValue(coding::setCode, types, index++);
			if (hasDisplay) {
				setValue(coding::setDisplay, types, index++);
			}
			setValue(s -> coding.setSystem(Systems.mapCodeSystem(s)), types, index++);
			setValue(coding::setVersion, types, versionIndex);
			setValue(s -> coding.setSystem(Systems.mapCodeSystem(s)), types, codeSystemOID);
			if (!coding.hasDisplay() || coding.getDisplay().equals(coding.getCode())) {
				// See if we can do better for display names
				Mapping.setDisplay(coding);
			}
			return coding;
		} catch (Exception e) {
			warnException("Unexpected {} converting {}[{}] to Coding: {}",
					e.getClass().getName(), composite.toString(), index,
					e.getMessage(), e);
			return null;
		}
	}

	private static Quantity getQuantity(Primitive pt) {
		Quantity qt = new Quantity();
		String value = null;
		if (StringUtils.isBlank(pt.getValue())) {
			return null;
		}
		value = pt.getValue();
		String[] valueParts = value.split("\\s+");
		try {
			qt.setValueElement(new DecimalType(valueParts[0]));
		} catch (NumberFormatException ex) {
			return null;
		}
		if (valueParts.length > 1) {
			Coding coding = Units.toUcum(valueParts[1]);
			if (coding != null) {
				qt.setCode(coding.getCode());
				qt.setUnit(coding.getDisplay());
				qt.setSystem(coding.getSystem());
			}
		}
		if (qt.isEmpty()) {
			return null;
		}
		return qt;
	}

	private static void setUnits(Quantity qt, Type unit) {
		CodeableConcept cc = toCodeableConcept(unit);
		if (cc != null && cc.hasCoding()) {
			List<Coding> codingList = cc.getCoding();
			Collections.sort(codingList,
					DatatypeConverter::compareUnitsBySystem);
			Coding coding = codingList.get(0);
			qt.setCode(coding.getCode());
			qt.setSystem(Units.UCUM);
			qt.setUnit(coding.getDisplay());
		}
	}

	private static void setValue(Consumer<String> consumer, Type[] types,
			int i) {
		if (i < types.length && types[i] instanceof Primitive st) {
			consumer.accept(st.getValue());
		}
	}

	private static String toString(CodeableConcept cc) {
		if (cc == null) {
			return null;
		}
		if (cc.hasText()) {
			return cc.getText();
		}
		if (cc.hasCoding()) {
			Coding coding = cc.getCodingFirstRep();
			if (coding.hasDisplay()) {
				return coding.getDisplay();
			}
			if (coding.hasCode()) {
				return coding.getCode();
			}
		}
		return null;
	}

	private static String toString(Quantity qt) {
		if (qt == null) {
			return null;
		}
		StringBuilder b = new StringBuilder();
		if (qt.hasValue()) {
			b.append(qt.getValue().toString());
		}
		if (qt.hasUnit()) {
			b.append(' ');
			b.append(qt.getUnit());
		}
		if (StringUtils.isBlank(b)) {
			return null;
		}
		return b.toString();
	}
	
	private static void warn(String msg, Object ...args) {
		log.warn(msg, args);
	}
	private static void warnException(String msg, Object ...args) {
		log.error(msg, args);
	}
	private static void debugException(String msg, Object ...args) {
		log.debug(msg, args);
	}
}
