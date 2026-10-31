package dev.friendrun;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

/**
 * Applies the host-frozen Draft+ role assignments on the integrated server.
 * The immutable snapshot is published by the client thread and consumed by
 * the server thread, so remote players in the host's world receive the same
 * authoritative modifiers as players in their own worlds.
 */
public final class DraftPlusRoles {
    private record Snapshot(boolean active, Map<String, String> roles) {
        private Snapshot {
            roles = Map.copyOf(roles);
        }
    }

    private static volatile Snapshot snapshot = new Snapshot(false, Map.of());
    private static final Map<UUID, String> APPLIED = new HashMap<>();
    private static MinecraftServer trackedServer;

    private DraftPlusRoles() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(DraftPlusRoles::tickServer);
    }

    /** Called on the client thread after Firebase state or local match phase changes. */
    public static void publish(RunState state) {
        boolean active = state.launched && state.gameMode == Game.DRAFT_PLUS
            && state.roundGame == Game.DRAFT_PLUS
            && (state.phase == RunState.Phase.COUNTDOWN || state.phase == RunState.Phase.RUNNING);
        Map<String, String> roles = new HashMap<>();
        if (active) {
            for (Map.Entry<String, String> entry : state.draftPlusMatchRoles.entrySet()) {
                if (DraftPlusRole.byId(entry.getValue()) != null)
                    roles.put(entry.getKey(), entry.getValue());
            }
            // The frozen map is authoritative, but a lobby snapshot can briefly
            // arrive before its sibling draftPlusRoles field. Player roles are
            // already locked after launch, so use them as a safe short-term fallback.
            if (roles.isEmpty()) {
                for (RunState.PlayerInfo player : state.players) {
                    if (DraftPlusRole.byId(player.draftPlusRole()) != null)
                        roles.put(player.name(), player.draftPlusRole());
                }
            }
        }
        Snapshot next = new Snapshot(active, roles);
        if (!next.equals(snapshot)) snapshot = next;
    }

    public static void clear() {
        snapshot = new Snapshot(false, Map.of());
    }

    private static void tickServer(MinecraftServer server) {
        if (trackedServer != server) {
            APPLIED.clear();
            trackedServer = server;
        }

        Snapshot current = snapshot;
        Set<UUID> online = new HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID uuid = player.getUUID();
            online.add(uuid);
            String playerName = player.getScoreboardName();
            DraftPlusRole role = current.active() ? DraftPlusRole.byId(current.roles().get(playerName)) : null;
            String desired = role == null ? "" : role.id();
            String previous = APPLIED.get(uuid);
            boolean modifierMissing = role != null && !hasModifier(player, role);

            if (desired.equals(previous) && !modifierMissing) continue;

            removeRoleModifiers(player);
            if (role != null) {
                AttributeInstance attribute = player.getAttribute(role.attribute());
                if (attribute != null) {
                    attribute.addOrUpdateTransientModifier(new AttributeModifier(
                        role.modifierId(), role.amount(), role.operation()));
                }
            }

            // Removing the +4 max-health role must not leave current health above the new maximum.
            if ("guard".equals(previous) && (role == null || !role.id().equals(previous)))
                player.setHealth(Math.min(player.getHealth(), player.getMaxHealth()));

            if (role == null) APPLIED.remove(uuid);
            else APPLIED.put(uuid, desired);
        }
        APPLIED.keySet().removeIf(uuid -> !online.contains(uuid));
    }

    private static boolean hasModifier(ServerPlayer player, DraftPlusRole role) {
        AttributeInstance attribute = player.getAttribute(role.attribute());
        return attribute != null && attribute.getModifier(role.modifierId()) != null;
    }

    private static void removeRoleModifiers(ServerPlayer player) {
        for (DraftPlusRole role : DraftPlusRole.values()) {
            AttributeInstance attribute = player.getAttribute(role.attribute());
            if (attribute != null) attribute.removeModifier(role.modifierId());
        }
    }
}
