# Implementation Plan: OryxOS Workspace Initialization

**Branch**: `002-workspace-init` | **Date**: 2026-07-29 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/002-workspace-init/spec.md`

## Summary

Add the lightweight `oryxos init` Picocli subcommand in `oryxos-cli`. The
command uses only Java filesystem APIs and packaged starter resources to create
the documented `.oryxos/` tree in the current directory. A two-pass
initializer first validates existing path kinds, then creates only missing
paths, preserving every existing file. The command reports created and
preserved content without starting Spring, contacting a provider, or requiring
credentials.

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Picocli 4.7.7; Java `Path`/`Files` APIs; JUnit 5 for tests

**Storage**: Local `.oryxos/` filesystem tree and an initially empty
`oryxos.db` data artifact; schema creation remains in the storage feature

**Testing**: JUnit 5 with `@TempDir`; Picocli `CommandLine` execution tests;
Maven Surefire

**Target Platform**: JDK 21 command line on documented Linux distributions;
implementation remains portable to Windows and other JDK 21 filesystems

**Project Type**: Maven multi-module CLI within the existing nine-module
single-application repository

**Performance Goals**: Complete a local initialization in under one second on
a normal local filesystem and preserve the documented lightweight-command
startup profile

**Constraints**: Offline-capable; no Spring application context; no external
provider or credentials; never overwrite existing files; actionable filesystem
errors; UTF-8 starter text; current directory is the only target

**Scale/Scope**: One workspace containing 3 required directory paths and 7
required files, including one default Agent

## Constitution Check

*GATE: Passed before Phase 0 research and passed again after Phase 1 design.*

- **Documentation and Contracts First — PASS**: The CLI and workspace contracts
  come from `docs/CliGuide.md`, `docs/DemandAnalysis.md`, and
  `docs/TechnicalSolution.md`; `contracts/init-command.md` makes exit and
  filesystem behavior testable.
- **Preserve the Nine-Module Architecture — PASS**: The feature changes only
  `oryxos-cli` plus its tests and resources. No module or dependency direction
  changes are introduced.
- **Testable Vertical Slices — PASS**: Clean initialization, repeat/repair
  behavior, and user feedback each have independent acceptance tests. The
  affected-module test and full Maven package build are required.
- **Secure and Controllable Agent Runtime — PASS**: Starter files contain no
  real secrets and initialization does not start or invoke the Agent runtime.
- **Simplicity and Honest Scope — PASS**: The design uses JDK filesystem APIs
  and one concrete initializer, with no migration framework, remote workspace,
  schema management, or speculative abstraction.

## Project Structure

### Documentation (this feature)

```text
specs/002-workspace-init/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── init-command.md
└── tasks.md
```

### Source Code (repository root)

```text
oryxos-cli/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/org/oryxos/cli/
    │   │   ├── OryxOsCommand.java
    │   │   ├── InitCommand.java
    │   │   ├── WorkspaceInitializer.java
    │   │   └── WorkspaceInitializationResult.java
    │   └── resources/org/oryxos/cli/workspace/
    │       ├── agents/default/AGENT.md
    │       ├── memory/MEMORY.md
    │       ├── mcp_servers.yaml
    │       ├── AGENTS.md
    │       ├── SOUL.md
    │       └── USER.md
    └── test/java/org/oryxos/cli/
        ├── InitCommandTest.java
        └── WorkspaceInitializerTest.java
```

**Structure Decision**: Keep command parsing, output, filesystem behavior, and
starter resources inside the responsible `oryxos-cli` module. The initializer
is a concrete package-local collaborator for direct filesystem testing, not a
cross-module service or interface. `oryxos.db` is created as a missing file by
the initializer rather than packaged as a binary resource.

## Design Decisions

1. `OryxOsCommand` registers `InitCommand` as a Picocli subcommand.
2. `InitCommand` resolves the current directory and delegates filesystem work
   to `WorkspaceInitializer`, then renders the result to the injected writers.
3. `WorkspaceInitializer` owns a fixed manifest of required directories,
   packaged text templates, and the database artifact.
4. Initialization performs a read-only preflight over all required paths before
   creating anything. Existing paths with the expected kind are preserved;
   kind conflicts fail with the exact path.
5. After a successful preflight, missing directories are created before missing
   files. Text resources are copied as UTF-8 bytes and `oryxos.db` is created
   empty for later storage initialization.
6. A result value reports the absolute workspace path and created/preserved
   counts so the command can distinguish first, repaired, and repeat runs.
7. Tests exercise the initializer with temporary directories and the public CLI
   contract through Picocli without changing the process-wide working directory.

## Post-Design Constitution Re-check

The Phase 1 artifacts introduce no new module, framework, runtime coupling, or
unresolved clarification. The CLI contract, artifact model, and quickstart
comply with all seven constitution principles. ReAct, Provider mapping, Tool
dispatch, Sandbox, Session audit writes, and long-term Memory are not invoked or
changed by this lightweight filesystem command. No complexity exception is
required.

## Complexity Tracking

No constitution violations require justification.
