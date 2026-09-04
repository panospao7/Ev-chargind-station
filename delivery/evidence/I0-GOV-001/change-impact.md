# I0-GOV-001 Change Impact — DEC-AGENT-01 (Option B)

## Scope
Local-commit-only exception. No push/merge/rebase/reset/tag/deploy/prod change.
No business code, contracts, migrations, allocation, auth, or secrets change.

## Files
- `AGENTS.md` (+2): narrow exception paragraph in section 12.
- `docs/00_governance/01_decision_and_open_question_register_v1.0.md` (+10): section 16e DEC-AGENT-01 PROVISIONAL.
- `docs/08_delivery_and_ai_agents/03_ai_agent_rules_review_gates_v1.0.md` (+5/-1): section 7 bullet, section 9 qualifier, section 32 footnote.
- `opencode.json` (1 line): `git commit*` deny -> ask; all other git destructive ops remain deny.
- `delivery/tasks/I0-GOV-001.yaml` (new): L4 packet.

## Consistency
AGENTS.md, ARC-016 sections 7/9/32, and opencode.json agree: local commit gated, push/merge human-only, Gate G preserved.

## Verification
- `git status --porcelain=v1` + `git diff --stat`: 4 modified + 1 new packet, 17 insertions, 2 deletions — PASS
- `node -e JSON.parse(opencode.json)`: PASS
- Task YAML check: python NOT_RUN (no python in env); packet follows I0-DEL-001 schema by inspection
- Secret scan: no keys/tokens; only policy words `secrets`/`node_modules` in added prose — PASS
- `node_modules/` absent (stashed in stash@{0}); not added to git — PASS

## Risks
- opencode.json `ask` cannot enforce branch/allowedFiles scope; enforcement relies on agent pre-commit gate + human review.
- Stash@{0} contains node_modules/ + I0-DEL-001 dirt; do not pop on this branch.
- DEC-AGENT-01 PROVISIONAL; recommend pilot on one L0/L1 task before APPROVED.

## Rollback
Human: `git restore` listed files or `git checkout -- .`; delete untracked packet/evidence. No migration, no data change.

## Fix round 1 (2026-09-04)
- SEC-001 (MAJOR) resolved via owner-selected option (a): staging human-only, sweep-commit flags (-a/--all/--include/--patch on unrelated paths) prohibited — recorded in AGENTS.md §12, GOV-001 §16e, ARC-016 §7.
- SEC-002 (MINOR) resolved: ARC-016 §32 footnote marked illustrative; DEC-AGENT-01 register row (GOV-001 §16e) authoritative.
- SEC-003 (MINOR) resolved: ARC-016 §32 retained-prohibition list completed (reset, tag, deploy added).
- SEC-004..006 (NOTE): not actioned — optional, out of minimal fix scope.
- SEC-001 negative test satisfied textually per option (a): sweep-commit path explicitly prohibited by decision text in all three artifacts.

## Broadening round 2 (2026-09-04)
- Owner direction in chat: workflow more autonomous; DEC-AGENT-01 broadened from Option B (local commits only, staging human-only) to autonomous staging, task-branch creation, local commits, and pushing task branches. Merge to main / protected branches stays human-only.
- `opencode.json`: `edit` allow (with .env/pem/key/secrets denies), `task` allow; `git add/commit/push/branch/checkout/switch` allowed with explicit denies for sweeps (`add -A/--all`, `commit -a/--all`), push-to-main, force/all/mirror push, `branch -D`, checkout path restore, stash drop; `stash pop/apply` ask; merge/rebase/reset/restore/clean/tag deny retained; read-only git inspection allowed (rev-parse, merge-base, fetch, remote -v).
- AGENTS.md §12, GOV-001 §16e, ARC-016 §7/§9/§32 synchronized to the broadened scope.
- Task packet updated: objective, non-goals, human decisions, AC-04, negative cases, assumptions; open question "ask vs deny for git commit" resolved (allow).
- Residual caveat: prefix permissions cannot scope push by branch; push-to-main guarded by deny patterns + AGENTS.md rule; server-side branch protection on `main` recommended.
