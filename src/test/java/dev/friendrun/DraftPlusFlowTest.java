package dev.friendrun;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class DraftPlusFlowTest {
    private static final List<DraftPlusRules.Player> FFA_PLAYERS = List.of(
        new DraftPlusRules.Player("alice", -1, false, 0),
        new DraftPlusRules.Player("bob", -1, false, 1),
        new DraftPlusRules.Player("cara", -1, false, 2)
    );

    private static List<String> goals(int count) {
        List<String> result = new ArrayList<>();
        for (int i = 0; i < count; i++) result.add("item:test_" + i);
        return result;
    }

    private static boolean isExcludedAdvancement(String goal, Set<String> completed) {
        return goal.startsWith("advancement:")
            && completed.contains(goal.substring("advancement:".length()));
    }

    private static DraftPlusFlow.State boardState(
            DraftPlusRules.Format format, List<DraftPlusRules.Player> players, List<String> owners) {
        List<String> board = new ArrayList<>();
        for (int i = 0; i < DraftPlusRules.CELL_COUNT; i++) board.add("item:board_" + i);
        return new DraftPlusFlow.State(format, players, List.of(), List.of(), board, owners,
            DraftPlusFlow.Phase.PLAY, 0, 0, 0, DraftPlusRules.CELL_COUNT, "", "", "", "");
    }

    @Test
    void noBanDraftBuildsTwentyFiveUniqueCells() {
        DraftPlusFlow.State state = DraftPlusFlow.start(
            DraftPlusRules.Format.FFA, FFA_PLAYERS, goals(40), false, Map.of(), new Random(1));

        assertEquals(DraftPlusFlow.Phase.DRAFT, state.phase());
        for (int pick = 0; pick < DraftPlusRules.CELL_COUNT; pick++) {
            assertFalse(state.offerA().isBlank());
            assertFalse(state.offerB().isBlank());
            assertNotEquals(state.offerA(), state.offerB());
            state = DraftPlusFlow.pick(state, state.currentActor(), 0, Map.of(), new Random(pick + 2));
        }

        assertEquals(DraftPlusFlow.Phase.PLAY, state.phase());
        assertEquals(DraftPlusRules.CELL_COUNT, state.boardGoals().size());
        assertEquals(DraftPlusRules.CELL_COUNT, Set.copyOf(state.boardGoals()).size());
        assertEquals(DraftPlusRules.CELL_COUNT, state.owners().size());
        assertTrue(state.owners().stream().allMatch("-"::equals));
    }

    @Test
    void ffaBansAreSequentialAndLeadIntoDraft() {
        DraftPlusFlow.State state = DraftPlusFlow.start(
            DraftPlusRules.Format.FFA, FFA_PLAYERS, goals(40), true, Map.of(), new Random(3));

        assertEquals(DraftPlusFlow.Phase.BANS, state.phase());
        for (int ban = 0; ban < DraftPlusRules.defaultBanCount(DraftPlusRules.Format.FFA); ban++) {
            DraftPlusFlow.State beforeBan = state;
            String wrongActor = FFA_PLAYERS.stream().map(DraftPlusRules.Player::id)
                .filter(id -> !id.equals(beforeBan.currentActor())).findFirst().orElseThrow();
            assertThrows(IllegalArgumentException.class,
                () -> DraftPlusFlow.ban(beforeBan, wrongActor, beforeBan.offerA(), Map.of(), new Random(9)));
            state = DraftPlusFlow.ban(beforeBan, beforeBan.currentActor(), beforeBan.offerA(), Map.of(), new Random(ban + 10));
        }

        assertEquals(DraftPlusFlow.Phase.DRAFT, state.phase());
        assertEquals(3, state.bannedGoals().size());
        assertEquals(37, state.availableGoals().size());
    }

    @Test
    void eachDrafterDoesNotReceiveTheirCompletedAdvancement() {
        List<String> pool = goals(30);
        pool.addAll(List.of(
            "advancement:story/root",
            "advancement:story/mine_stone",
            "advancement:story/smelt_iron"
        ));
        Map<String, Set<String>> completed = Map.of(
            "alice", Set.of("story/root"),
            "bob", Set.of("story/mine_stone"),
            "cara", Set.of("story/smelt_iron")
        );
        DraftPlusFlow.State state = DraftPlusFlow.start(
            DraftPlusRules.Format.FFA, FFA_PLAYERS, pool, false, completed, new Random(4));

        while (state.phase() == DraftPlusFlow.Phase.DRAFT) {
            Set<String> excluded = completed.get(state.currentActor());
            assertFalse(isExcludedAdvancement(state.offerA(), excluded),
                "offer A contains a completed advancement");
            assertFalse(isExcludedAdvancement(state.offerB(), excluded),
                "offer B contains a completed advancement");
            state = DraftPlusFlow.pick(state, state.currentActor(), 0, completed, new Random(state.pickIndex() + 20));
        }
        assertEquals(DraftPlusFlow.Phase.PLAY, state.phase());
    }

    @Test
    void ffaBoardCompletionAwardsUniqueCellLeader() {
        List<String> owners = new ArrayList<>();
        owners.addAll(java.util.Collections.nCopies(9, "alice"));
        owners.addAll(java.util.Collections.nCopies(8, "bob"));
        owners.addAll(java.util.Collections.nCopies(7, "cara"));
        owners.add("-");
        DraftPlusFlow.State state = boardState(DraftPlusRules.Format.FFA, FFA_PLAYERS, owners);

        state = DraftPlusFlow.claim(state, "cara", 24);

        assertEquals(DraftPlusFlow.Phase.COMPLETE, state.phase());
        assertEquals("player:alice", state.winner());
    }

    @Test
    void ffaBoardCompletionIsDrawWhenTopCellCountsAreTied() {
        List<String> owners = new ArrayList<>();
        owners.addAll(java.util.Collections.nCopies(9, "alice"));
        owners.addAll(java.util.Collections.nCopies(9, "bob"));
        owners.addAll(java.util.Collections.nCopies(6, "cara"));
        owners.add("-");
        DraftPlusFlow.State state = boardState(DraftPlusRules.Format.FFA, FFA_PLAYERS, owners);

        state = DraftPlusFlow.claim(state, "cara", 24);

        assertEquals(DraftPlusFlow.Phase.COMPLETE, state.phase());
        assertEquals("", state.winner());
    }

    @Test
    void teamWinsWhenItsMembersReachThirteenCells() {
        List<DraftPlusRules.Player> teams = List.of(
            new DraftPlusRules.Player("a1", 0, true, 0),
            new DraftPlusRules.Player("a2", 0, false, 1),
            new DraftPlusRules.Player("b1", 1, true, 2),
            new DraftPlusRules.Player("b2", 1, false, 3)
        );
        List<String> owners = new ArrayList<>();
        owners.addAll(java.util.Collections.nCopies(12, "a1"));
        owners.addAll(java.util.Collections.nCopies(12, "b1"));
        owners.add("-");
        DraftPlusFlow.State state = boardState(DraftPlusRules.Format.TEAM, teams, owners);

        state = DraftPlusFlow.claim(state, "a2", 24);

        assertEquals(DraftPlusFlow.Phase.COMPLETE, state.phase());
        assertEquals("team:0", state.winner());
    }
}
