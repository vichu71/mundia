-- Penalty shootout scores for knockout matches that end in a draw after 90'.
-- NULL when the match was decided in regulation/extra time (no shootout).
ALTER TABLE matches
  ADD COLUMN home_penalty_score INT NULL AFTER away_goals,
  ADD COLUMN away_penalty_score INT NULL AFTER home_penalty_score;
