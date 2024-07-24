package gov.cdc.izgw.v2tofhir.utils;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.ServiceConfigurationError;

import org.hl7.fhir.r4.model.Immunization;
import org.hl7.fhir.r4.model.Patient;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.param.DateParam;
import ca.uhn.fhir.rest.param.NumberParam;
import ca.uhn.fhir.rest.param.TokenParam;
import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.DataTypeException;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.model.Segment;
import ca.uhn.hl7v2.model.Type;
import ca.uhn.hl7v2.model.Varies;
import ca.uhn.hl7v2.model.v251.datatype.CE;
import ca.uhn.hl7v2.model.v251.datatype.CX;
import ca.uhn.hl7v2.model.v251.datatype.ID;
import ca.uhn.hl7v2.model.v251.datatype.IS;
import ca.uhn.hl7v2.model.v251.datatype.NM;
import ca.uhn.hl7v2.model.v251.datatype.TS;
import ca.uhn.hl7v2.model.v251.datatype.XAD;
import ca.uhn.hl7v2.model.v251.datatype.XPN;
import ca.uhn.hl7v2.model.v251.datatype.XTN;
import ca.uhn.hl7v2.model.v251.message.QBP_Q11;
import ca.uhn.hl7v2.model.v251.segment.MSH;
import ca.uhn.hl7v2.model.v251.segment.QPD;
import io.azam.ulidj.ULID;

/**
 * This class supports construction of an HL7 QBP Message following the
 * CDC HL7 Version 2.5.1 Implementation Guide for Immunization Messaging
 * from HTTP GET parameters retrieved using ServletRequest.getParameters()
 * or from a map of Strings to a list of Strings.
 * 
 * NOTE On HAPI DatatypeException and HL7Exception:  The HAPI V2 code is pervasive with these
 * checked exceptions due to message validation capabilities.  Well written HAPI code is 
 * unlikely to encounter them.  As a user of this library, instead of catching the error
 * wherever it occurs, you can just simply wrap a whole method that uses it in a try/catch block.
 * 
 * @see <a href="https://repository.immregistries.org/files/resources/5bef530428317/hl7_2_5_1_release_1_5__2018_update.pdf">CDC HL7 Version 2.5.1 Implementation Guide for Immunization Messaging</a>
 * 	
 * @author Audacious Inquiry
 */
public class QBPUtils {
	private static final FhirContext R4 = FhirContext.forR4();
	private QBPUtils() {}
	/**
	 * Create a QBP message for an Immunization History (Z34) or Forecast (Z44)
	 * @param profile	The message profile (Z34 or Z44).
	 * @return The QBP message structure
	 * @throws DataTypeException	If an error occured creating the message.
	 */
	public static QBP_Q11 createMessage(
		String profile
	) throws DataTypeException {
		QBP_Q11 qbp = new QBP_Q11();
		MSH msh = qbp.getMSH();
		// Use standard encoding characters
		msh.getFieldSeparator().setValue("|");
		msh.getEncodingCharacters().setValue("^~\\&");
		
		// Set time of message to now.
		msh.getDateTimeOfMessage().getTime().setValue(new Date());
		
		// Set message type correctly
		msh.getMessageType().getMessageCode().setValue("QBP");
		msh.getMessageType().getTriggerEvent().setValue("Q11");
		msh.getMessageType().getMessageStructure().setValue("QBP_Q11");
		
		// Give it a unique identifier
		msh.getMessageControlID().setValue(ULID.random());
		
		// Set version and protocol flags
		msh.getVersionID().getVersionID().setValue("2.5.1");
		msh.getAcceptAcknowledgmentType().setValue("NE");
		msh.getApplicationAcknowledgmentType().setValue("NE");
		
		// Set the profile identifer
		msh.getMessageProfileIdentifier(0).getEntityIdentifier().setValue(profile);
		msh.getMessageProfileIdentifier(0).getNamespaceID().setValue("CDCPHINVS");
		
		// Initialize the QPD segment appropriately
		QPD qpd = qbp.getQPD();
		CE mqn = qpd.getMessageQueryName(); 
		mqn.getIdentifier().setValue(profile);
		mqn.getText().setValue(
			"Z44".equals(profile) ? "Request Evaluated History and Forecast" : "Request Immunization History"
		);
		mqn.getNameOfCodingSystem().setValue("CDCPHINVS");
		qpd.getQueryTag().setValue(ULID.random());
		
		return qbp;
	}
	
