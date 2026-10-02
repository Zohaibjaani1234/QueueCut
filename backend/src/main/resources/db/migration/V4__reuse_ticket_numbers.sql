-- QueueCut Database Schema Migration V4
-- Ticket numbers now restart from the last student still in the queue (#1 when the queue is empty),
-- so a number can repeat within a day once earlier holders are finished.
-- Uniqueness is only required among students currently in the queue.

ALTER TABLE queue_entry DROP CONSTRAINT uq_entry_number_per_session;

CREATE UNIQUE INDEX uq_entry_active_number_per_session
    ON queue_entry (session_id, queue_number)
    WHERE status IN ('WAITING', 'ALMOST_READY', 'CURRENT');
