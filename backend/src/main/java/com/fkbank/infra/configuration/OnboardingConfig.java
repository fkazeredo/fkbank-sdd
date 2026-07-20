package com.fkbank.infra.configuration;

import com.fkbank.domain.account.AccountEventPublisher;
import com.fkbank.domain.account.AccountNumbers;
import com.fkbank.domain.account.CurrentAccountRepository;
import com.fkbank.domain.account.CurrentAccounts;
import com.fkbank.domain.customer.CustomerRepository;
import com.fkbank.domain.identity.CredentialRepository;
import com.fkbank.domain.identity.PasswordHasher;
import com.fkbank.domain.ledger.Ledger;
import com.fkbank.domain.onboarding.BureauCheck;
import com.fkbank.domain.onboarding.OnboardingEventPublisher;
import com.fkbank.domain.onboarding.OnboardingOutcome;
import com.fkbank.domain.onboarding.OnboardingRepository;
import com.fkbank.domain.onboarding.SignUp;
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
 * Assembles account opening and the sign-up flow, and gives each the transaction it needs.
 *
 * <p>The domain classes carry no framework annotations, so their transaction boundaries are
 * decided here — and they are not the same boundary, which is the whole reason this
 * configuration is worth reading.
 */
@Configuration
class AccountOpeningConfig {

  /**
   * Opening an account has to be all or nothing.
   *
   * <p>An account number without a place in the chart of accounts is an account nobody can pay
   * into; a ledger account without the row that names its owner is a balance nobody owns.
   * Reading a summary joins whatever transaction is already running, or opens a read-only one.
   */
  @Bean
  CurrentAccounts currentAccounts(
      CurrentAccountRepository accounts,
      AccountNumbers numbers,
      AccountEventPublisher events,
      Ledger ledger,
      Clock clock,
      PlatformTransactionManager transactionManager) {

    CurrentAccounts currentAccounts =
        new CurrentAccounts(accounts, numbers, events, ledger, clock);

    RuleBasedTransactionAttribute read = new RuleBasedTransactionAttribute();
    read.setReadOnly(true);

    NameMatchTransactionAttributeSource attributes = new NameMatchTransactionAttributeSource();
    attributes.addTransactionalMethod("summaryOf", read);
    attributes.addTransactionalMethod("*", new RuleBasedTransactionAttribute());

    return (CurrentAccounts) transactional(currentAccounts, attributes, transactionManager);
  }

  /**
   * Settling an application is one transaction; everything an approval creates commits together
   * or not at all.
   */
  @Bean
  OnboardingOutcome onboardingOutcome(
      OnboardingRepository onboardings,
      CustomerRepository customers,
      CredentialRepository credentials,
      CurrentAccounts accounts,
      OnboardingEventPublisher events,
      Clock clock,
      PlatformTransactionManager transactionManager) {

    OnboardingOutcome outcome =
        new OnboardingOutcome(onboardings, customers, credentials, accounts, events, clock);

    NameMatchTransactionAttributeSource attributes = new NameMatchTransactionAttributeSource();
    attributes.addTransactionalMethod("*", new RuleBasedTransactionAttribute());

    return (OnboardingOutcome) transactional(outcome, attributes, transactionManager);
  }

  /**
   * Submitting deliberately runs outside a transaction.
   *
   * <p>It waits on the credit bureau, and a transaction held open across a call to somebody
   * else's server keeps a database connection busy for as long as that server takes to answer.
   * Under any load worth the name, that exhausts the pool. The two things that must be atomic —
   * writing the application, and settling it — each get their own transaction from the
   * collaborators above, and the wait happens between them rather than inside one.
   *
   * <p>Reading a status is a single query and needs no boundary of its own.
   */
  @Bean
  SignUp signUp(
      OnboardingRepository onboardings,
      CustomerRepository customers,
      PasswordHasher passwordHasher,
      BureauCheck bureau,
      OnboardingOutcome outcome,
      Clock clock,
      PlatformTransactionManager transactionManager) {

    SignUp signUp = new SignUp(onboardings, customers, passwordHasher, bureau, outcome, clock);

    RuleBasedTransactionAttribute none = new RuleBasedTransactionAttribute();
    none.setPropagationBehavior(TransactionDefinition.PROPAGATION_NOT_SUPPORTED);

    NameMatchTransactionAttributeSource attributes = new NameMatchTransactionAttributeSource();
    attributes.addTransactionalMethod("submit", none);
    attributes.addTransactionalMethod("*", new RuleBasedTransactionAttribute());

    return (SignUp) transactional(signUp, attributes, transactionManager);
  }

  private static Object transactional(
      Object target,
      TransactionAttributeSource attributes,
      PlatformTransactionManager transactionManager) {

    TransactionInterceptor interceptor = new TransactionInterceptor();
    interceptor.setTransactionManager(transactionManager);
    interceptor.setTransactionAttributeSource(attributes);

    ProxyFactory factory = new ProxyFactory(target);
    factory.setProxyTargetClass(true);
    factory.addAdvice(interceptor);
    return factory.getProxy();
  }
}
