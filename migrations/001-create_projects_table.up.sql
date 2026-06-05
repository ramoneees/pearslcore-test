-- Create projects table
CREATE TABLE IF NOT EXISTS projects (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'planned',
    created_at TEXT NOT NULL
);
--;;
-- Unique index on normalized name (lowercase + trimmed) enforces
-- case-insensitive uniqueness at the DB level (single source of truth
-- for duplicate detection — application code relies on this constraint).
CREATE UNIQUE INDEX IF NOT EXISTS idx_projects_name_unique
ON projects(lower(trim(name)));
--;;
-- Composite index for listing/filtering by status, ordered by created_at desc.
CREATE INDEX IF NOT EXISTS idx_projects_status_created_at
ON projects(status, created_at DESC);
