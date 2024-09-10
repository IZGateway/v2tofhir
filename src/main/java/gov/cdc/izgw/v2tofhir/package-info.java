/**
 * The V2 to FHIR package contains the core code for the IZ Gateway Transformation Service 
 * components that perform conversion of HL7 V2 Immunization Messages written according to 
 * the CDC HL7 Version 2.5.1 Implementation Guide for Immunization Messaging to HL7 FHIR 
 * Resources conforming to FHIR R4, FHIR US Core V7.0.0, USCDI V4 and the HL7 Version 2 to FHIR 
 * Implementation Guide from the January 2024 STU Ballot.
 *
 * The converter package has everything a user needs to convert HL7 messages and components parsed 
 * using the HAPI V2 library into FHIR Resources.
 * 
 * The segment package contains parsers for HL7 V2 message segments.
 * The datatype package contains parsers for HL7 V2 datatypes.
 * The utils package contains a variety of utility classes useful during the conversion process.
 * 
 *  @see <a href="https://repository.immregistries.org/files/resources/5bef530428317/hl7_2_5_1_release_1_5__2018_update.pdf">CDC HL7 Version 2.5.1 Implementation Guide for Immunization Messaging</a>
 *  @see <a href="https://hl7.org/fhir/us/core/STU7/">FHIR US Core Implementation Guide V7.0.0</a>
 *  @see <a href="https://www.healthit.gov/isp/united-states-core-data-interoperability-uscdi#uscdi-v4">USCDI V4</a>
 *  @see <a href="https://hl7.org/fhir/uv/v2mappings/2024Jan/">HL7 Version 2 to FHIR</a>
 *  @see <a href="https://github.com/IZGateway/v2tofhir">Github</a>
 *  
 *	@author Audacious Inquiry
 */
package gov.cdc.izgw.v2tofhir;
