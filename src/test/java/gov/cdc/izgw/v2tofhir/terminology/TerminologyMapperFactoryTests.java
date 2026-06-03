package gov.cdc.izgw.v2tofhir.terminology;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.Optional;

import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.NamingSystem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link TerminologyMapperFactory}.
 * Tests exercise {@link TerminologyMapperFactory#loadMapper(String)} directly to avoid
 * the need for environment-variable mutation.  The {@link TerminologyMapperFactory#get()}
 * caching path is tested by verifying identity equality on repeated calls.
 */
class TerminologyMapperFactoryTests {

    @BeforeEach
    @AfterEach
    void resetCache() {
        TerminologyMapperFactory.resetForTesting();
    }

    // -------------------------------------------------------------------------
    // loadMapper — null / blank → DefaultTerminologyMapper
    // -------------------------------------------------------------------------

    @Test
    void loadMapper_null_returnsDefault() {
        TerminologyMapper mapper = TerminologyMapperFactory.loadMapper(null);
        assertNotNull(mapper);
        assertInstanceOf(DefaultTerminologyMapper.class, mapper);
    }

    @Test
    void loadMapper_blank_returnsDefault() {
        TerminologyMapper mapper = TerminologyMapperFactory.loadMapper("   ");
        assertNotNull(mapper);
        assertInstanceOf(DefaultTerminologyMapper.class, mapper);
    }

    // -------------------------------------------------------------------------
    // loadMapper — valid TerminologyMapper class → custom instance
    // -------------------------------------------------------------------------

    @Test
    void loadMapper_validClass_returnsCustomInstance() {
        TerminologyMapper mapper = TerminologyMapperFactory.loadMapper(
                ValidCustomMapper.class.getName());
        assertNotNull(mapper);
        assertInstanceOf(ValidCustomMapper.class, mapper);
    }

    // -------------------------------------------------------------------------
    // loadMapper — error cases → fallback to DefaultTerminologyMapper
    // -------------------------------------------------------------------------

    @Test
    void loadMapper_classNotFound_returnsDefault() {
        TerminologyMapper mapper = TerminologyMapperFactory.loadMapper(
                "com.example.DoesNotExist");
        assertNotNull(mapper);
        assertInstanceOf(DefaultTerminologyMapper.class, mapper);
    }

    @Test
    void loadMapper_doesNotImplementTerminologyMapper_returnsDefault() {
        // NotAMapper exists on classpath but does not implement TerminologyMapper
        TerminologyMapper mapper = TerminologyMapperFactory.loadMapper(
                NotAMapper.class.getName());
        assertNotNull(mapper);
        assertInstanceOf(DefaultTerminologyMapper.class, mapper);
    }

    @Test
    void loadMapper_subInterfaceOnly_returnsDefault() {
        // SubInterfaceOnlyMapper implements only NamingSystemResolver, not TerminologyMapper
        TerminologyMapper mapper = TerminologyMapperFactory.loadMapper(
                SubInterfaceOnlyMapper.class.getName());
        assertNotNull(mapper);
        assertInstanceOf(DefaultTerminologyMapper.class, mapper);
    }

    @Test
    void loadMapper_constructorThrows_returnsDefault() {
        TerminologyMapper mapper = TerminologyMapperFactory.loadMapper(
                ThrowingConstructorMapper.class.getName());
        assertNotNull(mapper);
        assertInstanceOf(DefaultTerminologyMapper.class, mapper);
    }

    // -------------------------------------------------------------------------
    // get() — caching behaviour
    // -------------------------------------------------------------------------

    @Test
    void get_noEnvVar_returnsDefaultTerminologyMapper() {
        // With no env var set (typical CI environment), get() falls back to default
        TerminologyMapper mapper = TerminologyMapperFactory.get();
        assertNotNull(mapper);
        assertInstanceOf(DefaultTerminologyMapper.class, mapper);
    }

    @Test
    void get_repeatedCalls_returnSameReference() {
        TerminologyMapper first  = TerminologyMapperFactory.get();
        TerminologyMapper second = TerminologyMapperFactory.get();
        TerminologyMapper third  = TerminologyMapperFactory.get();
        assertSame(first, second, "Repeated get() calls should return the same instance");
        assertSame(first, third,  "Repeated get() calls should return the same instance");
    }

    @Test
    void resetForTesting_allowsNewInstanceOnNextGet() {
        TerminologyMapper first = TerminologyMapperFactory.get();
        TerminologyMapperFactory.resetForTesting();
        TerminologyMapper second = TerminologyMapperFactory.get();
        // Both are DefaultTerminologyMapper but they are distinct instances after reset
        assertInstanceOf(DefaultTerminologyMapper.class, first);
        assertInstanceOf(DefaultTerminologyMapper.class, second);
        // They should be different objects (new instance after reset)
        assert first != second : "After resetForTesting(), a new instance should be created";
    }

    // -------------------------------------------------------------------------
    // setDefaultSupplier — task 9 tests
    // -------------------------------------------------------------------------

    @Test
    void setDefaultSupplier_usedWhenEnvVarAbsent() {
        // Register a supplier that returns a ValidCustomMapper before get() is called
        TerminologyMapperFactory.setDefaultSupplier(ValidCustomMapper::new);
        TerminologyMapper mapper = TerminologyMapperFactory.get();
        assertNotNull(mapper);
        assertInstanceOf(ValidCustomMapper.class, mapper,
                "get() should return the supplier-provided type when env var is absent");
    }

    @Test
    void setDefaultSupplier_ignoredWhenClassNameProvided() {
        // When loadMapper is given an explicit class name the supplier must NOT be used
        TerminologyMapperFactory.setDefaultSupplier(ValidCustomMapper::new);
        TerminologyMapper mapper = TerminologyMapperFactory.loadMapper(
                ValidCustomMapper.class.getName());
        assertNotNull(mapper);
        assertInstanceOf(ValidCustomMapper.class, mapper,
                "loadMapper with a valid class name should return that class regardless of supplier");
        // The instance comes from the class name, not the supplier — both happen to be
        // ValidCustomMapper here, so we verify by giving a *different* supplier and checking
        // the correct class is still used
        TerminologyMapperFactory.resetForTesting();
        TerminologyMapperFactory.setDefaultSupplier(DefaultTerminologyMapper::new);
        TerminologyMapper mapper2 = TerminologyMapperFactory.loadMapper(
                ValidCustomMapper.class.getName());
        assertInstanceOf(ValidCustomMapper.class, mapper2,
                "loadMapper with a valid class name ignores the default supplier");
    }

    @Test
    void setDefaultSupplier_afterResolution_isNoOp() {
        // Resolve the instance first (with the default supplier → DefaultTerminologyMapper)
        TerminologyMapper first = TerminologyMapperFactory.get();
        assertInstanceOf(DefaultTerminologyMapper.class, first);

        // Attempting to install a new supplier after resolution must be a no-op
        TerminologyMapperFactory.setDefaultSupplier(ValidCustomMapper::new);

        // The cached instance must not change
        TerminologyMapper second = TerminologyMapperFactory.get();
        assertSame(first, second,
                "setDefaultSupplier after resolution must not replace the cached instance");
        assertInstanceOf(DefaultTerminologyMapper.class, second,
                "Cached instance type must remain DefaultTerminologyMapper");
    }

    @Test
    void resetForTesting_resetSupplierToDefault() {
        // Register a custom supplier, resolve, reset, then verify default is restored
        TerminologyMapperFactory.setDefaultSupplier(ValidCustomMapper::new);
        TerminologyMapperFactory.resetForTesting();

        // After reset the supplier is back to DefaultTerminologyMapper::new
        TerminologyMapper mapper = TerminologyMapperFactory.get();
        assertInstanceOf(DefaultTerminologyMapper.class, mapper,
                "resetForTesting() must restore the default supplier");
    }

    // =========================================================================
    // Test helper classes (must be public / package-accessible for Class.forName
    // + getDeclaredConstructor().newInstance() to work)
    // =========================================================================

    /** A valid full {@link TerminologyMapper} implementation used in the happy-path test. */
    public static final class ValidCustomMapper implements TerminologyMapper {
        // All methods have defaults in the interface — nothing extra needed
    }

    /** A class that exists on the classpath but does NOT implement {@link TerminologyMapper}. */
    public static final class NotAMapper {
        // Intentionally does not implement TerminologyMapper
    }

    /**
     * Implements only {@link NamingSystemResolver} — sub-interface only, not the full
     * {@link TerminologyMapper} composite.
     */
    public static final class SubInterfaceOnlyMapper implements NamingSystemResolver {
        @Override
        public Optional<String> toUri(String oid) { return Optional.empty(); }
        @Override
        public Optional<String> toOid(String uri) { return Optional.empty(); }
        @Override
        public Optional<NamingSystem> getNamingSystem(String id) { return Optional.empty(); }
    }

    /** A valid {@link TerminologyMapper} whose constructor throws a {@link RuntimeException}. */
    public static final class ThrowingConstructorMapper implements TerminologyMapper {
        public ThrowingConstructorMapper() {
            throw new IllegalStateException("Simulated constructor failure");
        }

        // Suppress "unused" — the constructor is invoked reflectively
        @Override
        public Optional<TranslationResult> mapCode(String mappingName, Coding source) {
            return Optional.empty();
        }

        @Override
        public Optional<TranslationResult> translate(Coding source, String targetSystem) {
            return Optional.empty();
        }

        @Override
        public Optional<Coding> mapUnit(String unitCode) { return Optional.empty(); }

        @Override
        public Optional<Extension> mapRace(String code, String codeSystem) {
            return Optional.empty();
        }

        @Override
        public Optional<Extension> mapEthnicity(String code, String codeSystem) {
            return Optional.empty();
        }
    }
}
