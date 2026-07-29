# Tasks: OryxOS Core Runtime

**Input**: Design documents from `specs/003-core-runtime/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`,
`contracts/`, and `quickstart.md`

**Tests**: Tests are required for every changed behavior and appear before the
implementation tasks they specify.

**Organization**: Tasks are grouped by the five prioritized User Stories. The
delivery order preserves the required vertical path:
Provider → ReAct → Memory → Tool → Web/CLI → integration acceptance.

## Format: `[ID] [P?] [Story?] Description`

- **[P]**: Task can proceed in parallel because it changes different files and
  has no incomplete dependency.
- **[Story]**: User Story mapping (`[US1]` through `[US5]`).
- Every task names exact repository-relative file paths.

## Phase 1: Setup

**Purpose**: Align dependency management, module POMs, configuration, and the
single executable-JAR assembly before feature work.

- [X] T001 Pin Spring Boot `3.5.7`, Spring AI `1.1.2`, Spring AI Alibaba BOM `1.1.2.2`, MCP Java SDK BOM `2.0.0`, springdoc `2.8.17`, Picocli `4.7.7`, and sqlite-jdbc `3.50.3.0`; retain Boot-managed SnakeYAML/Jackson/JUnit/Mockito versions; and configure Enforcer, Surefire, and Failsafe in `pom.xml`
- [X] T002 [P] Add `org.springframework.ai:spring-ai-starter-model-openai` and only the schema/protocol adapter dependencies needed for explicit DeepSeek/Kimi mappings, excluding Agent-framework or automatic Tool-execution starters, in `oryxos-provider/pom.xml`
- [X] T003 [P] Add Boot-managed Spring Web, Jackson, Spring Data JPA, and backend contract-test dependencies required by Markdown, SQLite, and self-hosted Mem0 adapters in `oryxos-memory/pom.xml`
- [X] T004 [P] Add `io.modelcontextprotocol.sdk:mcp-core` and `mcp-json-jackson2` from MCP BOM `2.0.0`, Boot-managed Spring Web/Jackson, and Tool test dependencies in `oryxos-tool/pom.xml`
- [X] T005 [P] Add Boot-managed Spring Data JPA and Hibernate community dialects, sqlite-jdbc `3.50.3.0`, explicit SQL initializer support, and storage test dependencies in `oryxos-storage/pom.xml`
- [X] T006 [P] Add Boot-managed Spring MVC/test dependencies and `org.springdoc:springdoc-openapi-starter-webmvc-ui` `2.8.17` in `oryxos-web/pom.xml`
- [X] T007 [P] Add Boot-managed Spring Context/Scheduling and SnakeYAML to `oryxos-core/pom.xml`, Picocli to `oryxos-channel-cli/pom.xml` and `oryxos-cli/pom.xml`, and Spring Boot aggregation/test dependencies to `oryxos-boot/pom.xml`
- [X] T008 Remove the separate shaded CLI artifact, wire the Boot JAR as the single production entrypoint, and add packaged-JAR test support in `oryxos-cli/pom.xml` and `oryxos-boot/pom.xml`
- [X] T009 Define virtual-thread, SQLite, Provider, Memory backend, Sandbox, MCP, scheduler, and redacted environment-placeholder configuration in `oryxos-boot/src/main/resources/application.yml`

**Checkpoint**: The nine-module reactor resolves dependencies and still has one
production executable assembly.

---

## Phase 2: Foundational

**Purpose**: Establish inward-facing models, ports, workspace loading, base
Session/audit schema and persistence, and architecture rules that block all User Stories.

