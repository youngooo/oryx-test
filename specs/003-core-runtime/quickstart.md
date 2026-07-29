# Quickstart and Acceptance Guide

This guide describes the intended verification path after implementation. It does not claim the current repository skeleton already provides these commands.

## Prerequisites

- JDK 21
- Maven 3.9+
- A writable temporary workspace
- Optional live acceptance credentials supplied only through environment variables
- An optional self-hosted Mem0 endpoint for the live Mem0 acceptance profile; automated tests use a local HTTP stub

Never place API keys in `AGENT.md`, source files, tests, logs, or committed configuration.

## 1. Build and Test

```powershell
mvn clean package
```

The build must run unit, module-integration, web-contract, packaged-JAR, and demo fixture tests. The final artifact is produced by `oryxos-boot`; other modules are libraries.

## 2. Initialize an Isolated Workspace

```powershell
$oryxWorkspace = Join-Path $env:TEMP 'oryxos-quickstart'
New-Item -ItemType Directory -Force -Path $oryxWorkspace | Out-Null
java -jar .\oryxos-boot\target\oryxos.jar init $oryxWorkspace
```

Expected tree:

```text
.oryxos/
├── agents/
├── memory/
│   └── MEMORY.md
├── logs/
├── mcp_servers.yaml
├── AGENTS.md
├── SOUL.md
├── USER.md
└── oryxos.db
```

Running `init` again must preserve existing user content.

## 3. Configure Providers Safely

Set secrets in the process environment:

```powershell
$env:DEEPSEEK_API_KEY = '<set-outside-source-control>'
$env:KIMI_API_KEY = '<set-outside-source-control>'
```

Use placeholder references in application configuration. Then verify explicit mapping without revealing keys:

```powershell
java -jar .\oryxos-boot\target\oryxos.jar provider list --workspace $oryxWorkspace
```

Acceptance requires separate successful smoke invocations for Provider names `deepseek` and `kimi`. Automated CI uses stub `ChatModel` adapters.

## 4. Create and Inspect an Agent

```powershell
java -jar .\oryxos-boot\target\oryxos.jar profile create weather --workspace $oryxWorkspace
java -jar .\oryxos-boot\target\oryxos.jar profile show weather --workspace $oryxWorkspace
java -jar .\oryxos-boot\target\oryxos.jar tool list --workspace $oryxWorkspace
```

Edit `.oryxos/agents/weather/AGENT.md` according to `contracts/agent-definition.md`. A malformed second Agent must be reported without preventing `weather` from loading.

## 5. Verify the Minimal ReAct Path

Start a chat:

```powershell
java -jar .\oryxos-boot\target\oryxos.jar chat --profile weather --workspace $oryxWorkspace
```

Verify:

1. A no-Tool response terminates normally.
2. A single Tool call executes once and is appended in order.
3. Multiple Tool rounds remain ordered.
4. A non-terminating stub stops at 10 iterations.
5. Provider and Tool failures produce safe errors and durable audit rows.

## 6. Start the REST Service

```powershell
java -jar .\oryxos-boot\target\oryxos.jar serve --workspace $oryxWorkspace
```

In another terminal:

```powershell
$baseUrl = 'http://localhost:8080/api/v1'
$sessionBody = @{
  profileName = 'weather'
  channel = 'api'
  userId = 'quickstart-user'
} | ConvertTo-Json

$created = Invoke-RestMethod -Method Post `
  -Uri "$baseUrl/sessions" `
  -ContentType 'application/json' `
  -Body $sessionBody

$messageBody = @{ content = 'What should I wear today?' } | ConvertTo-Json
Invoke-RestMethod -Method Post `
  -Uri "$baseUrl/sessions/$($created.data.sessionId)/messages" `
  -ContentType 'application/json' `
  -Body $messageBody

$sessionDetail = Invoke-RestMethod -Uri `
  "$baseUrl/sessions/$($created.data.sessionId)?messageLimit=100&llmOffset=0&llmLimit=100&toolOffset=0&toolLimit=100"
