-- QueueCut Database Schema Migration V1
-- Creates all tables, foreign keys, constraints, and indexes

-- 1. barber_account
CREATE TABLE barber_account (
    id               UUID        NOT NULL DEFAULT gen_random_uuid(),
    username         VARCHAR(50) NOT NULL,
    password_hash    VARCHAR(255) NOT NULL,
    full_name        VARCHAR(100) NOT NULL,
    contact_number   VARCHAR(20),
    role             VARCHAR(20) NOT NULL DEFAULT 'ROLE_BARBER',
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_barber_account    PRIMARY KEY (id),
    CONSTRAINT uq_barber_username   UNIQUE (username),
    CONSTRAINT chk_barber_role      CHECK (role IN ('ROLE_BARBER'))
);

-- 2. queue_session
CREATE TABLE queue_session (
    id                UUID        NOT NULL DEFAULT gen_random_uuid(),
    barber_id         UUID        NOT NULL,
    session_date      DATE        NOT NULL,
    status            VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    last_queue_number INTEGER     NOT NULL DEFAULT 0,
    opened_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    closed_at         TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_queue_session         PRIMARY KEY (id),
    CONSTRAINT fk_session_barber        FOREIGN KEY (barber_id)
                                            REFERENCES barber_account(id)
                                            ON DELETE RESTRICT,
    CONSTRAINT uq_session_date_barber   UNIQUE (barber_id, session_date),
    CONSTRAINT chk_session_status       CHECK (status IN ('OPEN', 'CLOSED')),
    CONSTRAINT chk_last_queue_number    CHECK (last_queue_number >= 0)
);

-- 3. queue_entry
CREATE TABLE queue_entry (
    id              UUID        NOT NULL DEFAULT gen_random_uuid(),
    session_id      UUID        NOT NULL,
    queue_number    INTEGER     NOT NULL,
    student_name    VARCHAR(100) NOT NULL,
    student_id      VARCHAR(50) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'WAITING',
    student_token   UUID        NOT NULL DEFAULT gen_random_uuid(),
    joined_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    called_at       TIMESTAMPTZ,
    completed_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_queue_entry           PRIMARY KEY (id),
    CONSTRAINT fk_entry_session         FOREIGN KEY (session_id)
                                            REFERENCES queue_session(id)
                                            ON DELETE RESTRICT,
    CONSTRAINT uq_entry_number_per_session
                                        UNIQUE (session_id, queue_number),
    CONSTRAINT chk_entry_status         CHECK (status IN (
                                            'WAITING',
                                            'ALMOST_READY',
                                            'CURRENT',
                                            'COMPLETED',
                                            'SKIPPED',
                                            'CANCELLED'
                                        )),
    CONSTRAINT chk_queue_number_positive CHECK (queue_number > 0)
);

-- 4. settings
CREATE TABLE settings (
    id               SMALLINT    NOT NULL DEFAULT 1,
    avg_haircut_min  INTEGER     NOT NULL DEFAULT 20,
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_settings          PRIMARY KEY (id),
    CONSTRAINT chk_settings_one_row CHECK (id = 1),
    CONSTRAINT chk_avg_haircut_min  CHECK (avg_haircut_min BETWEEN 5 AND 120)
);

-- Indexes for performance and constraints

-- queue_session lookups
CREATE INDEX idx_session_barber_date
    ON queue_session (barber_id, session_date DESC);

CREATE INDEX idx_session_status
    ON queue_session (status)
    WHERE status = 'OPEN';

-- queue_entry lookups
CREATE INDEX idx_entry_session_status
    ON queue_entry (session_id, status);

CREATE INDEX idx_entry_session_number
    ON queue_entry (session_id, queue_number ASC);

CREATE INDEX idx_entry_student_token
    ON queue_entry (student_token);

CREATE INDEX idx_entry_session_joined
    ON queue_entry (session_id, joined_at ASC);

-- Partial Unique Index: Prevent duplicate active entries per student per session
CREATE UNIQUE INDEX uq_entry_student_active_per_session
    ON queue_entry (session_id, student_id)
    WHERE status IN ('WAITING', 'ALMOST_READY', 'CURRENT');

-- Partial Unique Index: Only one CURRENT entry allowed per session
CREATE UNIQUE INDEX uq_entry_one_current_per_session
    ON queue_entry (session_id)
    WHERE status = 'CURRENT';
