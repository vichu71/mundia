-- Purge orphaned score_breakdowns: rows tied to a match whose corresponding
-- match_prediction no longer exists in the same prediction_set. These inflated
-- both the general ranking (LIVE sets) and the initial-bet ranking (INITIAL sets),
-- because findRanking/findInitialRanking sum breakdowns directly, while the closed-
-- predictions modal only counts matches with a live prediction.
--
-- Root cause fixed in code (ScoringService.calculateForMatch now deletes all
-- breakdowns per match before re-inserting; PredictionController.deleteAll now
-- clears INITIAL breakdowns too). This migration heals the existing data.
-- Idempotent: after a clean recalculation there is nothing left to delete.

DELETE sb FROM score_breakdowns sb
LEFT JOIN match_predictions mp
  ON mp.prediction_set_id = sb.prediction_set_id
 AND mp.match_id = sb.match_id
WHERE sb.match_id IS NOT NULL
  AND mp.id IS NULL;
