package gov.cdc.izgw.v2tofhir.utils;

import java.util.List;

import org.hl7.fhir.instance.model.api.IAnyResource;
import org.hl7.fhir.r4.model.Immunization;
import org.hl7.fhir.r4.model.Patient;
import ca.uhn.fhir.rest.param.DateParam;
import ca.uhn.fhir.rest.param.NumberParam;
import ca.uhn.fhir.rest.param.TokenParam;
import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.v251.datatype.CX;
import ca.uhn.hl7v2.model.v251.datatype.ID;
import ca.uhn.hl7v2.model.v251.datatype.IS;
import ca.uhn.hl7v2.model.v251.datatype.NM;
import ca.uhn.hl7v2.model.v251.datatype.ST;
import ca.uhn.hl7v2.model.v251.datatype.TS;
import ca.uhn.hl7v2.model.v251.datatype.XAD;
import ca.uhn.hl7v2.model.v251.datatype.XPN;
import ca.uhn.hl7v2.model.v251.datatype.XTN;
import ca.uhn.hl7v2.model.v251.segment.QPD;
import ca.uhn.hl7v2.model.v251.segment.RCP;
import lombok.Data;

/**
 * A structure to manage query parameters.
 * @author Audacious Inquiry
 */
@Data
public class IzQuery {
	/** The number of results to return */
	public static final String COUNT = "_count";
	/** Code used for an Immunization History Request */
	public static final String HISTORY = "Z34";
	/** Search parameter for Patient Address Lines */
	public static final String PATIENT_ADDRESS = Immunization.SP_PATIENT + "." + Patient.SP_ADDRESS;
	/** Search parameter for Patient Address City */
	public static final String PATIENT_ADDRESS_CITY = Immunization.SP_PATIENT + "." + Patient.SP_ADDRESS_CITY;
	/** Search parameter for Patient Address Country */
	public static final String PATIENT_ADDRESS_COUNTRY = Immunization.SP_PATIENT + "." + Patient.SP_ADDRESS_COUNTRY;
	/** Search parameter for Patient Address Postal Code */
	public static final String PATIENT_ADDRESS_POSTAL = Immunization.SP_PATIENT + "." + Patient.SP_ADDRESS_POSTALCODE;
	/** Search parameter for Patient Address State */
	public static final String PATIENT_ADDRESS_STATE = Immunization.SP_PATIENT + "." + Patient.SP_ADDRESS_STATE;
	/** Search parameter for Birth Date */
	public static final String PATIENT_BIRTHDATE = Immunization.SP_PATIENT + "." + Patient.SP_BIRTHDATE;
	/** Search parameter for Gender */
	public static final String PATIENT_GENDER = Immunization.SP_PATIENT + "." + Patient.SP_GENDER;
	/** Search parameter for Patient Phone */
	public static final String PATIENT_HOMEPHONE = Immunization.SP_PATIENT + "." + Patient.SP_PHONE;

	/** Search parameter for PatientList */
	public static final String PATIENT_LIST = Immunization.SP_PATIENT + "." + Patient.SP_IDENTIFIER;
	/** Search parameter for Mothers Maiden Name Extension */
	public static final String PATIENT_MOTHERS_MAIDEN_NAME = Immunization.SP_PATIENT + ".mothers-maiden-name";
	/** Search parameter for Patient Multiple Birth Indicator */
	public static final String PATIENT_MULTIPLE_BIRTH_INDICATOR  = Immunization.SP_PATIENT + ".multipleBirth-indicator";
	/** Search parameter for Patient Multiple Birth Order */
	public static final String PATIENT_MULTIPLE_BIRTH_ORDER = Immunization.SP_PATIENT + ".multipleBirth-order";
	/** Search parameter for PatientName Family Part */
	public static final String PATIENT_NAME_FAMILY = Immunization.SP_PATIENT + "." + Patient.SP_FAMILY;
	/** Search parameter for PatientName Given and Middle Part */
	public static final String PATIENT_NAME_GIVEN = Immunization.SP_PATIENT + "." + Patient.SP_GIVEN;
	/** Search parameter for PatientName Suffix Part */
	public static final String PATIENT_NAME_SUFFIX = Immunization.SP_PATIENT + ".suffix";
	/** Search parameter for PatientName Use Part */
	public static final String PATIENT_NAME_USE = Immunization.SP_PATIENT + ".name-use";
	/** Code used for an Immunization Evaluated History and Recommendation Request */
	public static final String RECOMMENDATION = "Z44";
	private static final String CANNOT_REPEAT = "Cannot repeat query parameter: ";

