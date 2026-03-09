# Terraform Snowflake Projects Module

This Terraform module creates a Snowflake database, schema, and projects table matching the Projects API data model.

## Usage

```hcl
module "projects" {
  source = "./terraform"
  
  database_name    = "PROJECTS_DB"
  schema_name      = "PUBLIC"
  table_name       = "PROJECTS"
  owner_role_name  = "ACCOUNTADMIN"
}
```

## Requirements

| Name | Version |
|------|---------|
| terraform | >= 1.0.0 |
| snowflake provider | ~> 0.89.0 |

## Providers

| Name | Version |
|------|---------|
| snowflake | ~> 0.89.0 |

## Resources

| Name | Type |
|------|------|
| snowflake_database.this | resource |
| snowflake_schema.this | resource |
| snowflake_table.projects | resource |
| snowflake_table_constraint.projects_name_unique | resource |
| snowflake_view.projects_by_status | resource |
| snowflake_grant_privileges_to_account_role.database_ownership | resource |

## Inputs

| Name | Description | Type | Default | Required |
|------|-------------|------|---------|:--------:|
| database_name | Name of the Snowflake database | `string` | `"PROJECTS_DB"` | no |
| schema_name | Name of the Snowflake schema | `string` | `"PUBLIC"` | no |
| table_name | Name of the projects table | `string` | `"PROJECTS"` | no |
| owner_role_name | Name of the role that should own the database objects | `string` | `"ACCOUNTADMIN"` | no |
| data_retention_days | Number of days to retain data for Time Travel | `number` | `1` | no |

## Outputs

| Name | Description |
|------|-------------|
| database_name | Name of the created database |
| schema_name | Name of the created schema |
| table_name | Name of the projects table |
| fully_qualified_table_name | Fully qualified table name for use in queries |

## Schema

The projects table has the following schema:

| Column | Type | Nullable | Description |
|--------|------|----------|-------------|
| id | VARCHAR(36) | NO | UUID primary key |
| name | VARCHAR(120) | NO | Project name |
| status | VARCHAR(20) | NO | Project status (planned, active, completed, archived) |
| created_at | TIMESTAMP_NTZ | NO | Creation timestamp |

### Constraints

- Primary key on `id`
- Unique constraint on `name`

### Indexes

- Unique index on `name` for case-insensitive uniqueness checks

## Notes

- This module uses the Snowflake Terraform provider from Snowflake-Labs
- Data retention is set to 1 day by default (minimum for Snowflake Standard edition)
- The module creates a view `projects_by_status` for convenience queries