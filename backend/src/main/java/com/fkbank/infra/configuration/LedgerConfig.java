package com.fkbank.infra.configuration;

import com.fkbank.domain.ledger.AccountRepository;
import com.fkbank.domain.ledger.BalanceRepository;
import com.fkbank.domain.ledger.Ledger;
import com.fkbank.domain.ledger.LedgerEventPublisher;
import com.fkbank.domain.ledger.PostingRepository;
import java.time.Clock;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.interceptor.NameMatchTransactionAttributeSource;
import org.springframework.transaction.interceptor.RuleBasedTransactionAttribute;
import org.springframework.transaction.interceptor.TransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

/**
 * Assembles the ledger and gives it a transaction.
 *
 * <p>The ledger itself holds no framework annotations: it is banking behaviour, and a domain class
 * that has to know it is running inside Spring is a domain class that cannot be reasoned about or
 * unit-tested without one. The transaction is therefore wrapped around it here.
 *
 * <p>The boundary is not decoration. A posting locks two balances, reads them, checks them and
 * writes them; without a surrounding transaction the locks would be released as each statement
 * finished and two concurrent postings could both conclude that the money is there.
 */
@Configuration
class LedgerConfig {

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }

  @Bean
  Ledger ledger(
      AccountRepository accounts,
      PostingRepository postings,
      BalanceRepository balances,
      LedgerEventPublisher events,
      Clock clock,
      PlatformTransactionManager transactionManager) {

    Ledger ledger = new Ledger(accounts, postings, balances, events, clock);

    TransactionInterceptor interceptor = new TransactionInterceptor();
    interceptor.setTransactionManager(transactionManager);
    interceptor.setTransactionAttributeSource(ledgerTransactionAttributes());

    ProxyFactory factory = new ProxyFactory(ledger);
    factory.setProxyTargetClass(true);
    factory.addAdvice(interceptor);
    return (Ledger) factory.getProxy();
  }

  /**
   * One transaction per operation, and a stricter one for the audit.
   *
   * <p>Movements run at the database default. They serialize on the row locks they take, so a
   * stronger isolation level would buy nothing and cost contention on the hottest path in the
   * product.
   *
   * <p>The trial balance is different: it reads the postings, then the balances, then the two
   * totals, and compares figures that must describe the same instant. At read-committed each of
   * those statements sees whatever had committed by the time it ran, so a movement landing
   * midway through makes the audit report drift on an account that is perfectly fine — and an
   * audit that cries wolf is one people learn to ignore. A repeatable read gives all four
   * statements one snapshot.
   */
  private static TransactionAttributeSource ledgerTransactionAttributes() {
    RuleBasedTransactionAttribute audit = new RuleBasedTransactionAttribute();
    audit.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    audit.setReadOnly(true);

    NameMatchTransactionAttributeSource attributes = new NameMatchTransactionAttributeSource();
    attributes.addTransactionalMethod("trialBalance", audit);
    attributes.addTransactionalMethod("*", new RuleBasedTransactionAttribute());
    return attributes;
  }
}
