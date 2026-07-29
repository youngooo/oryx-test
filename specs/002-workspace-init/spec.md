# Feature Specification: OryxOS Workspace Initialization

**Feature Branch**: `main`

**Created**: 2026-07-29

**Status**: Draft

**Input**: User description: "init"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Initialize a Ready-to-Edit Workspace (Priority: P1)

As an OryxOS user starting in a project directory, I want one command to create a complete local workspace so that I can immediately configure the default Agent and proceed to the next OryxOS command.

**Why this priority**: Without a valid workspace, no Agent definition, bootstrap context, memory, configuration, or local runtime data can be managed.

**Independent Test**: Run `oryxos init` in an empty temporary directory and verify that every required workspace directory and starter file exists, including a minimally valid default Agent definition.

**Acceptance Scenarios**:

1. **Given** a writable directory without an OryxOS workspace, **When** the user runs `oryxos init`, **Then** a `.oryxos/` workspace with the required directories and starter files is created in that directory.
2. **Given** initialization completes successfully, **When** the user inspects `.oryxos/agents/default/AGENT.md`, **Then** it contains a minimally valid, editable default Agent definition with no embedded secret.
3. **Given** initialization completes successfully, **When** the user proceeds to workspace inspection or Agent configuration, **Then** no external provider connection or credential is required merely to use the generated workspace files.

---

### User Story 2 - Re-run Initialization Safely (Priority: P2)

As a user returning to an existing or partially initialized workspace, I want initialization to preserve my files so that re-running the command cannot erase Agent definitions, memory, or personalized bootstrap content.

**Why this priority**: Workspace files are user-owned configuration and memory. Safe repeatability prevents a routine recovery or setup action from becoming destructive.

**Independent Test**: Initialize a directory, customize every generated text file, remove one generated empty directory, run `oryxos init` again, and verify that all customized bytes remain unchanged while the missing directory is restored.

**Acceptance Scenarios**:

1. **Given** a complete initialized workspace containing user edits, **When** the user runs `oryxos init` again, **Then** initialization succeeds without overwriting or deleting any existing file.
2. **Given** a partial workspace with some required paths absent, **When** the user runs `oryxos init`, **Then** only missing paths are created and all existing content is preserved.
3. **Given** an existing non-directory item blocks a required directory path, **When** the user runs `oryxos init`, **Then** the command reports the conflicting path, does not replace it, and does not claim successful initialization.

---

### User Story 3 - Understand the Initialization Result (Priority: P3)

As a command-line user, I want a concise and accurate result message so that I know which workspace was targeted and what to do next.

**Why this priority**: Clear feedback prevents users from editing or running commands in the wrong project directory.

**Independent Test**: Run initialization in both a new and an existing workspace and verify that each outcome identifies the target workspace and distinguishes newly created content from preserved content.

**Acceptance Scenarios**:

1. **Given** a successful first initialization, **When** the command finishes, **Then** the output identifies the initialized workspace and gives an actionable next step.
2. **Given** a successful repeat initialization, **When** the command finishes, **Then** the output states that existing content was preserved.
3. **Given** initialization cannot complete, **When** the command finishes, **Then** it returns a failure outcome with the obstructing path or permission problem and does not print a success message.

### Edge Cases