- [X] T010 Add a module-boundary test covering the nine modules, inward dependency direction, single general Tool module, and Agent-as-context rule in `oryxos-boot/src/test/java/org/oryxos/boot/ModuleBoundaryTest.java`
- [X] T011 [P] Add domain-model validation tests for Profile, Session, Message, ToolResult, and audit values in `oryxos-core/src/test/java/org/oryxos/core/model/CoreModelTest.java`
- [X] T012 [P] Add workspace initialization idempotency and partial/conflicting-path tests in `oryxos-core/src/test/java/org/oryxos/core/workspace/WorkspaceInitializerTest.java`
- [X] T013 [P] Add Agent frontmatter, invalid-Agent isolation, reload, and context-order tests in `oryxos-core/src/test/java/org/oryxos/core/agent/AgentLoaderTest.java` and `oryxos-core/src/test/java/org/oryxos/core/agent/ContextLoaderTest.java`
- [X] T014 Create immutable AgentDefinition, Profile, ScheduleDefinition, Session, Message, ToolDefinition, ToolResult, ToolInvocationRecord, and LlmCallRecord models in `oryxos-core/src/main/java/org/oryxos/core/model/AgentDefinition.java`, `oryxos-core/src/main/java/org/oryxos/core/model/Profile.java`, `oryxos-core/src/main/java/org/oryxos/core/model/ScheduleDefinition.java`, `oryxos-core/src/main/java/org/oryxos/core/model/Session.java`, `oryxos-core/src/main/java/org/oryxos/core/model/Message.java`, `oryxos-core/src/main/java/org/oryxos/core/model/ToolDefinition.java`, `oryxos-core/src/main/java/org/oryxos/core/model/ToolResult.java`, `oryxos-core/src/main/java/org/oryxos/core/model/ToolInvocationRecord.java`, and `oryxos-core/src/main/java/org/oryxos/core/model/LlmCallRecord.java`
- [X] T015 Create ProviderGateway, MemoryService, ToolCatalog, Sandbox, SessionStore, InvocationAuditStore, and ClockProvider ports in `oryxos-core/src/main/java/org/oryxos/core/port/ProviderGateway.java`, `oryxos-core/src/main/java/org/oryxos/core/port/MemoryService.java`, `oryxos-core/src/main/java/org/oryxos/core/port/ToolCatalog.java`, `oryxos-core/src/main/java/org/oryxos/core/port/Sandbox.java`, `oryxos-core/src/main/java/org/oryxos/core/port/SessionStore.java`, `oryxos-core/src/main/java/org/oryxos/core/port/InvocationAuditStore.java`, and `oryxos-core/src/main/java/org/oryxos/core/port/ClockProvider.java`
- [X] T016 Define OryxTool, ToolExecutionContext, MemoryContext, MemoryScope, and LongTermMemoryView contracts in `oryxos-core/src/main/java/org/oryxos/core/tool/OryxTool.java`, `oryxos-core/src/main/java/org/oryxos/core/tool/ToolExecutionContext.java`, `oryxos-core/src/main/java/org/oryxos/core/memory/MemoryContext.java`, `oryxos-core/src/main/java/org/oryxos/core/memory/MemoryScope.java`, and `oryxos-core/src/main/java/org/oryxos/core/memory/LongTermMemoryView.java`
- [X] T017 Implement idempotent `.oryxos` workspace creation and starter files in `oryxos-core/src/main/java/org/oryxos/core/workspace/WorkspaceInitializer.java`
- [X] T018 Implement AgentLoader, ProfileRegistry, and ContextLoader with frontmatter-derived Profiles and no Agent-as-Tool registration in `oryxos-core/src/main/java/org/oryxos/core/agent/AgentLoader.java`, `oryxos-core/src/main/java/org/oryxos/core/agent/ProfileRegistry.java`, and `oryxos-core/src/main/java/org/oryxos/core/agent/ContextLoader.java`
- [X] T019 Add foundational SQLite schema, Session create/load/append/restart, LLM/Tool audit write, and session-scoped paged audit read tests in `oryxos-storage/src/test/java/org/oryxos/storage/CorePersistenceFoundationTest.java`
- [X] T020 Create schema version marker 1 plus `sessions`, `llm_calls`, and `tool_invocations` tables, foreign keys, constraints, and correlation/order indexes in `oryxos-storage/src/main/resources/db/schema.sql`
- [X] T021 Implement the base Session entity, repository, JSON conversion, and SessionStore adapter needed by the first vertical slice in `oryxos-storage/src/main/java/org/oryxos/storage/entity/SessionEntity.java`, `oryxos-storage/src/main/java/org/oryxos/storage/repository/SessionRepository.java`, `oryxos-storage/src/main/java/org/oryxos/storage/adapter/SessionJsonConverter.java`, and `oryxos-storage/src/main/java/org/oryxos/storage/adapter/JpaSessionStore.java`
- [X] T022 Implement LLM/Tool audit entities, repositories, durable write methods, and session-scoped `(startedAt,id)` paged read/count methods behind InvocationAuditStore in `oryxos-storage/src/main/java/org/oryxos/storage/entity/LlmCallEntity.java`, `oryxos-storage/src/main/java/org/oryxos/storage/entity/ToolInvocationEntity.java`, `oryxos-storage/src/main/java/org/oryxos/storage/repository/LlmCallRepository.java`, `oryxos-storage/src/main/java/org/oryxos/storage/repository/ToolInvocationRepository.java`, and `oryxos-storage/src/main/java/org/oryxos/storage/adapter/JpaInvocationAuditStore.java`

**Checkpoint**: Core contracts compile, workspace initialization is idempotent,
valid Agents load independently, the base Session/LLM/Tool schema survives restart,
and no implementation module dependency cycle exists.

---

## Phase 3: User Story 1 — Use a Configured LLM Provider (P1) 🎯 MVP

**Goal**: A minimal Agent uses an explicitly named DeepSeek or Kimi Provider,
returns a no-Tool response, and writes a safe LLM-call audit record.

**Independent Test**: Invoke the same minimal Profile through DeepSeek and Kimi
stub models and observe the selected Provider/model and complete success/failure
audit data without exposing credentials or silently failing over.

### Required Tests for User Story 1

- [X] T023 [P] [US1] Add explicit DeepSeek/Kimi mapping, unknown Provider, duplicate name, and no-fallback tests in `oryxos-provider/src/test/java/org/oryxos/provider/ProviderServiceTest.java`
- [X] T024 [P] [US1] Add missing/invalid credential and secret-redaction configuration tests in `oryxos-provider/src/test/java/org/oryxos/provider/ProviderConfigurationTest.java`
- [X] T025 [P] [US1] Add Function Calling conversion tests proving Spring AI 1.1.2 effective options set `internalToolExecutionEnabled(false)`, no Tool advisor/manager dispatches calls, and schema conversion remains execution-free in `oryxos-provider/src/test/java/org/oryxos/provider/FunctionCallingAdapterTest.java`
- [X] T026 [US1] Add LLM-call persistence tests for success, failure, optional token counts, duration, and restart recovery in `oryxos-storage/src/test/java/org/oryxos/storage/LlmCallPersistenceTest.java`
- [X] T027 [US1] Add a Provider vertical-slice test with both named stub models and zero real credentials plus credential-gated live DeepSeek/Kimi smoke invocations of the same no-Tool Agent through `AgentService` and `ReActLoop` in `oryxos-boot/src/test/java/org/oryxos/boot/ProviderAcceptanceTest.java`

### Implementation for User Story 1

- [X] T028 [US1] Implement immutable Provider request/response conversion and safe error categories in `oryxos-provider/src/main/java/org/oryxos/provider/adapter/ProviderRequestMapper.java` and `oryxos-provider/src/main/java/org/oryxos/provider/adapter/ProviderResponseMapper.java`
- [X] T029 [US1] Implement explicit provider-name-to-ChatModel configuration with fail-fast validation and redaction in `oryxos-provider/src/main/java/org/oryxos/provider/config/ProviderConfiguration.java`
- [X] T030 [US1] Implement ProviderService as the ProviderGateway adapter and prevent silent fallback in `oryxos-provider/src/main/java/org/oryxos/provider/service/ProviderService.java`
- [X] T031 [US1] Implement schema-only Function Calling adaptation with Spring AI automatic Tool execution disabled in `oryxos-provider/src/main/java/org/oryxos/provider/adapter/FunctionCallingAdapter.java`
- [X] T032 [US1] Integrate ProviderService success and failure attempt lifecycles with the foundational `InvocationAuditStore` in `oryxos-provider/src/main/java/org/oryxos/provider/service/ProviderService.java`

