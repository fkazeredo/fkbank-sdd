/**
 * Identity bounded context: credentials, authorization policy, transaction PIN and lockout
 * (docs/DOMAIN.md §Module map). It depends on no other bounded context.
 *
 * <p>This {@code package-info} carries the only Spring reference permitted anywhere under
 * {@code domain}: Spring Modulith runs with {@code detection-strategy=explicitly-annotated},
 * so a bounded context must be annotated to be discovered at all. The ArchUnit rule that bans
 * Spring from the domain therefore targets domain <em>types</em> and excludes
 * {@code package-info}, which carries module metadata rather than banking behavior.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Identity")
package com.fkbank.domain.identity;
