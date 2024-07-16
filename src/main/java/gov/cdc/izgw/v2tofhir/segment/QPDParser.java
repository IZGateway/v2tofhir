package gov.cdc.izgw.v2tofhir.segment;

import java.lang.reflect.InvocationTargetException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Address;
import org.hl7.fhir.r4.model.BaseDateTimeType;
import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.CodeType;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.ContactPoint;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.InstantType;
import org.hl7.fhir.r4.model.MessageHeader;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.PositiveIntType;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Bundle.BundleEntryRequestComponent;
import org.hl7.fhir.r4.model.Bundle.BundleEntryResponseComponent;
import org.hl7.fhir.r4.model.Bundle.HTTPVerb;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.GenericComposite;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.model.Segment;
import ca.uhn.hl7v2.model.Type;
import ca.uhn.hl7v2.model.Varies;
import gov.cdc.izgw.v2tofhir.annotation.ComesFrom;
import gov.cdc.izgw.v2tofhir.annotation.Produces;
import gov.cdc.izgw.v2tofhir.converter.DatatypeConverter;
import gov.cdc.izgw.v2tofhir.converter.MessageParser;
import gov.cdc.izgw.v2tofhir.utils.ParserUtils;
import lombok.extern.slf4j.Slf4j;

/**
 * The QPD Parser generates a Parameters resource documenting the QPD parameters found in QPD-3 to QPD-*
 * 
 * For queries (message type of QBP):
 * 
 * The Bundle.entry containing this Parameters resource in Bundle.entry.resource will contain a 
 * Bundle.entry.request element with the method set to GET and the url set to the FHIR _search URL which would perform the query.
 * 
 * For responses to queries (message type of RSP):
 * 
 * The Bundle.entry containing this Parameters resource in Bundle.entry.resource will contain a 
 * Bundle.entry.response element with the location set to the FHIR _search URL which would perform the query.
 *
 * @author Audacious Inquiry
 *
 */
@Produces(segment="QPD", resource=Parameters.class)
@ComesFrom(path="Parameters.meta.tag", source="QPD-1", comment="Query Name")
@ComesFrom(path="Parameters.meta.tag[1].code", source="QPD-2", comment="Query Tag")
@ComesFrom(path="Parameters.meta.tag[1].system", fixed="QueryTag")
@ComesFrom(path="Parameters.parameter.name", source= { "QPD-3", "QPD-*" }, comment="Varies by query")
@ComesFrom(path="Parameters.parameter.value", source= { "QPD-3", "QPD-*" }, comment="Varies by query")
@Slf4j
public class QPDParser extends AbstractSegmentParser {
	private Parameters params;
	private MessageHeader mh;
	private static List<FieldHandler> fieldHandlers = new ArrayList<>();
	static {
		log.debug("{} loaded", QPDParser.class.getName());
	}
	/**
	 * Construct a new QPD Parser for the given messageParser.
	 * 
	 * @param messageParser The messageParser using this QPDParser
	 */
	public QPDParser(MessageParser messageParser) {
		super(messageParser, "QPD");
		if (fieldHandlers.isEmpty()) {
			initFieldHandlers(this, fieldHandlers);
		}
	}
	
	@Override
	public List<FieldHandler> getFieldHandlers() {
		return fieldHandlers;
	}
	
