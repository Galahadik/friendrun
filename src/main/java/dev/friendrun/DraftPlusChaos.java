package dev.friendrun;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootTable;

/** Host-integrated-server effects for the five agreed Draft+ chaos modifiers. */
public final class DraftPlusChaos {
    public static final List<String> MODIFIER_IDS = List.of(
        "rich_harvest", "iron_fever", "dangerous_night", "fast_craft", "resource_scarcity"
    );

    private static final Identifier MOB_HEALTH = Identifier.fromNamespaceAndPath("friendrun", "draft_plus_chaos_health");
    private static final Identifier MOB_DAMAGE = Identifier.fromNamespaceAndPath("friendrun", "draft_plus_chaos_damage");
    private static volatile Snapshot snapshot = Snapshot.EMPTY;
    private static Snapshot lastServerSnapshot = Snapshot.EMPTY;
    private static MinecraftServer trackedServer;

    private record Snapshot(boolean active, List<String> modifiers, int difficulty) {
        private static final Snapshot EMPTY = new Snapshot(false, List.of(), 0);
        private Snapshot {
            modifiers = List.copyOf(modifiers);
            difficulty = Math.max(0, Math.min(2, difficulty));
        }
        boolean has(String id) {
            return active && modifiers.stream().anyMatch(value -> value.equals(id) || value.startsWith(id + ":"));
        }
    }

    private DraftPlusChaos() {}

