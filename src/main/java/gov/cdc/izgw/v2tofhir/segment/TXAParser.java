package gov.cdc.izgw.v2tofhir.segment;

import java.util.ArrayList;
import java.util.List;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.DocumentReference;
import org.hl7.fhir.r4.model.Practitioner;
import org.hl7.fhir.r4.model.Provenance;
import org.hl7.fhir.r4.model.Attachment;
import org.hl7.fhir.r4.model.CodeType;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Enumeration;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.InstantType;
import org.hl7.fhir.r4.model.StringType;
import gov.cdc.izgw.v2tofhir.annotation.ComesFrom;
import gov.cdc.izgw.v2tofhir.annotation.Produces;
import gov.cdc.izgw.v2tofhir.converter.MessageParser;

import lombok.extern.slf4j.Slf4j;

/**
 * The TXA Parser creates DocumentReference, and Provenance resources from TXA segments.
 *
 * @see <a href="https://build.fhir.org/ig/HL7/v2-to-fhir/ConceptMap-segment-txa-to-documentreference.html">V2-to-FHIR: TXA to DocumentReference</a>
 * @see <a href="https://build.fhir.org/ig/HL7/v2-to-fhir/ConceptMap-segment-txa-to-provenance.html">V2-to-FHIR: TXA to Provenance</a>
 *
 * @author Audacious Inquiry
 */

@Produces(segment = "TXA", resource = DocumentReference.class, extra = { Practitioner.class, Provenance.class })
@Slf4j
public class TXAParser extends BetterSegmentParser {
    private DocumentReference documentReference;
    private Practitioner practitioner;
    private Provenance provenance;
    private static List<FieldHandler> fieldHandlers = new ArrayList<>();
    static { log.debug("{} loaded", TXAParser.class.getName()); }
    private DocumentReference.DocumentReferenceContentComponent content;
    private Attachment contentAttachment;
    private Extension extension1;
    private Extension statusExtension1;

    /**
     * Create an TXA Parser for the specified MessageParser
     *
     * @param p The message parser.
     */
    public TXAParser(MessageParser p) {
        super(p, "TXA");
        if (fieldHandlers.isEmpty()) {
            FieldHandler.initFieldHandlers(this, fieldHandlers);
        }
    }


    /** @return the existing DocumentReference resource */
    public DocumentReference getDocumentReference() {
        return documentReference;
    }

    /** @return a new or existing Practitioner resource */
    public Practitioner getPractitioner() {
        if (practitioner != null) return practitioner;
        practitioner = createResource(Practitioner.class);
        return practitioner;
    }

    /** @return a new or existing Provenance resource */
    public Provenance getProvenance() {
        if (provenance != null) return provenance;
        provenance = createResource(Provenance.class);
        return provenance;
    }

    /** handle mapping for getContent().add from TXA-3 */
    /* TXA-3 -> DocumentReference.content.attachment.contentType : code = "documentContentPresentation"*/
    private DocumentReference.DocumentReferenceContentComponent getContent() {
        if (content == null) {
            content = new DocumentReference.DocumentReferenceContentComponent();
            getDocumentReference().getContent().add(content);
        }
        return content;
    }


    /** handle mapping for setAttachment from TXA-3 */
    /* TXA-3 -> DocumentReference.content.attachment.contentType : code = "documentContentPresentation"*/
    private Attachment getContentAttachment() {
        if (contentAttachment == null) {
            contentAttachment = new Attachment();
            getContent().setAttachment(contentAttachment);
        }
        return contentAttachment;
    }


    /** handle mapping for getExtension().add from TXA-6 */
    /* TXA-6 -> DocumentReference.extension.url : StringType = "originationDateTime"*/
    private Extension getExtension1() {
        if (extension1 == null) {
            extension1 = new Extension();
            getDocumentReference().getExtension().add(extension1);
        }
        return extension1;
    }


    /** handle mapping for getStatusElement from TXA-19 */
    /* TXA-19 -> DocumentReference.status.extension.url : StringType = "documentAvailabilityStatus"*/
    private Enumeration<Enumerations.DocumentReferenceStatus> getStatus() {
        return getDocumentReference().getStatusElement();
    }


    /** handle mapping for getExtension().add from TXA-19 */
    /* TXA-19 -> DocumentReference.status.extension.url : StringType = "documentAvailabilityStatus"*/
    private Extension getStatusExtension1() {
        if (statusExtension1 == null) {
            statusExtension1 = new Extension();
            getStatus().getExtension().add(statusExtension1);
        }
        return statusExtension1;
    }


    @Override
    public IBaseResource setup() {
        documentReference = createResource(DocumentReference.class);
        return documentReference;
    }

    @Override
    protected List<FieldHandler> getFieldHandlers() {
        return fieldHandlers;
    }

    /**
     * Set the Document Type
     * @param documentType the Document Type
     */
    @ComesFrom(path = "DocumentReference.type", field = 2, comment = "Document Type", table = "0270")
    public void setDocumentType(CodeableConcept documentType) {
        /* TXA-2 -> DocumentReference.type : CodeableConcept = "documentType"*/
        getDocumentReference().setType(documentType);
    }

    /**
     * Set the Document Content Presentation
     * @param documentContentPresentation the Document Content Presentation
     */
    @ComesFrom(path = "DocumentReference.content.attachment.contentType", field = 3, comment = "Document Content Presentation", table = "0191")
    public void setDocumentContentPresentation(CodeType documentContentPresentation) {
        /* TXA-3 -> DocumentReference.content.attachment.contentType : code = "documentContentPresentation"*/
        getContentAttachment().setContentTypeElement(documentContentPresentation);
    }

