# Research: OryxOS Web Info Hello

## Decision 1: Reuse the existing product-information resource

- **Decision**: Add the greeting to the existing product-information response.
- **Rationale**: The repository already defines product information as a core REST contract, and the current controller already serves product name and stage. This is the smallest complete Hello World slice.
- **Alternatives considered**: A new `/hello` endpoint was rejected because it would expand the documented core API without adding product value. A website-only greeting was rejected because it would not exercise `oryxos-web`.

## Decision 2: Keep the response deterministic and dependency-free

- **Decision**: Return a static greeting alongside the existing static product fields.
- **Rationale**: The result remains testable offline and cannot leak runtime configuration.
- **Alternatives considered**: Reading environment variables or build metadata was rejected as unnecessary for this exercise.

## Decision 3: Verify through the Spring MVC boundary

- **Decision**: Add a focused MockMvc test for the existing endpoint.
- **Rationale**: A Web boundary test validates routing, HTTP success, JSON serialization, and all required values in one test.
- **Alternatives considered**: A direct controller unit test would not verify routing or serialization. A full random-port test would add avoidable startup overhead.

## Resolution

There are no unresolved technical clarifications.
