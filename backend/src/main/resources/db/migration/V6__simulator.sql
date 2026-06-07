-- Mark simulated users so they can be wiped cleanly
ALTER TABLE users ADD COLUMN is_sim BOOLEAN NOT NULL DEFAULT FALSE;

-- Track which simulation day we're on per pool
CREATE TABLE sim_state (
  id         BIGINT NOT NULL AUTO_INCREMENT,
  pool_id    BIGINT NOT NULL,
  sim_day    INT NOT NULL DEFAULT 0,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uq_sim_state_pool (pool_id),
  CONSTRAINT fk_sim_state_pool FOREIGN KEY (pool_id) REFERENCES pools (id) ON DELETE CASCADE
);
