# Feature Specification: OryxOS Core Runtime

**Feature Branch**: `main`

**Created**: 2026-07-29

**Status**: Draft

**Input**: User description: "Transform docs/DemandAnalysis.md into a Spec Kit specification with exactly 5 user stories."

## Clarifications

### Session 2026-07-29

- Q: How should concurrent messages targeting the same Session be handled? → A: Serialize them in service-acceptance order; different Sessions may execute concurrently.
- Q: What should happen when a client sends a message to an archived Session? → A: Reject the message and instruct the client to create a new Session.
- Q: How should schedules handle trigger times missed while OryxOS is stopped? → A: Do not catch up automatically; resume at the next scheduled time and use manual replay when needed.
- Q: How long should core-stage Session, Tool Invocation, and LLM Call records be retained? → A: Retain them until an administrator explicitly deletes the workspace data; do not expire them automatically.
- Q: How should concurrent reads and appends to shared long-term memory behave? → A: Serialize appends; reads observe all appends that completed before the read began.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Use a Configured LLM Provider (Priority: P1)

As an Agent author, I want an Agent to use a named language-model Provider
without depending on a particular vendor so that I can select an appropriate
model for each business Agent and keep enterprise deployment choices open.

**Why this priority**: Every Agent response depends on a working model
connection. The remaining runtime capabilities cannot deliver user value until
the Provider boundary is reliable.

**Independent Test**: Configure the same minimal Agent once with DeepSeek and
once with Kimi, send a message that requires no Tool, and verify that both
configurations return a model response while preserving the selected Provider
and model in the call record.

**Acceptance Scenarios**:

1. **Given** a valid Agent definition and Provider credential, **When** the
   Agent receives a text message, **Then** it returns a response from the named
   Provider and records the Provider, model, duration, and available token
   usage.
2. **Given** two configured Providers with the same general protocol, **When**
   the Agent's Provider selection changes, **Then** the Agent can use the new
   Provider without changing its business instructions.
3. **Given** a missing or invalid Provider credential, **When** a user invokes
   the Agent, **Then** the request fails clearly without exposing the credential
   or silently selecting another Provider.
4. **Given** two Providers are configured concurrently, **When** different
   Agents invoke them, **Then** each Agent consistently uses its explicitly
   named Provider.

---

### User Story 2 - Complete Multi-Step Work with ReAct (Priority: P2)

As an Agent user, I want the Agent to decide when to use Tools, incorporate
their results, and continue reasoning until it can answer so that multi-step
work does not require a hard-coded workflow.

**Why this priority**: ReAct is the execution core that turns model responses
into an Agent capable of completing real tasks.

**Independent Test**: Start a CLI conversation with an Agent that has one safe
HTTP Tool, ask for current external information, and verify the ordered
sequence of user message, model Tool request, Tool result, follow-up model
response, and final answer.

**Acceptance Scenarios**:

1. **Given** a request that needs no Tool, **When** the Agent processes it,
   **Then** it returns the model's final response without executing a Tool.
2. **Given** a request that needs one Tool, **When** the model requests that
   Tool, **Then** OryxOS validates and executes it once, adds its result to the
   conversation, and returns the model's final response.
3. **Given** a request requiring multiple Tool rounds, **When** each result is
   returned, **Then** the Agent preserves message order and continues until a
   final response is produced.
4. **Given** the Agent reaches its configured maximum number of iterations,
   **When** no final response has been produced, **Then** execution stops with a
   clear bounded-loop outcome.
5. **Given** messages arrive from CLI, Web Service, or a schedule, **When** they
   trigger the same Agent, **Then** they follow the same execution path and
   produce the same audit behavior.

---

### User Story 3 - Retain Conversation and Long-Term Memory (Priority: P3)

As a returning user, I want an Agent to remember conversation context and
selected long-term facts across restarts so that I do not need to repeat
preferences, project background, or prior decisions.

**Why this priority**: Durable memory distinguishes an Agent runtime from a
stateless chatbot and enables continuity in enterprise work.

**Independent Test**: Tell an Agent a durable preference, save it to long-term
memory, restart OryxOS, begin a later conversation, and verify that the Agent
can recall and use that preference while the earlier Session remains
queryable. Repeat the save/load/keyword-recall contract against each supported
long-term-memory mode without changing the Agent instructions or interaction
flow.