	/**
	 * Set the sending application for a QBP message.
	 * @param qbp	The QBP message
	 * @param sendingApp	The sending application
	 * @throws DataTypeException	If an error occurs
	 */
	public static void setSendingApplication(QBP_Q11 qbp, String sendingApp) throws DataTypeException {
		qbp.getMSH().getSendingApplication().getHd1_NamespaceID().setValue(sendingApp);	
	}
	
	/**
	 * Set the sending facility for a QBP message.
	 * @param qbp	The QBP message
	 * @param sendingFacility	The sending facility
	 * @throws DataTypeException	If an error occurs
	 */
	public static void setSendingFacility(QBP_Q11 qbp, String sendingFacility) throws DataTypeException {
		qbp.getMSH().getSendingFacility().getHd1_NamespaceID().setValue(sendingFacility);	
	}
	
	/**
	 * Set the receiving application for a QBP message.
	 * @param qbp	The QBP message
	 * @param receivingApp	The receiving application
	 * @throws DataTypeException	If an error occurs
	 */
	public static void setReceivingApplication(QBP_Q11 qbp, String receivingApp) throws DataTypeException {
		qbp.getMSH().getReceivingApplication().getHd1_NamespaceID().setValue(receivingApp);	
	}
	
	/**
	 * Set the receiving facility for a QBP message.
	 * @param qbp	The QBP message
	 * @param receivingFacility	The receiving facility
	 * @throws DataTypeException	If an error occurs
	 */
	public static void setReceivingFacility(QBP_Q11 qbp, String receivingFacility) throws DataTypeException {
		qbp.getMSH().getReceivingFacility().getHd1_NamespaceID().setValue(receivingFacility);	
	}
	
	/**
	 * Add the FHIR Search Request Parameters to the QBP Message.
	 * 
	 * @param qbp	The QBP message to update
	 * @param map	A map such as that returned by ServletRequest.getParameters() 
	 * containing the request parameters
	 * @return	The updated QPD Segment
	 * @throws HL7Exception If an error occurs
	 */
	public static QPD addRequestToQPD(QBP_Q11 qbp, Map<String, String[]> map) throws HL7Exception {
		for (Map.Entry<String, String[]> e: map.entrySet()) {
			addParameter(qbp.getQPD(), e.getKey(), Arrays.asList(e.getValue()));
		}
		return qbp.getQPD();
	}

	/**
	 * Add the FHIR Search Request Parameters to the QBP Message.
	 * 
	 * @param qbp	The QBP message to update
	 * @param map	A map similar to that returned by ServletRequest.getParameters() 
	 * containing the request parameters, save that arrays are lists.  NOTE: Only
	 * patient.identifier can be repeated.  
	 * @return	The updated QPD Segment
	 * @throws HL7Exception If an error occurs
	 */
	public static QPD addParamsToQPD(QBP_Q11 qbp, Map<String, List<String>> map) throws HL7Exception {
		for (Map.Entry<String, List<String>> e: map.entrySet()) {
			addParameter(qbp.getQPD(), e.getKey(), e.getValue());
		}
		return qbp.getQPD();
	}
	
