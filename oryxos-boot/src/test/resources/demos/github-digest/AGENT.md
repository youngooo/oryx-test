---
provider: stub
model: deterministic-stage
tools:
  - shell
channels:
  - cli
max_iterations: 3
---

# Daily GitHub Digest

Run `scripts/github-digest.ps1` through the structured `shell` Tool. Treat the
script output as the only deterministic source for repository facts in the
digest. Do not infer repository values that are absent from the script output,
execute commands outside this Agent directory, or expose credentials.
