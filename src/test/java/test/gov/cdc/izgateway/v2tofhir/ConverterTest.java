package test.gov.cdc.izgateway.v2tofhir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StreamTokenizer;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeType;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.InstantType;
import org.hl7.fhir.r4.model.IntegerType;
import org.hl7.fhir.r4.model.PositiveIntType;
import org.hl7.fhir.r4.model.PrimitiveType;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.TimeType;
import org.hl7.fhir.r4.model.UnsignedIntType;
import org.hl7.fhir.r4.model.UriType;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Composite;
import ca.uhn.hl7v2.model.Group;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.model.Primitive;
import ca.uhn.hl7v2.model.Segment;
import ca.uhn.hl7v2.model.Structure;
import ca.uhn.hl7v2.model.Type;
import ca.uhn.hl7v2.model.Visitable;
import ca.uhn.hl7v2.parser.Parser;
import ca.uhn.hl7v2.parser.PipeParser;
import gov.cdc.izgw.v2tofhir.converter.DatatypeConverter;
import gov.cdc.izgw.v2tofhir.converter.Mapping;
import gov.cdc.izgw.v2tofhir.converter.ParserUtils;
import gov.cdc.izgw.v2tofhir.converter.Systems;
import lombok.extern.slf4j.Slf4j;

@Slf4j
/**
 * This test checks a V2 conversion for FHIR Parsing and validates the result against the FHIR US Core
 * It requires a running CDC / Microsoft generated FHIR Converter at localhost:8080 
 */
