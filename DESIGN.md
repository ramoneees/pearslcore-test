# Design Document: Projects API

## 1. Problem Definition

This document describes the design and implementation of a production-shaped Projects API in Clojure. The API provides CRUD operations for managing projects with the following requirements:

- **Endpoints**: GET /projects, POST /projects, GET /projects/{id}
- **Features**: Pagination, sorting, filtering, validation, versioning
- **Data Model**: Projects with id, name, status, and created_at fields
- **Quality**: Production-shaped with proper error handling, logging, and testing

### Final Acceptance Criteria

- ✅ Working Clojure implementation using Malli
- ✅ OpenAPI 3.1.1 spec matching final behavior
- ✅ SQL migrations in /migrations plus seed data
- ✅ Tests with meaningful coverage
- ✅ Terraform module for Snowflake database + schema + projects table
- ✅ DESIGN.md documenting decisions
- ✅ README.md with quickstart and curl examples

### Assumptions

1. Single-tenant API (no multi-tenancy requirements)
2. Local development uses SQLite (not production deployment)
3. API version 1 is the only supported version currently
4. No authentication/authorization required for this implementation
5. No rate limiting or caching layer required
6. Synchronous request/response model (no async operations)

### Out-of-Scope Items

- Authentication and authorization
- Rate limiting
- Caching layer
- Webhook notifications
- Bulk operations
- PATCH endpoints
- DELETE endpoints
- Real-time updates/WebSockets
- Multi-tenancy
- Audit logging (beyond basic request logging)

---

## 2. Data Model

### Entity: Project

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| id | UUID (string) | Primary key, server-generated | Unique identifier |
| name | String | Required, 3-120 chars, unique (case-insensitive) | Project name |
| status | Enum | Required, default: "planned" | One of: planned, active, completed, archived |
| created_at | Timestamp | Required, server-generated | ISO-8601 format (e.g., 2026-03-08T12:34:56Z) |

### Why SQLite Over DuckDB

**Decision**: Use SQLite for local development.

**Rationale**:
1. **Simplicity**: SQLite is a single-file database requiring no server process, making it ideal for local development and testing
2. **Concurrency Model**: DuckDB is optimized for OLAP workloads (analytics), while SQLite's OLTP model better suits CRUD operations
3. **Tooling**: Better ecosystem support for migrations (Migratus) and connection pooling (HikariCP) in Clojure
4. **Maturity**: SQLite has more mature JDBC drivers and better compatibility with existing Clojure libraries
5. **Use Case Fit**: This is a small mutable CRUD service with simple queries, not an analytical workload

**Tradeoffs**:
- SQLite has limited write concurrency (single writer), but this is acceptable for local development
- Production would use Snowflake or PostgreSQL with proper connection pooling

### Database Schema

```sql
CREATE TABLE projects (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'planned',
    created_at TEXT NOT NULL
);

-- Unique index on normalized name (case-insensitive)
CREATE UNIQUE INDEX idx_projects_name_unique
ON projects(lower(trim(name)));

-- Index for listing/filtering by status and created_at
CREATE INDEX idx_projects_status_created_at
ON projects(status, created_at DESC);
```

---

## 3. API Contract

### Versioning

**Decision**: Header-based versioning via `X-API-Version` header.

**Rationale**:
- Preserves the required route shape (`/projects`, `/projects/{id}`)
- Makes versioning explicit and testable
- Easy to add new versions without changing URL structure
- Default to version 1 if header is absent
- Return 400 for unsupported versions

**Example**:
```
GET /projects HTTP/1.1
X-API-Version: 1
```

### Endpoints

#### GET /projects

Returns a paginated list of projects.

**Query Parameters**:
- `limit` (optional, default: 20, min: 1, max: 100)
- `offset` (optional, default: 0, min: 0)
- `sort` (optional, default: "created_at", values: "created_at" | "name")
- `order` (optional, default: "desc", values: "asc" | "desc")
- `status` (optional, values: "planned" | "active" | "completed" | "archived")

