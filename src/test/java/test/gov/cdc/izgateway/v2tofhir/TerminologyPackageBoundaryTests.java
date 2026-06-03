package test.gov.cdc.izgateway.v2tofhir;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Enforces that the five static utility classes moved to
 * {@code gov.cdc.izgw.v2tofhir.terminology} are not accessed directly from outside
 * that package, with the sole exception of the deprecated forwarding stubs in
 * {@code gov.cdc.izgw.v2tofhir.utils}.
 *
 * <p>New code should use {@link gov.cdc.izgw.v2tofhir.terminology.TerminologyMapper}
 * (obtained via {@link gov.cdc.izgw.v2tofhir.terminology.TerminologyMapperFactory#get()})
 * for all terminology operations, and
 * {@link gov.cdc.izgw.v2tofhir.utils.Systems} for namespace/system URI constants.</p>
 */
class TerminologyPackageBoundaryTests {

    private static final String TERMINOLOGY_PKG = "gov.cdc.izgw.v2tofhir.terminology";
    private static final String UTILS_PKG        = "gov.cdc.izgw.v2tofhir.utils";

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .importPackages("gov.cdc.izgw.v2tofhir", "test.gov.cdc.izgateway.v2tofhir");
    }

    @Test
    void noClassOutsideTerminologyPackageShouldAccessTerminologySystems() {
        ArchRule rule = noClasses()
                .that().resideOutsideOfPackage(TERMINOLOGY_PKG)
                .and().resideOutsideOfPackage(UTILS_PKG)
                .should().accessClassesThat()
                .haveFullyQualifiedName(TERMINOLOGY_PKG + ".Systems");
        rule.check(classes);
    }

    @Test
    void noClassOutsideTerminologyPackageShouldAccessTerminologyMapping() {
        ArchRule rule = noClasses()
                .that().resideOutsideOfPackage(TERMINOLOGY_PKG)
                .and().resideOutsideOfPackage(UTILS_PKG)
                .should().accessClassesThat()
                .haveFullyQualifiedName(TERMINOLOGY_PKG + ".Mapping");
        rule.check(classes);
    }

    @Test
    void noClassOutsideTerminologyPackageShouldAccessTerminologyUnits() {
        ArchRule rule = noClasses()
                .that().resideOutsideOfPackage(TERMINOLOGY_PKG)
                .and().resideOutsideOfPackage(UTILS_PKG)
                .should().accessClassesThat()
                .haveFullyQualifiedName(TERMINOLOGY_PKG + ".Units");
        rule.check(classes);
    }

    @Test
    void noClassOutsideTerminologyPackageShouldAccessTerminologyISO3166() {
        ArchRule rule = noClasses()
                .that().resideOutsideOfPackage(TERMINOLOGY_PKG)
                .and().resideOutsideOfPackage(UTILS_PKG)
                .should().accessClassesThat()
                .haveFullyQualifiedName(TERMINOLOGY_PKG + ".ISO3166");
        rule.check(classes);
    }

    @Test
    void noClassOutsideTerminologyPackageShouldAccessTerminologyRaceAndEthnicity() {
        ArchRule rule = noClasses()
                .that().resideOutsideOfPackage(TERMINOLOGY_PKG)
                .and().resideOutsideOfPackage(UTILS_PKG)
                .should().accessClassesThat()
                .haveFullyQualifiedName(TERMINOLOGY_PKG + ".RaceAndEthnicity");
        rule.check(classes);
    }
}
