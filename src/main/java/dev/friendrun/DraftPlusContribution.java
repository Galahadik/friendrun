package dev.friendrun;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Deterministic, anti-farm personal-contribution scoring for Draft+.
 * Only distinct item IDs count: repeatedly collecting the same stack cannot
 * increase the score. The final adjustment is relative to the match average
 * and is always clamped to the agreed ±5 MMR range.
 */
public final class DraftPlusContribution {
    private static final Set<String> RARE_RESOURCES = Set.of(
        "diamond", "emerald", "ancient_debris", "netherite_scrap", "netherite_ingot",
        "amethyst_shard", "echo_shard", "heart_of_the_sea", "nautilus_shell",
        "prismarine_shard", "prismarine_crystals", "wither_skeleton_skull"
    );
    private static final Set<String> KEY_ITEMS = Set.of(
        "blaze_rod", "ender_pearl", "eye_of_ender", "ghast_tear", "nether_star",
        "elytra", "shulker_shell", "totem_of_undying", "trident", "dragon_egg",
        "dragon_breath", "wither_skeleton_skull", "heavy_core", "mace",
        "trial_key", "ominous_trial_key", "recovery_compass", "conduit"
    );

    public record Metrics(Set<String> acquiredItems, int cellsCaptured) {
        public Metrics {
            acquiredItems = acquiredItems == null ? Set.of() : Set.copyOf(acquiredItems);
            cellsCaptured = Math.max(0, Math.min(DraftPlusRules.CELL_COUNT, cellsCaptured));
        }
    }

    private DraftPlusContribution() {}

    /** IDs that should be included in the client's match-long inventory baseline. */
    public static Set<String> trackedItemIds() {
        Set<String> ids = new HashSet<>(RARE_RESOURCES);
        ids.addAll(KEY_ITEMS);
        return Set.copyOf(ids);
    }

    /** One point per distinct rare resource, two per distinct key item, one per captured cell. */
    public static int score(Metrics metrics) {
        int score = metrics.cellsCaptured();
        Set<String> counted = new HashSet<>();
        for (String id : metrics.acquiredItems()) {
            if (id == null || id.isBlank() || !counted.add(id)) continue;
            if (KEY_ITEMS.contains(id)) score += 2;
            else if (RARE_RESOURCES.contains(id) || ItemPicker.isHard(id)) score += 1;
        }
        return score;
    }

    /**
     * Convert match-relative contribution scores to MMR adjustments. A score
     * equal to the match average yields zero; each point above/below average
     * shifts the adjustment by one MMR, capped at ±5.
     */
    public static Map<String, Integer> adjustments(Map<String, Metrics> metricsByPlayer) {
        if (metricsByPlayer == null || metricsByPlayer.isEmpty()) return Map.of();
        Map<String, Integer> scores = new HashMap<>();
        int total = 0;
        for (Map.Entry<String, Metrics> entry : metricsByPlayer.entrySet()) {
            int value = score(entry.getValue() == null ? new Metrics(Set.of(), 0) : entry.getValue());
            scores.put(entry.getKey(), value);
            total += value;
        }
        double average = total / (double) scores.size();
        Map<String, Integer> result = new HashMap<>();
        for (Map.Entry<String, Integer> entry : scores.entrySet()) {
            int adjustment = (int) Math.round(entry.getValue() - average);
            result.put(entry.getKey(), Math.max(-DraftPlusRating.MAX_CONTRIBUTION,
                Math.min(DraftPlusRating.MAX_CONTRIBUTION, adjustment)));
        }
        return Map.copyOf(result);
    }
}
