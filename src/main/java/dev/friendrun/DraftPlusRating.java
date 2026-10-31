package dev.friendrun;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Isolated Elo calculations for Draft+ FFA and Team.
 *
 * This class intentionally does not read or write the legacy Ranking tables.
 * The caller supplies each player's Draft+-specific MMR and a precomputed
 * personal-contribution adjustment; this class clamps that adjustment to ±5.
 */
public final class DraftPlusRating {
    public static final int START_MMR = 1000;
    public static final int K_FACTOR = 32;
    public static final int MAX_CONTRIBUTION = 5;
    private static final double ELO_DIVISOR = 400.0;

    public enum TeamResult { A_WIN, DRAW, B_WIN }

    /** contributionAdjustment is the caller's raw individual adjustment before the ±5 cap. */
    public record PlayerResult(String playerId, int mmr, int cells, int contributionAdjustment) {
        public PlayerResult {
            if (playerId == null || playerId.isBlank())
                throw new IllegalArgumentException("Player id is blank");
            if (mmr < 0) throw new IllegalArgumentException("MMR cannot be negative");
            if (cells < 0 || cells > DraftPlusRules.CELL_COUNT)
                throw new IllegalArgumentException("Invalid number of captured cells");
        }

        public static PlayerResult fresh(String playerId, int cells, int contributionAdjustment) {
            return new PlayerResult(playerId, START_MMR, cells, contributionAdjustment);
        }
    }

    public record Delta(String playerId, int oldMmr, int baseDelta,
                        int contributionDelta, int delta, int newMmr) {}

    private DraftPlusRating() {}

    /**
     * FFA Elo: every player is compared with every other player. The pairwise
     * actual score is 1/0/0.5 according to captured-cell counts. K is shared
     * across all opponents by dividing each pair's weight by (players - 1).
     */
    public static List<Delta> calculateFfa(List<PlayerResult> players) {
        return calculateFfa(players, K_FACTOR);
    }

    public static List<Delta> calculateFfa(List<PlayerResult> players, int kFactor) {
        validatePlayers(players, 2, 10);
        validateK(kFactor);

        int size = players.size();
        double[] raw = new double[size];
        for (int i = 0; i < size; i++) {
            PlayerResult a = players.get(i);
            for (int j = i + 1; j < size; j++) {
                PlayerResult b = players.get(j);
                double actualA = score(a.cells(), b.cells());
                double expectedA = expected(a.mmr(), b.mmr());
                double pairDelta = (kFactor / (double) (size - 1)) * (actualA - expectedA);
                raw[i] += pairDelta;
                raw[j] -= pairDelta;
            }
        }

        int[] base = roundZeroSum(raw);
        List<Delta> result = new ArrayList<>(size);
        for (int i = 0; i < size; i++) result.add(delta(players.get(i), base[i]));
        return List.copyOf(result);
    }

    /**
     * Team Elo: teams must be equally sized. Expected score uses the average
     * MMR of each team. Every teammate receives the same base team delta;
     * individual contribution is applied afterward and capped to ±5.
     */
    public static List<Delta> calculateTeam(
        List<PlayerResult> teamA, List<PlayerResult> teamB, TeamResult result
    ) {
        return calculateTeam(teamA, teamB, result, K_FACTOR);
    }

    public static List<Delta> calculateTeam(
        List<PlayerResult> teamA, List<PlayerResult> teamB, TeamResult result, int kFactor
    ) {
        Objects.requireNonNull(result, "result");
        validatePlayers(teamA, 1, 5);
        validatePlayers(teamB, 1, 5);
        validateK(kFactor);
        if (teamA.size() != teamB.size())
            throw new IllegalArgumentException("Team sizes must match");

        Set<String> ids = new HashSet<>();
        for (PlayerResult player : teamA) ids.add(player.playerId());
        for (PlayerResult player : teamB)
            if (!ids.add(player.playerId())) throw new IllegalArgumentException("Duplicate player id");

        double averageA = teamA.stream().mapToInt(PlayerResult::mmr).average().orElse(START_MMR);
        double averageB = teamB.stream().mapToInt(PlayerResult::mmr).average().orElse(START_MMR);
        double actualA = switch (result) {
            case A_WIN -> 1.0;
            case DRAW -> 0.5;
            case B_WIN -> 0.0;
        };
        int baseA = (int) Math.round(kFactor * (actualA - expected(averageA, averageB)));
        int baseB = -baseA;

        List<Delta> deltas = new ArrayList<>(teamA.size() + teamB.size());
        for (PlayerResult player : teamA) deltas.add(delta(player, baseA));
        for (PlayerResult player : teamB) deltas.add(delta(player, baseB));
        return List.copyOf(deltas);
    }

    private static Delta delta(PlayerResult player, int baseDelta) {
        int contribution = Math.max(-MAX_CONTRIBUTION,
            Math.min(MAX_CONTRIBUTION, player.contributionAdjustment()));
        int proposed = baseDelta + contribution;
        int newMmr = Math.max(0, player.mmr() + proposed);
        return new Delta(player.playerId(), player.mmr(), baseDelta, contribution,
            newMmr - player.mmr(), newMmr);
    }

    private static double expected(double mine, double theirs) {
        return 1.0 / (1.0 + Math.pow(10.0, (theirs - mine) / ELO_DIVISOR));
    }

    private static double score(int mine, int theirs) {
        return mine > theirs ? 1.0 : mine < theirs ? 0.0 : 0.5;
    }

    /** Rounds individual FFA deltas while preserving a zero-sum base Elo result. */
    private static int[] roundZeroSum(double[] raw) {
        int[] rounded = new int[raw.length];
        int sum = 0;
        for (int i = 0; i < raw.length; i++) {
            rounded[i] = (int) Math.round(raw[i]);
            sum += rounded[i];
        }
        if (sum == 0) return rounded;

        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < raw.length; i++) order.add(i);
        if (sum > 0) {
            order.sort(Comparator.<Integer>comparingDouble(i -> rounded[i] - raw[i]).reversed());
            for (int index : order) {
                if (sum == 0) break;
                rounded[index]--;
                sum--;
            }
        } else {
            order.sort(Comparator.<Integer>comparingDouble(i -> raw[i] - rounded[i]).reversed());
            for (int index : order) {
                if (sum == 0) break;
                rounded[index]++;
                sum++;
            }
        }
        return rounded;
    }

    private static void validatePlayers(List<PlayerResult> players, int min, int max) {
        Objects.requireNonNull(players, "players");
        if (players.size() < min || players.size() > max)
            throw new IllegalArgumentException("Invalid player count");
        Set<String> ids = new HashSet<>();
        for (PlayerResult player : players) {
            Objects.requireNonNull(player, "player");
            if (!ids.add(player.playerId())) throw new IllegalArgumentException("Duplicate player id");
        }
    }

    private static void validateK(int kFactor) {
        if (kFactor <= 0) throw new IllegalArgumentException("K-factor must be positive");
    }
}
