# AI log (up-next)

Tools used: Claude (design, code drafts, tests, documents). Keep this file honest: the brief reads a log with no errors as a log where no checking took place.
Edit the entries below into your own words and add new ones as you go.

## What I asked for, and what I did with it
| Asked for | Accepted | Rejected or changed |
|---|---|---|
| OpenAPI contract, schema and rules for up-next | Contract, schema, vote and version rules | Added `VOTING_CLOSED` only after confirming with the supervisor |
| Backend code (services, controllers, security) | Structure and SQL after running it | Anything I could not explain; I read every file before committing |
| Tests for each rule | The HTTP rule tests | I removed each protection once to prove its test fails |

## Cases where the AI output was wrong and I caught it
| # | What it claimed | How I checked | What was actually true | What I did |
|---|---|---|---|---|
| 1 | The OpenAPI file was valid | Ran an OpenAPI validator | An unquoted colon in a description broke the YAML | Quoted the line, revalidated; keep the validator in the pipeline |
| 2 | The demo seed had "one request with no votes" | Ran a quick check of the vote formula | Five requests get no votes (every ninth) | Kept it (the rule needs at least one) and documented it |
| 3 | (ClinicFlow) The fake ports config registered cleanly | The Spring context test failed | The same class was registered twice, so Spring found two beans | Registered it once |
| 4 | (ClinicFlow) The CORS check passed on my deployed service | Looked at the response headers | I was testing someone else's server: the URL was a placeholder | Used the real URL and checked the request id header on every environment |
