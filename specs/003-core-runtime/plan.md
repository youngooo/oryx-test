# Implementation Plan: OryxOS Core Runtime

**Branch**: `main` | **Date**: 2026-07-29 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification in `specs/003-core-runtime/spec.md` and architecture in `docs/TechnicalSolution.md`

## Summary

Deliver the first runnable OryxOS core as a JDK 21, Spring Boot 3.x, nine-module Maven monolith packaged as one executable JAR. The implementation follows one vertical execution path—Provider → ReAct → Memory/Tool → Web/CLI → integration acceptance—so CLI, REST, and scheduler triggers all converge on `AgentService`.

The core owns the ReAct loop and Tool scheduling. Spring AI/Spring AI Alibaba are limited to Provider protocol adaptation, Function Calling conversion, and Tool Schema generation; automatic Tool execution is disabled. Sessions, LLM calls, and Tool invocations are persisted in SQLite; Agent definitions remain workspace files; long-term memory uses the selected Markdown, SQLite, or self-hosted Mem0 backend behind one contract.

## Technical Context

**Language/Version**: Java 21
**Primary Dependencies**: Spring Boot 3.5.7; Spring AI 1.1.2; Spring AI Alibaba 1.1.2.2; MCP Java SDK BOM 2.0.0 (`mcp-core` + `mcp-json-jackson2`); springdoc-openapi 2.8.17; Picocli 4.7.7; sqlite-jdbc 3.50.3.0; Spring MVC, Spring Data JPA, SnakeYAML, Jackson, JUnit 5, and Mockito managed by the Spring Boot/Spring AI BOMs
**Storage**: `.oryxos/oryxos.db` (SQLite) for Session, audit data, and the optional SQLite memory backend; `.oryxos/agents/` for Agent definitions; selectable Markdown, SQLite, or self-hosted Mem0 long-term memory backend
**Testing**: JUnit 5, Mockito, Spring Boot Test, MockMvc, temporary workspaces, provider/MCP HTTP stubs, packaged-JAR smoke tests on Windows and Linux, timed CLI/service startup checks, four-hour/100-Session load acceptance, and a timed novice-operator deployment trial
**Target Platform**: Windows and Linux single-node private deployment
**Project Type**: Maven multi-module monolith, one executable JAR
**Performance Goals**: light CLI commands start within 500 ms; core service starts within 2–4 s; session APIs remain responsive with 100 concurrent Sessions; design can be stress-tested at 1,000 concurrent Sessions
**Constraints**: synchronous blocking core flow on Java virtual threads; 60 s invocation timeout; self-hosted Mem0 connect timeout 3 s and per-request response timeout 10 s; default 10 ReAct iterations; message input at most 32 KiB; injected Session history at most 20 messages; Session query at most 100 messages; no plaintext secrets; no Spring AI automatic Tool execution
**Scale/Scope**: single process; multiple Agents sharing one runtime; 9 built-in Tools; 10 REST endpoints; 12 CLI subcommands; DeepSeek and Kimi acceptance coverage; 3 end-to-end demos

## Constitution Check

### Before Design

| Gate | Status | Evidence |
|---|---|---|
| Requirements and contracts first | PASS | `spec.md` contains five independently testable stories, clarified edge cases, 59 functional requirements, and 17 measurable success criteria. |
| Nine modules, one deployment unit | PASS | Existing parent POM declares the required nine modules; `oryxos-boot` remains the sole final executable assembly. |
| Core-runtime-first delivery | PASS | Plan excludes RBAC, SSO, multi-tenancy, distributed scheduling, custom vector-database infrastructure, container/microVM isolation, SSE, and cross-node delegation; the explicitly required self-hosted Mem0 adapter remains in scope. |
| OryxOS owns ReAct and Tool execution | PASS | `ReActLoop` and `ToolExecutor` are core services; automatic Tool execution in Spring AI is explicitly disabled. |
| Unified Tool and enforceable Sandbox boundary | PASS | Built-in, MCP, and plugin Tools adapt to `OryxTool`; every call passes validation and `Sandbox.enforce` before execution. |
| Durable audit and safe memory | PASS | Sessions, `tool_invocations`, and `llm_calls` persist from the first vertical slice; long-term memory is accessed only through a port. |
| Tests and demos are mandatory | PASS | Each phase has unit/integration gates and the final phase runs the three specified demos plus `mvn clean package`. |

## Architecture

### Dependency Direction

`oryxos-core` defines runtime models and ports and depends on no other OryxOS module. Provider, Memory, Tool, Storage, Channel, Web, and CLI modules depend inward on those ports. `oryxos-boot` is the only composition root and aggregates all implementations.