The default Markdown acceptance fixture MUST use this observable two-conversation
flow: first tell the Agent "我项目用 Spring Boot，部署在 K8s 上" and verify that
the ReAct loop actively invokes `save_memory`; recreate the memory adapter or
start a new Session; then ask "帮我看看我的项目能用什么数据库" and verify that the
final response explicitly uses both the remembered Spring Boot and K8s context.
Direct Store calls alone do not satisfy this Agent-level acceptance. The
backend-neutral contract additionally runs append, adapter recreation, and
keyword recall against Markdown, SQLite, and self-hosted Mem0.

**Acceptance Scenarios**:

1. **Given** an active conversation, **When** the same Channel, user, and Agent
   identity sends another message, **Then** the Agent continues with the same
   Session history.
2. **Given** a completed message exchange, **When** OryxOS restarts, **Then**
   the Session and its ordered messages can be restored.
3. **Given** a fact selected for long-term memory, **When** the Agent saves and
   later recalls it by keyword, **Then** the fact remains available across
   conversations.
4. **Given** conversation or long-term context exceeds its configured core-stage
   limit, **When** a prompt is assembled, **Then** early content is bounded
   predictably while recent conversation and relevant retained facts remain
   usable.
5. **Given** a Session is archived, **When** a client queries it, **Then** its
   historical messages remain available; when a client sends a new message to
   that Session, OryxOS rejects it and instructs the client to create a new
   Session.
6. **Given** multiple messages target the same active Session concurrently,
   **When** OryxOS accepts them, **Then** it processes them serially in
   acceptance order while allowing unrelated Sessions to proceed concurrently.
7. **Given** Session and invocation records have aged, **When** no administrator
   has explicitly deleted workspace data, **Then** the records remain available
   and are not removed by an automatic retention timer.
8. **Given** multiple accepted long-term memory appends, **When** they arrive
   concurrently, **Then** OryxOS serializes them without losing content, and a
   read observes every append completed before that read began.
9. **Given** an operator selects the default document mode, local structured
   mode, or self-hosted managed-memory mode, **When** the same Agent saves and
   recalls a fact, **Then** all modes preserve the same core/archival scope and
   keyword-recall contract.
10. **Given** the selected long-term-memory mode is unavailable or invalid,
    **When** an Agent requires memory context, **Then** the request fails
    clearly without silently omitting long-term memory or losing the readable
    Session history.
11. **Given** an operator changes the selected memory mode, **When** existing
    facts remain in the previous mode, **Then** OryxOS does not copy them
    automatically and clearly identifies the newly selected mode as the
    authoritative source.

---

### User Story 4 - Extend Agents with Safe Tools (Priority: P4)

As an Agent author, I want built-in and Plugin Tools to appear through one
consistent Tool catalog with explicit safety controls so that Agents can act on
files, services, scripts, memory, and notifications without bypassing runtime
governance.

**Why this priority**: Tools create practical business value, but unsafe or
duplicated execution can directly affect enterprise systems.

**Independent Test**: Exercise the nine built-in Tools plus distinct fixtures
for a pre-existing external Tool server, an operator-authored external Tool
server, and an in-process Java Plugin Tool. Verify each applicable cell in the
Tool acceptance matrix below, including an audit record for every attempt.
Then run two Agent-level stage scenarios through the internal manual trigger
and shared Agent execution path, without depending on the public REST or CLI
replay contracts: a technology-digest Agent reads its local formatting skill,
uses a news Tool server and Memory, while a GitHub-digest Agent executes its
own deterministic data-collection script through the Shell Tool.

**Acceptance Scenarios**:

1. **Given** an Agent with an allowed built-in Tool, **When** the model supplies
   valid arguments, **Then** OryxOS executes the Tool once and returns a
   structured success result.
2. **Given** invalid Tool arguments or a disallowed file path, command, or HTTP
   host, **When** execution is requested, **Then** OryxOS rejects the call
   before side effects and records the failed attempt.
3. **Given** an independently supplied, pre-existing external Tool server,
   **When** it is configured without changing that server, **Then** the Agent
   can discover and invoke it through the same Tool catalog used for built-in
   Tools.
4. **Given** an operator-authored external Tool server represented by a
   separate configuration and fixture, **When** it is connected through the
   external Tool-server contract, **Then** its Tools expose the same schema,
   invocation result, failure, and audit behavior as the pre-existing-server
   route.
