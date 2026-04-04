package gov.cdc.izgw.v2tofhir.terminology;

import java.util.concurrent.atomic.AtomicReference;

import lombok.extern.slf4j.Slf4j;

/**
 * Factory that provides the application-wide {@link TerminologyMapper} instance.
 *
 * <p>On the first call to {@link #get()} the factory reads the environment variable
 * {@code V2TOFHIR_TERMINOLOGY_MAPPER}. If the variable is set, the factory attempts to
 * load the named class and instantiate it via its public no-argument constructor. The class
 * must implement {@link TerminologyMapper}; if it only implements a sub-interface the factory
 * treats it as an error and falls back to the default.</p>
 *
 * <p>If the variable is absent, the named class cannot be found, does not implement
 * {@link TerminologyMapper}, has no public no-arg constructor, or throws during construction,
 * the factory logs a warning and falls back to {@link DefaultTerminologyMapper}. The
 * application continues to function normally in all error cases.</p>
 *
 * <p>The resolved instance is cached in an {@link AtomicReference}; subsequent calls to
 * {@link #get()} return the same object reference without re-reading the environment variable
 * or repeating class loading.</p>
 *
 * @author Audacious Inquiry
 * @see TerminologyMapper
 * @see DefaultTerminologyMapper
 */
@Slf4j
public final class TerminologyMapperFactory {

    private static final String FALLING_BACK = "falling back to DefaultTerminologyMapper";

	/** Environment variable that names the {@link TerminologyMapper} implementation to use. */
    public static final String ENV_VAR = "V2TOFHIR_TERMINOLOGY_MAPPER";

    private static final AtomicReference<TerminologyMapper> INSTANCE = new AtomicReference<>();

    private TerminologyMapperFactory() {
        // Utility class — not instantiable
    }

    /**
     * Returns the application-wide {@link TerminologyMapper} instance, creating it on first
     * call.
     *
     * <p>Resolution order:</p>
     * <ol>
     *   <li>Return cached instance if already resolved.</li>
     *   <li>Read {@code V2TOFHIR_TERMINOLOGY_MAPPER}; if set, attempt dynamic load.</li>
     *   <li>Fall back to {@link DefaultTerminologyMapper} on any error or when the variable
     *       is absent.</li>
     * </ol>
     *
     * @return the shared {@link TerminologyMapper}; never {@code null}
     */
    public static TerminologyMapper get() {
        TerminologyMapper existing = INSTANCE.get();
        if (existing != null) {
            return existing;
        }
        TerminologyMapper resolved = loadMapper(System.getenv(ENV_VAR));
        INSTANCE.compareAndSet(null, resolved);
        return INSTANCE.get();
    }

    /**
     * Resolves a {@link TerminologyMapper} from the supplied class name without touching the
     * cached instance.
     *
     * <p>If {@code className} is {@code null} or blank a new {@link DefaultTerminologyMapper}
     * is returned. All failure cases (class not found, wrong type, construction failure) log
     * a warning and return a {@link DefaultTerminologyMapper}.</p>
     *
     * <p>This method is package-private to allow direct testing without environment variable
     * manipulation.</p>
     *
     * @param className fully-qualified class name, or {@code null}/blank for the default
     * @return a {@link TerminologyMapper}; never {@code null}
     */
    static TerminologyMapper loadMapper(String className) {
        return resolve(className);
    }

    /**
     * Clears the cached {@link TerminologyMapper} instance so that the next call to
     * {@link #get()} will re-read the environment variable and resolve a new instance.
     *
     * <p><strong>This method is intended for use in tests only.</strong> Do not call it in
     * production code.</p>
     */
    static void resetForTesting() {
        INSTANCE.set(null);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private static TerminologyMapper resolve(String className) {
        if (className == null || className.isBlank()) {
            log.info("{}={} — using DefaultTerminologyMapper", ENV_VAR,
                    className == null ? "(not set)" : "(blank)");
            return new DefaultTerminologyMapper();
        }

        log.info("{}={} — attempting to load custom TerminologyMapper", ENV_VAR, className);
        try {
            Class<?> clazz = Class.forName(className);
            if (!TerminologyMapper.class.isAssignableFrom(clazz)) {
                log.warn("{} = '{}': class does not implement TerminologyMapper — " + FALLING_BACK, ENV_VAR, className);
                return new DefaultTerminologyMapper();
            }
            TerminologyMapper mapper = (TerminologyMapper) clazz.getDeclaredConstructor()
                    .newInstance();
            log.info("Loaded custom TerminologyMapper: {}", className);
            return mapper;
        } catch (ClassNotFoundException e) {
            log.warn("{} = '{}': class not found on classpath — " + FALLING_BACK, ENV_VAR, className);
        } catch (ClassCastException e) {
            log.warn("{} = '{}': class does not implement TerminologyMapper ({}) — " + FALLING_BACK, ENV_VAR, className,
                    e.getMessage());
        } catch (ReflectiveOperationException e) {
            log.warn("{} = '{}': could not instantiate class ({}) — " + FALLING_BACK, ENV_VAR, className,
                    e.getMessage());
        } catch (RuntimeException e) {
            log.warn("{} = '{}': constructor threw {} ({}) — " + FALLING_BACK, ENV_VAR, className,
                    e.getClass().getSimpleName(), e.getMessage());
        }
        return new DefaultTerminologyMapper();
    }
}