	/** The patient address */
	private final XAD address;
	/** The patient birth date */
	private final TS birthDate;
	/** The patient gender */
	private final IS gender;
	/** The mothers maiden name */
	private final XPN maiden;
	/** The name */
	private final XPN name;
	private final QPD qpd;
	/** The patient home phone */
	private final ST telephone;
	/** Max number of records to return */
	int count = 5;
	/** If an id was found */
	boolean hasId = false;

	/**
	 * Create a new Query for an Immunization or ImmunizationRecommendation
	 * @param qpd	The QPD to create
	 * @throws HL7Exception
	 */
	public IzQuery(QPD qpd) throws HL7Exception {
		this.qpd = qpd;
		name = QBPUtils.getField(qpd, 4, XPN.class);
		maiden = QBPUtils.getField(qpd, 5, XPN.class);
		birthDate = QBPUtils.getField(qpd, 6, TS.class);
		gender = QBPUtils.getField(qpd, 7, IS.class);
		address = QBPUtils.getField(qpd, 8, XAD.class);
		telephone = QBPUtils.getField(qpd, 9, XTN.class).getTelephoneNumber();
	}
	
	/**
	 * Add a given search parameter to the QPD segment.
	 * @param fhirParamName	The FHIR parameter name
	 * @param params	The list of parameters.
	 * @throws HL7Exception	If an error occurs.
	 */
	public void addParameter(	// NOSONAR: Giant switch is acceptable
		String fhirParamName, List<String> params
	) throws HL7Exception {
		if (fhirParamName == null || fhirParamName.isEmpty()) {
			return;
		}
		for (String param: params) {
			if (fhirParamName.length() > 0 && fhirParamName.charAt(0) == '_' && !COUNT.equals(fhirParamName)) {
				// Ignore FHIR special search parameters.
				continue;
			}
			switch (fhirParamName) {
			case COUNT:
				setCount(param);
				break;
				
			case PATIENT_LIST: //	QPD-3	PatientList (can repeat)
				setPatientList(fhirParamName, param);
				break;
				
			case PATIENT_NAME_FAMILY: //	QPD-4.1	PatientName
				setPatientFamilyName(param);
				break;
			case PATIENT_NAME_GIVEN: //	QPD-4.2	and QPD-4.3	PatientName
				setPatientGivenName(param);
				break;
			case PATIENT_NAME_SUFFIX: //	QPD-4.4	PatientName
				setPatientNameSuffix(param);
				break;

			case PATIENT_MOTHERS_MAIDEN_NAME: //	QPD-5	PatientMotherMaidenName
				setMothersMaidenName(param);
				break;
			// Given and Suffix won't be used in V2 searches on mother's maiden name, but it allows
			// the v2tofhir components to round trip.
			case PATIENT_MOTHERS_MAIDEN_NAME + "-given":
				setMothersMaidenGivenName(param);
				break;
			case PATIENT_MOTHERS_MAIDEN_NAME + "-suffix":
				setMothersMaidenNameSuffix(param);
				break;
			case PATIENT_BIRTHDATE: //	QPD-6	PatientDateOfBirth
				setPatientBirthDate(fhirParamName, param);
				break;
				
			case PATIENT_GENDER: //	QPD-7	PatientSex
				setPatientGender(fhirParamName, param);
				break;
			
			case PATIENT_ADDRESS: //	QPD-8-1 and QPD-8-2	PatientAddress
				setPatientAddress(param);
				break;
			case PATIENT_ADDRESS_CITY: //	QPD-8-3	PatientAddress
				setPatientSuffixCity(param);
				break;
			case PATIENT_ADDRESS_STATE: //	QPD-8-4	PatientAddress
				setPatientAddressState(param);
				break;
			case PATIENT_ADDRESS_POSTAL: //	QPD-8-5	PatientAddress
				setPatientAddressPostal(param);
				break;
			case PATIENT_ADDRESS_COUNTRY: //	QPD-8-5	PatientAddress
				setPatientAddressCountry(param);
				break;
			
			case PATIENT_HOMEPHONE: //	QPD-9	PatientHomePhone
				setPatientPhone(param);
				break;
			
			case PATIENT_MULTIPLE_BIRTH_INDICATOR: //	QPD-10	PatientMultipleBirthIndicator
				setMultipleBirthIndicator(param);
				break;
			
			case PATIENT_MULTIPLE_BIRTH_ORDER: //	QPD-11	PatientBirthOrder
				setMultipleBirthOrder(param);
				break;
			
			default:
				throw new IllegalArgumentException("Invalid query parameter: " + param);
			}
		}
	}
	