	/** Search parameter for PatientList */
	public static final String PATIENT_LIST = Immunization.SP_PATIENT + "." + Patient.SP_IDENTIFIER;
	/** Search parameter for PatientName Family Part */
	public static final String PATIENT_NAME_FAMILY = Immunization.SP_PATIENT + "." + Patient.SP_FAMILY;
	/** Search parameter for PatientName Given and Middle Part */
	public static final String PATIENT_NAME_GIVEN = Immunization.SP_PATIENT + "." + Patient.SP_GIVEN;
	/** Search parameter for PatientName Suffix Part */
	public static final String PATIENT_NAME_SUFFIX = Immunization.SP_PATIENT + ".suffix";
	/** Search parameter for PatientName Use Part */
	public static final String PATIENT_NAME_USE = Immunization.SP_PATIENT + ".name-use";
	/** Search parameter for Mothers Maiden Name Extension */
	public static final String PATIENT_MOTHERS_MAIDEN_NAME = Immunization.SP_PATIENT + ".mothers-maiden-name";
	/** Search parameter for Birth Date */
	public static final String PATIENT_BIRTHDATE = Immunization.SP_PATIENT + "." + Patient.SP_BIRTHDATE;
	/** Search parameter for Gender */
	public static final String PATIENT_GENDER = Immunization.SP_PATIENT + "." + Patient.SP_GENDER;
	/** Search parameter for Patient Address Lines */
	public static final String PATIENT_ADDRESS = Immunization.SP_PATIENT + "." + Patient.SP_ADDRESS;
	/** Search parameter for Patient Address City */
	public static final String PATIENT_ADDRESS_CITY = Immunization.SP_PATIENT + "." + Patient.SP_ADDRESS_CITY;
	/** Search parameter for Patient Address State */
	public static final String PATIENT_ADDRESS_STATE = Immunization.SP_PATIENT + "." + Patient.SP_ADDRESS_STATE;
	/** Search parameter for Patient Address Postal Code */
	public static final String PATIENT_ADDRESS_POSTAL = Immunization.SP_PATIENT + "." + Patient.SP_ADDRESS_POSTALCODE;
	/** Search parameter for Patient Address Country */
	public static final String PATIENT_ADDRESS_COUNTRY = Immunization.SP_PATIENT + "." + Patient.SP_ADDRESS_COUNTRY;
	/** Search parameter for Patient Phone */
	public static final String PATIENT_HOMEPHONE = Immunization.SP_PATIENT + "." + Patient.SP_PHONE;
	/** Search parameter for Patient Multiple Birth Indicator */
	public static final String PATIENT_MULTIPLE_BIRTH_INDICATOR  = Immunization.SP_PATIENT + ".multipleBirth-indicator";
	/** Search parameter for Patient Multiple Birth Order */
	public static final String PATIENT_MULTIPLE_BIRTH_ORDER = Immunization.SP_PATIENT + ".multipleBirth-order";
	
