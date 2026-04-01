package gov.cdc.izgw.v2tofhir.terminology;

import java.util.Optional;

/**
 * Resolves and manages preferred display names for coded concepts.
 *
 * <p>Lookup defaults return empty results; {@link #setDisplay} default throws
 * {@link UnsupportedOperationException} as there is no meaningful no-op for a store operation.</p>
 *
 * @author Audacious Inquiry
 * @see TerminologyMapper
 */
public interface DisplayNameResolver {

    /**
     * Returns the preferred display text for the given code in the given system.
     *
     * @param system the code system URI
     * @param code   the code
     * @return the display text, or {@link Optional#empty()} if not registered
     */
    default Optional<String> getDisplay(String system, String code) {
        return Optional.empty();
    }

    /**
     * Returns {@code true} if a display name is registered for the given code.
     *
     * @param system the code system URI
     * @param code   the code
     * @return {@code true} if a display is available; {@code false} otherwise
     */
    default boolean hasDisplay(String system, String code) {
        return false;
    }

    /**
     * Registers or overrides the display name for the given code.
     *
     * <p>The default implementation throws {@link UnsupportedOperationException}.
     * Implementations that support a writable display registry should override this method.</p>
     *
     * @param system  the code system URI
     * @param code    the code
     * @param display the display text to register
     * @throws UnsupportedOperationException if this resolver does not support display registration
     */
    default void setDisplay(String system, String code, String display) {
        throw new UnsupportedOperationException("setDisplay is not supported by this DisplayNameResolver");
    }
}
