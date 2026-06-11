-- Reset user-owned data for Mundia.
-- Keeps sports catalog data such as teams, rounds, matches and sync history.
-- Run against the production database only after making a backup.

START TRANSACTION;

DELETE FROM audit_logs;
DELETE FROM champion_predictions;
DELETE FROM score_breakdowns;
DELETE FROM prize_projections;
DELETE FROM match_predictions;
DELETE FROM bracket_predictions;
DELETE FROM prediction_sets;
DELETE FROM payments;
DELETE FROM pool_invitations;
DELETE FROM prize_rules;
DELETE FROM sim_state;
DELETE FROM pool_members;
DELETE FROM pools;
DELETE FROM users;

ALTER TABLE audit_logs AUTO_INCREMENT = 1;
ALTER TABLE champion_predictions AUTO_INCREMENT = 1;
ALTER TABLE score_breakdowns AUTO_INCREMENT = 1;
ALTER TABLE prize_projections AUTO_INCREMENT = 1;
ALTER TABLE match_predictions AUTO_INCREMENT = 1;
ALTER TABLE bracket_predictions AUTO_INCREMENT = 1;
ALTER TABLE prediction_sets AUTO_INCREMENT = 1;
ALTER TABLE payments AUTO_INCREMENT = 1;
ALTER TABLE pool_invitations AUTO_INCREMENT = 1;
ALTER TABLE prize_rules AUTO_INCREMENT = 1;
ALTER TABLE sim_state AUTO_INCREMENT = 1;
ALTER TABLE pool_members AUTO_INCREMENT = 1;
ALTER TABLE pools AUTO_INCREMENT = 1;
ALTER TABLE users AUTO_INCREMENT = 1;

COMMIT;
