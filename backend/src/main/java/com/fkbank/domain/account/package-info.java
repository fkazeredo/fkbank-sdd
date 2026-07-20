/**
 * Account bounded context: the accounts customers hold, their statements and their receipts.
 *
 * <p>An account here is the product a person owns — a branch and a number they can read out
 * loud. Where its money is recorded is the accounting core's business, and this context reaches
 * that core only through the core's own entry point: it opens the customer's ledger account
 * when the account is opened, and asks for a balance when someone wants to see one. It never
 * reads or writes a balance itself.
 *
 * <p>This {@code package-info} carries the only Spring reference permitted anywhere under
 * {@code domain}: Spring Modulith runs with {@code detection-strategy=explicitly-annotated}, so
 * a bounded context must be annotated to be discovered at all.
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Account",
    allowedDependencies = {"domain.ledger", "domain.customer"})
package com.fkbank.domain.account;