**Response** (200):
```json
{
  "items": [...],
  "limit": 20,
  "offset": 0,
  "total": 5
}
```

#### POST /projects

Creates a new project.

**Request Body**:
```json
{
  "name": "My Project",
  "status": "active"  // optional
}
```

**Response** (201):
- Location header: `/projects/{id}`
- Body: Created project

**Response** (409): Duplicate name conflict
**Response** (422): Validation error

#### GET /projects/{id}

Returns a single project by ID.

**Response** (200): Project resource
**Response** (400): Invalid UUID format
**Response** (404): Project not found

### JSON Conventions

- **Request/Response**: snake_case keys at HTTP boundary
- **Internal**: kebab-case where helpful
- **Closed schemas**: Request bodies reject unknown fields

### Error Model

All errors follow a consistent JSON structure:

```json
{
  "error": {
    "code": "validation_error",
    "message": "Request validation failed",
    "details": [
      { "field": "name", "message": "must be between 3 and 120 characters" }
    ]
  }
}
```

**Status Codes**:
- `400 bad_request`: Malformed JSON, invalid UUID, invalid query params, unsupported API version
- `404 not_found`: Project does not exist
- `409 conflict`: Duplicate project name
- `422 validation_error`: Well-formed JSON that fails validation
- `500 internal_error`: Unexpected errors (no stack traces leaked)

---

## 4. Validation

### Name Validation Rules

1. **Required**: Name must be present
2. **Trimming**: Leading/trailing whitespace is trimmed before validation
3. **Length**: 3-120 characters after trimming
4. **Characters**: 
   - No control characters
   - Allow letters, numbers, spaces, and common punctuation: `. _ - ' / ( )`
5. **Uniqueness**: Case-insensitive after trimming (e.g., "Test" and "test" are duplicates)
6. **Internal whitespace**: Not collapsed (e.g., "My  Project" is valid)

### Status Validation

- Must be one of: `planned`, `active`, `completed`, `archived`
- Defaults to `planned` if not specified

### ID Validation

- Route `id` must be a valid UUID string (format: `xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx`)

---

## 5. Implementation Notes

### Library Choices and Tradeoffs

| Library | Purpose | Rationale |
|---------|---------|-----------|
| Ring | HTTP abstraction | Standard Clojure HTTP library |
| Reitit | Routing | Modern, data-driven, excellent Malli integration |
| Jetty | HTTP server | Production-ready, well-maintained |
| Muuntaja | Content negotiation | Seamless JSON serialization |
| Malli | Validation/coercion | Fast, data-driven, excellent Reitit integration |
| Integrant | State management | Simple, explicit component lifecycle |
| next.jdbc | Database access | Modern, performant JDBC wrapper |
| HikariCP | Connection pooling | Production-grade pooling |
| Migratus | Migrations | Simple SQL-based migrations |

### Project Layout

```
src/pearslcore_test/
  config.clj          configuration defaults and EDN file loading
  system.clj          Integrant component graph + start/stop/reset
  handler.clj         Reitit router, middleware chain, route definitions
  handler/projects.clj endpoint handlers and error-response helpers
  schema.clj          Malli schemas (request, response, domain validation)
  middleware.clj      wrap-version, wrap-error, wrap-logging, wrap-json-parse-error
  main.clj            -main entry point
  db/
    datasource.clj    HikariCP pool creation/teardown
    migrations.clj    Migratus migration runner
    repository.clj    SQL queries (list/create/get)

dev/user.clj          REPL helpers (integrant.repl go/halt/reset)
migrations/           Migratus SQL files (up + down)
resources/openapi.yaml  OpenAPI 3.1.1 spec, served at GET /openapi.yaml
test/pearslcore_test/
  handler/projects_test.clj  integration tests
terraform/            Snowflake module (main.tf, variables.tf, outputs.tf)
```

### Query Parameter Coercion

