-- QueueCut Database Schema Migration V2
-- Seeds initial barber account for Muhammad Arslan
-- Note: Password hash is a placeholder that will be initialized/verified by DataInitializer on startup

INSERT INTO barber_account (id, username, password_hash, full_name, contact_number, role)
VALUES (
    gen_random_uuid(),
    'arslan',
    '$2a$12$PLACEHOLDER_REPLACED_BY_STARTUP',
    'Muhammad Arslan',
    '03458717687',
    'ROLE_BARBER'
)
ON CONFLICT (username) DO NOTHING;
