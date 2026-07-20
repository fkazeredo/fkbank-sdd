/**
 * Customer bounded context: registration data, consents and the data-protection lifecycle.
 *
 * <p>It owns the rule that one person holds one account: the CPF and e-mail address that
 * identify someone are unique here, and every other context asks this one rather than keeping
 * its own copy of the answer. It depends on no other bounded context, which is what lets the
 * uniqueness rule be stated in one place.
 *
 * <p>Declared income lives here too, and deliberately does not use the accounting core's money
 * type — it is what a person says they earn, not money the bank holds.
 *
 * <p>This {@code package-info} carries the only Spring reference permitted anywhere under
 * {@code domain}: Spring Modulith runs with {@code detection-strategy=explicitly-annotated}, so
 * a bounded context must be annotated to be discovered at all.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Customer")
package com.fkbank.domain.customer;
