-- Repopulate knockout bracket with Pendiente placeholders.
-- These get replaced by real data when the WC26 sync runs.

-- Round of 32: 16 matches
INSERT INTO matches (round_id, home_team_id, away_team_id, kickoff_at, status, status_short, home_goals, away_goals, result_source)
SELECT r.id, t.id, t.id, NULL, 'OPEN', 'NS', NULL, NULL, 'NONE'
FROM rounds r
CROSS JOIN teams t
CROSS JOIN (SELECT 1 n UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
            UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8
            UNION ALL SELECT 9 UNION ALL SELECT 10 UNION ALL SELECT 11 UNION ALL SELECT 12
            UNION ALL SELECT 13 UNION ALL SELECT 14 UNION ALL SELECT 15 UNION ALL SELECT 16) nums
WHERE r.name = 'Round of 32' AND t.name = 'Pendiente'
  AND NOT EXISTS (SELECT 1 FROM matches m WHERE m.round_id = r.id);

-- Round of 16: 8 matches
INSERT INTO matches (round_id, home_team_id, away_team_id, kickoff_at, status, status_short, home_goals, away_goals, result_source)
SELECT r.id, t.id, t.id, NULL, 'OPEN', 'NS', NULL, NULL, 'NONE'
FROM rounds r
CROSS JOIN teams t
CROSS JOIN (SELECT 1 n UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
            UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8) nums
WHERE r.name = 'Round of 16' AND t.name = 'Pendiente'
  AND NOT EXISTS (SELECT 1 FROM matches m WHERE m.round_id = r.id);

-- Cuartos: 4 matches
INSERT INTO matches (round_id, home_team_id, away_team_id, kickoff_at, status, status_short, home_goals, away_goals, result_source)
SELECT r.id, t.id, t.id, NULL, 'OPEN', 'NS', NULL, NULL, 'NONE'
FROM rounds r
CROSS JOIN teams t
CROSS JOIN (SELECT 1 n UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4) nums
WHERE r.name = 'Cuartos' AND t.name = 'Pendiente'
  AND NOT EXISTS (SELECT 1 FROM matches m WHERE m.round_id = r.id);

-- Semis: 2 matches
INSERT INTO matches (round_id, home_team_id, away_team_id, kickoff_at, status, status_short, home_goals, away_goals, result_source)
SELECT r.id, t.id, t.id, NULL, 'OPEN', 'NS', NULL, NULL, 'NONE'
FROM rounds r
CROSS JOIN teams t
CROSS JOIN (SELECT 1 n UNION ALL SELECT 2) nums
WHERE r.name = 'Semis' AND t.name = 'Pendiente'
  AND NOT EXISTS (SELECT 1 FROM matches m WHERE m.round_id = r.id);

-- Final: 1 match
INSERT INTO matches (round_id, home_team_id, away_team_id, kickoff_at, status, status_short, home_goals, away_goals, result_source)
SELECT r.id, t.id, t.id, NULL, 'OPEN', 'NS', NULL, NULL, 'NONE'
FROM rounds r
CROSS JOIN teams t
WHERE r.name = 'Final' AND t.name = 'Pendiente'
  AND NOT EXISTS (SELECT 1 FROM matches m WHERE m.round_id = r.id);
