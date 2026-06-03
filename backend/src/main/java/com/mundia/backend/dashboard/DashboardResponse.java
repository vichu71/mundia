package com.mundia.backend.dashboard;

import java.util.List;

public record DashboardResponse(
        List<PoolDto> pools,
        List<MatchDto> matches,
        List<RankingDto> ranking,
        List<InitialRankingDto> initialRanking,
        List<PrizeRowDto> prizeRows,
        List<BracketRoundDto> bracketRounds,
        List<PendingPaymentDto> pendingPayments,
        List<RecommendationDto> recommendations
) {
    public record PoolDto(
            long id,
            String name,
            String code,
            String status,
            String statusType,
            String userRole,
            int members,
            int paid,
            int pot
    ) {
    }

    public record MatchDto(
            long id,
            String home,
            String away,
            String homeFl,
            String awayFl,
            String pred,
            String real,
            Integer points,
            String status,
            String statusType,
            String note,
            String kickoff,
            String source
    ) {
    }

    public record RankingDto(
            int pos,
            String name,
            String avatar,
            int points,
            int exact,
            int winners,
            int prize,
            String delta,
            boolean alive
    ) {
    }

    public record InitialRankingDto(
            int pos,
            String name,
            int points,
            int exact,
            int winners,
            String bonus
    ) {
    }

    public record PrizeRowDto(
            String label,
            int amount,
            String state,
            String stateType,
            int contenders,
            int pct
    ) {
    }

    public record BracketRoundDto(String name, List<BracketMatchDto> matches) {
    }

    public record BracketMatchDto(
            String home,
            String homeFl,
            String away,
            String awayFl,
            String pred,
            String real,
            String winner,
            boolean done
    ) {
    }

    public record RecommendationDto(String type, String text) {
    }

    public record PendingPaymentDto(long id, String name) {
    }
}
