---

description: "Dependency-ordered implementation tasks for OryxOS workspace initialization"
---

# Tasks: OryxOS Workspace Initialization

**Input**: Design documents from `/specs/002-workspace-init/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`,
`contracts/init-command.md`, `quickstart.md`

**Tests**: Automated tests are required by the feature acceptance scenarios,
repository constitution, and `AGENTS.md`. Within each story, write the listed
tests first and confirm they fail for the intended missing behavior.

**Organization**: Tasks are grouped by user story so the clean-init MVP,
idempotent repair, and terminal feedback can each be demonstrated at a
checkpoint.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel because it changes a different file and has no
  dependency on another incomplete task in the same phase
- **[Story]**: Maps the task to US1, US2, or US3 in `spec.md`
- Every task names the exact file or directory it changes or validates

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Enable isolated JUnit 5 tests in the responsible CLI module.

- [ ] T001 Add the JUnit Jupiter test dependency managed by the parent BOM to `oryxos-cli/pom.xml`

---

## Phase 2: Foundational (Blocking Prerequisite)

**Purpose**: Define the result shared by filesystem behavior and command output.

**⚠️ CRITICAL**: Complete this phase before implementing any user story.

- [ ] T002 Create the package-local immutable workspace path plus created/preserved path result in `oryxos-cli/src/main/java/org/oryxos/cli/WorkspaceInitializationResult.java`

**Checkpoint**: The CLI module can compile the shared result type and run JUnit
5 tests.

---

## Phase 3: User Story 1 - Initialize a Ready-to-Edit Workspace (Priority: P1) 🎯 MVP

**Goal**: `oryxos init` creates the complete documented `.oryxos/` tree and a
safe, minimally valid default Agent without Spring, network, or credentials.

**Independent Test**: Execute the command against an empty JUnit temporary
directory and verify all required artifacts, starter content, exit code, and
root-command registration.

### Tests for User Story 1

- [ ] T003 [P] [US1] Add clean-directory creation and required artifact assertions to `oryxos-cli/src/test/java/org/oryxos/cli/WorkspaceInitializerTest.java`
- [ ] T004 [P] [US1] Add Picocli root registration and successful `init` execution tests to `oryxos-cli/src/test/java/org/oryxos/cli/InitCommandTest.java`
- [ ] T005 [US1] Add default-Agent structure, UTF-8 starter content, and no-real-secret assertions to `oryxos-cli/src/test/java/org/oryxos/cli/WorkspaceInitializerTest.java`

### Implementation for User Story 1

- [ ] T006 [P] [US1] Add the six reviewable starter templates under `oryxos-cli/src/main/resources/org/oryxos/cli/workspace/agents/default/AGENT.md`, `oryxos-cli/src/main/resources/org/oryxos/cli/workspace/memory/MEMORY.md`, `oryxos-cli/src/main/resources/org/oryxos/cli/workspace/mcp_servers.yaml`, `oryxos-cli/src/main/resources/org/oryxos/cli/workspace/AGENTS.md`, `oryxos-cli/src/main/resources/org/oryxos/cli/workspace/SOUL.md`, and `oryxos-cli/src/main/resources/org/oryxos/cli/workspace/USER.md`
- [ ] T007 [US1] Implement the required directory manifest, classpath template copying, empty `oryxos.db` creation, and post-create verification in `oryxos-cli/src/main/java/org/oryxos/cli/WorkspaceInitializer.java`
- [ ] T008 [US1] Implement the no-argument Picocli command in `oryxos-cli/src/main/java/org/oryxos/cli/InitCommand.java` and register it in `oryxos-cli/src/main/java/org/oryxos/cli/OryxOsCommand.java`

**Checkpoint**: A clean temporary project can be initialized and its generated
default Agent is ready to edit. This is the suggested MVP.

---

## Phase 4: User Story 2 - Re-run Initialization Safely (Priority: P2)

**Goal**: Repeat and partial initialization preserve every existing file byte,
repair only missing paths, and reject path-kind conflicts without replacement.

**Independent Test**: Customize generated files, remove selected required
paths, add a deterministic kind conflict in a separate case, and verify
preservation, repair, preflight failure, and recovery.

### Tests for User Story 2

- [ ] T009 [US2] Add byte-for-byte repeat preservation, empty/malformed existing file, partial repair, interrupted-run recovery, non-ASCII project path, and no-write-on-preflight-conflict tests to `oryxos-cli/src/test/java/org/oryxos/cli/WorkspaceInitializerTest.java`

### Implementation for User Story 2

- [ ] T010 [US2] Add two-pass path-kind preflight, create-missing-only behavior, preserved-path accounting, and actionable path conflict exceptions to `oryxos-cli/src/main/java/org/oryxos/cli/WorkspaceInitializer.java`

**Checkpoint**: Repeated initialization is non-destructive, partial valid
workspaces are repaired, and deterministic conflicts leave existing content
unchanged.

---

## Phase 5: User Story 3 - Understand the Initialization Result (Priority: P3)

**Goal**: Users receive accurate first-run, repaired/repeat, and failure output
with the target path, next step, and correct exit code.

**Independent Test**: Run the Picocli command against new, complete, partial,
and conflicting workspaces with captured output/error writers.

