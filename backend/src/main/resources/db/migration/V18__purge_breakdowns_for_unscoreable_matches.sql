-- Purge score_breakdowns belonging to matches that are no longer in a scoreable
-- state (result withdrawn / match reopened). This catches the case V17 missed:
-- a match was briefly CLOSED with a 0-0 result, got scored, then reverted to OPEN
-- with NULL goals — but its breakdowns survived because recalculateAll only ever
-- revisits matches that currently have a result. Those stale rows inflated the ranking
-- (e.g. USA-Paraguay scored as 0-0 while the match has not been played yet).
--
-- Root cause fixed in ScoringService.recalculateAll (now deletes breakdowns for
-- matches outside the scoreable set before recomputing). This migration heals
-- the existing data deterministically on deploy.
-- Idempotent: nothing to delete once data is clean.

DELETE FROM score_breakdowns
WHERE match_id IS NOT NULL
  AND match_id NOT IN (
    SELECT id FROM matches
    WHERE home_goals IS NOT NULL AND away_goals IS NOT NULL
      AND status IN ('CLOSED', 'FINISHED')
  );
