<!--
Sync Impact Report
- Version change: 1.1.0 -> 1.2.0
- Modified principles:
  - III. Core Runtime First -> clarified that self-hosted Mem0 is a required
    core adapter while custom/in-process vector infrastructure remains deferred
  - V. Unified Tools with Explicit Sandbox Control -> clarified single Tool
    Maven-module boundary and the MemoryTools ownership exception
  - VI. Local Persistence, Auditability, and Secret Safety ->
    VI. Unified Memory, Persistence, Auditability, and Secret Safety
- Added sections: none
- Removed sections: none
- Templates updated:
  - ✅ .specify/templates/plan-template.md
  - ✅ .specify/templates/spec-template.md
  - ✅ .specify/templates/tasks-template.md
- Command/skill review:
  - ✅ All installed .agents/skills/speckit-*/SKILL.md files reviewed; no
    command-specific wording update required
- Runtime guidance:
  - ✅ docs/TechnicalSolution.md
  - ✅ docs/AiProgrammingGuide.md
  - ✅ README.md and AGENTS.md reviewed; no wording change required
- Active feature artifacts:
  - ✅ specs/003-core-runtime/plan.md already follows the amended architecture
  - ⚠ specs/003-core-runtime/spec.md deferred to the requested follow-up
    specification update by the constitution workflow scope guard
- Deferred TODOs:
  - Update specs/003-core-runtime/spec.md and re-evaluate
    specs/003-core-runtime/checklists/architecture.md before task generation
-->
# OryxOS Constitution

## Core Principles

### I. Documentation and Contracts First

Every feature MUST be traceable to the current requirements and technical
solution. Conflicts MUST be resolved in this order:
`docs/TechnicalSolution.md`, `docs/DemandAnalysis.md`,
`docs/AiProgrammingGuide.md`, `docs/CliGuide.md`, then the remaining product
and research documents listed in `AGENTS.md`. Public REST and CLI behavior MUST
be explicit, testable, and documented. Material changes to code, requirements,
or architecture MUST update every affected document; planned behavior MUST NOT
be described as implemented.

### II. Single Deployable Nine-Module Architecture

OryxOS MUST remain a JDK 21, Spring Boot 3.x, Maven multi-module monolith that
produces one executable Boot JAR. The reactor MUST retain the nine documented
modules and their dependency direction. Features MUST live in the smallest
responsible module without circular dependencies, cross-layer shortcuts, or
unrelated restructuring. Spring MVC with Java 21 virtual threads and a
synchronous blocking core flow is the core-stage execution model.

### III. Core Runtime First

Core-stage work MUST prioritize the five shared runtime capabilities:
Provider, ReAct, Memory, Tool, and Web Service. CLI, Web Service, and
`AgentScheduler` triggers MUST converge on the same `AgentService` execution
path. Work MUST proceed in dependency order—Provider, ReAct, Memory and Tool,
Web Service, then integration acceptance—with a runnable vertical result at
each stage. Multi-tenancy, SSO, RBAC, full audit-query UI, container or microVM
sandboxing, cluster high availability, distributed coordination, vector
databases implemented inside OryxOS, workflow orchestration, and cross-node
Agent collaboration MUST remain extension-stage scope unless the governing
documents are explicitly amended. The self-hosted Mem0 adapter required by the
current Memory contract is core-stage integration; it MUST NOT be represented
as an in-process OryxOS vector-database implementation.

### IV. OryxOS Owns the ReAct and Tool Loop

OryxOS MUST implement and control `ReActLoop`, prompt assembly, iteration
limits, Tool dispatch, result feedback, and termination. Spring AI and Spring
AI Alibaba MAY provide Provider protocol adaptation, Function Calling format
conversion, and Tool schema generation, but their automatic Tool execution
MUST be disabled. Provider selection MUST use an explicit provider-name to
`ChatModel` mapping and MUST NOT infer identity solely by scanning Bean types.
Each LLM response and Tool result MUST be appended to the Session in execution
order.

### V. Unified Tools with Explicit Sandbox Control

Built-in Tools, MCP Tools, and Java Plugin Tools MUST adapt to `OryxTool` and
register through `ToolRegistry`. General Tool infrastructure—built-ins, MCP
client and adapter, registry, Sandbox, and notification adapters—MUST remain in
the single `oryxos-tool` Maven module and MUST NOT be split into separate Tool
modules. `MemoryTools` remain owned by `oryxos-memory` but MUST register through
the same `ToolRegistry`; this ownership exception does not create another
general Tool module. An Agent directory, `AGENT.md`, `AgentLoader`, or
`ContextLoader` MUST NOT be modeled as a Tool. Tool arguments MUST be validated
and Sandbox policy checked before execution. Core-stage isolation MUST use a
`Sandbox` boundary with `WhitelistSandbox`; it MUST NOT use Java
`SecurityManager` or claim application-level allowlists are strong process
isolation. File paths MUST be normalized before workspace-boundary checks,
HTTP allowlists MUST validate the parsed host, and Shell allowlists MUST
validate the actual command.

### VI. Unified Memory, Persistence, Auditability, and Secret Safety

