package com.fkbank.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * The hexagonal structure as executable law.
 *
 * <p>Turns the three-root package layout, the direction of dependencies, and the
 * ubiquitous-language naming rule from prose a reviewer must remember into a build failure
 * nobody can merge past.
 */
@AnalyzeClasses(
    packages = "com.fkbank",
    importOptions = {ImportOption.DoNotIncludeTests.class, ImportOption.DoNotIncludeJars.class})
class ArchitectureTest {

  private static final String ROOT = "com.fkbank";
  private static final Set<String> ALLOWED_ROOTS = Set.of("domain", "application", "infra");

  /**
   * Exactly three packages sit directly below {@code com.fkbank}. A fourth root package is how
   * a modular monolith quietly becomes a layered ball of mud.
   */
  @Test
  void onlyThreeRootPackagesExistBelowComFkbank() {
    JavaClasses imported =
        new com.tngtech.archunit.core.importer.ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(ROOT);

    Set<String> roots =
        imported.stream()
            .map(JavaClass::getPackageName)
            .filter(name -> name.startsWith(ROOT + "."))
            .map(name -> name.substring(ROOT.length() + 1))
            .map(name -> name.contains(".") ? name.substring(0, name.indexOf('.')) : name)
            .collect(Collectors.toCollection(TreeSet::new));

    assertThat(roots)
        .as("only domain, application and infra may sit directly below %s", ROOT)
        .isSubsetOf(ALLOWED_ROOTS);
  }

  /**
   * The domain is framework-free.
   *
   * <p>{@code package-info} is excluded deliberately: Spring Modulith runs with
   * {@code detection-strategy=explicitly-annotated}, so a bounded context must carry the
   * {@code @ApplicationModule} annotation to be discovered at all. That annotation is module
   * metadata, not banking behavior, and it is the only Spring reference the domain may carry.
   */
  @ArchTest
  static final ArchRule domainDoesNotImportSpring =
      noClasses()
          .that()
          .resideInAPackage("..domain..")
          .and()
          .doNotHaveSimpleName("package-info")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("org.springframework..")
          .because("the domain holds banking behavior and must not depend on a framework");

  /** Persistence is an outbound concern; aggregates carry invariants, not ORM annotations. */
  @ArchTest
  static final ArchRule domainDoesNotImportJakartaPersistence =
      noClasses()
          .that()
          .resideInAPackage("..domain..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("jakarta.persistence..")
          .because("the domain model is persistence-ignorant");

  /** Dependencies point inward: the core never knows about its adapters. */
  @ArchTest
  static final ArchRule domainDependsOnlyOnDomain =
      noClasses()
          .that()
          .resideInAPackage("..com.fkbank.domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("com.fkbank.application..", "com.fkbank.infra..")
          .because("domain may depend only on domain");

  /** Driving adapters never reach into driven adapters. */
  @ArchTest
  static final ArchRule applicationDoesNotDependOnInfra =
      noClasses()
          .that()
          .resideInAPackage("com.fkbank.application..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("com.fkbank.infra..")
          .because("application is a delivery adapter and must not know the technical side");

  /** Driven adapters never reach into driving adapters. */
  @ArchTest
  static final ArchRule infraDoesNotDependOnApplication =
      noClasses()
          .that()
          .resideInAPackage("com.fkbank.infra..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("com.fkbank.application..")
          .because("infra implements ports and must not know the delivery mechanism");

  /**
   * Bounded contexts are flat: {@code domain.identity.Something}, never
   * {@code domain.identity.model.Something}.
   *
   * <p>Grouping subpackages (model/service/usecase/port/event) reintroduce technical layering
   * inside a context and scatter one concept across four folders.
   */
  @ArchTest
  static final ArchRule boundedContextsAreFlat =
      classes()
          .that()
          .resideInAPackage("com.fkbank.domain..")
          .should(beDirectlyInsideABoundedContext())
          .because("bounded contexts carry no grouping subpackages");

  /**
   * No interactor or use-case classes: behavior lives on the model, and an application service
   * appears only for real orchestration, named for the domain activity.
   */
  @ArchTest
  static final ArchRule domainCarriesUbiquitousLanguageNames =
      noClasses()
          .that()
          .resideInAPackage("com.fkbank.domain..")
          .should()
          .haveSimpleNameEndingWith("UseCase")
          .orShould()
          .haveSimpleNameEndingWith("Interactor")
          .orShould()
          .haveSimpleNameEndingWith("Service")
          .orShould()
          .haveSimpleNameEndingWith("Impl")
          .because("domain types carry ubiquitous-language names, not technical role names");

  /**
   * Balances belong to the ledger and to nothing else.
   *
   * <p>The moment a second module can read or write a balance directly, "can this account afford
   * it" has two implementations, and the day they disagree is the day money is created. Other
   * modules ask the ledger for a movement; only its own persistence adapter touches the stored
   * figure.
   *
   * <p>Bean wiring is exempt because it names the port in a constructor argument without ever
   * calling it: assembling the ledger is not using it. The exemption is the composition package
   * alone, so a module that wants a balance still has to go through the ledger.
   */
  @ArchTest
  static final ArchRule onlyTheLedgerTouchesBalances =
      noClasses()
          .that()
          .resideOutsideOfPackages(
              "com.fkbank.domain.ledger..",
              "com.fkbank.infra.persistence.ledger..",
              "com.fkbank.infra.configuration..")
          .should()
          .dependOnClassesThat()
          .haveNameMatching(".*\\.Balance(Entity|Repository|JpaRepository)?")
          .because("no module outside the ledger may read or write a balance");

  private static ArchCondition<JavaClass> beDirectlyInsideABoundedContext() {
    return new ArchCondition<>("reside directly in com.fkbank.domain.<bounded-context>") {
      @Override
      public void check(JavaClass item, ConditionEvents events) {
        String packageName = item.getPackageName();
        String relative = packageName.substring("com.fkbank.domain".length());
        // Exactly one segment: ".identity" is a bounded context. "" is the domain root, where a
        // class belongs to no context at all, and ".identity.model" is a grouping subpackage.
        // Both are violations - the first would otherwise be a hole big enough for a shared
        // "common domain" package to grow in.
        long depth = relative.chars().filter(character -> character == '.').count();
        if (depth != 1) {
          String message =
              relative.isEmpty()
                  ? ("%s sits directly in com.fkbank.domain; every domain type belongs to a"
                          + " bounded context")
                      .formatted(item.getName())
                  : "%s sits in a grouping subpackage (%s); bounded contexts are flat"
                      .formatted(item.getName(), packageName);
          events.add(SimpleConditionEvent.violated(item, message));
        }
      }
    };
  }
}
