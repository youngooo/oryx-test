# Architecture Requirements Checklist: OryxOS Core Runtime

**Purpose**: Review whether the feature requirements and implementation plan state the mandatory architecture boundaries completely, clearly, consistently, and measurably before task generation
**Created**: 2026-07-29
**Feature**: [spec.md](../spec.md)

**Note**: This checklist evaluates requirements-writing quality. It does not test implementation behavior.

## Requirement Completeness

- [x] CHK001 Is `MemoryService` explicitly required as the single memory-facing contract used by ReAct and Prompt assembly, instead of leaving Session and long-term memory as separate caller dependencies? [Gap, Spec §FR-013, Plan §Architecture]
- [x] CHK002 Are all three conceptual memory layers documented, with Session and long-term memory identified as core-stage scope and episodic memory identified as extension-stage scope? [Completeness, Spec §Memory and Session Capability, Plan §Architecture]
- [x] CHK003 Are the responsibilities of `MemoryService`, `SessionManager`, `LongTermMemory`, and `LongTermMemoryStore` distinguished in the requirements, including the authoritative core-stage backend set? [Gap, Spec §FR-019–FR-024, Plan §Delivery Phase 2]
- [x] CHK004 Is the requirement that built-in, MCP, and Java Plugin Tool facilities remain within one `oryxos-tool` Maven module documented independently of internal package layout? [Completeness, Constitution §V and §Technical Constraints]
- [x] CHK005 Is the `MemoryTools` ownership exception clarified so that `save_memory` and `recall_memory` can live in `oryxos-memory` without being interpreted as a second general Tool Maven module? [Gap, Plan §Project Structure]
- [x] CHK006 Is an Agent directory explicitly defined as a context source—not an `OryxTool`—with `ContextLoader` responsible for `AGENT.md` and Bootstrap prompt context? [Completeness, Spec §Agent Definition and §FR-013, Constitution §V]
- [x] CHK007 Is disabling Spring AI/Spring AI Alibaba automatic Tool execution stated as a normative feature requirement, not only a Constitution or Plan constraint? [Gap, Spec §FR-012 and §FR-016, Constitution §IV]

## Requirement Clarity

- [x] CHK008 Is “three-layer memory” defined clearly enough that implementers cannot mistake the deferred episodic layer for a core-stage deliverable? [Clarity, Plan §Architecture, Spec §Scope & Governance Constraints]
- [x] CHK009 Is the complete `MemoryContext` composition defined, including history bounds, core-memory preservation, archival-memory limits, and the empty episodic contribution? [Clarity, Spec §FR-013, §FR-022, §FR-047]
- [x] CHK010 Is it unambiguous that ReAct and Prompt assembly must use only `MemoryService`, without directly accessing `SessionStore`, `MEMORY.md`, `memory_entries`, or Mem0? [Clarity, Plan §Architecture]
- [x] CHK011 Is `memory.backend` defined with allowed values, default value, invalid-value outcome, and configuration ownership? [Clarity, Plan §Delivery Phase 2, Data Model §LongTermMemoryStore]
- [x] CHK012 Are the allowed Spring AI protocol/schema responsibilities and prohibited automatic Tool scheduling responsibilities stated precisely and objectively? [Clarity, Constitution §IV, Plan §Summary]

## Requirement Consistency

- [x] CHK013 Is the Plan’s three-backend core scope reconciled with the Spec statement that vector databases and semantic Memory extraction are out of scope? [Conflict, Spec §Scope & Governance Constraints, Plan §Delivery Phase 2]
- [x] CHK014 Is `Mem0MemoryStore` reconciled with the Constitution requirement that core long-term memory be based on local `MEMORY.md` and vector retrieval remain an extension? [Conflict, Constitution §VI, Plan §Delivery Phase 2]
- [x] CHK015 Is the Plan summary’s statement that long-term memory remains in workspace files consistent with its later SQLite and self-hosted Mem0 backend requirements? [Conflict, Plan §Summary and §Technical Context]
- [x] CHK016 Is keyword-only recall in the Spec consistent with the semantic-retrieval capability attributed to the Mem0 backend, including what behavior callers may rely on? [Conflict, Spec §FR-021 and §Assumptions, Data Model §LongTermMemoryStore]
- [x] CHK017 Is ownership of the `MemoryService` contract in core and its façade implementation in `oryxos-memory` consistent with the documented nine-module responsibilities? [Consistency, Plan §Dependency Direction, Constitution §II]
- [x] CHK018 Are `MemoryTools` consistently described as `OryxTool` implementations registered in the shared `ToolRegistry`, despite belonging to the Memory module? [Consistency, Spec §FR-025–FR-026, Plan §Project Structure]
- [x] CHK019 Are Agent loading and automatic Tool execution boundaries consistent across the Spec, Plan, Agent contract, and Constitution? [Consistency, Spec §FR-002, §FR-012, §FR-016 and §FR-038, Constitution §IV–V]

## Acceptance Criteria Quality