5. **Given** an in-process Java Plugin Tool, **When** it is registered, **Then**
   it exposes
   the same discoverable name, description, input contract, result contract,
   and audit behavior as other Tools.
6. **Given** a retryable Tool failure, **When** retry policy applies, **Then**
   the call is retried no more than the configured limit; non-retryable failures
   return immediately.
7. **Given** an Agent schedule becomes due, **When** it triggers Tool-using
   work, **Then** the same scheduled task cannot overlap itself in one running
   instance and the scheduled trigger enters the same Agent execution path used
   by manual triggers.
8. **Given** schedule times are missed while OryxOS is stopped, **When** the
   service restarts, **Then** it waits for the next scheduled time rather than
   automatically replaying missed executions.
9. **Given** a technology-digest Agent whose `AGENT.md` directs it to use
   `skills/digest-format.md`, **When** it runs through the internal manual
   trigger, **Then** it calls `read_file` for that Agent-local skill only when
   needed, invokes the configured news MCP Tool, combines the returned news
   with a distinctive fact seeded only in Memory, produces a digest that
   reflects that fact, and records each Tool attempt.
10. **Given** a GitHub-digest Agent with a script in its own `scripts/`
    directory, **When** it runs through the internal manual trigger, **Then**
    it calls `shell` to execute that script within the approved workspace,
    treats the script output as the deterministic GitHub data source, and
    records the Shell attempt before generating the digest.

#### Tool Acceptance Matrix

| Tool origin | Distinct acceptance fixture | Required acceptance |
|---|---|---|
| Nine built-in Tools | One fixture for every named built-in Tool | Discovery and success for every Tool; argument validation where inputs are constrained; workspace, command, or parsed-host rejection for file, Shell, and HTTP Tools; adapter/transport failure for HTTP and notification Tools; retry classification for retry-capable operations; audit for every attempt |
| Pre-existing external Tool server | Independently supplied server configured without changing its implementation | Discovery, schema mapping, successful invocation, transport failure normalization, and audit |
| Operator-authored external Tool server | Separate custom-server configuration and fixture | Discovery, schema mapping, successful invocation, invalid-argument rejection, transport failure normalization, and audit |
| In-process Java Plugin Tool | Java Plugin fixture registered in process | Discovery, schema mapping, successful invocation, invalid-argument rejection, thrown-exception normalization, and audit |

---

### User Story 5 - Integrate OryxOS Through Web and CLI Contracts (Priority: P5)

As an enterprise application developer or operator, I want stable REST and CLI
contracts for Agents, Sessions, Memory, Tools, and system status so that I can
embed OryxOS into existing systems and operate it without duplicating Agent
execution logic.

**Why this priority**: The external service boundary turns the runtime into a
shared enterprise platform and completes the end-to-end core-stage delivery.

**Independent Test**: Start the service, exercise all ten core REST operations
and twelve CLI commands, manually replay a scheduled Agent through both the CLI
conversation path and Agent invocation contract, and verify successful,
invalid-input, and resource-not-found outcomes while observing the same Agent,
Session, LLM-call, and Tool-invocation data from CLI and Web entry points.

**Acceptance Scenarios**:

1. **Given** a running service, **When** a client creates a Session, sends
   messages, reads history, and archives it, **Then** all four lifecycle
   operations return consistent results.
2. **Given** a defined Agent, **When** a client invokes it without retaining a
   caller-managed conversation, **Then** the invocation returns one complete
   result through the shared Agent execution path.
3. **Given** a client requests Profile, Memory, Tool, health, or runtime
   information, **When** the corresponding read operation is called, **Then**
   the service returns the documented information without exposing secrets.
4. **Given** invalid input or an unknown resource, **When** a REST or CLI
   operation is attempted, **Then** it returns a consistent, actionable error
   without an internal stack trace.
5. **Given** multiple Agent directories in one workspace, **When** CLI, Web, or
   scheduled requests target them, **Then** the Agents coexist while using
   their own derived configuration and the shared runtime services.
6. **Given** an Agent definition also has a schedule, **When** an operator
   manually runs it through the CLI conversation path or Agent invocation
   contract, **Then** the same Agent execution path is used and its Session,
   LLM-call, and Tool-invocation records are queryable.
