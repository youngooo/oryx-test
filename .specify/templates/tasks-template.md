---

description: "OryxOS dependency-ordered implementation task template"
---

# Tasks: [FEATURE NAME]

**Input**: Design documents from `/specs/[###-feature-name]/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`,
`contracts/`, and `quickstart.md` when applicable

**Tests**: Tests are REQUIRED for every changed behavior. Include the normal
path and applicable input-validation, failure, boundary, persistence, contract,
and security paths. Place test tasks before the implementation they verify.

**Organization**: Group tasks by independently testable user story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Only when the task touches different files and has no incomplete
  dependency
- **[Story]**: Required in user-story phases (`[US1]`, `[US2]`, and so on)
- Every task MUST name an exact repository-relative file path

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Add only the dependencies and test infrastructure required by the
feature.

- [ ] T001 Add required feature/test dependency in `[responsible-module]/pom.xml`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Implement shared contracts or models that block all user stories.

- [ ] T002 Create shared feature contract in `[responsible-module]/src/main/java/[package]/[Contract].java`
- [ ] T003 [P] Add shared test fixture in `[responsible-module]/src/test/java/[package]/[Fixture].java`

When Memory is in scope, foundational tasks MUST establish `MemoryService` as
the only ReAct-facing façade before backend tasks and MUST add one shared
`LongTermMemoryStore` contract suite for Markdown, SQLite, and self-hosted Mem0.
Tool tasks MUST preserve the single general `oryxos-tool` Maven module,
register `MemoryTools` through the shared registry, and keep Agent/context
loading outside the Tool model.

**Checkpoint**: Foundation compiles and shared tests can run.

---

## Phase 3: User Story 1 - [Title] (Priority: P1) 🎯 MVP

**Goal**: [User-visible outcome]

**Independent Test**: [Runnable proof of this story alone]

### Required Tests for User Story 1

- [ ] T004 [P] [US1] Add normal-path test in `[responsible-module]/src/test/java/[package]/[Feature]Test.java`
- [ ] T005 [US1] Add applicable validation, failure, and boundary tests in `[responsible-module]/src/test/java/[package]/[Feature]Test.java`
- [ ] T006 [P] [US1] Add public contract or integration test in `[responsible-module]/src/test/java/[package]/[Feature]ContractTest.java`

### Implementation for User Story 1

- [ ] T007 [US1] Implement the minimal story behavior in `[responsible-module]/src/main/java/[package]/[Feature].java`
- [ ] T008 [US1] Wire the public CLI/API/runtime entry in `[responsible-module]/src/main/java/[package]/[Entry].java`

**Checkpoint**: User Story 1 passes independently and provides the MVP Demo.

---

## Phase 4: User Story 2 - [Title] (Priority: P2)

**Goal**: [User-visible outcome]

**Independent Test**: [Runnable proof of this story alone]

### Required Tests for User Story 2

- [ ] T009 [US2] Add normal, validation, failure, and boundary tests in `[responsible-module]/src/test/java/[package]/[Feature]Test.java`

### Implementation for User Story 2

- [ ] T010 [US2] Implement the story behavior in `[responsible-module]/src/main/java/[package]/[Feature].java`

**Checkpoint**: User Stories 1 and 2 both pass independently.

---

## Phase 5: User Story 3 - [Title] (Priority: P3)

**Goal**: [User-visible outcome]

**Independent Test**: [Runnable proof of this story alone]

### Required Tests for User Story 3

- [ ] T011 [US3] Add normal, validation, failure, and boundary tests in `[responsible-module]/src/test/java/[package]/[Feature]Test.java`

### Implementation for User Story 3

- [ ] T012 [US3] Implement the story behavior in `[responsible-module]/src/main/java/[package]/[Feature].java`

**Checkpoint**: All user stories pass independently.

---

## Final Phase: Documentation, Security, and Quality Gates

- [ ] T013 [P] Update affected contract and runtime guidance in `docs/[affected-document].md`
- [ ] T014 Run affected-module tests and reconcile evidence in `specs/[###-feature-name]/quickstart.md`
- [ ] T015 Run `mvn clean package` from `pom.xml` and resolve only feature-related regressions
- [ ] T016 Run Spec Kit consistency analysis against `specs/[###-feature-name]/spec.md`, `specs/[###-feature-name]/plan.md`, and `specs/[###-feature-name]/tasks.md`

---

## Dependencies & Execution Order

- Setup has no dependencies.
- Foundational work depends on Setup and blocks all user stories.
- User stories start after Foundation and follow documented story dependencies.
- Within each story, required tests precede implementation.
- Final documentation and quality gates follow all selected stories.

## Parallel Opportunities

- Mark tasks `[P]` only when they change different files and share no incomplete
  dependency.
- Different stories may proceed in parallel only when their plan dependencies
  and files do not conflict.

## Implementation Strategy

1. Complete Setup and Foundation.
2. Implement and verify User Story 1 as the MVP.
3. Add each later story without breaking earlier acceptance tests.
4. Run the quickstart/Demo, affected-module tests, full Maven build, and
   consistency analysis before completion.

## Notes

- Preserve the nine-module boundary and use the smallest responsible module.
- Do not enable Spring AI automatic Tool execution.
- Do not let ReAct or Prompt assembly bypass `MemoryService`; keep episodic
  memory deferred and apply the same contract to all three required
  long-term-memory backends.
- Keep general Tool infrastructure in `oryxos-tool`; do not model Agent
  directories, `AGENT.md`, `AgentLoader`, or `ContextLoader` as Tools.
- Do not place secrets in source, tests, Agent definitions, examples, or logs.
- Add persistence, audit, Sandbox, and security tasks whenever the feature
  touches those concerns.
- Do not claim extension-stage capabilities as implemented.