**Decision**: Handlers consume coerced parameters from `[:parameters :query]` and `[:parameters :path]`, not raw Ring params.

**Rationale**:
- Reitit's `coerce-request-middleware` (configured globally with `reitit.coercion.malli`) validates and coerces params before the handler runs. By the time a handler is invoked, `[:parameters :query :limit]` is already an integer and invalid values have already returned a 400.
- Reading from `query-params` (raw strings) and then re-parsing would duplicate logic already declared in the route `:parameters` schema and create two sources of truth.
- This means handlers can destructure coerced params directly and trust their types.

### Body Coercion and Closed-Schema Enforcement

**Decision**: Use a custom `malli-coercion` (in `handler.clj`) that overrides reitit's
default `:compile` step and substitutes a no-op transformer for body decoding.

**Rationale**:
- The request body schema `create-project-request-schema` is `:closed true`, so unknown
  or forbidden keys (`id`, `created_at`, anything else) must be rejected at the boundary.
- Reitit's default malli coercion re-compiles schemas (collapsing the `:closed` flag)
  and uses a JSON transformer whose map decoder can strip extras before validation
  runs. Either of those silently turns `:closed true` into a no-op.
- The override leaves each schema's `:closed` setting intact and ensures the decode
  step is a no-op for body parameters, so extras survive to the closed-map validation
  and trigger a coercion failure.

**Trade-off**: We are explicit about a behavior reitit normally hides; this is a small
amount of coercion code in exchange for not having to reimplement closed-map checks
inside each handler.

### Coercion Error Mapping

**Decision**: A custom `coerce-exceptions` middleware translates Reitit/Malli coercion
failures into our error envelope.

**Mapping**:
- Body failures (`:in` contains `:body-params`) → `422 validation_error`
- Query/path failures → `400 bad_request`
- Details are produced by walking Malli's `humanized` map, so *every* failing field is
  reported (not just the first one).

### ID Strategy

**Decision**: Server-generated UUID strings.

**Rationale**:
- UUIDs are globally unique, allowing future distributed systems
- String format (not binary) for JSON compatibility
- Server-generated to prevent client manipulation
- No auto-increment for better security and distribution

### Pagination Choice

**Decision**: Limit/offset pagination.

**Rationale**:
- Simple to implement and understand
- Works well for small to medium datasets
- Compatible with filtering and sorting
- Standard pattern for REST APIs

**Limitations**:
- Not ideal for large datasets with frequent changes (cursor-based would be better)
- "Total" count can be expensive for very large tables

### Stateful Component Management

**Architecture**: Integrant-based lifecycle.

**Components**:
1. **Config**: Application configuration
2. **Datasource**: HikariCP connection pool
3. **Migrations**: Migratus migration runner
4. **Handler**: Reitit ring handler
5. **Server**: Jetty HTTP server

**Lifecycle**:
```
start: config → datasource → migrations → handler → server
stop:  server → handler → migrations → datasource → config
```

### DB Pooling Rationale for SQLite

**Decision**: Use HikariCP with pool size 4 even though SQLite has limited concurrency.

**Rationale**:
1. **Consistency**: Same codebase works for both SQLite (dev) and Snowflake/PostgreSQL (prod)
2. **Connection Reuse**: Avoids overhead of creating new connections for each request
3. **Future-Proofing**: Pool size can be increased for production databases
4. **Tradeoff**: For SQLite, the pool is oversized, but the overhead is minimal

**Production Consideration**:
- SQLite: Pool size 2-4 is sufficient
- PostgreSQL/Snowflake: Pool size 10-20 depending on load

---

## 6. Terraform Module

### Structure

```
terraform/
├── main.tf      # Main resources (database, schema, table, view)
├── variables.tf # Input variables
├── outputs.tf   # Output values
└── README.md    # Module documentation
```

### Resources Created

1. `snowflake_database.this`: Database for projects
2. `snowflake_schema.this`: Schema for projects
3. `snowflake_table.projects`: Projects table
4. `snowflake_view.projects_by_status`: Convenience view

