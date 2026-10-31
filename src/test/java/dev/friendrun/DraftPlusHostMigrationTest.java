package dev.friendrun;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

final class DraftPlusHostMigrationTest {
    private static JsonObject ratedMatch() {
        JsonObject doc = new JsonObject();
        doc.addProperty("launched", true);
        doc.addProperty("gameMode", Game.DRAFT_PLUS);
        doc.addProperty("draftPlusRated", true);
        return doc;
    }

    @Test
    void staleReturningClientMustRefreshHeartbeatBeforeHostElection() {
        JsonObject doc = ratedMatch();

        assertTrue(Lobby.shouldHeartbeatBeforeDraftPlusMigration(doc, 20_001, 10_000));
    }

    @Test
    void freshHeartbeatDoesNotTriggerAnotherWrite() {
        JsonObject doc = ratedMatch();

        assertFalse(Lobby.shouldHeartbeatBeforeDraftPlusMigration(doc, 20_001, 15_002));
    }

    @Test
    void trainingMatchDoesNotUseRatedMigrationHeartbeatGate() {
        JsonObject doc = ratedMatch();
        doc.addProperty("draftPlusRated", false);

        assertFalse(Lobby.shouldHeartbeatBeforeDraftPlusMigration(doc, 20_001, 10_000));
    }
    private static JsonObject disconnectedMatch(long hostSeen, long aliceSeen, long bobSeen) {
        JsonObject doc = ratedMatch();
        doc.addProperty("hostSeen", hostSeen);
        JsonObject state = new JsonObject();
        state.addProperty("phase", "PLAY");
        doc.add("draftPlusState", state);
        JsonObject players = new JsonObject();
        JsonObject alice = new JsonObject();
        alice.addProperty("lastSeen", aliceSeen);
        players.add("alice", alice);
        JsonObject bob = new JsonObject();
        bob.addProperty("lastSeen", bobSeen);
        players.add("bob", bob);
        doc.add("players", players);
        return doc;
    }

    @Test
    void globalTimeoutRequiresCandidateAndEveryOtherPlayerToBeOfflineForFiveMinutes() {
        JsonObject doc = disconnectedMatch(1_000, 1_000, 1_000);
        assertTrue(Lobby.shouldMarkDraftPlusGlobalTimeout(doc, "alice", 301_001, true));
    }

    @Test
    void globalTimeoutIsNotTriggeredWhenCandidateWasOnline() {
        JsonObject doc = disconnectedMatch(1_000, 1_000, 1_000);
        assertFalse(Lobby.shouldMarkDraftPlusGlobalTimeout(doc, "alice", 301_001, false));
    }

    @Test
    void globalTimeoutIsNotTriggeredWhenAnotherPlayerRecentlyHeartbeated() {
        JsonObject doc = disconnectedMatch(1_000, 1_000, 300_000);
        assertFalse(Lobby.shouldMarkDraftPlusGlobalTimeout(doc, "alice", 301_001, true));
    }

    @Test
    void globalTimeoutIsNotTriggeredWhenHostTimeoutHasNotReachedFiveMinutes() {
        JsonObject doc = disconnectedMatch(300_000, 1_000, 1_000);
        assertFalse(Lobby.shouldMarkDraftPlusGlobalTimeout(doc, "alice", 301_001, true));
    }

    @Test
    void completedMatchDoesNotReceiveGlobalTimeoutFlag() {
        JsonObject doc = disconnectedMatch(1_000, 1_000, 1_000);
        doc.getAsJsonObject("draftPlusState").addProperty("phase", "COMPLETE");
        assertFalse(Lobby.shouldMarkDraftPlusGlobalTimeout(doc, "alice", 301_001, true));
    }

}
