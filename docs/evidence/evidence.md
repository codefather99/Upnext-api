# Evidence: the tests fail when the protection is removed

A test that passes both with and without its protection proves nothing. For each rule below, the protection was removed on a throwaway branch, the matching test was run and had to **fail**, then the change was discarded and the full suite run again.

- **Project:** up-next (Team 2, side project) · **Date:** 30 October 2026 · **Branch used:** `scratch/protection-removed` (deleted afterwards)
- **Tests run against:** a real Postgres (Testcontainers) over real HTTP, not mocks.
- **How to reproduce any experiment:** every command is in section 4.

## 1. Summary

| # | Rule | Protection removed | Test that must fail | Failure seen | Evidence |
|---|---|---|---|---|---|
| A | One vote per user per request, even for simultaneous taps | The `PRIMARY KEY (request_id, user_id)` on `vote` in `V1__upnext_core.sql` | `eight_simultaneous_votes_from_one_user_record_exactly_one_vote` | **8 votes recorded instead of 1** | Screenshot A |
| B | Two admins moving one card: one wins, one sees the true card | The version check: `AND version = ?` in `Sql.MOVE` | `two_admins_moving_one_card_at_once_one_wins_and_the_other_sees_the_true_card` | **Both admins "won" (2 instead of 1)** | Screenshot B |
| C | Only admins may move cards or open the board | The `.hasRole("ADMIN")` rule in `SecurityConfig` | `a_plain_user_cannot_move_cards_or_open_the_board_but_an_admin_can` | **Not captured yet** (see section 5) | Pending |

## 2. The experiments

### A. Removing "one vote per user"
**Protection:** the primary key on `vote (request_id, user_id)`; the service simply inserts with `ON CONFLICT DO NOTHING` and lets the database refuse a second vote.

**Change made:** deleted the `PRIMARY KEY (request_id, user_id)` line (and the comma before it) from the `vote` table in `V1__upnext_core.sql`.

**Result:** the test failed at `ApiRulesTest.java:151` with
`one user, one vote, however many taps: {... "voteCount":8 ...} ==> expected: <1> but was: <8>`
Eight simultaneous taps by one user recorded **eight** votes. That is exactly the bug the rule exists to prevent, so the test detects the loss of the protection. Maven reported `Tests run: 1, Failures: 1`.

![Screenshot A: eight votes recorded without the primary key](evidence/A-votes-without-primary-key.png)

### B. Removing "two admins, one card"
**Protection:** a compare-and-set update, `UPDATE request SET status = ?, version = version + 1 ... WHERE id = ? AND version = ?`. If another admin moved the card first, no row matches and the service answers `409 STALE_VERSION` with the true card.

**Change made:** replaced `AND version = ?` in `Sql.MOVE` with `AND ?::int IS NOT NULL` (the same number of parameters, with the check switched off).

**Result:** the test failed at `ApiRulesTest.java:187` with
`exactly one admin may win ==> expected: <1> but was: <2>`
Both admins were told their move succeeded, so one move was silently overwritten. Maven reported `Tests run: 1, Failures: 1`.

![Screenshot B: both admins win without the version check](evidence/B-two-admins-without-version-check.png)

### C. Removing "only admins move cards"
Pending: see section 5.

## 3. Recovery, and what passes with the protections in place

After each experiment the change was discarded with `git checkout -- .`, then the scratch branch was deleted and the full suite was run on the real branch.

- Full suite on the real branch: **BUILD SUCCESS** (`.\mvnw.cmd -B verify`). Paste the final lines here: `Tests run: __, Failures: 0, Errors: 0, Skipped: 0` and the date and time.

Rule tests in `ApiRulesTest` (all run over real HTTP against a real database):

| Test | What it proves |
|---|---|
| `visitors_can_browse_and_read_the_roadmap_but_not_participate` | Visitors can read, but cannot submit, vote or open the board (401). The seed provides at least 40 requests. |
| `a_plain_user_cannot_move_cards_or_open_the_board_but_an_admin_can` | **Access rule** enforced in the service: a USER gets 403, an ADMIN gets 200. |
| `duplicate_email_in_another_letter_case_is_refused_and_login_works` | The same email in a different letter case is refused (`EMAIL_TAKEN`); wrong password is 401. |
| `invalid_input_is_a_validation_error_with_field_messages` | Bad input returns 422 with field messages the screen can show. |
| `voting_twice_and_withdrawing_twice_are_both_harmless` | Voting is idempotent in both directions. |
| `eight_simultaneous_votes_from_one_user_record_exactly_one_vote` | **Rule 1** (experiment A). |
| `voting_is_refused_once_a_request_is_completed` | `VOTING_CLOSED` is returned, giving the interface a real refusal to revert on. |
| `two_admins_moving_one_card_at_once_one_wins_and_the_other_sees_the_true_card` | **Rule 2** (experiment B). The loser gets `STALE_VERSION` carrying the true card. |
| `roadmap_board_and_history_agree_after_a_move` | The roadmap is a query over status; history records who moved the card; a no-op adds no history row. |
| `filters_and_the_seed_shapes_work` | Filters, empty results, bad sort values and the awkward seed cases behave. |

`JwtServiceTest` also checks that forged, expired and wrongly signed tokens are rejected.

**Honest status:** only experiments A and B (and, once captured, C) have been shown to fail without their protection. The other tests are written to check behaviour, but have not each been put through a removal experiment.

## 4. Commands to reproduce

```powershell
git status                                   # must be clean first
git checkout -b scratch/protection-removed

# A: delete the PRIMARY KEY line (and the comma above it) in V1__upnext_core.sql, then:
.\mvnw.cmd -B "-Dtest=ApiRulesTest#eight_simultaneous_votes_from_one_user_record_exactly_one_vote" test
git checkout -- .

# B: in Sql.java change "AND version = ?" to "AND ?::int IS NOT NULL" in MOVE, then:
.\mvnw.cmd -B "-Dtest=ApiRulesTest#two_admins_moving_one_card_at_once_one_wins_and_the_other_sees_the_true_card" test
git checkout -- .

# C: in SecurityConfig change .hasRole("ADMIN") to .hasAnyRole("USER", "ADMIN") on the status rule, then:
.\mvnw.cmd -B "-Dtest=ApiRulesTest#a_plain_user_cannot_move_cards_or_open_the_board_but_an_admin_can" test
git checkout -- .

git checkout <your real branch>
git branch -D scratch/protection-removed
.\mvnw.cmd -B verify
```

## 5. Still to capture
- **Experiment C (access rule).** The screenshot named for it contained the experiment B output, so the role-check failure has not yet been captured. Repeat step C above, expect `expected: <403> but was: <200>`, and add the screenshot as `evidence/C-user-can-move-cards-without-role-rule.png`.
- **The final green run** with its counts, in section 3.

## 6. Database-level corroboration
Separately from the HTTP tests, the same two rules were run directly against Postgres with `db/verify_upnext_sql.py` (it extracts the SQL from `Sql.java` and races it):

| Race | With protection | Protection removed |
|---|---|---|
| 8 simultaneous votes by one user | 1 vote | 8 votes |
| 2 admins moving one card at once | one row updated, one not | both rows updated |
| 6 simultaneous signups with one email in mixed case | 1 account | not tested |

This matches what the HTTP tests showed (8 votes; 2 winners), from a different angle.
