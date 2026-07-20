package com.fkbank.acceptance.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import com.fkbank.domain.ledger.Account;
import com.fkbank.domain.ledger.Ledger;
import com.fkbank.domain.ledger.Money;
import com.fkbank.domain.ledger.PostingId;
import com.fkbank.testsupport.LedgerFixture;
import com.fkbank.testsupport.LedgerIntegrationTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.Advisor;
import org.springframework.aop.framework.Advised;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.jpa.EntityManagerFactoryUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionExecution;
import org.springframework.transaction.TransactionExecutionListener;
import org.springframework.transaction.interceptor.TransactionAttribute;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * What transaction each ledger operation actually gets, observed rather than configured.
 *
 * <p>The ledger carries no framework annotations: its transaction is attached from the outside by
 * a name-matching attribute source. A name-matching source that failed to match would leave the
 * money path with no transaction at all while the application still started, so the mapping is
 * worth proving rather than reading. Two independent observations are made per operation — the
 * attribute the framework resolves for the method, and the isolation PostgreSQL itself reports
 * from inside the transaction that was actually begun.
 */
@DisplayName("Ledger transaction semantics")
class LedgerTransactionSemanticsIT extends LedgerIntegrationTest {

  @Autowired private Ledger ledger;
  @Autowired private LedgerFixture fixture;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private EntityManagerFactory entityManagerFactory;

  private final List<BegunTransaction> begun = new CopyOnWriteArrayList<>();
  private final TransactionExecutionListener recorder = new Recorder();

  @BeforeEach
  void armTheRecorder() {
    ((AbstractPlatformTransactionManager) transactionManager).addListener(recorder);
    begun.clear();
  }

  @AfterEach
  void disarmTheRecorder() {
    AbstractPlatformTransactionManager manager =
        (AbstractPlatformTransactionManager) transactionManager;
    List<TransactionExecutionListener> remaining =
        new ArrayList<>(manager.getTransactionExecutionListeners());
    remaining.remove(recorder);
    manager.setTransactionExecutionListeners(remaining);
  }

  // ----------------------------------------------- the attribute the framework actually resolves

  @Test
  @DisplayName("every public ledger operation resolves to a real transaction attribute")
  void everyOperationIsTransactional() {
    for (Method method : publicLedgerMethods()) {
      assertThat(attributeFor(method))
          .as(
              "%s resolves to no transaction attribute: the money path would run untransacted and"
                  + " every row lock would be released statement by statement",
              method.getName())
          .isNotNull();
    }
  }

  @Test
  @DisplayName("the movement methods keep the database default and stay writable")
  void movementsKeepTheDatabaseDefault() {
    for (String name : List.of("record", "reverse", "openAccount")) {
      TransactionAttribute attribute = attributeFor(methodNamed(name));

      assertThat(attribute.getIsolationLevel())
          .as("%s must not have been dragged up with the audit", name)
          .isEqualTo(TransactionDefinition.ISOLATION_DEFAULT);
      assertThat(attribute.isReadOnly()).as("%s writes", name).isFalse();
      assertThat(attribute.getPropagationBehavior())
          .isEqualTo(TransactionDefinition.PROPAGATION_REQUIRED);
    }
  }

