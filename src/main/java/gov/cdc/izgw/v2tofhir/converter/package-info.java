/**
 *	The converter package contains classes for parsing and converting HL7 V2 messages, segments and datatypes
 *  into FHIR Bundles, Resources and datatypes respectively.
 *
 *  The key classes are MessageParser, DatatypeConverter, and Context, and from these three classes you 
 * 	can access everything necessary to convert HL7 V2 messages and components into FHIR objects.
 *
 *	Converting a HAPI V2 Message into a FHIR Bundle is as simple as:
 *	<pre>
 *		Bundle b = new MessageParser().convert(message);
 *	</pre>
 *
 *	Converting a collection of HAPI V2 Segments or Groups is as simple as:
 *	<pre>
 *		Bundle b =  new MessageParser().convert(segments);
 *	</pre>
 *
 *	Converting a single HAPI V2 segment or group is as simple as:
 *	<pre>
 *		Bundle b =  new MessageParser().convert(Collections.singleton(segment));
 *	</pre>
 *
 *  @see <a href="https://github.com/IZGateway/v2tofhir">Github</a>
 */
 package gov.cdc.izgw.v2tofhir.converter;
 