**Checkpoint**: The US1 Provider implementation slice is independently verified
through both named Providers and provides the minimal Provider MVP. Formal story
completion still requires the applicable T120–T122 constitutional evidence.

---

## Phase 4: User Story 2 — Complete Multi-Step Work with ReAct (P2)

**Goal**: OryxOS owns prompt assembly, model calls, Tool dispatch, ordered result
feedback, and bounded termination through one AgentService path.

**Independent Test**: Run no-Tool, one HTTP Tool, multiple Tool rounds, Tool
failure, and maximum-iteration conversations through CLI with every message and
audit record ordered.

### Required Tests for User Story 2

- [X] T033 [P] [US2] Add PromptBuilder order, current-time, Tool filtering, history bound, and oversized-context tests in `oryxos-core/src/test/java/org/oryxos/core/prompt/PromptBuilderTest.java`
- [X] T034 [P] [US2] Add ReAct no-Tool, one-Tool, multi-round, sequential multi-call, malformed response, and max-iteration tests in `oryxos-core/src/test/java/org/oryxos/core/react/ReActLoopTest.java`
- [X] T035 [P] [US2] Add ToolExecutor lookup, argument-validation, Sandbox rejection, retryability, timeout, and exactly-once audit tests in `oryxos-core/src/test/java/org/oryxos/core/tool/ToolExecutorTest.java`
- [X] T036 [P] [US2] Add ToolRegistry duplicate-name and Profile-subset tests in `oryxos-tool/src/test/java/org/oryxos/tool/registry/ToolRegistryTest.java`
- [X] T037 [US2] Add HTTP GET allowlist, redirect-host, timeout, large-result, and malformed-response tests in `oryxos-tool/src/test/java/org/oryxos/tool/builtin/HttpGetToolTest.java`
- [X] T038 [US2] Add an AgentService concurrency/context-cleanup and shared-entry-path test in `oryxos-core/src/test/java/org/oryxos/core/service/AgentServiceTest.java`
- [X] T039 [US2] Add interactive CLI no-Tool/HTTP-Tool/max-iteration acceptance tests in `oryxos-channel-cli/src/test/java/org/oryxos/channel/cli/CliChannelAcceptanceTest.java`

### Implementation for User Story 2

- [X] T040 [US2] Implement deterministic prompt assembly through ContextLoader and MemoryService in `oryxos-core/src/main/java/org/oryxos/core/prompt/PromptBuilder.java`
- [X] T041 [US2] Implement the synchronous bounded ReAct state machine with ordered message accumulation exclusively through MemoryService, without a SessionStore dependency, in `oryxos-core/src/main/java/org/oryxos/core/react/ReActLoop.java`
- [X] T042 [US2] Implement Tool lookup, schema validation, Sandbox enforcement, result normalization, and retry bounds in `oryxos-core/src/main/java/org/oryxos/core/tool/ToolExecutor.java`
- [X] T043 [US2] Implement the shared Tool registry without splitting the Tool Maven module in `oryxos-tool/src/main/java/org/oryxos/tool/registry/ToolRegistry.java`
- [X] T044 [US2] Implement the core WhitelistSandbox action model and parsed-host validation in `oryxos-tool/src/main/java/org/oryxos/tool/sandbox/SandboxAction.java` and `oryxos-tool/src/main/java/org/oryxos/tool/sandbox/WhitelistSandbox.java`
- [X] T045 [US2] Implement the first built-in `http_get` OryxTool with bounded safe output in `oryxos-tool/src/main/java/org/oryxos/tool/builtin/HttpGetTool.java`
- [X] T046 [US2] Integrate ToolExecutor validation, Sandbox rejection, success, retryable failure, and terminal failure paths with the foundational `InvocationAuditStore` in `oryxos-core/src/main/java/org/oryxos/core/tool/ToolExecutor.java`
- [X] T047 [US2] Implement AgentService as the sole CLI/Web/scheduler orchestration entry and guarantee context cleanup in `oryxos-core/src/main/java/org/oryxos/core/service/AgentService.java`
- [X] T048 [US2] Implement the interactive CliChannel adapter, Picocli `chat` command, and Boot-composed US1/US2 chat runtime with StageMemoryService as the ReAct-facing Session façade in `oryxos-channel-cli/src/main/java/org/oryxos/channel/cli/CliChannel.java`, `oryxos-cli/src/main/java/org/oryxos/cli/command/ChatCommand.java`, and `oryxos-boot/src/main/java/org/oryxos/boot/StageRuntimeConfiguration.java`

**Checkpoint**: The US2 ReAct implementation slice is independently verified
over the US1 Provider foundation; OryxOS—not Spring AI—dispatches every Tool
request. Formal story completion still requires the applicable T120–T122
constitutional evidence.

---

## Phase 5: User Story 3 — Retain Conversation and Long-Term Memory (P3)

**Goal**: Durable Sessions and a unified three-layer MemoryService preserve
conversation and selected facts across restarts with three interchangeable
long-term-memory backends.

**Independent Test**: Save and recall the same preference through Markdown,
SQLite, and a stubbed self-hosted Mem0 endpoint; restart the runtime; query the
Session; and prove concurrency, archive, failure, and no-auto-migration rules.

### Required Tests for User Story 3

