# Implementation Plan: [FEATURE]

**Branch**: `[###-feature-name]` | **Date**: [DATE] | **Spec**: [link]

**Input**: Feature specification from `/specs/[###-feature-name]/spec.md`

**Note**: This template is filled in by the `__SPECKIT_COMMAND_PLAN__` command; its definition describes the execution workflow.

## Summary

[Extract from feature spec: primary requirement + technical approach from research]

## Technical Context

<!--
  ACTION REQUIRED: Replace the content in this section with the technical details
  for the project. The structure here is presented in advisory capacity to guide
  the iteration process.
-->

**Language/Version**: [e.g., Python 3.11, Swift 5.9, Rust 1.75 or NEEDS CLARIFICATION]

**Primary Dependencies**: [e.g., FastAPI, UIKit, LLVM or NEEDS CLARIFICATION]

**Storage**: [if applicable, e.g., PostgreSQL, CoreData, files or N/A]

**Testing**: [e.g., pytest, XCTest, cargo test or NEEDS CLARIFICATION]

**Target Platform**: [e.g., Linux server, iOS 15+, WASM or NEEDS CLARIFICATION]

**Project Type**: [e.g., library/cli/web-service/mobile-app/compiler/desktop-app or NEEDS CLARIFICATION]

**Performance Goals**: [domain-specific, e.g., 1000 req/s, 10k lines/sec, 60 fps or NEEDS CLARIFICATION]

**Constraints**: [domain-specific, e.g., <200ms p95, <100MB memory, offline-capable or NEEDS CLARIFICATION]

**Scale/Scope**: [domain-specific, e.g., 10k users, 1M LOC, 50 screens or NEEDS CLARIFICATION]

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **Documentation and contracts**: Cite the controlling requirements and
  technical-solution sections. Identify every affected public REST or CLI
  contract and required documentation update.
- **Architecture and module ownership**: Confirm the nine-module reactor,
  dependency direction, single executable JAR, responsible module, and
  synchronous Spring MVC/virtual-thread model remain intact.
- **Core-stage scope and simplicity**: Classify the feature as core or
  extension stage. Reject undocumented scope expansion and justify each new
  abstraction with real implementations or an explicit extension boundary.
- **Runtime control and security**: Confirm OryxOS retains ReAct/Tool dispatch,
  Provider mapping is explicit, Tool/Sandbox checks are preserved, secrets are
  excluded, and required Session/LLM/Tool audit writes are designed.
- **Memory architecture**: Confirm `MemoryService` is the only ReAct-facing
  memory contract, Session and long-term memory remain separate behind the
  façade, episodic memory is deferred, and Markdown/SQLite/self-hosted Mem0
  backends share one `LongTermMemoryStore` contract.
- **Tool and Agent boundaries**: Confirm general Tool infrastructure remains in
  the single `oryxos-tool` Maven module, `MemoryTools` use the shared registry,
  and Agent directories/loaders remain prompt context rather than Tools.
- **Test and Demo gates**: Name normal, validation, failure, boundary,
  persistence, contract, and security coverage applicable to the feature.
  Require affected-module tests, a reproducible quickstart or Demo, and
  `mvn clean package`.

Any failing gate MUST stop planning unless the constitution is explicitly
amended. Re-run and record this check after Phase 1 design.

## Project Structure

### Documentation (this feature)

```text
specs/[###-feature]/
├── plan.md              # This file (__SPECKIT_COMMAND_PLAN__ command output)
├── research.md          # Phase 0 output (__SPECKIT_COMMAND_PLAN__ command)
├── data-model.md        # Phase 1 output (__SPECKIT_COMMAND_PLAN__ command)
├── quickstart.md        # Phase 1 output (__SPECKIT_COMMAND_PLAN__ command)
├── contracts/           # Phase 1 output (__SPECKIT_COMMAND_PLAN__ command)
└── tasks.md             # Phase 2 output (__SPECKIT_COMMAND_TASKS__ command - NOT created by __SPECKIT_COMMAND_PLAN__)
```

### Source Code (repository root)
```text
oryxos-core/
oryxos-provider/
oryxos-memory/
oryxos-tool/
oryxos-channel-cli/
oryxos-web/
oryxos-storage/
oryxos-cli/
oryxos-boot/
```

**Structure Decision**: [Document the selected structure and reference the real
module directories, production packages, resources, and test paths affected by
this feature. Explain why the selected module owns the behavior and confirm
that dependency direction remains valid.]

## Complexity Tracking

> Constitution violations cannot be justified here. Fill this table only for
> design complexity that passes every Constitution Check gate but still needs
> an explicit simplicity rationale.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| [e.g., 4th project] | [current need] | [why 3 projects insufficient] |
| [e.g., Repository pattern] | [specific problem] | [why direct DB access insufficient] |
