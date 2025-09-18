package gov.cdc.izgw.v2tofhir.segment;

import java.util.Date;
import java.util.List;

import org.hl7.fhir.r4.model.Base64BinaryType;
import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.CodeType;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.DecimalType;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Period;
import org.hl7.fhir.r4.model.PrimitiveType;
import org.hl7.fhir.r4.model.Property;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.Type;
import org.hl7.fhir.r4.model.UriType;
import org.hl7.fhir.r4.model.UrlType;
import org.hl7.fhir.r4.model.MessageHeader.MessageSourceComponent;

import gov.cdc.izgw.v2tofhir.converter.MessageParser;
import gov.cdc.izgw.v2tofhir.utils.ParserUtils;
import lombok.extern.slf4j.Slf4j;

/**
 * This class lets us override or add methods to the parsers that are generated
 * by the code generator.
 *
 * @author Audacious Inquiry
 *
 */
@Slf4j
public abstract class BetterSegmentParser extends AbstractSegmentParser {

    protected BetterSegmentParser(MessageParser p, String s) {
        super(p, s);
    }

    /**
     * @param <T> The type of list element
     * @param l	The list
     * @return The last element of the list or null if empty or missing
     */
    public <T> T last(List<T> l) {
        return (l == null || l.isEmpty()) ? null : l.get(l.size() - 1);
    }

    /**
     * Map a code via a concept map
     * @param <T>	The code type
     * @param type	The code to map
     * @param mapName	The name of the map to use
     * @return	The mapped concept
     */
    public <T extends Type> T mapVia(T type, String mapName) {
        return type;
    }

    public Base64BinaryType toBase64BinaryType(String value) {
        return new Base64BinaryType(value);
    }

    public BooleanType toBooleanType(String value) {
        if (value.equalsIgnoreCase("true")) {
            return new BooleanType(true);
        }
        if (value.equalsIgnoreCase("false")) {
            return new BooleanType(false);
        }
        log.warn("Illegal fixed value for Boolean: {}", value);
        return null;
    }

    public CodeableConcept toCodeableConcept(String value) {
        return null;
    }

    public CodeType toCodeType(PrimitiveType<?> value) {
        return new CodeType(value.asStringValue());
    }

    // TODO: These cases need enumeration
    public CodeType toCodeType(String value) {
        return new CodeType(value);
    }

    public Coding toCoding(String value) {
        return null;
    }

    /**
     * Convert a period to a start date time
     * @param startDateTime	The period
     * @return	The start date time of the period
     */
    public Date toDateTimeType(Period startDateTime) {
        return startDateTime.getStart();
    }

    public DateTimeType toDateTimeType(String value) {
        // TODO parse the value and figure out what to do with it.
        return null;
    }

    public DecimalType toDecimalType(String value) {
        return null;
    }

    /**
     * Convert a resource to an identifier by returning its first identifier.
     * @param resource	The resource
     * @return	The identifier if the resource has one
     */
    public Identifier toIdentifier(Resource resource) {
        Property property = resource.getChildByName("identifier");
        if (property.hasValues()) {
            return (Identifier) property.getValues().get(0);
        }
        return null;
    }

    public Identifier toIdentifier(String value) {
        return null;
    }

    public Reference toLocation(String value) {
        // TODO: This is wrong
        return null;
    }

    public Reference toPractitioner(String value) {
        // TODO: This is wrong
        return null;
    }
    public Quantity toQuantity(Quantity quantity) {
        return quantity;
    }

    /**
     * Create a reference to target from source source target.path
     * @param target	The target resource
     * @param source	The source resource
     * @param path		The path to it in the source resource
     * @return	The new Reference
     */
    public Reference toReference(Resource target, Resource source, String path) {
        return ParserUtils.toReference(target, source, path);
    }

    /**
     * Convert a CodeableConcept to a string
     * @param cc The CodeableConcept
     * @return cc.text if present, otherwise the string representation of the first Coding
     */
    public String toStringType(CodeableConcept cc) {
        return cc.hasText() ? cc.getText() : toStringType(cc.getCodingFirstRep());
    }

    /**
     * Convert a Coding to a string
     * @param coding The Coding
     * @return cc.text if present, otherwise the string representation of the Coding
     */
    public String toStringType(Coding coding) {
        return coding.hasDisplay() ? coding.getDisplay() : coding.getCode();
    }

    public String toStringType(Identifier identifier) {
        return identifier.getValue();
    }
    public StringType toStringType(PrimitiveType<?> value) {
        return new StringType(value.asStringValue());
    }

    public StringType toStringType(String value) {
        return new StringType(value);
    }

    public UriType toUriType(PrimitiveType<?> value) {
        return new UriType(value.asStringValue());
    }

    public UriType toUriType(String value) {
        return new UriType(value);
    }

    public UrlType toUrlType(PrimitiveType<?> value) {
        return new UrlType(value.asStringValue());
    }

    public UrlType toUrlType(String value) {
        return new UrlType(value);
    }

    public UrlType toUrlType(MessageSourceComponent sendingApplication) {
        return sendingApplication.getEndpointElement();
    }
}
