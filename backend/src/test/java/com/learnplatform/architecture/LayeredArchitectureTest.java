package com.learnplatform.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.learnplatform", importOptions = ImportOption.DoNotIncludeTests.class)
class LayeredArchitectureTest {

    @ArchTest
    static final ArchRule controllers_must_not_access_persistence_directly = noClasses()
            .that().resideInAPackage("..controller..")
            .should().dependOnClassesThat().resideInAnyPackage("..mapper..", "..entity..")
            .because("Controller 只负责 HTTP 边界，数据访问和实体转换必须经过 Service");

    @ArchTest
    static final ArchRule transaction_boundaries_must_not_live_in_controllers = methods()
            .that().areDeclaredInClassesThat().resideInAPackage("..controller..")
            .should().notBeAnnotatedWith(Transactional.class)
            .because("事务边界属于 Service 层");

    @ArchTest
    static final ArchRule lower_layers_must_not_depend_on_controllers = noClasses()
            .that().resideInAnyPackage("..service..", "..mapper..", "..entity..")
            .should().dependOnClassesThat().resideInAPackage("..controller..")
            .because("底层代码不能反向依赖 HTTP 入口层");

    @ArchTest
    static final ArchRule configuration_packages_must_not_contain_business_services = noClasses()
            .that().resideInAPackage("..config..")
            .should().beAnnotatedWith(Service.class)
            .because("config 只承载配置，业务与安全服务必须进入对应职责包");
}
