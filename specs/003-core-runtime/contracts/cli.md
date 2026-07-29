# CLI Contract

The production entrypoint is the single executable Boot JAR:

```powershell
java -jar oryxos-boot/target/oryxos.jar <command> [options]
```

Exit codes:

- `0`: success
- `2`: invalid command or arguments
- `3`: workspace/configuration error
- `4`: requested resource not found or archived
- `5`: runtime Provider/Tool/invocation failure

Human-readable output is the default. `--json` emits stable JSON where supported and sends diagnostics to stderr.

## Commands

| Command | Purpose | Core behavior |
|---|---|---|
| `init [path]` | Initialize a workspace | Creates the `.oryxos/` tree and starter files without overwriting existing content. |
| `status` | Show local/runtime status | Reports workspace validity, configured Providers, loaded/invalid Agents, database, and service state. |
| `chat --profile <name>` | Interactive conversation | Reuses/creates a CLI Session and routes every turn through `AgentService`. |
| `serve` | Start REST service | Starts Spring MVC and scheduler. |
| `gateway` | Start gateway mode | Starts configured inbound channels through the same runtime. |
| `profile list` | List derived Profiles | Lists valid Agent directories and validation state. |
| `profile create <name>` | Create Agent skeleton | Creates `<name>/AGENT.md`; does not create Profile YAML. |
| `profile show <name>` | Show derived Profile | Shows frontmatter-derived runtime configuration with secrets redacted. |
| `profile delete <name>` | Remove an Agent definition | Requires confirmation unless `--force`; never deletes Session/audit history. |
| `provider list` | List Provider configuration | Shows names/models/availability without secrets. |
| `tool list` | List registered Tools | Shows name, origin, and description. |
| `session list` | List Sessions | Supports Profile/status filters and bounded paging. |

`profile` remains the public command-group name for compatibility; it manages Agent directories and their derived Profiles.

## Shared Options

| Option | Meaning |
|---|---|
| `--workspace <path>` | Workspace containing `.oryxos`; defaults to current directory resolution. |
| `--json` | Machine-readable output where supported. |
| `--verbose` | Additional safe diagnostics; never prints credentials. |
| `--help` | Command help. |
| `--version` | Application version. |

## Behavioral Requirements

- `init`, help, version, and direct local metadata operations avoid Spring startup where practical.
- `chat`, `serve`, `gateway`, and Agent invocations use the same Boot-composed runtime.
- Invalid Agents are reported but do not block valid Agents.
- Archived Sessions are never silently reopened.
- `profile delete` is the only destructive CLI command in the core set and requires explicit confirmation unless forced.
- Scheduled tasks can always be manually replayed through `chat` or the Agent invocation API; no separate scheduler execution path exists.
