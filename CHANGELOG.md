# Change Log

All notable changes to this project will be documented in this file.
This change log follows the conventions of [keepachangelog.com](https://keepachangelog.com/).

## [Unreleased]

### Changed
- Route `:parameters` now reference named schemas in `pearslcore-test.schema`
  (`list-projects-params-schema`, `create-project-request-schema`) instead of inlining
  the map shape in `handler.clj`. Single source of truth for OpenAPI, validation, and tests.
- Body coercion now goes through a custom `malli-coercion` that:
  - overrides reitit's default `:compile` step (which rewrites schemas and was masking
    the body schema's `:closed true` setting), and
  - swaps the body transformer for a no-op so extra keys survive to the validate step
    and are caught by the closed-map check.
- Coercion failures are now mapped to our error envelope by a custom
  `coerce-exceptions` middleware: body coercion → `422 validation_error`,
  query/path coercion → `400 bad_request`. The middleware reports *every* failing
  field via a Malli humanized-error walker, not just the first one.
- `repository/create-project!` no longer performs an upfront `get-project-by-name`
  lookup. The unique index on `lower(trim(name))` is the single source of truth for
  duplicate detection; a `UNIQUE constraint failed` `SQLException` is translated into
  the `:duplicate_name` ex-info. Removes the previous TOCTOU window.
- `list-projects` handler passes the coerced string status straight through; the
  detour through `keyword` is gone.

### Removed
- `handler.projects/validate-create-request` — body validation, closedness, forbidden
  fields, and unknown fields are all handled by the Malli schema via reitit coercion.
- `schema/validate-project-name` — duplicated rules already encoded in
  `project-name-schema`; the schema is now the single rule definition.
- `schema/pagination-params`, `sorting-params`, `status-filter-params` — flattened
  into `list-projects-params-schema` (avoids depending on `:merge` registry support).
- `repository/get-project-by-name` and its `declare` — no longer needed once the
  upfront duplicate check was removed.
- `test/pearslcore_test/core_test.clj` — trivial `(is (= 1 1))` stub.

### Fixed
- **Latent migration bug**: `migrations/001-create_projects_table.up.sql` contained
  three `;`-separated DDL statements (table + two indexes), but Migratus passes the
  file as a single statement and the SQLite JDBC driver only executes the first one.
  As a result, **`idx_projects_name_unique` and `idx_projects_status_created_at`
  were never being created**. The previous upfront `get-project-by-name` lookup
  was masking the missing unique index at the application layer. Fixed by adding
  Migratus `--;;` separators in both `up` and `down` migration files. Detected by
  duplicate-name tests failing after the upfront lookup was removed.

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
