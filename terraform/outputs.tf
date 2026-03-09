/**
 * Output values from the Snowflake projects module.
 */

output "database_name" {
  description = "Name of the created database"
  value       = snowflake_database.this.name
}

output "schema_name" {
  description = "Name of the created schema"
  value       = snowflake_schema.this.name
}

output "table_name" {
  description = "Name of the projects table"
  value       = snowflake_table.projects.name
}

output "fully_qualified_table_name" {
  description = "Fully qualified table name for use in queries"
  value       = "${snowflake_database.this.name}.${snowflake_schema.this.name}.${snowflake_table.projects.name}"
}