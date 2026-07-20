---
id: SPEC-0013
title: Limits engine
slug: limits-engine
status: AWAITING_SPEC_APPROVAL
risk: R3
profile: critical
modules: [limits, identity, transfer, pix, billpay, card]
depends_on: [SPEC-0002, SPEC-0007, SPEC-0008]
relevant_adrs: []
reading_list:
  domain: ["Cross-cutting rules (3)"]
  architecture: ["Backend", "Security"]
planned_sprint: "S6 (implementation)"
planned_release: null
owner_approved_at: null
owner_approved_hash: null
split_review_required: true
---

# SPEC-0013 — Limits engine

## Context
Limits are layered and user-controlled, mirroring Brazilian market practice: an initial
daytime tier from declared income + bureau score, a regulatory night cap on PIX, favorite
contacts with higher defaults, and user adjustments with an anti-coercion delay. Default
hierarchy (owner decision, 2026-07-15): **favorites > daytime > nighttime**.

## Scope
Limit evaluation service consulted by every money-moving route; "My limits" screen
(daytime, nighttime, favorites); favorite contacts management; monthly tier review job.

## Out of scope
**Device registration/fingerprinting — none.** This is a web application; there is no
device registry (owner decision, 2026-07-15). Also out: "street mode" (trusted-network
detection is native-app territory), behavioral fraud scoring, per-merchant card rules.

## Business rules
- **BR-1 (daytime default)** — At account opening, the daytime transactional limit comes
  from declared monthly income (SPEC-0002) + bureau score band: min(2× declared income,
  score-band cap). Tiers are reference data seeded by migration, not code.
- **BR-2 (nighttime default)** — PIX/transfer between 20:00 and 06:00: cap of $1,000 total
  in the window (regulatory default). The customer may shift the window start to 22:00.
- **BR-3 (favorites default)** — The customer registers favorite contacts; a favorite's
  default limit is **2× the customer's daytime limit** and applies in BOTH windows
  (favorites bypass the night cap up to their own limit).
- **BR-4 (user adjustment, anti-coercion)** — Daytime, nighttime and per-favorite limits
  are ALL user-adjustable: lowering takes effect immediately; **raising takes effect only
  after 24h**. Adding a favorite or raising a favorite's limit follows the same 24h delay.
  Every adjustment requires the PIN and generates a notification (SPEC-0015).
- **BR-5 (evolution)** — A monthly job reviews usage (months active, movement volume, no
  incidents) and may raise the daytime tier one step; it never lowers automatically. Every
  change is notified and audited.
- **BR-6 (enforcement)** — Every money-moving route consults the engine BEFORE the ledger;
  a blocked operation returns `422 LIMIT_EXCEEDED` naming which limit was hit; nothing is
  posted.

## Acceptance criteria
- [ ] Transfer above the daytime limit ⇒ LIMIT_EXCEEDED, ledger untouched
- [ ] PIX at 21:00 above $1,000 to a non-favorite ⇒ blocked; same value to a favorite
      (within its limit) ⇒ allowed; same value at 10:00 within daytime ⇒ allowed
- [ ] New favorite's default = 2× daytime limit, usable only after the 24h delay
      (clock-controlled test)
- [ ] Lowering any limit applies immediately; raising applies only after 24h
- [ ] Monthly review raises the tier per BR-5 and notifies; jqwik on window-sum accounting

## Edge cases
Operation exactly at the cap (allowed) · window boundary 19:59:59 vs 20:00:00 · pending
raise canceled by the customer (instant) · daily counters reset at the product timezone
midnight (UTC-3 fixed in MVP) · removing a favorite (instant, it lowers exposure).

## Open Questions
_None — rules sourced from market/regulatory practice; hierarchy set by the owner
(2026-07-15)._

## Impact
Migrations: `limit_tier`, `customer_limit`, `favorite_contact` · Events: `LimitChanged` ·
Contract: limits + favorites endpoints · Screens: My limits (day/night), favorites ·
Emulator: bureau (score band)

## Decision log

Format: `DL-NNNN — YYYY-MM-DD — decision — decided by <owner|architecture|assumption>`

- DL-0009 — 2026-07-15 — Limits hierarchy is favorites > daytime > nighttime; the night
  cap is BRL 1,000 from 20:00–06:00 with the start shiftable to 22:00; favorite default is
  2× daytime in both windows; raises take effect after 24 hours and reductions immediately;
  device registration is excluded for the web application — decided by owner.
