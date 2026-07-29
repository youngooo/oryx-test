# Agent Definition Contract

OryxOS uses one directory per Agent:

```text
.oryxos/agents/<name>/
├── AGENT.md
├── skills/       # optional Agent-local instructions
├── scripts/      # optional trusted deterministic scripts
└── REFERENCE.md  # optional reference material
```

No `.oryxos/profiles/*.yaml` file exists. A runtime Profile is derived from `AGENT.md`.

## `AGENT.md`

```markdown
---
provider: deepseek
model: deepseek-v4-flash
tools:
  - http_get
  - notify
channels:
  - cli
  - scheduler
notify:
  target: daily-updates
schedules:
  - id: daily-weather
    cron: "0 0 7 * * *"
    zone: Asia/Shanghai
    prompt: 查询今天的天气，给出穿搭建议并通知我。
max_iterations: 10
---

# Weather Assistant

Follow the user's location and provide concise, practical advice.
```

## Frontmatter Fields

| Field | Required | Rules |
|---|---|---|
| `provider` | yes | Must match an explicitly configured Provider. |
| `model` | no | Provider-specific model override. |
| `tools` | no | List of registered Tool names; default empty. |
| `channels` | no | Enabled channels; default includes CLI. |
| `notify.target` | no | Logical notification target, never a plaintext credential. |
| `schedules` | no | List of unique `id`, valid cron, zone, and non-empty prompt. |
| `max_iterations` | no | Integer 1–10; default 10. |

Unknown required semantics, invalid types, unresolved Provider/Tool names, invalid cron expressions, or empty Markdown bodies make that Agent invalid. Other valid Agents continue to load.

## Load and Reload Semantics

- Startup scans `.oryxos/agents/*/AGENT.md`.
- `AGENT.md` body, `skills/*.md`, scripts, and references are read when needed, so edits are visible on the next read.
- Adding/removing an Agent or modifying frontmatter/schedules requires explicit workspace reload or process restart in the core stage.
- The core stage exposes listing, not runtime upload/edit APIs.
- Agent-local `skills/` are not globally indexed. The model may use `read_file` to load them when needed.
- Allowing `shell` for an Agent trusts that Agent author to execute its `scripts/`; the application whitelist is not a strong isolation boundary.

## Prompt Context Order

1. `AGENT.md` body and bootstrap context (`AGENTS.md`, `SOUL.md`, `USER.md`)
2. Current date, time, and configured zone
3. Long-term memory within deterministic limits
4. Latest Session history, at most 20 messages
5. Tool descriptions and schemas available to this Agent
6. Current user/scheduler message

Secrets and unrelated Agent resources are excluded.
