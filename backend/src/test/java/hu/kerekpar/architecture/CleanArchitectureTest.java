package hu.kerekpar.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "hu.kerekpar", importOptions = ImportOption.DoNotIncludeTests.class)
class CleanArchitectureTest {

    @ArchTest
    static final ArchRule domainDependsOnNothingOutside = noClasses().that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..application..", "..adapter..", "..config..", "org.springframework..", "com.fasterxml..",
                    "jakarta.persistence..");

    @ArchTest
    static final ArchRule applicationIsFrameworkAndAdapterFree = noClasses().that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..adapter..", "..config..", "org.springframework..", "jakarta..", "com.fasterxml..");

    @ArchTest
    static final ArchRule webAdapterUsesOnlyInboundPorts = noClasses().that().resideInAPackage("..adapter.in..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..adapter.out..", "..application.usecase..", "..application.port.out..");

    @ArchTest
    static final ArchRule persistenceAdapterDoesNotKnowWeb = noClasses().that().resideInAPackage("..adapter.out..")
            .should().dependOnClassesThat().resideInAnyPackage("..adapter.in..", "..application.usecase..");

    @ArchTest
    static final ArchRule jpaStaysInsideThePersistenceAdapter = noClasses()
            .that().resideOutsideOfPackage("..adapter.out.persistence..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "jakarta.persistence..", "org.springframework.data..", "org.hibernate..");
}
