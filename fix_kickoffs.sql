-- Actualizar kickoff_at según calendario oficial WC2026
-- Total partidos de grupos: 72

UPDATE matches m
  JOIN teams ht ON ht.id = m.home_team_id AND ht.name = 'Mexico'
  JOIN teams at ON at.id = m.away_team_id AND at.name = 'South Africa'
  SET m.kickoff_at = '2026-06-11 19:00:00'
  WHERE m.kickoff_at IS NOT NULL;

UPDATE matches m
  JOIN teams ht ON ht.id = m.home_team_id AND ht.name = 'South Korea'
  JOIN teams at ON at.id = m.away_team_id AND at.name = 'Czech Republic'
  SET m.kickoff_at = '2026-06-12 02:00:00'
  WHERE m.kickoff_at IS NOT NULL;

UPDATE matches m
  JOIN teams ht ON ht.id = m.home_team_id AND ht.name = 'Canada'
  JOIN teams at ON at.id = m.away_team_id AND at.name = 'Bosnia and Herzegovina'
  SET m.kickoff_at = '2026-06-12 19:00:00'
  WHERE m.kickoff_at IS NOT NULL;

UPDATE matches m
  JOIN teams ht ON ht.id = m.home_team_id AND ht.name = 'United States'
  JOIN teams at ON at.id = m.away_team_id AND at.name = 'Paraguay'
  SET m.kickoff_at = '2026-06-13 01:00:00'
  WHERE m.kickoff_at IS NOT NULL;

UPDATE matches m
  JOIN teams ht ON ht.id = m.home_team_id AND ht.name = 'Qatar'
  JOIN teams at ON at.id = m.away_team_id AND at.name = 'Switzerland'
  SET m.kickoff_at = '2026-06-13 19:00:00'
  WHERE m.kickoff_at IS NOT NULL;

UPDATE matches m
  JOIN teams ht ON ht.id = m.home_team_id AND ht.name = 'Brazil'
  JOIN teams at ON at.id = m.away_team_id AND at.name = 'Morocco'
  SET m.kickoff_at = '2026-06-13 22:00:00'
  WHERE m.kickoff_at IS NOT NULL;

UPDATE matches m
  JOIN teams ht ON ht.id = m.home_team_id AND ht.name = 'Haiti'
  JOIN teams at ON at.id = m.away_team_id AND at.name = 'Scotland'
  SET m.kickoff_at = '2026-06-14 01:00:00'
  WHERE m.kickoff_at IS NOT NULL;

UPDATE matches m
  JOIN teams ht ON ht.id = m.home_team_id AND ht.name = 'Australia'
  JOIN teams at ON at.id = m.away_team_id AND at.name = 'Turkey'
  SET m.kickoff_at = '2026-06-14 04:00:00'
  WHERE m.kickoff_at IS NOT NULL;

UPDATE matches m
  JOIN teams ht ON ht.id = m.home_team_id AND ht.name = 'Germany'
  JOIN teams at ON at.id = m.away_team_id AND at.name = 'Curacao'
  SET m.kickoff_at = '2026-06-14 17:00:00'
  WHERE m.kickoff_at IS NOT NULL;

UPDATE matches m
  JOIN teams ht ON ht.id = m.home_team_id AND ht.name = 'Netherlands'
  JOIN teams at ON at.id = m.away_team_id AND at.name = 'Japón'
  SET m.kickoff_at = '2026-06-14 20:00:00'
  WHERE m.kickoff_at IS NOT NULL;

UPDATE matches m
  JOIN teams ht ON ht.id = m.home_team_id AND ht.name = 'Ivory Coast'
  JOIN teams at ON at.id = m.away_team_id AND at.name = 'Ecuador'
  SET m.kickoff_at = '2026-06-14 23:00:00'
  WHERE m.kickoff_at IS NOT NULL;

UPDATE matches m
  JOIN teams ht ON ht.id = m.home_team_id AND ht.name = 'Sweden'
  JOIN teams at ON at.id = m.away_team_id AND at.name = 'Tunisia'
  SET m.kickoff_at = '2026-06-15 02:00:00'
  WHERE m.kickoff_at IS NOT NULL;

