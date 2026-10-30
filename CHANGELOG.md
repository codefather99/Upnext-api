# Changelog

All notable changes to up-next. Format: [Keep a Changelog](https://keepachangelog.com), versions follow [Semantic Versioning](https://semver.org).
Every release has a git tag (`vX.Y.Z`) and a section below. Put work in progress under **Unreleased** and move it on release.

## [Unreleased]

## [0.1.0] - 2026-10-30
### Added
- Contract-first API: 14 operations in `openapi/upnext-openapi.yaml` (validated), Prism mock on port 4011.
- Accounts: sign up, sign in, roles USER and ADMIN, enforced in the service.
- Requests: submit, browse with tag, status and keyword filters, Most Upvoted and Newest sorting.
- Voting: `PUT`/`DELETE /requests/{id}/vote`, idempotent, one vote per user per request (database primary key).
- Admin board: move cards with a version check (compare and set), status history with who and when.
- Public roadmap computed from request status.
- Demonstration seed: 44 requests across four statuses, awkward cases included.
- Pipeline: build and test on every push, deploy hook on `main`, post-deploy smoke check, manual database rollback workflow.
### Known limitations
- Merging duplicate requests is designed (ADR-U3) but not built.
- Comments, rate limiting and live updates are not built.
