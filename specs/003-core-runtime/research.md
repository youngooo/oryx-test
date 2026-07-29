# Phase 0 Research: OryxOS Core Runtime

## 1. Runtime Architecture

**Decision**: Use the existing nine Maven modules as one modular monolith and package only `oryxos-boot` as the production executable JAR.

**Rationale**: This preserves clear responsibilities while meeting the single-process, single-deployment core-stage requirement. A composition root in Boot allows implementation modules to depend inward on core ports without circular dependencies.

**Alternatives considered**:

- Separate deployable services: rejected because distributed coordination, deployment, and failure handling are outside the core stage.
- One flat Maven module: rejected because it erases the required module boundaries.
- Multiple executable CLI/server artifacts: rejected because the project contract requires one executable JAR.

## 2. Core Ports and Module Dependency Direction

**Decision**: Put runtime-facing interfaces in `oryxos-core` and implementations in Provider, Memory, Tool, and Storage modules. The core-facing memory port is named `MemoryService`; `oryxos-memory` provides its unified façade implementation. Keep Boot as the only module that sees all implementations.

**Rationale**: `ReActLoop` and `AgentService` need Provider, Tool, Memory, Session, and audit behavior. Defining contracts in Core prevents dependency cycles, while the concrete `MemoryService` façade ensures ReAct never separately queries Session storage and long-term memory.

**Alternatives considered**:

- Core directly depends on all implementation modules: rejected because Tool, Memory, and Storage already need core models.
- A separate “ports” Maven module: rejected as unnecessary tenth-module complexity.
- Shared static service locators: rejected because they hide dependencies and make tests unsafe.

## 3. Spring AI Boundary and Provider Selection

**Decision**: Use Spring AI/Spring AI Alibaba only for DeepSeek/Kimi protocol adaptation, Function Calling format conversion, and Tool Schema generation. Build an explicit configured map from Provider name to `ChatModel`; disable automatic Tool execution.

**Rationale**: Explicit mapping makes configuration errors fail fast and keeps OryxOS in control of every Tool invocation, Session mutation, and audit record.

**Alternatives considered**:

- Bean-type inference: rejected because multiple `ChatModel` beans become ambiguous.
- `ChatClient` automatic Tool execution: rejected because it can duplicate calls and bypass OryxOS audit/Sandbox.
- Provider fallback/hedging: deferred because policy, cost, and replay semantics are not specified.

**Dependency baseline (verified 2026-07-29)**:

- Keep Spring Boot at `3.5.7`.
- Pin Spring AI to `1.1.2` and import Spring AI Alibaba BOM `1.1.2.2`.
  Spring AI Alibaba `1.1.2.x` is built on Spring AI `1.1.2`, while Spring AI
  `1.1.x` supports Spring Boot `3.5.x`.
- Pin MCP Java SDK BOM to `2.0.0` and use `mcp-core` plus
  `mcp-json-jackson2`, matching this Spring Boot 3/Jackson 2 codebase instead
  of the Jackson 3 convenience bundle.
- Pin springdoc-openapi to `2.8.17`, the documented `2.8.x` line for Spring
  Boot `3.5.x`.
- Keep Picocli at `4.7.7` and sqlite-jdbc at `3.50.3.0`.
- Let the Spring Boot BOM manage SnakeYAML, Jackson, JUnit 5, Mockito, Spring
  MVC, and Spring Data JPA; do not add independent versions that can drift from
  Boot's tested dependency set.

The root POM owns these properties and BOM imports. Each module POM declares
only the dependencies for its responsibility, and the build verifies the
resolved graph plus application startup. Provider adapters remain isolated so
artifact changes cannot alter core contracts.

**Automatic Tool execution mechanism**: On the pinned Spring AI `1.1.2` line,
OryxOS calls `ChatModel` directly with
`DefaultToolCallingChatOptions.internalToolExecutionEnabled(false)` and does
not install a `ChatClient` Tool advisor or delegate execution to
`ToolCallingManager`. `FunctionCallingAdapter` supplies schemas only;
`ReActLoop` inspects returned Tool requests and the OryxOS `ToolExecutor` is the
sole dispatcher. Configuration and adapter tests fail if the effective option
is absent or true.

Official compatibility references:

