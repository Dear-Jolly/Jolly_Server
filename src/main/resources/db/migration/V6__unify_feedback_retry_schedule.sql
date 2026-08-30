DROP INDEX idx_letters_pending ON letters;

ALTER TABLE letters DROP COLUMN recovery_count;
ALTER TABLE letters ADD COLUMN next_retry_at DATETIME(6) NULL;

CREATE INDEX idx_letters_retry ON letters (status, next_retry_at);
CREATE INDEX idx_letters_stalled ON letters (status, updated_at);

UPDATE letters
SET next_retry_at = CURRENT_TIMESTAMP(6)
WHERE status = 'SUBMITTED';
