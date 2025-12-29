# Agent Guide – Coroutine Visualizer Backend

## Scope
- Applies to `backend` Kotlin/Ktor service in `visualizer-for-coroutines`.
- Default to ASCII; keep edits minimal and focused on requested changes.

## Working Style
- Prefer `rg` for searches; avoid destructive git commands.
- Add short, high-value comments only when behavior is non-obvious.
- Update related docs/http samples when adding or changing endpoints.

## Testing
- Run the smallest meaningful Gradle task (`./gradlew test` or targeted module tests) when feasible; if skipped, state why and what to run.
- Note any environment/setup requirements for running tests.

## Commit & PR Prep
- Use Conventional Commit style (`feat: ...`, `fix: ...`, `docs: ...`, `chore: ...`).
- Draft PR description with:
  - `Summary` (what changed and why)
  - `Testing` (commands run + results)
  - `Risks`/`Follow-ups` if applicable
- Do not push to GitHub from the agent; provide the commit message and PR text for a human to use.

## Endpoints & Scenarios
- When adding tests/scenarios, wire them through `routes/TestRoutes.kt` or `routes/VizScenarioRoutes.kt` and keep naming consistent with `VizEventMain` helpers.
- Ensure new events integrate with the existing session/bus model and emit lifecycle events via `EventContext`.

## Dependencies
- Avoid adding new dependencies unless necessary; justify additions and update Gradle files and docs if you must add them.
