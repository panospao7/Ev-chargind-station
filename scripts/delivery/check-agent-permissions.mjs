import { execSync } from 'child_process';
import { existsSync, readFileSync } from 'fs';

// Evaluates OpenCode's resolved agent rules against expected outcomes.
// Rules come from `opencode debug agent`. Matching copies OpenCode 1.19.0:
// backslashes normalize to /, * = any chars, ? = one char, a trailing " *" makes
// its tail optional, matching ignores case, and the last matching rule wins with
// ask as the fallback. Commands are never executed.
//
// This evaluates whole command strings, while OpenCode matches patterns its shell
// parser extracts, and it does not model session "always" approvals. It is not a
// git parser or a sandbox. A pass means these forms resolve as expected, not that
// no other form exists or that a live session behaves the same.

const PROJECT_AGENTS = [
  'orchestrator', 'planner', 'coder', 'tester', 'reviewer', 'debugger',
  'documentation', 'contract-reviewer', 'data-reviewer', 'security-reviewer'
];
const BUILT_IN_CHECKED = ['build', 'plan', 'general', 'explore'];
const BUILT_IN = [...BUILT_IN_CHECKED, 'compaction', 'summary', 'title'];
const ALL_AGENTS = [...PROJECT_AGENTS, ...BUILT_IN_CHECKED];
const WRITER_AGENTS = ['coder', 'debugger'];
const MAVEN_AGENTS = ['coder', 'tester', 'reviewer', 'debugger', 'contract-reviewer', 'data-reviewer', 'security-reviewer'];
const LEGACY_AGENTS = [
  'scout', 'planner-advanced', 'coder-fast', 'specialist-coder', 'specialist-coder-backup', 'swarm-coder', 'tester-static',
  'tester-runtime', 'ci-build-debugger', 'reviewer-fast', 'reviewer-strict', 'architecture-guardian',
  'privacy-security-guardian', 'room-migration-guardian', 'documentor'
];

