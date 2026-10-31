package dev.friendrun;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Pure rules for Draft+. Kept separate from the legacy Draft implementation.
 *
 * This class deliberately contains no UI, Firebase, world, or rating code. It is the
 * deterministic rules layer that the host-side lobby flow can call and validate.
 */
public final class DraftPlusRules {
    public static final int BOARD_SIZE = 5;
    public static final int CELL_COUNT = BOARD_SIZE * BOARD_SIZE;
    public static final int WIN_CELLS = CELL_COUNT / 2 + 1;

    public enum Format { FFA, TEAM }

    /** teamIndex is -1 for FFA, otherwise 0 or 1. joinedOrder preserves lobby order. */
    public record Player(String id, int teamIndex, boolean captain, int joinedOrder) {
        public Player {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("Player id is blank");
            if (teamIndex < -1 || teamIndex > 1) throw new IllegalArgumentException("Invalid team index");
        }
    }

    private DraftPlusRules() {}

    /** Validates the selected format and returns players in stable lobby-join order. */
    public static List<Player> validateRoster(Format format, List<Player> roster) {
        Objects.requireNonNull(format, "format");
        if (roster == null || roster.isEmpty()) throw new IllegalArgumentException("Roster is empty");
        List<Player> players = roster.stream()
            .sorted(java.util.Comparator.comparingInt(Player::joinedOrder))
            .toList();
        long unique = players.stream().map(Player::id).distinct().count();
        if (unique != players.size()) throw new IllegalArgumentException("Duplicate player id");

        if (format == Format.FFA) {
            if (players.size() > 10) throw new IllegalArgumentException("FFA supports at most 10 players");
            if (players.stream().anyMatch(p -> p.teamIndex() != -1 || p.captain()))
                throw new IllegalArgumentException("FFA players cannot have teams or captain roles");
            return players;
        }

        if (players.size() < 2 || players.size() > 10 || players.size() % 2 != 0)
            throw new IllegalArgumentException("Team format requires two equal teams of 1 to 5 players");
        for (int team = 0; team < 2; team++) {
            final int teamId = team;
            List<Player> members = players.stream().filter(p -> p.teamIndex() == teamId).toList();
            if (members.isEmpty() || members.size() > 5)
                throw new IllegalArgumentException("Each team must contain 1 to 5 players");
            if (members.stream().filter(Player::captain).count() != 1)
                throw new IllegalArgumentException("Each team must have exactly one captain");
        }
        if (players.stream().anyMatch(p -> p.teamIndex() < 0))
            throw new IllegalArgumentException("Every team player must belong to a team");
        long teamA = players.stream().filter(p -> p.teamIndex() == 0).count();
        long teamB = players.stream().filter(p -> p.teamIndex() == 1).count();
        if (teamA != teamB) throw new IllegalArgumentException("Teams must have equal size");
        return players;
    }

    /** Number of bans in the agreed default rules. Lobby settings may disable bans. */
    public static int defaultBanCount(Format format) {
        return format == Format.TEAM ? 4 : 3;
    }

    /** Number of consecutive bans in one captain's turn; FFA always makes one ban per turn. */
    public static int bansPerTurn(Format format) {
        return format == Format.TEAM ? 2 : 1;
    }

    /**
     * Returns who makes a ban. firstActor is team index (0/1) for team mode,
     * or the first player's index in the stable FFA roster.
     */
    public static Player banActor(Format format, List<Player> roster, int firstActor, int banIndex) {
        List<Player> players = validateRoster(format, roster);
        if (banIndex < 0 || banIndex >= defaultBanCount(format))
            throw new IllegalArgumentException("Ban index is outside the configured default sequence");
        if (format == Format.FFA) {
            if (firstActor < 0 || firstActor >= players.size()) throw new IllegalArgumentException("Invalid first player");
            return players.get((firstActor + banIndex) % players.size());
        }
        if (firstActor < 0 || firstActor > 1) throw new IllegalArgumentException("Invalid starting team");
        int team = (firstActor + banIndex / 2) % 2;
        return players.stream().filter(p -> p.teamIndex() == team && p.captain()).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Team captain is missing"));
    }

    /** Current drafter for the next of the 25 shared-board cells. */
    public static Player drafter(Format format, List<Player> roster, int firstActor, int pickIndex) {
        List<Player> players = validateRoster(format, roster);
        if (pickIndex < 0 || pickIndex >= CELL_COUNT) throw new IllegalArgumentException("Draft is already complete");
        if (format == Format.FFA) {
            if (firstActor < 0 || firstActor >= players.size()) throw new IllegalArgumentException("Invalid first player");
            return players.get((firstActor + pickIndex) % players.size());
        }
        if (firstActor < 0 || firstActor > 1) throw new IllegalArgumentException("Invalid starting team");
        int team = (firstActor + pickIndex % 2) % 2;
        int pickWithinTeam = pickIndex / 2;
        List<Player> members = players.stream().filter(p -> p.teamIndex() == team).toList();
        return members.get(pickWithinTeam % members.size());
    }

    /** Full deterministic pick sequence, useful for previews and host-side validation. */
    public static List<Player> draftOrder(Format format, List<Player> roster, int firstActor) {
        List<Player> order = new ArrayList<>(CELL_COUNT);
        for (int pick = 0; pick < CELL_COUNT; pick++)
            order.add(drafter(format, roster, firstActor, pick));
        return List.copyOf(order);
    }

    /**
     * Returns the winner token after a board is complete or a side reaches 13 cells.
     * FFA token: "player:<id>"; team token: "team:<0|1>".
     * Empty means no winner yet.
     */
    public static Optional<String> winner(Format format, List<Player> roster, List<String> ownerIds) {
        List<Player> players = validateRoster(format, roster);
        if (ownerIds == null || ownerIds.size() > CELL_COUNT)
            throw new IllegalArgumentException("Invalid board ownership list");
        if (format == Format.FFA) {
            for (Player player : players) {
                long owned = ownerIds.stream().filter(player.id()::equals).count();
                if (owned >= WIN_CELLS) return Optional.of("player:" + player.id());
            }
        } else {
            for (int team = 0; team < 2; team++) {
                final int teamId = team;
                long owned = ownerIds.stream().filter(owner -> players.stream()
                    .anyMatch(p -> p.id().equals(owner) && p.teamIndex() == teamId)).count();
                if (owned >= WIN_CELLS) return Optional.of("team:" + team);
            }
        }
        return Optional.empty();
    }
}
