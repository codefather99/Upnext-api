# Decision records (up-next)

Each record has: context, decision, rejected alternatives, cost accepted, and the observation that would reverse it. Evidence is from `db/verify_upnext_sql.py` (real Postgres) and `ApiRulesTest` (real HTTP).

## ADR-U1: Voting is `PUT` and `DELETE`, not a toggle
**Context.** The brief wants "voting again removes the vote" and also "two taps in quick succession record one vote".
**Decision.** `PUT /requests/{id}/vote` means "I vote" and `DELETE` means "I do not vote". Both are idempotent. The database primary key `(request_id, user_id)` holds the rule; the service just inserts with `ON CONFLICT DO NOTHING`.
**Rejected.** A single `POST /vote` toggle: two taps add then remove, and it is not idempotent. A check-then-insert in Java: two requests both see "no vote" and both insert.
**Cost.** The screen must know `viewerHasVoted`. A stale screen sends the wrong call, which is harmless because both calls are idempotent.
**Evidence.** 8 simultaneous votes by one user: 1 vote with the key, 8 without it.
**Reverse if** a client appears that cannot track state, or users report votes "not sticking" because of stale screens.

## ADR-U2: Moving a card uses a version check (compare and set)
**Context.** Two admins can drag the same card at the same moment. One must win and the other must see the card where it truly is.
**Decision.** `UPDATE request SET status = ?, version = version + 1 WHERE id = ? AND version = ?`. Zero rows updated means someone else moved it first, so the service answers `409 STALE_VERSION` carrying the true card.
**Rejected.** Last write wins (silently loses an admin's move). A row lock held while a person drags a card (blocks others for as long as a human takes).
**Cost.** The losing admin must look again. A history row is written only for real moves.
**Evidence.** Two simultaneous moves with the same version: one row updated, one not. Without the version clause both report success.
**Reverse if** admins hit conflicts often enough that they ask for per-column ordering or live updates.

## ADR-U3: Duplicate requests (the design problem with no clean answer)
**Context.** Without AI detection, duplicates split the votes.
**Decision (rule for the MVP, built later).** An admin merges a duplicate into a target: the duplicate is hidden (`duplicate_of` set, excluded from browse, board and roadmap) and its voters are **copied to the target once per user** with `INSERT ... SELECT ... ON CONFLICT DO NOTHING`, so someone who voted on both counts once. The duplicate's own votes stay for audit. **Not built yet; declared as a known limitation.**
**Rejected.** Auto-detection (later phase). Deleting the duplicate (loses history). Adding the counts together (double counts people who voted on both).
**Cost.** The merge is not undone by the product. The duplicate's author is not told.
**Reverse if** admins merge wrongly often enough to need an "unmerge" action.
