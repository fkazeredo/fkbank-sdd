package com.fkbank.domain.account;

/**
 * Hands out the next account number.
 *
 * <p>A port because the guarantee it makes — that no two callers ever receive the same number,
 * however concurrent they are — is one the store provides and application code cannot. Counting
 * existing accounts and adding one is the version of this that quietly issues duplicates.
 */
public interface AccountNumbers {

  long next();
}
