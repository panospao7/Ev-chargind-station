# I1-IAM-002 — Independent re-run of the `-ci` line

- **Task ID:** I1-IAM-002 (recorded under I1-DEL-001)
- **Commit tested:** 431392f6df301f3afef018be3fbd144f50ee593d (tip of `origin/task/i1-iam-002-ci`)
- **Baseline of that line:** 589a36f7e863a6c0fdabbc10560839ac874ff7ec
- **Date:** 2026-11-04, 09:58 to 10:03 UTC
- **Environment:** Windows 11, Git Bash, Temurin JDK 21.0.8, Docker 29.8.2; `git archive` copy of the commit outside the repository
- **Run by:** a Claude Code session that did not write this line. It is not an independent review.

## Results

| Command | Result |
|---|---|
| `./mvnw -B -pl apps/bff -am test "-Dmaven.compiler.release=21"` | PASS: BFF 78/78, test-support 16/16 |
| Included: `TokenExchangeIntegrationTests` | PASS: 12/12, in the Testcontainers Keycloak run |
| `./mvnw -B -pl services/discovery-insights-service -am test "-Dmaven.compiler.release=21"` | PASS: 27/27, test-support 16/16 |
| `./mvnw -B -pl services/station-operations-service -am test "-Dmaven.compiler.release=21"` | PASS: 36/36, test-support 16/16 |

These match the counts in that branch's `02-coder.md`.

## Limits

- **JDK:** compiled at release 21, as the branch's own run did. The project baseline is JDK 25, and no CI has run on this line.
- **Not merged:** the line is 13 commits behind `main` and has not been ported. Merging may conflict with the Admin-REST work on `main`.
- **`contracts:verify`:** NOT_RUN on this commit.
- **Reviews:** tester, general, contract and security reviews have not run on this line.

## Must resolve first

A pass does not settle the following, because the suites mint their own tokens with header `typ=Bearer`:

- Per that branch's handoff, Keycloak emits header `typ=JWT`, while both services validate with a header-based `JwtTypeValidator("Bearer")`. As described, this would reject real Keycloak tokens. The handoff rated this MINOR. It should be reassessed and given an end-to-end test using a real exchanged token against a service.
- AC-05's standalone cases were not run: duplicate `jti`, excessive lifetime, wrong assertion audience.

No secrets or token values are included in this file.