	/**
	 * Finalize some the parameters and then check for a valid query
	 * @throws HL7Exception Should an error occur during parsing of HL7 content
	 */
	public void update() throws HL7Exception {
		if (!getName().isEmpty()) {
			getName().getNameTypeCode().setValue("L");
		}
		if (!getMaiden().isEmpty()) {
			getMaiden().getNameTypeCode().setValue("M");
		}
		if (!getAddress().isEmpty()) {
			getAddress().getAddressType().setValue("L");
		}
		RCP rcp = (RCP) getQpd().getMessage().get("RCP");
		rcp.getQuantityLimitedRequest().getQuantity().setValue(Integer.toString(getCount()));
		rcp.getQuantityLimitedRequest().getUnits().getIdentifier().setValue("RD");
		rcp.getQuantityLimitedRequest().getUnits().getText().setValue("records");
		rcp.getQuantityLimitedRequest().getUnits().getNameOfCodingSystem().setValue("HL70126");
		
		if (!isHasId() && 
			(getName().isEmpty() || getGender().isEmpty() || getBirthDate().isEmpty()) ) {
			throw new IllegalArgumentException("A query must contain either a "
					+ "patient.identifier or the patient name, gender, and birthDate");
		}
	}

	private void setCount(String param) {
		NumberParam number = new NumberParam();
		number.setValueAsQueryToken(QBPUtils.R4, COUNT, null, param);
		setCount(number.getValue().intValue());
		if (getCount() > 10) {
			throw new IllegalArgumentException("_count must be <= 10");
		} else if (getCount() <= 0) {
			throw new IllegalArgumentException("_count must be > 0");
		}
	}
	private void setCount(int count) {
		this.count = count;
	}

	private void setMothersMaidenGivenName(String param) throws HL7Exception {
		if (getMaiden().getGivenName().isEmpty()) {
			getMaiden().getGivenName().setValue(param);
		} else if (getMaiden().getSecondAndFurtherGivenNamesOrInitialsThereof().isEmpty()) {
			getMaiden().getSecondAndFurtherGivenNamesOrInitialsThereof().setValue(param);
		} else {
			throw new IllegalArgumentException("Cannot repeat query parameter more than one: " + param + "-given");
		}
	}

	private void setMothersMaidenName(String param) throws HL7Exception {
		if (getMaiden().getFamilyName().getSurname().isEmpty()) {
			getMaiden().getFamilyName().getSurname().setValue(param);
			getMaiden().getNameTypeCode().setValue("M");
		} else {
			throw new IllegalArgumentException(IzQuery.CANNOT_REPEAT + param);
		}
	}

	private void setMothersMaidenNameSuffix(String param) throws HL7Exception {
		if (!getMaiden().getSuffixEgJRorIII().isEmpty()) {
			throw new IllegalArgumentException(IzQuery.CANNOT_REPEAT + param + "-suffix");
		}
		getMaiden().getSuffixEgJRorIII().setValue(param);
	}

	private void setMultipleBirthIndicator(String param) throws HL7Exception{
		TokenParam token;
		token = new TokenParam();
		token.setValueAsQueryToken(QBPUtils.R4, PATIENT_MULTIPLE_BIRTH_INDICATOR, null, param);
		String mbi = token.getValueNotNull();
		ID mbid = QBPUtils.getField(qpd, 10, ID.class);
		if (!mbid.isEmpty()) {
			throw new IllegalArgumentException(IzQuery.CANNOT_REPEAT + param);
		}
		if (mbi.length() != 0) {
			switch (mbi.toLowerCase().charAt(0)) {
			case 'y', 't':
				mbid.setValue("Y"); break;
			case 'n', 'f':
				mbid.setValue("N"); break;
			default:
				break;
			}
		}
	}

	private void setMultipleBirthOrder(String param) throws HL7Exception{
		NumberParam number = new NumberParam();
		number.setValueAsQueryToken(QBPUtils.R4, PATIENT_MULTIPLE_BIRTH_ORDER, null, param);
		
		NM nm = QBPUtils.getField(qpd, 11, NM.class);
		if (!nm.isEmpty()) {
			throw new IllegalArgumentException(IzQuery.CANNOT_REPEAT + param);
		}
		nm.setValue(number.getValue().toPlainString());
	}

