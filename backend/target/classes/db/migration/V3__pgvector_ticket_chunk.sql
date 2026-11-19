CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE ticket_chunk (
    id                  UUID PRIMARY KEY,
    ticket_id           VARCHAR(32) NOT NULL,
    chunk_index         INT NOT NULL,
    content             TEXT NOT NULL,
    status              VARCHAR(32) NOT NULL,
    priority            VARCHAR(16) NOT NULL,
    assignee            VARCHAR(255),
    category            VARCHAR(128),
    content_fingerprint VARCHAR(128) NOT NULL,
    ingested_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    embedding           vector(768) NOT NULL,
    UNIQUE (ticket_id, chunk_index)
);

CREATE INDEX idx_ticket_chunk_ticket_id ON ticket_chunk (ticket_id);
CREATE INDEX idx_ticket_chunk_embedding ON ticket_chunk USING hnsw (embedding vector_cosine_ops);