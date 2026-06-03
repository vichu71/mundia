INSERT INTO prize_rules (pool_id, category, percentage_when_perfect_alive, percentage_when_perfect_extinct, enabled)
SELECT p.id, 'CHAMPION', 5.00, 5.00, TRUE
FROM pools p
WHERE p.invite_code = 'MUNDIA-26'
ON DUPLICATE KEY UPDATE
  percentage_when_perfect_alive = VALUES(percentage_when_perfect_alive),
  percentage_when_perfect_extinct = VALUES(percentage_when_perfect_extinct),
  enabled = VALUES(enabled);
