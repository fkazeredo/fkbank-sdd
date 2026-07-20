/**
 * Onboarding bounded context: the sign-up flow and the credit-bureau check behind it.
 *
 * <p>It owns the sequence by which a stranger becomes a customer, and it is the only context
 * that talks to the bureau. What an approval produces belongs to other contexts — the person to
 * customer, their sign-in details to identity, their account to account — so this context
 * coordinates them rather than duplicating what they know.
 *
 * <p>An application is its own record precisely because none of those things exist yet while it
 * is being checked. That is what lets an applicant wait, come back, and find the same
 * application rather than starting another one.
 *
 * <p>This {@code package-info} carries the only Spring reference permitted anywhere under
 * {@code domain}: Spring Modulith runs with {@code detection-strategy=explicitly-annotated}, so
 * a bounded context must be annotated to be discovered at all.
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Onboarding",
    allowedDependencies = {"domain.customer", "domain.identity", "domain.account"})
package com.fkbank.domain.onboarding;
