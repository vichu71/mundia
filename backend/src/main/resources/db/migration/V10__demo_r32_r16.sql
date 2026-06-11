-- Add Round of 32 and Round of 16 knockout rounds
INSERT INTO rounds (name, stage, sort_order, external_name) VALUES
  ('Round of 32', 'KNOCKOUT', -2, 'Round of 32'),
  ('Round of 16', 'KNOCKOUT', -1, 'Round of 16')
ON DUPLICATE KEY UPDATE stage = VALUES(stage), sort_order = VALUES(sort_order);

-- Fix sort_order of existing knockout rounds so tree order is correct
UPDATE rounds SET sort_order = 0 WHERE name = 'Cuartos';
UPDATE rounds SET sort_order = 1 WHERE name = 'Semis';
UPDATE rounds SET sort_order = 2 WHERE name = 'Final';
UPDATE rounds SET sort_order = 50 WHERE name = '3rd Place Final';

-- Round of 32: 16 placeholder matches (Pendiente vs Pendiente)
INSERT INTO matches (round_id, home_team_id, away_team_id, kickoff_at, status, status_short, home_goals, away_goals, result_source)
SELECT r.id, t.id, t.id, NULL, 'OPEN', 'NS', NULL, NULL, 'NONE'
FROM rounds r
CROSS JOIN teams t
CROSS JOIN (SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
            UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8
            UNION ALL SELECT 9 UNION ALL SELECT 10 UNION ALL SELECT 11 UNION ALL SELECT 12
            UNION ALL SELECT 13 UNION ALL SELECT 14 UNION ALL SELECT 15 UNION ALL SELECT 16) nums
WHERE r.name = 'Round of 32' AND t.name = 'Pendiente'
  AND NOT EXISTS (SELECT 1 FROM matches m WHERE m.round_id = r.id);

-- Round of 16: 8 placeholder matches
INSERT INTO matches (round_id, home_team_id, away_team_id, kickoff_at, status, status_short, home_goals, away_goals, result_source)
SELECT r.id, t.id, t.id, NULL, 'OPEN', 'NS', NULL, NULL, 'NONE'
FROM rounds r
CROSS JOIN teams t
CROSS JOIN (SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
            UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8) nums
WHERE r.name = 'Round of 16' AND t.name = 'Pendiente'
  AND NOT EXISTS (SELECT 1 FROM matches m WHERE m.round_id = r.id);
