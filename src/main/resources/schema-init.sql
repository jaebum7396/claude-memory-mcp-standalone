-- pgvector HNSW indexes (cosine similarity)
CREATE INDEX IF NOT EXISTS idx_memories_embedding ON memories USING hnsw (embedding vector_cosine_ops);
CREATE INDEX IF NOT EXISTS idx_notes_embedding ON notes USING hnsw (embedding vector_cosine_ops);

-- host column indexes
CREATE INDEX IF NOT EXISTS idx_memories_host ON memories (host);
CREATE INDEX IF NOT EXISTS idx_notes_host ON notes (host);