	private void setPatientAddress(String param) throws HL7Exception{
		if (getAddress().getStreetAddress().isEmpty()) {
			getAddress().getStreetAddress().getStreetOrMailingAddress().setValue(param);
		} else if (getAddress().getOtherDesignation().isEmpty()) {
			getAddress().getOtherDesignation().setValue(param);
		} else {
			throw new IllegalArgumentException("Cannot repeat query parameter more than once: " + param);
		}
	}

	private void setPatientAddressCountry(String param) throws HL7Exception{
		if (!getAddress().getCountry().isEmpty()) {
			throw new IllegalArgumentException(IzQuery.CANNOT_REPEAT + param);
		}
		getAddress().getCountry().setValue(param);
	}

	private void setPatientAddressPostal(String param) throws HL7Exception{
		if (!getAddress().getZipOrPostalCode().isEmpty()) {
			throw new IllegalArgumentException(IzQuery.CANNOT_REPEAT + param);
		}
		getAddress().getZipOrPostalCode().setValue(param);
	}

	private void setPatientAddressState(String param) throws HL7Exception{
		if (!getAddress().getStateOrProvince().isEmpty()) {
			throw new IllegalArgumentException(IzQuery.CANNOT_REPEAT + param);
		}
		getAddress().getStateOrProvince().setValue(param);
	}

	private void setPatientBirthDate(String fhirParamName, String param) throws HL7Exception{
		if (!getBirthDate().getTime().isEmpty()) {
			throw new IllegalArgumentException(IzQuery.CANNOT_REPEAT + param);
		}
		DateParam dateParam = new DateParam();
		dateParam.setValueAsQueryToken(QBPUtils.R4, fhirParamName, null, param);
		getBirthDate().getTime().setValue(dateParam.getValueAsString().replace("-", ""));
	}

	private void setPatientFamilyName(String param) throws HL7Exception{
		if (!getName().isEmpty()) {
			throw new IllegalArgumentException(IzQuery.CANNOT_REPEAT + param);
		}
		getName().getFamilyName().getSurname().setValue(param);
	}

	private void setPatientGender(String fhirParamName, String param) throws HL7Exception{
		TokenParam token;
		if (!getGender().isEmpty()) {
			throw new IllegalArgumentException(IzQuery.CANNOT_REPEAT + param);
		}
		token = new TokenParam();
		token.setValueAsQueryToken(QBPUtils.R4, fhirParamName, null, param);
		if (token.getValueNotNull().length() == 0) {
			token.setValue("U");	// Per CDC Guide
		}
		// HL7 codes are same as first character of FHIR codes.
		getGender().setValue(token.getValue().substring(0, 1).toUpperCase());
	}

	private void setPatientGivenName(String param) throws HL7Exception{
		if (getName().getGivenName().isEmpty()) {
			getName().getGivenName().setValue(param);
		} else if (getName().getSecondAndFurtherGivenNamesOrInitialsThereof().isEmpty()) {
			getName().getSecondAndFurtherGivenNamesOrInitialsThereof().setValue(param);
		} else {
			throw new IllegalArgumentException("Cannot repeat query parameter more than once: " + param);
		}
	}

	private void setPatientList(String fhirParamName, String param) throws HL7Exception{
		TokenParam token;
		token = new TokenParam();
		token.setValueAsQueryToken(QBPUtils.R4, fhirParamName, null, param);

		hasId = true;
		// get QPD-3
		CX cx = QBPUtils.addRepetition(qpd, 3, CX.class);
		cx.getIDNumber().setValue(token.getValue());
		cx.getAssigningAuthority().getNamespaceID().setValue(token.getSystem());
		cx.getIdentifierTypeCode().setValue("MR");  // Per CDC Guide
	}

	private void setPatientNameSuffix(String param) throws HL7Exception{
		if (getName().getSuffixEgJRorIII().isEmpty()) {
			getName().getSuffixEgJRorIII().setValue(param);
		} else {
			throw new IllegalArgumentException(IzQuery.CANNOT_REPEAT + param);
		}
	}

	private void setPatientPhone(String param) throws HL7Exception{
		TokenParam token;
		token = new TokenParam();
		token.setValueAsQueryToken(QBPUtils.R4, PATIENT_HOMEPHONE, null, param);
		if (!getTelephone().isEmpty()) {
			throw new IllegalArgumentException(IzQuery.CANNOT_REPEAT + param);
		}
		getTelephone().setValue(token.getValue());
	}

	private void setPatientSuffixCity(String param) throws HL7Exception{
		if (!getAddress().getCity().isEmpty()) {
			throw new IllegalArgumentException(IzQuery.CANNOT_REPEAT + param);
		}
		getAddress().getCity().setValue(param);
	}
}