### Limitations

**Terraform Provider Syntax Changes**: The Snowflake Terraform provider (v0.89.0) has changed its syntax for primary keys and constraints. This module uses a simplified table definition that passes validation. In production, additional constraints (unique index on name, primary key) should be added via SQL scripts or Snowflake UI.

---

## 7. AI Usage

### How AI Output Was Verified

**Test suite**: All 14 integration tests run against a temporary SQLite database (per-test
isolation via `with-test-database`). Tests cover happy paths, edge cases, and all documented
error codes. Tests are the first line of verification — if a handler, schema, or middleware
change breaks contract, a test fails.

**Structured code review**: A systematic review pass identified and fixed the following
AI-generated issues before submission:

| Issue | Category | Fix |
|-------|----------|-----|
| `main/-main` returned immediately after starting Jetty (uberjar would exit) | Bug | Added `(deref (promise))` |
| `list-projects` read raw `query-params` instead of reitit coerced `[:parameters :query]` | Bug | Rewrote handler to use coerced params |
| `get-project` read raw `path-params` instead of `[:parameters :path]` | Bug | Updated to coerced path |
| Route `:responses` had only descriptions — Malli response validation never ran | Bug | Added `:body` schemas to all primary 2xx routes |
| `iso-8601-timestamp` regex rejected real `Instant/now` output (fractional seconds) | Bug | Updated regex to allow optional fractional seconds |
| `make-request` test helper overwrote entire `:headers` map | Bug | Fixed to `(update :headers merge headers)` |
| `validate-create-request` had confusing outer `errors` binding before `cond` | Readability | Moved seed inside `:else` branch |
| `create-migration-table!` created a table that conflicted with Migratus internals | Dead code | Removed |
| `get-connection`, `coerce-and-validate`, `body-coercer`, `query-coercer` never called | Dead code | Removed |
| Duplicate `-main` in `system.clj`; unused `core.clj` stub | Dead code | Removed |
| `wrap-logging` emitted a redundant pre-request log line | Readability | Removed |
| `terraform/README.md` listed resources not present in `main.tf` | Doc error | Corrected |

**Manual curl verification**: Each endpoint was exercised manually after implementation:
- `GET /projects` — pagination envelope, seed data present
- `POST /projects` — `201` + `Location` header
- Duplicate name — `409` with stable payload
- Invalid UUID — `400`
- `GET /projects?status=active` — filter + total semantics

### Second review pass

A scoring-rubric-driven review pass tightened the implementation further and
**uncovered a latent migration bug** that the first pass had not detected:

| Issue | Category | Fix |
|-------|----------|-----|
| Route `:parameters` inlined the query-map shape, duplicating `schema/list-projects-params-schema` | Quality | Routes now reference the named schemas — single source of truth |
| Closed body schema (`create-project-request-schema`) was effectively ignored — Reitit's default `:compile`/transformer was stripping extra keys before the closed-map check fired | Bug | Custom `malli-coercion` overrides `:compile` to identity and substitutes a no-op body transformer so extras survive to validation |
| Coercion failures returned the framework's default 400; unknown body fields needed to be 422 | Bug | Custom `coerce-exceptions` middleware: body → 422 `validation_error`, query/path → 400 `bad_request` |
| `validate-create-request` only reported the first unknown field | Bug | New `humanized->details` walker reports every failing field via Malli's humanized error map |
| `repository/create-project!` did a read-then-insert duplicate check — TOCTOU race window between the lookup and the insert | Bug | Dropped the upfront lookup; the unique index + `SQLException` catch is the single source of truth |
| Status-check predicate in `validate-create-request` accepted both keywords and strings — body keys are always strings after JSON decode, so the keyword half was dead | Dead code | `validate-create-request` removed entirely; closed Malli schema covers the check |
| `schema/validate-project-name` duplicated the rules already encoded in `project-name-schema` | Dead code | Removed — the Malli schema is the only rule definition |
| `schema/pagination-params`, `sorting-params`, `status-filter-params` were only used by `:merge` into the list-params schema | Dead code | Flattened into `list-projects-params-schema`; drops dependency on `:merge` registry support |
| `test/pearslcore_test/core_test.clj` was a trivial `(is (= 1 1))` stub | Dead code | Removed |
| **`migrations/001-create_projects_table.up.sql` contained three `;`-separated DDL statements, but Migratus passes the file as a single statement and the SQLite JDBC driver only executes the first one — the unique index and the status index were never being created. The upfront app-level duplicate check was masking the missing constraint.** | **Latent bug** | Added Migratus `--;;` separators in both `up` and `down` migrations. Detected by the duplicate-name test failing after the upfront lookup was removed |