UPDATE matches m
  JOIN teams ht ON ht.id = m.home_team_id AND ht.name = 'Spain'
  JOIN teams at ON at.id = m.away_team_id AND at.name = 'Cape Verde'
  SET m.kickoff_at = '2026-06-15 16:00:00'
  WHERE m.kickoff_at IS NOT NULL;

UPDATE matches m
  JOIN teams ht ON ht.id = m.home_team_id AND ht.name = 'Belgium'
  JOIN teams at ON at.id = m.away_team_id AND at.name = 'Egypt'
  SET m.kickoff_at = '2026-06-15 19:00:00'
  WHERE m.kickoff_at IS NOT NULL;

UPDATE matches m
  JOIN teams ht ON ht.id = m.home_team_id AND ht.name = 'Saudi Arabia'
  JOIN teams at ON at.id = m.away_team_id AND at.name = 'Uruguay'
  SET m.kickoff_at = '2026-06-15 22:00:00'
  WHERE m.kickoff_at IS NOT NULL;

UPDATE matches m
  JOIN teams ht ON ht.id = m.home_team_id AND ht.name = 'Iran'
  JOIN teams at ON at.id = m.away_team_id AND at.name = 'New Zealand'
  SET m.kickoff_at = '2026-06-16 01:00:00'
  WHERE m.kickoff_at IS NOT NULL;

UPDATE matches m
  JOIN teams ht ON ht.id = m.home_team_id AND ht.name = 'France'
  JOIN teams at ON at.id = m.away_team_id AND at.name = 'Senegal'
  SET m.kickoff_at = '2026-06-16 19:00:00'
  WHERE m.kickoff_at IS NOT NULL;

UPDATE matches m
  JOIN teams ht ON ht.id = m.home_team_id AND ht.name = 'Iraq'
  JOIN teams at ON at.id = m.away_team_id AND at.name = 'Norway'
  SET m.kickoff_at = '2026-06-16 22:00:00'
  WHERE m.kickoff_at IS NOT NULL;

UPDATE matches m
  JOIN teams ht ON ht.id = m.home_team_id AND ht.name = 'Argentina'
  JOIN teams at ON at.id = m.away_team_id AND at.name = 'Algeria'
  SET m.kickoff_at = '2026-06-17 01:00:00'
  WHERE m.kickoff_at IS NOT NULL;

UPDATE matches m
  JOIN teams ht ON ht.id = m.home_team_id AND ht.name = 'Austria'
  JOIN teams at ON at.id = m.away_team_id AND at.name = 'Jordan'
  SET m.kickoff_at = '2026-06-17 04:00:00'
  WHERE m.kickoff_at IS NOT NULL;

UPDATE matches m
  JOIN teams ht ON ht.id = m.home_team_id AND ht.name = 'Portugal'
  JOIN teams at ON at.id = m.away_team_id AND at.name = 'DR Congo'
  SET m.kickoff_at = '2026-06-17 17:00:00'
  WHERE m.kickoff_at IS NOT NULL;

UPDATE matches m
  JOIN teams ht ON ht.id = m.home_team_id AND ht.name = 'England'
  JOIN teams at ON at.id = m.away_team_id AND at.name = 'Croatia'
  SET m.kickoff_at = '2026-06-17 20:00:00'
  WHERE m.kickoff_at IS NOT NULL;

UPDATE matches m
  JOIN teams ht ON ht.id = m.home_team_id AND ht.name = 'Ghana'
  JOIN teams at ON at.id = m.away_team_id AND at.name = 'Panama'
  SET m.kickoff_at = '2026-06-17 23:00:00'
  WHERE m.kickoff_at IS NOT NULL;

UPDATE matches m
  JOIN teams ht ON ht.id = m.home_team_id AND ht.name = 'Uzbekistan'
  JOIN teams at ON at.id = m.away_team_id AND at.name = 'Colombia'
  SET m.kickoff_at = '2026-06-18 02:00:00'
  WHERE m.kickoff_at IS NOT NULL;