- [X] T049 [P] [US3] Add a unified MemoryService composition test proving ReAct-facing callers receive Session plus long-term memory through one contract in `oryxos-memory/src/test/java/org/oryxos/memory/DefaultMemoryServiceTest.java`
- [X] T050 [P] [US3] Add a reusable append/load/keyword/scope/truncation/concurrency contract suite covering empty and over-32-KiB rejection plus same-scope duplicate idempotency in `oryxos-memory/src/test/java/org/oryxos/memory/LongTermMemoryStoreContract.java`
- [X] T051 [P] [US3] Apply the shared contract to Markdown, SQLite, and stubbed Mem0 backends in `oryxos-memory/src/test/java/org/oryxos/memory/MarkdownMemoryStoreTest.java`, `oryxos-memory/src/test/java/org/oryxos/memory/SqliteMemoryStoreTest.java`, and `oryxos-memory/src/test/java/org/oryxos/memory/Mem0MemoryStoreTest.java`
- [X] T052 [P] [US3] Add backend selection, unknown value, unavailable/malformed backend, Mem0 REST v1 and auth configuration, 3-second connect/10-second response timeout, no-silent-omission, self-hosted endpoint, redaction, and no-auto-migration tests in `oryxos-memory/src/test/java/org/oryxos/memory/MemoryBackendConfigurationTest.java`
- [X] T053 [US3] Add Session create/reuse/restart/archive/retention and 100-message-query tests in `oryxos-storage/src/test/java/org/oryxos/storage/SessionPersistenceTest.java`
- [X] T054 [US3] Add same-Session acceptance-order serialization and different-Session concurrency tests in `oryxos-core/src/test/java/org/oryxos/core/session/SessionCoordinatorTest.java`
- [X] T055 [US3] Add `save_memory`/`recall_memory` schema, scope, failure, and shared `ToolRegistry` registration tests in `oryxos-memory/src/test/java/org/oryxos/memory/tool/MemoryToolsTest.java` and `oryxos-boot/src/test/java/org/oryxos/boot/MemoryAcceptanceTest.java`
- [X] T056 [US3] Add a default-Markdown Agent-level two-conversation acceptance proving an audited `save_memory` call and a later response using the remembered Spring Boot/K8s preference, plus an all-three-backend append/recreate/keyword-recall acceptance flow using a real SQLite file and stubbed self-hosted Mem0 in `oryxos-boot/src/test/java/org/oryxos/boot/MemoryAcceptanceTest.java`

### Implementation for User Story 3

- [X] T057 [US3] Extend the explicit SQLite schema with version marker 2, memory entries, constraints, and indexes without redefining the foundational Session/LLM/Tool tables in `oryxos-storage/src/main/resources/db/schema.sql`
- [X] T058 [US3] Complete the foundational Session persistence adapter with JSON conversion, optimistic updates, active-Session identity lookup, bounded message queries, idempotent archive, and retention behavior in `oryxos-storage/src/main/java/org/oryxos/storage/adapter/SessionJsonConverter.java` and `oryxos-storage/src/main/java/org/oryxos/storage/adapter/JpaSessionStore.java`
- [X] T059 [US3] Implement per-Session serialization, active-Session identity lookup, immutable archive, and retention behavior in `oryxos-core/src/main/java/org/oryxos/core/session/SessionManager.java` and `oryxos-core/src/main/java/org/oryxos/core/session/SessionCoordinator.java`
- [X] T060 [US3] Define the backend-neutral LongTermMemoryStore contract in `oryxos-memory/src/main/java/org/oryxos/memory/LongTermMemoryStore.java`
- [X] T061 [US3] Replace the US1/US2 stage façade with the complete unified MemoryService façade containing Session, long-term, and empty episodic layers in `oryxos-memory/src/main/java/org/oryxos/memory/DefaultMemoryService.java`
- [X] T062 [US3] Implement core/archival Markdown parsing, serialized append, deterministic archival truncation, and keyword recall in `oryxos-memory/src/main/java/org/oryxos/memory/markdown/MarkdownMemoryStore.java`
- [X] T063 [US3] Implement MemoryEntry persistence and the SQLite backend in `oryxos-memory/src/main/java/org/oryxos/memory/sqlite/MemoryEntryEntity.java`, `oryxos-memory/src/main/java/org/oryxos/memory/sqlite/MemoryEntryRepository.java`, and `oryxos-memory/src/main/java/org/oryxos/memory/sqlite/SqliteMemoryStore.java`
- [X] T064 [US3] Implement the self-hosted Mem0 REST v1 adapter for add/load/search with environment-supplied API-key or Bearer authentication, 3-second connect/10-second response timeouts, pinned-server compatibility validation, and credential redaction in `oryxos-memory/src/main/java/org/oryxos/memory/mem0/Mem0MemoryStore.java`
- [X] T065 [US3] Implement fail-fast `memory.backend` selection with Markdown default and no implicit migration in `oryxos-memory/src/main/java/org/oryxos/memory/config/MemoryBackendConfiguration.java`
- [X] T066 [US3] Implement `save_memory` and `recall_memory` as memory-owned OryxTools registered through the shared catalog in `oryxos-memory/src/main/java/org/oryxos/memory/tool/MemoryTools.java`
- [X] T067 [US3] Integrate unified memory loading, core-memory overflow failure, and archive rejection into `oryxos-core/src/main/java/org/oryxos/core/prompt/PromptBuilder.java` and `oryxos-core/src/main/java/org/oryxos/core/service/AgentService.java`

**Checkpoint**: The US3 Memory implementation slice is independently verified
for all three backends; ReAct and PromptBuilder have no direct backend
dependency. Formal story completion still requires the applicable T120–T122
constitutional evidence.

---

## Phase 6: User Story 4 — Extend Agents with Safe Tools (P4)

