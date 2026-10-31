package dev.friendrun;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class DraftPlusContributionTest {
    @Test
    void distinctRareAndKeyItemsHaveEqualInitialWeight() {
        assertEquals(1, DraftPlusContribution.score(new DraftPlusContribution.Metrics(
            Set.of("diamond"), 0)));
        assertEquals(1, DraftPlusContribution.score(new DraftPlusContribution.Metrics(
            Set.of("blaze_rod"), 0)));
        assertEquals(2, DraftPlusContribution.score(new DraftPlusContribution.Metrics(
            Set.of("diamond", "blaze_rod"), 0)));
    }

    @Test
    void successfulDraftPicksContributeToPersonalScore() {
        assertEquals(5, DraftPlusContribution.score(
            new DraftPlusContribution.Metrics(Set.of(), 2, 3)));
    }

    @Test
    void capturedCellsContributeToPersonalScore() {
        assertEquals(4, DraftPlusContribution.score(new DraftPlusContribution.Metrics(
            Set.of("diamond"), 3)));
    }

    @Test
    void adjustmentsAreRelativeToMatchAverage() {
        Map<String, Integer> result = DraftPlusContribution.adjustments(Map.of(
            "a", new DraftPlusContribution.Metrics(Set.of("diamond", "emerald"), 4),
            "b", new DraftPlusContribution.Metrics(Set.of(), 0)
        ));
        assertEquals(3, result.get("a"));
        assertEquals(-3, result.get("b"));
    }

    @Test
    void contributionIsCappedAtFive() {
        Map<String, Integer> result = DraftPlusContribution.adjustments(Map.of(
            "high", new DraftPlusContribution.Metrics(
                Set.of("diamond", "emerald", "ancient_debris", "netherite_ingot",
                    "blaze_rod", "ender_pearl", "eye_of_ender", "ghast_tear"), 25),
            "low", new DraftPlusContribution.Metrics(Set.of(), 0)
        ));
        assertEquals(5, result.get("high"));
        assertEquals(-5, result.get("low"));
    }

    @Test
    void ffaSuccessfulPickRequiresThePickerToCaptureThatCell() {
        List<DraftPlusRules.Player> players = List.of(
            new DraftPlusRules.Player("alice", -1, false, 0),
            new DraftPlusRules.Player("bob", -1, false, 1)
        );
        DraftPlusFlow.State state = new DraftPlusFlow.State(DraftPlusRules.Format.FFA, players,
            List.of(), List.of(), List.of("goal:a", "goal:b"), List.of("alice", "bob"),
            List.of("alice", "alice"), List.of(), DraftPlusFlow.Phase.PLAY,
            0, 0, 0, 25, "", "", "", "");
        assertEquals(Map.of("alice", 1), DraftPlusContribution.successfulDraftPicks(state));
    }

    @Test
    void teamSuccessfulPickCreditsThePickerWhenAnyTeammateCapturesIt() {
        List<DraftPlusRules.Player> players = List.of(
            new DraftPlusRules.Player("a1", DraftPlusTeams.TEAM_A, false, 0),
            new DraftPlusRules.Player("a2", DraftPlusTeams.TEAM_A, false, 1),
            new DraftPlusRules.Player("b1", DraftPlusTeams.TEAM_B, false, 2),
            new DraftPlusRules.Player("b2", DraftPlusTeams.TEAM_B, false, 3)
        );
        DraftPlusFlow.State state = new DraftPlusFlow.State(DraftPlusRules.Format.TEAM, players,
            List.of(), List.of(), List.of("goal:a", "goal:b"), List.of("a1", "b1"),
            List.of("a2", "b2"), List.of(), DraftPlusFlow.Phase.PLAY,
            0, 0, 0, 25, "", "", "", "");
        assertEquals(Map.of("a1", 1, "b1", 1), DraftPlusContribution.successfulDraftPicks(state));
    }

    @Test
    void emptyMetricsProduceNeutralAdjustment() {
        assertEquals(Map.of("a", 0, "b", 0), DraftPlusContribution.adjustments(Map.of(
            "a", new DraftPlusContribution.Metrics(Set.of(), 0),
            "b", new DraftPlusContribution.Metrics(Set.of(), 0)
        )));
    }
}