- The current directory is not writable or a parent path needed for `.oryxos/` cannot be created.
- `.oryxos` already exists as a regular file rather than a directory.
- A required nested directory path is occupied by a regular file.
- A required starter file already exists, including an empty file or a file with invalid user-authored content.
- Initialization is interrupted after only part of the workspace has been created and is then re-run.
- Directory or file names contain spaces or non-ASCII characters.
- Provider credentials are absent from the environment.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The `oryxos init` command MUST target a `.oryxos/` workspace directly beneath the user's current directory.
- **FR-002**: Initialization MUST ensure the workspace contains `agents/default/`, `memory/`, and `logs/` directories.
- **FR-003**: Initialization MUST ensure the workspace contains `agents/default/AGENT.md`, `memory/MEMORY.md`, `mcp_servers.yaml`, `AGENTS.md`, `SOUL.md`, `USER.md`, and `oryxos.db`.
- **FR-004**: The generated `agents/default/AGENT.md` MUST be a minimally valid, editable Agent definition following the one-directory-per-Agent model.
- **FR-005**: Generated starter content MUST guide users toward environment-based secret configuration and MUST NOT contain a real credential, token, password, or other secret.
- **FR-006**: The generated bootstrap, memory, and MCP configuration files MUST be usable as safe starter files without requiring the user to infer missing file formats.
- **FR-007**: Initialization MUST NOT require a model provider, network access, provider credential, or running OryxOS service.
- **FR-008**: Initialization MUST NOT overwrite, truncate, delete, rename, or otherwise modify any pre-existing file.
- **FR-009**: When part of the required workspace structure already exists, initialization MUST preserve it and create only missing required paths.
- **FR-010**: Initialization MUST treat an existing empty file as user-owned content and MUST NOT replace it with starter content.
- **FR-011**: Before reporting success, initialization MUST verify that every required path exists with the expected file-or-directory kind.
- **FR-012**: If an existing item conflicts with the required path kind, initialization MUST identify the conflicting path, preserve it, and return a failure outcome.
- **FR-013**: If creation fails because of permissions or another filesystem error, initialization MUST return a failure outcome that identifies the affected path or operation.
- **FR-014**: A successful result MUST identify the workspace location and distinguish a newly created workspace from a safely preserved existing workspace.
- **FR-015**: A successful result MUST suggest a valid next action, such as editing the default Agent or inspecting workspace status.
- **FR-016**: Re-running initialization after an interrupted or partial attempt MUST be able to complete any still-missing, non-conflicting workspace items.

### Key Entities

- **Workspace**: The `.oryxos/` directory beneath the current directory; it owns Agent definitions, bootstrap context, memory, logs, MCP configuration, and local runtime data.
- **Default Agent**: The initial Agent directory named `default`, containing an editable `AGENT.md` from which a runtime Profile can later be derived.
- **Bootstrap Document**: One of `AGENTS.md`, `SOUL.md`, or `USER.md`; user-owned context that influences later Agent execution.
- **Workspace Data File**: `oryxos.db`, the local data artifact reserved for sessions and invocation audit records.

## Scope & Governance Constraints *(mandatory)*

- **Delivery phase**: Core stage; workspace initialization is part of the
  documented 12-command CLI contract and is required before Agents can run.
- **In scope**: A lightweight, offline, current-directory `oryxos init` command
  that creates or safely repairs the minimum documented workspace.
- **Out of scope**: Agent runtime registration, database schema migration,
  remote initialization, workspace backup/rollback, vector storage, RBAC, and
  container or microVM isolation.
- **Public contract impact**: Defines the `oryxos init` CLI behavior, output,
  exit status, generated paths, and no-overwrite guarantee.
- **Security, privacy, and audit constraints**: Generated content contains no
  real secret; existing files remain unchanged; no data leaves the local
  machine; `oryxos.db` is created only as the data artifact reserved for later
  storage initialization.
- **Authoritative sources**: `AGENTS.md` sections 3–4, 7–8, and 11–12;
  `docs/TechnicalSolution.md` sections 1.1, 8.1, and 8.7;
  `docs/DemandAnalysis.md` sections 5.1 and 13; `docs/CliGuide.md` section 4.1;
  and `docs/AiProgrammingGuide.md` section 4.2.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: In 100% of clean-directory acceptance runs, one `oryxos init` invocation creates all 3 required directories and all 7 required files.
- **SC-002**: A user can complete initialization and locate the default Agent definition in under 30 seconds without consulting external documentation.
- **SC-003**: Across repeat-initialization tests, 100% of pre-existing files retain byte-for-byte identical content.
- **SC-004**: Across partial-workspace tests, all missing non-conflicting required paths are restored in one repeat invocation.
- **SC-005**: Across conflict and permission-failure tests, 100% of failures identify an affected path, preserve the conflicting content, and avoid a false success message.
- **SC-006**: Initialization completes without network access, provider credentials, or a running background service.

## Assumptions

- The command has no positional workspace argument in the core-stage CLI contract; the current directory is always the target project directory.
- Existing workspace content is user-owned even when empty or malformed. Validation and repair of user-authored content are outside this feature's scope.
- The generated default Agent is a configuration starting point, not proof that a model provider is configured or that an Agent can already execute.
- Optional `skills/`, `scripts/`, and `REFERENCE.md` resources are not created by default because the minimal Agent requires only `AGENT.md`.
- Advanced workspace migration, backup, rollback, remote initialization, and secret-store integration are outside the core-stage scope.