**Goal**: Nine built-in Tools plus three explicitly distinguished Plugin routes
share one catalog, one execution pipeline, explicit Sandbox policy, audit, and
notification. This independently testable slice also establishes non-overlapping
scheduler primitives and convergence of scheduled and internal manual triggers.

**Independent Test**: Exercise each built-in Tool and separate pre-existing
external-server, custom external-server, and Java Plugin fixtures against every
applicable Spec Tool-acceptance-matrix assertion. Separately prove cron parsing,
single-process non-overlap, failure isolation, no catch-up, and convergence of
scheduled and internal manual triggers on AgentService.

### Required Tests for User Story 4

- [X] T068 [P] [US4] Add normalized path, traversal, symlink escape, missing path, and write-boundary tests in `oryxos-tool/src/test/java/org/oryxos/tool/builtin/FileToolsTest.java`
- [X] T069 [P] [US4] Add executable-token, argument, working-directory, compound-syntax, timeout, and output-limit tests in `oryxos-tool/src/test/java/org/oryxos/tool/builtin/ShellToolTest.java`
- [X] T070 [P] [US4] Add POST body/header redaction, parsed-host, redirect, timeout, and response-limit tests in `oryxos-tool/src/test/java/org/oryxos/tool/builtin/HttpPostToolTest.java`
- [X] T071 [P] [US4] Add notification target, adapter failure, retry classification, and redaction tests in `oryxos-tool/src/test/java/org/oryxos/tool/notify/NotifyToolTest.java`
- [X] T072 [P] [US4] Add distinct pre-existing-server and custom-server MCP fixtures covering discovery, duplicate names, schema mapping, success, invalid arguments where applicable, stdio/HTTP transport failure normalization, result normalization, and audit in `oryxos-tool/src/test/java/org/oryxos/tool/mcp/McpClientServiceTest.java`
- [X] T073 [P] [US4] Add Java `@Tool` discovery, schema mapping, success, invalid-argument rejection, thrown-exception normalization, OryxTool adaptation, and audit tests in `oryxos-tool/src/test/java/org/oryxos/tool/plugin/JavaPluginToolAdapterTest.java`
- [X] T074 [US4] Add acceptance for all nine built-ins and separately reported pre-existing external-server, custom external-server, and Java Plugin fixtures against every applicable Spec Tool-acceptance-matrix assertion in `oryxos-boot/src/test/java/org/oryxos/boot/ToolAcceptanceTest.java`
- [X] T075 [US4] Add cron parsing, per-task non-overlap, failure isolation, missed-trigger no-replay, and scheduled/internal-manual trigger convergence tests in `oryxos-core/src/test/java/org/oryxos/core/scheduling/AgentSchedulerTest.java`

### Implementation for User Story 4

- [X] T076 [US4] Complete symlink-aware workspace, actual-command, and parsed-host policies in `oryxos-tool/src/main/java/org/oryxos/tool/sandbox/WhitelistSandbox.java`
- [X] T077 [US4] Implement `read_file`, `write_file`, and `list_dir` with bounded outputs in `oryxos-tool/src/main/java/org/oryxos/tool/builtin/ReadFileTool.java`, `oryxos-tool/src/main/java/org/oryxos/tool/builtin/WriteFileTool.java`, and `oryxos-tool/src/main/java/org/oryxos/tool/builtin/ListDirTool.java`
- [X] T078 [US4] Implement structured executable-plus-arguments Shell invocation in `oryxos-tool/src/main/java/org/oryxos/tool/builtin/ShellTool.java`
- [X] T079 [US4] Implement `http_post` and harden shared HTTP redirect/host handling in `oryxos-tool/src/main/java/org/oryxos/tool/builtin/HttpPostTool.java` and `oryxos-tool/src/main/java/org/oryxos/tool/builtin/HttpClientPolicy.java`
- [X] T080 [US4] Implement NotifyChannelAdapter, WebhookNotifyAdapter, and `notify` Tool in `oryxos-tool/src/main/java/org/oryxos/tool/notify/NotifyChannelAdapter.java`, `oryxos-tool/src/main/java/org/oryxos/tool/notify/WebhookNotifyAdapter.java`, and `oryxos-tool/src/main/java/org/oryxos/tool/notify/NotifyTool.java`
- [X] T081 [US4] Implement the shared MCP configuration/transport path used by both pre-existing and custom external Tool servers, including discovery, invocation, and OryxTool adaptation, in `oryxos-tool/src/main/java/org/oryxos/tool/mcp/McpServerConfiguration.java`, `oryxos-tool/src/main/java/org/oryxos/tool/mcp/McpClientService.java`, and `oryxos-tool/src/main/java/org/oryxos/tool/mcp/McpToolAdapter.java`
- [X] T082 [US4] Implement Java Spring Bean Tool discovery and adaptation without enabling Spring AI dispatch in `oryxos-tool/src/main/java/org/oryxos/tool/plugin/JavaPluginToolAdapter.java`
- [X] T083 [US4] Register built-in, Memory, MCP, and Java Plugin Tools with duplicate-name failure and Profile filtering in `oryxos-tool/src/main/java/org/oryxos/tool/registry/ToolRegistry.java`
- [X] T084 [US4] Implement cron scheduling, timezone handling, scheduler Session identity, per-task ReentrantLock, failure isolation, and no catch-up replay in `oryxos-core/src/main/java/org/oryxos/core/scheduling/AgentScheduler.java`
- [X] T085 [US4] Parse and register Agent schedule definitions only on startup or explicit reload in `oryxos-core/src/main/java/org/oryxos/core/agent/AgentLoader.java` and `oryxos-core/src/main/java/org/oryxos/core/agent/ProfileRegistry.java`
- [X] T086 [US4] Route scheduled invocations and the internal manual-trigger adapter through AgentService in `oryxos-core/src/main/java/org/oryxos/core/scheduling/ScheduledAgentInvoker.java`
- [X] T087 [P] [US4] Add credential-free technology-digest and GitHub-digest stage fixtures in `oryxos-boot/src/test/resources/demos/tech-digest/AGENT.md`, `oryxos-boot/src/test/resources/demos/tech-digest/skills/digest-format.md`, `oryxos-boot/src/test/resources/demos/github-digest/AGENT.md`, and `oryxos-boot/src/test/resources/demos/github-digest/scripts/github-digest.ps1`
- [X] T088 [US4] Add internal AgentService stage acceptance proving the technology digest follows `AGENT.md` → `read_file` → `skills/digest-format.md` → news MCP → seeded Memory-only fact and emits that fact, while the GitHub digest follows `AGENT.md` → `shell` → Agent-local script and emits a unique script-output marker, with ordered Tool audit assertions, in `oryxos-boot/src/test/java/org/oryxos/boot/AgentResourceStageAcceptanceTest.java`