7. **Given** an operator follows the deployment and quickstart documentation,
   **When** starting from prerequisites, **Then** a single-node instance can be
   deployed and verified within 30 minutes.

### Edge Cases

- The workspace is absent, partially initialized, or contains a conflicting
  path type.
- An Agent definition is missing required fields, names an unknown Provider or
  Tool, or references a missing bootstrap file.
- A Provider is slow, unavailable, returns malformed output, or omits token
  usage.
- A model requests an unknown Tool, repeats the same Tool request, or returns a
  Tool call after the iteration limit.
- A model response contains both explanatory/final-looking text and one or more
  Tool requests; the Tool requests remain authoritative for that iteration and
  the text is retained only as intermediate Session content.
- Two Tool sources advertise the same registry name; workspace loading fails
  that registration clearly instead of selecting one by discovery order.
- Tool output is empty, very large, malformed, retryable, or non-retryable.
- A file path attempts traversal outside the workspace; an HTTP URL redirects
  to an unapproved host; a Shell request hides an unapproved executable.
- Long-term memory is empty, contains duplicate facts, or exceeds the core-stage
  context limit.
- Core-scope memory alone exceeds the available prompt budget; it is not
  silently truncated and the invocation returns a bounded, actionable error.
- The selected long-term-memory mode is unknown, unavailable, malformed, or
  contains a value resembling a secret.
- An operator switches long-term-memory mode while facts remain only in the
  previous mode; no implicit data transfer occurs.
- Concurrent long-term memory appends are serialized; reads observe all appends
  completed before the read began.
- Concurrent messages targeting one Session are serialized in service-acceptance
  order; requests for different Sessions may execute concurrently.
- A message targeting an archived Session is rejected with guidance to create a
  new Session; a request targeting a nonexistent Session returns a
  missing-resource outcome.
- A scheduled execution lasts beyond its next trigger time.
- Schedule times missed while OryxOS is stopped are not replayed automatically;
  operators use manual invocation when a missed run must be recovered.
- Provider, Tool, or notification configuration contains an unresolved
  environment placeholder.
- Logs, Tool results, or API error payloads contain values resembling secrets.
- Session and invocation audit records remain present regardless of age until
  an administrator explicitly deletes the workspace data.
- Ten Agents and one hundred Sessions are active on one node during sustained
  operation.

## Requirements *(mandatory)*

### Functional Requirements

#### Workspace, Agent, and Entry Foundations

- **FR-001**: Users MUST be able to initialize a local OryxOS workspace
  containing a default Agent, bootstrap context, long-term memory, logs,
  external Tool-server configuration, and the local runtime data artifact.
- **FR-002**: Each business Agent MUST be defined by one self-contained
  directory whose primary definition combines runtime settings with task and
  behavior instructions.
- **FR-003**: OryxOS MUST derive a runtime Profile from each Agent definition
  and MUST support at least ten Agents coexisting in one instance.
- **FR-004**: Agent validation MUST identify missing or unsupported Provider,
  Tool, Channel, bootstrap, scheduling, and loop settings without silently
  substituting different values.
- **FR-005**: CLI, Web Service, and scheduled triggers MUST converge on one
  Agent execution path and MUST NOT duplicate Agent-processing behavior.
- **FR-006**: The core release MUST provide the documented twelve CLI commands
  for workspace, status, conversation, service, Agent/Profile, Provider, Tool,
  and Session operations.

#### Provider Capability

- **FR-007**: All model calls MUST use a named Provider abstraction so Agent
  instructions remain independent of a specific model vendor.
- **FR-008**: The core release MUST demonstrate successful calls through both
  DeepSeek and Kimi configurations.
- **FR-009**: Provider selection MUST remain explicit when multiple Providers
  are present.
- **FR-010**: Every model-call attempt MUST record the Session, Provider, model,
  duration, available token counts, timestamp, and success or failure outcome.
- **FR-011**: A Provider failure in the core stage MUST return a clear error and
  MUST NOT silently fail over to another Provider.

#### ReAct Capability

- **FR-012**: OryxOS MUST control the complete reason–act loop, including prompt
  assembly, model calls, Tool dispatch, result feedback, and termination.
- **FR-013**: Prompt context MUST include the Agent instructions, bootstrap
  context, long-term memory, bounded Session history, current date and time,
  and only the Tools available to the current Agent.
