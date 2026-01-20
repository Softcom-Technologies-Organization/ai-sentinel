package pro.softcom.aisentinel.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

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
                .importPackages("pro.softcom.aisentinel");
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
    @DisplayName("Adapter.in controllers must depend only on in-ports, not on implementations")
    void adapterIn_shouldDependOnlyOnInPorts_NotOnUsecaseImpl() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("..infrastructure..adapter.in..")
            .should().dependOnClassesThat().resideInAnyPackage("..application..usecase..")
            .as("Adapter.in must not know use case implementations");

        rule.allowEmptyShould(true).check(importedClasses);
    }

    @Test
    @DisplayName("Use cases must not depend on infrastructure")
    void usecases_shouldNotDependOnInfrastructure() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("..application..usecase..")
            .should().dependOnClassesThat().resideInAnyPackage("..infrastructure..")
            .as("Use case implementations must remain in the core (without infrastructure)");

        rule.allowEmptyShould(true).check(importedClasses);
    }

    @Test
    @DisplayName("In/out ports must not depend on Spring or infrastructure")
    void ports_shouldBeFrameworkAndInfraAgnostic() {
        ArchRule rule = noClasses()
            .that().resideInAnyPackage("..application..port.in..", "..application..port.out..")
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "..infrastructure..")
            .as("Ports must be agnostic (no Spring/Infrastructure)");

        rule.allowEmptyShould(true).check(importedClasses);
    }

    @Test
    @DisplayName("Out-ports must not be implemented in the application")
    void outPorts_shouldNotBeImplementedInApplication() {
        List<Class<?>> outPorts = findPortInterfaces(".port.out.");
        for (Class<?> port : outPorts) {
            noClasses()
                .that().resideInAPackage("..application..")
                .should().implement(port)
                .as("Out-ports must not be implemented in the application: " + port.getSimpleName())
                .allowEmptyShould(true)
                .check(importedClasses);
        }
    }

    @Test
    @DisplayName("Adapter.in controllers must depend on in-ports (application API)")
    void controllersInAdapterIn_shouldDependOnInPorts() {
        ArchRule rule = classes()
            .that().resideInAPackage("..infrastructure..adapter.in..")
            .and().areAnnotatedWith(RestController.class)
            .or().haveSimpleNameEndingWith("Controller")
            .should().dependOnClassesThat().resideInAPackage("..application..port.in..")
            .as("Only entry points (controllers) must rely on in-ports");

        rule.allowEmptyShould(true).check(importedClasses);
    }

    @Test
    @DisplayName("In-ports must only have dependents in use cases, adapter.in or infrastructure config")
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
            .as("In-ports must only be referenced by use cases, input adapters, or infrastructure config");

        rule.allowEmptyShould(true).check(importedClasses);
    }
}
