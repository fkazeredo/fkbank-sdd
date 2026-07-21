# Test books (docs/tests/)

Human-executable manual test cases, one book per slice: `TB-<id>.md` — preconditions,
numbered steps, expected result per step (market-standard format). Written by QA during
`/qa`; executed by the operator when independent QA is not required (normally R0/R1) or as a
final check before merge. Test books are created with the slices that need them.

Books live here for as long as their slice does; they are updated, not replaced, when a later
pass changes the answer. A book that records a finding as open must be brought up to date when
that finding closes — a stale book is worse than none, because it is read as current.

Ownership follows the cycle. QA writes and owns a book while it has a cycle left; once those
cycles are spent the work has returned to the main agent, which updates the book itself and says
in the book which sections it wrote (`.claude/rules/qa-ownership.md` §Bidirectional boundary).
What the main agent never does is restate a QA verdict as its own.

| Book | Slice | Standing verdict |
|---|---|---|
| `TB-0018.md` | SPEC-0018 — walking skeleton | PASS_WITH_OBSERVATIONS |
| `TB-0016.md` | SPEC-0016 — observability baseline | QA_VERIFIED (after one rework cycle) |
| `TB-0001.md` | SPEC-0001 — ledger core | PASS_WITH_OBSERVATIONS (run 2 of 2); observations closed post-review by the main agent |
| `TB-0003.md` | SPEC-0003 — statement and receipts | QA_OBSERVATIONS (run 1 of 2) |
