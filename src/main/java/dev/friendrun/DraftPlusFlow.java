package dev.friendrun;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
    public enum Phase { BANS, DRAFT, COMPLETE }

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
            return new State(format, players, pool, List.of(), List.of(), List.of(),
                Phase.BANS, 0, firstBanActor, -1, 0, actor.id(), "", "", "");
        }
        return beginDraft(format, players, pool, List.of(), random);
    }

    /** Applies one ban. Only the currently assigned actor may ban an available goal. */
    public static State ban(State state, String actorId, String goalId, Random random) {
        requirePhase(state, Phase.BANS);
        Objects.requireNonNull(random, "random");
        requireActor(state, actorId);
        if (goalId == null || !state.availableGoals().contains(goalId))
            throw new IllegalArgumentException("Goal is not available to ban");

        List<String> available = new ArrayList<>(state.availableGoals());
        available.remove(goalId);
        List<String> banned = new ArrayList<>(state.bannedGoals());
        banned.add(goalId);
        int nextBan = state.banIndex() + 1;
        if (nextBan >= DraftPlusRules.defaultBanCount(state.format()))
            return beginDraft(state.format(), state.players(), available, banned, random);

        DraftPlusRules.Player next = DraftPlusRules.banActor(
            state.format(), state.players(), state.firstBanActor(), nextBan);
        return new State(state.format(), state.players(), available, banned,
            state.boardGoals(), state.owners(), Phase.BANS, nextBan,
            state.firstBanActor(), -1, 0, next.id(), "", "", "");
    }

    /** Applies the current player's choice (0 = A, 1 = B). */
    public static State pick(State state, String actorId, int choice, Random random) {
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
        owners.add(actorId);

        var winner = DraftPlusRules.winner(state.format(), state.players(), owners);
        if (winner.isPresent() || board.size() >= DraftPlusRules.CELL_COUNT) {
            return new State(state.format(), state.players(), available, state.bannedGoals(),
                board, owners, Phase.COMPLETE, state.banIndex(), state.firstBanActor(),
                state.firstDraftActor(), board.size(), "", "", "", winner.orElse(""));
        }

        int nextPick = board.size();
        DraftPlusRules.Player next = DraftPlusRules.drafter(
            state.format(), state.players(), state.firstDraftActor(), nextPick);
        String[] offers = drawOffers(available, random);
        return new State(state.format(), state.players(), available, state.bannedGoals(),
            board, owners, Phase.DRAFT, state.banIndex(), state.firstBanActor(),
            state.firstDraftActor(), nextPick, next.id(), offers[0], offers[1], "");
    }

    private static State beginDraft(
        DraftPlusRules.Format format,
        List<DraftPlusRules.Player> players,
        List<String> available,
        List<String> banned,
        Random random
    ) {
        int firstActor = format == DraftPlusRules.Format.FFA
            ? random.nextInt(players.size()) : random.nextInt(2);
        DraftPlusRules.Player first = DraftPlusRules.drafter(format, players, firstActor, 0);
        String[] offers = drawOffers(available, random);
        return new State(format, players, available, banned, List.of(), List.of(),
            Phase.DRAFT, banned.size(), -1, firstActor, 0, first.id(),
            offers[0], offers[1], "");
    }

    private static String[] drawOffers(List<String> available, Random random) {
        if (available.size() < 2)
            throw new IllegalStateException("Not enough goals remain to offer two choices");
        int a = random.nextInt(available.size());
        String first = available.get(a);
        int b = random.nextInt(available.size() - 1);
        if (b >= a) b++;
        return new String[] { first, available.get(b) };
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
