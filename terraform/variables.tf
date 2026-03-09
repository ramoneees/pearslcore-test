/**
 * Input variables for the Snowflake projects module.
 */

variable "database_name" {
  description = "Name of the Snowflake database"
  type        = string
  default     = "PROJECTS_DB"
}

variable "schema_name" {
  description = "Name of the Snowflake schema"
  type        = string
  default     = "PUBLIC"
}

variable "table_name" {
  description = "Name of the projects table"
  type        = string
  default     = "PROJECTS"
}

variable "owner_role_name" {
  description = "Name of the role that should own the database objects"
  type        = string
  default     = "ACCOUNTADMIN"
}

variable "data_retention_days" {
  description = "Number of days to retain data for Time Travel"
  type        = number
  default     = 1
}