  @Test
  @DisplayName("the audit resolves to a read-only repeatable read")
  void theAuditIsRaised() {
    TransactionAttribute attribute = attributeFor(methodNamed("trialBalance"));

    assertThat(attribute.getIsolationLevel())
        .isEqualTo(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    assertThat(attribute.isReadOnly()).isTrue();
  }

  // ------------------------------------------------------ what the database reports from inside

  @Test
  @DisplayName("a movement really begins a transaction, at the database default")
  void aMovementBeginsAReadCommittedTransaction() {
    Account from = fixture.customerAccountHolding("40.00");
    Account to = fixture.emptyCustomerAccount();
    begun.clear();

    ledger.record(from.id(), to.id(), Money.of("5.00"));

    BegunTransaction transaction = onlyTransactionFor("record");
    assertThat(transaction.databaseIsolation())
        .as("a movement holds SELECT ... FOR UPDATE for its whole length at read committed")
        .isEqualTo("read committed");
    assertThat(transaction.readOnly()).isFalse();
  }

  @Test
  @DisplayName("a reversal really begins a transaction, at the database default")
  void aReversalBeginsAReadCommittedTransaction() {
    Account from = fixture.customerAccountHolding("40.00");
    Account to = fixture.emptyCustomerAccount();
    PostingId original = ledger.record(from.id(), to.id(), Money.of("5.00")).id();
    begun.clear();

    ledger.reverse(original);

    BegunTransaction transaction = onlyTransactionFor("reverse");
    assertThat(transaction.databaseIsolation()).isEqualTo("read committed");
    assertThat(transaction.readOnly()).isFalse();
  }

  @Test
  @DisplayName("the audit really runs at repeatable read, read-only, as PostgreSQL sees it")
  void theAuditRunsAtRepeatableRead() {
    begun.clear();

    ledger.trialBalance();

    BegunTransaction transaction = onlyTransactionFor("trialBalance");
    assertThat(transaction.databaseIsolation())
        .as("a configured level means nothing until the engine confirms it")
        .isEqualTo("repeatable read");
    assertThat(transaction.readOnly()).isTrue();
  }

  @Test
  @DisplayName("the read-only audit still returns a complete answer")
  void theReadOnlyAuditStillAnswers() {
    Account from = fixture.customerAccountHolding("40.00");
    Account to = fixture.emptyCustomerAccount();
    ledger.record(from.id(), to.id(), Money.of("7.50"));

    assertThat(ledger.trialBalance().driftedAccounts())
        .as("read-only must not have silenced the audit's own reads")
        .doesNotContain(from.id(), to.id());
    assertThat(ledger.balanceOf(to.id())).isEqualTo(Money.of("7.50"));
  }

  @Test
  @DisplayName("a movement immediately after the audit is not left read-only")
  void theConnectionIsNotLeftReadOnly() {
    ledger.trialBalance();

    Account from = fixture.customerAccountHolding("20.00");
    Account to = fixture.emptyCustomerAccount();
    ledger.record(from.id(), to.id(), Money.of("3.00"));

    assertThat(ledger.balanceOf(to.id())).isEqualTo(Money.of("3.00"));
  }

  // ------------------------------------------------------- the audit keeps its own transaction

  @Test
  @DisplayName("called inside an existing transaction the audit still gets its own snapshot")
  void theAuditKeepsItsSnapshotInsideAnOuterTransaction() {
    TransactionTemplate outer = new TransactionTemplate(transactionManager);
    begun.clear();

    outer.execute(
        status -> {
          ledger.trialBalance();
          return null;
        });

    BegunTransaction audit = onlyTransactionFor("trialBalance");

    assertThat(audit.databaseIsolation())
        .as(
            "isolation belongs to the transaction, not the method: joining the caller's"
                + " transaction would hand the audit that transaction's level instead of the"
                + " snapshot it relies on, precisely inside the batch or job most likely to"
                + " need it")
        .isEqualTo("repeatable read");
    assertThat(audit.readOnly()).isTrue();

    assertThat(attributeFor(methodNamed("trialBalance")).getPropagationBehavior())
        .as("the audit demands a transaction of its own rather than joining one")
        .isEqualTo(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  // ------------------------------------------------------------------------------------ plumbing

  private BegunTransaction onlyTransactionFor(String operation) {
    List<BegunTransaction> matching =
        begun.stream().filter(transaction -> transaction.operation().equals(operation)).toList();
    assertThat(matching).as("no transaction was begun for %s at all", operation).hasSize(1);
    return matching.get(0);
  }

  private static List<Method> publicLedgerMethods() {
    return List.of(Ledger.class.getDeclaredMethods()).stream()
        .filter(method -> Modifier.isPublic(method.getModifiers()))
        .filter(method -> !method.isSynthetic())
        .toList();
  }

  private static Method methodNamed(String name) {
    return publicLedgerMethods().stream()
        .filter(method -> method.getName().equals(name))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Ledger has no public method named " + name));
  }

  private TransactionAttribute attributeFor(Method method) {
    return interceptorOn(ledger)
        .getTransactionAttributeSource()
        .getTransactionAttribute(method, Ledger.class);
  }

  private static TransactionInterceptor interceptorOn(Object proxy) {
    Object current = proxy;
    while (current instanceof Advised advised) {
      for (Advisor advisor : advised.getAdvisors()) {
        if (advisor.getAdvice() instanceof TransactionInterceptor interceptor) {
          return interceptor;
        }
      }
      try {
        current = advised.getTargetSource().getTarget();
      } catch (Exception unreachable) {
        break;
      }
    }
    throw new AssertionError(
        "the ledger bean carries no transaction interceptor: nothing on the money path is"
            + " transactional");
  }

  private String databaseIsolationNow() {
    EntityManager entityManager =
        EntityManagerFactoryUtils.getTransactionalEntityManager(entityManagerFactory);
    if (entityManager == null) {
      return "no entity manager bound";
    }
    return String.valueOf(
        entityManager
            .createNativeQuery("select current_setting('transaction_isolation')")
            .getSingleResult());
  }

  /** One transaction the manager actually began, with what the engine reported from inside it. */
  private record BegunTransaction(String operation, boolean readOnly, String databaseIsolation) {}

  private final class Recorder implements TransactionExecutionListener {

    @Override
    public void afterBegin(TransactionExecution transaction, Throwable failure) {
      if (failure != null) {
        return;
      }
      begun.add(
          new BegunTransaction(
              operationOf(TransactionSynchronizationManager.getCurrentTransactionName()),
              TransactionSynchronizationManager.isCurrentTransactionReadOnly(),
              probeDatabaseIsolation()));
    }

    private String operationOf(String transactionName) {
      if (transactionName == null) {
        return "<unnamed>";
      }
      int lastDot = transactionName.lastIndexOf('.');
      return lastDot < 0 ? transactionName : transactionName.substring(lastDot + 1);
    }

    private String probeDatabaseIsolation() {
      try {
        return databaseIsolationNow();
      } catch (RuntimeException probeFailed) {
        return "probe failed: " + probeFailed;
      }
    }
  }
}