Session data, Tool invocations, and LLM calls MUST persist in
`.oryxos/oryxos.db`; successful and failed `tool_invocations` and `llm_calls`
MUST be written from the core stage. `ReActLoop` and `PromptBuilder` MUST access
Session and long-term memory only through the unified `MemoryService` façade;
they MUST NOT query Session persistence or any long-term-memory backend
directly. The façade MUST represent three layers: Session and long-term memory
are core-stage capabilities, while episodic memory remains extension-stage
scope.

Long-term memory MUST be isolated behind `LongTermMemoryStore`. The core release
MUST provide `MarkdownMemoryStore` as the default, `SqliteMemoryStore`, and a
self-hosted `Mem0MemoryStore` adapter, selectable through `memory.backend`
without changing `MemoryService`, `PromptBuilder`, `MemoryTools`, or
`ReActLoop`. All backends MUST preserve the same core/archival scope,
deterministic truncation, append visibility, and keyword-recall contract.
Mem0 MAY provide stronger semantic matching internally, but callers MUST NOT
depend on semantics beyond that shared contract. OryxOS MUST NOT add an
in-process vector database in the core stage.

Secrets MUST enter through environment variables or separate secure
configuration and MUST NOT appear in `AGENT.md`, MCP or Mem0 configuration,
source, tests, logs, generated examples, audit records, or API responses.

### VII. Tested and Demonstrable Vertical Slices

Every user story MUST deliver a small, independently verifiable outcome.
Automated tests are mandatory for changed behavior and MUST cover the normal
path plus relevant input-validation, failure, boundary, persistence, and
security paths. ReAct, Tool, Session, API, path traversal, command allowlist,
and HTTP host allowlist behavior MUST receive the specialized coverage defined
in `AGENTS.md`. A story is not complete until affected-module tests pass, the
relevant quickstart or Demo is reproducible, and `mvn clean package` succeeds
across all nine modules.

## Technical Constraints

- Agent definitions MUST use one directory per Agent with `AGENT.md`
  frontmatter and body; independently maintained Profile YAML files are
  prohibited.
- The core runtime MUST expose the documented 10 REST endpoints with
  `ApiResponse` and global exception handling, and the documented 12 CLI
  subcommands.
- The core stage MUST implement the 9 documented built-in Tools in the single
  Tool system, with general Tool infrastructure contained in `oryxos-tool` and
  `MemoryTools` owned by `oryxos-memory` but registered through the shared
  `ToolRegistry`.
- `MemoryService` MUST be the single ReAct-facing memory contract and MUST
  compose Session memory with the selected `LongTermMemoryStore`.
- The core stage MUST support `memory.backend=markdown`, `sqlite`, and `mem0`;
  Markdown MUST be the default and Mem0 MUST target a self-hosted endpoint.
- `AgentLoader` and `ContextLoader` MUST remain core context-loading services;
  `AGENT.md` body content enters the system prompt, while Agent-local resources
  are read on demand through existing Tools.
- `ReActLoop` MUST enforce `max_iterations`, defaulting to 10.
- Prompt assembly MUST include the Agent body, Bootstrap context, Memory,
  bounded history, available Tools, and the current date and time.
- A scheduled task MUST use task-level in-process non-overlap control and MUST
  NOT be represented as having a distributed lock.
- New abstractions require at least two real implementations or a documented
  extension boundary.
- `website/` is the independent static project site; `oryxos-web` is the
  backend REST module. Their responsibilities MUST remain separate.

## Development Workflow and Quality Gates

1. Read the relevant requirements, technical solution, and existing
   implementation before changing artifacts or code.
2. Classify the change as core-stage or extension-stage work and reject silent
   scope expansion.
3. Create or update the specification, implementation plan,
   dependency-ordered tasks, contracts, and quickstart as applicable.
4. Pass the plan Constitution Check before research and again after design.
5. Implement the smallest necessary change while preserving unrelated user
   work and module dependency direction.
6. Create and run required unit, contract, integration, persistence, and
   security tests appropriate to the changed behavior.
7. Run affected-module tests and `mvn clean package`; if a gate cannot run, the
   work MUST be reported as unverified rather than complete.
8. Run Spec Kit consistency analysis at story checkpoints and resolve all
   constitution violations before implementation continues.
9. Report changed artifacts, verification evidence, remaining limitations, and
   any credential, logging, path, concurrency, or audit risk.

## Governance

This constitution governs all Spec Kit artifacts and implementation work in
this repository. `AGENTS.md` provides operational repository rules, while
`docs/TechnicalSolution.md` and `docs/DemandAnalysis.md` provide detailed
architecture and requirements. The constitution prevails when a generated
artifact or workflow template conflicts with it.

Amendments require an explicit project-owner or maintainer instruction, a
documented rationale, a Sync Impact Report, and propagation to affected
templates and active artifacts. An AI assistant MUST NOT amend the constitution
on its own initiative. Compliance MUST be checked during planning, task
generation, implementation review, and pull-request review.

Versions follow semantic versioning:

- **MAJOR**: incompatible governance changes, principle removal, or
  redefinition.
- **MINOR**: a new principle or materially expanded mandatory guidance.
- **PATCH**: non-semantic clarification, correction, or wording improvement.

The ratification date remains the original adoption date. The last-amended date
MUST change whenever constitution content changes.

**Version**: 1.2.0 | **Ratified**: 2026-07-25 | **Last Amended**: 2026-07-29