const FORBIDDEN_COMMANDS = [
  // pushes reaching main, or carrying anything beyond one task branch
  'git push origin main', 'git push origin HEAD:main', 'git push -u origin main', 'git push origin task/x:main',
  'git push origin task/x:refs/heads/main', 'git push origin task/x main', 'git push origin task/x MAIN',
  'git push origin task/x HEAD', 'git push origin task/x refs/heads/main', 'git push -u origin task/x refs/heads/main',
  'git push origin task/x refs/heads/master', 'git push origin task/x "main"', "git push origin task/x 'main'",
  'git push origin task/x ma\\in', 'git push origin task/x\tmain', 'git push origin task/x @', 'git push origin task/x other-branch',
  'git push origin task/x --tags', 'git push origin task/x --follow-tags', 'git push origin task/x --no-verify',
  'git push https://github.com/o/r task/x:main', 'git push main', 'git push HEAD', 'git push master',
  // force, delete and other-target pushes
  'git push origin task/x --force', 'git push origin task/x --force-with-lease', 'git push origin task/x -f',
  'git push -f origin task/x', 'git push -fu origin task/x', 'git push origin task/x -uf', 'git push origin task/x -fu',
  'git push origin task/x -vf', 'git push origin task/x refs/heads/main -uf', 'git push origin +task/x',
  'git push origin --delete task/x', 'git push origin task/x -d', 'git push origin task/x -ud', 'git push origin task/x --de',
  'git push origin task/x --dele', 'git push origin task/x refs/heads/main --de', 'git push origin task/x --repo=../evil.git',
  'git push origin task/x --receive-pack=evil', 'git push --all origin', 'git push --mirror origin',
  // staging and commit forms that sweep, force or skip checks
  'git add .', 'git add ./', 'git add ./.', 'git add -- .', 'git add -- ./', 'git add -- :/', 'git add ":/"', 'git add :^x', 'git add ":(top)"',
  'git add -v .', 'git add -vA', 'git add -fA', 'git add -A', 'git add --all', 'git add --al', 'git add -u', 'git add -vu', 'git add --upd',
  'git add --no-ignore-removal', 'git add -f .env', 'git add --force .env', 'git add -f secret.pem', 'git add src .',
  'git commit -am x', 'git commit -a -m x', 'git commit -m x -a', 'git commit -m x --all', 'git commit -m x -am y', 'git commit -m x -av',
  'git commit -m x -va', 'git commit -F f -am y', 'git commit --amend -m x', 'git commit -m x --amen', 'git commit -m x --am',
  'git commit -F f --amen', 'git commit -m x --no-verify', 'git commit -m x -n', 'git commit -nm x', 'git commit --no-verify -m x',
  // discarding work
  'git checkout main -- src/A.java', 'git checkout HEAD -- src/A.java', 'git checkout -- src/A.java', 'git checkout .',
  'git checkout -f main', 'git checkout HEAD p', 'git checkout main p', 'git checkout -b task/x -f', 'git checkout -b task/x --force origin/main',
  'git switch --discard-changes main', 'git switch -f main', 'git switch task/x --discard-changes', 'git switch task/x -f',
  'git switch task/x --force', 'git switch -c task/x --discard-changes', 'git switch -c task/x -f',
  'git stash', 'git stash clear', 'git stash drop', 'git reset --hard', 'git restore src/A.java', 'git clean -fd', 'git rm x',
  // history and ref changes
  'git branch -D x', 'git branch -vD x', 'git branch --del x', 'git branch -dr x', 'git branch -f main x', 'git branch --forc main x',
  'git branch -qf main x', 'git rebase main', 'git merge main', 'git merge', 'git tag v1', 'git pull', 'git update-ref refs/heads/main task/x',
  'git update-ref -d refs/heads/x', 'git symbolic-ref HEAD refs/heads/main', 'git filter-branch --all', 'git reflog expire --all --expire=now',
  'git gc --prune=now', 'git worktree remove --force x', 'git replace a b', 'git prune', 'git am x', 'git cherry-pick abc', 'git revert abc',
  'git remote add evil https://x', 'git remote set-url origin https://x', 'git config alias.st push', 'git config --global alias.x push',
  'git config remote.origin.push x', 'git fetch . task/x:main', 'git fetch . +task/x:refs/heads/main', 'git fetch --upload-pack=evil .',
  'git fetch-pack --upload-pack=evil .', 'git difftool -x evil', 'git difftool --extcmd=evil -y', 'git mergetool',
  // git global options hide the subcommand from the rules above
  'git -C . push origin main', 'git -c x=y push origin main', 'git --no-pager push origin main', 'git -c x=y clean -fd',
  // read commands that write files
  'git status > AGENTS.md', 'git log > opencode.json', 'git diff > .opencode/agents/orchestrator.md', 'git rev-parse HEAD > delivery/status.yaml',
  'git diff --output=AGENTS.md', 'git log --output=opencode.json', 'git show --output=.opencode/agents/coder.md',
  'git log -1 --format=evil > .git/hooks/post-commit', 'git log -1 --format=evil --output=.git/hooks/post-commit',
  // secret content through allowed read commands
  'git diff --no-index .env /dev/null', 'git diff --no-index C:/Users/x/.ssh/id_rsa /dev/null', 'git show HEAD:infra/local/.env', 'git log -p -- .env',
  'git grep --no-index KEY .env', 'git grep --untracked KEY', 'git blame --contents .env x',
  // GitHub state and credentials
  'gh pr merge 51 --squash', 'gh pr review 51 --approve', 'gh pr close 51', 'gh pr edit 1', 'gh -R o/r pr merge 1', 'gh api -X PUT repos/o/r/pulls/51/merge',
  'gh api repos/o/r/pulls/1/merge -f x=y', 'gh api repos/o/r/pulls/1/merge --input f', 'gh api -XPUT repos/o/r/pulls/1/merge',
  'gh api --method=PUT repos/o/r/pulls/1/merge', 'gh api graphql -f query=mutation', 'gh alias set x "pr merge"', 'gh auth token', 'gh repo delete o/r',
  'gh repo set-default o/r', 'gh release create v1', 'gh secret set X', 'gh variable set X', 'gh workflow run x', 'gh workflow disable x', 'gh issue create',
  // shells, interpreters and deletion
  'rm -rf x', 'RM -rf x', '/usr/bin/rm -rf x', 'Remove-Item -Recurse -Force x', 'remove-item x', 'ri x', 'erase x', 'rd /s /q x', 'del x', 'rmdir x',
  'find . -delete', 'xargs rm', 'sudo ls', 'powershell -c "Remove-Item x"', 'pwsh -c x', 'cmd /c del x', 'bash -c "rm -rf x"', 'sh -c "rm -rf x"',
  'python -c x', 'node -e x', 'Set-Content AGENTS.md x', 'Out-File AGENTS.md', 'Invoke-Expression x',
  // infrastructure, database and publishing
  'terraform apply', 'terraform destroy', 'tofu apply', 'kubectl apply -f x.yaml', 'kubectl delete pod x', 'flyway clean', 'flyway validate clean', 'flyway info clean',
  'docker system prune -a', 'docker volume rm x', 'docker compose down -v', 'npm publish', 'npm install -g x', 'npm i -g x',
  // Maven forms that are not a plain test or verify
  './mvnw flyway:clean test', '.\\mvnw.cmd flyway:clean test', '.\\mvnw.cmd deploy -DskipTests', '.\\mvnw.cmd deploy -Dtest=x', '.\\mvnw.cmd flyway:migrate -Dtest=x',
  '.\\mvnw.cmd flyway:repair -Dtest=x', '.\\mvnw.cmd exec:exec -Dtest=x -Dexec.executable=cmd', '.\\mvnw.cmd org.flywaydb:flyway-maven-plugin:clean -Dtest=x',
  '.\\mvnw.cmd -f C:/evil/pom.xml test', './mvnw exec:exec -Dtest=x', './mvnw deploy -Dtest=x', './mvnw -f C:/evil/pom.xml test',
  '.\\mvnw.cmd -pl apps/bff deploy test', '.\\mvnw.cmd -pl apps/bff org.flywaydb:flyway-maven-plugin:clean test', '.\\mvnw.cmd -pl apps/bff clean test',
  // other build forms that run arbitrary commands, and redirects on allowed commands
  'npm run test --script-shell=evil', 'npm run build --script-shell=evil', 'npm run contracts:verify --script-shell=evil', 'node scripts/contracts/../../evil.js',
  'npm run contracts:verify > AGENTS.md', '.\\mvnw.cmd test > AGENTS.md', 'gh pr view 1 > AGENTS.md', 'node scripts/delivery/validate.mjs > AGENTS.md',
  // second-round review: expansion, abbreviations, external paths and build properties
  // OpenCode's parser skips an assignment statement, so these are the forms it evaluates
  'git push origin task/$B', 'git push -u origin task/$B', 'git push origin task/x${IFS}main', 'git push origin task/`x`', 'git switch task/$B', 'git switch -c task/$B',
  'git grep --open=evil x', 'git grep --open-files=evil x', 'git grep -nOevil x', 'git grep -O evil x',
  'git diff C:/Users/x/.ssh/id_rsa /dev/null', 'git diff C:\\Users\\x\\.ssh\\id_rsa NUL', 'git diff /c/Users/x/.config/gh/hosts.yml /dev/null', 'git diff ~/.ssh/id_rsa /dev/null',
  'git grep --no-ind --no-exclude KEY', 'git grep --untr KEY', 'git blame --cont x y', 'git show HEAD:secrets/x', 'git log -p -- secrets/x',
  './mvnw test -Djvm=evil', '.\\mvnw.cmd test -Djvm=evil', './mvnw test -Dmaven.ext.class.path=evil.jar', './mvnw test -DargLine=x', './mvnw test -l AGENTS.md',
  './mvnw test --log-file=AGENTS.md', './mvnw test -fevil/pom.xml', './mvnw test -sevil.xml', './mvnw test -gs evil.xml', './mvnw test --global-settings=evil.xml',
  './mvnw test site-deploy', './mvnw -pl x test -Dmaven.test.skip=true', './mvnw -pl x test -DskipTests', './mvnw test -DforkMode=never', './mvnw test -Dmaven.repo.local=x',
  'npm run test --prefix=C:/evil', 'npm run build --prefix ../evil', 'npm run contracts:verify --script-she=evil', 'npm run test --node-options=--require=evil', 'npm run test --userconfig=evil',
  'make verify -f evil.mk', 'make verify --eval=x', 'make test SHELL=evil',
  // third-round verification: plumbing prompts must not downgrade the shared denies
  'git diff-tree --output=AGENTS.md HEAD', 'git diff-files --ext-diff', 'git show-ref > AGENTS.md', 'git diff-tree HEAD > opencode.json',
  'git diff-index -p HEAD -- .env', 'git diff $HOME/.ssh/id_rsa NUL', 'git diff NUL $HOME/.ssh/id_rsa'
];