	/**
	 * Add a given search parameter to the QPD segment.
	 * @param qpd	The QPD segment
	 * @param fhirParamName	The FHIR parameter name
	 * @param params	The list of parameters.
	 * @throws HL7Exception	If an error occurs.
	 */
	public static void addParameter(	// NOSONAR: Giant switch is acceptable
		QPD qpd, String fhirParamName, List<String> params
	) throws HL7Exception {
		if (fhirParamName == null || fhirParamName.isEmpty()) {
			return;
		}
		XPN name = getField(qpd, 4, XPN.class);
		XPN maiden = getField(qpd, 5, XPN.class);
		TS birthDate = getField(qpd, 6, TS.class);
		XAD address = getField(qpd, 8, XAD.class);
		TokenParam token = null;
		
		for (String param: params) {
			switch (fhirParamName) {
			case PATIENT_LIST: //	QPD-3	PatientList (can repeat)
				token = new TokenParam();
				token.setValueAsQueryToken(R4, fhirParamName, null, param);
				// get QPD-3
				CX cx = addRepetition(qpd, 3, CX.class);
				cx.getIDNumber().setValue(token.getValue());
				cx.getAssigningAuthority().getNamespaceID().setValue(token.getSystem());
				cx.getIdentifierTypeCode().setValue("MR");  // Per CDC Guide
				break;
				
			case PATIENT_NAME_FAMILY: //	QPD-4.1	PatientName
				name.getFamilyName().getSurname().setValue(param);
				break;
			case PATIENT_NAME_GIVEN: //	QPD-4.2	and QPD-4.3	PatientName
				if (name.getGivenName().isEmpty()) {
					name.getGivenName().setValue(param);
				} else {
					name.getSecondAndFurtherGivenNamesOrInitialsThereof().setValue(param);
				}
				break;
			case PATIENT_NAME_SUFFIX: //	QPD-4.4	PatientName
				name.getSuffixEgJRorIII().setValue(param);
				break;
			case PATIENT_NAME_USE: //	QPD-4.7	PatientName
				name.getNameTypeCode().setValue("L");
				break;
			case PATIENT_MOTHERS_MAIDEN_NAME: //	QPD-5	PatientMotherMaidenName
				maiden.getFamilyName().getSurname().setValue(param);
				maiden.getNameTypeCode().setValue("M");
				break;
			// Given and Suffix won't be used in V2 searches on mother's maiden name, but it allows
			// the v2tofhir components to round trip.
			case PATIENT_MOTHERS_MAIDEN_NAME + "-given":
				if (maiden.getGivenName().isEmpty()) {
					maiden.getGivenName().setValue(param);
				} else {
					maiden.getSecondAndFurtherGivenNamesOrInitialsThereof().setValue(param);
				}
				break;
			case PATIENT_MOTHERS_MAIDEN_NAME + "-suffix":
				maiden.getSuffixEgJRorIII().setValue(param);
				break;
			case PATIENT_BIRTHDATE: //	QPD-6	PatientDateOfBirth
				DateParam dateParam = new DateParam();
				dateParam.setValueAsQueryToken(R4, fhirParamName, null, param);
				birthDate.getTime().setValue(dateParam.getValueAsString().replace("-", ""));
				break;
				
			case PATIENT_GENDER: //	QPD-7	PatientSex
				token = new TokenParam();
				token.setValueAsQueryToken(R4, fhirParamName, null, param);
				if (token.getValueNotNull().length() == 0) {
					token.setValue("U");	// Per CDC Guide
				}
				// HL7 codes are same as first character of FHIR codes.
				getField(qpd, 7, IS.class).setValue(token.getValue().substring(0, 1).toUpperCase());
				break;
			case PATIENT_ADDRESS: //	QPD-8-1 and QPD-8-2	PatientAddress
				if (address.getStreetAddress().isEmpty()) {
					address.getStreetAddress().getStreetOrMailingAddress().setValue(param);
				} else {
					address.getOtherDesignation().setValue(param);
				}
				break;
			case PATIENT_ADDRESS_CITY: //	QPD-8-3	PatientAddress
				address.getCity().setValue(param);
				break;
			case PATIENT_ADDRESS_STATE: //	QPD-8-4	PatientAddress
				address.getStateOrProvince().setValue(param);
				break;
			case PATIENT_ADDRESS_POSTAL: //	QPD-8-5	PatientAddress
				address.getZipOrPostalCode().setValue(param);
				break;
			case PATIENT_ADDRESS_COUNTRY: //	QPD-8-5	PatientAddress
				address.getCountry().setValue(param);
				break;
			case PATIENT_HOMEPHONE: //	QPD-9	PatientHomePhone
				token = new TokenParam();
				token.setValueAsQueryToken(R4, PATIENT_HOMEPHONE, null, param);
				getField(qpd, 9, XTN.class).getTelephoneNumber().setValue(token.getValue());
				break;
			case PATIENT_MULTIPLE_BIRTH_INDICATOR: //	QPD-10	PatientMultipleBirthIndicator
				token = new TokenParam();
				token.setValueAsQueryToken(R4, PATIENT_MULTIPLE_BIRTH_INDICATOR, null, param);
				String mbi = token.getValueNotNull();
				if (mbi.length() != 0) {
					switch (mbi.toLowerCase().charAt(0)) {
					case 'y', 't':
						getField(qpd, 10, ID.class).setValue("Y"); break;
					case 'n', 'f':
						getField(qpd, 10, ID.class).setValue("N"); break;
					default:
						break;
					}
				}
				break;
			case PATIENT_MULTIPLE_BIRTH_ORDER: //	QPD-11	PatientBirthOrder
				NumberParam number = new NumberParam();
				number.setValueAsQueryToken(R4, PATIENT_MULTIPLE_BIRTH_ORDER, null, param);
				getField(qpd, 10, NM.class).setValue(number.getValue().toPlainString());
				break;
			default:
				break;
			}
		}
		if (!name.isEmpty()) {
			name.getNameTypeCode().setValue("L");
		}
		if (!maiden.isEmpty()) {
			maiden.getNameTypeCode().setValue("M");
		}
		if (!address.isEmpty()) {
			address.getAddressType().setValue("L");
		}
	}