**Checkpoint**: The US4 Tool, scheduler, and Agent-resource stage slice is
independently verified through both internal AgentService Demo paths. Formal
story completion still requires the applicable T120–T122 constitutional
evidence.

---

## Phase 7: User Story 5 — Integrate Through Web and CLI Contracts (P5)

**Goal**: Ten REST operations and twelve CLI subcommands expose the same Agent,
Session, Memory, Tool, health, and runtime data through the shared AgentService,
including public manual replay of an Agent that also has a schedule.

**Independent Test**: Exercise all documented REST and CLI contracts for
success, validation, archived/not-found, Provider/Tool failure, timeout, and
safe error output; manually replay a scheduled Agent through both the CLI
conversation path and Agent invocation contract and verify the same persisted
Session and invocation-audit state.

### Required Tests for User Story 5

- [X] T089 [P] [US5] Add ApiResponse serialization and global validation/not-found/conflict/provider/tool/timeout/internal error mapping tests in `oryxos-web/src/test/java/org/oryxos/web/GlobalExceptionHandlerTest.java`
- [X] T090 [P] [US5] Add Session create/message/get/archive, 32-KiB and 100-message limits, independent LLM/Tool audit pagination and totals, deterministic audit ordering, invalid page inputs, concurrency, and archived-message contract tests in `oryxos-web/src/test/java/org/oryxos/web/SessionApiControllerTest.java`
- [X] T091 [P] [US5] Add stateless Agent invocation, scheduled-Agent manual replay, and internal Session/audit correlation tests in `oryxos-web/src/test/java/org/oryxos/web/AgentApiControllerTest.java`
- [X] T092 [P] [US5] Add Profile, Memory, Tool, health, and info read-contract tests in `oryxos-web/src/test/java/org/oryxos/web/ReadApiControllerTest.java`
- [X] T093 [P] [US5] Add OpenAPI path/operation/schema consistency tests, including the SessionDetail LLM/Tool audit projection and page metadata, against `specs/003-core-runtime/contracts/openapi.yaml` in `oryxos-web/src/test/java/org/oryxos/web/OpenApiContractTest.java`
- [X] T094 [P] [US5] Add Picocli help, exit-code, JSON output, redaction, all-twelve-command, and scheduled-Agent replay through the `chat` conversation path tests in `oryxos-cli/src/test/java/org/oryxos/cli/OryxOsCommandTest.java`
- [X] T095 [US5] Add controlled timed tests asserting light CLI commands start without Spring in under 500 ms and the packaged service reaches health in at most 4 seconds in `oryxos-boot/src/test/java/org/oryxos/boot/CliBootIntegrationTest.java`
- [X] T096 [US5] Add packaged-JAR REST/CLI shared-state and restart smoke tests in `oryxos-boot/src/test/java/org/oryxos/boot/PackagedRuntimeAcceptanceTest.java`

### Implementation for User Story 5

- [X] T097 [US5] Implement ApiResponse, public DTOs, validation constraints, and safe error codes in `oryxos-web/src/main/java/org/oryxos/web/api/ApiResponse.java`, `oryxos-web/src/main/java/org/oryxos/web/api/SessionDtos.java`, and `oryxos-web/src/main/java/org/oryxos/web/api/AgentDtos.java`
- [X] T098 [US5] Implement global exception handling for validation, missing, archived, Provider, Tool, timeout, and internal failures in `oryxos-web/src/main/java/org/oryxos/web/error/GlobalExceptionHandler.java`
- [X] T099 [US5] Implement the four Session lifecycle operations, including bounded message history and independently paginated LLM/Tool audit projection with totals, in `oryxos-web/src/main/java/org/oryxos/web/controller/SessionApiController.java`
- [X] T100 [US5] Implement stateless Agent invocation, including manual replay of an Agent that also has a schedule, with a persisted internal Session in `oryxos-web/src/main/java/org/oryxos/web/controller/AgentApiController.java`
- [X] T101 [US5] Implement Profile, Memory, and Tool read operations in `oryxos-web/src/main/java/org/oryxos/web/controller/ProfileApiController.java`, `oryxos-web/src/main/java/org/oryxos/web/controller/MemoryApiController.java`, and `oryxos-web/src/main/java/org/oryxos/web/controller/ToolApiController.java`
- [X] T102 [US5] Extend health/info operations with safe database, Provider, Agent, build, and capability state in `oryxos-web/src/main/java/org/oryxos/web/SystemApiController.java`
- [X] T103 [US5] Publish the ten-operation OpenAPI document and enforce the 60-second invocation deadline in `oryxos-web/src/main/java/org/oryxos/web/config/OpenApiConfiguration.java` and `oryxos-web/src/main/java/org/oryxos/web/config/InvocationTimeoutConfiguration.java`
- [X] T104 [US5] Implement ConfigLoader and shared CLI output/error conventions in `oryxos-cli/src/main/java/org/oryxos/cli/config/ConfigLoader.java` and `oryxos-cli/src/main/java/org/oryxos/cli/output/CliOutput.java`
- [X] T105 [US5] Implement the remaining `init`, `status`, `serve`, and `gateway` commands in `oryxos-cli/src/main/java/org/oryxos/cli/command/InitCommand.java`, `oryxos-cli/src/main/java/org/oryxos/cli/command/StatusCommand.java`, `oryxos-cli/src/main/java/org/oryxos/cli/command/ServeCommand.java`, and `oryxos-cli/src/main/java/org/oryxos/cli/command/GatewayCommand.java`
- [X] T106 [US5] Implement `profile list/create/show/delete` with Agent-directory semantics and delete confirmation in `oryxos-cli/src/main/java/org/oryxos/cli/command/ProfileCommand.java`
- [X] T107 [US5] Implement `provider list`, `tool list`, and `session list` commands in `oryxos-cli/src/main/java/org/oryxos/cli/command/ProviderCommand.java`, `oryxos-cli/src/main/java/org/oryxos/cli/command/ToolCommand.java`, and `oryxos-cli/src/main/java/org/oryxos/cli/command/SessionCommand.java`
- [X] T108 [US5] Refactor the Picocli root to expose exactly twelve subcommands and accept a Boot-supplied RuntimeLauncher in `oryxos-cli/src/main/java/org/oryxos/cli/OryxOsCommand.java` and `oryxos-cli/src/main/java/org/oryxos/cli/runtime/RuntimeLauncher.java`
- [X] T109 [US5] Compose all nine modules, keep light commands context-free, and launch heavy commands through Spring in `oryxos-boot/src/main/java/org/oryxos/boot/OryxOsApplication.java` and `oryxos-boot/src/main/java/org/oryxos/boot/SpringRuntimeLauncher.java`
- [X] T110 [US5] Update the public project page to distinguish implemented core capabilities from extension-stage plans in `website/index.html` and `website/README.md`

