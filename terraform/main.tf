/**
 * Terraform module to create a Snowflake database, schema, and projects table.
 * 
 * NOTE: This module is designed for Snowflake cloud data warehouse.
 * The Snowflake Terraform provider syntax may change between versions.
 * This configuration targets version ~> 0.89.0 of the provider.
 * 
 * LIMITATION: The provider syntax for primary_key and constraints
 * has changed in recent versions. This module uses simplified table
 * definition that passes validation. In production, additional
 * constraints (unique, indexes) should be added via SQL scripts
 * or the Snowflake UI.
 */

terraform {
  required_version = ">= 1.0.0"
  required_providers {
    snowflake = {
      source  = "Snowflake-Labs/snowflake"
      version = "~> 0.89.0"
    }
  }
}

resource "snowflake_database" "this" {
  name = var.database_name
}

resource "snowflake_schema" "this" {
  name     = var.schema_name
  database = snowflake_database.this.name
}

resource "snowflake_table" "projects" {
  database = snowflake_database.this.name
  schema   = snowflake_schema.this.name
  name     = var.table_name

  column {
    name     = "id"
    type     = "VARCHAR(36)"
    nullable = false
  }

  column {
    name     = "name"
    type     = "VARCHAR(120)"
    nullable = false
  }

  column {
    name     = "status"
    type     = "VARCHAR(20)"
    nullable = false
  }

  column {
    name     = "created_at"
    type     = "TIMESTAMP_NTZ"
    nullable = false
  }
}

resource "snowflake_view" "projects_by_status" {
  database = snowflake_database.this.name
  schema   = snowflake_schema.this.name
  name     = "projects_by_status"

  statement = <<-SQL
    SELECT id, name, status, created_at
    FROM projects
    ORDER BY created_at DESC
  SQL
}