	private static final String[][] v2QueryNames = {
		{ 	"Z34", "Request Immunization History", "CDCPHINVS", "Immunization",
			// QPD Parameter Name, V2 Type to convert from, FHIR Query Parameter, FHIR Type to convert parmater to
			"PatientList", "CX", "patient.identifier", Identifier.class.getSimpleName(),
			"PatientName", "XPN", "patient.name", HumanName.class.getSimpleName(), 
			"PatientMotherMaidenName", "XPN", "patient.mothersMaidenName", StringType.class.getSimpleName(), 
			"PatientDateOfBirth", "TS", "patient.birthdate", DateType.class.getSimpleName(),
			"PatientSex", "IS", "patient.gender", CodeType.class.getSimpleName(), 
			"PatientAddress", "XAD", "patient.address", Address.class.getSimpleName(), 
			"PatientHomePhone", "XTN", "patient.phone", ContactPoint.class.getSimpleName(),
			"PatientMultipleBirthIndicator", "ID", null, BooleanType.class.getSimpleName(),
			"PatientBirthOrder", "NM", null, PositiveIntType.class.getSimpleName(),
			"ClientLastUpdateDate", "TS", null, InstantType.class.getSimpleName(),
			"ClientLastUpdateFacility", "HD", null, Identifier.class.getSimpleName()
		},
		{	"Z44", "Request Evaluated History and Forecast", "CDCPHINVS", "ImmunizationRecommendation",
			"PatientList", "CX", "patient.identifier", Identifier.class.getSimpleName(),
			"PatientName", "XPN", "patient.name", HumanName.class.getSimpleName(), 
			"PatientMotherMaidenName", "XPN", "patient.mothersMaidenName", StringType.class.getSimpleName(), 
			"PatientDateOfBirth", "TS", "patient.birthdate", DateType.class.getSimpleName(),
			"PatientSex", "IS", "patient.gender", CodeType.class.getSimpleName(), 
			"PatientAddress", "XAD", "patient.address", Address.class.getSimpleName(), 
			"PatientHomePhone", "XTN", "patient.phone", ContactPoint.class.getSimpleName(),
			"PatientMultipleBirthIndicator", "ID", null, BooleanType.class.getSimpleName(),
			"PatientBirthOrder", "NM", null, PositiveIntType.class.getSimpleName(),
			"ClientLastUpdateDate", "TS", null, InstantType.class.getSimpleName(),
			"ClientLastUpdateFacility", "HD", null, Identifier.class.getSimpleName()
		},
		{	// IHE PDQ Query
			"Q22", "FindCandidates", "HL7", "Patient",
			"PID.3", "CX", "identifier", Identifier.class.getSimpleName(),
			"PID.5", "XPN", "name", HumanName.class.getSimpleName(),
			"PID.6", "XPN", "mothersMaidenName", StringType.class.getSimpleName(),
			"PID.7", "TS", "birthdate", DateType.class.getSimpleName(),
			"PID.8", "IS", "gender", CodeType.class.getSimpleName(),
			"PID.11", "XAD", "address", Address.class.getSimpleName(),
			"PID.13", "XTN", "phone", ContactPoint.class.getSimpleName(),
			"PID.18", "ID", null, Identifier.class.getSimpleName()
		},
		{	// IHE PIX Query
			"IHE PIX Query", "", "", Identifier.class.getSimpleName(),
			"Person Identifier", "CX", "identifier", Identifier.class.getSimpleName(), 
			"What Domains Returned", "CX", "name", HumanName.class.getSimpleName()
		}
	};
	
	public IBaseResource setup() {
		params = createResource(Parameters.class);
		mh = this.getFirstResource(MessageHeader.class);
		return params;
	}
	
	@Override
	public void parse(Segment seg) {
		Type query = getField(seg, 1);
		CodeableConcept queryName = DatatypeConverter.toCodeableConcept(query);
		if (queryName == null) {
			try {
				warn("Cannot parse unnamed query for: " + seg.encode());
			} catch (HL7Exception e) {
				// Ignore it.
			}
			return;
		}
		setup();
		
		params.getMeta().addTag(queryName.getCodingFirstRep());
		
		String queryTag = ParserUtils.toString(getField(seg, 2));
		String[] parameters = getQueryParameters(queryName);
		params.getMeta().addTag("QueryTag", queryTag, null);

		if (parameters.length == 0) {
			log.warn("Cannot parse {} query", query);
			return;
		}
		String request = getSearchRequest(seg, parameters);
		
		BundleEntryComponent entry = (BundleEntryComponent) params.getUserData(BundleEntryComponent.class.getName());
		Boolean isResponse = isResponse();
		if (entry != null && Boolean.TRUE.equals(isResponse)) {
			// Was this QPD in response to a query message, or was it the query itself.
			BundleEntryResponseComponent resp = entry.getResponse();
			resp.setLocation(request);
			resp.setLastModified(getBundle().getTimestamp());
		} else if (entry != null && Boolean.FALSE.equals(isResponse)) {
			BundleEntryRequestComponent req = entry.getRequest();
			req.setMethod(HTTPVerb.GET);
			req.setUrl(request);
		} else {
			params.addParameter("_search", request);
		}
	}

	private String getSearchRequest(Segment seg, String[] parameters) {
		StringBuilder request = null;
		request = new StringBuilder("/fhir/");
		request.append(parameters[3]).append("?");
		for (int i = 3, offset = 4; i <= seg.numFields() && offset < parameters.length; i++, offset += 4) {
			Type[] types = getFields(seg, i); // Get the fields
			for (Type t : types) {
				String fhirType = parameters[offset + 3];
				t = adjustIfVaries(t, parameters[offset + 1]);
				org.hl7.fhir.r4.model.Type converted = DatatypeConverter.convert(fhirType, t, null);
				addQueryParameter(request, params, parameters[offset + 2], converted);
			}
		}
		// Remove any trailing ? or &
		request.setLength(request.length() - 1);
		return request.toString();
	}
	
	private Boolean isResponse() {
		if (mh != null && mh.getMeta().hasTag()) {
			if (mh.getMeta().getTag().stream().anyMatch(c -> c.hasCode() && "QBP".equals(c.getCode()))) {
				return true;
			} else if (mh.getMeta().getTag().stream().anyMatch(c -> c.hasCode() && "RSP".equals(c.getCode()))) {
				return false;
			}
		}
		return null; // NOSONAR: null = unknown
	}

