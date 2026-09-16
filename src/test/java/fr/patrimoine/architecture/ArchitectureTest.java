package fr.patrimoine.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import jakarta.persistence.Entity;

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

    /**
     * The application layer is allowed exactly two framework concessions -- the {@code @Service}
     * stereotype and declarative transactions -- because transaction demarcation genuinely is a
     * use-case concern. This rule draws that line so the concession cannot quietly widen: no web
     * layer, no Spring Data, no JPA, no Jackson. The day someone reaches for {@code @GetMapping} or
     * an {@code EntityManager} in a use case, the build says no.
     */
    @ArchTest
    static final ArchRule application_layer_knows_nothing_of_web_or_persistence =
            noClasses()
                    .that()
                    .resideInAPackage("..application..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(
                            "org.springframework.web..",
                            "org.springframework.http..",
                            "org.springframework.data..",
                            "jakarta.persistence..",
                            "jakarta.servlet..",
                            "com.fasterxml.jackson..")
                    .because(
                            "use cases orchestrate the domain; they must not know how they are called"
                                    + " or where data is stored");

    /** A driven port is a contract, so it can only be an interface. */
    @ArchTest
    static final ArchRule driven_ports_are_interfaces =
            classes()
                    .that()
                    .resideInAPackage("..application.port.out..")
                    .should()
                    .beInterfaces()
                    .because(
                            "an out-port declares what the application needs, never how it is met");

    /** Same for the driving side: a use case is an interface the API layer depends on. */
    @ArchTest
    static final ArchRule use_cases_are_interfaces =
            classes()
                    .that()
                    .haveSimpleNameEndingWith("UseCase")
                    .should()
                    .beInterfaces()
                    .because(
                            "controllers depend on the use case, not on the service, so they can be"
                                    + " tested against a stub");

    /**
     * Controllers go through a use-case interface, never the service behind it. Without this,
     * "hexagonal" decays into a package naming convention the first time someone autowires a
     * concrete service into a controller.
     *
     * <p>{@code allowEmptyShould} because the API layer does not exist yet. Like the optional
     * layers above, the rule is armed now so it bites on the first controller instead of relying on
     * someone remembering to add it then.
     */
    @ArchTest
    static final ArchRule services_are_reached_through_their_ports =
            noClasses()
                    .that()
                    .resideInAPackage("..api..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage("..application.service..")
                    .because("the API layer depends on port.in interfaces, not on implementations")
                    .allowEmptyShould(true);

    /**
     * JPA entities are a storage detail of the persistence adapter. Package-private visibility
     * already keeps them there; this rule stops someone widening it to make a compile error go away
     * in a place that should never have needed an entity.
     */
    @ArchTest
    static final ArchRule entities_stay_inside_the_persistence_adapter =
            classes()
                    .that()
                    .areAnnotatedWith(Entity.class)
                    .should()
                    .resideInAPackage("..infrastructure.persistence..")
                    .andShould()
                    .notBePublic()
                    .because(
                            "the rest of the application works with domain aggregates, never with"
                                    + " rows");
}
