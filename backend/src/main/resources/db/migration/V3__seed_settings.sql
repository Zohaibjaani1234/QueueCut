-- QueueCut Database Schema Migration V3
-- Seeds default global configuration settings

INSERT INTO settings (id, avg_haircut_min)
VALUES (1, 20)
ON CONFLICT (id) DO NOTHING;