- **FR-014**: Each model response and Tool result MUST be appended to the
  Session in execution order before the next model call.
- **FR-015**: A model response without Tool requests MUST terminate the loop and
  become the final Agent response.
- **FR-016**: A valid Tool request MUST execute at most once per requested call
  and its result MUST be returned to the loop. When the same model response also
  contains final-looking text, the Tool request MUST take precedence and that
  text MUST NOT terminate the loop.
- **FR-017**: The loop MUST enforce an Agent-configurable iteration limit with a
  default of ten and MUST return a bounded-loop outcome when reached.
- **FR-018**: The core stage MUST process Tool calls sequentially and MUST NOT
  delegate work from one Agent to another Agent.

#### Memory and Session Capability

- **FR-019**: OryxOS MUST associate active Session history with the combination
  of Channel, user, and Agent identity.
- **FR-020**: Session messages and status MUST persist locally and MUST be
  recoverable after restart.
- **FR-021**: Users and Agents MUST be able to append selected facts to shared
  long-term memory and retrieve them using keyword matching.
- **FR-022**: Long-term memory MUST be included in Agent context while applying
  a deterministic core-stage size limit.
- **FR-023**: The core stage MUST bound oversized conversation history by
  retaining recent turns. OryxOS-owned automatic summarization and an embedded
  vector or semantic-retrieval layer are excluded; a selected self-hosted
  memory service MAY provide stronger matching internally, but callers MUST
  rely only on the shared keyword-recall contract.
- **FR-024**: Session lifecycle MUST support creation, continued messaging,
  history retrieval, and archival.

#### Tool and Scheduling Capability

- **FR-025**: The Tool catalog MUST include `read_file`, `write_file`,
  `list_dir`, `shell`, `http_get`, `http_post`, `save_memory`,
  `recall_memory`, and `notify`.
- **FR-026**: Built-in, external Tool-server, and Java Plugin Tools MUST appear
  through one discoverable Tool contract and registry. Duplicate registry names
  across any Tool origin, including memory-owned Tools, MUST fail registration
  clearly rather than being overwritten or selected by discovery order.
- **FR-027**: OryxOS MUST support all three independently accepted Plugin
  routes: (1) configuring and invoking an independently supplied, pre-existing
  external Tool server without modifying it; (2) connecting an
  operator-authored external Tool server through a separate configuration and
  fixture using the same external-server contract; and (3) registering an
  in-process Java Plugin Tool. The two external routes MAY share one runtime
  adapter, but MUST have distinct acceptance fixtures and assertions.
- **FR-028**: Tool arguments MUST be validated before execution.
- **FR-029**: File, Shell, and HTTP operations MUST pass their applicable
  workspace, command, and parsed-host allowlist checks before side effects.
- **FR-030**: Successful and failed Tool attempts MUST record input, result or
  error, retryability, duration, Session, Tool name, and timestamp.
- **FR-031**: Retryable Tool failures MUST use a bounded retry policy of no more
  than three retries; non-retryable failures MUST return immediately.
- **FR-032**: Each scheduled task MUST use task-level, single-process
  non-overlap control, and a schedule failure MUST NOT stop later triggers.
- **FR-033**: Scheduled and manual triggers MUST converge on the same Agent
  execution service. Scheduler-foundation acceptance MAY prove this convergence
  with an internal manual-trigger adapter before public adapters exist, but this
  requirement is complete only when scheduled work is manually reproducible
  through the CLI or Agent invocation contract with queryable Session, LLM-call,
  and Tool-invocation records.

#### Web Service and Operational Capability

- **FR-034**: The core Web Service MUST provide the documented ten operations:
  four Session lifecycle operations, one Agent invocation, Profile listing,
  long-term Memory retrieval, Tool listing, health, and runtime information.
  Session retrieval MUST expose bounded ordered messages plus independently
  paginated LLM Call and Tool Invocation records with total counts, so every
  persisted attempt can be retrieved without adding a separate audit operation.
- **FR-035**: REST success and failure responses MUST use a consistent envelope
  and consistent invalid-input, missing-resource, Provider-failure, and
  internal-failure semantics.
- **FR-036**: The Agent invocation contract MUST reuse the same execution path,
  Tool controls, Session/audit recording, and configuration used by other
  triggers.