**Verification after the second pass**: `lein test` → 13 tests, 43 assertions,
0 failures, 0 errors. The duplicate-name test now genuinely exercises the DB
constraint rather than being intercepted by an app-level lookup.

### AI Assistance Areas

- Initial project scaffolding (system lifecycle, middleware chain, handler structure)
- Malli schema definitions and reitit route declarations
- SQL query patterns and repository layer
- OpenAPI spec structure
- Terraform module boilerplate
- Documentation drafts (README, DESIGN.md, doc/intro.md)
---

## 8. Run & Test

### Prerequisites

- Java 11 or higher
- Leiningen 2.9.0 or higher
- SQLite (for local development)
- Terraform 1.0.0 or higher (for Snowflake module)

### Running the Application

```bash
# Install dependencies
lein deps

# Run migrations (creates .data/dev.db)
lein migratus migrate

# Start the server
lein run
```

Server will start on http://localhost:3000

### Running Tests

```bash
# Run all tests
lein test

# Run specific test
lein test pearslcore-test.handler.projects-test
```

### Terraform Commands

```bash
# Initialize Terraform
cd terraform
terraform init -backend=false

# Validate configuration
terraform validate

# Format configuration
terraform fmt
```

### Example API Calls

```bash
# List projects
curl http://localhost:3000/projects

# Create a project
curl -X POST http://localhost:3000/projects \
  -H "Content-Type: application/json" \
  -d '{"name": "My Project"}'

# Get a project by ID
curl http://localhost:3000/projects/{id}

# Filter by status
curl http://localhost:3000/projects?status=active

# Paginate results
curl http://localhost:3000/projects?limit=10&offset=20

# Sort by name ascending
curl http://localhost:3000/projects?sort=name&order=asc

# Use API version header
curl -H "X-API-Version: 1" http://localhost:3000/projects
```

---

## 9. Performance & Security Considerations

### Performance

1. **Database Indexes**:
   - Unique index on normalized name for fast uniqueness checks
   - Composite index on (status, created_at) for filtered listings

2. **Connection Pooling**:
   - HikariCP with configurable pool size
   - Connection reuse across requests

3. **Query Optimization**:
   - Use indexed columns in WHERE and ORDER BY
   - Deterministic sorting (tiebreaker on id)

### Security

1. **Input Validation**:
   - Malli schemas validate all inputs
   - Closed schemas prevent unknown fields
   - Parameterized queries prevent SQL injection

2. **Error Handling**:
   - No stack traces leaked to clients
   - Generic error messages for unexpected failures
   - Consistent error structure

3. **Data Protection**:
   - Server-generated IDs prevent manipulation
   - Server-generated timestamps ensure consistency
   - Normalized name comparison prevents duplicate detection bypass

---

## 10. Conclusion

This implementation provides a production-shaped Projects API that demonstrates:

- Clean architecture with separation of concerns
- Comprehensive validation and error handling
- Proper testing and documentation
- Infrastructure as code (Terraform)
- Clear design decisions with documented rationale

The codebase is ready for review and can be extended with additional features (authentication, rate limiting, etc.) as needed.