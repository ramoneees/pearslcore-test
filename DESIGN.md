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

1. **Tests Run**: All integration tests pass with temporary SQLite databases
2. **Manual Checks**:
   - Tested each endpoint with curl commands
   - Verified error responses match specification
   - Checked pagination, sorting, and filtering work correctly
   - Validated name uniqueness enforcement
3. **Code Review**: Reviewed all generated code for:
   - Idiomatic Clojure patterns
   - Proper error handling
   - SQL injection prevention
   - Resource cleanup

### AI Assistance Areas

- Project structure and organization
- Error response formatting
- Documentation structure
- Terraform template 
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