	/**
	 * Some segments can have varying field types, for example, QPD.  This method
	 * allows the field values to be adjusted to the appropriate V2 type.
	 *  
	 * @param t	The type to adjust.
	 * @param typeName The name of the field type to adjust to.
	 * @return	The adjusted type if adjustment is needed, otherwise the original type.
	 */
	private Type adjustIfVaries(Type t, String typeName) {
		if (t instanceof Varies v) {
			t = v.getData();
		}
		if (t instanceof GenericComposite) {
			// Well, this is unfortunate, we need to do some classloading shenanigans.
			t = convertTo(t, typeName);
		}
		return t;
	}

	private Type convertTo(Type t, String typeName) {
		Class<? extends Type> target = resolve("ca.uhn.hl7v2.model.v281.datatype", typeName);
		if (target == null) {
			target = resolve("ca.uhn.hl7v2.model.v251.datatype", typeName);
		}
		try {
			Type newType = target.getDeclaredConstructor(Message.class).newInstance(t.getMessage());
			newType.parse(t.encode());
			return newType;
		} catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException
				| NoSuchMethodException | SecurityException e) {
			log.warn("Cannot contruct new " + target.getClass().getName());
			return t;
		} catch (HL7Exception e) {
			log.warn("Cannot parse " + t + " into " + target.getClass().getName());
			return t;
		}
	}
	
	Class<? extends Type> resolve(String packageName, String typeName) {
		String className = packageName + "." + typeName;
		try {
			@SuppressWarnings("unchecked")
			Class<? extends Type> clazz = (Class<? extends Type>) Type.class.getClassLoader().loadClass(className);
			return clazz;
		} catch (ClassNotFoundException e1) {
			log.warn("Cannot resolve " + className);
			return null;
		}
	}

	private void addQueryParameter(StringBuilder request, Parameters params, String name, org.hl7.fhir.r4.model.Type converted) {
		if (converted == null) {
			return;
		}
		boolean used = false;
		switch (converted.getClass().getSimpleName()) {
		case "Address":
			Address addr = (Address) converted;
			for (StringType line: addr.getLine()) {
				appendParameter(request, line, name);
			}
			used = appendParameter(request, addr.getCity(), name + "-city")
				|| appendParameter(request, addr.getState(), name + "-state")
				|| appendParameter(request, addr.getPostalCode(), name + "-postalCode")
				|| appendParameter(request, addr.getCountry(), name + "-country");
			break;
		case "HumanName":
			HumanName hn = (HumanName) converted;
			used = appendParameter(request, hn.getFamily(), StringUtils.replace(name, "name", "family")) || 
				   appendParameter(request, hn.getGivenAsSingleString(), StringUtils.replace(name, "name", "given"));
			break;
		case "Identifier":
			Identifier ident = (Identifier) converted;
			String value = ident.hasSystem() ? ident.getSystem() + "|" : "";
			if (ident.hasValue()) {
				value += ident.getValue();
			}
			used = appendParameter(request, value, name);
			break;
		case "DateType":
			DateType dt = new DateType(((BaseDateTimeType)converted).getValue());
			used = appendParameter(request, dt.asStringValue(), name);
			break;
		case "ContactPoint":
			ContactPoint cp = (ContactPoint)converted;
			if (!cp.hasSystem() || cp.getSystem().toString().equals(name)) {
				used = appendParameter(request, cp.getValue(), name);
			}
			break;
		case "StringType":
			StringType string = (StringType)converted;
			used = appendParameter(request, string.asStringValue(), name);
			break;
		case "CodeType":
			CodeType code = (CodeType)converted;
			used = appendParameter(request, code.asStringValue(), name);
			break;
		default:
			break;
		}
		if (used) {
			params.addParameter().setName(name).setValue(converted);
		}
	}
	
	private boolean appendParameter(StringBuilder request, StringType value, String name) {
		return appendParameter(request, value.asStringValue(), name);
	}
	private boolean appendParameter(StringBuilder request, String value, String name) {
		if (!StringUtils.isBlank(value)) {
			request
				.append(name)
				.append("=")
				.append(URLEncoder.encode(value.trim(), StandardCharsets.UTF_8))
				.append("&");
			return true;
		}
		return false;
	}

	private String[] getQueryParameters(CodeableConcept queryName) {
		for (String[] names: v2QueryNames) {
			if (queryName.getCoding().stream().anyMatch(coding -> codingMatches(coding, names))) {
				return names;
			}
		}
		return new String[0];
	}
	
	boolean codingMatches(Coding coding, String[] names) {
		return StringUtils.isBlank(names[0]) || names[0].equals(coding.getCode());  
	}

}