UPDATE matches m
  JOIN teams ht ON ht.id = m.home_team_id AND ht.name = 'Czech Republic'
  JOIN teams at ON at.id = m.away_team_id AND at.name = 'South Africa'
  SET m.kickoff_at = '2026-06-18 16:00:00'
  WHERE m.kickoff_at IS NOT NULL;

UPDATE matches m
  JOIN teams ht ON ht.id = m.home_team_id AND ht.name = 'Switzerland'
  JOIN teams at ON at.id = m.away_team_id AND at.name = 'Bosnia and Herzegovina'
  SET m.kickoff_at = '2026-06-18 19:00:00'
  WHERE m.kickoff_at IS NOT NULL;

UPDATE matches m
  JOIN teams ht ON ht.id = m.home_team_id AND ht.name = 'Canada'
  JOIN teams at ON at.id = m.away_team_id AND at.name = 'Qatar'
  SET m.kickoff_at = '2026-06-18 22:00:00'
  WHERE m.kickoff_at IS NOT NULL;

UPDATE matches m
  JOIN teams ht ON ht.id = m.home_team_id AND ht.name = 'Mexico'
  JOIN teams at ON at.id = m.away_team_id AND at.name = 'South Korea'
  SET m.kickoff_at = '2026-06-19 01:00:00'
  WHERE m.kickoff_at IS NOT NULL;

UPDATE matches m
  JOIN teams ht ON ht.id = m.home_team_id AND ht.name = 'United States'
  JOIN teams at ON at.id = m.away_team_id AND at.name = 'Australia'
  SET m.kickoff_at = '2026-06-19 19:00:00'
  WHERE m.kickoff_at IS NOT NULL;

UPDATE matches m
  JOIN teams ht ON ht.id = m.home_team_id AND ht.name = 'Scotland'
  JOIN teams at ON at.id = m.away_team_id AND at.name = 'Morocco'
  SET m.kickoff_at = '2026-06-19 22:00:00'
  WHERE m.kickoff_at IS NOT NULL;

UPDATE matches m
  JOIN teams ht ON ht.id = m.home_team_id AND ht.name = 'Brazil'
  JOIN teams at ON at.id = m.away_team_id AND at.name = 'Haiti'
  SET m.kickoff_at = '2026-06-20 00:30:00'
  WHERE m.kickoff_at IS NOT NULL;

UPDATE matches m
  JOIN teams ht ON ht.id = m.home_team_id AND ht.name = 'Turkey'
  JOIN teams at ON at.id = m.away_team_id AND at.name = 'Paraguay'
  SET m.kickoff_at = '2026-06-20 03:00:00'
  WHERE m.kickoff_at IS NOT NULL;

UPDATE matches m
  JOIN teams ht ON ht.id = m.home_team_id AND ht.name = 'Netherlands'
  JOIN teams at ON at.id = m.away_team_id AND at.name = 'Sweden'
  SET m.kickoff_at = '2026-06-20 17:00:00'
  WHERE m.kickoff_at IS NOT NULL;

UPDATE matches m
  JOIN teams ht ON ht.id = m.home_team_id AND ht.name = 'Germany'
  JOIN teams at ON at.id = m.away_team_id AND at.name = 'Ivory Coast'
  SET m.kickoff_at = '2026-06-20 20:00:00'
  WHERE m.kickoff_at IS NOT NULL;

UPDATE matches m
  JOIN teams ht ON ht.id = m.home_team_id AND ht.name = 'Ecuador'
  JOIN teams at ON at.id = m.away_team_id AND at.name = 'Curacao'
  SET m.kickoff_at = '2026-06-21 00:00:00'
  WHERE m.kickoff_at IS NOT NULL;

UPDATE matches m
  JOIN teams ht ON ht.id = m.home_team_id AND ht.name = 'Tunisia'
  JOIN teams at ON at.id = m.away_team_id AND at.name = 'Japón'
  SET m.kickoff_at = '2026-06-21 04:00:00'
  WHERE m.kickoff_at IS NOT NULL;

