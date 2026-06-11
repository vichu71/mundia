-- Semis: 2 placeholder matches (Pendiente vs Pendiente)
INSERT INTO matches (round_id, home_team_id, away_team_id, kickoff_at, status, status_short, home_goals, away_goals, result_source)
SELECT r.id, t.id, t.id, NULL, 'OPEN', 'NS', NULL, NULL, 'NONE'
FROM rounds r
CROSS JOIN teams t
CROSS JOIN (SELECT 1 UNION ALL SELECT 2) nums
WHERE r.name = 'Semis' AND t.name = 'Pendiente'
  AND NOT EXISTS (SELECT 1 FROM matches m WHERE m.round_id = r.id);

-- Final: 1 placeholder match (Pendiente vs Pendiente)
INSERT INTO matches (round_id, home_team_id, away_team_id, kickoff_at, status, status_short, home_goals, away_goals, result_source)
SELECT r.id, t.id, t.id, NULL, 'OPEN', 'NS', NULL, NULL, 'NONE'
FROM rounds r
CROSS JOIN teams t
WHERE r.name = 'Final' AND t.name = 'Pendiente'
  AND NOT EXISTS (SELECT 1 FROM matches m WHERE m.round_id = r.id);
