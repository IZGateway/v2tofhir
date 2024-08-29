package gov.cdc.izgw.v2tofhir.utils;

import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;

/**
 * Constants for various codes that are commonly used during conversions.
 * 
 * @author Audacious Inquiry
 *
 */
public class Codes {
	/**
	 * Standard code for the Filler Order Group Identifier
	 */
	public static final CodeableConcept FILLER_ORDER_GROUP_TYPE =
		new CodeableConcept().addCoding(
			new Coding(Systems.ID_TYPE, "FGN", "Filler Group Number"));
	/**
	 * Standard code for the Filler Order Identifier
	 */
	public static final CodeableConcept FILLER_ORDER_IDENTIFIER_TYPE =
		new CodeableConcept().addCoding(
			new Coding(Systems.ID_TYPE, "FILL", "Filler Order Number"));
	/**
	 * Standard code for the Placer Order Group Identifier
	 */
	public static final CodeableConcept PLACER_ORDER_GROUP_TYPE =
		new CodeableConcept().addCoding(
			new Coding(Systems.ID_TYPE, "PGN", "Placer Group Number"));
	/**
	 * Standard code for the Placer Order Identifier
	 */
	public static final CodeableConcept PLACER_ORDER_IDENTIFIER_TYPE =
		new CodeableConcept().addCoding(
			new Coding(Systems.ID_TYPE, "PLAC", "Placer Order Number"));
	
	/**
	 * Code for create used in Provenance
	 */
	public static final CodeableConcept CREATE_ACTIVITY = 
			new CodeableConcept().addCoding(new Coding("http://terminology.hl7.org/CodeSystem/v3-DataOperation", "CREATE", "create"));
	/**
	 * Code for assembler used in Provenance
	 */
	
	public static final CodeableConcept ASSEMBLER_AGENT = new CodeableConcept().addCoding(new Coding("http://terminology.hl7.org/CodeSystem/provenance-participant-type", "assembler", "Assembler"));
	
	private static final String V2TABLES = "http://terminology.hl7.org/CodeSystem/v2-";
	
	/**
	 * The function code for an Ordering Provider
	 */
	public static final CodeableConcept ORDERING_PROVIDER_FUNCTION_CODE = 
			new CodeableConcept().addCoding(new Coding(V2TABLES + "0433", "OP", "Ordering Provider"));
	/**
	 * The function code for an Administrating Provider
	 */
	public static final CodeableConcept ADMIN_PROVIDER_FUNCTION_CODE = 
			new CodeableConcept().addCoding(new Coding(V2TABLES + "0433", "AP", "Administrating Provider"));

	/**
	 * The role code for the Medical Director of laboratory or other observation producing facility. 
	 */
	public static final CodeableConcept MEDICAL_DIRECTOR_ROLE_CODE = 
			new CodeableConcept().addCoding(new Coding(V2TABLES + "0912", "MDIR", "Medical Director"));
	
	/** The code for a Social Security Number found on Identifier.type */
	public static final CodeableConcept SSN_TYPE = new CodeableConcept().addCoding(
			new Coding(V2TABLES + "0203", "SS", "Social Security Number")
		);
	
	/** The concept of MOTHER */
	public static final CodeableConcept MOTHER = new CodeableConcept().addCoding(
			new Coding("http://terminology.hl7.org/CodeSystem/v3-RoleCode", "MTH", "Mother")
		);
	
	/** Attending Provider */
	public static final CodeableConcept ATTENDING_PARTICIPANT = new CodeableConcept()
		.addCoding(
			new Coding(Systems.V3_PARTICIPATION_TYPE, "ATND", "attender")
		);
	/** Consulting Provider */
	public static final CodeableConcept CONSULTING_PARTICIPANT = new CodeableConcept()
		.addCoding(
			new Coding(Systems.V3_PARTICIPATION_TYPE, "CON", "consultant")
		);
	/** Referring Provider */
	public static final CodeableConcept REFERRING_PARTICIPANT = new CodeableConcept()
		.addCoding(
			new Coding(Systems.V3_PARTICIPATION_TYPE, "REF", "referrer")
		);
	
	/** Admitting Provider */
	public static final CodeableConcept ADMITTING_PARTICIPANT = new CodeableConcept()
			.addCoding(
				new Coding(Systems.V3_PARTICIPATION_TYPE, "ADM", "admitter")
			);
	
	/** An otherwise unspecified participant */
	public static final CodeableConcept PARTICIPANT = new CodeableConcept()
			.addCoding(
					new Coding(Systems.V3_PARTICIPATION_TYPE, "PART", "participant")
				);
	
	/** Visit Number */
	public static final CodeableConcept VISIT_NUMBER = new CodeableConcept()
			.addCoding(
					new Coding(V2TABLES + "0203", "VN", "visit number")
				);
	
	private Codes() {}
}
