package dev.friendrun;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

/**
 * Authoritative, side-effect-free Draft+ match flow.
 *
 * The host owns the Random instance and publishes each returned State to the
 * lobby. Clients only render that state and submit an actor/choice request.
 * This class does not depend on Firebase, Minecraft worlds, UI, or ratings.
 */
public final class DraftPlusFlow {
    public enum Phase { BANS, DRAFT, PLAY, COMPLETE }

    public record State(
        DraftPlusRules.Format format,
        List<DraftPlusRules.Player> players,
        List<String> availableGoals,
        List<String> bannedGoals,
        List<String> boardGoals,
        List<String> owners,
        Phase phase,
        int banIndex,
        int firstBanActor,
        int firstDraftActor,
        int pickIndex,
        String currentActor,
        String offerA,
        String offerB,
        String winner
    ) {
        public State {
            Objects.requireNonNull(format, "format");
            Objects.requireNonNull(phase, "phase");
            players = List.copyOf(players);
            availableGoals = List.copyOf(availableGoals);
            bannedGoals = List.copyOf(bannedGoals);
            boardGoals = List.copyOf(boardGoals);
            owners = List.copyOf(owners);
            currentActor = currentActor == null ? "" : currentActor;
            offerA = offerA == null ? "" : offerA;
            offerB = offerB == null ? "" : offerB;
            winner = winner == null ? "" : winner;
        }
    }

    private DraftPlusFlow() {}

    /**
     * Begins a match. Candidate IDs must be unique and must contain enough
     * goals to make 25 unique picks while presenting two options each turn.
     */
    public static State start(
        DraftPlusRules.Format format,
        List<DraftPlusRules.Player> roster,
        List<String> candidateGoals,
        boolean bansEnabled,
        Map<String, Set<String>> completedAdvancements,
        Random random
    ) {
        Objects.requireNonNull(random, "random");
        List<DraftPlusRules.Player> players = DraftPlusRules.validateRoster(format, roster);
        List<String> pool = validatePool(candidateGoals);
        int banCount = bansEnabled ? DraftPlusRules.defaultBanCount(format) : 0;
        if (pool.size() < DraftPlusRules.CELL_COUNT + banCount + 1)
            throw new IllegalArgumentException("Not enough unique goals for this match");

        if (banCount > 0) {
            int firstBanActor = format == DraftPlusRules.Format.FFA
                ? random.nextInt(players.size()) : random.nextInt(2);
            DraftPlusRules.Player actor = DraftPlusRules.banActor(format, players, firstBanActor, 0);
            String[] offers = drawOffers(pool, excludedFor(completedAdvancements, actor.id()), random);
            return new State(format, players, pool, List.of(), List.of(), List.of(),
                Phase.BANS, 0, firstBanActor, -1, 0, actor.id(), offers[0], offers[1], "");
        }
        return beginDraft(format, players, pool, List.of(), completedAdvancements, random);
    }

    /** Applies one ban. Only the currently assigned actor may ban an available goal. */
    public static State ban(State state, String actorId, String goalId, Map<String, Set<String>> completedAdvancements, Random random) {
        requirePhase(state, Phase.BANS);
        Objects.requireNonNull(random, "random");
        requireActor(state, actorId);
        if (goalId == null || (!goalId.equals(state.offerA()) && !goalId.equals(state.offerB()))
            || !state.availableGoals().contains(goalId))
            throw new IllegalArgumentException("Goal is not one of the current ban offers");

        List<String> available = new ArrayList<>(state.availableGoals());
        available.remove(goalId);
        List<String> banned = new ArrayList<>(state.bannedGoals());
        banned.add(goalId);
        int nextBan = state.banIndex() + 1;
        if (nextBan >= DraftPlusRules.defaultBanCount(state.format()))
            return beginDraft(state.format(), state.players(), available, banned, completedAdvancements, random);

        DraftPlusRules.Player next = DraftPlusRules.banActor(
            state.format(), state.players(), state.firstBanActor(), nextBan);
        String[] offers = drawOffers(available, excludedFor(completedAdvancements, next.id()), random);
        return new State(state.format(), state.players(), available, banned,
            state.boardGoals(), state.owners(), Phase.BANS, nextBan,
            state.firstBanActor(), -1, 0, next.id(), offers[0], offers[1], "");
    }

    /** Applies the current player's choice (0 = A, 1 = B). */
    public static State pick(State state, String actorId, int choice, Map<String, Set<String>> completedAdvancements, Random random) {
        requirePhase(state, Phase.DRAFT);
        Objects.requireNonNull(random, "random");
        requireActor(state, actorId);
        if (choice != 0 && choice != 1) throw new IllegalArgumentException("Choice must be 0 or 1");
        String selected = choice == 0 ? state.offerA() : state.offerB();
        if (selected.isBlank() || !state.availableGoals().contains(selected))
            throw new IllegalArgumentException("Selected goal is no longer available");

        List<String> available = new ArrayList<>(state.availableGoals());
        available.remove(selected);
        List<String> board = new ArrayList<>(state.boardGoals());
        board.add(selected);
        List<String> owners = new ArrayList<>(state.owners());
        owners.add("-");

        if (board.size() >= DraftPlusRules.CELL_COUNT) {
            return new State(state.format(), state.players(), available, state.bannedGoals(),
                board, owners, Phase.PLAY, state.banIndex(), state.firstBanActor(),
                state.firstDraftActor(), board.size(), "", "", "", "");
        }

        int nextPick = board.size();
        DraftPlusRules.Player next = DraftPlusRules.drafter(
            state.format(), state.players(), state.firstDraftActor(), nextPick);
        String[] offers = drawOffers(available, excludedFor(completedAdvancements, next.id()), random);
        return new State(state.format(), state.players(), available, state.bannedGoals(),
            board, owners, Phase.DRAFT, state.banIndex(), state.firstBanActor(),
            state.firstDraftActor(), nextPick, next.id(), offers[0], offers[1], "");
    }