**Checkpoint**: The US5 public-contract implementation slice is complete when
these tasks pass. Full constitutional completion of all five User Stories still
requires the Final Phase evidence, including T112 and T120–T122.

---

## Final Phase: Documentation, Demos, Security, and Quality Gates

- [ ] T111 [P] Add the weather Agent fixture and promote the credential-free technology-digest and GitHub-digest stage fixtures unchanged into final scheduled/public-replay acceptance in `oryxos-boot/src/test/resources/demos/weather/AGENT.md`, `oryxos-boot/src/test/resources/demos/tech-digest/AGENT.md`, `oryxos-boot/src/test/resources/demos/tech-digest/skills/digest-format.md`, `oryxos-boot/src/test/resources/demos/github-digest/AGENT.md`, and `oryxos-boot/src/test/resources/demos/github-digest/scripts/github-digest.ps1`
- [ ] T112 Add scheduled and public-manual-replay end-to-end acceptance for all three Demos, retaining the technology-digest `read_file`/news-MCP/seeded-Memory-only-fact assertions and GitHub-digest Agent-local Shell-script/unique-output-marker assertions, with paginated Session lookup proving every persisted LLM/Tool attempt is retrievable and totals match in `oryxos-boot/src/test/java/org/oryxos/boot/DemoAcceptanceTest.java`
- [ ] T113 [P] Add credential-pattern, log, API payload, Tool result, Provider, MCP, and Mem0 redaction acceptance tests in `oryxos-boot/src/test/java/org/oryxos/boot/SecretSafetyAcceptanceTest.java`
- [ ] T114 [P] Add a controlled load profile that runs ten Agents for four continuous hours with 100 concurrent Sessions, asserts no Agent/Session data loss, p99 Session creation below 200 ms, and internal forwarding overhead below 50 ms; retain 1,000 Sessions as an opt-in stress profile in `oryxos-boot/src/test/java/org/oryxos/boot/RuntimeLoadTest.java`
- [ ] T115 [P] Add equivalent packaged-JAR workspace/init/status/serve/health/Session/shutdown smoke scripts and a Windows/Linux CI matrix in `scripts/acceptance/runtime-smoke.ps1`, `scripts/acceptance/runtime-smoke.sh`, and `.github/workflows/cross-platform.yml`
- [ ] T116 Reconcile implemented commands, API audit pagination, Memory modes, security boundaries, Windows/Linux commands, the US4 internal technology/GitHub stage runs, three final Demo instructions, and the timed new-operator protocol in `README.md`, `docs/TechnicalSolution.md`, `docs/AiProgrammingGuide.md`, `docs/CliGuide.md`, and `specs/003-core-runtime/quickstart.md`
- [ ] T117 Run the four-hour/100-Session performance profile, capture p99 Session creation and internal forwarding measurements, and record pass/fail evidence against every threshold in `specs/003-core-runtime/acceptance/performance.md`
- [ ] T118 Run the same packaged JAR smoke flow on supported Windows and Linux hosts and record OS, JDK, commands, artifact checksum, timing, and results in `specs/003-core-runtime/acceptance/cross-platform.md`
- [ ] T119 Conduct a clean-host trial with a participant who did not contribute to OryxOS, allow only the published guide, require build/init/start/health/Session/audit verification within 30 minutes, and record timestamps, obstacles, assistance, and outcome in `specs/003-core-runtime/acceptance/operator-30-minute.md`
- [ ] T120 Run affected-module test suites, including `AgentResourceStageAcceptanceTest`, and record commands/results in `specs/003-core-runtime/quickstart.md`
- [ ] T121 Run the packaged-JAR smoke workflow and all three scheduled/public-replay Demo workflows and record evidence in `specs/003-core-runtime/quickstart.md`
- [ ] T122 Run `mvn clean package` from `pom.xml`, resolve only feature-related regressions, and record the final nine-module build result in `specs/003-core-runtime/quickstart.md`
- [ ] T123 Run Spec Kit consistency analysis over `specs/003-core-runtime/spec.md`, `specs/003-core-runtime/plan.md`, and `specs/003-core-runtime/tasks.md`, then resolve all HIGH/CRITICAL findings in those files

