-- Canonical knockout model: one row per knockout match, forever.
-- Placeholders (Pendiente vs Pendiente, result_source='NONE') are the canonical rows.
-- Sim/sync UPDATE these rows instead of inserting parallel ones, so match ids
-- (and therefore user predictions) survive the whole tournament.

-- 1. Ensure canonical rounds exist with coherent sort order
INSERT INTO rounds (name, stage, sort_order, external_name) VALUES
  ('Round of 32',    'KNOCKOUT', 10, 'Round of 32'),
  ('Round of 16',    'KNOCKOUT', 11, 'Round of 16'),
  ('Quarter-finals', 'KNOCKOUT', 12, 'Quarter-finals'),
  ('Semi-finals',    'KNOCKOUT', 13, 'Semi-finals'),
  ('Third place',    'KNOCKOUT', 14, 'Third place'),
  ('Final',          'KNOCKOUT', 15, 'Final')
ON DUPLICATE KEY UPDATE
  stage = VALUES(stage),
  sort_order = VALUES(sort_order),
  external_name = VALUES(external_name);

-- 2. Move matches from legacy round names to the canonical rounds, then drop the legacy rounds
UPDATE matches m
JOIN rounds legacy ON legacy.id = m.round_id AND legacy.name = 'Cuartos'
JOIN rounds canon  ON canon.name = 'Quarter-finals'
SET m.round_id = canon.id;

UPDATE matches m
JOIN rounds legacy ON legacy.id = m.round_id AND legacy.name = 'Semis'
JOIN rounds canon  ON canon.name = 'Semi-finals'
SET m.round_id = canon.id;

UPDATE matches m
JOIN rounds legacy ON legacy.id = m.round_id AND legacy.name = '3rd Place Final'
JOIN rounds canon  ON canon.name = 'Third place'
SET m.round_id = canon.id;

DELETE FROM rounds WHERE name IN ('Cuartos', 'Semis', '3rd Place Final');

-- 3. Drop the duplicated SIM knockout matches (regenerable demo data) and their scoring/predictions
DELETE sb FROM score_breakdowns sb
JOIN matches m ON m.id = sb.match_id
JOIN rounds r  ON r.id = m.round_id
WHERE r.stage = 'KNOCKOUT' AND m.result_source = 'SIM';

DELETE mp FROM match_predictions mp
JOIN matches m ON m.id = mp.match_id
JOIN rounds r  ON r.id = m.round_id
WHERE r.stage = 'KNOCKOUT' AND m.result_source = 'SIM';

DELETE m FROM matches m
JOIN rounds r ON r.id = m.round_id
WHERE r.stage = 'KNOCKOUT' AND m.result_source = 'SIM';

-- 4. Wipe residual results left on NONE placeholders by old playRound runs
DELETE sb FROM score_breakdowns sb
JOIN matches m ON m.id = sb.match_id
JOIN rounds r  ON r.id = m.round_id
WHERE r.stage = 'KNOCKOUT' AND m.result_source = 'NONE';

UPDATE matches m
JOIN rounds r ON r.id = m.round_id
SET m.home_goals = NULL, m.away_goals = NULL,
    m.status = 'OPEN', m.status_short = 'NS', m.kickoff_at = NULL
WHERE r.stage = 'KNOCKOUT' AND m.result_source = 'NONE';

-- 5. Top up placeholders so every canonical round has its full slot count
--    (Third place stays empty: it is not part of the 31-match initial bracket)
INSERT INTO matches (round_id, home_team_id, away_team_id, kickoff_at, status, status_short, home_goals, away_goals, result_source)
SELECT r.id, t.id, t.id, NULL, 'OPEN', 'NS', NULL, NULL, 'NONE'
FROM rounds r
JOIN teams t ON t.name = 'Pendiente'
JOIN (SELECT 1 n UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
      UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8
      UNION ALL SELECT 9 UNION ALL SELECT 10 UNION ALL SELECT 11 UNION ALL SELECT 12
      UNION ALL SELECT 13 UNION ALL SELECT 14 UNION ALL SELECT 15 UNION ALL SELECT 16) nums
WHERE r.name = 'Round of 32'
  AND nums.n > (SELECT COUNT(*) FROM matches m WHERE m.round_id = r.id);

INSERT INTO matches (round_id, home_team_id, away_team_id, kickoff_at, status, status_short, home_goals, away_goals, result_source)
SELECT r.id, t.id, t.id, NULL, 'OPEN', 'NS', NULL, NULL, 'NONE'
FROM rounds r
JOIN teams t ON t.name = 'Pendiente'
JOIN (SELECT 1 n UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
      UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8) nums
WHERE r.name = 'Round of 16'
  AND nums.n > (SELECT COUNT(*) FROM matches m WHERE m.round_id = r.id);

INSERT INTO matches (round_id, home_team_id, away_team_id, kickoff_at, status, status_short, home_goals, away_goals, result_source)
SELECT r.id, t.id, t.id, NULL, 'OPEN', 'NS', NULL, NULL, 'NONE'
FROM rounds r
JOIN teams t ON t.name = 'Pendiente'
JOIN (SELECT 1 n UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4) nums
WHERE r.name = 'Quarter-finals'
  AND nums.n > (SELECT COUNT(*) FROM matches m WHERE m.round_id = r.id);

INSERT INTO matches (round_id, home_team_id, away_team_id, kickoff_at, status, status_short, home_goals, away_goals, result_source)
SELECT r.id, t.id, t.id, NULL, 'OPEN', 'NS', NULL, NULL, 'NONE'
FROM rounds r
JOIN teams t ON t.name = 'Pendiente'
JOIN (SELECT 1 n UNION ALL SELECT 2) nums
WHERE r.name = 'Semi-finals'
  AND nums.n > (SELECT COUNT(*) FROM matches m WHERE m.round_id = r.id);

INSERT INTO matches (round_id, home_team_id, away_team_id, kickoff_at, status, status_short, home_goals, away_goals, result_source)
SELECT r.id, t.id, t.id, NULL, 'OPEN', 'NS', NULL, NULL, 'NONE'
FROM rounds r
JOIN teams t ON t.name = 'Pendiente'
JOIN (SELECT 1 n) nums
WHERE r.name = 'Final'
  AND nums.n > (SELECT COUNT(*) FROM matches m WHERE m.round_id = r.id);
