INSERT INTO users (email, display_name)
VALUES
  ('admin@mundia.local', 'Admin'),
  ('diego@mundia.local', 'Diego'),
  ('juan@mundia.local', 'Juan'),
  ('david@mundia.local', 'David'),
  ('sonia@mundia.local', 'Sonia'),
  ('petri@mundia.local', 'Petri'),
  ('fernando@mundia.local', 'Fernando')
ON DUPLICATE KEY UPDATE display_name = VALUES(display_name);

INSERT INTO pools (owner_user_id, name, description, invite_code, entry_fee_cents, currency, status)
SELECT id, 'Mundial Familia 2026', 'Porra familiar principal', 'MUNDIA-26', 1000, 'EUR', 'OPEN'
FROM users
WHERE email = 'admin@mundia.local'
ON DUPLICATE KEY UPDATE name = VALUES(name), status = VALUES(status);

INSERT INTO pools (owner_user_id, name, description, invite_code, entry_fee_cents, currency, status)
SELECT id, 'La porra de los primos', 'Borrador para probar varias porras', 'PRIMOS-10', 1000, 'EUR', 'DRAFT'
FROM users
WHERE email = 'admin@mundia.local'
ON DUPLICATE KEY UPDATE name = VALUES(name), status = VALUES(status);

INSERT INTO pool_members (pool_id, user_id, role, status, joined_at)
SELECT p.id, u.id, CASE WHEN u.email = 'admin@mundia.local' THEN 'ADMIN' ELSE 'PLAYER' END, 'ACTIVE', CURRENT_TIMESTAMP(6)
FROM pools p
JOIN users u ON u.email IN (
  'admin@mundia.local',
  'diego@mundia.local',
  'juan@mundia.local',
  'david@mundia.local',
  'sonia@mundia.local',
  'petri@mundia.local',
  'fernando@mundia.local'
)
WHERE p.invite_code = 'MUNDIA-26'
ON DUPLICATE KEY UPDATE status = VALUES(status);

INSERT INTO payments (pool_member_id, amount_cents, method, status, notes)
SELECT pm.id, 1000, 'BIZUM', 'PENDING', 'Pendiente de confirmar por admin'
FROM pool_members pm
JOIN pools p ON p.id = pm.pool_id
JOIN users u ON u.id = pm.user_id
WHERE p.invite_code = 'MUNDIA-26'
  AND u.email <> 'admin@mundia.local'
  AND NOT EXISTS (
    SELECT 1
    FROM payments existing
    WHERE existing.pool_member_id = pm.id
  );

INSERT INTO rounds (name, stage, sort_order, external_name)
VALUES
  ('Cuartos', 'KNOCKOUT', 1, 'Quarter-finals'),
  ('Semis', 'KNOCKOUT', 2, 'Semi-finals'),
  ('Final', 'KNOCKOUT', 3, 'Final')
ON DUPLICATE KEY UPDATE stage = VALUES(stage), sort_order = VALUES(sort_order), external_name = VALUES(external_name);

INSERT INTO teams (external_team_id, name, country_code)
VALUES
  (NULL, 'Espana', 'es'),
  (NULL, 'Alemania', 'de'),
  (NULL, 'Brasil', 'br'),
  (NULL, 'Portugal', 'pt'),
  (NULL, 'Argentina', 'ar'),
  (NULL, 'Francia', 'fr'),
  (NULL, 'Inglaterra', 'gb-eng'),
  (NULL, 'Italia', 'it'),
  (NULL, 'Pendiente', 'un')
ON DUPLICATE KEY UPDATE name = VALUES(name);

INSERT INTO matches (round_id, home_team_id, away_team_id, kickoff_at, status, status_short, home_goals, away_goals, result_source)
SELECT r.id, ht.id, at.id, NULL, 'OPEN', 'NS', NULL, NULL, 'NONE'
FROM rounds r
JOIN teams ht ON ht.name = 'Espana'
JOIN teams at ON at.name = 'Alemania'
WHERE r.name = 'Cuartos'
  AND NOT EXISTS (
    SELECT 1 FROM matches m
    WHERE m.round_id = r.id AND m.home_team_id = ht.id AND m.away_team_id = at.id
  );

INSERT INTO matches (round_id, home_team_id, away_team_id, kickoff_at, status, status_short, home_goals, away_goals, result_source)
SELECT r.id, ht.id, at.id, NULL, 'OPEN', 'NS', NULL, NULL, 'NONE'
FROM rounds r
JOIN teams ht ON ht.name = 'Brasil'
JOIN teams at ON at.name = 'Portugal'
WHERE r.name = 'Cuartos'
  AND NOT EXISTS (
    SELECT 1 FROM matches m
    WHERE m.round_id = r.id AND m.home_team_id = ht.id AND m.away_team_id = at.id
  );

INSERT INTO matches (round_id, home_team_id, away_team_id, kickoff_at, status, status_short, home_goals, away_goals, result_source)
SELECT r.id, ht.id, at.id, NULL, 'OPEN', 'NS', NULL, NULL, 'NONE'
FROM rounds r
JOIN teams ht ON ht.name = 'Argentina'
JOIN teams at ON at.name = 'Francia'
WHERE r.name = 'Cuartos'
  AND NOT EXISTS (
    SELECT 1 FROM matches m
    WHERE m.round_id = r.id AND m.home_team_id = ht.id AND m.away_team_id = at.id
  );

INSERT INTO matches (round_id, home_team_id, away_team_id, kickoff_at, status, status_short, home_goals, away_goals, result_source)
SELECT r.id, ht.id, at.id, NULL, 'OPEN', 'NS', NULL, NULL, 'NONE'
FROM rounds r
JOIN teams ht ON ht.name = 'Inglaterra'
JOIN teams at ON at.name = 'Italia'
WHERE r.name = 'Cuartos'
  AND NOT EXISTS (
    SELECT 1 FROM matches m
    WHERE m.round_id = r.id AND m.home_team_id = ht.id AND m.away_team_id = at.id
  );

INSERT INTO prize_rules (pool_id, category, percentage_when_perfect_alive, percentage_when_perfect_extinct, enabled)
SELECT p.id, category, pct_alive, pct_extinct, TRUE
FROM pools p
JOIN (
  SELECT 'PERFECT_WINNERS' category, 75.00 pct_alive, 0.00 pct_extinct
  UNION ALL SELECT 'GENERAL', 10.00, 40.00
  UNION ALL SELECT 'INITIAL_BET', 5.00, 15.00
  UNION ALL SELECT 'EXACT_RESULTS', 5.00, 20.00
  UNION ALL SELECT 'WINNERS', 5.00, 20.00
) rules
WHERE p.invite_code = 'MUNDIA-26'
ON DUPLICATE KEY UPDATE
  percentage_when_perfect_alive = VALUES(percentage_when_perfect_alive),
  percentage_when_perfect_extinct = VALUES(percentage_when_perfect_extinct),
  enabled = VALUES(enabled);
