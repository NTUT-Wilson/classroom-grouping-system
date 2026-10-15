package com.classroomgrouping;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

class ArchitectureTest {
    @Test
    void featureModulesRespectOwnership() {
        var classes = new ClassFileImporter().withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.classroomgrouping");
        noClasses().that().resideInAPackage("..identity..")
                .should().dependOnClassesThat().resideInAnyPackage("..courses..", "..grouping..")
                .allowEmptyShould(true).check(classes);
        noClasses().that().resideInAPackage("..courses..")
                .should().dependOnClassesThat().resideInAPackage("..grouping..")
                .allowEmptyShould(true).check(classes);
        noClasses().that().resideOutsideOfPackage("..identity..")
                .should().dependOnClassesThat().resideInAPackage("..identity.internal..")
                .allowEmptyShould(true).check(classes);
        noClasses().that().resideOutsideOfPackage("..courses..")
                .should().dependOnClassesThat().resideInAPackage("..courses.internal..")
                .allowEmptyShould(true).check(classes);
        noClasses().that().resideOutsideOfPackage("..grouping..")
                .should().dependOnClassesThat().resideInAPackage("..grouping.internal..")
                .allowEmptyShould(true).check(classes);
        noClasses().that().resideInAPackage("..grouping.results..")
                .should().dependOnClassesThat().resideInAPackage("..grouping.internal..")
                .allowEmptyShould(true).check(classes);
        noClasses().that().resideInAPackage("..grouping.results..")
                .should().dependOnClassesThat().resideInAnyPackage("org.springframework.jdbc..",
                        "org.springframework.data..", "jakarta.persistence..")
                .allowEmptyShould(true).check(classes);
        noClasses().that().resideInAPackage("..grouping.results..")
                .should().dependOnClassesThat().haveFullyQualifiedName("com.classroomgrouping.grouping.api.DueWork")
                .allowEmptyShould(true).check(classes);
        noClasses().that().resideInAnyPackage("..identity..", "..courses..", "..grouping..")
                .should().dependOnClassesThat().resideInAPackage("..platform..")
                .allowEmptyShould(true).check(classes);
        slices().matching("com.classroomgrouping.(*)..").should().beFreeOfCycles().check(classes);
    }
}
