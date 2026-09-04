---
description: Diagnose and minimally fix CI, Maven, Spring Boot, Flyway, Angular, lint, or test failures.
agent: ev-ci-debugger
subtask: true
---

Diagnose this CI/build/test failure:

```text
$ARGUMENTS
```

This command supports ARC-016 Gate F (CI verification): it reproduces and
root-causes failing checks so required CI gates can pass. It never declares CI
green by itself, never merges, and never approves — human review and human
merge remain required.

## Instructions

1. Start from the provided failure log/output.
2. Identify the first meaningful failure, not just the final failure line.
3. Separate root cause from cascading errors.
4. Inspect related source and current diff.
5. Prefer targeted commands over broad suite commands.
6. Ask before running builds or expensive checks.
7. Apply a minimal fix only if the root cause is clear.
8. Do not weaken tests/guards to hide failures unless explicitly approved and justified.
9. Stop if the fix requires unapproved schema, privacy, security, or broad architecture changes.

## Preferred targeted checks

Use only when appropriate and with approval:

```bash
./mvnw -pl <service-module> -am compile
./mvnw -pl <service-module> test -Dtest=<ClassName>
npm run test -- --include "<changed>.spec.ts"
npm run lint
```

Broader checks only after targeted checks:

```bash
./mvnw verify
npm run test
```

## Output format

```markdown
CI/build root cause:
- ...

Fix:
- files: ...
- change: ...

Validation:
- command: ...
- result: PASS|FAIL|NOT RUN
- important output: ...

Remaining failures:
- none | details

Next recommended command:
- `command`
```
