# v2tofhir
This is the HL7 Version 2 to FHIR R4 Converter library for the IZ Gateway Transformation Service. The library
is intended to be a stand-alone set of HL7 Version 2 messaging conversion tools that can be used in multiple
contexts. It has been designed to support conversion of messages written according the the 
(CDC HL7 Version 2 Immunization Implementation Guide)[https://repository.immregistries.org/files/resources/5bef530428317/hl7_2_5_1_release_1_5__2018_update.pdf] and may be suitable for other uses. This library depends on both the HAPI V2 and the HAPI FHIR libraries.

## Principles of the V2 Converter
The conversion methods of the V2 converter are intended to be forgiving, and follow (Postel's law)[https://en.wikipedia.org/wiki/Robustness_principle], doing the best that they can to convert a V2 datatype or
string to the appropriate FHIR type.  

As a result, the conversion methods (e.g., DatatypeConverter.to*, DatatypeParser.convert, and  DatatypeParser.fromString) do not throw exceptions during the conversion process. 
Instead, these conversion methods return null if the datatype was null or empty (Type.isEmpty() returns true or an exception), or if the datatype could not be converted because values are invalid. This follows the convention that conversion should do as much as possible without causing a failure in applications using this library, as it is deemed more appropriate to return as much information as is possible rather than nothing at all.

## How To Guide

### Converting a Single HL7 V2 Message
The main use case for this library is to convert a sinngle HL7 V2 Message to a Bundle of FHIR Resources.

```java
	import gov.cdc.izgw.v2tofhir.MessageParser;
	import org.hl7.fhir.r4.model.Bundle;
	import ca.uhn.hl7v2.model.Segment;
	import ca.uhn.hl7v2.model.Structure;

	...
		
	void convertMessageExample(Message message) {
		MessageParser parser = new MessageParser();
		Bundle b parser.createBundle(message);
		// Do something with the bundle resources
	}	
```

### Converting HL7 V2 Message Structures (Segments or Groups) to FHIR Resources
An individual segment, structure or group or sequence of these objects can also be converted to a FHIR Bundle. 
In HAPI V2, a Segment is a specialization of a Structure and a Group is a named collection of segments or groups within a message.  

The MessageParser class can be used to parse an iterable sequence of these objects in the provided order. These conversions are performed as best as possible because some segments in the sequence (e.g., MSH) may be missing and fail to create essential resources (e.g., MessageHeader).

Individual segments can contribute information to existing Resources created during a conversion.  For example, the ORC, RXA, RXR and OBX segments of the ORDER group in a VXU_V04 message all contribute information to the Immunization resource.

```java
	void convertStructureExample(Structure structure) {
		MessageParser parser = new MessageParser();
		Bundle b parser.createBundle(Collections.singletonList(structure));
		// Do something with the bundle resources
	}	
```

### Converting an HL7 V2 Datatype to a FHIR Datatype
The HL7 Version 2 datatypes can be converted to FHIR Datatypes by the DatatypeConverter class or specific
DatatypeParser implementations.  To convert an HL7 V2 Datatype to a FHIR Datatype, call the 
to{FHIR Classname}(Type t) static method in DatatypeConverter.  See the example below:

```java
	convertTypesExample(DTM dtm, CWE cwe, XPN xpn) {
		/* dtm can be converted to FHIR Types derived from BaseDateTimeType */
		InstanceType instant = DatatypeConverter.toInstantType(dtm);
		/* The calls below produces the same results as the call above */
		instant = DatatypeConverter.convert(InstantType.class, dtm);
		instant = DatatypeConverter.convert("InstantType", dtm);
		
		DateTimeType dateTime = DatatypeConverter.toDateTimeType(dtm);
		DateType date = DatatypeConverter.toDateType(dtm);
		
		/* Codes can be converted to Coding, CodeableConcept, CodeType or Identifier types */
		CodeableConcept cc = DatatypeConverter.toCodeableConcept(cwe);
		Coding coding = DatatypeConverter.toCoding(cwe);
		CodeType code = DatatypeConverter.toCodeType(cwe);

		HumanName hn = DatatypeConverter.toHumanName(hn);
		/* HumanName also has a standalone parser */
		hn = new HumanNameParser().convert(hn);
	}
	
```

### Converting a FHIR Resource or Datatype to a string representation
The TextUtils class enables conversion of HL7 V2 types and FHIR Datatypes to a text string relying on the same supporting methods so that the representations are similar if not identical.

Call TextUtils.toString(ca.uhn.hl7v2.model.Type) to convert an HL7 Version 2 datatype to a string.  Call TextUtils.toString(org.hl7.fhir.r4.model.Type) to convert a FHIR datatype to
a string.  When the FHIR type was created by a DatatypeConverter method, the toString() results on the FHIR type should be identical to the toString() method on the source.

These methods are primarily used to support validation of FHIR conversions.

### Converting Text Strings to FHIR Datatypes
Stand-alone datatype parsers (classes implementing DatatypeParser such as AddressParser, ContactPointParser, and HumanNameParser) support conversion of text strings the specified
FHIR datatype. These parsers use very lightweight natural language processing to parse the
string into the relevant data type.

```java
	HumanName hn = new HumanNameParser().fromString("Mr. Keith W. Boone");
	Address addr = new AddressParser().fromString(
		"1600 Clifton Road, NE\n" + 
		"Atlanta, GA 30333"
	);
	ContactPointParser parser = new ContactPointParser();
	/* All of below return a Phone number */
	ContactPoint cp = parser.fromString("tel:800-555-1212");
	cp = parser.fromString("+1.800.555.1212");
	cp = parser.fromString("(800) 555-1212");
	
	/* All of below return an e-mail address */
	cp = parser.fromString("mailto:izgateway@cdc.gov");
	cp = parser.fromString("izgateway@cdc.gov");
	cp = parser.fromString("IZ Gateway <izgateway@cdc.gov>");
	
	/** Returns a network address */
	cp = parser.fromString("https://support.izgateway.org");
	
```

## Developer Guide
### Writing a new Datatype Parser
### Writing a new Structure Parser
### Accessing Existing Resources
