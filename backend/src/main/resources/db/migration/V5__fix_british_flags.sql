UPDATE teams SET country_code = 'gb-eng' WHERE name = 'England'  AND country_code != 'gb-eng';
UPDATE teams SET country_code = 'gb-sct' WHERE name = 'Scotland' AND country_code != 'gb-sct';
UPDATE teams SET country_code = 'gb-wls' WHERE name = 'Wales'    AND country_code != 'gb-wls';
UPDATE teams SET country_code = 'gb-nir' WHERE name = 'Northern Ireland' AND country_code != 'gb-nir';
