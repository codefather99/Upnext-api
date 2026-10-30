# Contract decisions (up-next)

Every change to `openapi/upnext-openapi.yaml` gets one dated line: what changed, why, and which two engineers agreed it.

| Date | Change | Why | Agreed by |
|---|---|---|---|
| 2026-10-24 | v0.1.0 initial contract: 14 operations (meta, signup, login, me, browse, create, vote PUT/DELETE, move card, status history, board, roadmap, tags) | Starting point so Ehimare can build against the mock | Henry Emefo, Ehimare J. Okosun *(confirm when Ehimare approves)* |
| 2026-10-24 | Voting is `PUT`/`DELETE /requests/{id}/vote`, not a single toggle | Two quick taps must record one vote, and voting again must remove it. A toggle cannot do both. | Henry Emefo, Ehimare J. Okosun *(confirm)* |
| 2026-10-24 | Added `409 VOTING_CLOSED` for COMPLETED requests | Gives a real, demonstrable refusal for the optimistic-update revert | Henry Emefo, Ehimare J. Okosun *(confirm; also confirm with Laja)* |