class ConverterTest {
	private static final String base = "http://localhost:8080/fhir-converter/convert-to-fhir";
	private static final String messageTemplate = 
			  "{"
 			  + "\n \"input_data\": \"%s\","
			  + "\n \"input_type\": \"vxu\","
			  + "\n \"root_template\": \"VXU_V04\","
			  + "\n \"rr_data\": null"
			+ "\n}";
	private static final String[] TEST_MESSAGES = {
		"MSH|^~\\&|TEST|MOCK|IZGW|IZGW|20240618083101-0400||RSP^K11^RSP_K11|RESULT-01|P|2.5.1|||ER|AL|||||Z32^CDCPHINVS\rMSA|AA|QUERY-01\rQAK|QUERY-01|OK|Z34^Request Immunization History^CDCPHINVS\rQPD|Z34^Request Immunization History^CDCPHINVS|QUERY-01|234814^^^MYEHR^MR\rPID|1||234814^^^MYEHR^MR||FagenAIRA^TheodoricAIRA^ElbertAIRA^^^^L|FagenAIRA^TheodoricAIRA^^^^^M|19631226|M||ASIAN|1517 Huth Ave^^Wyndmere^ND^58081^^L||{{Telephone Number}}|||||||||not HISPANIC\rNK1|1|FagenAIRA^TheodoricAIRA^^^^^L|MTH^Mom^HL70063|1517 Huth Ave^^Wyndmere^ND^58081^^L\rORC|RE||65930^DCS||||||20120113|^FagenAIRA^TheodoricAIRA||^FagenAIRA^TheodoricAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\rRXA|0|1|20210516||208^208^CVX|0.3|ml||01^historical^NIP001|||||||||||CP|A\rRXR|C28161^IM^NCIT^IM^^HL70162|RT^Right Thigh^HL70163\rOBX|1|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\rOBX|2|DT|29769-7^VIS presented^LN|2|20120113||||||F\rOBX|3|CE|69764-9 ^Document type^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F \rORC|RE||65949^DCS||||||20120113|^FagenAIRA^TheodoricAIRA||^FagenAIRA^TheodoricAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\rOBX|4|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\rOBX|5|DT|29769-7^VIS presented^LN|2|20120113||||||F\rOBX|6|CE|69764-9^Eligibility Status^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F\r\r\n",
		"MSH|^~\\&|TEST|MOCK|IZGW|IZGW|20240618083102-0400||RSP^K11^RSP_K11|RESULT-02|P|2.5.1|||ER|AL|||||Z32^CDCPHINVS\rMSA|AA|QUERY-02\rQAK|QUERY-02|OK|Z34^Request Immunization History^CDCPHINVS\rQPD|Z34^Request Immunization History^CDCPHINVS|QUERY-02|234815^^^MYEHR^MR\rPID|1||234815^^^MYEHR^MR||FagenAIRA^ChristosAIRA^DemarionAIRA^^^^L|FagenAIRA^ChristosAIRA^^^^^M|20040315|M||ASIAN|1606 Ealfsen St^^Bismarck^ND^58501^^L||{{Telephone Number}}|||||||||not HISPANIC\rNK1|1|FagenAIRA^ChristosAIRA^^^^^L|MTH^Mom^HL70063|1606 Ealfsen St^^Bismarck^ND^58501^^L\rORC|RE||65930^DCS||||||20120113|^FagenAIRA^ChristosAIRA||^FagenAIRA^ChristosAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\rRXA|0|1|20040315||08^08^CVX|.05|ml||01^historical^NIP001|||||||||||CP|A\rRXR|C28161^IM^NCIT^IM^^HL70162|RT^Right Thigh^HL70163\rOBX|1|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\rOBX|2|DT|29769-7^VIS presented^LN|2|20120113||||||F\rOBX|3|CE|69764-9 ^Document type^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F \rORC|RE||65949^DCS||||||20120113|^FagenAIRA^ChristosAIRA||^FagenAIRA^ChristosAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\rOBX|4|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\rOBX|5|DT|29769-7^VIS presented^LN|2|20120113||||||F\rOBX|6|CE|69764-9^Eligibility Status^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F\rORC|RE||65930^DCS||||||20120113|^FagenAIRA^ChristosAIRA||^FagenAIRA^ChristosAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\rRXA|0|1|20040515||08^08^CVX|.05|ml||01^historical^NIP001|||||||||||CP|A\rRXR|C28161^IM^NCIT^IM^^HL70162|RT^Right Thigh^HL70163\rOBX|1|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\rOBX|2|DT|29769-7^VIS presented^LN|2|20120113||||||F\rOBX|3|CE|69764-9 ^Document type^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F \rORC|RE||65949^DCS||||||20120113|^FagenAIRA^ChristosAIRA||^FagenAIRA^ChristosAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\rOBX|4|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\rOBX|5|DT|29769-7^VIS presented^LN|2|20120113||||||F\rOBX|6|CE|69764-9^Eligibility Status^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F\rORC|RE||65930^DCS||||||20120113|^FagenAIRA^ChristosAIRA||^FagenAIRA^ChristosAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\rRXA|0|1|20040515||116^116^CVX||ml||01^historical^NIP001|||||||||||CP|A\rRXR|C28161^IM^NCIT^IM^^HL70162|RT^Right Thigh^HL70163\rOBX|1|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\rOBX|2|DT|29769-7^VIS presented^LN|2|20120113||||||F\rOBX|3|CE|69764-9 ^Document type^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F \rORC|RE||65949^DCS||||||20120113|^FagenAIRA^ChristosAIRA||^FagenAIRA^ChristosAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\rOBX|4|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\rOBX|5|DT|29769-7^VIS presented^LN|2|20120113||||||F\rOBX|6|CE|69764-9^Eligibility Status^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F\rORC|RE||65930^DCS||||||20120113|^FagenAIRA^ChristosAIRA||^FagenAIRA^ChristosAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\rRXA|0|1|20040515||106^106^CVX||ml||01^historical^NIP001|||||||||||CP|A\rRXR|C28161^IM^NCIT^IM^^HL70162|RT^Right Thigh^HL70163\rOBX|1|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\rOBX|2|DT|29769-7^VIS presented^LN|2|20120113||||||F\rOBX|3|CE|69764-9 ^Document type^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F \rORC|RE||65949^DCS||||||20120113|^FagenAIRA^ChristosAIRA||^FagenAIRA^ChristosAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\rOBX|4|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\rOBX|5|DT|29769-7^VIS presented^LN|2|20120113||||||F\rOBX|6|CE|69764-9^Eligibility Status^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F\rORC|RE||65930^DCS||||||20120113|^FagenAIRA^ChristosAIRA||^FagenAIRA^ChristosAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\rRXA|0|1|20040515||49^49^CVX||ml||01^historical^NIP001|||||||||||CP|A\rRXR|C28161^IM^NCIT^IM^^HL70162|RT^Right Thigh^HL70163\rOBX|1|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\rOBX|2|DT|29769-7^VIS presented^LN|2|20120113||||||F\rOBX|3|CE|69764-9 ^Document type^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F \rORC|RE||65949^DCS||||||20120113|^FagenAIRA^ChristosAIRA||^FagenAIRA^ChristosAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\rOBX|4|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\rOBX|5|DT|29769-7^VIS presented^LN|2|20120113||||||F\rOBX|6|CE|69764-9^Eligibility Status^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F\rORC|RE||65930^DCS||||||20120113|^FagenAIRA^ChristosAIRA||^FagenAIRA^ChristosAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\rRXA|0|1|20040515||10^10^CVX||ml||01^historical^NIP001|||||||||||CP|A\rRXR|C28161^IM^NCIT^IM^^HL70162|RT^Right Thigh^HL70163\rOBX|1|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\rOBX|2|DT|29769-7^VIS presented^LN|2|20120113||||||F\rOBX|3|CE|69764-9 ^Document type^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F \rORC|RE||65949^DCS||||||20120113|^FagenAIRA^ChristosAIRA||^FagenAIRA^ChristosAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\rOBX|4|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\rOBX|5|DT|29769-7^VIS presented^LN|2|20120113||||||F\rOBX|6|CE|69764-9^Eligibility Status^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F\rORC|RE||65930^DCS||||||20120113|^FagenAIRA^ChristosAIRA||^FagenAIRA^ChristosAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\rRXA|0|1|20040715||116^116^CVX||ml||01^historical^NIP001|||||||||||CP|A\rRXR|C28161^IM^NCIT^IM^^HL70162|RT^Right Thigh^HL70163\rOBX|1|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\rOBX|2|DT|29769-7^VIS presented^LN|2|20120113||||||F\rOBX|3|CE|69764-9 ^Document type^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F \rORC|RE||65949^DCS||||||20120113|^FagenAIRA^ChristosAIRA||^FagenAIRA^ChristosAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\rOBX|4|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\rOBX|5|DT|29769-7^VIS presented^LN|2|20120113||||||F\rOBX|6|CE|69764-9^Eligibility Status^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F\rORC|RE||65930^DCS||||||20120113|^FagenAIRA^ChristosAIRA||^FagenAIRA^ChristosAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\rRXA|0|1|20040715||106^106^CVX||ml||01^historical^NIP001|||||||||||CP|A\rRXR|C28161^IM^NCIT^IM^^HL70162|RT^Right Thigh^HL70163\rOBX|1|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\rOBX|2|DT|29769-7^VIS presented^LN|2|20120113||||||F\rOBX|3|CE|69764-9 ^Document type^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F \rORC|RE||65949^DCS||||||20120113|^FagenAIRA^ChristosAIRA||^FagenAIRA^ChristosAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\rOBX|4|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\rOBX|5|DT|29769-7^VIS presented^LN|2|20120113||||||F\rOBX|6|CE|69764-9^Eligibility Status^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F\rORC|RE||65930^DCS||||||20120113|^FagenAIRA^ChristosAIRA||^FagenAIRA^ChristosAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\rRXA|0|1|20040715||49^49^CVX||ml||01^historical^NIP001|||||||||||CP|A\rRXR|C28161^IM^NCIT^IM^^HL70162|RT^Right Thigh^HL70163\rOBX|1|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\rOBX|2|DT|29769-7^VIS presented^LN|2|20120113||||||F\rOBX|3|CE|69764-9 ^Document type^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F \rORC|RE||65949^DCS||||||20120113|^FagenAIRA^ChristosAIRA||^FagenAIRA^ChristosAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\rOBX|4|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\rOBX|5|DT|29769-7^VIS presented^LN|2|20120113||||||F\rOBX|6|CE|69764-9^Eligibility Status^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F\rORC|RE||65930^DCS||||||20120113|^FagenAIRA^ChristosAIRA||^FagenAIRA^ChristosAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\rRXA|0|1|20040715||10^10^CVX||ml||01^historical^NIP001|||||||||||CP|A\rRXR|C28161^IM^NCIT^IM^^HL70162|RT^Right Thigh^HL70163\rOBX|1|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\rOBX|2|DT|29769-7^VIS presented^LN|2|20120113||||||F\rOBX|3|CE|69764-9 ^Document type^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F \rORC|RE||65949^DCS||||||20120113|^FagenAIRA^ChristosAIRA||^FagenAIRA^ChristosAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\rOBX|4|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\rOBX|5|DT|29769-7^VIS presented^LN|2|20120113||||||F\rOBX|6|CE|69764-9^Eligibility Status^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F\rORC|RE||65930^DCS||||||20120113|^FagenAIRA^ChristosAIRA||^FagenAIRA^ChristosAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\rRXA|0|1|20040915||08^08^CVX|.05|ml||01^historical^NIP001|||||||||||CP|A\rRXR|C28161^IM^NCIT^IM^^HL70162|RT^Right Thigh^HL70163\rOBX|1|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\rOBX|2|DT|29769-7^VIS presented^LN|2|20120113||||||F\rOBX|3|CE|69764-9 ^Document type^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F \rORC|RE||65949^DCS||||||20120113|^FagenAIRA^ChristosAIRA||^FagenAIRA^ChristosAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\rOBX|4|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\rOBX|5|DT|29769-7^VIS presented^LN|2|20120113||||||F\rOBX|6|CE|69764-9^Eligibility Status^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F\rORC|RE||65930^DCS||||||20120113|^FagenAIRA^ChristosAIRA||^FagenAIRA^ChristosAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\rRXA|0|1|20040915||116^116^CVX||ml||01^historical^NIP001|||||||||||CP|A\rRXR|C28161^IM^NCIT^IM^^HL70162|RT^Right Thigh^HL70163\rOBX|1|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\rOBX|2|DT|29769-7^VIS presented^LN|2|20120113||||||F\rOBX|3|CE|69764-9 ^Document type^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F \rORC|RE||65949^DCS||||||20120113|^FagenAIRA^ChristosAIRA||^FagenAIRA^ChristosAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\rOBX|4|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\rOBX|5|DT|29769-7^VIS presented^LN|2|20120113||||||F\rOBX|6|CE|69764-9^Eligibility Status^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F\rORC|RE||65930^DCS||||||20120113|^FagenAIRA^ChristosAIRA||^FagenAIRA^ChristosAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\rRXA|0|1|20040915||106^106^CVX||ml||01^historical^NIP001|||||||||||CP|A\rRXR|C28161^IM^NCIT^IM^^HL70162|RT^Right Thigh^HL70163\rOBX|1|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\rOBX|2|DT|29769-7^VIS presented^LN|2|20120113||||||F\rOBX|3|CE|69764-9 ^Document type^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F \rORC|RE||65949^DCS||||||20120113|^FagenAIRA^ChristosAIRA||^FagenAIRA^ChristosAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\rOBX|4|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\rOBX|5|DT|29769-7^VIS presented^LN|2|20120113||||||F\rOBX|6|CE|69764-9^Eligibility Status^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F\rORC|RE||65930^DCS||||||20120113|^FagenAIRA^ChristosAIRA||^FagenAIRA^ChristosAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\rRXA|0|1|20040915||10^10^CVX||ml||01^historical^NIP001|||||||||||CP|A\rRXR|C28161^IM^NCIT^IM^^HL70162|RT^Right Thigh^HL70163\rOBX|1|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\rOBX|2|DT|29769-7^VIS presented^LN|2|20120113||||||F\rOBX|3|CE|69764-9 ^Document type^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F \rORC|RE||65949^DCS||||||20120113|^FagenAIRA^ChristosAIRA||^FagenAIRA^ChristosAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\rOBX|4|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\rOBX|5|DT|29769-7^VIS presented^LN|2|20120113||||||F\rOBX|6|CE|69764-9^Eligibility Status^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F\rORC|RE||65930^DCS||||||20120113|^FagenAIRA^ChristosAIRA||^FagenAIRA^ChristosAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\rRXA|0|1|20050315||03^03^CVX|0.5|ml||01^historical^NIP001|||||||||||CP|A\rRXR|C28161^IM^NCIT^IM^^HL70162|RT^Right Thigh^HL70163\rOBX|1|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\rOBX|2|DT|29769-7^VIS presented^LN|2|20120113||||||F\rOBX|3|CE|69764-9 ^Document type^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F \rORC|RE||65949^DCS||||||20120113|^FagenAIRA^ChristosAIRA||^FagenAIRA^ChristosAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\rOBX|4|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\rOBX|5|DT|29769-7^VIS presented^LN|2|20120113||||||F\rOBX|6|CE|69764-9^Eligibility Status^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F\rORC|RE||65930^DCS||||||20120113|^FagenAIRA^ChristosAIRA||^FagenAIRA^ChristosAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\rRXA|0|1|20050315||21^21^CVX||ml||01^historical^NIP001|||||||||||CP|A\rRXR|C28161^IM^NCIT^IM^^HL70162|RT^Right Thigh^HL70163\rOBX|1|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\rOBX|2|DT|29769-7^VIS presented^LN|2|20120113||||||F\rOBX|3|CE|69764-9 ^Document type^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F \rORC|RE||65949^DCS||||||20120113|^FagenAIRA^ChristosAIRA||^FagenAIRA^ChristosAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\rOBX|4|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\rOBX|5|DT|29769-7^VIS presented^LN|2|20120113||||||F\rOBX|6|CE|69764-9^Eligibility Status^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F\rORC|RE||65930^DCS||||||20120113|^FagenAIRA^ChristosAIRA||^FagenAIRA^ChristosAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\rRXA|0|1|20050315||83^83^CVX||ml||01^historical^NIP001|||||||||||CP|A\rRXR|C28161^IM^NCIT^IM^^HL70162|RT^Right Thigh^HL70163\rOBX|1|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\rOBX|2|DT|29769-7^VIS presented^LN|2|20120113||||||F\rOBX|3|CE|69764-9 ^Document type^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F \rORC|RE||65949^DCS||||||20120113|^FagenAIRA^ChristosAIRA||^FagenAIRA^ChristosAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\rOBX|4|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\rOBX|5|DT|29769-7^VIS presented^LN|2|20120113||||||F\rOBX|6|CE|69764-9^Eligibility Status^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F\rORC|RE||65930^DCS||||||20120113|^FagenAIRA^ChristosAIRA||^FagenAIRA^ChristosAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\rRXA|0|1|20050615||106^106^CVX||ml||01^historical^NIP001|||||||||||CP|A\rRXR|C28161^IM^NCIT^IM^^HL70162|RT^Right Thigh^HL70163\rOBX|1|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\rOBX|2|DT|29769-7^VIS presented^LN|2|20120113||||||F\rOBX|3|CE|69764-9 ^Document type^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F \rORC|RE||65949^DCS||||||20120113|^FagenAIRA^ChristosAIRA||^FagenAIRA^ChristosAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\rOBX|4|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\rOBX|5|DT|29769-7^VIS presented^LN|2|20120113||||||F\rOBX|6|CE|69764-9^Eligibility Status^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F\rORC|RE||65930^DCS||||||20120113|^FagenAIRA^ChristosAIRA||^FagenAIRA^ChristosAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\rRXA|0|1|20050615||49^49^CVX||ml||01^historical^NIP001|||||||||||CP|A\rRXR|C28161^IM^NCIT^IM^^HL70162|RT^Right Thigh^HL70163\rOBX|1|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\rOBX|2|DT|29769-7^VIS presented^LN|2|20120113||||||F\rOBX|3|CE|69764-9 ^Document type^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F \rORC|RE||65949^DCS||||||20120113|^FagenAIRA^ChristosAIRA||^FagenAIRA^ChristosAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\rOBX|4|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\rOBX|5|DT|29769-7^VIS presented^LN|2|20120113||||||F\rOBX|6|CE|69764-9^Eligibility Status^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F\r\r\n",
		"MSH|^~\\&|TEST|MOCK|IZGW|IZGW|20240618083103-0400||RSP^K11^RSP_K11|RESULT-03|P|2.5.1|||ER|AL|||||Z32^CDCPHINVS\rMSA|AA|QUERY-03\rQAK|QUERY-03|OK|Z34^Request Immunization History^CDCPHINVS\rQPD|Z34^Request Immunization History^CDCPHINVS|QUERY-03|234816^^^MYEHR^MR\rPID|1||234816^^^MYEHR^MR||FagenAIRA^SophoclesAIRA^JerrieAIRA^^^^L|FagenAIRA^SophoclesAIRA^^^^^M|19760128|M||ASIAN|1760 Ve Marne Ln^^Fargo^ND^58104^^L||{{Telephone Number}}|||||||||not HISPANIC\rNK1|1|FagenAIRA^SophoclesAIRA^^^^^L|MTH^Mom^HL70063|1760 Ve Marne Ln^^Fargo^ND^58104^^L\rORC|RE||65930^DCS||||||20120113|^FagenAIRA^SophoclesAIRA||^FagenAIRA^SophoclesAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\rRXA|0|1|20210429||208^208^CVX|0.3|ml||01^historical^NIP001|||||||||||CP|A\rRXR|C28161^IM^NCIT^IM^^HL70162|RT^Right Thigh^HL70163\rOBX|1|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\rOBX|2|DT|29769-7^VIS presented^LN|2|20120113||||||F\rOBX|3|CE|69764-9 ^Document type^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F \rORC|RE||65949^DCS||||||20120113|^FagenAIRA^SophoclesAIRA||^FagenAIRA^SophoclesAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\rOBX|4|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\rOBX|5|DT|29769-7^VIS presented^LN|2|20120113||||||F\rOBX|6|CE|69764-9^Eligibility Status^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F\rORC|RE||65930^DCS||||||20120113|^FagenAIRA^SophoclesAIRA||^FagenAIRA^SophoclesAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\rRXA|0|1|20210531||208^208^CVX|0.3|ml||01^historical^NIP001|||||||||||CP|A\rRXR|C28161^IM^NCIT^IM^^HL70162|RT^Right Thigh^HL70163\rOBX|1|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\rOBX|2|DT|29769-7^VIS presented^LN|2|20120113||||||F\rOBX|3|CE|69764-9 ^Document type^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F \rORC|RE||65949^DCS||||||20120113|^FagenAIRA^SophoclesAIRA||^FagenAIRA^SophoclesAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\rOBX|4|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\rOBX|5|DT|29769-7^VIS presented^LN|2|20120113||||||F\rOBX|6|CE|69764-9^Eligibility Status^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F\r\r\n",
		"MSH|^~\\&|TEST|MOCK|IZGW|IZGW|20240618083104-0400||RSP^K11^RSP_K11|RESULT-04|P|2.5.1|||ER|AL|||||Z32^CDCPHINVS\rMSA|AA|QUERY-04\rQAK|QUERY-04|OK|Z34^Request Immunization History^CDCPHINVS\rQPD|Z34^Request Immunization History^CDCPHINVS|QUERY-04|234817^^^MYEHR^MR\rPID|1||234817^^^MYEHR^MR||NuckollsAIRA^NurhanAIRA^InceAIRA^^^^L|NuckollsAIRA^NurhanAIRA^^^^^M|19521007|M||ASIAN|1154 Laadhoeke St^^Grand Forks^ND^58201^^L||{{Telephone Number}}|||||||||not HISPANIC\rNK1|1|NuckollsAIRA^NurhanAIRA^^^^^L|MTH^Mom^HL70063|1154 Laadhoeke St^^Grand Forks^ND^58201^^L\rORC|RE||65930^DCS||||||20120113|^NuckollsAIRA^NurhanAIRA||^NuckollsAIRA^NurhanAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\rRXA|0|1|20210710||208^208^CVX|0.3|ml||01^historical^NIP001|||||||||||CP|A\rRXR|C28161^IM^NCIT^IM^^HL70162|RT^Right Thigh^HL70163\rOBX|1|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\rOBX|2|DT|29769-7^VIS presented^LN|2|20120113||||||F\rOBX|3|CE|69764-9 ^Document type^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F \rORC|RE||65949^DCS||||||20120113|^NuckollsAIRA^NurhanAIRA||^NuckollsAIRA^NurhanAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\rOBX|4|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\rOBX|5|DT|29769-7^VIS presented^LN|2|20120113||||||F\rOBX|6|CE|69764-9^Eligibility Status^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F\rORC|RE||65930^DCS||||||20120113|^NuckollsAIRA^NurhanAIRA||^NuckollsAIRA^NurhanAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\rRXA|0|1|20210730||208^208^CVX|0.3|ml||01^historical^NIP001|||||||||||CP|A\rRXR|C28161^IM^NCIT^IM^^HL70162|RT^Right Thigh^HL70163\rOBX|1|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\rOBX|2|DT|29769-7^VIS presented^LN|2|20120113||||||F\rOBX|3|CE|69764-9 ^Document type^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F \rORC|RE||65949^DCS||||||20120113|^NuckollsAIRA^NurhanAIRA||^NuckollsAIRA^NurhanAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\rOBX|4|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\rOBX|5|DT|29769-7^VIS presented^LN|2|20120113||||||F\rOBX|6|CE|69764-9^Eligibility Status^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F\r\r\n",
		"MSH|^~\\&|TEST|MOCK|IZGW|IZGW|20240618083105-0400||RSP^K11^RSP_K11|RESULT-05|P|2.5.1|||ER|AL|||||Z32^CDCPHINVS\rMSA|AA|QUERY-05\rQAK|QUERY-05|OK|Z34^Request Immunization History^CDCPHINVS\rQPD|Z34^Request Immunization History^CDCPHINVS|QUERY-05|234818^^^MYEHR^MR\rPID|1||234818^^^MYEHR^MR||LaurelAIRA^ZechariahAIRA^KenverAIRA^^^^L|LaurelAIRA^ZechariahAIRA^^^^^M|19970705|M||ASIAN|1964 Aapelle Cir^^Fargo^ND^58104^^L||{{Telephone Number}}|||||||||not HISPANIC\rNK1|1|LaurelAIRA^ZechariahAIRA^^^^^L|MTH^Mom^HL70063|1964 Aapelle Cir^^Fargo^ND^58104^^L\rORC|RE||65930^DCS||||||20120113|^LaurelAIRA^ZechariahAIRA||^LaurelAIRA^ZechariahAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\rRXA|0|1|20210510||208^208^CVX|0.3|ml||01^historical^NIP001|||||||||||CP|A\rRXR|C28161^IM^NCIT^IM^^HL70162|RT^Right Thigh^HL70163\rOBX|1|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\rOBX|2|DT|29769-7^VIS presented^LN|2|20120113||||||F\rOBX|3|CE|69764-9 ^Document type^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F \rORC|RE||65949^DCS||||||20120113|^LaurelAIRA^ZechariahAIRA||^LaurelAIRA^ZechariahAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\rOBX|4|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\rOBX|5|DT|29769-7^VIS presented^LN|2|20120113||||||F\rOBX|6|CE|69764-9^Eligibility Status^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F\rORC|RE||65930^DCS||||||20120113|^LaurelAIRA^ZechariahAIRA||^LaurelAIRA^ZechariahAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\rRXA|0|1|20210611||208^208^CVX|0.3|ml||01^historical^NIP001|||||||||||CP|A\rRXR|C28161^IM^NCIT^IM^^HL70162|RT^Right Thigh^HL70163\rOBX|1|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\rOBX|2|DT|29769-7^VIS presented^LN|2|20120113||||||F\rOBX|3|CE|69764-9 ^Document type^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F \rORC|RE||65949^DCS||||||20120113|^LaurelAIRA^ZechariahAIRA||^LaurelAIRA^ZechariahAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\rOBX|4|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\rOBX|5|DT|29769-7^VIS presented^LN|2|20120113||||||F\rOBX|6|CE|69764-9^Eligibility Status^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F\r\r\n",
		"MSH|^~\\&|TEST|MOCK|IZGW|IZGW|20240618083106-0400||RSP^K11^RSP_K11|RESULT-06|P|2.5.1|||ER|AL|||||Z32^CDCPHINVS\rMSA|AA|QUERY-06\rQAK|QUERY-06|OK|Z34^Request Immunization History^CDCPHINVS\rQPD|Z34^Request Immunization History^CDCPHINVS|QUERY-06|234819^^^MYEHR^MR\rPID|1||234819^^^MYEHR^MR||NavarroAIRA^ZaylinAIRA^DonteAIRA^^^^L|NavarroAIRA^ZaylinAIRA^^^^^M|20100628|F||ASIAN|1719 Zuren Pl^^Kindred^ND^58051^^L||{{Telephone Number}}|||||||||not HISPANIC\rNK1|1|NavarroAIRA^ZaylinAIRA^^^^^L|MTH^Mom^HL70063|1719 Zuren Pl^^Kindred^ND^58051^^L\rORC|RE||65930^DCS||||||20120113|^NavarroAIRA^ZaylinAIRA||^NavarroAIRA^ZaylinAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\rRXA|0|1|20100628||08^08^CVX|.05|ml||01^historical^NIP001|||||||||||CP|A\rRXR|C28161^IM^NCIT^IM^^HL70162|RT^Right Thigh^HL70163\rOBX|1|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\rOBX|2|DT|29769-7^VIS presented^LN|2|20120113||||||F\rOBX|3|CE|69764-9 ^Document type^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F \rORC|RE||65949^DCS||||||20120113|^NavarroAIRA^ZaylinAIRA||^NavarroAIRA^ZaylinAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\rOBX|4|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\rOBX|5|DT|29769-7^VIS presented^LN|2|20120113||||||F\rOBX|6|CE|69764-9^Eligibility Status^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F\rORC|RE||65930^DCS||||||20120113|^NavarroAIRA^ZaylinAIRA||^NavarroAIRA^ZaylinAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\rRXA|0|1|20100828||08^08^CVX|.05|ml||01^historical^NIP001|||||||||||CP|A\rRXR|C28161^IM^NCIT^IM^^HL70162|RT^Right Thigh^HL70163\rOBX|1|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\rOBX|2|DT|29769-7^VIS presented^LN|2|20120113||||||F\rOBX|3|CE|69764-9 ^Document type^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F \rORC|RE||65949^DCS||||||20120113|^NavarroAIRA^ZaylinAIRA||^NavarroAIRA^ZaylinAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\rOBX|4|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\rOBX|5|DT|29769-7^VIS presented^LN|2|20120113||||||F\rOBX|6|CE|69764-9^Eligibility Status^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F\rORC|RE||65930^DCS||||||20120113|^NavarroAIRA^ZaylinAIRA||^NavarroAIRA^ZaylinAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\rRXA|0|1|20100828||116^116^CVX||ml||01^historical^NIP001|||||||||||CP|A\rRXR|C28161^IM^NCIT^IM^^HL70162|RT^Right Thigh^HL70163\rOBX|1|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\rOBX|2|DT|29769-7^VIS presented^LN|2|20120113||||||F\rOBX|3|CE|69764-9 ^Document type^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F \rORC|RE||65949^DCS||||||20120113|^NavarroAIRA^ZaylinAIRA||^NavarroAIRA^ZaylinAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\rOBX|4|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\rOBX|5|DT|29769-7^VIS presented^LN|2|20120113||||||F\rOBX|6|CE|69764-9^Eligibility Status^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F\rORC|RE||65930^DCS||||||20120113|^NavarroAIRA^ZaylinAIRA||^NavarroAIRA^ZaylinAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\rRXA|0|1|20100828||106^106^CVX||ml||01^historical^NIP001|||||||||||CP|A\rRXR|C28161^IM^NCIT^IM^^HL70162|RT^Right Thigh^HL70163\rOBX|1|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\rOBX|2|DT|29769-7^VIS presented^LN|2|20120113||||||F\rOBX|3|CE|69764-9 ^Document type^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F \rORC|RE||65949^DCS||||||20120113|^NavarroAIRA^ZaylinAIRA||^NavarroAIRA^ZaylinAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\rOBX|4|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\rOBX|5|DT|29769-7^VIS presented^LN|2|20120113||||||F\rOBX|6|CE|69764-9^Eligibility Status^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F\rORC|RE||65930^DCS||||||20120113|^NavarroAIRA^ZaylinAIRA||^NavarroAIRA^ZaylinAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\rRXA|0|1|20100828||49^49^CVX||ml||01^historical^NIP001|||||||||||CP|A\rRXR|C28161^IM^NCIT^IM^^HL70162|RT^Right Thigh^HL70163\rOBX|1|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\rOBX|2|DT|29769-7^VIS presented^LN|2|20120113||||||F\rOBX|3|CE|69764-9 ^Document type^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F \rORC|RE||65949^DCS||||||20120113|^NavarroAIRA^ZaylinAIRA||^NavarroAIRA^ZaylinAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\rOBX|4|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\rOBX|5|DT|29769-7^VIS presented^LN|2|20120113||||||F\rOBX|6|CE|69764-9^Eligibility Status^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F\rORC|RE||65930^DCS||||||20120113|^NavarroAIRA^ZaylinAIRA||^NavarroAIRA^ZaylinAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\rRXA|0|1|20100828||10^10^CVX||ml||01^historical^NIP001|||||||||||CP|A\rRXR|C28161^IM^NCIT^IM^^HL70162|RT^Right Thigh^HL70163\rOBX|1|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\rOBX|2|DT|29769-7^VIS presented^LN|2|20120113||||||F\rOBX|3|CE|69764-9 ^Document type^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F \rORC|RE||65949^DCS||||||20120113|^NavarroAIRA^ZaylinAIRA||^NavarroAIRA^ZaylinAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\rOBX|4|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\rOBX|5|DT|29769-7^VIS presented^LN|2|20120113||||||F\rOBX|6|CE|69764-9^Eligibility Status^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F\rORC|RE||65930^DCS||||||20120113|^NavarroAIRA^ZaylinAIRA||^NavarroAIRA^ZaylinAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\rRXA|0|1|20101028||116^116^CVX||ml||01^historical^NIP001|||||||||||CP|A\rRXR|C28161^IM^NCIT^IM^^HL70162|RT^Right Thigh^HL70163\rOBX|1|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\rOBX|2|DT|29769-7^VIS presented^LN|2|20120113||||||F\rOBX|3|CE|69764-9 ^Document type^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F \rORC|RE||65949^DCS||||||20120113|^NavarroAIRA^ZaylinAIRA||^NavarroAIRA^ZaylinAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\rOBX|4|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\rOBX|5|DT|29769-7^VIS presented^LN|2|20120113||||||F\rOBX|6|CE|69764-9^Eligibility Status^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F\rORC|RE||65930^DCS||||||20120113|^NavarroAIRA^ZaylinAIRA||^NavarroAIRA^ZaylinAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\rRXA|0|1|20101028||106^106^CVX||ml||01^historical^NIP001|||||||||||CP|A\rRXR|C28161^IM^NCIT^IM^^HL70162|RT^Right Thigh^HL70163\rOBX|1|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\rOBX|2|DT|29769-7^VIS presented^LN|2|20120113||||||F\rOBX|3|CE|69764-9 ^Document type^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F \rORC|RE||65949^DCS||||||20120113|^NavarroAIRA^ZaylinAIRA||^NavarroAIRA^ZaylinAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\rOBX|4|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\rOBX|5|DT|29769-7^VIS presented^LN|2|20120113||||||F\rOBX|6|CE|69764-9^Eligibility Status^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F\rORC|RE||65930^DCS||||||20120113|^NavarroAIRA^ZaylinAIRA||^NavarroAIRA^ZaylinAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\rRXA|0|1|20101028||49^49^CVX||ml||01^historical^NIP001|||||||||||CP|A\rRXR|C28161^IM^NCIT^IM^^HL70162|RT^Right Thigh^HL70163\rOBX|1|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\rOBX|2|DT|29769-7^VIS presented^LN|2|20120113||||||F\rOBX|3|CE|69764-9 ^Document type^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F \rORC|RE||65949^DCS||||||20120113|^NavarroAIRA^ZaylinAIRA||^NavarroAIRA^ZaylinAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\rOBX|4|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\rOBX|5|DT|29769-7^VIS presented^LN|2|20120113||||||F\rOBX|6|CE|69764-9^Eligibility Status^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F\rORC|RE||65930^DCS||||||20120113|^NavarroAIRA^ZaylinAIRA||^NavarroAIRA^ZaylinAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\rRXA|0|1|20101028||10^10^CVX||ml||01^historical^NIP001|||||||||||CP|A\rRXR|C28161^IM^NCIT^IM^^HL70162|RT^Right Thigh^HL70163\rOBX|1|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\rOBX|2|DT|29769-7^VIS presented^LN|2|20120113||||||F\rOBX|3|CE|69764-9 ^Document type^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F \rORC|RE||65949^DCS||||||20120113|^NavarroAIRA^ZaylinAIRA||^NavarroAIRA^ZaylinAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\rOBX|4|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\rOBX|5|DT|29769-7^VIS presented^LN|2|20120113||||||F\rOBX|6|CE|69764-9^Eligibility Status^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F\rORC|RE||65930^DCS||||||20120113|^NavarroAIRA^ZaylinAIRA||^NavarroAIRA^ZaylinAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\rRXA|0|1|20101228||08^08^CVX|.05|ml||01^historical^NIP001|||||||||||CP|A\rRXR|C28161^IM^NCIT^IM^^HL70162|RT^Right Thigh^HL70163\rOBX|1|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\rOBX|2|DT|29769-7^VIS presented^LN|2|20120113||||||F\rOBX|3|CE|69764-9 ^Document type^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F \rORC|RE||65949^DCS||||||20120113|^NavarroAIRA^ZaylinAIRA||^NavarroAIRA^ZaylinAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\rOBX|4|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\rOBX|5|DT|29769-7^VIS presented^LN|2|20120113||||||F\rOBX|6|CE|69764-9^Eligibility Status^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F\rORC|RE||65930^DCS||||||20120113|^NavarroAIRA^ZaylinAIRA||^NavarroAIRA^ZaylinAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\rRXA|0|1|20101228||116^116^CVX||ml||01^historical^NIP001|||||||||||CP|A\rRXR|C28161^IM^NCIT^IM^^HL70162|RT^Right Thigh^HL70163\rOBX|1|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\rOBX|2|DT|29769-7^VIS presented^LN|2|20120113||||||F\rOBX|3|CE|69764-9 ^Document type^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F \rORC|RE||65949^DCS||||||20120113|^NavarroAIRA^ZaylinAIRA||^NavarroAIRA^ZaylinAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\rOBX|4|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\rOBX|5|DT|29769-7^VIS presented^LN|2|20120113||||||F\rOBX|6|CE|69764-9^Eligibility Status^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F\rORC|RE||65930^DCS||||||20120113|^NavarroAIRA^ZaylinAIRA||^NavarroAIRA^ZaylinAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\rRXA|0|1|20101228||106^106^CVX||ml||01^historical^NIP001|||||||||||CP|A\rRXR|C28161^IM^NCIT^IM^^HL70162|RT^Right Thigh^HL70163\rOBX|1|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\rOBX|2|DT|29769-7^VIS presented^LN|2|20120113||||||F\rOBX|3|CE|69764-9 ^Document type^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F \rORC|RE||65949^DCS||||||20120113|^NavarroAIRA^ZaylinAIRA||^NavarroAIRA^ZaylinAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\rOBX|4|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\rOBX|5|DT|29769-7^VIS presented^LN|2|20120113||||||F\rOBX|6|CE|69764-9^Eligibility Status^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F\rORC|RE||65930^DCS||||||20120113|^NavarroAIRA^ZaylinAIRA||^NavarroAIRA^ZaylinAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\rRXA|0|1|20101228||10^10^CVX||ml||01^historical^NIP001|||||||||||CP|A\rRXR|C28161^IM^NCIT^IM^^HL70162|RT^Right Thigh^HL70163\rOBX|1|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\rOBX|2|DT|29769-7^VIS presented^LN|2|20120113||||||F\rOBX|3|CE|69764-9 ^Document type^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F \rORC|RE||65949^DCS||||||20120113|^NavarroAIRA^ZaylinAIRA||^NavarroAIRA^ZaylinAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\rOBX|4|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\rOBX|5|DT|29769-7^VIS presented^LN|2|20120113||||||F\rOBX|6|CE|69764-9^Eligibility Status^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F\rORC|RE||65930^DCS||||||20120113|^NavarroAIRA^ZaylinAIRA||^NavarroAIRA^ZaylinAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\rRXA|0|1|20110628||03^03^CVX|0.5|ml||01^historical^NIP001|||||||||||CP|A\rRXR|C28161^IM^NCIT^IM^^HL70162|RT^Right Thigh^HL70163\rOBX|1|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\rOBX|2|DT|29769-7^VIS presented^LN|2|20120113||||||F\rOBX|3|CE|69764-9 ^Document type^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F \rORC|RE||65949^DCS||||||20120113|^NavarroAIRA^ZaylinAIRA||^NavarroAIRA^ZaylinAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\rOBX|4|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\rOBX|5|DT|29769-7^VIS presented^LN|2|20120113||||||F\rOBX|6|CE|69764-9^Eligibility Status^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F\rORC|RE||65930^DCS||||||20120113|^NavarroAIRA^ZaylinAIRA||^NavarroAIRA^ZaylinAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\rRXA|0|1|20110628||21^21^CVX||ml||01^historical^NIP001|||||||||||CP|A\rRXR|C28161^IM^NCIT^IM^^HL70162|RT^Right Thigh^HL70163\rOBX|1|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\rOBX|2|DT|29769-7^VIS presented^LN|2|20120113||||||F\rOBX|3|CE|69764-9 ^Document type^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F \rORC|RE||65949^DCS||||||20120113|^NavarroAIRA^ZaylinAIRA||^NavarroAIRA^ZaylinAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\rOBX|4|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\rOBX|5|DT|29769-7^VIS presented^LN|2|20120113||||||F\rOBX|6|CE|69764-9^Eligibility Status^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F\rORC|RE||65930^DCS||||||20120113|^NavarroAIRA^ZaylinAIRA||^NavarroAIRA^ZaylinAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\rRXA|0|1|20110628||83^83^CVX||ml||01^historical^NIP001|||||||||||CP|A\rRXR|C28161^IM^NCIT^IM^^HL70162|RT^Right Thigh^HL70163\rOBX|1|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\rOBX|2|DT|29769-7^VIS presented^LN|2|20120113||||||F\rOBX|3|CE|69764-9 ^Document type^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F \rORC|RE||65949^DCS||||||20120113|^NavarroAIRA^ZaylinAIRA||^NavarroAIRA^ZaylinAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\rOBX|4|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\rOBX|5|DT|29769-7^VIS presented^LN|2|20120113||||||F\rOBX|6|CE|69764-9^Eligibility Status^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F\rORC|RE||65930^DCS||||||20120113|^NavarroAIRA^ZaylinAIRA||^NavarroAIRA^ZaylinAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\rRXA|0|1|20110928||106^106^CVX||ml||01^historical^NIP001|||||||||||CP|A\rRXR|C28161^IM^NCIT^IM^^HL70162|RT^Right Thigh^HL70163\rOBX|1|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\rOBX|2|DT|29769-7^VIS presented^LN|2|20120113||||||F\rOBX|3|CE|69764-9 ^Document type^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F \rORC|RE||65949^DCS||||||20120113|^NavarroAIRA^ZaylinAIRA||^NavarroAIRA^ZaylinAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\rOBX|4|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\rOBX|5|DT|29769-7^VIS presented^LN|2|20120113||||||F\rOBX|6|CE|69764-9^Eligibility Status^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F\rORC|RE||65930^DCS||||||20120113|^NavarroAIRA^ZaylinAIRA||^NavarroAIRA^ZaylinAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\rRXA|0|1|20110928||49^49^CVX||ml||01^historical^NIP001|||||||||||CP|A\rRXR|C28161^IM^NCIT^IM^^HL70162|RT^Right Thigh^HL70163\rOBX|1|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\rOBX|2|DT|29769-7^VIS presented^LN|2|20120113||||||F\rOBX|3|CE|69764-9 ^Document type^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F \rORC|RE||65949^DCS||||||20120113|^NavarroAIRA^ZaylinAIRA||^NavarroAIRA^ZaylinAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\rOBX|4|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\rOBX|5|DT|29769-7^VIS presented^LN|2|20120113||||||F\rOBX|6|CE|69764-9^Eligibility Status^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F\r\r\n",
		"MSH|^~\\&|TEST|MOCK|IZGW|IZGW|20240618083107-0400||RSP^K11^RSP_K11|RESULT-07|P|2.5.1|||ER|AL|||||Z32^CDCPHINVS\rMSA|AA|QUERY-07\rQAK|QUERY-07|OK|Z34^Request Immunization History^CDCPHINVS\rQPD|Z34^Request Immunization History^CDCPHINVS|QUERY-07|234820^^^MYEHR^MR\rPID|1||234820^^^MYEHR^MR||CuyahogaAIRA^MarnyAIRA^MalkaAIRA^^^^L|CuyahogaAIRA^MarnyAIRA^^^^^M|19600507|F||ASIAN|1663 Persoon Ave^^Williston^ND^58801^^L||{{Telephone Number}}|||||||||not HISPANIC\rNK1|1|CuyahogaAIRA^MarnyAIRA^^^^^L|MTH^Mom^HL70063|1663 Persoon Ave^^Williston^ND^58801^^L\rORC|RE||65930^DCS||||||20120113|^CuyahogaAIRA^MarnyAIRA||^CuyahogaAIRA^MarnyAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\rRXA|0|1|20210623||208^208^CVX|0.3|ml||01^historical^NIP001|||||||||||CP|A\rRXR|C28161^IM^NCIT^IM^^HL70162|RT^Right Thigh^HL70163\rOBX|1|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\rOBX|2|DT|29769-7^VIS presented^LN|2|20120113||||||F\rOBX|3|CE|69764-9 ^Document type^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F \rORC|RE||65949^DCS||||||20120113|^CuyahogaAIRA^MarnyAIRA||^CuyahogaAIRA^MarnyAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\rOBX|4|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\rOBX|5|DT|29769-7^VIS presented^LN|2|20120113||||||F\rOBX|6|CE|69764-9^Eligibility Status^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F\rORC|RE||65930^DCS||||||20120113|^CuyahogaAIRA^MarnyAIRA||^CuyahogaAIRA^MarnyAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\rRXA|0|1|20210726||208^208^CVX|0.3|ml||01^historical^NIP001|||||||||||CP|A\rRXR|C28161^IM^NCIT^IM^^HL70162|RT^Right Thigh^HL70163\rOBX|1|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\rOBX|2|DT|29769-7^VIS presented^LN|2|20120113||||||F\rOBX|3|CE|69764-9 ^Document type^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F \rORC|RE||65949^DCS||||||20120113|^CuyahogaAIRA^MarnyAIRA||^CuyahogaAIRA^MarnyAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\rOBX|4|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\rOBX|5|DT|29769-7^VIS presented^LN|2|20120113||||||F\rOBX|6|CE|69764-9^Eligibility Status^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F\r\r\n",
		"MSH|^~\\&|TEST|MOCK|IZGW|IZGW|20240618083108-0400||RSP^K11^RSP_K11|RESULT-08|P|2.5.1|||ER|AL|||||Z32^CDCPHINVS\rMSA|AA|QUERY-08\rQAK|QUERY-08|OK|Z34^Request Immunization History^CDCPHINVS\rQPD|Z34^Request Immunization History^CDCPHINVS|QUERY-08|234821^^^MYEHR^MR\rPID|1||234821^^^MYEHR^MR||LaurelAIRA^NyssaAIRA^^^^^L|LaurelAIRA^NyssaAIRA^^^^^M|19670703|F||ASIAN|1586 Dittenseradeel Pl^^Thompson^ND^58278^^L||{{Telephone Number}}|||||||||not HISPANIC\rNK1|1|LaurelAIRA^NyssaAIRA^^^^^L|MTH^Mom^HL70063|1586 Dittenseradeel Pl^^Thompson^ND^58278^^L\rORC|RE||65930^DCS||||||20120113|^LaurelAIRA^NyssaAIRA||^LaurelAIRA^NyssaAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\rRXA|0|1|20210617||208^208^CVX|0.3|ml||01^historical^NIP001|||||||||||CP|A\rRXR|C28161^IM^NCIT^IM^^HL70162|RT^Right Thigh^HL70163\rOBX|1|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\rOBX|2|DT|29769-7^VIS presented^LN|2|20120113||||||F\rOBX|3|CE|69764-9 ^Document type^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F \rORC|RE||65949^DCS||||||20120113|^LaurelAIRA^NyssaAIRA||^LaurelAIRA^NyssaAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\rOBX|4|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\rOBX|5|DT|29769-7^VIS presented^LN|2|20120113||||||F\rOBX|6|CE|69764-9^Eligibility Status^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F\r\r\n",
		"MSH|^~\\&|TEST|MOCK|IZGW|IZGW|20240618083109-0400||RSP^K11^RSP_K11|RESULT-09|P|2.5.1|||ER|AL|||||Z32^CDCPHINVS\rMSA|AA|QUERY-09\rQAK|QUERY-09|OK|Z34^Request Immunization History^CDCPHINVS\rQPD|Z34^Request Immunization History^CDCPHINVS|QUERY-09|234822^^^MYEHR^MR\rPID|1||234822^^^MYEHR^MR||FagenAIRA^AitanAIRA^JessieAIRA^^^^L|FagenAIRA^AitanAIRA^^^^^M|19790705|M||ASIAN|1709 Eerneuzen Cir^^Williston^ND^58801^^L||{{Telephone Number}}|||||||||not HISPANIC\rNK1|1|FagenAIRA^AitanAIRA^^^^^L|MTH^Mom^HL70063|1709 Eerneuzen Cir^^Williston^ND^58801^^L\rORC|RE||65930^DCS||||||20120113|^FagenAIRA^AitanAIRA||^FagenAIRA^AitanAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\rRXA|0|1|20210705||208^208^CVX|0.3|ml||01^historical^NIP001|||||||||||CP|A\rRXR|C28161^IM^NCIT^IM^^HL70162|RT^Right Thigh^HL70163\rOBX|1|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\rOBX|2|DT|29769-7^VIS presented^LN|2|20120113||||||F\rOBX|3|CE|69764-9 ^Document type^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F \rORC|RE||65949^DCS||||||20120113|^FagenAIRA^AitanAIRA||^FagenAIRA^AitanAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\rOBX|4|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\rOBX|5|DT|29769-7^VIS presented^LN|2|20120113||||||F\rOBX|6|CE|69764-9^Eligibility Status^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F\rORC|RE||65930^DCS||||||20120113|^FagenAIRA^AitanAIRA||^FagenAIRA^AitanAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\rRXA|0|1|20210727||208^208^CVX|0.3|ml||01^historical^NIP001|||||||||||CP|A\rRXR|C28161^IM^NCIT^IM^^HL70162|RT^Right Thigh^HL70163\rOBX|1|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\rOBX|2|DT|29769-7^VIS presented^LN|2|20120113||||||F\rOBX|3|CE|69764-9 ^Document type^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F \rORC|RE||65949^DCS||||||20120113|^FagenAIRA^AitanAIRA||^FagenAIRA^AitanAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\rOBX|4|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\rOBX|5|DT|29769-7^VIS presented^LN|2|20120113||||||F\rOBX|6|CE|69764-9^Eligibility Status^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F\r\r\n",
		"MSH|^~\\&|TEST|MOCK|IZGW|IZGW|20240618083110-0400||RSP^K11^RSP_K11|RESULT-10|P|2.5.1|||ER|AL|||||Z32^CDCPHINVS\rMSA|AA|QUERY-10\rQAK|QUERY-10|OK|Z34^Request Immunization History^CDCPHINVS\rQPD|Z34^Request Immunization History^CDCPHINVS|QUERY-10|234823^^^MYEHR^MR\rPID|1||234823^^^MYEHR^MR||NavarroAIRA^SaulAIRA^LaneyAIRA^^^^L|NavarroAIRA^SaulAIRA^^^^^M|19630204|M||ASIAN|1539 Meek Pl^^Belcourt^ND^58316^^L||{{Telephone Number}}|||||||||not HISPANIC\rNK1|1|NavarroAIRA^SaulAIRA^^^^^L|MTH^Mom^HL70063|1539 Meek Pl^^Belcourt^ND^58316^^L\rORC|RE||65930^DCS||||||20120113|^NavarroAIRA^SaulAIRA||^NavarroAIRA^SaulAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\rRXA|0|1|20210706||208^208^CVX|0.3|ml||01^historical^NIP001|||||||||||CP|A\rRXR|C28161^IM^NCIT^IM^^HL70162|RT^Right Thigh^HL70163\rOBX|1|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\rOBX|2|DT|29769-7^VIS presented^LN|2|20120113||||||F\rOBX|3|CE|69764-9 ^Document type^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F \rORC|RE||65949^DCS||||||20120113|^NavarroAIRA^SaulAIRA||^NavarroAIRA^SaulAIRA^^^^^^^ ^^^^^^^^^^^MD|||||||||Dabig Clinic System\rOBX|4|CE|64994-7^Eligibility Status^LN|1|V02^Medicaid^HL70064||||||F||||||VXC40^vaccine level^CDCPHINVS\rOBX|5|DT|29769-7^VIS presented^LN|2|20120113||||||F\rOBX|6|CE|69764-9^Eligibility Status^LN|2|253088698300026411121116^Multivaccine VIS^cdcgs1vis||||||F\r\r\n"
	};
	private static final Parser v2Parser = new PipeParser();
	private static final FhirContext ctx = FhirContext.forR4();
	private static final IParser fhirParser = ctx.newJsonParser().setPrettyPrint(true);
	@ParameterizedTest
	@MethodSource("testMessages")
	@Disabled("Used for testing a local microsoft V2 converter")
	void testConversion(String hl7Message) throws IOException, ParseException {
		HttpURLConnection con = getUrlConnection();
		String value = messageTemplate.formatted(StringEscapeUtils.escapeJson(hl7Message)); 
		con.setRequestProperty("Content-Type", "application/json");
		OutputStream os = con.getOutputStream();
		log.info("Request: {}", value);
		
		os.write(value.getBytes(StandardCharsets.UTF_8));
		os.flush();
		os.close();
		int statusCode = con.getResponseCode();
		try {
			assertEquals(HttpURLConnection.HTTP_OK, statusCode);
		} catch (Error e) {
			String es = IOUtils.toString(con.getErrorStream(), StandardCharsets.UTF_8);
			log.error("Error: {}", es);
			throw e;
		}
		InputStream is = con.getInputStream();
		String fhirResult = testMicrosoftConverterResponse(is);
		log.info("HL7 V2: {}", hl7Message);
		log.info("FHIR: {}", fhirResult);
		IParser p = ctx.newJsonParser();
		Bundle b = p.parseResource(Bundle.class, fhirResult);
	}
	
