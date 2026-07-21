package com.fkbank.infra.configuration;

import com.fkbank.domain.account.CurrentAccountRepository;
import com.fkbank.domain.account.Statements;
import com.fkbank.domain.customer.CustomerRepository;
import com.fkbank.domain.ledger.Ledger;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.interceptor.NameMatchTransactionAttributeSource;
import org.springframework.transaction.interceptor.RuleBasedTransactionAttribute;
import org.springframework.transaction.interceptor.TransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

/**
 * Assembles the statement and receipt reads and gives them a read-only transaction.
 *
 * <p>{@code receiptOf} makes several related reads — the posting, both legs' chart-of-accounts
 * codes, whether it was reversed, the counterparty's customer record — that must all describe the
 * same instant; without a surrounding transaction a reversal recorded mid-lookup could leave the
 * receipt describing a posting that no longer matches its own status.
 */
@Configuration
class StatementConfig {

  @Bean
  Statements statements(
      CurrentAccountRepository accounts,
      CustomerRepository customers,
      Ledger ledger,
      PlatformTransactionManager transactionManager) {

    Statements statements = new Statements(accounts, customers, ledger);

    RuleBasedTransactionAttribute readOnly = new RuleBasedTransactionAttribute();
    readOnly.setReadOnly(true);

    TransactionAttributeSource attributes = readOnlyForEveryMethod(readOnly);

    TransactionInterceptor interceptor = new TransactionInterceptor();
    interceptor.setTransactionManager(transactionManager);
    interceptor.setTransactionAttributeSource(attributes);

    ProxyFactory factory = new ProxyFactory(statements);
    factory.setProxyTargetClass(true);
    factory.addAdvice(interceptor);
    return (Statements) factory.getProxy();
  }

  private static TransactionAttributeSource readOnlyForEveryMethod(
      RuleBasedTransactionAttribute readOnly) {
    NameMatchTransactionAttributeSource attributes = new NameMatchTransactionAttributeSource();
    attributes.addTransactionalMethod("*", readOnly);
    return attributes;
  }
}
