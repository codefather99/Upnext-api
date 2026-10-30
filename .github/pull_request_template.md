## What and why
<!-- One or two sentences. Link the issue or decision record if there is one. -->

## Type
- [ ] feat  - [ ] fix  - [ ] docs  - [ ] chore  - [ ] refactor  - [ ] test

## Checklist
- [ ] `./mvnw -B verify` passes locally
- [ ] If the API changed: `openapi/upnext-openapi.yaml` updated **and** a dated line added to `docs/contract-decisions.md`
- [ ] If the schema changed: a **new** migration file (never edit an applied one) **and** its `db/down` script
- [ ] A rule I touched has a test that fails when the rule is removed
- [ ] `CHANGELOG.md` updated under "Unreleased"
- [ ] Anything simulated is labelled; no secrets committed
