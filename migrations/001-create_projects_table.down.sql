-- Drop indexes
DROP INDEX IF EXISTS idx_projects_status_created_at;
DROP INDEX IF EXISTS idx_projects_name_unique;

-- Drop projects table
DROP TABLE IF EXISTS projects;