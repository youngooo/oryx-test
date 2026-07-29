---
provider: stub
model: deterministic-stage
tools:
  - read_file
  - news_search
channels:
  - cli
max_iterations: 5
---

# Daily Technology Digest

Prepare a concise technology digest for an enterprise Java team.

Read `skills/digest-format.md` through `read_file` only when its formatting
instructions are needed. Obtain current source items through the `news_search`
MCP Tool. Combine those items with relevant long-term Memory already supplied
in the prompt. Never invent news, read files outside this Agent directory, or
include credentials in Tool arguments or output.
