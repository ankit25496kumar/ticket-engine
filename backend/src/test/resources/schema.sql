CREATE TABLE IF NOT EXISTS ticket_id_sequence (
    next_value BIGINT NOT NULL DEFAULT 1001
);
INSERT INTO ticket_id_sequence (next_value) SELECT 1001 WHERE NOT EXISTS (SELECT 1 FROM ticket_id_sequence);