UPDATE matches m
  JOIN teams ht ON ht.id = m.home_team_id AND ht.name = 'Spain'
  JOIN teams at ON at.id = m.away_team_id AND at.name = 'Saudi Arabia'
  SET m.kickoff_at = '2026-06-21 16:00:00'
  WHERE m.kickoff_at IS NOT NULL;

UPDATE matches m
  JOIN teams ht ON ht.id = m.home_team_id AND ht.name = 'Belgium'
  JOIN teams at ON at.id = m.away_team_id AND at.name = 'Iran'
  SET m.kickoff_at = '2026-06-21 19:00:00'
  WHERE m.kickoff_at IS NOT NULL;

UPDATE matches m
  JOIN teams ht ON ht.id = m.home_team_id AND ht.name = 'Uruguay'
  JOIN teams at ON at.id = m.away_team_id AND at.name = 'Cape Verde'
  SET m.kickoff_at = '2026-06-21 22:00:00'
  WHERE m.kickoff_at IS NOT NULL;

UPDATE matches m
  JOIN teams ht ON ht.id = m.home_team_id AND ht.name = 'New Zealand'
  JOIN teams at ON at.id = m.away_team_id AND at.name = 'Egypt'
  SET m.kickoff_at = '2026-06-22 01:00:00'
  WHERE m.kickoff_at IS NOT NULL;

UPDATE matches m
  JOIN teams ht ON ht.id = m.home_team_id AND ht.name = 'Argentina'
  JOIN teams at ON at.id = m.away_team_id AND at.name = 'Austria'
  SET m.kickoff_at = '2026-06-22 17:00:00'
  WHERE m.kickoff_at IS NOT NULL;

UPDATE matches m
  JOIN teams ht ON ht.id = m.home_team_id AND ht.name = 'France'
  JOIN teams at ON at.id = m.away_team_id AND at.name = 'Iraq'
  SET m.kickoff_at = '2026-06-22 21:00:00'
  WHERE m.kickoff_at IS NOT NULL;

UPDATE matches m
  JOIN teams ht ON ht.id = m.home_team_id AND ht.name = 'Norway'
  JOIN teams at ON at.id = m.away_team_id AND at.name = 'Senegal'
  SET m.kickoff_at = '2026-06-23 00:00:00'
  WHERE m.kickoff_at IS NOT NULL;

UPDATE matches m
  JOIN teams ht ON ht.id = m.home_team_id AND ht.name = 'Jordan'
  JOIN teams at ON at.id = m.away_team_id AND at.name = 'Algeria'
  SET m.kickoff_at = '2026-06-23 03:00:00'
  WHERE m.kickoff_at IS NOT NULL;

UPDATE matches m
  JOIN teams ht ON ht.id = m.home_team_id AND ht.name = 'Portugal'
  JOIN teams at ON at.id = m.away_team_id AND at.name = 'Uzbekistan'
  SET m.kickoff_at = '2026-06-23 17:00:00'
  WHERE m.kickoff_at IS NOT NULL;

UPDATE matches m
  JOIN teams ht ON ht.id = m.home_team_id AND ht.name = 'England'
  JOIN teams at ON at.id = m.away_team_id AND at.name = 'Ghana'
  SET m.kickoff_at = '2026-06-23 20:00:00'
  WHERE m.kickoff_at IS NOT NULL;

UPDATE matches m
  JOIN teams ht ON ht.id = m.home_team_id AND ht.name = 'Panama'
  JOIN teams at ON at.id = m.away_team_id AND at.name = 'Croatia'
  SET m.kickoff_at = '2026-06-23 23:00:00'
  WHERE m.kickoff_at IS NOT NULL;

UPDATE matches m
  JOIN teams ht ON ht.id = m.home_team_id AND ht.name = 'Colombia'
  JOIN teams at ON at.id = m.away_team_id AND at.name = 'DR Congo'
  SET m.kickoff_at = '2026-06-24 02:00:00'
  WHERE m.kickoff_at IS NOT NULL;

