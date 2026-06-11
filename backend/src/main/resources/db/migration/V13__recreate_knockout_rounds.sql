-- Ensure knockout rounds exist with correct sort order.
-- Round of 32 and Round of 16 may have been lost in V12 cleanup.
INSERT INTO rounds (name, stage, sort_order, external_name) VALUES
  ('Round of 32', 'KNOCKOUT', -2, 'Round of 32'),
  ('Round of 16', 'KNOCKOUT', -1, 'Round of 16'),
  ('Final',       'KNOCKOUT',  2, 'Final')
ON DUPLICATE KEY UPDATE sort_order = VALUES(sort_order), external_name = VALUES(external_name);

UPDATE rounds SET sort_order = 0  WHERE name = 'Cuartos';
UPDATE rounds SET sort_order = 1  WHERE name = 'Semis';
UPDATE rounds SET sort_order = 50 WHERE name = '3rd Place Final';