	@ParameterizedTest
	@MethodSource("testComposites")
	void testCompositeConversionsDataCheck(Type t) {
		assertNotNull(t);
		assertTrue(t instanceof Composite);
	}

	@ParameterizedTest
	@MethodSource("testCompositesForCoding")
	void testCompositeConversionsForCodings(Type t) throws HL7Exception {
		CodeableConcept cc = DatatypeConverter.toCodeableConcept(t);
		Coding coding = DatatypeConverter.toCoding(t);
		if (cc != null && coding != null) {
			assertEquals(toString(cc.getCodingFirstRep()), toString(coding));
		}
		
		String first = (t instanceof Composite comp) ? comp.getComponents()[0].encode() : t.encode();
		if ("HD".equals(t.getClass().getSimpleName())) {
			// HD has System but not code
			assertFalse(coding.hasCode());
			assertTrue(coding.hasSystem());
		} else {
			assertEquals(first, coding.getCode());
		}
		if (hasDisplay(t) && hasComponent(t, 2)) {
			// Either both are blank or both are filled.
			assertEquals(StringUtils.isBlank(getComponent(t, 2)), StringUtils.isBlank(coding.getDisplay()));
			// If both are filled, check values.
			String[] a = { getComponent(t, 2), Mapping.getDisplay(coding) };
			List<String> l = Arrays.asList(a);
			Supplier<Boolean> test = null;
			
			if (StringUtils.isNotBlank(coding.getDisplay())) {
				// Display came from component 2, or it was properly mapped.
				test = () -> l.contains(coding.getDisplay());
			} else {
				// Display is empty and there are no good values to use.
				test = () -> StringUtils.isAllEmpty(a);
			}
			assertEquals(Boolean.TRUE, test.get(),
				"Display value " + coding.getDisplay() + " expected to be from " + l);
		}
		String encoded = ParserUtils.unescapeV2Chars(t.encode());
		if (coding.hasSystem()) {
			if (coding.getSystem().contains(":")) {
				// There is a proper URI, verify we got it correctly.
				Collection<String> names = Systems.getSystemNames(coding.getSystem());
				// We know about this URI
				assertNotNull(names);
				// At only one of the names for the system is in the message fields.
				// NOTE: We might need to make the "only one" part more lax as test 
				// data improves
				List<String> fields = Arrays.asList(encoded.split("\\^"));
				List<String> f = names.stream().filter(n -> fields.contains(n)).toList();
				assertEquals(1, f.size(), "Found " + f + " in " + encoded);
				
			} else {
				assertTrue(
					StringUtils.contains(encoded, coding.getSystem()), 
					t.encode() + " does not contain " + coding.getSystem()
				);
			}
		}
		System.out.println(encoded);
		System.out.println(toString(coding));
	}
	private boolean hasDisplay(Type t) {
		return Arrays.asList("CE", "CNE", "CWE").contains(t.getClass().getSimpleName());
	}
	private boolean hasComponent(Type t, int index) {
		if (t instanceof Composite comp) {
			Type types[] = comp.getComponents();
			return types.length >= index;
		}
		return false;
	}
	private String getComponent(Type t, int index) throws HL7Exception {
		if (t instanceof Composite comp) {
			Type types[] = comp.getComponents();
			return ParserUtils.unescapeV2Chars(types[index-1].encode());
		}
		return null;
	}
	
