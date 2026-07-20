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
import org.springframework.transaction.interceptor.MatchAlwaysTransactionAttributeSource;
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
    interceptor.setTransactionAttributeSource(new MatchAlwaysTransactionAttributeSource());

    ProxyFactory factory = new ProxyFactory(ledger);
    factory.setProxyTargetClass(true);
    factory.addAdvice(interceptor);
    return (Ledger) factory.getProxy();
  }
}