- **FR-037**: Operators MUST be able to run interactive conversation, service,
  and gateway modes against the same Agent definitions and Session store.
- **FR-038**: Agent instructions and bootstrap content MUST take effect for the
  next prompt read; newly added Agents and frontmatter or schedule changes MUST
  require an explicit core-stage reload or restart.
- **FR-039**: Provider and Tool credentials MUST be supplied through environment
  references or separate secure configuration and MUST NOT appear in Agent
  definitions, external Tool-server configuration, source, tests, logs, or
  client responses.
- **FR-040**: OryxOS MUST keep enterprise data within the operator-controlled
  deployment except for explicitly configured Provider, Tool, and notification
  calls initiated by an Agent.
- **FR-041**: CLI commands MUST provide help and actionable errors without
  exposing internal stack traces to normal users.
- **FR-042**: The core release MUST provide a public project page and deployment
  guidance that explain the product boundary and enable a new operator to
  verify a single-node installation.
- **FR-043**: Messages accepted concurrently for the same active Session MUST
  execute serially in service-acceptance order; this ordering MUST NOT prevent
  different Sessions from executing concurrently.
- **FR-044**: An archived Session MUST remain readable but MUST reject new
  messages with guidance to create a new Session; archival MUST NOT implicitly
  reactivate or replace the Session.
- **FR-045**: Schedules MUST NOT automatically replay trigger times missed while
  OryxOS was stopped; after restart they MUST resume at the next scheduled time,
  while manual replay remains available.
- **FR-046**: Core-stage Session, Tool Invocation, and LLM Call records MUST NOT
  expire automatically; they MUST remain available until an administrator
  explicitly deletes the workspace data.
- **FR-047**: Accepted long-term memory appends MUST execute serially without
  lost content; a memory read MUST include every append that completed before
  the read began.

#### Architecture Invariants

- **FR-048**: Reason–act and prompt-assembly capabilities MUST obtain a single
  composed memory view through the unified Memory Service and MUST NOT access
  Session persistence or a long-term-memory backend separately.
- **FR-049**: The Memory Service MUST represent Session, long-term, and episodic
  memory as three distinct layers; Session and long-term memory are core-stage
  capabilities, while episodic memory MUST remain extension-stage scope.
- **FR-050**: The core release MUST offer three selectable long-term-memory
  modes: a human-readable document mode as the default, a local structured
  mode, and an operator-controlled self-hosted managed-memory mode.
- **FR-051**: All long-term-memory modes MUST share the same append, complete
  core-scope load, deterministically bounded archival load, and keyword-recall
  behavior. Switching mode MUST NOT change the Agent, prompt, or Tool contract.
  After trimming, an empty append or content larger than 32 KiB MUST be rejected;
  re-appending identical content in the same scope MUST be idempotent and expose
  that fact once through the shared load/recall contract.
- **FR-052**: Changing the selected long-term-memory mode MUST NOT automatically
  copy or delete facts from another mode; the operator MUST be informed which
  mode is authoritative and remains responsible for any data migration.
- **FR-053**: Failure of the selected long-term-memory mode MUST be reported
  clearly and MUST NOT silently remove long-term context from an Agent
  invocation; independently persisted Session history MUST remain queryable.
- **FR-054**: Core-scope long-term memory MUST remain complete. If it cannot fit
  with mandatory prompt context, the invocation MUST fail clearly rather than
  silently truncating core-scope facts.
- **FR-055**: Any model-integration capability that can automatically dispatch
  Tools MUST be disabled; Tool scheduling and execution MUST occur only through
  the OryxOS reason–act loop and Tool executor.
- **FR-056**: General built-in, external Tool-server, registry, Sandbox, and
  notification Tool capabilities MUST remain one Tool subsystem; memory-owned
  Tools MUST join the same registry rather than create another general Tool
  subsystem.
- **FR-057**: Agent definitions and their loaders MUST be treated as prompt
  context sources, not Tools. Agent body content MUST enter the system prompt,
  while Agent-local skills, references, and scripts MUST be accessed on demand
  through already-declared Tools.

#### Demo Stage Acceptance

- **FR-058**: Before the public Web/CLI replay contracts are required, the
  technology-digest stage scenario MUST run through the shared internal Agent
  execution path, follow `AGENT.md`, read `skills/digest-format.md` on demand,
  invoke a configured news MCP Tool, combine its result with a distinctive
  Memory-only fact, expose that fact in the generated digest, and retain the
  resulting Tool audit sequence.
