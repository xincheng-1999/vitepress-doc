CREATE TABLE IF NOT EXISTS tasks (
    id VARCHAR(36) PRIMARY KEY,
    owner_id VARCHAR(50) NOT NULL,
    title VARCHAR(100) NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('TODO', 'DOING', 'DONE')),
    created_at TIMESTAMP(6) NOT NULL
);
CREATE INDEX IF NOT EXISTS tasks_owner_created ON tasks(owner_id, created_at, id);