```text
oryxos-boot
├── oryxos-web ───────────────┐
├── oryxos-cli ───────────────┤
├── oryxos-channel-cli ───────┤
├── oryxos-provider ──────────┤
├── oryxos-memory ────────────┼──> oryxos-core
├── oryxos-tool ──────────────┤
└── oryxos-storage ───────────┘
```

Core ports include Provider access, Tool catalog/execution dependencies, the core-facing `MemoryService` contract, Session persistence, and audit persistence. `oryxos-memory` supplies the concrete unified `MemoryService` façade and depends inward on those contracts. This prevents cycles such as Core → Memory → Core while ensuring ReAct sees one memory API rather than separate Session and long-term-memory paths.

### Unified Execution Flow

```text
CLI / REST / Scheduler
        |
        v
   AgentService
        |
        +--> AgentLoader + ContextLoader
        +--> MemoryService
        |       +--> SessionManager --> SessionStore
        |       +--> LongTermMemory --> LongTermMemoryStore
        |       `--> EpisodicMemory (extension-stage slot)
        |
        v
     ReActLoop
        |
        +--> PromptBuilder --> MemoryService.loadContext
        |                  --> ProviderGateway --> LLM audit
        |
        +--> ToolExecutor --> validate --> Sandbox --> OryxTool --> Tool audit
        |
        `--> MemoryService.appendSessionMessage --> ordered Session append
                                             `--> termination
```

`ReActLoop` and `PromptBuilder` obtain complete memory context only through `MemoryService`; `ReActLoop` also routes every generated Assistant and Tool message through `MemoryService.appendSessionMessage`. They do not query or receive `SessionStore`, `MEMORY.md`, `memory_entries`, or Mem0 directly. The façade composes the three-layer model: Session memory and long-term memory are implemented in the core stage, while the episodic-memory slot remains an extension boundary.

An Agent directory is context, not a Tool. `AgentLoader` and `ContextLoader` remain in `oryxos-core`; `AGENT.md` body content is injected into the system prompt, while Agent-local skills, references, and scripts are accessed on demand through existing `read_file` or `shell` Tools. No Agent directory, `AGENT.md`, or loader is registered as `OryxTool`.

All writes for one Session are serialized by a per-Session coordinator in service-acceptance order. Different Sessions may run concurrently on virtual threads. An archived Session is immutable and rejects new messages.

### Executable JAR and CLI Startup

`oryxos-boot` owns the executable main class and constructs the Picocli root command. Light commands (`init`, metadata/list operations that can read local configuration directly) avoid creating the Spring context where practical. Runtime commands (`chat`, `serve`, `gateway`, scheduled execution) start the shared Spring composition through a launcher supplied by `oryxos-boot`, avoiding a reverse dependency from `oryxos-cli` to Boot.

## Project Structure

### Documentation and Planning Artifacts

```text
specs/003-core-runtime/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── agent-definition.md
│   ├── cli.md
│   ├── openapi.yaml
│   └── tool.md
└── checklists/
    └── requirements.md
```

### Source Code

```text
oryxos-core/
└── src/{main,test}/java/org/oryxos/core/
    ├── agent/
    ├── model/
    ├── port/
    ├── prompt/
    ├── react/
    ├── scheduling/
    └── service/

oryxos-provider/
└── src/{main,test}/java/org/oryxos/provider/
    ├── config/
    ├── adapter/
    └── service/

oryxos-memory/
└── src/{main,test}/java/org/oryxos/memory/
    ├── DefaultMemoryService.java
    ├── markdown/
    ├── sqlite/
    ├── mem0/
    ├── service/
    └── tool/

oryxos-tool/
└── src/{main,test}/java/org/oryxos/tool/
    ├── builtin/
    ├── mcp/
    ├── notify/
    ├── plugin/
    ├── registry/
    └── sandbox/

oryxos-channel-cli/
└── src/{main,test}/java/org/oryxos/channel/cli/

oryxos-web/
└── src/{main,test}/java/org/oryxos/web/
    ├── api/
    ├── controller/
    └── error/

oryxos-storage/
└── src/{main,test}/
    ├── java/org/oryxos/storage/
    │   ├── adapter/
    │   ├── entity/
    │   └── repository/
    └── resources/db/

oryxos-cli/
└── src/{main,test}/java/org/oryxos/cli/
    ├── command/
    ├── config/
    └── runtime/

oryxos-boot/
└── src/{main,test}/
    ├── java/org/oryxos/boot/
    └── resources/
