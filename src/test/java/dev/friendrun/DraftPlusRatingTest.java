package dev.friendrun;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

final class DraftPlusRatingTest {
    @Test
    void ffaComparesEveryPlayerAndKeepsBaseEloZeroSum() {
        List<DraftPlusRating.PlayerResult> players = List.of(
            DraftPlusRating.PlayerResult.fresh("alice", 20, 0),
            DraftPlusRating.PlayerResult.fresh("bob", 5, 0),
            DraftPlusRating.PlayerResult.fresh("cara", 0, 0)
        );

        List<DraftPlusRating.Delta> deltas = DraftPlusRating.calculateFfa(players);
        Map<String, DraftPlusRating.Delta> byId = deltas.stream()
            .collect(Collectors.toMap(DraftPlusRating.Delta::playerId, Function.identity()));

        assertTrue(byId.get("alice").baseDelta() > 0);
        assertTrue(byId.get("bob").baseDelta() == 0);
        assertTrue(byId.get("cara").baseDelta() < 0);
        assertEquals(0, deltas.stream().mapToInt(DraftPlusRating.Delta::baseDelta).sum());
    }

    @Test
    void equalRatingFfaDrawDoesNotChangeBaseMmr() {
        List<DraftPlusRating.Delta> deltas = DraftPlusRating.calculateFfa(List.of(
            DraftPlusRating.PlayerResult.fresh("alice", 12, 0),
            DraftPlusRating.PlayerResult.fresh("bob", 12, 0)
        ));

        assertEquals(0, deltas.get(0).baseDelta());
        assertEquals(0, deltas.get(1).baseDelta());
    }

    @Test
    void ffaUsesExpectedScoreFromEachOpponentsMmr() {
        List<DraftPlusRating.Delta> deltas = DraftPlusRating.calculateFfa(List.of(
            new DraftPlusRating.PlayerResult("alice", 1400, 10, 0),
            new DraftPlusRating.PlayerResult("bob", 1000, 5, 0),
            new DraftPlusRating.PlayerResult("cara", 1000, 5, 0)
        ));
        Map<String, DraftPlusRating.Delta> byId = deltas.stream()
            .collect(Collectors.toMap(DraftPlusRating.Delta::playerId, Function.identity()));

        assertTrue(byId.get("alice").baseDelta() > 0);
        assertTrue(byId.get("bob").baseDelta() < 0);
        assertTrue(byId.get("cara").baseDelta() < 0);
    }

    @Test
    void teamUsesAverageTeamMmrAndSameBaseDeltaForTeammates() {
        List<DraftPlusRating.PlayerResult> teamA = List.of(
            new DraftPlusRating.PlayerResult("a1", 1200, 13, 0),
            new DraftPlusRating.PlayerResult("a2", 800, 0, 0)
        );
        List<DraftPlusRating.PlayerResult> teamB = List.of(
            new DraftPlusRating.PlayerResult("b1", 1000, 10, 0),
            new DraftPlusRating.PlayerResult("b2", 1000, 0, 0)
        );

        List<DraftPlusRating.Delta> deltas =
            DraftPlusRating.calculateTeam(teamA, teamB, DraftPlusRating.TeamResult.A_WIN);
        Map<String, DraftPlusRating.Delta> byId = deltas.stream()
            .collect(Collectors.toMap(DraftPlusRating.Delta::playerId, Function.identity()));

        assertEquals(byId.get("a1").baseDelta(), byId.get("a2").baseDelta());
        assertEquals(byId.get("b1").baseDelta(), byId.get("b2").baseDelta());
        assertEquals(-byId.get("a1").baseDelta(), byId.get("b1").baseDelta());
        assertEquals(0, deltas.stream().mapToInt(DraftPlusRating.Delta::baseDelta).sum());
    }

    @Test
    void personalContributionIsAppliedAfterTeamResultAndCappedAtFive() {
        List<DraftPlusRating.Delta> deltas = DraftPlusRating.calculateTeam(
            List.of(
                new DraftPlusRating.PlayerResult("a1", 1000, 13, 99),
                new DraftPlusRating.PlayerResult("a2", 1000, 13, -99)
            ),
            List.of(
                new DraftPlusRating.PlayerResult("b1", 1000, 12, 0),
                new DraftPlusRating.PlayerResult("b2", 1000, 12, 0)
            ),
            DraftPlusRating.TeamResult.A_WIN
        );
        Map<String, DraftPlusRating.Delta> byId = deltas.stream()
            .collect(Collectors.toMap(DraftPlusRating.Delta::playerId, Function.identity()));

        assertEquals(5, byId.get("a1").contributionDelta());
        assertEquals(-5, byId.get("a2").contributionDelta());
        assertEquals(byId.get("a1").baseDelta() + 5, byId.get("a1").delta());
        assertEquals(byId.get("a2").baseDelta() - 5, byId.get("a2").delta());
    }

    @Test
    void trainingOrInvalidFfaSizeMustNotBeSettledByRatingCalculator() {
        assertThrows(IllegalArgumentException.class,
            () -> DraftPlusRating.calculateFfa(List.of(DraftPlusRating.PlayerResult.fresh("solo", 0, 0))));
    }

    @Test
    void teamRejectsUnequalSizesAndDuplicatePlayers() {
        assertThrows(IllegalArgumentException.class, () -> DraftPlusRating.calculateTeam(
            List.of(DraftPlusRating.PlayerResult.fresh("a", 13, 0)),
            List.of(DraftPlusRating.PlayerResult.fresh("b1", 0, 0),
                    DraftPlusRating.PlayerResult.fresh("b2", 0, 0)),
            DraftPlusRating.TeamResult.A_WIN));
        assertThrows(IllegalArgumentException.class, () -> DraftPlusRating.calculateTeam(
            List.of(DraftPlusRating.PlayerResult.fresh("same", 13, 0)),
            List.of(DraftPlusRating.PlayerResult.fresh("same", 0, 0)),
            DraftPlusRating.TeamResult.A_WIN));
    }

    @Test
    void mmrNeverFallsBelowZero() {
        List<DraftPlusRating.Delta> deltas = DraftPlusRating.calculateFfa(List.of(
            new DraftPlusRating.PlayerResult("winner", 1000, 20, 0),
            new DraftPlusRating.PlayerResult("loser", 1, 0, -5)
        ));
        DraftPlusRating.Delta loser = deltas.stream()
            .filter(delta -> delta.playerId().equals("loser")).findFirst().orElseThrow();
        assertEquals(0, loser.newMmr());
        assertEquals(-1, loser.delta());
    }
}
