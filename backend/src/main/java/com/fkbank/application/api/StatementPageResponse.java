package com.fkbank.application.api;

import com.fkbank.domain.account.StatementCursor;
import com.fkbank.domain.account.StatementPage;
import java.util.List;

/** One page of the statement, and the cursor to ask for the next one, when there is one. */
public record StatementPageResponse(List<StatementLineResponse> lines, String nextCursor) {

  static StatementPageResponse of(StatementPage page) {
    return new StatementPageResponse(
        page.lines().stream().map(StatementLineResponse::of).toList(),
        page.nextCursor().map(StatementCursor::encode).orElse(null));
  }
}