```

**Structure Decision**: Preserve the nine existing Maven modules and introduce packages only as real responsibilities appear. Unit tests stay beside their owning module; cross-module, packaged-JAR, and demo tests live in `oryxos-boot`.

## Delivery Phases

### Phase 1 — Foundation, Provider, and Minimal ReAct

1. Align parent/module POMs, dependency management, compiler/test plugins, and the executable Boot assembly.
2. Define core value objects and inward-facing ports, including immutable Tool/LLM results and Session messages.
3. Implement workspace `init`, Agent directory parsing, frontmatter validation, Profile derivation, and explicit reload behavior.
4. Create the foundational SQLite schema for Sessions, LLM calls, and Tool
   invocations, then implement base Session and invocation-audit adapters before
   any Provider vertical slice.
5. Implement explicit Provider-name-to-`ChatModel` mapping for DeepSeek and Kimi,
   disable automatic Tool execution, and persist every Provider attempt through
   the foundational audit adapter.
6. Implement `PromptBuilder`, `ReActLoop`, iteration limits, termination rules,
   and in-memory test adapters.
7. Prove a CLI-to-`AgentService` no-Tool conversation before expanding breadth.

**Gate**: Base Session/LLM/Tool schema restart tests, Provider selection and LLM
audit tests, no-Tool/single-Tool/max-iteration ReAct tests, invalid Agent
isolation tests, and a runnable CLI smoke test.

### Phase 2 — Complete Session, Memory, Tool, Audit, and Scheduler Foundation

1. Complete per-Session serialization, active identity lookup, archive behavior,
   retention semantics, bounded history queries, and restart recovery on the
   foundational Session persistence.
2. Extend the explicit schema only for Memory entries; do not move or recreate
   the Session/LLM/Tool tables established by Phase 1.
3. Implement the unified `MemoryService` façade. It composes Session memory from `SessionManager` with long-term memory from `LongTermMemoryStore`, returns one complete `MemoryContext` to ReAct/PromptBuilder, and retains the episodic-memory extension slot without implementing episodic retrieval in the core stage.
4. Implement all three required `LongTermMemoryStore` backends behind the same contract: `MarkdownMemoryStore` (default), `SqliteMemoryStore`, and the self-hosted `Mem0MemoryStore` REST adapter. Switching `memory.backend` must not change `MemoryService`, `PromptBuilder`, `MemoryTools`, or `ReActLoop`.
5. Implement `OryxTool`, `ToolRegistry`, schema/argument validation, `WhitelistSandbox`, and all nine built-in Tools.
6. Add MCP discovery/invocation adaptation, Java plugin Tool adaptation, and notification channel adapters.
7. Integrate every successful or failed Tool attempt with the foundational
   invocation-audit adapter without persisting secrets.
8. Implement only the internal scheduler foundation: frontmatter cron parsing,
   task-level single-process non-overlap, failure isolation, no catch-up replay,
   and convergence of scheduled and internal manual triggers on `AgentService`.
   Public CLI/API manual replay and end-to-end scheduled acceptance remain after
   the Web/CLI contracts are complete.
9. Add two Agent-level stage fixtures and run them through the internal manual
   trigger and shared `AgentService` path. The technology digest must follow its
   `AGENT.md`, read `skills/digest-format.md` on demand through `read_file`,
   invoke a news MCP Tool, and combine the result with a distinctive fact
   seeded only in Memory. The GitHub digest must invoke its Agent-local
   `scripts/github-digest.ps1` through `shell` and use a unique marker in the
   script output as the deterministic data source. This stage gate does not
   require the public REST/CLI replay contracts.

**Gate**: restart recovery, concurrent same/different Session tests, a default-Markdown Agent-level two-conversation acceptance proving ReAct actively calls `save_memory` and a new Session response uses the remembered Spring Boot/K8s preference, a test proving ReAct obtains Session plus long-term memory through only `MemoryService`, append–adapter-recreation–keyword-recall contract tests shared by all three long-term-memory backends, shared `ToolRegistry` registration assertions for both Memory Tools, backend-switching tests, the complete nine-built-in Tool matrix, separately reported pre-existing external-server, custom external-server, and Java Plugin fixtures, Tool success/validation/failure/retry/audit tests, path traversal/command/HTTP-host security tests, Memory ordering tests, cron parsing, task-level non-overlap, scheduler failure isolation, missed-trigger no-replay, scheduled/internal-manual trigger convergence, a technology-digest stage run proving `AGENT.md` → `read_file` → `skills/digest-format.md` → news MCP → Memory, and a GitHub-digest stage run proving `AGENT.md` → `shell` → Agent-local script → deterministic script output.

### Phase 3 — Web Service and CLI Contract Completion

1. Implement the ten versioned Spring MVC endpoints from `contracts/openapi.yaml`.
2. Add `ApiResponse`, global exception mapping, message/history limits, invocation timeout, and OpenAPI publication.
3. Complete the twelve Picocli subcommands while retaining a single Boot JAR.
4. Route REST and every CLI execution path through the same `AgentService`.

**Gate**: MockMvc contract tests for success, validation, conflict, timeout, and not-found cases; CLI exit-code/output tests for all command groups; and public manual replay through both the CLI conversation path and Agent invocation contract proving convergence on `AgentService` with queryable Session and invocation audit data.

### Phase 4 — Scheduler Integration and Release Acceptance

1. Connect the Phase 2 scheduler foundation to the completed CLI/API trigger
   surfaces; do not introduce a second Agent execution path.
2. Prove scheduled runs use the same Session/audit path as public manual
   replays and continue to avoid replaying missed triggers after downtime.
3. Add the weather fixture and reuse the Phase 2 technology-digest and
   GitHub-digest fixtures without changing their skill/script behavior.
4. Verify scheduled execution and public manual replay for every demo,
   including Session and invocation lookup through the API. For the technology
   digest, retain the exact `skills/digest-format.md`, news MCP, and Memory
   assertions; for the GitHub digest, retain the Agent-local Shell-script and
   deterministic-output assertions.
5. Complete security review, documentation synchronization, exact performance
   thresholds, Windows/Linux packaged-JAR smoke tests, the timed new-operator
   deployment trial, and the full Maven build.

**Gate**: all three end-to-end demos, no-overlap scheduler test,
next-fire-after-restart test, credential/log review, performance evidence,
Windows/Linux smoke evidence, a successful deployment/verification trial in at
most 30 minutes, and `mvn clean package`.

## Verification Strategy

- **Unit**: Prompt ordering/truncation, Agent frontmatter parsing, Provider mapping, ReAct state transitions, unified `MemoryService` composition, Tool validation, Sandbox normalization, Memory serialization, scheduler locks.
- **Module integration**: SQLite repositories and schema, the shared `LongTermMemoryStore` contract against Markdown/SQLite/Mem0 adapters, Spring AI adapters with stub models, MCP and Mem0 adapters with stub servers, notification adapters, Picocli commands.
- **Web contract**: MockMvc against all ten paths and the shared response/error schema.
- **Concurrency**: same-Session ordering, cross-Session parallelism, concurrent memory append/read visibility, and non-overlapping scheduled tasks.
- **Performance**: enforce p99 Session creation below 200 ms, internal forwarding overhead below 50 ms, light CLI startup below 500 ms, service startup at or below 4 s, and a four-hour run with ten Agents and 100 concurrent Sessions; keep 1,000 Sessions as an opt-in stress profile.
- **Cross-platform**: run the same packaged JAR, workspace initialization, lightweight CLI, service health, Session, and shutdown smoke flow on current Windows and Linux runners.
- **Operability**: conduct a clean-host, timed trial in which a first-time operator follows only the published guide and deploys and verifies one node within 30 minutes; retain elapsed-time and obstacle evidence.
- **Security**: `../` and symlink escape attempts, parsed-host allowlist bypasses, shell token/argument bypasses, redaction, and absent plaintext credentials.
- **End to end**: first run the technology and GitHub stage fixtures through
  the internal `AgentService` path; after Web/CLI completion, run the packaged
  JAR in a temporary workspace and execute and inspect all three demos through
  both scheduled and public manual paths with API-visible Session and invocation
  evidence.

## Post-Design Constitution Check

| Gate | Status | Design Result |
|---|---|---|
| Contracts remain authoritative | PASS | OpenAPI, CLI, Agent, and Tool contracts are written before implementation tasks. |
| Module boundaries remain valid | PASS | Core ports make all implementation dependencies point inward; Boot is the sole composition root. |
| Scope remains core-only | PASS | Deferred capabilities are documented in `research.md` and absent from contracts. |
| ReAct ownership is explicit | PASS | No external Agent loop or automatic Tool executor participates in the runtime path. |
| Tool safety is universal | PASS | All Tool origins converge before validation, Sandbox, execution, and audit. |
| Storage and memory satisfy durability | PASS | `MemoryService` is the single ReAct-facing façade; Markdown, SQLite, and self-hosted Mem0 backends share one contract covering ordering, scope, recall, and backend substitution. |
| Verification is release-blocking | PASS | Phase gates culminate in three demos, exact performance checks, Windows/Linux smoke, a timed operator trial, and a full clean package. |

## Complexity Tracking

No Constitution violation requires an exception. The small set of core ports is justified by multiple concrete implementations or explicit extension boundaries: two LLM Providers, three Tool origins, three required long-term-memory backends behind one `MemoryService` façade, and replaceable persistence adapters.
