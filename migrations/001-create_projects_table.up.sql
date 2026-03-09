-- Create projects table
CREATE TABLE IF NOT EXISTS projects (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'planned',
    created_at TEXT NOT NULL
);

-- Create unique index on normalized name (lowercase + trimmed)
CREATE UNIQUE INDEX IF NOT EXISTS idx_projects_name_unique
ON projects(lower(trim(name)));

-- Create index for listing/filtering by status and created_at
CREATE INDEX IF NOT EXISTS idx_projects_status_created_at
ON projects(status, created_at DESC);