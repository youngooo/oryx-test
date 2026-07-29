# Research: OryxOS Workspace Initialization

## Sources

The decisions below reconcile the four requested implementation references:

- `docs/TechnicalSolution.md`: module ownership, workspace tree, lightweight
  command boundary, and credential rules.
- `docs/DemandAnalysis.md`: required initialization outcome, default Agent,
  compatibility, and end-to-end acceptance obligation.
- `docs/AiProgrammingGuide.md`: placement of `oryxos init` in the CLI portion of
  the first runnable ReAct vertical slice.
- `docs/CliGuide.md`: current-directory behavior, exact user-facing tree,
  lightweight startup, and no-overwrite idempotency.

## Decision 1: Keep initialization entirely in `oryxos-cli`

**Decision**: Implement `InitCommand` and its concrete filesystem collaborator
in `oryxos-cli`. The command must not start a Spring application context.

**Rationale**: The technical solution assigns one Picocli class per subcommand
and explicitly classifies `init` as a lightweight file command. The repository
already has the Picocli root command and the required module dependency.

**Alternatives considered**:

- Put workspace creation in `oryxos-core`: rejected because it is currently a
  CLI-triggered support concern and would widen the core API prematurely.
- Launch `oryxos-boot` and inject a Spring service: rejected because it violates
  the documented lightweight-command contract.

## Decision 2: Package editable text templates as resources

**Decision**: Store the six non-empty starter templates beneath
`oryxos-cli/src/main/resources/org/oryxos/cli/workspace/`. Keep the required path
manifest in the initializer and create `oryxos.db` as a missing empty file.

**Rationale**: Resource files make user-facing starter content reviewable,
testable, and available from both classes and packaged JARs. An empty SQLite file
is a valid starting artifact; schema ownership belongs to the later storage
feature.

**Alternatives considered**:

- Embed all templates as Java string constants: rejected because multiline
  Markdown/YAML becomes harder to review and maintain.
- Add a dependency from `oryxos-cli` to `oryxos-storage` to initialize schema:
  rejected because the current feature only promises the workspace artifact,
  and storage schema work is explicitly staged later.
- Package a prebuilt binary database: rejected because it introduces opaque
  binary state and premature schema coupling.

## Decision 3: Use two-pass, create-missing-only initialization

**Decision**: First validate every existing required path against its expected
file or directory kind. Only after preflight succeeds, create missing
directories followed by missing files. Never open an existing file for write.

**Rationale**: This directly enforces the CLI guide's idempotency promise and
avoids creating part of a workspace when a deterministic kind conflict was
discoverable in advance. A later I/O failure can still leave a partial tree;
the same create-missing behavior repairs it on the next run.

**Alternatives considered**:

- Unconditionally copy templates with replacement: rejected because it destroys
  user-owned Agent, bootstrap, and memory content.
- Write to a staging tree and rename the entire workspace atomically: rejected
  because it complicates partial-workspace repair and cross-filesystem behavior.
- Treat an empty or malformed existing file as missing: rejected because the
  specification defines all existing files as user-owned.

## Decision 4: Return structured initialization results

**Decision**: Return the absolute workspace path plus created and preserved
path collections/counts from the initializer. Translate I/O failures into a
concise command error and non-zero exit result.

**Rationale**: The CLI must accurately distinguish a new, repaired, and
unchanged workspace without mixing filesystem operations with presentation.
Structured results also make the command output deterministic to test.

**Alternatives considered**:

- Print directly from each filesystem operation: rejected because it couples
  low-level behavior to terminal output and makes false or partial success
  messages harder to prevent.
- Return only a boolean: rejected because it cannot explain mixed
  created/preserved outcomes.

## Decision 5: Test filesystem and CLI behavior separately

**Decision**: Use JUnit 5 `@TempDir` for clean, partial, conflicting,
non-ASCII-path, and repeated initialization cases. Execute `InitCommand` through
Picocli with injected target paths and writers for output and exit-code tests.

**Rationale**: Temporary directories provide deterministic, isolated evidence
for byte preservation and path behavior. Picocli-level tests verify the public
command registration and terminal contract without booting Spring.

**Alternatives considered**:

- End-to-end tests only through a packaged JAR: rejected as the sole test layer
  because failures would be slower and less localized.
- Mutate `user.dir` in tests: rejected because it is process-global and can make
  concurrent tests unreliable.

## Resolved Clarifications

No `NEEDS CLARIFICATION` items remain. The four source documents and existing
module skeleton determine the command location, required artifacts,
idempotency rule, runtime boundary, and test approach.
