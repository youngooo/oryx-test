PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS schema_version (
    version INTEGER PRIMARY KEY,
    applied_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT OR IGNORE INTO schema_version(version) VALUES (1);
INSERT OR IGNORE INTO schema_version(version) VALUES (2);

CREATE TABLE IF NOT EXISTS sessions (
    session_id TEXT PRIMARY KEY,
    profile_name TEXT NOT NULL,
    channel TEXT NOT NULL,
    user_id TEXT NOT NULL,
    messages_json TEXT NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    created_at TEXT NOT NULL,
    last_active_at TEXT NOT NULL,
    archived_at TEXT,
    version INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_sessions_active_identity
    ON sessions(profile_name, channel, user_id, status, last_active_at);

CREATE TABLE IF NOT EXISTS llm_calls (
    id TEXT PRIMARY KEY,
    session_id TEXT NOT NULL,
    provider TEXT NOT NULL,
    model TEXT NOT NULL,
    iteration INTEGER NOT NULL CHECK (iteration >= 1),
    prompt_tokens INTEGER,
    completion_tokens INTEGER,
    success INTEGER NOT NULL,
    finish_reason TEXT,
    error_code TEXT,
    error_message TEXT,
    started_at TEXT NOT NULL,
    completed_at TEXT NOT NULL,
    duration_ms INTEGER NOT NULL CHECK (duration_ms >= 0),
    FOREIGN KEY (session_id) REFERENCES sessions(session_id)
);

CREATE INDEX IF NOT EXISTS idx_llm_calls_session_order
    ON llm_calls(session_id, started_at, id);
CREATE INDEX IF NOT EXISTS idx_llm_calls_provider_order
    ON llm_calls(provider, started_at);

CREATE TABLE IF NOT EXISTS tool_invocations (
    id TEXT PRIMARY KEY,
    session_id TEXT NOT NULL,
    tool_name TEXT NOT NULL,
    arguments_json TEXT NOT NULL,
    success INTEGER NOT NULL,
    result_summary TEXT,
    error_code TEXT,
    error_message TEXT,
    retryable INTEGER NOT NULL,
    started_at TEXT NOT NULL,
    completed_at TEXT NOT NULL,
    duration_ms INTEGER NOT NULL CHECK (duration_ms >= 0),
    FOREIGN KEY (session_id) REFERENCES sessions(session_id)
);

CREATE INDEX IF NOT EXISTS idx_tool_invocations_session_order
    ON tool_invocations(session_id, started_at, id);
CREATE INDEX IF NOT EXISTS idx_tool_invocations_tool_order
    ON tool_invocations(tool_name, started_at);

CREATE TABLE IF NOT EXISTS memory_entries (
    id TEXT PRIMARY KEY,
    scope TEXT NOT NULL CHECK (scope IN ('CORE', 'ARCHIVAL')),
    content TEXT NOT NULL CHECK (length(trim(content)) > 0),
    created_at TEXT NOT NULL,
    UNIQUE(scope, content)
);

CREATE INDEX IF NOT EXISTS idx_memory_entries_scope_order
    ON memory_entries(scope, created_at, id);