$sessionDetail.data.messages
$sessionDetail.data.llmCalls
$sessionDetail.data.llmCallsPage
$sessionDetail.data.toolInvocations
$sessionDetail.data.toolInvocationsPage
Invoke-RestMethod -Method Delete -Uri "$baseUrl/sessions/$($created.data.sessionId)"
```

Page the two audit arrays independently: increase `llmOffset` until it reaches
`llmCallsPage.total`, and increase `toolOffset` until it reaches
`toolInvocationsPage.total`. This proves every persisted attempt is retrievable.
A subsequent message to the archived Session must return HTTP 409 with code
`SESSION_ARCHIVED` and instruct the client to create a new Session.

## 7. Concurrency and Restart Checks

Automated acceptance tests must demonstrate:

- ReAct and PromptBuilder obtain combined Session and long-term memory through `MemoryService` only.
- Two accepted requests for the same Session complete in service-acceptance order.
- Requests for different Sessions can overlap.
- Concurrent long-term memory appends do not lose content.
- A memory read observes all appends completed before the read began.
- Restarting the service preserves Sessions and both audit tables.
- No automatic retention process removes records.

## 8. Memory Backend Contract Checks

Run the same `LongTermMemoryStore` contract suite with:

```text
memory.backend=markdown
memory.backend=sqlite
memory.backend=mem0
```

For each backend, verify:

1. `CORE` content is returned completely.
2. Only `ARCHIVAL` content is truncated.
3. `append`, `load`, and `recallByKeyword` preserve the shared behavior.
4. A backend switch requires no change to `MemoryService`, `PromptBuilder`, `MemoryTools`, or `ReActLoop`.
5. Mem0 uses a self-hosted endpoint and redacts credentials and authorization headers.
6. An unknown backend value fails startup clearly.

## 9. Security Checks

Run the security test suite with temporary workspace fixtures:

- Reject `../` and symlink filesystem escapes.
- Reject allowed-host prefix tricks such as `allowed.example.evil.test`.
- Reject unapproved executables and unsupported compound Shell commands.
- Audit both successful and denied calls.
- Confirm database, logs, REST responses, and CLI output contain no configured secrets.

## 10. End-to-End Demo 1: Daily Weather

Definition: `AGENT.md` only.

### Current US1 + US2 stage acceptance

Before Scheduler and notification work begins, trigger the weather Agent through
the CLI Channel and verify that the shared `AgentService` path:

1. invokes the real Provider through the OryxOS `ReActLoop`;
2. calls the allowlisted Open-Meteo endpoint through `http_get`;
3. appends ordered Assistant and Tool messages to one reusable CLI Session; and
4. returns model-generated clothing advice.

The packaged `oryxos chat` command is available for this stage. `AgentScheduler`
and `notify` remain part of the final Demo acceptance and must reuse the same
`AgentService` path.

Interactive CLI input and output use the active system console reader/writer so
Windows code pages preserve Chinese text; redirected input/output remains UTF-8.

Build and start the interactive command:

```powershell
mvn -pl oryxos-boot -am package
java -jar .\oryxos-boot\target\oryxos.jar chat `
  --profile weather `
  --workspace <directory-containing-.oryxos>
```

Credential-gated live Provider smoke tests accept `DEEPSEEK_API_KEY` and either
`KIMI_API_KEY` or `MOONSHOT_API_KEY`. Without those variables, deterministic
stub tests still run and the corresponding live smoke is reported as skipped.

Required behavior:

1. Cron trigger queries weather with `http_get`.
2. Agent generates clothing advice.
3. `notify` sends the result.
4. Manual invocation runs the same `AgentService` path.
5. Paginated REST Session lookup shows ordered messages and every LLM and Tool
   attempt, including failures, with matching total counts.

## 11. End-to-End Demo 2: Daily Technology Digest

Definition: `AGENT.md + skills/`.

Required behavior:

1. Agent reads a child Skill only when needed through `read_file`.
2. Agent invokes an MCP Tool through the unified registry.
3. Agent recalls/saves long-term memory.
4. Agent sends the digest through `notify`.
5. Scheduled and manual runs both succeed and are auditable.

## 12. End-to-End Demo 3: Daily GitHub Digest

Definition: `AGENT.md + scripts/`.

Required behavior:

1. Agent invokes an approved script through the `shell` Tool.
2. Script output, not script source, returns to the ReAct context.
3. Agent summarizes and sends the result.
4. Scheduled and manual runs both succeed and are auditable.
5. Documentation states that enabling Shell trusts the Agent author and is not strong isolation.

## 13. Final Release Gate

Before considering the feature complete:

```powershell
mvn clean package
java -jar .\oryxos-boot\target\oryxos.jar --version
java -jar .\oryxos-boot\target\oryxos.jar status --workspace $oryxWorkspace
```

On Linux, run the same artifact:

```bash
mvn clean package
java -jar ./oryxos-boot/target/oryxos.jar --version
java -jar ./oryxos-boot/target/oryxos.jar status --workspace "$oryxWorkspace"
```

Confirm all ten REST routes match `contracts/openapi.yaml`, all twelve CLI subcommands match `contracts/cli.md`, and no deferred expansion capability is described as implemented.

## 14. Timed New-Operator Acceptance

Use a clean supported Windows or Linux host with JDK 21 and Maven available.
The participant must not have contributed to OryxOS and may use only the public
README and this quickstart.

1. Start the timer before the participant first opens the deployment guide.
2. Build the nine-module reactor and initialize a fresh workspace.
3. Start the packaged service, pass the health check, create a Session, send one
   message with stubbed external services, and retrieve its Session/audit view.
4. Stop the timer only after the participant verifies the persisted Session,
   LLM calls, and Tool invocations.
5. Record OS, JDK, Maven, start/end timestamps, elapsed minutes, commands,
   obstacles, and outcome in `acceptance/operator-30-minute.md`.

The gate passes only when the elapsed time is at most 30 minutes without
undocumented assistance.
