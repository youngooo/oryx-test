# Quickstart: Validate OryxOS Workspace Initialization

## Prerequisites

- JDK 21
- Maven 3.9+
- A checkout of the OryxOS repository

No model provider, API key, network service, or Spring runtime is required.

## 1. Run focused tests

From the repository root:

```bash
mvn -pl oryxos-cli -am test
```

Expected result: the CLI module tests pass for clean initialization,
idempotency, partial repair, path conflicts, output, and exit codes.

## 2. Package the executable CLI

```bash
mvn -pl oryxos-cli -am package
```

The executable artifact is:

```text
oryxos-cli/target/oryxos-cli-0.1.0-SNAPSHOT-executable.jar
```

## 3. Validate a clean workspace

Create an empty temporary directory, change into it, and run:

```bash
java -jar <repository>/oryxos-cli/target/oryxos-cli-0.1.0-SNAPSHOT-executable.jar init
```

Confirm that the command returns exit code `0`, prints the absolute workspace
path and a next step, and creates the tree in
[`data-model.md`](data-model.md).

Inspect `.oryxos/agents/default/AGENT.md` and confirm it has YAML frontmatter,
Markdown instructions, and no real credential.

## 4. Validate safe repetition

Replace the content of `.oryxos/AGENTS.md` with a unique marker, record its
checksum, and run the same `init` command again.

Expected result:

- exit code `0`;
- output states that existing content was preserved;
- the checksum is unchanged;
- no duplicate or backup file appears.

## 5. Validate partial repair

Remove the empty `.oryxos/logs/` directory and run `init` again.

Expected result: `logs/` is recreated while every existing file remains
byte-for-byte unchanged.

## 6. Validate a path conflict

In a new temporary project directory, create `.oryxos/agents` as a regular file
and run `init`.

Expected result:

- non-zero exit code;
- the error names `.oryxos/agents`;
- the conflicting file is unchanged;
- no success message is printed.

See [`contracts/init-command.md`](contracts/init-command.md) for the complete CLI
contract.

## 7. Run the project quality gate

```bash
mvn clean package
```

Expected result: all nine modules build and all tests pass.
