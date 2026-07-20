---
id: SPEC-0015
title: Movement and security notifications
slug: notifications
status: AWAITING_SPEC_APPROVAL
risk: R2
profile: standard
modules: [notification, application, infra]
depends_on: [SPEC-0003, SPEC-0004, SPEC-0005, SPEC-0006, SPEC-0007]
relevant_adrs: []
reading_list:
  domain: ["Module map (notification)"]
  architecture: ["Events and integrations"]
planned_sprint: S3
planned_release: null
owner_approved_at: null
owner_approved_hash: null
---

# SPEC-0015 — Movement and security notifications

## Context
Every real banking app tells the customer, in real time, when money moves and when the
account is accessed — it is both UX and the first fraud alarm. Our notification module
exists in the domain but had no spec; market scan confirmed the gap.

## Scope
In-app notification center + e-mail (emulator) for: money in, money out, PIN lockout,
limit changes (SPEC-0013 plugs in at S6). Preferences screen.

## Out of scope
Push/SMS channels (web MVP: in-app + e-mail) · marketing messages · WhatsApp.

## Business rules
- **BR-1** — Consumed from the outbox (money events) with an idempotent consumer: an event
  produces exactly one notification even under redelivery.
- **BR-2** — Money-out and security notifications (PIN lockout, limit changes) cannot be
  disabled; money-in and yield notifications are preference-toggleable.
- **BR-3** — Every notification carries the receipt/entity link; opening it lands on the
  receipt or the relevant screen.
- **BR-4** — Delivery is best-effort and never blocks the money path: a notification
  failure is retried with backoff and logged; the posting is untouched.
- **BR-5** — Every limit adjustment (SPEC-0013) notifies immediately, stating the limit,
  old/new value and when it takes effect.

## Acceptance criteria
- [ ] Transfer out ⇒ sender gets money-out, recipient gets money-in (in-app + e-mail emulator)
- [ ] Redelivered event ⇒ single notification (consumer idempotency)
- [ ] Disabling money-in stops it; money-out toggle does not exist
- [ ] Notification click opens the receipt
- [ ] E-mail emulator `fail` scenario ⇒ retry with backoff; money path unaffected

## Edge cases
Burst of events (ordering by event time, not delivery time) · notification for an
anonymized customer (dropped silently, SPEC-0014).

## Open Questions
_None._

## Impact
Migrations: `notification`, `notification_preference` · Events: consumes money events ·
Contract: notification center endpoints · Screens: notification center, preferences ·
Emulator: e-mail (`deliver`, `fail`, `duplicate`)

## Decision log

Format: `DL-NNNN — YYYY-MM-DD — decision — decided by <owner|architecture|assumption>`

_No entries yet._