	@ParameterizedTest
	@MethodSource("testCompositesForIdentifier")
	void testCompositeConversionsForIdentifier(Type t) throws HL7Exception {
		Coding coding = DatatypeConverter.toCoding(t);
		Identifier id = DatatypeConverter.toIdentifier(t);
		if (id != null && coding != null) {
			assertEquals(coding.getCode(), id.getValue());
			assertEquals(coding.getSystem(), id.getSystem());
		}
		String first = (t instanceof Composite comp) ? comp.getComponents()[0].encode() : t.encode();
		if ("HD".equals(t.getClass().getSimpleName())) {
			assertEquals(first, id.getSystem());
		} else {
			assertEquals(first, id.getValue());
		}
	}
	
	@ParameterizedTest
	@MethodSource("testPrimitives")
	void testPrimitiveConversionsDataCheck(Type t) throws HL7Exception {
		assertNotNull(t);
		assertTrue(t instanceof Primitive);
	}
	
	@ParameterizedTest
	@MethodSource("testPrimitives")
	void testPrimitiveConversionsIntegerType(Type t) throws HL7Exception {
		assertTrue(t instanceof Primitive);
		compareStringValues(DatatypeConverter::toIntegerType, IntegerType::new, t);
	}
	@ParameterizedTest
	@MethodSource("testPrimitives")
	void testPrimitiveConversionsPositiveIntType(Type t) throws HL7Exception {
		compareStringValues(DatatypeConverter::toPositiveIntType, PositiveIntType::new, t);
	}
	@ParameterizedTest
	@MethodSource("testPrimitives")
	void testPrimitiveConversionsUnsignedIntType(Type t) throws HL7Exception {
		compareStringValues(DatatypeConverter::toUnsignedIntType, UnsignedIntType::new, t); 
	}
	@ParameterizedTest
	@MethodSource("testPrimitives")
	void testPrimitiveConversionsDateTimeType(Type t) throws HL7Exception {
		compareStringValues(DatatypeConverter::toDateTimeType, DateTimeType::new, t);
	}
	@ParameterizedTest
	@MethodSource("testPrimitives")
	void testPrimitiveConversionsDateType(Type t) throws HL7Exception {
		compareStringValues(DatatypeConverter::toDateType, DateType::new, t);
	}
	@ParameterizedTest
	@MethodSource("testPrimitives")
	void testPrimitiveConversionsInstantType(Type t) throws HL7Exception {
		compareStringValues(DatatypeConverter::toInstantType, InstantType::new, t); 
	}
	@ParameterizedTest
	@MethodSource("testPrimitives")
	@Disabled(value="HAPI's TimeType has a bug in which they don't parse the actual value, just store it")
	void testPrimitiveConversionsTimeType(Type t) throws HL7Exception {
		compareStringValues(DatatypeConverter::toTimeType, TimeType::new, t); 
	}
	@ParameterizedTest
	@MethodSource("testPrimitives")
	void testPrimitiveConversionsStringType(Type t) throws HL7Exception {
		compareStringValues(DatatypeConverter::toStringType, StringType::new, t);
	}
	@ParameterizedTest
	@MethodSource("testPrimitives")
	void testPrimitiveConversionsUriType(Type t) throws HL7Exception {
		compareStringValues(DatatypeConverter::toUriType, UriType::new, t);
	}
	@ParameterizedTest
	@MethodSource("testPrimitives")
	void testPrimitiveConversionsCodeType(Type t) throws HL7Exception {
		compareStringValues(DatatypeConverter::toCodeType, CodeType::new, t);
	}
	