---

## Dependencies & Execution Order

### Phase Dependencies

```text
Setup
  └── Foundational
       └── US1 Provider
            └── US2 ReAct
                 └── US3 Memory
                       └── US4 Tool and Scheduler
                            └── US5 Web, CLI, and public manual replay
                                 └── Final acceptance
```

- Setup has no dependencies.
- Foundational depends on Setup and blocks all User Stories.
- US1 depends only on Foundational and is the suggested MVP.
- US2 depends on US1 because ReAct requires the Provider boundary.
- US3 depends on US2 because Memory is composed into the proven prompt/ReAct
  path.
- The US4 implementation slice depends on US2 and US3 because it completes the
  Tool catalog, includes MemoryTools, and establishes internal scheduling.
- The US5 implementation slice depends on the US4 implementation slice—not on
  US4's final constitutional evidence—so every public trigger observes the same
  execution, persistence, Memory, Tool, and audit behavior.
- Public manual replay belongs to US5; T112 later proves scheduled and public
  manual triggers converge end to end for all three Demos.
- Final acceptance depends on all five stories and the constitutional evidence
  in T120–T122.

### User Story Dependency Summary

| Story | Depends On | Independently Verifiable Outcome |
|---|---|---|
| US1 Provider | Foundation | DeepSeek and Kimi named calls with safe LLM audit |
| US2 ReAct | US1 | Ordered no-/single-/multi-Tool loop with bounded termination |
| US3 Memory | US2 | Restart-safe Session plus three backend-neutral memory modes |
| US4 Tool | US2, US3 | Nine built-ins, three separately accepted Plugin routes, Sandbox, audit, scheduler foundation, and internal technology/GitHub Agent-resource stage runs |
| US5 Web/CLI | US4 implementation slice | Ten REST operations and twelve CLI commands over shared state, including public manual replay |

## Parallel Opportunities

- **Setup**: T002–T007 can proceed in parallel after T001.
- **Foundation**: T011–T013 can proceed in parallel; T014–T018 establish core
  contracts/workspace loading, then T019 tests must precede T020–T022 base persistence.
- **US1**: T023–T025 can proceed in parallel; storage test T026 can proceed
  alongside Provider adapter tests.
- **US2**: T033–T036 can proceed in parallel before implementation; HTTP Tool
  work T037/T045 is isolated from PromptBuilder/ReAct work T040/T041.
- **US3**: T049–T052 can proceed in parallel; backend implementations
  T062–T064 can proceed in parallel only after T060 and the shared contract
  suite T050 are complete.
- **US4**: T068–T073 can proceed in parallel; built-in, notification, MCP, and
  plugin implementations T077–T082 touch separate packages; T087 fixture work
  can begin after those Tool paths exist and blocks T088 stage acceptance.
- **US5**: T089–T094 can proceed in parallel; controller implementations
  T099–T102 and CLI command groups T105–T107 touch separate files.
- **Final**: T111, T113, T114, and T115 can proceed in parallel before the
  performance, cross-platform, operator, Demo, and build evidence tasks.

## Parallel Execution Examples

### User Story 1

```text
T023 Provider mapping tests
T024 Provider credential/redaction tests
T025 schema-only Function Calling tests
T026 LLM audit persistence tests
```

### User Story 2

```text
T033 PromptBuilder tests
T034 ReActLoop tests
T035 ToolExecutor tests
T036 ToolRegistry tests
```

### User Story 3

```text
T051 Markdown/SQLite/Mem0 contract adapters
  after T050 shared backend contract
T062 MarkdownMemoryStore
T063 SqliteMemoryStore
T064 Mem0MemoryStore
  after T060 LongTermMemoryStore
```

### User Story 4

```text
T068 File Tool security tests
T069 Shell Tool security tests
T070 HTTP POST security tests
T071 Notification tests
T072 MCP tests
T073 Java Plugin tests
T087 technology/GitHub Agent-resource stage fixtures
T088 internal AgentService stage acceptance after T087
```

### User Story 5

```text
T089 Global error contract tests
T090 Session API tests
T091 Agent API tests
T092 Read API tests
T093 OpenAPI consistency tests
T094 CLI contract tests
```

## Implementation Strategy

### MVP First

1. Complete Setup and Foundation.
2. Complete US1 and demonstrate the same minimal Agent against DeepSeek and
   Kimi stubs/live opt-in profiles.
3. Stop and validate Provider routing, error handling, secret safety, and audit
   before adding ReAct.

### Incremental Delivery

1. Add US2 as the first runnable CLI Agent loop with only `http_get`.
2. Add US3 behind the unified MemoryService and prove each backend with the
   shared contract suite.
3. Add the remaining Tool, Plugin, notification, Sandbox, and scheduler
   capabilities in US4, then prove the technology/GitHub Agent-resource stage
   flows through the internal AgentService path.
4. Expose the proven shared runtime through REST and the complete CLI in US5.
5. Run all Demos, security checks, packaged-JAR checks, and the full Maven build.

## Notes

- Preserve the nine-module boundary and the `org.oryxos` package root.
- General Tool infrastructure remains in `oryxos-tool`; MemoryTools remain in
  `oryxos-memory` and use the shared registry.
- Agent directories, `AGENT.md`, AgentLoader, and ContextLoader are context
  sources, never Tools.
- Spring AI automatic Tool execution remains disabled.
- ReAct and PromptBuilder access Session and long-term memory only through
  MemoryService.
- Core-stage Mem0 integration targets a self-hosted endpoint; OryxOS does not
  add an embedded vector database.
- Every test uses stubs or temporary workspaces by default; live credentials are
  opt-in and never committed.
