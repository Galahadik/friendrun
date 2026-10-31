package dev.friendrun;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

final class DraftPlusRematchTest {
    @Test
    void doesNotStartRematchBeforeSettlementIsConfirmed() {
        JsonObject players = players(true, true);

        assertFalse(Lobby.draftPlusRematchReady(false, players, 100_000));
    }

    @Test
    void startsRematchWhenSettlementIsConfirmedAndAllActivePlayersAreReady() {
        JsonObject players = players(true, true);

        assertTrue(Lobby.draftPlusRematchReady(true, players, 100_000));
    }

    @Test
    void waitsUntilEveryActivePlayerIsReady() {
        JsonObject players = players(true, false);

        assertFalse(Lobby.draftPlusRematchReady(true, players, 100_000));
    }

    @Test
    void doesNotStartRematchWhenEveryPlayerIsOffline() {
        JsonObject players = players(true, true);
        players.getAsJsonObject("alice").addProperty("lastSeen", 1);
        players.getAsJsonObject("bob").addProperty("lastSeen", 1);

        assertFalse(Lobby.draftPlusRematchReady(true, players, 100_000));
    }

    @Test
    void ignoresDisconnectedPlayersButRequiresAtLeastOneActivePlayer() {
        JsonObject players = players(true, true);
        players.getAsJsonObject("bob").addProperty("lastSeen", 1);

        assertTrue(Lobby.draftPlusRematchReady(true, players, 100_000));
    }

    @Test
    void pendingRatedSettlementKeepsHostFencingEvenIfNextMatchSettingIsTraining() {
        JsonObject doc = completedMatch(true, false);

        assertTrue(Lobby.isRatedDraftPlus(doc));
        assertFalse(Lobby.draftPlusSettlementConfirmed(doc));
        assertFalse(Lobby.draftPlusEditable(doc));
    }

    @Test
    void confirmedSettlementAllowsNextMatchSettingsWithoutKeepingOldRatedLease() {
        JsonObject doc = completedMatch(true, false);
        doc.addProperty("draftPlusSettledSeed", "match-42");

        assertTrue(Lobby.draftPlusSettlementConfirmed(doc));
        assertFalse(Lobby.isRatedDraftPlus(doc));
        assertTrue(Lobby.draftPlusEditable(doc));
    }

    @Test
    void pendingTrainingSettlementDoesNotBecomeRatedFromNextMatchSetting() {
        JsonObject doc = completedMatch(false, true);

        assertFalse(Lobby.isRatedDraftPlus(doc));
        assertFalse(Lobby.draftPlusEditable(doc));
    }

    private static JsonObject completedMatch(boolean frozenRated, boolean nextMatchRated) {
        JsonObject doc = new JsonObject();
        doc.addProperty("launched", true);
        doc.addProperty("gameMode", Game.DRAFT_PLUS);
        doc.addProperty("draftPlusRated", nextMatchRated);
        doc.addProperty("draftPlusSeed", "match-42");
        JsonObject state = new JsonObject();
        state.addProperty("phase", DraftPlusFlow.Phase.COMPLETE.name());
        doc.add("draftPlusState", state);
        JsonObject frozen = new JsonObject();
        frozen.addProperty("rated", frozenRated);
        doc.add("draftPlusFrozenSettings", frozen);
        return doc;
    }

    private static JsonObject players(boolean aliceReady, boolean bobReady) {
        JsonObject players = new JsonObject();
        JsonObject alice = new JsonObject();
        alice.addProperty("ready", aliceReady);
        alice.addProperty("lastSeen", 99_000);
        JsonObject bob = new JsonObject();
        bob.addProperty("ready", bobReady);
        bob.addProperty("lastSeen", 99_000);
        players.add("alice", alice);
        players.add("bob", bob);
        return players;
    }
}
