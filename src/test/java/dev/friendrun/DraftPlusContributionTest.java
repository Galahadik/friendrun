package dev.friendrun;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class DraftPlusContributionTest {
    @Test
    void distinctRareAndKeyItemsHaveDifferentWeights() {
        assertEquals(1, DraftPlusContribution.score(new DraftPlusContribution.Metrics(
            Set.of("diamond"), 0)));
        assertEquals(2, DraftPlusContribution.score(new DraftPlusContribution.Metrics(
            Set.of("blaze_rod"), 0)));
        assertEquals(3, DraftPlusContribution.score(new DraftPlusContribution.Metrics(
            Set.of("diamond", "blaze_rod"), 0)));
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
    void emptyMetricsProduceNeutralAdjustment() {
        assertEquals(Map.of("a", 0, "b", 0), DraftPlusContribution.adjustments(Map.of(
            "a", new DraftPlusContribution.Metrics(Set.of(), 0),
            "b", new DraftPlusContribution.Metrics(Set.of(), 0)
        )));
    }
}
