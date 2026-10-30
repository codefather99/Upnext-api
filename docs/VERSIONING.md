# Version control and versioning rules (up-next)

One page so anyone, including a marker, can see how change is controlled.

## 1. Branches
- `main` is always deployable. Nothing is committed to it directly after the first commit: everything arrives by pull request.
- Work branches are short-lived and named `<type>/<short-name>`: `feat/vote-endpoints`, `fix/stale-version-message`, `docs/readme`, `chore/ci`.
- Squash-merge pull requests so `main` reads as one clear commit per change. Delete the branch after merging.
- Protect `main` on GitHub: *Settings, Branches, Add rule*: require a pull request, require the `build-test` check to pass.

## 2. Commits (Conventional Commits)
Format: `type(scope): what changed`, present tense, under 72 characters.
`feat(votes): make voting idempotent with PUT and DELETE` · `fix(board): return the true card on STALE_VERSION` · `test(votes): eight simultaneous votes record one` · `docs(adr): record the duplicate-merge rule` · `chore(ci): add manual database rollback`.
Types: `feat`, `fix`, `docs`, `test`, `refactor`, `chore`. One logical change per commit. Never commit secrets, `target/` or `.idea/`.

## 3. Release versions (SemVer)
`MAJOR.MINOR.PATCH`. While below 1.0, a new feature bumps MINOR and a fix bumps PATCH.
To release:
1. Move the **Unreleased** notes in `CHANGELOG.md` under a new `## [x.y.z] - date` heading.
2. Set `upnext.version=x.y.z` in `application.properties` (it is shown by `GET /api/v1/meta`).
3. Commit: `chore(release): v0.1.0`, merge by pull request.
4. Tag the merge commit on `main` and push the tag:
   ```
   git checkout main && git pull
   git tag -a v0.1.0 -m "up-next v0.1.0: MVP"
   git push origin v0.1.0
   ```
5. On GitHub: *Releases, Draft a new release*, choose the tag, paste the changelog section.

## 4. API versioning
- The path carries the major version: `/api/v1/...`.
- **Additive changes stay in v1** (a new optional field, a new endpoint, a new error code the screen can ignore).
- **Breaking changes need `/api/v2`** and a new contract file `openapi/upnext-openapi-v2.yaml`, with v1 kept working until the interface has moved.
- Every contract change adds a dated line to `docs/contract-decisions.md` naming two engineers.

## 5. Database versioning
- Migrations are numbered files in `src/main/resources/db/migration`: `V1`-`V99` data and rules, `V100`-`V199` auth. The next ones are `V2__...`, `V101__...`.
- **Never edit a migration that has been applied anywhere.** Add a new one. (Flyway checksums will refuse the edit, which is the point.)
- Every migration has a down script `db/down/V<n>__down.sql`. A migration without one is not merged.

## 6. Rolling back
| What went wrong | What to do |
|---|---|
| A bad commit reached `main` but not the database | `git revert <sha>`, open a pull request, merge. The pipeline redeploys. |
| The deployed service is broken now | In Render: *Deploys*, pick the last good deploy, *Rollback*. Then fix forward by pull request. |
| A bad migration | Run the **rollback-db** workflow in GitHub Actions with the version number (highest first, for example `100` then `1`), revert the commit, redeploy. |
| Need an earlier release | `git checkout v0.1.0` (tags are permanent) and deploy that commit. |

## 7. Hotfix
Branch from `main` as `fix/...`, one small change, pull request, merge, bump PATCH, tag, release.