    /** Claims a drafted board cell after its goal has been completed in the world. */
    public static State claim(State state, String actorId, int cellIndex) {
        requirePhase(state, Phase.PLAY);
        if (actorId == null || state.players().stream().noneMatch(p -> p.id().equals(actorId)))
            throw new IllegalArgumentException("Unknown Draft+ player");
        if (cellIndex < 0 || cellIndex >= state.boardGoals().size()
            || cellIndex >= state.owners().size())
            throw new IllegalArgumentException("Invalid Draft+ board cell");
        if (!"-".equals(state.owners().get(cellIndex)))
            throw new IllegalArgumentException("Draft+ board cell is already claimed");

        List<String> owners = new ArrayList<>(state.owners());
        owners.set(cellIndex, actorId);
        var winner = DraftPlusRules.winner(state.format(), state.players(), owners);
        boolean full = owners.stream().noneMatch("-"::equals);
        String winnerId = winner.orElse("");
        if (full && winnerId.isEmpty() && state.format() == DraftPlusRules.Format.FFA) {
            long best = -1;
            String leader = "";
            boolean tied = false;
            for (DraftPlusRules.Player player : state.players()) {
                long owned = owners.stream().filter(player.id()::equals).count();
                if (owned > best) {
                    best = owned;
                    leader = player.id();
                    tied = false;
                } else if (owned == best) {
                    tied = true;
                }
            }
            if (!tied) winnerId = "player:" + leader;
        }
        Phase nextPhase = winner.isPresent() || full ? Phase.COMPLETE : Phase.PLAY;
        return new State(state.format(), state.players(), state.availableGoals(), state.bannedGoals(),
            state.boardGoals(), owners, nextPhase, state.banIndex(), state.firstBanActor(),
            state.firstDraftActor(), state.pickIndex(), "", "", "", winnerId);
    }

    private static State beginDraft(
        DraftPlusRules.Format format,
        List<DraftPlusRules.Player> players,
        List<String> available,
        List<String> banned,
        Map<String, Set<String>> completedAdvancements,
        Random random
    ) {
        int firstActor = format == DraftPlusRules.Format.FFA
            ? random.nextInt(players.size()) : random.nextInt(2);
        DraftPlusRules.Player first = DraftPlusRules.drafter(format, players, firstActor, 0);
        String[] offers = drawOffers(available, excludedFor(completedAdvancements, first.id()), random);
        return new State(format, players, available, banned, List.of(), List.of(),
            Phase.DRAFT, banned.size(), -1, firstActor, 0, first.id(),
            offers[0], offers[1], "");
    }

    private static Set<String> excludedFor(Map<String, Set<String>> completedAdvancements, String playerId) {
        if (completedAdvancements == null) return Set.of();
        return completedAdvancements.getOrDefault(playerId, Set.of());
    }

    private static String[] drawOffers(List<String> available, Set<String> completedAdvancements, Random random) {
        List<String> eligible = available.stream()
            .filter(goal -> !goal.startsWith("advancement:")
                || !completedAdvancements.contains(goal.substring("advancement:".length())))
            .toList();
        if (eligible.size() < 2)
            throw new IllegalStateException("Not enough uncompleted goals remain to offer two choices");
        int a = random.nextInt(eligible.size());
        String first = eligible.get(a);
        int b = random.nextInt(eligible.size() - 1);
        if (b >= a) b++;
        return new String[] { first, eligible.get(b) };
    }

    private static List<String> validatePool(List<String> goals) {
        Objects.requireNonNull(goals, "candidateGoals");
        Set<String> unique = new HashSet<>();
        List<String> pool = new ArrayList<>();
        for (String goal : goals) {
            if (goal == null || goal.isBlank())
                throw new IllegalArgumentException("Goal ID is blank");
            if (!unique.add(goal))
                throw new IllegalArgumentException("Duplicate goal ID: " + goal);
            pool.add(goal);
        }
        return List.copyOf(pool);
    }

    private static void requirePhase(State state, Phase expected) {
        Objects.requireNonNull(state, "state");
        if (state.phase() != expected)
            throw new IllegalStateException("Expected phase " + expected + " but was " + state.phase());
    }

    private static void requireActor(State state, String actorId) {
        if (actorId == null || !state.currentActor().equals(actorId))
            throw new IllegalArgumentException("It is not this player's turn");
    }
}
