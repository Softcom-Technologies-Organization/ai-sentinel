package pro.softcom.sentinelle.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RestController;

/**
 * ArchUnit tests to lock the hexagonal architecture boundaries (Ports & Adapters).
 * Focus: forbid core layers depending on infrastructure and on Spring, and
 * ensure adapters/config live in infrastructure packages.
 */
class HexagonalArchitectureTest {

    private static JavaClasses importedClasses;

    @BeforeAll
    static void setup() {
        importedClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("pro.softcom.sentinelle");
        System.out.println("[DEBUG_LOG] Imported classes: " + importedClasses.size());
    }

    @Test
    @DisplayName("Layering: no Application/Domain -> Infrastructure dependency; Application only depends on Domain")
    void layering_shouldLockBoundaries() {
        // 1) No Domain -> Application/Infrastructure
        ArchRule domainNoOut = noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage("..application..", "..infrastructure..")
                .as("Domain must not depend on Application or Infrastructure");

        // 2) No Application -> Infrastructure
        ArchRule appNoInfra = noClasses()
                .that().resideInAPackage("..application..")
                .should().dependOnClassesThat().resideInAnyPackage("..infrastructure..")
                .as("Application must not depend on Infrastructure");

        // 3) Optional documentation layer view (does not enforce direction here)
        ArchRule layers = layeredArchitecture()
                .consideringAllDependencies()
                .layer("Domain").definedBy("..domain..")
                .layer("Application").definedBy("..application..")
                .layer("Infrastructure").definedBy("..infrastructure..")
                // Who may access whom (incoming deps)
                .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Infrastructure")
                .whereLayer("Application").mayOnlyBeAccessedByLayers("Infrastructure")
                .whereLayer("Infrastructure").mayNotBeAccessedByAnyLayer()
                .as("Hexagonal layering (adapters in infrastructure call application; domain is core)");

        domainNoOut.allowEmptyShould(true).check(importedClasses);
        appNoInfra.allowEmptyShould(true).check(importedClasses);
        layers.allowEmptyShould(true).check(importedClasses);
    }

    @Test
    @DisplayName("No Spring in Domain/Application (framework-agnostic core)")
    void core_shouldNotDependOnSpring() {
        ArchRule rule = noClasses()
                .that().resideInAnyPackage("..domain..", "..application..")
                .should().dependOnClassesThat().resideInAnyPackage("org.springframework..")
                .as("Domain & Application must not depend on Spring");

        rule.allowEmptyShould(true).check(importedClasses);
    }

    @Test
    @DisplayName("Application must not depend on Spring (should fail until Spring deps moved to infra)")
    void application_shouldNotDependOnSpring() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..application..")
                .should().dependOnClassesThat().resideInAnyPackage("org.springframework..")
                .as("Application layer must be framework-agnostic (no org.springframework)");