const PROTECTED_EDIT_DENIED = [
  '.git/config', '.git/hooks/pre-commit', '.git/hooks/pre-push', '.git/refs/heads/main', 'services/x/.git/config',
  'GIT~1/hooks/pre-commit', 'GIT~1/config', 'OPENCO~1.JSO', 'OPENCO~1/agents/coder.md', 'AGENTS.md::$DATA', 'opencode.json::$DATA',
  'x.keystore', 'x.ppk', 'x.pkcs12', '.netrc', '.git-credentials', 'credentials.json', 'id_ecdsa', 'id_dsa', '.aws/credentials',
  'services/x/src/test/resources/AGENTS.md', 'services/x/src/test/.opencode/agents/x.md', 'tests/opencode.json',
  'delivery/handoffs/AGENTS.md', 'delivery/handoffs/opencode.json', 'delivery/handoffs/.opencode/agents/x.md',
  '.env', 'infra/local/.env', '.env.local', 'x.pem', 'infra/local/dev.pem', 'x.key', 'x.p12', 'infra/x.p12', 'x.jks', 'x.pfx', 'id_rsa', 'infra/id_ed25519',
  'secrets/x', 'infra/secrets/x', 'tests/.env', 'tests/secrets/x.key', 'services/x/src/test/resources/dev.pem', 'delivery/evidence/x.pem',
  'delivery/evidence/.env', 'delivery/handoffs/secrets/x', 'delivery/deviations/.env.local'
];
const SECRET_READ_DENIED = ['.env', 'infra/local/.env', 'x.pem', 'infra/x.pem', 'x.key', 'x.p12', 'infra/x.jks', 'x.pfx', 'id_rsa', 'infra/id_ed25519', 'secrets/x', '.npmrc', '.git/config',
  'x.keystore', 'x.ppk', 'x.pkcs12', '.netrc', '.git-credentials', 'credentials.json', 'id_ecdsa', 'id_dsa', '.aws/credentials'];