- **FR-059**: Before the public Web/CLI replay contracts are required, the
  GitHub-digest stage scenario MUST run through the shared internal Agent
  execution path, invoke `shell` for a script under that Agent's `scripts/`
  directory, use the script output as its deterministic GitHub data source,
  and retain the resulting Tool audit sequence.

### Key Entities

- **Workspace**: The local root holding Agent definitions, bootstrap context,
  Memory, Tool-server configuration, logs, and runtime data.
- **Agent Definition**: A self-contained directory whose primary document
  contains the Agent's runtime configuration, task, behavior, and boundaries.
- **Profile**: The validated runtime view derived from an Agent definition,
  including Provider, Tools, Channels, schedules, bootstrap inputs, and loop
  limits.
- **Provider**: A named model-service configuration used for model calls without
  changing Agent business instructions.
- **Session**: The durable conversation container identified by Channel, user,
  and Agent, containing ordered messages and lifecycle status.
- **Long-Term Memory**: User or Agent-selected facts retained across Sessions
  and retrievable by keyword in the core stage.
- **Memory Service**: The single memory view consumed by Agent reasoning,
  combining bounded Session history with long-term core and archival facts
  while reserving an empty episodic layer for later expansion.
- **Long-Term Memory Mode**: One of the three operator-selected persistence
  modes that implements the same scope, append, load, and keyword-recall
  contract.
- **Tool**: A discoverable action with a name, description, input contract,
  execution result, retry classification, and safety policy.
- **Tool Invocation**: The audit record of one successful or failed Tool
  attempt, linked to a Session.
- **LLM Call**: The audit record of one Provider interaction, linked to a
  Session.
- **Schedule**: An Agent-owned recurring trigger with an identifier, time rule,
  timezone, message, and single-process execution state.

## Scope & Governance Constraints *(mandatory)*

- **Delivery phase**: Core-stage runtime kernel, covering the five capabilities
  defined in `docs/DemandAnalysis.md` sections 1.2 and 5.
- **In scope**: Provider access, controlled ReAct execution, Session and
  unified three-layer Memory Service, three selectable long-term-memory modes,
  built-in and Plugin Tools, CLI Channel, scheduling, ten REST operations,
  twelve CLI commands, local persistence and audit writes, three release Demos,
  deployment guidance, and the public project page.
- **Out of scope**: Provider failover or adaptive routing, automatic or
  OryxOS-owned semantic Memory extraction, episodic Memory, an embedded vector
  database, guarantees beyond the shared keyword-recall contract, automatic
  migration between memory modes, full Tool Policy, container or microVM
  isolation, IM Channels, streaming responses, WebSockets, runtime Agent
  upload/edit APIs, full audit-query APIs, authentication, SSO, RBAC,
  multi-tenancy, dashboards, metrics export, cluster high availability, and
  cross-node Agent collaboration.
- **Public contract impact**: Establishes the ten core REST operations, twelve
  CLI commands, one-directory-per-Agent contract, three trigger modes, Session
  lifecycle, and core Tool catalog.
- **Security, privacy, and audit constraints**: Secrets remain outside
  definitions and outputs; Tool execution requires validation and application
  allowlists; enterprise data remains operator-controlled; every model and Tool
  attempt is auditable from the core stage; application allowlists are not
  represented as strong process isolation; the managed-memory mode must be
  self-hosted and its credentials, authorization values, errors, and stored
  content must follow the same secret-redaction rules.
- **Architecture invariants**: Agent reasoning uses one unified Memory Service;
  core delivery includes the default document, local structured, and self-hosted
  managed-memory modes; general Tool capabilities remain one subsystem;
  Agent definitions and loaders are context rather than Tools; model integration
  must not automatically dispatch Tools.
- **Authoritative sources**: `docs/DemandAnalysis.md` is the source
  requirement; `docs/TechnicalSolution.md`, `docs/AiProgrammingGuide.md`,
  `docs/CliGuide.md`, `AGENTS.md`, and the project constitution govern
  architecture, sequencing, public contracts, and quality gates.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: The same minimal Agent completes a no-Tool conversation through
  both DeepSeek and Kimi configurations, with 100% of call attempts recorded.
