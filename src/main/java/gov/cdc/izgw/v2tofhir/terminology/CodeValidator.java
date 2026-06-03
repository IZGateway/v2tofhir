package gov.cdc.izgw.v2tofhir.terminology;

/**
 * Validates coded concepts against value sets.
 *
 * <p>The default implementation returns {@code false}; override to provide real validation.</p>
 *
 * @author Audacious Inquiry
 * @see TerminologyMapper
 */
public interface CodeValidator {

    /**
     * Returns {@code true} if the given code is a valid member of the given code system
     * within the named value set.
     *
     * @param valueSetUri the canonical URI of the value set
     * @param system      the code system URI
     * @param code        the code to validate
     * @return {@code true} if valid; {@code false} if not valid or if the value set is unknown
     */
    default boolean validateCode(String valueSetUri, String system, String code) {
        return false;
    }
}