	private void compareStringValues(
		Function<Type, PrimitiveType<?>> conversionFunction, 
		Function<String, PrimitiveType<?>> stringFunction,
		Type t
	) throws HL7Exception {
		Throwable fhirConversionFailure = null;
		PrimitiveType<?> fhirTypeFromString = null;
		if (t.encode() == null) {
			log.warn("t is null for ", t.getMessage().encode());
		}
		try {
			String value = ParserUtils.unescapeV2Chars(t.encode());
			Boolean cleanupNeeded = needsIsoCleanup(t);
			if (cleanupNeeded != null) {
				value = ParserUtils.cleanupIsoDateTime(value, cleanupNeeded);
			}
			fhirTypeFromString = stringFunction.apply(value);
			assertEquals(t.encode(), fhirTypeFromString.asStringValue());
		} catch (Exception ex) {
			fhirConversionFailure = ex;
		} catch (AssertionError err) {
			fhirConversionFailure = err;
		}
		PrimitiveType<?> fhirType = conversionFunction.apply(t);
		// In these cases, if we reproduce the encoded value, count it as a win.
		String encodedValue = null;
		boolean needsIntFix = false;
		if (fhirType != null) {
			switch (fhirType.fhirType()) {
			case "instant", "datetime", "date":
				encodedValue = fhirType.asStringValue();
				if (encodedValue != null) {
					encodedValue = encodedValue.replaceAll("[\\-T:]", "");
				}
				break;
			case "time":
				encodedValue = fhirType.asStringValue();
				if (encodedValue != null) {
					encodedValue = encodedValue.replace(":", "");
				}
				break;
			case "integer":
			case "long":
			case "positiveInt":
			case "unsignedInt":
				encodedValue = fhirType.asStringValue();
				needsIntFix = true;
				break;
			default:
				encodedValue = fhirType.asStringValue();
				break;
			}
		}
		
		if (fhirConversionFailure == null) {
			assertNotNull(fhirTypeFromString);
			// The values are the same.
			if (fhirType == null) {
				// TimeType has a bug in that it doesn't parse the actual value
				// in use.
				if (!"time".equals(fhirTypeFromString.fhirType())) {
					assertEqualStrings(null, fhirTypeFromString.asStringValue());
				}
			} else {
				assertEquals(isEmpty(fhirTypeFromString.getValue()), isEmpty(fhirType.getValue()),
					"'" + fhirTypeFromString.getValue() + "' <> '" + fhirType.getValue() + "'");
				if (!isEmpty(fhirTypeFromString.getValue())) {
					assertEquals(fhirTypeFromString.getValue(), fhirType.getValue());
				}
				// The types are the same.
				assertEquals(fhirTypeFromString.fhirType(), fhirType.fhirType());
			}
		} 
		
		if (fhirType != null) {
			if (!needsIntFix) {
				assertEqualStrings(ParserUtils.unescapeV2Chars(t.encode()), encodedValue);
			} else {
				// The string representations should be the same, except in special cases
				assertEqualStrings(intFix(t), encodedValue);
			}
		}  
	}
	
