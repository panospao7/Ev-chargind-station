---
role: coder
taskId: I1-DEL-001
previousState: FIX_REQUIRED
resultingState: SELF_VERIFIED
baselineCommit: 811b81ed39ca954217cf785f5f9c3135f14b2263
impactLevel: L3
date: 2026-11-04
---

# I1-DEL-001 — Coder handoff, round 3

Responds to `06-reviewer-round2.md` and `07-security-reviewer-round2.md`. Work remains
uncommitted on `task/i1-del-001-opencode-controls`.

## Position

Round 2 showed that OpenCode matches patterns extracted by a shell parser, and that some
statements produce none. Text rules cannot close those routes. This round closes what
configuration and command text can close, corrects the evidence, and returns the rest to
the owner as a risk decision. It does not attempt a further pattern-based fix for them.

## Findings addressed

| Findings | Change |
|---|---|
| R2-03 | Push and switch deny `$`, backtick and `{` forms; check tests the parsed forms |
| R2-04 | `git grep` abbreviated and clustered `-O` forms denied |
| R2-07 | External-path `git diff`, abbreviated read options, `secrets/`, more key types denied |
| R2-08 | Maven `-Djvm`, `-DargLine`, `-Dmaven.ext`, `-l`, `-gs`, `site`, test-skip forms denied; npm `--prefix` and similar denied |
| R2-09 | `*~*` and `*::*` edit denies for writers |
| R2-10 | Nested control-file denies restated last; more paths prompt |
| R2-11, G-09 | Root-deny drift check for each project agent; more workflow expectations; header states limits |
| R2-12 | Evidence rewritten; limitations and who can resolve them are explicit |
| R2-13 | Scanner allows removed, so they prompt; hazardous `make` forms denied |
| R2-14 | `--maskSecrets`; a second config is denied |
| R2-15, R2-16 | More denies; `git show-*` and `git diff-*` prompt; remainder disclosed in the evidence after the round 3 verification |
| N-01 | Three more legacy names disabled |
| G-01, G-08 | Fingerprint uses a saved new-file list, so it survives staging and commit; failure means `BLOCKED`; shell recorded |
| G-02 | `/verify-task` and `/close-task` use the same pull-request-head rule |
| G-03 | `/verify-task` writes `verification.md`; `/close-task` requires it |
| G-04 | `/close-task` stops on the `status.yaml` conflict; scope checks exclude orchestrator records |
| G-05, R2-06 | "Once, never always" in `/ship-task`, `delivery/README.md`, and evidence |
| G-06 | Search is case-insensitive and covers hyphenated, camelCase and JSON forms; the verification found `privateKey` and `apiKey` missed, since corrected |
| G-07, G-10, R2-17 | State list and `/ship-task` wording corrected; `--body-file`; existing-PR case |
| G-11 | Evidence lists accepted false positives; bare `git branch` forms re-allowed |
| G-12 | I1-IAM-002 `branch` set to null; branch protection recorded as present |
| R2-02 (partial) | `/ship-task` stops on any non-sample git hook. The route itself stays open |

## A defect this round caught

A quote in a new orchestrator rule broke its YAML. OpenCode then silently ignored the project
agent and used the global one with the same name. The permission check caught this at once. An
agent file with invalid YAML fails without warning, so run the check after any edit.

## Not fixed; needs the owner

- **R2-01:** pin the shell. `"shell": "bash"` is in OpenCode's schema, but on this machine `bash`
  can also resolve to WSL's bash, so it was not pinned without a live test. `delivery/README.md`
  says to start OpenCode from Git Bash in the meantime.
- **R2-02, R2-05:** accept the risk or add an OS-level control. Neither can be fixed by config.
- **F-07, G-04:** how `delivery/status.yaml` relates to `allowedFiles`. `/ship-task` and `/close-task`
  stop on 18 of 20 parseable packets until decided.
- **Commit mechanism and `--auto`.** Prompts are the current default.
- **First-round N-4:** this packet waives the tester, while `/review-task` invokes it by default.

## Commands and results

| Command | Result |
|---|---|
| `node scripts/delivery/check-agent-permissions.mjs` (global config) | PASS: 5083/5083, 17 agents |
| Same with empty `XDG_CONFIG_HOME` | PASS: 5083/5083, 17 agents |
| `node scripts/delivery/validate.mjs` | PASS |
| `node scripts/delivery/self-test.mjs` | PASS: 8 passed, 0 failed |
| `secretlint --maskSecrets` on 33 changed files | PASS: exit 0 |
| Fingerprint commands, run twice in Git Bash | PASS: identical results |
| Generic secret search on fake files in a temporary repository | PASS: found 5 of 5 secret forms; excluded the clean file |
| Live session, end-to-end commands, CI | NOT_RUN |

## Acceptance criteria

| AC | Status |
|---|---|
| AC-01 | PASS for recorded forms at string level on bash. Not true for PowerShell (R2-01) |
| AC-02 | PASS in both configurations |
| AC-03 | PASS for the edit tool. Grouped redirects can still overwrite files (R2-02) |
| AC-04 | PASS for literal forms |
| AC-05 | Text corrected; end-to-end NOT_RUN |
| AC-06, AC-07 | PASS by text review and validator |

## Recommended next step

A narrow verification that these fixes landed, then owner decisions. Do not start another open-ended
attack round: the remaining routes are known and config cannot close them.

## Corrections after the round 3 verification

`09-verifier-round3.md` returned 1 MAJOR and 7 MINOR. All were corrected on the same worktree, except that the test-skip denies still need an owner decision (see `11-orchestrator.md`):
the generic secret search now covers `privateKey` and `apiKey`; the plumbing prompts sit before the
shared denies; `git grep` prompts rather than carrying a broad deny; `/ship-task` also stops on a new
file added after review; the remaining limitations are disclosed in the evidence.

The permission check now reports PASS 5187/5187 in both configurations. The validator and self-test
PASS, and secretlint exits 0 on all changed files.
