CREATE TABLE comment (
    id         UUID PRIMARY KEY,
    ticket_id  VARCHAR(32) NOT NULL REFERENCES ticket (ticket_id) ON DELETE CASCADE,
    author     VARCHAR(255),
    content    TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_comment_ticket_id ON comment (ticket_id);