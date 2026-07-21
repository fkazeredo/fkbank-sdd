package com.fkbank.domain.account;

import com.fkbank.domain.ledger.PostingLine;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** One page of a statement: its lines, newest first, and where the next page starts. */
public record StatementPage(List<PostingLine> lines, Optional<StatementCursor> nextCursor) {

  public StatementPage {
    Objects.requireNonNull(lines, "lines must not be null");
    Objects.requireNonNull(nextCursor, "next cursor must not be null");
    lines = List.copyOf(lines);
  }
}
