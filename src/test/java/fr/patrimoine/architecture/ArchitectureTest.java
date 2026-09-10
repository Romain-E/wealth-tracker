package fr.patrimoine.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * The architecture, enforced by the build rather than described in a README.
 *
 * <p>A diagram in documentation is a statement of intent that decays the first time someone is in a
 * hurry. These rules fail the build instead, which is the only version of "the domain has no
 * framework dependencies" that is still true in six months.
 */
@AnalyzeClasses(
        packages = "fr.patrimoine",
        importOptions = {ImportOption.DoNotIncludeTests.class})
class ArchitectureTest {

    /**
     * The central claim of the whole design. If this rule ever fails, the domain has stopped being
     * plain Java and the hexagon has leaked.
     */
    @ArchTest
    static final ArchRule domain_is_free_of_frameworks =
            noClasses()
                    .that()
                    .resideInAPackage("..domain..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(
                            "org.springframework..",
                            "jakarta.persistence..",
                            "jakarta.validation..",
                            "org.hibernate..",
                            "com.fasterxml.jackson..",
                            "io.github.resilience4j..")
                    .because(
                            "the domain must be testable, and readable, without any framework on the"
                                    + " classpath");

    /** Dependencies point inward. The domain knows nothing about what calls it. */
    @ArchTest
    static final ArchRule domain_does_not_know_the_outer_layers =
            noClasses()
                    .that()
                    .resideInAPackage("..domain..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("..application..", "..infrastructure..", "..api..")
                    .because(
                            "dependencies in a hexagonal architecture point inward, never outward");

    @ArchTest
    static final ArchRule layers_are_respected =
            layeredArchitecture()
                    .consideringOnlyDependenciesInLayers()
                    .layer("Domain")
                    .definedBy("..domain..")
                    .optionalLayer("Application")
                    .definedBy("..application..")
                    .optionalLayer("Infrastructure")
                    .definedBy("..infrastructure..")
                    .optionalLayer("Api")
                    .definedBy("..api..")
                    .whereLayer("Api")
                    .mayNotBeAccessedByAnyLayer()
                    .whereLayer("Infrastructure")
                    .mayNotBeAccessedByAnyLayer()
                    .whereLayer("Application")
                    .mayOnlyBeAccessedByLayers("Api", "Infrastructure");

    /**
     * Money is never a floating-point number. This is the rule that stops a well-meaning refactor
     * from reintroducing rounding error into a wealth report six months from now.
     */
    @ArchTest
    static final ArchRule no_floating_point_money =
            noFields()
                    .that()
                    .areDeclaredInClassesThat()
                    .resideInAPackage("..domain.model..")
                    .should()
                    .haveRawType(double.class)
                    .orShould()
                    .haveRawType(float.class)
                    .orShould()
                    .haveRawType(Double.class)
                    .orShould()
                    .haveRawType(Float.class)
                    .because(
                            "binary floating point cannot represent 0.10, and money is measured in it");

    @ArchTest
    static final ArchRule no_legacy_date_api =
            noClasses()
                    .should()
                    .dependOnClassesThat()
                    .haveFullyQualifiedName("java.util.Date")
                    .orShould()
                    .dependOnClassesThat()
                    .haveFullyQualifiedName("java.util.Calendar")
                    .orShould()
                    .dependOnClassesThat()
                    .haveFullyQualifiedName("java.text.SimpleDateFormat")
                    .because(
                            "java.time exists, is immutable, and is not silently timezone-dependent");
}
