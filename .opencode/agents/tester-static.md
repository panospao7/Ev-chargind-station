---
description: "DEPRECATED — use tester"
mode: subagent
model: merge-gateway/zai/glm-5.3-flash
variant: max
temperature: 0.1
steps: 5
color: info
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
  bash: deny
  external_directory: deny
  webfetch: deny
  websearch: deny
  task: deny
---

# DEPRECATED

This agent is retired and kept only as a deprecated stub.

Use `tester` instead.
