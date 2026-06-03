CREATE TABLE users (
  id BIGINT NOT NULL AUTO_INCREMENT,
  google_sub VARCHAR(128) NULL,
  email VARCHAR(255) NOT NULL,
  display_name VARCHAR(160) NOT NULL,
  avatar_url VARCHAR(512) NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uk_users_google_sub (google_sub),
  UNIQUE KEY uk_users_email (email)
);

CREATE TABLE pools (
  id BIGINT NOT NULL AUTO_INCREMENT,
  owner_user_id BIGINT NOT NULL,
  name VARCHAR(160) NOT NULL,
  description VARCHAR(500) NULL,
  invite_code VARCHAR(40) NOT NULL,
  entry_fee_cents INT NOT NULL DEFAULT 1000,
  currency CHAR(3) NOT NULL DEFAULT 'EUR',
  status VARCHAR(30) NOT NULL,
  initial_prediction_locked_at DATETIME(6) NULL,
  initial_bonus_enabled BOOLEAN NOT NULL DEFAULT TRUE,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uk_pools_invite_code (invite_code),
  KEY idx_pools_owner (owner_user_id),
  CONSTRAINT fk_pools_owner FOREIGN KEY (owner_user_id) REFERENCES users (id)
);

CREATE TABLE pool_members (
  id BIGINT NOT NULL AUTO_INCREMENT,
  pool_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  role VARCHAR(30) NOT NULL,
  status VARCHAR(30) NOT NULL,
  joined_at DATETIME(6) NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uk_pool_members_pool_user (pool_id, user_id),
  KEY idx_pool_members_user (user_id),
  CONSTRAINT fk_pool_members_pool FOREIGN KEY (pool_id) REFERENCES pools (id),
  CONSTRAINT fk_pool_members_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE pool_invitations (
  id BIGINT NOT NULL AUTO_INCREMENT,
  pool_id BIGINT NOT NULL,
  invited_email VARCHAR(255) NULL,
  invited_user_id BIGINT NULL,
  token VARCHAR(80) NOT NULL,
  status VARCHAR(30) NOT NULL,
  expires_at DATETIME(6) NULL,
  accepted_at DATETIME(6) NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uk_pool_invitations_token (token),
  KEY idx_pool_invitations_pool (pool_id),
  KEY idx_pool_invitations_user (invited_user_id),
  CONSTRAINT fk_pool_invitations_pool FOREIGN KEY (pool_id) REFERENCES pools (id),
  CONSTRAINT fk_pool_invitations_user FOREIGN KEY (invited_user_id) REFERENCES users (id)
);

CREATE TABLE payments (
  id BIGINT NOT NULL AUTO_INCREMENT,
  pool_member_id BIGINT NOT NULL,
  amount_cents INT NOT NULL,
  method VARCHAR(30) NOT NULL,
  status VARCHAR(30) NOT NULL,
  confirmed_by_user_id BIGINT NULL,
  confirmed_at DATETIME(6) NULL,
  notes VARCHAR(500) NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  KEY idx_payments_member (pool_member_id),
  KEY idx_payments_confirmed_by (confirmed_by_user_id),
  CONSTRAINT fk_payments_member FOREIGN KEY (pool_member_id) REFERENCES pool_members (id),
  CONSTRAINT fk_payments_confirmed_by FOREIGN KEY (confirmed_by_user_id) REFERENCES users (id)
);

CREATE TABLE teams (
  id BIGINT NOT NULL AUTO_INCREMENT,
  external_team_id BIGINT NULL,
  name VARCHAR(120) NOT NULL,
  country_code VARCHAR(16) NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uk_teams_external_team_id (external_team_id)
);

CREATE TABLE rounds (
  id BIGINT NOT NULL AUTO_INCREMENT,
  name VARCHAR(120) NOT NULL,
  stage VARCHAR(40) NOT NULL,
  sort_order INT NOT NULL,
  external_name VARCHAR(160) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_rounds_name (name)
);

CREATE TABLE matches (
  id BIGINT NOT NULL AUTO_INCREMENT,
  external_fixture_id BIGINT NULL,
  round_id BIGINT NOT NULL,
  home_team_id BIGINT NOT NULL,
  away_team_id BIGINT NOT NULL,
  kickoff_at DATETIME(6) NULL,
  status VARCHAR(30) NOT NULL,
  status_short VARCHAR(10) NULL,
  elapsed INT NULL,
  home_goals INT NULL,
  away_goals INT NULL,
  result_source VARCHAR(30) NOT NULL DEFAULT 'NONE',
  manual_override BOOLEAN NOT NULL DEFAULT FALSE,
  last_synced_at DATETIME(6) NULL,
  raw_payload_hash VARCHAR(128) NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uk_matches_external_fixture_id (external_fixture_id),
  KEY idx_matches_kickoff_at (kickoff_at),
  KEY idx_matches_status (status),
  KEY idx_matches_round (round_id),
  KEY idx_matches_home_team (home_team_id),
  KEY idx_matches_away_team (away_team_id),
  CONSTRAINT fk_matches_round FOREIGN KEY (round_id) REFERENCES rounds (id),
  CONSTRAINT fk_matches_home_team FOREIGN KEY (home_team_id) REFERENCES teams (id),
  CONSTRAINT fk_matches_away_team FOREIGN KEY (away_team_id) REFERENCES teams (id)
);

CREATE TABLE standings_snapshots (
  id BIGINT NOT NULL AUTO_INCREMENT,
  round_name VARCHAR(120) NULL,
  provider VARCHAR(40) NOT NULL,
  payload_json JSON NOT NULL,
  synced_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  KEY idx_standings_snapshots_synced_at (synced_at)
);

CREATE TABLE prediction_sets (
  id BIGINT NOT NULL AUTO_INCREMENT,
  pool_member_id BIGINT NOT NULL,
  type VARCHAR(30) NOT NULL,
  status VARCHAR(30) NOT NULL,
  submitted_at DATETIME(6) NULL,
  locked_at DATETIME(6) NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uk_prediction_sets_member_type (pool_member_id, type),
  CONSTRAINT fk_prediction_sets_member FOREIGN KEY (pool_member_id) REFERENCES pool_members (id)
);

CREATE TABLE match_predictions (
  id BIGINT NOT NULL AUTO_INCREMENT,
  prediction_set_id BIGINT NOT NULL,
  match_id BIGINT NOT NULL,
  home_goals INT NOT NULL,
  away_goals INT NOT NULL,
  predicted_winner_team_id BIGINT NULL,
  is_editable BOOLEAN NOT NULL DEFAULT TRUE,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uk_match_predictions_set_match (prediction_set_id, match_id),
  KEY idx_match_predictions_match (match_id),
  KEY idx_match_predictions_winner (predicted_winner_team_id),
  CONSTRAINT fk_match_predictions_set FOREIGN KEY (prediction_set_id) REFERENCES prediction_sets (id),
  CONSTRAINT fk_match_predictions_match FOREIGN KEY (match_id) REFERENCES matches (id),
  CONSTRAINT fk_match_predictions_winner FOREIGN KEY (predicted_winner_team_id) REFERENCES teams (id)
);

CREATE TABLE bracket_predictions (
  id BIGINT NOT NULL AUTO_INCREMENT,
  prediction_set_id BIGINT NOT NULL,
  round_id BIGINT NOT NULL,
  slot_key VARCHAR(80) NOT NULL,
  predicted_team_id BIGINT NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uk_bracket_predictions_slot (prediction_set_id, round_id, slot_key),
  KEY idx_bracket_predictions_team (predicted_team_id),
  CONSTRAINT fk_bracket_predictions_set FOREIGN KEY (prediction_set_id) REFERENCES prediction_sets (id),
  CONSTRAINT fk_bracket_predictions_round FOREIGN KEY (round_id) REFERENCES rounds (id),
  CONSTRAINT fk_bracket_predictions_team FOREIGN KEY (predicted_team_id) REFERENCES teams (id)
);

CREATE TABLE score_breakdowns (
  id BIGINT NOT NULL AUTO_INCREMENT,
  pool_member_id BIGINT NOT NULL,
  prediction_set_id BIGINT NOT NULL,
  match_id BIGINT NULL,
  category VARCHAR(40) NOT NULL,
  points INT NOT NULL,
  details_json JSON NULL,
  calculated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  KEY idx_score_breakdowns_member_category (pool_member_id, category),
  KEY idx_score_breakdowns_prediction_set (prediction_set_id),
  KEY idx_score_breakdowns_match (match_id),
  CONSTRAINT fk_score_breakdowns_member FOREIGN KEY (pool_member_id) REFERENCES pool_members (id),
  CONSTRAINT fk_score_breakdowns_prediction_set FOREIGN KEY (prediction_set_id) REFERENCES prediction_sets (id),
  CONSTRAINT fk_score_breakdowns_match FOREIGN KEY (match_id) REFERENCES matches (id)
);

CREATE TABLE prize_rules (
  id BIGINT NOT NULL AUTO_INCREMENT,
  pool_id BIGINT NOT NULL,
  category VARCHAR(50) NOT NULL,
  percentage_when_perfect_alive DECIMAL(5,2) NOT NULL,
  percentage_when_perfect_extinct DECIMAL(5,2) NOT NULL,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  PRIMARY KEY (id),
  UNIQUE KEY uk_prize_rules_pool_category (pool_id, category),
  CONSTRAINT fk_prize_rules_pool FOREIGN KEY (pool_id) REFERENCES pools (id)
);

CREATE TABLE prize_projections (
  id BIGINT NOT NULL AUTO_INCREMENT,
  pool_member_id BIGINT NOT NULL,
  category VARCHAR(50) NOT NULL,
  current_amount_cents INT NOT NULL,
  max_possible_amount_cents INT NOT NULL,
  status VARCHAR(30) NOT NULL,
  calculated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  KEY idx_prize_projections_member (pool_member_id),
  CONSTRAINT fk_prize_projections_member FOREIGN KEY (pool_member_id) REFERENCES pool_members (id)
);

CREATE TABLE sports_sync_runs (
  id BIGINT NOT NULL AUTO_INCREMENT,
  provider VARCHAR(40) NOT NULL,
  sync_type VARCHAR(40) NOT NULL,
  status VARCHAR(30) NOT NULL,
  request_url VARCHAR(600) NOT NULL,
  response_hash VARCHAR(128) NULL,
  started_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  finished_at DATETIME(6) NULL,
  error_message VARCHAR(1000) NULL,
  PRIMARY KEY (id),
  KEY idx_sports_sync_runs_provider_type (provider, sync_type),
  KEY idx_sports_sync_runs_started_at (started_at)
);

CREATE TABLE audit_logs (
  id BIGINT NOT NULL AUTO_INCREMENT,
  actor_user_id BIGINT NULL,
  pool_id BIGINT NULL,
  entity_type VARCHAR(80) NOT NULL,
  entity_id BIGINT NULL,
  action VARCHAR(80) NOT NULL,
  before_json JSON NULL,
  after_json JSON NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  KEY idx_audit_logs_actor (actor_user_id),
  KEY idx_audit_logs_pool (pool_id),
  KEY idx_audit_logs_entity (entity_type, entity_id),
  CONSTRAINT fk_audit_logs_actor FOREIGN KEY (actor_user_id) REFERENCES users (id),
  CONSTRAINT fk_audit_logs_pool FOREIGN KEY (pool_id) REFERENCES pools (id)
);