UPDATE matches m
  JOIN teams ht ON ht.id = m.home_team_id AND ht.name = 'Switzerland'
  JOIN teams at ON at.id = m.away_team_id AND at.name = 'Canada'
  SET m.kickoff_at = '2026-06-24 19:00:00'
  WHERE m.kickoff_at IS NOT NULL;

UPDATE matches m
  JOIN teams ht ON ht.id = m.home_team_id AND ht.name = 'Bosnia and Herzegovina'
  JOIN teams at ON at.id = m.away_team_id AND at.name = 'Qatar'
  SET m.kickoff_at = '2026-06-24 19:00:00'
  WHERE m.kickoff_at IS NOT NULL;

UPDATE matches m
  JOIN teams ht ON ht.id = m.home_team_id AND ht.name = 'Scotland'
  JOIN teams at ON at.id = m.away_team_id AND at.name = 'Brazil'
  SET m.kickoff_at = '2026-06-24 22:00:00'
  WHERE m.kickoff_at IS NOT NULL;

UPDATE matches m
  JOIN teams ht ON ht.id = m.home_team_id AND ht.name = 'Morocco'
  JOIN teams at ON at.id = m.away_team_id AND at.name = 'Haiti'
  SET m.kickoff_at = '2026-06-24 22:00:00'
  WHERE m.kickoff_at IS NOT NULL;

UPDATE matches m
  JOIN teams ht ON ht.id = m.home_team_id AND ht.name = 'South Africa'
  JOIN teams at ON at.id = m.away_team_id AND at.name = 'South Korea'
  SET m.kickoff_at = '2026-06-25 01:00:00'
  WHERE m.kickoff_at IS NOT NULL;

UPDATE matches m
  JOIN teams ht ON ht.id = m.home_team_id AND ht.name = 'Czech Republic'
  JOIN teams at ON at.id = m.away_team_id AND at.name = 'Mexico'
  SET m.kickoff_at = '2026-06-25 01:00:00'
  WHERE m.kickoff_at IS NOT NULL;

UPDATE matches m
  JOIN teams ht ON ht.id = m.home_team_id AND ht.name = 'Ecuador'
  JOIN teams at ON at.id = m.away_team_id AND at.name = 'Germany'
  SET m.kickoff_at = '2026-06-25 20:00:00'
  WHERE m.kickoff_at IS NOT NULL;

UPDATE matches m
  JOIN teams ht ON ht.id = m.home_team_id AND ht.name = 'Curacao'
  JOIN teams at ON at.id = m.away_team_id AND at.name = 'Ivory Coast'
  SET m.kickoff_at = '2026-06-25 20:00:00'
  WHERE m.kickoff_at IS NOT NULL;

UPDATE matches m
  JOIN teams ht ON ht.id = m.home_team_id AND ht.name = 'Tunisia'
  JOIN teams at ON at.id = m.away_team_id AND at.name = 'Netherlands'
  SET m.kickoff_at = '2026-06-25 23:00:00'
  WHERE m.kickoff_at IS NOT NULL;

UPDATE matches m
  JOIN teams ht ON ht.id = m.home_team_id AND ht.name = 'Japón'
  JOIN teams at ON at.id = m.away_team_id AND at.name = 'Sweden'
  SET m.kickoff_at = '2026-06-25 23:00:00'
  WHERE m.kickoff_at IS NOT NULL;

UPDATE matches m
  JOIN teams ht ON ht.id = m.home_team_id AND ht.name = 'Turkey'
  JOIN teams at ON at.id = m.away_team_id AND at.name = 'United States'
  SET m.kickoff_at = '2026-06-26 02:00:00'
  WHERE m.kickoff_at IS NOT NULL;

UPDATE matches m
  JOIN teams ht ON ht.id = m.home_team_id AND ht.name = 'Paraguay'
  JOIN teams at ON at.id = m.away_team_id AND at.name = 'Australia'
  SET m.kickoff_at = '2026-06-26 02:00:00'
  WHERE m.kickoff_at IS NOT NULL;