- [x] CHK020 Is there a measurable acceptance criterion showing that one `MemoryService` view contains the required Session and long-term-memory portions without exposing separate paths to ReAct? [Gap, Spec §SC-003, Plan §Phase 2 Gate]
- [x] CHK021 Are backend-conformance criteria identical and measurable for append, load, keyword recall, scope preservation, truncation, and concurrent visibility? [Acceptance Criteria, Spec §SC-003 and §SC-013, Plan §Phase 2 Gate]
- [x] CHK022 If three memory backends remain core scope, do success criteria cover each backend rather than validating only `MEMORY.md`? [Coverage, Spec §SC-003, Plan §Verification Strategy]
- [x] CHK023 Is backend substitution defined with objective invariants for `MemoryService`, `PromptBuilder`, `MemoryTools`, and `ReActLoop` contracts? [Measurability, Plan §Delivery Phase 2]
- [x] CHK024 Are the single Tool module, Agent-as-context boundary, and disabled automatic Tool execution each tied to an objectively reviewable acceptance criterion? [Traceability, Gap, Spec §SC-002 and §SC-004, Constitution §IV–V]

## Scenario Coverage

- [x] CHK025 Are primary requirements complete for composing Session history and selected long-term-memory content into one prompt context? [Coverage, Spec §FR-013 and §FR-022]
- [x] CHK026 Are exception requirements defined for unavailable or malformed Markdown, SQLite, and Mem0 backends under one `MemoryService` error contract? [Gap, Exception Flow]
- [x] CHK027 Is recovery or migration behavior defined—or explicitly excluded—when an operator changes `memory.backend` while data remains in the previous backend? [Gap, Recovery Flow]
- [x] CHK028 Are partial-memory failure requirements defined when Session history is available but the selected long-term-memory backend is unavailable? [Gap, Exception Flow]

## Edge Case Coverage

- [x] CHK029 Is the relationship between “core memory is never truncated” and the deterministic total prompt-size limit defined when core memory alone exceeds the budget? [Ambiguity, Spec §FR-022, Data Model §LongTermMemoryStore]
- [x] CHK030 Are duplicate, empty, oversized, and concurrently appended memory entries addressed consistently for every required backend? [Coverage, Spec §Edge Cases and §FR-047]
- [x] CHK031 Are duplicate Tool names across built-in, MCP, Java Plugin, and Memory Tool registrations covered by explicit requirements? [Gap, Edge Case, Spec §FR-026]
- [x] CHK032 Is the required treatment of a model response containing both final text and Tool calls defined without delegating the decision to Spring AI? [Gap, Edge Case, Spec §FR-012–FR-016]

## Non-Functional Requirements

- [x] CHK033 Are latency and timeout requirements defined for remote Mem0 access separately from local Markdown and SQLite memory operations? [Gap, Performance, Spec §SC-008]
- [x] CHK034 Are data-residency requirements explicit that any Mem0 endpoint must be self-hosted and operator-controlled? [Security, Spec §FR-040, Plan §Research]
- [x] CHK035 Are credential-redaction requirements complete for Mem0 tokens, authorization headers, errors, logs, and audit records? [Security, Spec §FR-039 and §SC-011]
- [x] CHK036 Are the security limits of `WhitelistSandbox` stated consistently without implying that a single Tool module provides process-level isolation? [Clarity, Constitution §V, Spec §Scope & Governance Constraints]

## Dependencies & Assumptions

- [x] CHK037 Is the compatible Spring AI/Spring AI Alibaba version and the mechanism required to disable automatic Tool execution documented as a dependency assumption requiring validation? [Dependency, Plan §Research]
- [x] CHK038 If Mem0 remains in scope, are its supported API version, authentication model, availability assumption, self-hosting boundary, and impact on the no-semantic-memory assumption documented? [Dependency, Gap, Spec §Assumptions]

## Ambiguities & Conflicts Requiring Resolution

- [x] CHK039 Has one authoritative decision been recorded for whether the core release includes only `MarkdownMemoryStore` or all three Markdown/SQLite/Mem0 backends, with any governance exception documented before task generation? [Conflict, Governance, Constitution §VI, Spec §Scope & Governance Constraints, Plan §Delivery Phase 2]
- [x] CHK040 Are the four mandatory invariants—unified `MemoryService`, single Tool Maven module, Agent-as-context, and disabled Spring AI automatic Tool execution—traceable from requirements through Plan gates without reviewer inference? [Traceability, Gap]

## Completion Evidence

- **CHK020**: SC-015 now defines the complete single-call MemoryContext acceptance shape.
- **CHK030**: FR-051, Data Model §LongTermMemoryStore, and T050 define empty, oversized, duplicate, and concurrent append behavior shared by all backends.
- **CHK031**: FR-026 and the Edge Cases section define duplicate Tool-name registration failure.
- **CHK032**: FR-016 defines Tool-call precedence when a model response also contains final-looking text.
- **CHK033**: Plan §Technical Context fixes Mem0 connect/response timeouts separately from local backends and the overall Agent deadline.
- **CHK037**: Research §Spring AI Boundary pins version 1.1.2 and records internalToolExecutionEnabled(false), direct ChatModel use, and the prohibited advisor/manager path.
- **CHK038**: Research §Three-Layer Memory records the self-hosted Mem0 REST v1 subset, authentication, version pinning, availability, timeout, and semantic-boundary assumptions.
- **Result**: 40/40 architecture requirement-quality checks pass after cross-checking Spec, Plan, Data Model, Research, Tasks, contracts, and Constitution.

## Notes

- Check items off as completed: `[x]`.
- Add findings and links inline.
- Resolve CHK013–CHK016 and CHK039 before `/speckit.tasks`; they represent scope conflicts that can produce incompatible task sets.
- Items evaluate the quality of the written requirements and Plan, not whether code has already been implemented.
