/**
 * Ledger bounded context: the double-entry accounting core.
 *
 * <p>This is the only place in the product allowed to read or write a balance. Every money rail
 * — deposits, transfers, PIX, card, boxes, loans — commands this module and holds no balance
 * logic of its own, so there is exactly one implementation of "can this account afford it" to
 * get right.
 *
 * <p>A posting is immutable once written. Corrections are made by recording a contra-posting
 * that references the original, never by updating or deleting history: an audit trail that can
 * be edited is not an audit trail.
 *
 * <p>This {@code package-info} carries the only Spring reference permitted anywhere under
 * {@code domain}: Spring Modulith runs with {@code detection-strategy=explicitly-annotated}, so
 * a bounded context must be annotated to be discovered at all.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Ledger")
package com.fkbank.domain.ledger;