UPDATE matches m
  JOIN teams ht ON ht.id = m.home_team_id AND ht.name = 'Senegal'
  JOIN teams at ON at.id = m.away_team_id AND at.name = 'Iraq'
  SET m.kickoff_at = '2026-06-26 19:00:00'
  WHERE m.kickoff_at IS NOT NULL;

UPDATE matches m
  JOIN teams ht ON ht.id = m.home_team_id AND ht.name = 'Norway'
  JOIN teams at ON at.id = m.away_team_id AND at.name = 'France'
  SET m.kickoff_at = '2026-06-26 19:00:00'
  WHERE m.kickoff_at IS NOT NULL;

UPDATE matches m
  JOIN teams ht ON ht.id = m.home_team_id AND ht.name = 'Uruguay'
  JOIN teams at ON at.id = m.away_team_id AND at.name = 'Spain'
  SET m.kickoff_at = '2026-06-27 00:00:00'
  WHERE m.kickoff_at IS NOT NULL;

UPDATE matches m
  JOIN teams ht ON ht.id = m.home_team_id AND ht.name = 'Cape Verde'
  JOIN teams at ON at.id = m.away_team_id AND at.name = 'Saudi Arabia'
  SET m.kickoff_at = '2026-06-27 00:00:00'
  WHERE m.kickoff_at IS NOT NULL;

UPDATE matches m
  JOIN teams ht ON ht.id = m.home_team_id AND ht.name = 'New Zealand'
  JOIN teams at ON at.id = m.away_team_id AND at.name = 'Belgium'
  SET m.kickoff_at = '2026-06-27 03:00:00'
  WHERE m.kickoff_at IS NOT NULL;

UPDATE matches m
  JOIN teams ht ON ht.id = m.home_team_id AND ht.name = 'Egypt'
  JOIN teams at ON at.id = m.away_team_id AND at.name = 'Iran'
  SET m.kickoff_at = '2026-06-27 03:00:00'
  WHERE m.kickoff_at IS NOT NULL;

UPDATE matches m
  JOIN teams ht ON ht.id = m.home_team_id AND ht.name = 'Panama'
  JOIN teams at ON at.id = m.away_team_id AND at.name = 'England'
  SET m.kickoff_at = '2026-06-27 21:00:00'
  WHERE m.kickoff_at IS NOT NULL;

UPDATE matches m
  JOIN teams ht ON ht.id = m.home_team_id AND ht.name = 'Croatia'
  JOIN teams at ON at.id = m.away_team_id AND at.name = 'Ghana'
  SET m.kickoff_at = '2026-06-27 21:00:00'
  WHERE m.kickoff_at IS NOT NULL;

UPDATE matches m
  JOIN teams ht ON ht.id = m.home_team_id AND ht.name = 'DR Congo'
  JOIN teams at ON at.id = m.away_team_id AND at.name = 'Uzbekistan'
  SET m.kickoff_at = '2026-06-27 23:30:00'
  WHERE m.kickoff_at IS NOT NULL;

UPDATE matches m
  JOIN teams ht ON ht.id = m.home_team_id AND ht.name = 'Colombia'
  JOIN teams at ON at.id = m.away_team_id AND at.name = 'Portugal'
  SET m.kickoff_at = '2026-06-27 23:30:00'
  WHERE m.kickoff_at IS NOT NULL;

UPDATE matches m
  JOIN teams ht ON ht.id = m.home_team_id AND ht.name = 'Jordan'
  JOIN teams at ON at.id = m.away_team_id AND at.name = 'Argentina'
  SET m.kickoff_at = '2026-06-28 02:00:00'
  WHERE m.kickoff_at IS NOT NULL;

UPDATE matches m
  JOIN teams ht ON ht.id = m.home_team_id AND ht.name = 'Algeria'
  JOIN teams at ON at.id = m.away_team_id AND at.name = 'Austria'
  SET m.kickoff_at = '2026-06-28 02:00:00'
  WHERE m.kickoff_at IS NOT NULL;