const EXPECTATIONS = [
  ...ALL_AGENTS.flatMap((agent) => [
    ...FORBIDDEN_COMMANDS.map((subject) => ({ agent, permission: 'bash', subject, expected: 'deny' })),
    ...SECRET_READ_DENIED.map((subject) => ({ agent, permission: 'read', subject, expected: 'deny' })),
    { agent, permission: 'read', subject: 'infra/local/.env.example', expected: 'allow' },
    { agent, permission: 'read', subject: 'docs/x.md', expected: 'allow' }
  ]),

  // Anything not otherwise listed must prompt, not fall through to allow.
  ...PROJECT_AGENTS.flatMap((agent) => [
    { agent, permission: 'bash', subject: 'curl https://example.org', expected: 'ask' },
    { agent, permission: 'bash', subject: 'env git push origin main', expected: 'ask' },
    { agent, permission: 'mcp_github_merge_pull_request', subject: '*', expected: 'ask' },
    { agent, permission: 'some_custom_tool', subject: '*', expected: 'ask' },
    { agent, permission: 'external_directory', subject: 'C:/Users/x/.ssh/id_rsa', expected: 'deny' }
  ]),

  // Read-only commands stay usable.
  ...PROJECT_AGENTS.flatMap((agent) => [
    { agent, permission: 'bash', subject: 'git status', expected: 'allow' },
    { agent, permission: 'bash', subject: 'git status --short', expected: 'allow' },
    { agent, permission: 'bash', subject: 'git diff --stat', expected: 'allow' },
    { agent, permission: 'bash', subject: 'git log -5', expected: 'allow' },
    { agent, permission: 'bash', subject: 'git branch --show-current', expected: 'allow' },
    { agent, permission: 'bash', subject: 'git rev-parse HEAD', expected: 'allow' },
    { agent, permission: 'bash', subject: 'git merge-base main HEAD', expected: 'allow' }
  ]),

  // The orchestrator's narrow write path.
  { agent: 'orchestrator', permission: 'bash', subject: 'git add -- delivery/status.yaml', expected: 'ask' },
  { agent: 'orchestrator', permission: 'bash', subject: 'git add -- delivery/a.yaml delivery/b.yaml', expected: 'ask' },
  { agent: 'orchestrator', permission: 'bash', subject: 'git commit -m "I1-DEL-001: change"', expected: 'ask' },
  { agent: 'orchestrator', permission: 'bash', subject: 'git push -u origin task/i1-del-001-x', expected: 'allow' },
  { agent: 'orchestrator', permission: 'bash', subject: 'git push origin task/main-fix', expected: 'allow' },
  { agent: 'orchestrator', permission: 'bash', subject: 'git switch task/i1-del-001-x', expected: 'allow' },
  { agent: 'orchestrator', permission: 'bash', subject: 'git switch -c task/i1-del-001-x', expected: 'ask' },
  { agent: 'orchestrator', permission: 'bash', subject: 'git checkout -b task/i1-del-001-x', expected: 'ask' },
  { agent: 'orchestrator', permission: 'bash', subject: 'git switch main', expected: 'ask' },
  { agent: 'orchestrator', permission: 'bash', subject: 'gh pr checks 51', expected: 'allow' },
  { agent: 'orchestrator', permission: 'bash', subject: 'gh run view 1', expected: 'allow' },
  { agent: 'orchestrator', permission: 'bash', subject: 'gh pr create --title x', expected: 'ask' },
  { agent: 'orchestrator', permission: 'bash', subject: 'node scripts/delivery/validate.mjs', expected: 'allow' },
  { agent: 'orchestrator', permission: 'bash', subject: 'node scripts/delivery/validate.mjs.evil.js', expected: 'ask' },
  { agent: 'orchestrator', permission: 'bash', subject: 'git hash-object --stdin', expected: 'allow' },
  { agent: 'orchestrator', permission: 'bash', subject: 'git hash-object -- a.yaml b.yaml', expected: 'allow' },
  { agent: 'orchestrator', permission: 'bash', subject: 'git hash-object -w -- a.yaml', expected: 'deny' },
  { agent: 'orchestrator', permission: 'bash', subject: 'git ls-files --others --exclude-standard -- . ":(exclude)delivery/handoffs/X/**" ":(exclude)delivery/evidence/X/**"', expected: 'allow' },
  { agent: 'orchestrator', permission: 'bash', subject: 'git diff abc123 -- . ":(exclude)delivery/handoffs/X/**" ":(exclude)delivery/status.yaml" ":(exclude)new/file.md"', expected: 'allow' },
  { agent: 'orchestrator', permission: 'bash', subject: 'git diff --cached --name-only', expected: 'allow' },
  { agent: 'orchestrator', permission: 'bash', subject: 'git diff --cached --name-only -i -G"(SECRET|PASSWORD|PASSWD|TOKEN|PRIVATE[_-]?KEY|API[_-]?KEY|CREDENTIAL)[A-Z_-]*[\\"\']?[ ]*[=:]"', expected: 'allow' },
  { agent: 'orchestrator', permission: 'bash', subject: 'git status --short -uall', expected: 'allow' },
  { agent: 'orchestrator', permission: 'bash', subject: 'gh pr view 1', expected: 'allow' },
  { agent: 'orchestrator', permission: 'bash', subject: 'gh run list', expected: 'allow' },
  { agent: 'orchestrator', permission: 'bash', subject: 'git fetch origin', expected: 'allow' },
  { agent: 'orchestrator', permission: 'bash', subject: 'gh pr create --title x --body-file delivery/handoffs/X/pr-body.md', expected: 'ask' },
  { agent: 'orchestrator', permission: 'bash', subject: 'npm exec --no -- secretlint --maskSecrets --secretlintrc scripts/contracts/secretlintrc.json a.yaml', expected: 'allow' },
  { agent: 'orchestrator', permission: 'bash', subject: 'npm exec --no -- secretlint --maskSecrets --secretlintrc scripts/contracts/secretlintrc.json --output=x a', expected: 'deny' },
  { agent: 'orchestrator', permission: 'bash', subject: 'npm exec --no -- secretlint --maskSecrets --secretlintrc scripts/contracts/secretlintrc.json a --secretlintrc=evil.json', expected: 'deny' },
  { agent: 'orchestrator', permission: 'edit', subject: 'delivery/status.yaml', expected: 'allow' },
  { agent: 'orchestrator', permission: 'edit', subject: 'delivery/handoffs/X/01-orchestrator.md', expected: 'allow' },
  { agent: 'orchestrator', permission: 'edit', subject: 'delivery/tasks/X.yaml', expected: 'ask' },
  { agent: 'orchestrator', permission: 'edit', subject: 'apps/web/src/app/a.ts', expected: 'deny' },

  // Test and CI inspection stay usable where agents need them.
  ...MAVEN_AGENTS.flatMap((agent) => [
    { agent, permission: 'bash', subject: '.\\mvnw.cmd -pl apps/bff -am test "-Dmaven.compiler.release=21"', expected: 'allow' },
    { agent, permission: 'bash', subject: '.\\mvnw.cmd test', expected: 'allow' },
    { agent, permission: 'bash', subject: './mvnw -pl apps/bff -am test', expected: 'allow' },
    { agent, permission: 'bash', subject: './mvnw verify', expected: 'allow' },
    { agent, permission: 'bash', subject: '.\\mvnw.cmd -pl services/x -am verify -Dtest=Foo', expected: 'allow' }
  ]),
  { agent: 'data-reviewer', permission: 'bash', subject: '.\\mvnw.cmd flyway:validate', expected: 'allow' },
  { agent: 'data-reviewer', permission: 'bash', subject: './mvnw -pl services/x flyway:info', expected: 'allow' },
  { agent: 'data-reviewer', permission: 'bash', subject: './mvnw -pl a org.flywaydb:flyway-maven-plugin:clean flyway:validate', expected: 'deny' },
  { agent: 'data-reviewer', permission: 'bash', subject: 'flyway validate', expected: 'allow' },
  ...['tester', 'reviewer', 'security-reviewer'].flatMap((agent) => [
    { agent, permission: 'bash', subject: 'gh pr checks 51', expected: 'allow' },
    { agent, permission: 'bash', subject: 'gh run view 1', expected: 'allow' }
  ]),
  ...['tester', 'reviewer', 'documentation'].map((agent) =>
    ({ agent, permission: 'bash', subject: 'node scripts/delivery/validate.mjs', expected: 'allow' })),
  ...['coder', 'tester', 'reviewer', 'debugger', 'contract-reviewer', 'security-reviewer'].map((agent) =>
    ({ agent, permission: 'bash', subject: 'npm run contracts:verify', expected: 'allow' })),
  { agent: 'contract-reviewer', permission: 'bash', subject: 'node scripts/contracts/validate-schemas.js', expected: 'allow' },

  // Skills stay as the two specialist reviewers declare them.
  ...['contract-reviewer', 'data-reviewer'].map((agent) => ({ agent, permission: 'skill', subject: 'task-packet', expected: 'deny' })),
  { agent: 'contract-reviewer', permission: 'skill', subject: 'contract-review', expected: 'allow' },
  { agent: 'data-reviewer', permission: 'skill', subject: 'migration-review', expected: 'allow' },
  { agent: 'orchestrator', permission: 'bash', subject: 'git grep -n Booking', expected: 'ask' },
  { agent: 'orchestrator', permission: 'bash', subject: 'git show-ref', expected: 'ask' },

  // Only the orchestrator writes git history.
  ...PROJECT_AGENTS.filter((a) => a !== 'orchestrator').flatMap((agent) => [
    { agent, permission: 'bash', subject: 'git add -- x', expected: 'deny' },
    { agent, permission: 'bash', subject: 'git commit -m x', expected: 'deny' },
    { agent, permission: 'bash', subject: 'git push origin task/x', expected: 'deny' },
    { agent, permission: 'bash', subject: 'git push -u origin task/x', expected: 'deny' },
    { agent, permission: 'bash', subject: 'git switch task/x', expected: 'deny' },
    { agent, permission: 'task', subject: 'coder', expected: 'deny' },
    { agent, permission: 'task', subject: 'general', expected: 'deny' }
  ]),

  // Edit protection.
  ...['orchestrator', 'coder', 'debugger', 'tester', 'documentation', 'build', 'general'].flatMap((agent) =>
    PROTECTED_EDIT_DENIED.map((subject) => ({ agent, permission: 'edit', subject, expected: 'deny' }))),
  ...WRITER_AGENTS.flatMap((agent) => [
    ...['docs/01_scope_and_requirements/x.md', 'docs/02_use_cases/driver/x.md', 'docs/04_platform_and_integrations/x.md',
      'docs/08_delivery_and_ai_agents/x.md', 'docs/00_governance/x.md', 'pom.xml', 'services/account-service/pom.xml',
      'package.json', 'package-lock.json', 'apps/web/package.json', 'apps/web/package-lock.json', '.spectral.yaml', '.mvn/wrapper/x', 'mvnw', 'mvnw.cmd',
      '.npmrc', 'apps/web/.npmrc', '.github/CODEOWNERS', '.github/actions/x/action.yml', '.github/dependabot.yml', '.gitignore', '.gitattributes',
      'Makefile', '.husky/pre-commit', '.claude/settings.json', 'CONTEXT.md', '.gitmodules', '.nvmrc', 'settings.xml', 'services/x/.mvn/jvm.config',
      'services/x/mvnw', 'services/x/mvnw.cmd', 'apps/web/.yarnrc.yml',
      'release-manifests/x.yaml', 'scripts/x.mjs', 'scripts/contracts/check-registries.js', 'scripts/delivery/validate.mjs',
      '.github/workflows/g3-contract-validation.yml', 'infra/local/compose.yaml',
      'services/booking-session-service/src/main/resources/db/migration/V2__x.sql', 'contracts/registries/problem-codes-v1.yaml',
      '.env.example', 'infra/local/.env.example'
    ].map((subject) => ({ agent, permission: 'edit', subject, expected: 'ask' })),
    ...['archive/x.md', 'delivery/status.yaml', 'delivery/tasks/X.yaml', 'delivery/backlog.yaml', 'delivery/iterations/I1.yaml', 'AGENTS.md', 'services/AGENTS.md',
      'CLAUDE.md', 'services/CLAUDE.md', 'opencode.json', 'opencode.jsonc', '.opencode.json', 'services/x/opencode.json', '.opencode/agents/coder.md',
      '.opencode/plugins/x.js', 'services/.opencode/agents/x.md'
    ].map((subject) => ({ agent, permission: 'edit', subject, expected: 'deny' })),
    { agent, permission: 'edit', subject: 'services/account-service/src/main/java/A.java', expected: 'allow' },
    { agent, permission: 'edit', subject: 'apps/web/src/app/a.ts', expected: 'allow' }
  ]),
  ...['docs/01_scope_and_requirements/x.md', 'docs/08_delivery_and_ai_agents/x.md'].map((subject) =>
    ({ agent: 'documentation', permission: 'edit', subject, expected: 'ask' })),
  { agent: 'documentation', permission: 'edit', subject: 'delivery/handoffs/X/01-documentation.md', expected: 'allow' },
  { agent: 'tester', permission: 'edit', subject: 'apps/web/src/app/a.ts', expected: 'deny' },
  { agent: 'tester', permission: 'edit', subject: 'services/x/src/test/java/A.java', expected: 'allow' },
  { agent: 'build', permission: 'edit', subject: 'apps/web/src/app/a.ts', expected: 'ask' },
  { agent: 'build', permission: 'edit', subject: 'AGENTS.md', expected: 'deny' },
  { agent: 'build', permission: 'edit', subject: 'delivery/status.yaml', expected: 'deny' },

  // Delegation stays limited to project agents.
  ...PROJECT_AGENTS.filter((a) => a !== 'orchestrator').map((subject) =>
    ({ agent: 'orchestrator', permission: 'task', subject, expected: 'allow' })),
  ...['general', 'explore', 'brand-new-agent', ...LEGACY_AGENTS].map((subject) =>
    ({ agent: 'orchestrator', permission: 'task', subject, expected: 'deny' }))
];