    public static void register() {
        PlayerBlockBreakEvents.AFTER.register(DraftPlusChaos::afterBlockBreak);
        LootTableEvents.MODIFY_DROPS.register(DraftPlusChaos::modifyDrops);
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> syncHostileMob(entity, snapshot));
        ServerTickEvents.END_SERVER_TICK.register(DraftPlusChaos::syncLoadedMobs);
    }

    /** Publish only the host-frozen modifier list; clients never roll or choose effects. */
    public static void publish(RunState state) {
        boolean active = state.launched && state.gameMode == Game.DRAFT_PLUS
            && state.roundGame == Game.DRAFT_PLUS
            && (state.phase == RunState.Phase.COUNTDOWN || state.phase == RunState.Phase.RUNNING);
        snapshot = active
            ? new Snapshot(state.draftPlusChaosEnabled, state.draftPlusChaosModifiers, state.draftPlusChaosDifficulty)
            : Snapshot.EMPTY;
    }

    public static void clear() {
        snapshot = Snapshot.EMPTY;
    }

    public static String displayName(String id) {
        if (id == null) return "";
        if (id.startsWith("resource_scarcity:")) {
            String category = id.substring("resource_scarcity:".length());
            String label = Tr.t("friendrun.draft_plus.chaos.category." + category, "ресурсы");
            return Tr.t("friendrun.draft_plus.chaos.resource_scarcity", "Дефицит ресурсов")
                + " (" + label + ")";
        }
        return switch (id) {
            case "rich_harvest", "iron_fever", "dangerous_night", "fast_craft", "resource_scarcity" ->
                Tr.t("friendrun.draft_plus.chaos." + id, id);
            default -> id;
        };
    }

    public static List<String> roll(boolean enabled, int countSetting, int difficulty, java.util.Random random) {
        if (!enabled) return List.of();
        int count = countSetting == 4 ? 1 + random.nextInt(3) : Math.max(1, Math.min(3, countSetting));
        java.util.ArrayList<String> pool = new java.util.ArrayList<>(MODIFIER_IDS);
        java.util.Collections.shuffle(pool, random);
        List<String> selected = new java.util.ArrayList<>(pool.subList(0, Math.min(count, pool.size())));
        if (selected.contains("resource_scarcity")) {
            String[] categories = { "ores", "crops", "wood", "mob_drops" };
            selected.set(selected.indexOf("resource_scarcity"),
                "resource_scarcity:" + categories[random.nextInt(categories.length)]);
        }
        return List.copyOf(selected);
    }

    private static int levelBonus(Snapshot current) {
        return current.difficulty() + 1;
    }

    private static void afterBlockBreak(Level level, Player player, BlockPos pos,
                                        BlockState state, net.minecraft.world.level.block.entity.BlockEntity blockEntity) {
        Snapshot current = snapshot;
        if (!current.has("rich_harvest") || !(level instanceof ServerLevel serverLevel)
            || player.isSpectator() || !(state.getBlock() instanceof CropBlock crop)) return;
        if (crop.getAge(state) < crop.getMaxAge()) return;

        net.minecraft.world.item.Item produce = state.is(Blocks.WHEAT) ? Items.WHEAT
            : state.is(Blocks.CARROTS) ? Items.CARROT
            : state.is(Blocks.POTATOES) ? Items.POTATO
            : state.is(Blocks.BEETROOTS) ? Items.BEETROOT : null;
        if (produce == null) return;
        spawnDrop(serverLevel, pos, new ItemStack(produce, levelBonus(current)));
    }

    private static void modifyDrops(net.minecraft.core.Holder<LootTable> holder,
                                    net.minecraft.world.level.storage.loot.LootContext context,
                                    List<ItemStack> drops) {
        Snapshot current = snapshot;
        if (!current.active()) return;
        if (current.has("iron_fever")
            && (holder.is(Blocks.IRON_ORE.getLootTable().orElseThrow())
                || holder.is(Blocks.DEEPSLATE_IRON_ORE.getLootTable().orElseThrow()))
            && drops.stream().anyMatch(stack -> stack.is(Items.RAW_IRON))) {
            drops.add(new ItemStack(Items.RAW_IRON, levelBonus(current)));
        }
        if (current.has("resource_scarcity")) {
            String category = current.modifiers().stream()
                .filter(id -> id.startsWith("resource_scarcity:"))
                .map(id -> id.substring("resource_scarcity:".length())).findFirst().orElse("");
            if (matchesScarcityTable(holder, category)) {
                double reduction = switch (current.difficulty()) {
                    case 0 -> 0.10;
                    case 1 -> 0.20;
                    default -> 0.30;
                };
                for (ItemStack stack : drops) {
                    int original = stack.getCount();
                    int removed = 0;
                    for (int i = 0; i < original; i++)
                        if (ThreadLocalRandom.current().nextDouble() < reduction) removed++;
                    if (removed > 0) stack.shrink(removed);
                }
                drops.removeIf(ItemStack::isEmpty);
            }
        }
    }

    private static boolean matchesScarcityTable(net.minecraft.core.Holder<LootTable> holder, String category) {
        var key = holder.unwrapKey().orElse(null);
        if (key == null) return false;
        String path = key.identifier().getPath();
        return switch (category) {
            case "ores" -> path.contains("_ore");
            case "crops" -> path.contains("wheat") || path.contains("carrot")
                || path.contains("potato") || path.contains("beetroot") || path.contains("nether_wart")
                || path.contains("sweet_berry");
            case "wood" -> path.contains("log") || path.contains("wood")
                || path.contains("stem") || path.contains("hyphae");
            case "mob_drops" -> path.startsWith("entities/");
            default -> false;
        };
    }

    private static void spawnDrop(ServerLevel level, BlockPos pos, ItemStack stack) {
        if (stack.isEmpty()) return;
        level.addFreshEntity(new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5,
            pos.getZ() + 0.5, stack));
    }

    private static void syncLoadedMobs(MinecraftServer server) {
        Snapshot current = snapshot;
        if (trackedServer != server) {
            trackedServer = server;
            lastServerSnapshot = Snapshot.EMPTY;
        }
        if (current.equals(lastServerSnapshot)) return;
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) syncHostileMob(entity, current);
        }
        lastServerSnapshot = current;
    }

    private static void syncHostileMob(Entity entity, Snapshot current) {
        if (!(entity instanceof Monster monster)) return;
        boolean enabled = current.has("dangerous_night");
        updateModifier(monster.getAttribute(Attributes.MAX_HEALTH), MOB_HEALTH, enabled ? current.difficulty() : -1);
        updateModifier(monster.getAttribute(Attributes.ATTACK_DAMAGE), MOB_DAMAGE, enabled ? current.difficulty() : -1);
    }

    private static void updateModifier(AttributeInstance attribute, Identifier id, int difficulty) {
        if (attribute == null) return;
        if (difficulty < 0) {
            attribute.removeModifier(id);
            return;
        }
        double amount = switch (difficulty) {
            case 0 -> 0.10;
            case 1 -> 0.20;
            default -> 0.35;
        };
        attribute.addOrUpdateTransientModifier(new AttributeModifier(
            id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    /** Called by the ResultSlot mixin after a player takes a crafted result. */
    public static void onCrafted(Player player, ItemStack crafted) {
        Snapshot current = snapshot;
        if (!current.has("fast_craft") || !(player instanceof ServerPlayer serverPlayer)
            || crafted == null || crafted.isEmpty()) return;
        double chance = switch (current.difficulty()) {
            case 0 -> 0.10;
            case 1 -> 0.20;
            default -> 0.30;
        };
        if (serverPlayer.getRandom().nextFloat() >= chance) return;
        ItemStack bonus = crafted.copy();
        if (!serverPlayer.getInventory().add(bonus)
            && serverPlayer.level() instanceof ServerLevel serverLevel)
            spawnDrop(serverLevel, serverPlayer.blockPosition(), bonus);
    }
}
