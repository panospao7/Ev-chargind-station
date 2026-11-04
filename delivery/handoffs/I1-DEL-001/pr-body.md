## Purpose

Make the OpenCode control plane enforceable before its first live use, now that delivery moves from ZCode to OpenCode. Also correct the I1-IAM-002 record, which hid an unmerged branch reporting that task as complete.

**These permission rules are guardrails against agent mistakes, not a security boundary.** The owner accepted that model on 2026-11-04. Branch protection and this review remain the real controls.

## Authority

Requirements: none (delivery control plane)
Invariants: AGENTS.md §4 secrets rule, §12 and DEC-AGENT-01
Architecture decisions: none
Contracts: none

## Changes

Files/modules changed:
- `opencode.json` and `.opencode/agents/*.md` (frontmatter only): exact-command allows, one shared deny list applied last, shell pinned to bash, 15 global agent names disabled or denied
- `.opencode/commands/`: new `/ship-task`; review fingerprint; `/verify-task` and `/close-task` dead ends fixed; invalid states removed
- `scripts/delivery/check-agent-permissions.mjs`: new permission regression check
- `delivery/`: I1-DEL-001 packet, handoffs and evidence; corrected I1-IAM-002 status, with RECON-01 and the re-run evidence

Database changes: none
Contract changes: none
Security/privacy changes: agent permissions only

## Tests

Commands run:
- `node scripts/delivery/check-agent-permissions.mjs`: PASS 5187/5187, with and without the owner's global config
- `node scripts/delivery/validate.mjs`, `node scripts/delivery/self-test.mjs`: PASS
- `secretlint --maskSecrets` on all committed files: PASS
- Live OpenCode session in a disposable repository: uses Git Bash, not WSL, and denies `git checkout -- .`

Tests not run: full end-to-end use of the commands, and any further live session. `npm run contracts:verify` and Maven suites were not run because no contract or product code changed.

The check evaluates command text, not what OpenCode's parser extracts, so a pass does not prove live behaviour.

## Risk

Impact level: L3

Known limitations, listed in `delivery/evidence/I1-DEL-001/agent-permissions.md`:
- Accepted by the owner: an agent can overwrite files with `(git status) > file`, and run a program through an environment variable.
- "Always" on a permission prompt can override denies, and `--auto` approves anything not denied. `delivery/README.md` forbids both.
- Agents that run tests run repository code using the owner's credentials.

Rollback/forward-fix path: revert this pull request. No data or product code is affected.

**Reviewer attention:**
- Rounds 1 and 2 of independent review failed, and their findings drove these changes. After round 3, a narrow verifier confirmed the listed corrections. No new full security review ran after that, deliberately.
- The orchestrator exception for its own records is in the commands and README only. DEC-AGENT-01 and AGENTS.md §12 need a separate task to record it.
- `delivery/status.yaml` review matrices for older `VERIFIED` tasks are still unreliable. This PR does not repair them.

**Do not merge expecting I1-IAM-002 to be done.** `origin/task/i1-iam-002-ci` passed a re-run (BFF 78/78 including 12/12 exchange tests, Discovery 27/27, Station Operations 36/36) on JDK 21 only, unreviewed and never in CI, and it may reject real Keycloak tokens. See `delivery/evidence/I1-IAM-002/ci-line-rerun.md`.

## Agent contribution

Producing agent: Claude Code (Opus 5.5), orchestrator and coder roles in one session, disclosed
Reviewing agent: separate Claude Code subagents on the same model, not a different model family
Human decisions required: merge. Open items are listed in `delivery/handoffs/I1-DEL-001/13-orchestrator.md`.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
