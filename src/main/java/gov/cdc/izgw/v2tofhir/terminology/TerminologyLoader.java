package gov.cdc.izgw.v2tofhir.terminology;

import java.util.Optional;

import org.hl7.fhir.r4.model.CodeSystem;
import org.hl7.fhir.r4.model.ConceptMap;
import org.hl7.fhir.r4.model.NamingSystem;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.ValueSet;

/**
 * Loads and retrieves FHIR terminology resources ({@link NamingSystem}, {@link ConceptMap},
 * {@link CodeSystem}, {@link ValueSet}).
 *
 * <p>The default {@link #load} implementation throws {@link UnsupportedOperationException}.
 * Retrieve methods default to returning {@link Optional#empty()}.</p>
 *
 * @author Audacious Inquiry
 * @see TerminologyMapper
 */
public interface TerminologyLoader {

    /**
     * Registers a FHIR terminology resource ({@link NamingSystem}, {@link ConceptMap},
     * {@link CodeSystem}, or {@link ValueSet}).
     *
     * <p>The default implementation throws {@link UnsupportedOperationException}.
     * Implementations that support dynamic loading should override this method.</p>
     *
     * @param resource the resource to load; must be one of the four supported types
     * @throws TerminologyException          if the resource is malformed or cannot be stored
     * @throws UnsupportedOperationException if this loader does not support dynamic loading
     */
    default void load(Resource resource) {
        throw new UnsupportedOperationException("load is not supported by this TerminologyLoader");
    }

    /**
     * Returns the {@link NamingSystem} resource registered under the given identifier.
     *
     * @param id the resource id
     * @return the resource, or {@link Optional#empty()} if not found
     */
    default Optional<NamingSystem> getNamingSystem(String id) {
        return Optional.empty();
    }

    /**
     * Returns the {@link ConceptMap} resource registered under the given identifier.
     *
     * @param id the resource id
     * @return the resource, or {@link Optional#empty()} if not found
     */
    default Optional<ConceptMap> getConceptMap(String id) {
        return Optional.empty();
    }

    /**
     * Returns the {@link CodeSystem} resource registered under the given identifier.
     *
     * @param id the resource id
     * @return the resource, or {@link Optional#empty()} if not found
     */
    default Optional<CodeSystem> getCodeSystem(String id) {
        return Optional.empty();
    }

    /**
     * Returns the {@link ValueSet} resource registered under the given identifier.
     *
     * @param id the resource id
     * @return the resource, or {@link Optional#empty()} if not found
     */
    default Optional<ValueSet> getValueSet(String id) {
        return Optional.empty();
    }
}
