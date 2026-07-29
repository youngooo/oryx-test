# OryxTool Contract

All built-in, MCP, and Java plugin Tools MUST adapt to this logical contract before registration.

```java
public interface OryxTool {
    String getName();
    String getDescription();
    JsonNode getInputSchema();
    ToolResult execute(JsonNode arguments, ToolExecutionContext context);
}
```

`ToolExecutionContext` contains the Session ID, Agent/Profile name, normalized workspace root, invocation deadline, and safe channel/user metadata. It MUST NOT expose Provider credentials.

## Registry Rules

1. Tool names are unique and stable.
2. Registry initialization fails on duplicates.
3. A Profile sees only its configured Tool subset.
4. Tool metadata is immutable after registry publication; reload rebuilds a new registry snapshot.
5. The core stage registers exactly these built-ins:
   `read_file`, `write_file`, `list_dir`, `shell`, `http_get`, `http_post`,
   `save_memory`, `recall_memory`, and `notify`.

## Execution Pipeline

```text
lookup
  -> JSON Schema validation
  -> argument normalization
  -> Sandbox.enforce
  -> OryxTool.execute
  -> ToolResult normalization
  -> redacted audit write
  -> ordered Session append
```

Every attempted call receives an audit record, including lookup, validation, Sandbox, timeout, transport, and Tool failures.

## Result Shape

```json
{
  "success": true,
  "content": "safe model-visible result",
  "error": null,
  "retryable": false
}
```

For failure, `success` is false, `error` is non-empty, and `retryable` is explicit. Exceptions MUST NOT escape directly into the ReAct loop or public API.

## Sandbox Contract

```java
public interface Sandbox {
    void enforce(SandboxAction action) throws SandboxDeniedException;
}
```

`WhitelistSandbox` is a policy check, not strong OS isolation:

- File paths are resolved and normalized under allowed workspace roots, including symlink-aware containment checks.
- HTTP URLs are parsed and validated by normalized host.
- Shell requests validate the actual executable and arguments; unsupported compound shell syntax is rejected.
- A rejection is terminal for that invocation, is appended to the Session as a safe Tool failure, and is audited.

## Built-in Input Contracts

| Tool | Required input | Optional input |
|---|---|---|
| `read_file` | `path` | `maxChars` |
| `write_file` | `path`, `content` | `createParents` |
| `list_dir` | `path` | `recursive`, `maxEntries` |
| `shell` | `command`, `args[]` | `workingDirectory`, `timeoutSeconds` |
| `http_get` | `url` | `headers`, `timeoutSeconds` |
| `http_post` | `url` | `headers`, `body`, `contentType`, `timeoutSeconds` |
| `save_memory` | `content` | `scope` (`CORE` or `ARCHIVAL`, default `ARCHIVAL`) |
| `recall_memory` | none | `keyword`, `maxChars` |
| `notify` | `message` | `target`, `title` |

Unknown fields and invalid types are rejected. Secret-bearing headers/arguments are redacted before logging and persistence.
