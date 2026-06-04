# Change Log

All notable changes to this project will be documented in this file.
This change log follows the conventions of [keepachangelog.com](https://keepachangelog.com/).

## [Unreleased]

### Changed
- `list-projects` handler now reads reitit coerced query params from `[:parameters :query]`
  instead of re-parsing raw `query-params` with manual integer conversion.
- `get-project` handler reads path param from `[:parameters :path :id]` to match reitit convention.
- `validate-create-request` — moved accumulator seed inside `:else` branch; consistent `let` indentation.
- `wrap-logging` no longer emits a redundant pre-request log line; only logs on response.
- `row->project` uses `select-keys` instead of manual field-by-field reconstruction.
- `now-iso8601` inlined `format-timestamp` wrapper into a single call.

### Removed
- `core.clj` — unused Leiningen project stub.
- `datasource/get-connection` — unused function; callers pass the datasource directly to next.jdbc.
- `migrations/create-migration-table!` — never called; conflicted with Migratus's own tracking table.
- `system/-main` — dead duplicate of the entry point in `main.clj`.
- `schema/body-coercer`, `schema/query-coercer`, `schema/coerce-and-validate` — defined but never used.
- Unused `malli.transform` and `malli.error` requires from `schema.clj`.
- Unused `next.jdbc` require from `migrations.clj` and `datasource.clj`.

### Fixed
- `main/-main` now calls `(deref (promise))` so the process does not exit immediately after
  starting Jetty when run as an uberjar.
- `make-request` test helper now merges headers with `(update :headers merge headers)` instead
  of overwriting the entire `:headers` key, which previously dropped `Content-Type` when
  both a body and custom headers were supplied.

## [0.1.0] - 2026-06-04

### Added
- `GET /projects` — paginated, sortable, filterable project list.
- `POST /projects` — create project with name validation and conflict detection.
- `GET /projects/{id}` — fetch single project by UUID.
- Malli schemas for domain validation (`schema.clj`).
- Integrant system lifecycle: config → datasource → migrations → handler → server.
- HikariCP connection pool via next.jdbc for SQLite (local dev).
- Migratus SQL migrations (`migrations/`).
- OpenAPI 3.1.1 spec (`resources/openapi.yaml`), served at `GET /openapi.yaml`.
- Integration test suite using per-test temporary SQLite databases.
- Terraform module for Snowflake database/schema/table provisioning.
- `DESIGN.md` with architecture decisions and tradeoffs.
