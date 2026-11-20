# Contributing to ArchGuard

Thanks for helping. This page covers how to work on the project. For how the system works, see the [project documentation](docs/ARCHGUARD_PROJECT_DOCUMENTATION.md).

## Local setup

Follow [Local Development Setup](docs/ARCHGUARD_PROJECT_DOCUMENTATION.md#9-local-development-setup) for prerequisites, environment variables, and starting PostgreSQL and RabbitMQ. [Testing](docs/ARCHGUARD_PROJECT_DOCUMENTATION.md#10-testing) covers running tests, the coverage report, and formatting.

Before opening a pull request, make sure this passes:

```bash
./mvnw verify          # macOS/Linux
.\mvnw.cmd verify      # Windows PowerShell
```

`verify` runs the tests and fails on formatting problems or low coverage. Fix formatting with `./mvnw spotless:apply`.

## Branches

Branch from `main` and name the branch `<type>/<short-description>`, using the same types as commit messages:

```text
feat/cycle-detection
fix/worker-clone-cleanup
docs/sidecar-contract
```

## Commit messages

Commits follow [Conventional Commits](https://www.conventionalcommits.org/): `<type>(<scope>): <summary>`, with the summary in lowercase imperative mood.

```text
feat(api): add JobStatus enum and job status / violations endpoints
fix(worker): always clean up the clone, shallow-clone and validate repo URLs
fix(sidecar): report per-file parse errors instead of failing the job
```

| Type | Use for |
|---|---|
| `feat` | New behavior |
| `fix` | Bug fixes |
| `test` | Tests only |
| `refactor` | Code changes that keep behavior the same |
| `docs` | Documentation only |
| `build` | Maven and build configuration |
| `ci` | GitHub Actions workflows |
| `chore` | Maintenance that fits nowhere else |

The scope is the part of the pipeline you changed, matching the `area:` labels: `api`, `worker`, `sidecar`, `rules`, `github`, `graph`, `metrics`, `persistence`, `config`. Leave it out when a change spans several areas.

## Adding a rule

Rules are written test first:

1. Add a failing test in `RuleEngineTest` with one file that should be flagged and one that should not.
2. Make the smallest rule change that passes it.
3. Add or adjust parser fixtures if the rule needs new import shapes.
4. Run `./mvnw verify`.

If you're proposing a rule rather than implementing it, open a **New rule** issue. The form asks for the same violating and compliant examples, so they can become the first tests. See [Current Rule Engine](docs/ARCHGUARD_PROJECT_DOCUMENTATION.md#7-current-rule-engine) for how matching works today.

## File-level versus job-level errors

When something goes wrong during analysis, ask whether the problem is limited to one file:

- **Limited to one file** (a syntax error, Python 2 code, bad encoding): skip the file, count it in `filesSkipped`, and keep going. One bad file must not fail an entire repository.
- **Stops analysis for every file** (clone failure, missing Python, sidecar crash, timeout): fail the job with a clear `errorMessage`.

The full list is in [File-level versus job-level errors](docs/ARCHGUARD_PROJECT_DOCUMENTATION.md#44-file-level-versus-job-level-errors).

## Pull requests

Opening a pull request fills in a template with a summary, how you tested the change, and the contributor checklist. Link the issue it closes, and keep each pull request to one change.
