package kz.vtipo.vfs;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class ArchitectureTest {

    @Test
    void apiLayerShouldNotDependOnCoreOrModel() {
        var classes = new ClassFileImporter().importPackages("kz.vtipo.vfs");
        ArchRule rule = noClasses()
            .that().resideInAPackage("..api..")
            .should().dependOnClassesThat().resideInAnyPackage("..core..", "..model..", "..proxy..");
        rule.check(classes);
    }
}
