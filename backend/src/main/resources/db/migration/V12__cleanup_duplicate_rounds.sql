-- Idempotent cleanup of duplicate rounds created by sync service.
-- Safe to run even if already applied manually.

-- 1. Move any remaining matches from duplicate synced rounds to canonical rounds
UPDATE matches SET round_id = (SELECT id FROM rounds WHERE name = 'Cuartos' LIMIT 1)
WHERE round_id IN (SELECT id FROM (SELECT id FROM rounds WHERE name = 'Quarter-finals' AND id != (SELECT id FROM rounds WHERE name = 'Cuartos' LIMIT 1)) x);

UPDATE matches SET round_id = (SELECT id FROM rounds WHERE name = 'Semis' LIMIT 1)
WHERE round_id IN (SELECT id FROM (SELECT id FROM rounds WHERE name = 'Semi-finals' AND id != (SELECT id FROM rounds WHERE name = 'Semis' LIMIT 1)) x);

UPDATE matches SET round_id = (SELECT id FROM rounds WHERE name = '3rd Place Final' LIMIT 1)
WHERE round_id IN (SELECT id FROM (SELECT id FROM rounds WHERE name = 'Third place') x);

-- 2. Delete duplicate rounds (Quarter-finals, Semi-finals, Third place) — only if different from canonical
DELETE FROM rounds WHERE name = 'Quarter-finals'
  AND id != (SELECT id FROM (SELECT id FROM rounds WHERE name = 'Cuartos' LIMIT 1) x);
DELETE FROM rounds WHERE name = 'Semi-finals'
  AND id != (SELECT id FROM (SELECT id FROM rounds WHERE name = 'Semis' LIMIT 1) x);
DELETE FROM rounds WHERE name = 'Third place';

-- 3. Delete score_breakdowns for remaining Pendiente NONE matches in knockout rounds
DELETE sb FROM score_breakdowns sb
WHERE sb.match_id IN (
  SELECT m.id FROM (
    SELECT m2.id FROM matches m2
    JOIN rounds r ON r.id = m2.round_id
    JOIN teams ht ON ht.id = m2.home_team_id
    WHERE r.stage = 'KNOCKOUT' AND ht.name = 'Pendiente' AND m2.result_source = 'NONE'
  ) m
);

-- 4. Delete match_predictions for remaining Pendiente NONE matches
DELETE mp FROM match_predictions mp
WHERE mp.match_id IN (
  SELECT m.id FROM (
    SELECT m2.id FROM matches m2
    JOIN rounds r ON r.id = m2.round_id
    JOIN teams ht ON ht.id = m2.home_team_id
    WHERE r.stage = 'KNOCKOUT' AND ht.name = 'Pendiente' AND m2.result_source = 'NONE'
  ) m
);

-- 5. Delete the Pendiente placeholder matches
DELETE FROM matches WHERE id IN (
  SELECT m.id FROM (
    SELECT m2.id FROM matches m2
    JOIN rounds r ON r.id = m2.round_id
    JOIN teams ht ON ht.id = m2.home_team_id
    WHERE r.stage = 'KNOCKOUT' AND ht.name = 'Pendiente' AND m2.result_source = 'NONE'
  ) m
);