	/**
	 * To capture numbers from ST values, the v2 converter ignores anything after the numeric part
	 * of the message, and truncates decimals to integers where needed.
	 * 
	 * @param t	The field being extracted from.
	 * @return	The corrected value.
	 * @throws HL7Exception Unlikely, but if something went wrong in the parse.
	 */
	private String intFix(Type t) throws HL7Exception {
		String value = ParserUtils.unescapeV2Chars(t.encode()).trim();
		value = value.split("\\s+")[0];
		value = StringUtils.substringBefore(value, ".");
		if (value.length() == 0) {
			value = "0";
		}
		
		return Integer.valueOf(value).toString();
	}

	private void assertEqualStrings(String a, String b) {
		// If one is empty or blank, the other one must be.
		assertEquals(StringUtils.isBlank(a), StringUtils.isBlank(b), a + " <> '" + b + "'");
		// if we get here, both have values or both are empty
		if (StringUtils.isNotEmpty(a)) {
			// and now we can safely compare them.
			assertEquals(a, b);
		}
	}
	private boolean isEmpty(Object o) {
		return o == null || (o instanceof String s && StringUtils.isEmpty(s)); 
	}

	private Boolean needsIsoCleanup(Type t) {
		String typeName = t.getClass().getSimpleName();
		switch (typeName) {
		case "TM": return Boolean.FALSE;
		case "DIN", "DLD", "DR", "DT", "DTM": 
			return Boolean.TRUE;
		default:
			return null;  // No cleanup needed
		}
	}

