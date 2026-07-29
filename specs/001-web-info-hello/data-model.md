# Data Model: OryxOS Web Info Hello

## Product Information

A read-only response view that identifies the running product.

| Field | Type | Required | Validation |
|-------|------|----------|------------|
| `name` | string | yes | Exact value `OryxOS` |
| `stage` | string | yes | Exact value `project-initialization` for the current phase |
| `message` | string | yes | Exact value `Hello from OryxOS` |

## Relationships and State

The view has no relationships, persistence, identifier, lifecycle, or state transition. Every successful request returns the same three values.

## Security Boundary

Only the three documented fields are exposed. Environment variables, credentials, tokens, paths, and internal configuration are outside the model.
