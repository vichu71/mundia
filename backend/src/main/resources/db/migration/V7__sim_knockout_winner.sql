ALTER TABLE matches
  ADD COLUMN winner_team_id BIGINT NULL,
  ADD KEY idx_matches_winner_team (winner_team_id),
  ADD CONSTRAINT fk_matches_winner_team FOREIGN KEY (winner_team_id) REFERENCES teams (id);

