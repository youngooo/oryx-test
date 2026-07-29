# Tasks: OryxOS Web Info Hello

**Input**: Design documents from `specs/001-web-info-hello/`
**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/openapi.yaml`, `quickstart.md`

## Phase 1: Setup

**Purpose**: Provide the existing Web module with its standard test support.

- [x] T001 Add the test-scoped Spring Boot test dependency in `oryxos-web/pom.xml`

---

## Phase 2: Foundational

No additional model, service, database, authentication, or infrastructure foundation is needed for this static read-only response.

---

## Phase 3: User Story 1 - Confirm OryxOS Web Identity (Priority: P1) 🎯 MVP

**Goal**: A single request to the existing product-information resource identifies OryxOS, its current stage, and the exact greeting.

**Independent Test**: A MockMvc request to `/api/v1/info` returns HTTP 200 with `name=OryxOS`, `stage=project-initialization`, and `message=Hello from OryxOS`.

### Tests for User Story 1

- [x] T002 [US1] Create the failing response-contract test in `oryxos-web/src/test/java/org/oryxos/web/SystemApiControllerTest.java`

### Implementation for User Story 1

- [x] T003 [US1] Add the deterministic greeting to the info response in `oryxos-web/src/main/java/org/oryxos/web/SystemApiController.java`
- [x] T004 [US1] Run `mvn -pl oryxos-web -am test` and record the passing result in `specs/001-web-info-hello/tasks.md`

**Checkpoint**: User Story 1 is independently functional and its HTTP contract is automated.

---

## Phase 4: Polish & Cross-Cutting Concerns

- [x] T005 Run `mvn clean package` for all nine modules and record the passing result in `specs/001-web-info-hello/tasks.md`
- [x] T006 Compare the implemented response with `specs/001-web-info-hello/contracts/openapi.yaml` and confirm no sensitive or machine-specific value is exposed

---

## Dependencies & Execution Order

### Phase Dependencies

- Phase 1 enables the test toolchain.
- Phase 2 has no tasks for this deliberately minimal feature.
- Phase 3 depends on T001; T002 must be written before T003, then T004 verifies both.
- Phase 4 depends on the completed user story.

### User Story Dependencies

- **User Story 1 (P1)** has no dependency on another user story and is the complete MVP.

### Parallel Opportunities

No implementation tasks should run in parallel: the exercise intentionally demonstrates a short test-first dependency chain. Documentation review in T006 may be prepared while the full reactor build in T005 runs, but completion should be recorded only after both finish.

## Implementation Strategy

1. Add only the missing test dependency.
2. Write and run the contract test to observe the expected failure for the missing greeting.
3. Make the smallest controller change that satisfies the contract.
4. Run the affected Maven slice.
5. Run the complete reactor package build and verify contract alignment.

## Format Validation

All six tasks use the required checkbox, sequential task ID, applicable user-story label, concrete action, and exact file path or executable command.
