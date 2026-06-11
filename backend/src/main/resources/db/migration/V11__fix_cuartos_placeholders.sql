-- Replace demo Cuartos matches (Espana/Alemania/Brasil/etc.) with Pendiente placeholders
-- so that all knockout rounds are consistently TBD until the API syncs real data
UPDATE matches m
JOIN rounds r ON r.id = m.round_id
SET m.home_team_id = (SELECT id FROM teams WHERE name = 'Pendiente'),
    m.away_team_id = (SELECT id FROM teams WHERE name = 'Pendiente'),
    m.home_goals = NULL,
    m.away_goals = NULL,
    m.status = 'OPEN',
    m.status_short = 'NS',
    m.result_source = 'NONE'
WHERE r.name = 'Cuartos';