function toRegex(pattern) {
  const normalized = pattern.replaceAll('\\', '/');
  let source = '';
  for (const ch of normalized) {
    if (ch === '*') source += '.*';
    else if (ch === '?') source += '.';
    else if ('.+^${}()|[]/'.includes(ch)) source += '\\' + ch;
    else source += ch;
  }
  if (source.endsWith(' .*')) source = source.slice(0, -3) + '( .*)?';
  return new RegExp('^' + source + '$', 'si');
}

const matches = (pattern, value) => toRegex(pattern).test(value.replaceAll('\\', '/'));

function evaluate(rules, permission, subject) {
  let action = 'ask';
  for (const rule of rules) {
    if (matches(rule.permission, permission) && matches(rule.pattern, subject)) action = rule.action;
  }
  return action;
}

function opencode(args) {
  for (let attempt = 1; ; attempt++) {
    try {
      return execSync(`opencode ${args}`, { encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'], shell: true });
    } catch (error) {
      if (attempt === 3) throw error;
    }
  }
}

const cache = new Map();
function resolvedRules(agent) {
  if (!cache.has(agent)) cache.set(agent, JSON.parse(opencode(`debug agent ${agent}`)).permission);
  return cache.get(agent);
}

console.log('=== Agent Permission Check ===');

const listed = [...opencode('agent list').matchAll(/^(\S+) \((primary|subagent|all)\)/gm)].map((m) => m[1]);
const unexpected = listed.filter((a) => !PROJECT_AGENTS.includes(a) && !BUILT_IN.includes(a));
const missing = PROJECT_AGENTS.filter((a) => !listed.includes(a));

const failures = [];
for (const check of EXPECTATIONS) {
  const actual = evaluate(resolvedRules(check.agent), check.permission, check.subject);
  if (actual !== check.expected) failures.push({ ...check, actual });
}

for (const f of failures) {
  console.log(`  FAIL ${f.agent} ${f.permission} "${f.subject}": expected ${f.expected}, got ${f.actual}`);
}
if (unexpected.length) console.log(`  FAIL agents outside this project are callable: ${unexpected.join(', ')}`);
if (missing.length) console.log(`  FAIL project agents missing: ${missing.join(', ')}`);

// Every bash deny at the root must also win for each project agent, because a
// same-named global agent file can place a catch-all after the root rules.
const rootDenies = JSON.parse(readFileSync('opencode.json', 'utf8')).permission.bash;
const driftFailures = [];
for (const [pattern, action] of Object.entries(rootDenies)) {
  if (action !== 'deny') continue;
  const sample = pattern.replaceAll('*', 'x').replaceAll('?', 'x');
  for (const agent of PROJECT_AGENTS) {
    const actual = evaluate(resolvedRules(agent), 'bash', sample);
    if (actual !== 'deny') driftFailures.push(`  FAIL root deny "${pattern}" not effective for ${agent}: "${sample}" gives ${actual}`);
  }
}
driftFailures.forEach((line) => console.log(line));

// Under PowerShell, OpenCode's parser extracts no pattern for common syntax, so rules would not apply.
const pinnedShell = JSON.parse(readFileSync('opencode.json', 'utf8')).shell;
if (pinnedShell !== 'bash') console.log(`  FAIL opencode.json shell must be bash, found: ${pinnedShell}`);

// Agents that write production code stay on a different model from the agents that review it.
const WRITER_MODEL = { providerID: 'zai', modelID: 'glm-5.3-flash', variant: 'max' };
const REVIEWER_MODEL = { providerID: 'zai', modelID: 'gemini-4-argon', variant: 'high' };
const EXPECTED_MODELS = {
  orchestrator: WRITER_MODEL, coder: WRITER_MODEL, debugger: WRITER_MODEL, documentation: WRITER_MODEL,
  build: WRITER_MODEL, plan: WRITER_MODEL, general: WRITER_MODEL, explore: WRITER_MODEL,
  planner: REVIEWER_MODEL, tester: REVIEWER_MODEL, reviewer: REVIEWER_MODEL,
  'security-reviewer': REVIEWER_MODEL, 'data-reviewer': REVIEWER_MODEL, 'contract-reviewer': REVIEWER_MODEL
};
const modelFailures = [];
for (const [agent, want] of Object.entries(EXPECTED_MODELS)) {
  const got = JSON.parse(opencode(`debug agent ${agent}`));
  const found = `${got.model?.providerID}/${got.model?.modelID} ${got.variant}`;
  if (found !== `${want.providerID}/${want.modelID} ${want.variant}`) modelFailures.push(`  FAIL ${agent} model: ${found}`);
}
for (const agent of [...PROJECT_AGENTS, ...BUILT_IN_CHECKED]) {
  if (!EXPECTED_MODELS[agent]) modelFailures.push(`  FAIL ${agent} has no expected model in this check`);
}
for (const agent of BUILT_IN.filter((name) => !BUILT_IN_CHECKED.includes(name))) {
  const { model } = JSON.parse(opencode(`debug agent ${agent}`));
  if (model) modelFailures.push(`  FAIL ${agent} pins its own model: ${model.providerID}/${model.modelID}`);
}
const rootConfig = JSON.parse(readFileSync('opencode.json', 'utf8'));
const WRITER_ID = `${WRITER_MODEL.providerID}/${WRITER_MODEL.modelID}`;
for (const key of ['model', 'small_model']) {
  if (rootConfig[key] !== WRITER_ID) modelFailures.push(`  FAIL opencode.json ${key} must be ${WRITER_ID}, found: ${rootConfig[key]}`);
}
// A second project config file could override the values read above without this check seeing them.
for (const path of ['opencode.jsonc', '.opencode.json', '.opencode/opencode.json', '.opencode/opencode.jsonc']) {
  if (existsSync(path)) modelFailures.push(`  FAIL unexpected project config file: ${path}`);
}
modelFailures.forEach((line) => console.log(line));

const failed = failures.length + unexpected.length + missing.length + driftFailures.length + modelFailures.length + (pinnedShell === 'bash' ? 0 : 1);
console.log(`\n${EXPECTATIONS.length - failures.length}/${EXPECTATIONS.length} expectations met; ${listed.length} agents listed`);
console.log(failed === 0 ? '=== ALL CHECKS PASSED ===' : `=== ${failed} FAILURE(S) ===`);
process.exit(failed === 0 ? 0 : 1);