    /**
     * Set the Origination Date/Time
     * @param originationDateTime the Origination Date/Time
     */
    @ComesFrom(path = "DocumentReference.date", field = 6, comment = "Origination Date/Time")
    public void setOriginationDateTime(InstantType originationDateTime) {
        /* TXA-6 -> DocumentReference.date : instant = "originationDateTime"*/
        getDocumentReference().setDateElement(originationDateTime);

        /* TXA-6 -> DocumentReference.extension.url : StringType = "originationDateTime"*/
        //getExtension1().setUrl(toStringType(originationDateTime));
        getExtension1().setUrl("http://hl7.org/fhir/6.0/StructureDefinition/extension-date");

        /* TXA-6 -> DocumentReference.extension.valueDate : Date = "originationDateTime"*/
        getExtension1().setValue(originationDateTime);
    }

    /**
     * Add the Originator Code/Name
     * @param originatorCodeName the Originator Code/Name
     */
    @ComesFrom(path = "DocumentReference.author{Practitioner}", field = 9, comment = "Originator Code/Name")
    public void addOriginatorCodeName(Practitioner originatorCodeName) {
        /* TXA-9 -> DocumentReference.author{Practitioner} : Practitioner = "originatorCodeName"*/
        getDocumentReference().getAuthor().add(toReference(originatorCodeName, getDocumentReference(), "Practitioner"));
    }

    /**
     * Set the Assigned Document Authenticator
     * @param assignedDocumentAuthenticator the Assigned Document Authenticator
     */
    @ComesFrom(path = "DocumentReference.authenticator{Practitioner}", field = 10, comment = "Assigned Document Authenticator")
    public void setAssignedDocumentAuthenticator(Practitioner assignedDocumentAuthenticator) {
        /* TXA-10 -> DocumentReference.authenticator{Practitioner} : Practitioner = "assignedDocumentAuthenticator"*/
        getDocumentReference().setAuthenticator(toReference(assignedDocumentAuthenticator, getDocumentReference(), "Practitioner"));
    }

    /**
     * Set the Unique Document Number
     * @param uniqueDocumentNumber the Unique Document Number
     */
    @ComesFrom(path = "DocumentReference.masterIdentifier", field = 12, comment = "Unique Document Number")
    public void setUniqueDocumentNumber(Identifier uniqueDocumentNumber) {
        /* TXA-12 -> DocumentReference.masterIdentifier : Identifier = "uniqueDocumentNumber"*/
        // v2tofhirconverter using current TXA ConceptMap used setMaster, instead of setMasterIdentifier
        getDocumentReference().setMasterIdentifier(uniqueDocumentNumber);
    }

    /**
     * Add the Unique Document File Name
     * @param uniqueDocumentFileName the Unique Document File Name
     */
    @ComesFrom(path = "DocumentReference.identifier", field = 16, comment = "Unique Document File Name")
    public void addUniqueDocumentFileName(Identifier uniqueDocumentFileName) {
        /* TXA-16 -> DocumentReference.identifier : Identifier = "uniqueDocumentFileName"*/
        getDocumentReference().getIdentifier().add(uniqueDocumentFileName);
    }

    /**
     * Set the Document Completion Status
     * @param documentCompletionStatus the Document Completion Status
     */
    @ComesFrom(path = "DocumentReference.docStatus", field = 17, comment = "Document Completion Status", table = "0271")
    public void setDocumentCompletionStatus(CodeType documentCompletionStatus) {
        /* TXA-17 -> DocumentReference.docStatus : code = "documentCompletionStatus"*/
        getDocumentReference().setDocStatus(DocumentReference.ReferredDocumentStatus.fromCode(documentCompletionStatus.toString()));
    }

    /**
     * Add the Document Confidentiality Status
     * @param documentConfidentialityStatus the Document Confidentiality Status
     */
    @ComesFrom(path = "DocumentReference.securityLabel", field = 18, comment = "Document Confidentiality Status", table = "0272")
    public void addDocumentConfidentialityStatus(CodeableConcept documentConfidentialityStatus) {
        documentConfidentialityStatus = mapVia(documentConfidentialityStatus, "ConceptMap/table-hl70272-to-v2-0272");
        /* TXA-18 -> DocumentReference.securityLabel : CodeableConcept = "documentConfidentialityStatus"*/
        getDocumentReference().getSecurityLabel().add(documentConfidentialityStatus);
    }

    /**
     * Set the Document Availability Status
     * @param documentAvailabilityStatus the Document Availability Status
     */
    @ComesFrom(path = "DocumentReference.status", field = 19, comment = "Document Availability Status", table = "0273")
    public void setDocumentAvailabilityStatus(CodeType documentAvailabilityStatus) {
        /* TXA-19 -> DocumentReference.status : code = "documentAvailabilityStatus"*/
        getDocumentReference().setStatus(Enumerations.DocumentReferenceStatus.fromCode(documentAvailabilityStatus.toString()));

        /* TXA-19 -> DocumentReference.status.extension.url : StringType = "documentAvailabilityStatus"*/
        getStatusExtension1().setUrl("http://hl7.org/fhir/StructureDefinition/alternate-codes");

        /* TXA-19 -> DocumentReference.status.extension.valueCodeableConcept : CodeableConcept = "documentAvailabilityStatus"*/
        getStatusExtension1().setValue(documentAvailabilityStatus);
    }

    /**
     * Set the Document Title
     * @param documentTitle the Document Title
     */
    @ComesFrom(path = "DocumentReference.description", field = 25, comment = "Document Title")
    public void setDocumentTitle(StringType documentTitle) {
        /* TXA-25 -> DocumentReference.description : string = "documentTitle"*/
        getDocumentReference().setDescriptionElement(documentTitle);
    }

}
