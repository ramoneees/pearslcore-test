# Projects API (Clojure)

Production-shaped Projects API built with Ring/Reitit/Malli/Integrant, SQLite for local development, and Terraform for Snowflake schema provisioning.

## What this project does

- Exposes three endpoints:
  - `GET /projects`
  - `POST /projects`
  - `GET /projects/{id}`
- Supports pagination, sorting, and status filtering on listing.
- Validates all external input with clear, stable error payloads.
- Manages lifecycle (config, datasource, migrations, server) via Integrant.
- Includes migration scripts, integration tests, OpenAPI spec, and Terraform module.

## Stack

- HTTP: Ring + Reitit + Jetty + Muuntaja
- Validation/coercion: Malli (via Reitit coercion)
- State management: Integrant
- DB access: next.jdbc + HikariCP
- Migrations: Migratus
- Local DB: SQLite
- Infra: Terraform (Snowflake module)
- Quality: clj-kondo + cljfmt + clojure.test

## Project layout

- `src/pearslcore_test/config.clj` configuration defaults and loading
- `src/pearslcore_test/system.clj` Integrant graph + start/stop/reset lifecycle
- `src/pearslcore_test/handler.clj` router and middleware chain
- `src/pearslcore_test/handler/projects.clj` endpoint handlers + error mapping
- `src/pearslcore_test/schema.clj` Malli schemas + domain validation helpers
- `src/pearslcore_test/db/datasource.clj` datasource/pool setup
- `src/pearslcore_test/db/repository.clj` SQL/query logic
- `src/pearslcore_test/db/migrations.clj` migration runner
- `migrations/` SQL schema + seed migrations
- `resources/openapi.yaml` OpenAPI 3.1.1 spec
- `test/pearslcore_test/handler/projects_test.clj` integration tests
- `terraform/` Snowflake module
- `DESIGN.md` implementation decisions and tradeoffs
- `doc/intro.md` system walkthrough
- `doc/interview-guide.md` interview narrative and Q&A

## Run locally

Prereqs: Java + Leiningen.

```bash
lein deps
mkdir -p .data
lein migratus migrate
lein run
```

Server starts on `http://localhost:3000` by default.

## Quick API examples

```bash
# list projects
curl http://localhost:3000/projects

# list with pagination/filter/sort
curl "http://localhost:3000/projects?limit=10&offset=0&status=active&sort=created_at&order=desc"

# create project
curl -X POST http://localhost:3000/projects \
  -H "Content-Type: application/json" \
  -d '{"name":"Website Revamp","status":"active"}'

# fetch by id
curl http://localhost:3000/projects/550e8400-e29b-41d4-a716-446655440001

# version header (supported value: 1)
curl -H "X-API-Version: 1" http://localhost:3000/projects
```

## Error contract

All error responses use:

```json
{
  "error": {
    "code": "validation_error",
    "message": "Request validation failed",
    "details": [
      {"field": "name", "message": "must be between 3 and 120 characters"}
    ]
  }
}
```

Main status mapping:

- `400` malformed JSON, bad UUID format, invalid query params, unsupported API version
- `404` project not found
- `409` duplicate normalized name
- `422` semantic validation failures
- `500` unexpected errors (no stack trace leakage)

## OpenAPI

- Spec file: `resources/openapi.yaml`
- Served at: `GET /openapi.yaml`

## Tests and quality checks

```bash
lein test
clj-kondo --lint src test dev
lein cljfmt check
```

## Terraform (Snowflake)

```bash
terraform -chdir=terraform init -backend=false
terraform -chdir=terraform validate
terraform -chdir=terraform fmt
```

## Deeper docs

- Design/tradeoffs: `DESIGN.md`
- Architecture walkthrough: `doc/intro.md`

## License

Copyright © 2026.

This program and accompanying materials are available under EPL 2.0.