- **SC-002**: Automated acceptance covers ReAct with no Tool, one Tool,
  multiple Tool rounds, Tool failure, and maximum-iteration termination, with
  every message and result preserved in order.
- **SC-003**: A saved user preference is recalled and affects a later response
  after both a new conversation and a process restart.
- **SC-004**: All nine built-in Tools and one Tool from each of the three
  distinct Plugin-route fixtures pass every applicable assertion in the User
  Story 4 Tool acceptance matrix; both external-server fixtures and the Java
  Plugin fixture are reported separately, and every attempted invocation has an
  audit record.
- **SC-005**: All ten core REST operations pass success, invalid-input, and
  missing-resource contract checks where applicable; Session retrieval also
  passes independent LLM/Tool audit pagination, ordering, and total-count checks.
- **SC-006**: All twelve CLI commands expose help and return actionable errors
  without normal-path stack traces.
- **SC-007**: One node runs at least ten Agents for four continuous hours while
  maintaining at least one hundred concurrent Sessions without loss of
  registered Agent configuration or persisted Session data.
- **SC-008**: At least 99% of Session creations complete within 200 milliseconds,
  and internal request forwarding adds less than 50 milliseconds, excluding
  external Provider and Tool time.
- **SC-009**: The daily weather, technology digest, and GitHub digest Demos each
  run on schedule and by manual replay, with Session history and every model and
  Tool attempt available for verification through paginated Session retrieval.
- **SC-010**: A new operator can follow the documentation to deploy and verify a
  single-node instance within 30 minutes.
- **SC-011**: Secret scanning and acceptance tests find zero real credentials in
  Agent definitions, external Tool configuration, source, tests, logs, and
  client responses.
- **SC-012**: The public project page is accessible and accurately distinguishes
  implemented core-runtime capabilities from extension-stage plans.
- **SC-013**: Concurrent long-term memory acceptance tests retain 100% of
  accepted appended entries without malformed output, and each read contains
  all entries completed before that read began.
- **SC-014**: The same save, restart, load, and keyword-recall acceptance flow
  succeeds in all three supported long-term-memory modes, with 100% of
  core-scope facts preserved and identical externally observable contracts.
- **SC-015**: Architecture acceptance finds that one `MemoryService` call
  supplies bounded Session history, complete core-scope long-term memory,
  deterministically bounded archival memory, and an empty episodic layer to
  Agent reasoning and prompt assembly; it also finds zero direct
  memory-backend dependencies in those callers, zero Agent definitions
  represented as Tools, and no model Tool request dispatched by more than one
  execution controller.
- **SC-016**: The technology-digest stage acceptance records, in order, an
  on-demand read of `skills/digest-format.md` and a news MCP invocation, then
  produces a digest containing a distinctive fact available only from seeded
  Memory through one internal Agent execution.
- **SC-017**: The GitHub-digest stage acceptance records one approved Shell
  execution of the Agent-local script and demonstrates that the generated
  digest contains a unique marker available only in that script's output.

## Assumptions

- The core stage runs as a single trusted instance on an operator-controlled
  host or internal network; full authentication and tenant isolation are
  extension-stage work.
- External model Providers, Tool servers, weather/news sources, GitHub, and
  notification targets used in acceptance environments are available and
  separately authorized by the operator.
- Provider response quality and latency are outside OryxOS control; success
  measures cover connection, routing, execution, recording, and failure
  behavior.
- Long-term memory is shared within the workspace in the core stage; per-user
  or per-tenant isolation is deferred.
- Automated retention and record-level deletion management are deferred; core
  Session and invocation records remain until workspace data is explicitly
  deleted by an administrator.
- The shared long-term-memory contract uses keyword recall and deterministic
  archival truncation. A self-hosted managed-memory service may enhance matching
  internally, but OryxOS callers do not depend on semantic behavior.
- The default document mode requires no external service. The local structured
  mode uses operator-controlled local data, and the managed-memory mode requires
  a separately deployed, operator-authorized, self-hosted service.
- Changing memory mode selects a new authoritative store; automatic cross-mode
  data migration and deletion are intentionally excluded.
- Tool calls execute sequentially, and scheduled non-overlap control applies
  only within one running process.
- The three release Demos use operator-approved domains, commands, scripts,
  external Tool servers, and notification destinations.
