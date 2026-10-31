package dev.friendrun;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.Random;
import org.junit.jupiter.api.Test;

final class DraftPlusChaosTest {
    @Test
    void disabledChaosReturnsNoModifiers() {
        assertTrue(DraftPlusChaos.roll(false, 3, 2, new Random(1)).isEmpty());
    }

    @Test
    void fixedCountRollsDistinctModifiers() {
        for (int count = 1; count <= 3; count++) {
            var rolled = DraftPlusChaos.roll(true, count, 1, new Random(100 + count));
            assertEquals(count, rolled.size());
            assertEquals(count, new HashSet<>(rolled).size());
        }
    }

    @Test
    void randomCountRollsOneToThreeModifiers() {
        for (int seed = 0; seed < 100; seed++) {
            var rolled = DraftPlusChaos.roll(true, 4, 0, new Random(seed));
            assertTrue(rolled.size() >= 1 && rolled.size() <= 3);
            assertEquals(rolled.size(), new HashSet<>(rolled).size());
        }
    }

    @Test
    void resourceScarcityRollIncludesOnePersistableCategory() {
        boolean found = false;
        for (int seed = 0; seed < 100; seed++) {
            var rolled = DraftPlusChaos.roll(true, 3, 2, new Random(seed));
            for (String id : rolled) {
                if (id.startsWith("resource_scarcity:")) {
                    assertTrue(java.util.Set.of("ores", "crops", "wood", "mob_drops")
                        .contains(id.substring("resource_scarcity:".length())));
                    found = true;
                }
            }
        }
        assertTrue(found);
    }
}