	/**
	 * Return the first repetition if it is empty, or create a new repetition
	 * at the end.
	 * 
	 * @param segment	The segment
	 * @param fieldNo	The field
	 * @return	An empty repetition of the field 
	 * @throws HL7Exception If an error occurs
	 */
	public static Type addRepetition(Segment segment, int fieldNo) throws HL7Exception {
		Type[] t = segment.getField(fieldNo);
		if (t.length == 1 && t[0].isEmpty()) {
			return t[0];
		}
		return addRepetition(segment, fieldNo, t.length);
	}

	/**
	 * A convenience method to get a field from a segment of a specific type
	 * for fields with Varies content.
	 * 
	 * NOTE: This method works well on GenericSegments and segments that have
	 * Varies content for the given field, -- OR -- if the type specified is 
	 * the type that the segment model reports for the field (or a superclass of it).
	 * 
	 * @param <T>	The name of the type
	 * @param segment	The segment to get it from
	 * @param fieldNo	The field number
	 * @param theClass	The type of field to get.
	 * @return	The first field of that type in the segment.
	 * @throws HL7Exception	If an error occurs
	 */
	private static <T extends Type> T getField(Segment segment, int fieldNo, Class<T> theClass) throws HL7Exception {
		Type type = segment.getField(fieldNo, 0);
		if (theClass.isInstance(type)) {
			return theClass.cast(type);
		}
		return convertType(theClass, type);
	}

	/**
	 * Add a repetition of the field, with the given type.
	 * 
	 * @param <T>	The type of the field
	 * @param segment	The segment containing the field
	 * @param fieldNo	The field number
	 * @param theClass	The class for the type
	 * @return	The requested field
	 * @throws HL7Exception	If an error occurs
	 */
	public static <T extends Type> T addRepetition(Segment segment, int fieldNo, Class<T> theClass) throws HL7Exception {
		Type[] t = segment.getField(fieldNo);
		Type type;
		if (t.length == 1 && t[0].isEmpty()) {
			type = t[0];
		} else {
			type = addRepetition(segment, fieldNo, t.length);
		}
		return convertType(theClass, type);
	}
	
	private static <T extends Type> T convertType(Class<T> theClass, Type type)
			throws ServiceConfigurationError, DataTypeException {
		if (theClass.isInstance(type)) {
			return theClass.cast(type);
		}
		
		if (type instanceof Varies v) {
			T data;
			try {
				data = theClass.getDeclaredConstructor(Message.class).newInstance((Object)null);
			} catch (InstantiationException | IllegalAccessException | IllegalArgumentException
					| InvocationTargetException | NoSuchMethodException | SecurityException e) {
				throw new ServiceConfigurationError("HAPI V2 is not Happy creating " + theClass.getName(), e);
			}
			v.setData(data);
			return data;
		}
		
		throw new IllegalArgumentException("Field is not an instance of Varies or of " + theClass.getSimpleName());
	}

	/**
	 * Add a repetition of the field at the specified position
	 * @param segment	The segment
	 * @param fieldNo	The one-based field number
	 * @param rep		The repetition number (zero-based)
	 * @return	The new field.
	 * @throws HL7Exception If an error occurs
	 */
	public static Type addRepetition(Segment segment, int fieldNo, int rep) throws HL7Exception {
		Type[] t = segment.getField(fieldNo);
		while (t.length <= rep) {
			segment.getField(fieldNo, t.length);
			t = segment.getField(fieldNo);
		}
		return t[rep];
	}
}