	private String testMicrosoftConverterResponse(InputStream is) throws IOException, ParseException {
		String response = IOUtils.toString(is, StandardCharsets.UTF_8);
		StreamTokenizer t = new StreamTokenizer(new StringReader(response));
		t.lowerCaseMode(false);
		t.quoteChar('"');
		t.whitespaceChars(0, ' ');
		t.wordChars('!', 0xFF);
		t.eolIsSignificant(false);
		
		StringBuilder b = new StringBuilder();
		String fhirResult = null;
		while (t.nextToken() != StreamTokenizer.TT_EOF) {
			if (t.ttype == '"') { 
				switch (t.sval) {
				case "FhirResource":
					skipColon(t);
					fhirResult = getJsonContent(t, b);
					break;
				case "Status":
					skipColon(t);
					assertEquals('"', t.nextToken());
					assertEquals(t.sval, "OK");
					break;
				}
			}
		}
		return fhirResult;
	}

	private void skipColon(StreamTokenizer t) throws IOException, ParseException {
		if (t.nextToken() == StreamTokenizer.TT_WORD) {
			if (":".equals(t.sval))
				return;
		}
		throw new ParseException("A colon ':' was expected.", t.lineno());
	}
	private String getJsonContent(StreamTokenizer t, StringBuilder b) throws IOException, ParseException {
		int braceCount = 0;
		while (t.nextToken() != StreamTokenizer.TT_EOF) {
			switch (t.ttype) {
			case StreamTokenizer.TT_WORD:
				b.append(t.sval);
				if ("{".equals(t.sval)) {
					braceCount++;
				} else if ("}".equals(t.sval)) {
					if (--braceCount == 0) {
						return b.toString();
					}
				}
				break;
			case '"':
				b.append('"');
				b.append(StringEscapeUtils.escapeJava(t.sval));
				b.append('"');
				break;
			}
			// Insert whitespace between each token
			b.append(' ');
		}
		throw new ParseException("Unexpected EOF", t.lineno());
	}

