package gov.cdc.izgw.v2tofhir.utils;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.ServiceConfigurationError;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.DataTypeException;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.model.Segment;
import ca.uhn.hl7v2.model.Type;
import ca.uhn.hl7v2.model.Varies;
import ca.uhn.hl7v2.model.v251.datatype.CE;
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
	static final FhirContext R4 = FhirContext.forR4();
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
	 * @param isPatient 
	 * @return	The updated QPD Segment
	 * @throws HL7Exception If an error occurs
	 */
	public static IzQuery addRequestParamsToQPD(QBP_Q11 qbp, Map<String, String[]> map, boolean isPatient) throws HL7Exception {
		IzQuery query = new IzQuery(qbp.getQPD());
		for (Map.Entry<String, String[]> e: map.entrySet()) {
			// Modify the parameter name for patient queries so that the 
			// keys are preceded by "patient."
			String param = isPatient ? "patient." + e.getKey() : e.getKey();
			query.addParameter(param, Arrays.asList(e.getValue()));
		}
		query.update();
		return query;
	}

	/**
	 * Add the FHIR Search Request Parameters to the QBP Message.
	 * 
	 * @param qbp	The QBP message to update
	 * @param map	A map similar to that returned by ServletRequest.getParameters() 
	 * containing the request parameters, save that arrays are lists.  NOTE: Only
	 * patient.identifier can be repeated.  
	 * @param isPatient True if the request is for the Patient resource
	 * @return	The updated QPD Segment
	 * @throws HL7Exception If an error occurs
	 */
	public static IzQuery addParamsToQPD(QBP_Q11 qbp, Map<String, List<String>> map, boolean isPatient) throws HL7Exception {
		IzQuery query = new IzQuery(qbp.getQPD());
		for (Map.Entry<String, List<String>> e: map.entrySet()) {
			// Modify the parameter name for patient queries so that the 
			// keys are preceded by "patient."
			String param = isPatient && !e.getKey().startsWith("_") ? "patient." + e.getKey() : e.getKey();
			query.addParameter(param, e.getValue());
		}
		query.update();
		return query;
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
	static <T extends Type> T getField(Segment segment, int fieldNo, Class<T> theClass) throws HL7Exception {
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
