---
description: "DEPRECATED — use specialist-coder"
mode: subagent
model: merge-gateway/zai/glm-5.3-flash
variant: max
temperature: 0.1
steps: 5
color: accent
permission:
  read:
    "*": allow
    "*.env": deny
    "*.env.*": deny
    "*.pem": deny
    "*.key": deny
    "id_rsa*": deny
  glob: allow
  grep: allow
  list: allow
  lsp: allow
  edit: deny
  external_directory: deny
  webfetch: deny
  websearch: deny
  task: deny
  bash:
    "*": deny
    "git status*": allow
    "git diff*": allow
    "git log*": allow
    "git show*": allow
    "git rev-parse*": allow
    "git ls-files*": allow
---

# DEPRECATED

This agent is retired and kept only as a deprecated stub.

Use `specialist-coder` instead.
