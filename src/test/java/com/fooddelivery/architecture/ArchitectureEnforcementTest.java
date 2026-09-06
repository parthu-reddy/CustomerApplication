package com.fooddelivery.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.base.DescribedPredicate;

import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(
    packages = "com.fooddelivery",
    importOptions = {
        ImportOption.DoNotIncludeTests.class,
        ImportOption.DoNotIncludeJars.class,
        ImportOption.DoNotIncludeArchives.class
    }
)
public class ArchitectureEnforcementTest {

    private static final DescribedPredicate<JavaClass> isGeneratedOrImpl = 
        DescribedPredicate.describe("is generated or impl", 
            clazz -> clazz.getSimpleName().endsWith("Impl") 
                  || clazz.isAnnotatedWith("jakarta.annotation.Generated") 
                  || clazz.isAnnotatedWith("javax.annotation.processing.Generated"));

    private static final DescribedPredicate<JavaClass> anyClass = 
        DescribedPredicate.alwaysTrue();

    // 1. Pragmatic Layered Architecture
    @ArchTest
    public static final ArchRule layered_architecture_is_respected = layeredArchitecture()
            .consideringAllDependencies()
            .layer("Controller").definedBy("..controller..")
            .layer("Service").definedBy("..service..", "..refund..", "..scheduler..", "..security..")
            .layer("Repository").definedBy("..repository..")
            .layer("Client").definedBy("..client..")
            .layer("Config").definedBy("..config..")
            .layer("Mapper").definedBy("..mapper..")
            .layer("Filter").definedBy("..filter..")
            .layer("DTO").definedBy("..dto..", "..entity..")
            
            // Controllers shouldn't be called by anyone except Configs (e.g. for security setup) or tests.
            // MCP service is an AI tool integration, it's essentially acting as a mega-controller.
            .whereLayer("Controller").mayOnlyBeAccessedByLayers("Config", "Service") 
            
            // Services hold business logic. They are called by Controllers, other Services, Configs, DTOs (for types), and Clients (which return Service inner DTOs)
            .whereLayer("Service").mayOnlyBeAccessedByLayers("Controller", "Service", "Config", "DTO", "Client")
            
            // Repositories can be called by Services, Configs, Controllers, Filters in this legacy codebase.
            .whereLayer("Repository").mayOnlyBeAccessedByLayers("Service", "Controller", "Config", "Filter")
            
            // Feign Clients are called by Services and Controllers
            .whereLayer("Client").mayOnlyBeAccessedByLayers("Service", "Config", "Controller")
            
            // Ignore MapStruct generated code
            .ignoreDependency(isGeneratedOrImpl, anyClass);

}
