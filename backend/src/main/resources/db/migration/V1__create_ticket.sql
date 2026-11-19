CREATE TABLE ticket_id_sequence (
    next_value BIGINT NOT NULL DEFAULT 1001
);

INSERT INTO ticket_id_sequence (next_value) VALUES (1001);

CREATE TABLE ticket (
    id              BIGSERIAL PRIMARY KEY,
    ticket_id       VARCHAR(32) NOT NULL UNIQUE,
    title           VARCHAR(255) NOT NULL,
    description     TEXT,
    priority        VARCHAR(16) NOT NULL CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH')),
    status          VARCHAR(32) NOT NULL CHECK (status IN ('OPEN', 'IN_PROGRESS', 'RESOLVED', 'CLOSED', 'CANCELLED')),
    assignee        VARCHAR(255),
    category        VARCHAR(128),
    resolution_notes TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ticket_status ON ticket (status);
CREATE INDEX idx_ticket_priority ON ticket (priority);
CREATE INDEX idx_ticket_assignee ON ticket (assignee);
CREATE INDEX idx_ticket_category ON ticket (category);
CREATE INDEX idx_ticket_created_at ON ticket (created_at DESC);