        // This rule is expected to FAIL currently because some application classes use Spring annotations/APIs
        rule.allowEmptyShould(true).check(importedClasses);
    }

    @Test
    @DisplayName("Controllers must live under infrastructure adapter.in")
    void controllers_shouldResideInInfrastructureAdapterIn() {
        ArchRule rule = classes()
                .that().areAnnotatedWith(RestController.class)
                .or().haveSimpleNameEndingWith("Controller")
                .should().resideInAPackage("..infrastructure..adapter.in..")
                .as("Controllers belong to infrastructure adapter.in");

        rule.allowEmptyShould(true).check(importedClasses);
    }

    @Test
    @DisplayName("@Configuration classes must live under infrastructure config")
    void configuration_shouldResideInInfrastructureConfig() {
        ArchRule rule = classes()
                .that().areAnnotatedWith(Configuration.class)
                .or().haveSimpleNameEndingWith("Config")
                .should().resideInAnyPackage("..infrastructure..config..", "..infrastructure..config")
                .as("Spring configuration belongs to infrastructure config");

        rule.allowEmptyShould(true).check(importedClasses);
    }

    @Test
    @DisplayName("DTOs must not reside in Domain")
    void dtos_shouldNotBeInDomain() {
        ArchRule rule = noClasses()
                .that().haveSimpleNameEndingWith("Dto")
                .should().resideInAPackage("..domain..")
                .as("DTOs must not be placed in the domain layer");

        rule.allowEmptyShould(true).check(importedClasses);
    }

    @Test
    @DisplayName("In-ports must be implemented in application usecases; infra must not implement in-ports")
    void inPorts_shouldBeImplementedByApplicationUsecases() {
        List<Class<?>> inPorts = findPortInterfaces(".port.in.");
        for (Class<?> port : inPorts) {
            classes()
                .that().implement(port)
                .should().resideInAPackage("..application..usecase..")
                .as("In-port '" + port.getSimpleName() + "' must be implemented in application usecase")
                .allowEmptyShould(true)
                .check(importedClasses);

            noClasses()
                .that().resideInAPackage("..infrastructure..")
                .should().implement(port)
                .as("Infrastructure must not implement in-port '" + port.getSimpleName() + "'")
                .allowEmptyShould(true)
                .check(importedClasses);
        }
    }

    @Test
    @DisplayName("Out-ports must be implemented in infrastructure adapter.out")
    void outPorts_shouldBeImplementedByInfrastructureAdapters() {
        List<Class<?>> outPorts = findPortInterfaces(".port.out.");
        for (Class<?> port : outPorts) {
            classes()
                .that().implement(port)
                .should().resideInAPackage("..infrastructure..adapter.out..")
                .as("Out-port '" + port.getSimpleName() + "' must be implemented in infrastructure adapter.out")
                .allowEmptyShould(true)
                .check(importedClasses);
        }
    }

    // Helpers
    private static List<Class<?>> findPortInterfaces(String portSegment) {
        List<Class<?>> result = new ArrayList<>();
        for (JavaClass jc : importedClasses) {
            if (!jc.isInterface()) {
                continue;
            }
            String pkg = jc.getPackageName();
            if (pkg.contains(".application.") && pkg.contains(portSegment)) {
                try {
                    result.add(Class.forName(jc.getName()));
                } catch (ClassNotFoundException _) {
                    // Safe to ignore in tests: class not present on reflection path
                }
            }
        }
        return result;
    }

    @Test
    @DisplayName("Adapter.in (contrôleurs) ne doivent dépendre que des ports-in, pas des implémentations")
    void adapterIn_shouldDependOnlyOnInPorts_NotOnUsecaseImpl() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("..infrastructure..adapter.in..")
            .should().dependOnClassesThat().resideInAnyPackage("..application..usecase..")
            .as("Les adapters in ne doivent pas connaître les implémentations des use cases");

        rule.allowEmptyShould(true).check(importedClasses);
    }

    @Test
    @DisplayName("Usecases ne doivent pas dépendre de l'infrastructure")
    void usecases_shouldNotDependOnInfrastructure() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("..application..usecase..")
            .should().dependOnClassesThat().resideInAnyPackage("..infrastructure..")
            .as("Les implémentations de use case doivent rester dans le core (sans infra)");

        rule.allowEmptyShould(true).check(importedClasses);
    }

    @Test
    @DisplayName("Ports (in/out) ne doivent pas dépendre de Spring ou de l'infrastructure")
    void ports_shouldBeFrameworkAndInfraAgnostic() {
        ArchRule rule = noClasses()
            .that().resideInAnyPackage("..application..port.in..", "..application..port.out..")
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "..infrastructure..")
            .as("Les ports doivent être agnostiques (pas de Spring/Infra)");

        rule.allowEmptyShould(true).check(importedClasses);
    }

    @Test
    @DisplayName("Out-ports ne doivent pas être implémentés dans l'application")
    void outPorts_shouldNotBeImplementedInApplication() {
        List<Class<?>> outPorts = findPortInterfaces(".port.out.");
        for (Class<?> port : outPorts) {
            noClasses()
                .that().resideInAPackage("..application..")
                .should().implement(port)
                .as("Les out-ports ne doivent pas être implémentés dans l'application: " + port.getSimpleName())
                .allowEmptyShould(true)
                .check(importedClasses);
        }
    }

    @Test
    @DisplayName("Les controllers d'Adapter.in doivent dépendre des ports-in (API application)")
    void controllersInAdapterIn_shouldDependOnInPorts() {
        ArchRule rule = classes()
            .that().resideInAPackage("..infrastructure..adapter.in..")
            .and().areAnnotatedWith(RestController.class)
            .or().haveSimpleNameEndingWith("Controller")
            .should().dependOnClassesThat().resideInAPackage("..application..port.in..")
            .as("Seuls les points d'entrée (controllers) doivent s'appuyer sur les ports-in");

        rule.allowEmptyShould(true).check(importedClasses);
    }

    @Test
    @DisplayName("Ports-in ne doivent avoir de dépendants que côté usecase, adapter.in ou config infra")
    void inPorts_shouldOnlyHaveDependentsInUsecaseOrAdapterInOrInfraConfig() {
        ArchRule rule = classes()
            .that().areInterfaces()
            .and().resideInAPackage("..application..port.in..")
            .should().onlyHaveDependentClassesThat()
            .resideInAnyPackage(
                "..application..usecase..",
                "..infrastructure..adapter.in..",
                "..infrastructure..config.."
            )
            .as("Les ports-in ne doivent être référencés que par les use cases, les adapters d'entrée, ou la config infra");

        rule.allowEmptyShould(true).check(importedClasses);
    }
}