	private HttpURLConnection getUrlConnection() throws IOException {
		URL url = new URL(base);
		HttpURLConnection con = (HttpURLConnection) url.openConnection();
		con.setRequestMethod("POST");
		con.setDoInput(true);
		con.setDoOutput(true);
		return con;
	}
	
	static List<String> testMessages() {
		return Arrays.asList(TEST_MESSAGES);
	}
	
	static Stream<Message> testV2Messages() {
		return testMessages().stream().map(ConverterTest::parse);
	}
	
	static Message parse(String message) {
		try {
			return v2Parser.parse(message);
		} catch (HL7Exception e) {
			e.printStackTrace();
			return null;
		}
	}
	
	static List<Segment> testSegments() {
		Set<Segment> testSegments = new TreeSet<Segment>(ConverterTest::compare);
		for (Message msg : testV2Messages().toList()) {
			iterateSegments(msg, testSegments);
		}
		return new ArrayList<>(testSegments);
	}
	
	private static void iterateSegments(Group g, Set<Segment> testSegments) {
		if (g == null) {
			return;
		}
		for (String name: g.getNames()) {
			try {
				for (Structure s: g.getAll(name)) {
					if (s instanceof Segment seg) {
						testSegments.add(seg);
					} else if (s instanceof Group group) {
						iterateSegments(group, testSegments);
					}
				}
			} catch (HL7Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	private static int compareObjects(Object a, Object b) {
		if (a == null && b == null) {
			return 0;
		}
		if (a == null) {
			return -1;
		}
		if (b == null) {
			return 1;
		}
		int comp = StringUtils.compare(a.getClass().getSimpleName(), b.getClass().getSimpleName());
		if (comp != 0) {
			return comp;
		}
		return 2;
	}
	private static int compare(Visitable a, Visitable b) {
		int comp = compareObjects(a, b);
		if (comp != 2) {
			return comp;
		}
		// Two Types are equal if they encode to the same string values.
		String a1;
		try {
			a1 = toString(a);
		} catch (HL7Exception e) {
			e.printStackTrace();
			return -1;
		}
		String b1;
		try {
			b1 = toString(b);
		} catch (HL7Exception e) {
			e.printStackTrace();
			return 1;
		}
		return StringUtils.compare(a1, b1);
	}
	private static String toString(Visitable a) throws HL7Exception {
		if (a instanceof Type t) {
			return t.encode();
		} else if (a instanceof Segment s) {
			return s.encode();
		}
		return null;
	}

	private static int compare(
			org.hl7.fhir.r4.model.Type a,
			org.hl7.fhir.r4.model.Type b) {
		int comp = compareObjects(a, b);
		if (comp != 2) {
			return comp;
		}
		return StringUtils.compare(toString(a), toString(b));
	}
	private static String toString(org.hl7.fhir.r4.model.Type a) {
		return fhirParser.encodeToString(a);
	}
	
	private static int compare(
			org.hl7.fhir.r4.model.Resource a,
			org.hl7.fhir.r4.model.Resource b) {
		int comp = compareObjects(a, b);
		if (comp != 2) {
			return comp;
		}
		return StringUtils.compare(toString(a), toString(b));
	}
	private static String toString(org.hl7.fhir.r4.model.Resource a) {
		return fhirParser.encodeResourceToString(a);
	}
	
	static Set<Type> testComposites() {
		return testComposites(Collections.emptySet());
	}
	static Set<Type> testCompositesForCoding() {
		List<String> codingTypes 
			= Arrays.asList("CE", "CWE", "CX", "EI", "HD");
		return testFields(t -> codingTypes.contains(t.getClass().getSimpleName()));
	}
	static Set<Type> testCompositesForIdentifier() {
		List<String> idTypes 
			= Arrays.asList("CE", "CWE", "CX", "EI", "HD");
		return testFields(t -> idTypes.contains(t.getClass().getSimpleName()));
	}
	
	static Set<Type> testComposites(Collection<String> type) {
		if (type.isEmpty()) {
			return testFields(t -> t instanceof Composite);
		} 
		return testFields(t -> type.contains(t.getClass().getSimpleName()));
	}
	
	static List<Type> testPrimitives() {
		Set<Type> testPrimitives = new TreeSet<Type>(ConverterTest::compare);
		testPrimitives.addAll(testFields(t -> t instanceof Primitive));
		for (Type type: testComposites()) {
			if (type instanceof Composite comp) {
				explodeComposite(comp, testPrimitives);
			}
		}
		return new ArrayList<>(testPrimitives);
	}
	
	static void explodeComposite(Composite comp, Set<Type> set) {
		for (Type part: comp.getComponents()) {
			if (part instanceof Primitive) {
				set.add(part);
			} else if (part instanceof Composite comp2){
				explodeComposite(comp2, set);
			}
		}
	}
	
	static Set<Type> testFields(Predicate<Type> test) {
		Set<Type> testFields = new TreeSet<Type>(ConverterTest::compare); 
		for (Segment segment: testSegments()) {
			for (int i = 1; i <= segment.numFields(); i++) {
				Type[] fields;
				try {
					fields = segment.getField(i);
				} catch (HL7Exception e) {
					e.printStackTrace();
					continue;
				}
				for (Type type: fields) {
					if (test.test(type)) {
						testFields.add(type);
					}
				}
			}
		}
		return testFields;
	}
	
	
}
