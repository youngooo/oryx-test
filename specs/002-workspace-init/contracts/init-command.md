# CLI Contract: `oryxos init`

## Synopsis

```text
oryxos init
```

The core-stage command accepts no positional workspace path. Standard Picocli
help options remain available.

## Target

The command targets `.oryxos/` beneath the process current directory. It does
not search parent directories and does not initialize a remote location.

## Successful outcome

Before exit, the command verifies the required artifact tree defined in
[`data-model.md`](../data-model.md). It creates only absent artifacts and
preserves every existing artifact with the expected file kind.

Standard output must:

1. identify the absolute workspace path;
2. distinguish newly created content from a fully preserved workspace;
3. identify a sensible next action, such as editing
   `.oryxos/agents/default/AGENT.md` or running `oryxos status`.

Exit code: `0`.

## Repeat and partial outcomes

- Re-running against a complete valid workspace succeeds without changing any
  existing file bytes.
- Running against a partial but non-conflicting workspace creates only missing
  items and reports a successful repaired result.
- An empty or malformed existing file is preserved; content validation and
  migration are outside this command.

Exit code: `0`.

## Failure outcome

Initialization fails when:

- a required directory path is occupied by a non-directory;
- a required file path is occupied by a directory;
- a required directory or file cannot be created or inspected;
- a packaged starter resource is unavailable.

Standard error must identify the affected path or operation in concise language.
The command must not print a success message or an application stack trace.
Existing items are never replaced, deleted, truncated, or renamed.

Exit code: non-zero (`1` for initialization failure).

## Runtime and security guarantees

- The command does not start Spring or the Agent runtime.
- The command does not access the network or call a model provider.
- No provider credential is required.
- Generated templates contain no real secret.
- Text templates are generated consistently from packaged UTF-8 resources.

## Examples

First run:

```text
$ oryxos init
Initialized OryxOS workspace: /work/example/.oryxos
Next: edit .oryxos/agents/default/AGENT.md or run `oryxos status`.
```

Repeat run:

```text
$ oryxos init
OryxOS workspace already initialized; existing content preserved:
/work/example/.oryxos
Next: edit .oryxos/agents/default/AGENT.md or run `oryxos status`.
```

Conflict:

```text
$ oryxos init
Initialization failed: expected directory but found another item:
/work/example/.oryxos/agents
```
