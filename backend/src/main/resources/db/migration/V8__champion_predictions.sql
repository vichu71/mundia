CREATE TABLE champion_predictions (
  id BIGINT NOT NULL AUTO_INCREMENT,
  pool_member_id BIGINT NOT NULL,
  team_id BIGINT NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uk_champion_predictions_member (pool_member_id),
  KEY idx_champion_predictions_team (team_id),
  CONSTRAINT fk_champion_predictions_member FOREIGN KEY (pool_member_id) REFERENCES pool_members (id),
  CONSTRAINT fk_champion_predictions_team FOREIGN KEY (team_id) REFERENCES teams (id)
);