- [Spring AI repository and Boot compatibility](https://github.com/spring-projects/spring-ai)
- [Spring AI 1.1.2 ToolCallingChatOptions API](https://docs.spring.io/spring-ai/docs/1.1.2/api/org/springframework/ai/model/tool/DefaultToolCallingChatOptions.Builder.html)
- [Spring AI Alibaba releases](https://github.com/alibaba/spring-ai-alibaba/releases)
- [MCP Java SDK releases](https://github.com/modelcontextprotocol/java-sdk/releases)
- [springdoc compatibility matrix](https://springdoc.org/faq.html)

## 4. Synchronous ReAct on Virtual Threads

**Decision**: Implement a synchronous, iterative `ReActLoop` and run server work on Java 21 virtual threads.

**Rationale**: The dominant operations are blocking LLM, HTTP, MCP, filesystem, and SQLite calls. Synchronous control flow preserves ordered Session updates and makes retries, limits, and auditing explicit.

**Alternatives considered**:

- WebFlux/reactive core: rejected because it adds a second concurrency model without a core-stage requirement.
- External Agent framework loop: rejected because it obscures termination, Tool scheduling, and audit behavior.
- Parallel Tool calls: deferred because ordered Tool results are part of the Session contract.

## 5. Session Concurrency and Archival

**Decision**: Serialize message acceptance and execution per Session using a keyed coordinator; allow different Sessions to execute concurrently. Archive is a terminal state, and archived Sessions reject further messages with a conflict response.

**Rationale**: This directly implements the clarified service-acceptance ordering while still using virtual-thread concurrency across Sessions.

**Alternatives considered**:

- Optimistic append with retries: rejected because it does not guarantee complete ReAct-turn ordering.
- Global lock: rejected because it unnecessarily serializes unrelated Sessions.
- Automatic Session reactivation: rejected because it weakens archive semantics.

## 6. SQLite Schema Management and Retention

**Decision**: Initialize a versioned, explicit SQL schema for `sessions`, `tool_invocations`, and `llm_calls`; use JPA repositories as adapters, not Hibernate schema generation as the production migration mechanism. Retain records until an administrator explicitly removes workspace data.

**Rationale**: An explicit schema is deterministic across restarts and makes constraints/indexes reviewable. Indefinite retention matches the clarified specification and avoids inventing expiry policy.

**Alternatives considered**:

- `ddl-auto=update`: rejected as insufficiently deterministic for production workspace data.
- Scheduled retention cleanup: rejected because no retention period is authorized.
- Separate audit database: rejected as unnecessary for a single-node core.

## 7. Message Representation

**Decision**: Persist ordered Session messages as JSON in the Session record for the core stage and persist LLM/Tool audit entries in normalized tables.

**Rationale**: The Session history is always loaded as an ordered aggregate, while audit records need independent querying and indexes. This matches the current technical schema without adding an unrequested messages table.

**Alternatives considered**:

- A normalized `messages` table: viable later, but deferred until query or scale requirements justify a migration.
- Filesystem Session history: rejected because restart recovery and API lookup belong in SQLite.

## 8. Three-Layer Memory Façade and Long-Term Backends

**Decision**: Expose exactly one `MemoryService` contract to `ReActLoop` and `PromptBuilder`. Its concrete façade composes Session memory through `SessionManager` and long-term memory through `LongTermMemoryStore`; the episodic layer remains an explicit extension-stage slot. Ship all three required long-term backends in the core stage:

- `MarkdownMemoryStore` as the default, operating on `MEMORY.md`.
- `SqliteMemoryStore`, operating on `memory_entries`.
- `Mem0MemoryStore`, integrating with a self-hosted Mem0 service through REST.

All backends implement `append(content, scope)`, `load()`, and `recallByKeyword(keyword)`. They preserve complete `CORE` memory, apply deterministic limits only to `ARCHIVAL`, perform no façade-level caching, and provide serialized append/read visibility.

**Rationale**: This follows the explicit architecture adjustment in `TechnicalSolution.md`: ReAct must see one three-layer memory concept, and changing `memory.backend` must not alter `MemoryService`, `PromptBuilder`, `MemoryTools`, or `ReActLoop`.

**Alternatives considered**:

- Let ReAct query Session storage and long-term memory separately: rejected because it destroys the required unified façade.
- Ship only Markdown and defer SQLite/Mem0: rejected because it is an unapproved deviation from the current technical solution.
- Let callers read/write any backend directly: rejected because it bypasses consistency and backend isolation.
- Inject unbounded memory: rejected because prompt size must remain deterministic.

**Self-hosted Mem0 compatibility boundary**: `Mem0MemoryStore` targets the
self-hosted Mem0 REST `v1` memory contract needed for add, list/load, and search.
The operator pins the Mem0 server/container version rather than tracking an
unbounded `latest` tag. Production self-hosting keeps authentication enabled and
supplies a per-user API key or Bearer access token through an environment
reference; OryxOS neither stores nor logs that value and does not use the admin
key for normal memory operations. The adapter uses a 3-second connect timeout
and 10-second response timeout inside the overall 60-second Agent deadline.
Startup validates the endpoint and authentication configuration, while runtime
unavailability returns the selected-backend failure required by FR-053 without
making persisted Session history unavailable. Acceptance stubs implement only
this pinned OryxOS-facing `v1` subset, so Mem0-internal semantic matching remains
an optional backend enhancement and never changes the shared keyword contract.

Official compatibility references:

- [Mem0 self-hosted REST API and authentication](https://docs.mem0.ai/open-source/features/rest-api)
- [Mem0 self-hosted project and release model](https://github.com/mem0ai/mem0)

## 9. Tool Model and Sandbox

**Decision**: Adapt built-in, MCP, and Java plugin Tools to `OryxTool`; validate JSON arguments, call `Sandbox.enforce`, execute, normalize the result, and write audit in one `ToolExecutor` path.

**Rationale**: A single path guarantees the same safety and audit policy regardless of Tool origin.

**Alternatives considered**:

- Separate executors by Tool origin: rejected because policy would drift.
- Java `SecurityManager`: rejected because it is deprecated/removed as a viable isolation design.
- Application whitelist described as strong isolation: rejected; it is explicitly a core-stage policy boundary, not a container sandbox.

## 10. Sandbox Validation Rules

**Decision**:

- Resolve filesystem paths to normalized absolute paths and verify they remain under permitted workspace roots; protect against traversal and symlink escape.
- Parse HTTP URLs and compare normalized resolved hosts against the allowlist; never use string-prefix matching.
- Tokenize/parse Shell invocation enough to validate the actual executable and reject unsupported compound constructs rather than trusting the raw string.

**Rationale**: These are the minimum controls needed to close the bypasses named in the Constitution and requirements.

**Alternatives considered**:

- Prefix checks for paths/URLs: rejected as bypassable.
- Arbitrary shell text filtering: rejected because substring checks do not identify the executed program.
- OS/container isolation: deferred to the expansion stage.

## 11. Agent Loading and Live Changes

**Decision**: Treat one directory as one Agent. Parse `AGENT.md` frontmatter into a runtime Profile, read the body and child resources from the Agent directory, and isolate invalid Agents without failing startup. Body/resource edits are visible on their next read; additions, removals, frontmatter, and schedule changes require explicit reload or restart.

**Rationale**: This matches the repository contract and avoids a second Profile YAML source of truth.

**Alternatives considered**:

- `.oryxos/profiles/*.yaml`: rejected as obsolete.
- Global Skill index: rejected because core Skills are Agent-local and loaded on demand.
- Always-on filesystem watcher: deferred because reload semantics are sufficient for core.

## 12. Scheduler Semantics

**Decision**: Use Spring `ThreadPoolTaskScheduler` and `CronTrigger`, with a per-task in-process lock. Do not persist scheduler state, do not overlap the same task, and do not replay triggers missed while the process was down.

**Rationale**: This is deterministic for a single process and implements the clarified downtime behavior without pretending to provide distributed scheduling.

**Alternatives considered**:

- Quartz/persistent schedules: deferred to the expansion stage.
- Distributed lock: rejected because there is no cluster in the core stage.
- Catch-up replay: rejected by clarification.

## 13. Stateless Agent Invocation and Audit

**Decision**: `POST /agents/{name}/invoke` creates an internal persisted invocation Session with a generated ID and returns that ID with the final response. The caller need not maintain conversational state, but audit records still have a Session anchor.

**Rationale**: This reconciles a stateless caller experience with mandatory Session/LLM/Tool audit and later API lookup.

**Alternatives considered**:

- No Session record: rejected because audit correlation would be incomplete.
- Reuse a global Agent Session: rejected because unrelated invocations would share ordering and history.

## 14. REST and Error Contract

**Decision**: Implement exactly the ten `/api/v1` endpoints with a common `ApiResponse {code,message,data,timestamp}` envelope, global exception mapping, 32 KiB message validation, 100-message query cap, and 60 s invocation timeout.

**Rationale**: A fixed contract supports CLI/API consistency and unambiguous acceptance tests.

**Alternatives considered**:

- SSE/WebSocket streaming: deferred.
- Per-controller error bodies: rejected because clients would need divergent handling.
- Authentication/RBAC: deferred to enterprise expansion.

## 15. CLI Startup and Single-JAR Packaging

**Decision**: `oryxos-boot` constructs the Picocli application and supplies a runtime launcher to heavy commands. Light commands avoid Spring startup when their dependencies are local filesystem/config readers.

**Rationale**: This preserves the single executable JAR while meeting the lightweight CLI startup goal and avoiding a CLI → Boot dependency cycle.

**Alternatives considered**:

- Start Spring for every command: rejected for the 500 ms light-command goal.
- Separate CLI executable: rejected by the deployment contract.
- Put command implementations in Boot: rejected because it collapses module responsibilities.

## 16. Testing and Acceptance

**Decision**: Use layered tests plus three packaged-JAR demos. Stub remote Providers, MCP, HTTP, and notification services for deterministic automated tests; retain opt-in live-provider smoke profiles for manual acceptance.

**Rationale**: Automated builds must not require credentials or unstable network services, but the feature must still demonstrate DeepSeek/Kimi compatibility and complete real workflows.

**Alternatives considered**:

- Live services in every build: rejected because it leaks cost, credentials, and nondeterminism.
- Unit tests only: rejected because module wiring, SQLite restart, CLI packaging, and REST contracts are central risks.

## Deferred Capabilities

The following are explicitly out of scope for this plan: multi-tenancy, SSO/RBAC, full audit-query UI/API, distributed scheduling or locking, container/microVM Sandbox, a custom vector-database implementation, episodic-memory retrieval, cross-node or nested Agent delegation, SSE/WebSocket streaming, rate limiting, and cluster high availability. The required self-hosted Mem0 adapter is in scope only through the `LongTermMemoryStore` contract.
