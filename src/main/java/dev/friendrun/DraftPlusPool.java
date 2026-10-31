package dev.friendrun;

import java.util.ArrayList;
import java.util.List;

/** Builds the Draft+ goal pool from the host's active item settings and shared objective catalogs. */
public final class DraftPlusPool {
    private DraftPlusPool() {}

    /**
     * Returns stable typed IDs understood by DraftGoal. Item candidates come from
     * ItemPicker.poolItems(), so the host's disabled-item and hard-item settings apply.
     */
    public static List<String> build() {
        List<String> goals = new ArrayList<>();
        for (net.minecraft.world.item.Item item : ItemPicker.poolItems())
            goals.add("item:" + ItemPicker.idOf(item));
        for (String id : DraftGoal.Quest.ids())
            goals.add("quest:" + id);
        for (String id : DraftGoal.Advancement.ids())
            goals.add("advancement:" + id);
        return List.copyOf(goals);
    }

    /** Rejects stale or malformed IDs before a goal enters a match state. */
    public static boolean isValid(String encoded) {
        DraftGoal goal = DraftGoal.parse(encoded);
        return goal != null && goal.encode().equals(encoded);
    }
}
