# up-next: a public feedback board and roadmap

> Demonstration project (fictional product, fictional users). Team 2, TeSA Africa capstone, side project.
> Not a real product. No real customers, no real data, no claim of any relationship with a real organisation.

## The problem
Feedback arrives by email, tickets and chat. Good ideas get lost, duplicates split the demand, and users never learn whether they were heard.

## Users
Visitors browse and read the roadmap. Users submit and vote. Admins (the product team) move cards.

## What it does
Sign up and sign in, submit requests, browse by tag, status and keyword, vote once per request, admin board, public roadmap. 14 operations, see `openapi/upnext-openapi.yaml`.

## The two rules, and how they are protected
| Rule | Protected by | Test |
|---|---|---|
| One vote per user per request, even for simultaneous taps | Primary key `(request_id, user_id)` | `eight_simultaneous_votes_from_one_user_record_exactly_one_vote` |
| Two admins moving one card: one wins, one sees the true card | Version check (compare and set) | `two_admins_moving_one_card_at_once_one_wins...` |
Access rules (admin only for moving cards and the board) are enforced in the service: `a_plain_user_cannot_move_cards...`.

## Stack
Spring Boot 4 (Java 21), Postgres, Flyway, GitHub Actions, Render and Neon. Interface: React and TypeScript (see its own repository).

## Run it locally
```
docker compose up -d                 # Postgres on port 5436
./mvnw spring-boot:run               # API on http://localhost:8081
./mvnw -B verify                     # tests (needs Docker)
npx @stoplight/prism-cli mock openapi/upnext-openapi.yaml -p 4011   # mock server
```
Environment variables: `DB_URL`, `DB_USER`, `DB_PASSWORD`, `UPNEXT_JWT_SECRET`, `UPNEXT_CORS_ORIGINS`, `UPNEXT_DEMO_PASSWORD`, `UPNEXT_SEED`.

## Demonstration logins (fictional)
`admin@upnext.demo` (ADMIN) and `ada@upnext.demo`, `tunde@upnext.demo` and six more (USER). Password: `Demo-Pass-1`. Change it with `UPNEXT_DEMO_PASSWORD`.

## Deployed addresses
- Service: `<fill in>`
- Interface: `<fill in>`

## Free tier terms I checked
Render and Neon: read each provider's own pricing page and write here what you found and the date. Things to look for: idle sleep, monthly hour allowance, database expiry, inactive project pausing, number of free projects per account.

## Known limitations
Duplicate merging is designed (ADR-U3) but not built. No comments, no rate limiting, no live updates. The demo seed gives five requests no votes.

## Versioning
See `docs/VERSIONING.md` and `CHANGELOG.md`. Current version: 0.1.0.
