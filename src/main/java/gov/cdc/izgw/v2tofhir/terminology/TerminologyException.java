package gov.cdc.izgw.v2tofhir.terminology;

/**
 * Unchecked exception signalling an unrecoverable error in a terminology operation,
 * such as a malformed resource or a storage failure.
 *
 * <p>Normal "not found" results are expressed as empty {@link java.util.Optional} returns,
 * not as {@code TerminologyException}.</p>
 *
 * @author Audacious Inquiry
 */
public class TerminologyException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new TerminologyException with the specified message.
     *
     * @param message a description of the failure
     */
    public TerminologyException(String message) {
        super(message);
    }

    /**
     * Constructs a new TerminologyException with the specified message and cause.
     *
     * @param message a description of the failure
     * @param cause   the underlying cause
     */
    public TerminologyException(String message, Throwable cause) {
        super(message, cause);
    }
}
