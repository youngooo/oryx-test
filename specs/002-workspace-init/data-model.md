# Data Model: OryxOS Workspace Initialization

This feature creates filesystem artifacts rather than business database
records. The model below defines the required workspace state and the result
reported by initialization.

## Workspace

Represents `.oryxos/` directly beneath the command's current directory.

| Field | Meaning | Validation |
|-------|---------|------------|
| `projectDirectory` | Directory from which the command is run | Must resolve to an accessible directory |
| `workspacePath` | `<projectDirectory>/.oryxos` | Must be a directory when it already exists |
| `artifacts` | Fixed set of required paths | Every path must have its documented file kind |

### Required artifact tree

| Relative path | Kind | Initial source | Existing-item behavior |
|---------------|------|----------------|------------------------|
| `agents/default/` | Directory | Created empty | Preserve directory; reject non-directory |
| `memory/` | Directory | Created empty | Preserve directory; reject non-directory |
| `logs/` | Directory | Created empty | Preserve directory; reject non-directory |
| `agents/default/AGENT.md` | Text file | Packaged template | Preserve file; reject directory |
| `memory/MEMORY.md` | Text file | Packaged template | Preserve file; reject directory |
| `mcp_servers.yaml` | Text file | Packaged template | Preserve file; reject directory |
| `AGENTS.md` | Text file | Packaged template | Preserve file; reject directory |
| `SOUL.md` | Text file | Packaged template | Preserve file; reject directory |
| `USER.md` | Text file | Packaged template | Preserve file; reject directory |
| `oryxos.db` | Data file | Created empty | Preserve file; reject directory |

Parent directories such as `.oryxos/` and `agents/` are required structural
paths and follow the same directory-kind validation.

## Default Agent

Represents `.oryxos/agents/default/AGENT.md`.

| Attribute | Requirement |
|-----------|-------------|
| Name | `default` |
| Definition form | YAML frontmatter followed by Markdown task instructions |
| Provider configuration | Editable starter values; secret values referenced only through environment placeholders |
| Tools/channels/schedules/bootstrap/settings | Structurally valid core-stage fields with conservative defaults |
| Secret content | No real key, token, password, or credential |

The file is a user-editable starting definition. Initialization does not derive
or register its runtime Profile.

## Bootstrap Document

Represents each of `AGENTS.md`, `SOUL.md`, and `USER.md`.

| Attribute | Requirement |
|-----------|-------------|
| Purpose | Explain project behavior, default personality, or user preference context |
| Encoding | UTF-8 text |
| Ownership after creation | User-owned and never overwritten by initialization |

## Workspace Initialization Result

Returned by the filesystem collaborator to the command layer.

| Field | Meaning |
|-------|---------|
| `workspacePath` | Absolute normalized path of the targeted workspace |
| `createdPaths` | Required paths created during this run |
| `preservedPaths` | Required paths that already existed with the expected kind |

### Derived outcomes

- **New**: all required paths were absent and are now created.
- **Repaired**: both `createdPaths` and `preservedPaths` are non-empty.
- **Preserved**: no required path needed creation.
- **Failed**: preflight finds a kind conflict or creation raises an I/O error;
  no successful result is returned.

## State Transitions

```text
ABSENT ──init──> CREATED
EXPECTED_KIND ──init──> PRESERVED
CONFLICTING_KIND ──init──> FAILED_AND_UNCHANGED
PARTIAL_VALID ──init──> REPAIRED
CREATE_IO_FAILURE ──retry after correction──> REPAIRED
```

Initialization never transitions an existing file to different content.