### Tests for User Story 3

- [ ] T011 [US3] Add first-run, repaired, preserved, and conflict output plus exit-code contract tests to `oryxos-cli/src/test/java/org/oryxos/cli/InitCommandTest.java`

### Implementation for User Story 3

- [ ] T012 [US3] Add new/repaired/preserved outcome derivation without filesystem mutation to `oryxos-cli/src/main/java/org/oryxos/cli/WorkspaceInitializationResult.java`
- [ ] T013 [US3] Render the absolute workspace path, preservation summary, next action, concise path-aware errors, non-zero failure code, and no stack trace in `oryxos-cli/src/main/java/org/oryxos/cli/InitCommand.java`

**Checkpoint**: All three stories satisfy the public CLI contract independently
at their respective test boundaries.

---

## Phase 6: Polish & Cross-Cutting Quality Gates

**Purpose**: Keep project documentation honest and prove both affected-module
and nine-module build health.

- [ ] T014 [P] Update the implemented-command status and runnable `oryxos init` example without claiming other CLI commands are complete in `README.md`
- [ ] T015 Run `mvn -pl oryxos-cli -am test`, execute the clean/repeat/partial/conflict scenarios, and reconcile any validation mismatch in `specs/002-workspace-init/quickstart.md`
- [ ] T016 Run `mvn clean package` from `pom.xml` and fix only workspace-init regressions in `oryxos-cli/src/main/` or `oryxos-cli/src/test/`
- [ ] T017 Run Spec Kit consistency analysis against `specs/002-workspace-init/spec.md`, `specs/002-workspace-init/plan.md`, and `specs/002-workspace-init/tasks.md`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: Starts immediately.
- **Phase 2 (Foundational)**: Depends on T001 and blocks all story work.
- **Phase 3 (US1)**: Depends on T002 and delivers the base command/MVP.
- **Phase 4 (US2)**: Depends on the US1 base initializer from T007.
- **Phase 5 (US3)**: Depends on the US1 base command from T008; after US1 it
  can proceed in parallel with US2 because it primarily changes command/result
  files while US2 changes initializer files.
- **Phase 6 (Polish)**: T014 can run after US3 behavior is settled; T015 depends
  on US1–US3; T016 follows T014–T015, and T017 is the final consistency gate.

### User Story Dependencies

```text
Setup T001
  └── Foundation T002
        └── US1 T003–T008 (MVP)
              ├── US2 T009–T010
              └── US3 T011–T013
                    └── Polish T014–T017
```

- **US1 (P1)**: No dependency on another story; establishes the command and
  clean initialization.
- **US2 (P2)**: Extends US1's initializer but remains independently testable
  through filesystem state and byte preservation.
- **US3 (P3)**: Extends US1's command presentation but remains independently
  testable through captured terminal streams and exit codes.

### Within Each User Story

- Add the story tests and confirm the intended failures before implementation.
- Keep starter content in resources rather than Java multiline constants.
- Complete filesystem behavior before relying on it in command output.
- Run the story-specific test class at its checkpoint.

## Parallel Opportunities

- T003 and T004 can run in parallel after T002 because they touch different
  test files and derive expectations from the frozen contract; T005 follows
  T003 in the same initializer test file.
- T006 can run in parallel with T003–T005 because it touches only resource
  files.
- After US1, T009–T010 (US2) and T011–T013 (US3) can be assigned in parallel;
  they operate on separate initializer versus command/result files.
- T014 can run in parallel with the final focused test pass once public output
  wording is stable.

## Parallel Example: User Story 1

```text
Task T003: clean creation tests in WorkspaceInitializerTest.java
Task T004: root command tests in InitCommandTest.java
Task T005: template and secret-safety tests in WorkspaceInitializerTest.java
Task T006: starter resources under src/main/resources/org/oryxos/cli/workspace/
```

T003 and T005 edit the same test file and therefore should be coordinated or
performed sequentially by one implementer even though each is independent of
T004 and T006.

## Parallel Example: User Stories 2 and 3

```text
Implementer A: T009–T010 in WorkspaceInitializerTest.java and WorkspaceInitializer.java
Implementer B: T011–T013 in InitCommandTest.java, WorkspaceInitializationResult.java, and InitCommand.java
```

## Implementation Strategy

### MVP First

1. Complete T001–T002.
2. Write and observe failures for T003–T005.
3. Complete T006–T008.
4. Run the US1 test classes and manually inspect a generated default Agent.
5. Stop here if only a demonstrable clean initialization is required.

### Incremental Delivery

1. **US1** creates a complete ready-to-edit workspace.
2. **US2** makes the same command safe for repeat and recovery use.
3. **US3** completes the terminal contract and operational clarity.
4. **Polish** aligns README status and runs focused tests, the full build, and
   the final Spec Kit consistency gate.

## Notes

- Do not modify or validate the content of any pre-existing workspace file.
- Do not add Spring, storage-module, network, or provider dependencies to
  `oryxos-cli` for this feature.
- Do not create optional Agent `skills/`, `scripts/`, or `REFERENCE.md`.
- Do not initialize database schema; only create the missing `oryxos.db`
  artifact.
- Keep all generated starter files free of real secrets and machine-specific
  paths.
