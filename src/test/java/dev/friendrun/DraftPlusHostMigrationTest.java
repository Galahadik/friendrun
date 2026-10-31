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
    void onlyThePersistedRatedDraftPlusHostCanCommitAuthoritativeChanges() {
        JsonObject doc = ratedMatch();
        doc.addProperty("host", "alice");
        assertTrue(Lobby.isCurrentRatedDraftPlusHost(doc, "alice"));
        assertFalse(Lobby.isCurrentRatedDraftPlusHost(doc, "bob"));

        doc.addProperty("host", "bob");
        doc.addProperty("hostEpoch", 2);
        assertFalse(Lobby.isCurrentRatedDraftPlusHost(doc, "alice"));
        assertTrue(Lobby.isCurrentRatedDraftPlusHost(doc, "bob"));
    }

    @Test
    void voluntaryWorldExitCannotBeUndoneByAStaleHostSnapshot() {
        JsonObject doc = ratedMatch();
        doc.addProperty("host", "alice");

        assertFalse(Lobby.shouldActAsDraftPlusHost(doc, "alice", true));
    }

    @Test
    void persistedReplacementHostCanActAfterVoluntaryWorldExit() {
        JsonObject doc = ratedMatch();
        doc.addProperty("host", "bob");

        assertFalse(Lobby.shouldActAsDraftPlusHost(doc, "alice", true));
        assertTrue(Lobby.shouldActAsDraftPlusHost(doc, "bob", false));
    }

    @Test
    void rootPatchMergePreservesUnrelatedFieldsAndAppliesNestedUpdates() {
        JsonObject doc = new JsonObject();
        doc.addProperty("host", "alice");
        JsonObject players = new JsonObject();
        JsonObject alice = new JsonObject();
        alice.addProperty("ready", false);
        alice.addProperty("score", 4);
        players.add("alice", alice);
        doc.add("players", players);

        JsonObject patch = new JsonObject();
        patch.addProperty("players/alice/ready", true);
        patch.addProperty("hostSeen", 1234);
        Lobby.applyFirebasePatch(doc, patch);

        assertTrue(Lobby.bool(Lobby.obj(doc, "players").getAsJsonObject("alice"), "ready"));
        assertTrue(Lobby.num(Lobby.obj(doc, "players").getAsJsonObject("alice"), "score") == 4);
        assertTrue(Lobby.num(doc, "hostSeen") == 1234);
        assertTrue(Lobby.str(doc, "host").equals("alice"));
    }

    @Test
    void electedCandidateCanClaimExpiredLeaseAndAdvanceEpoch() {
        JsonObject doc = ratedMatch();
        doc.addProperty("host", "alice");
        doc.addProperty("hostSeen", 1_000);
        doc.addProperty("hostEpoch", 4);
        JsonObject players = new JsonObject();
        JsonObject bob = new JsonObject();
        bob.addProperty("joinedAt", 2);
        bob.addProperty("lastSeen", 20_000);
        players.add("bob", bob);
        JsonObject cara = new JsonObject();
        cara.addProperty("joinedAt", 3);
        cara.addProperty("lastSeen", 20_000);
        players.add("cara", cara);
        doc.add("players", players);

        JsonObject transfer = new JsonObject();
        transfer.addProperty("host", "bob");
        transfer.addProperty("hostSeen", 20_000);
        transfer.addProperty("hostMigrationAt", 20_000);
        transfer.addProperty("hostEpoch", 5);

        assertTrue(Lobby.isValidDraftPlusHostMigration(doc, "bob", transfer));
        assertFalse(Lobby.isValidDraftPlusHostMigration(doc, "cara", transfer));
    }

    @Test
    void nextHostCanTakeOverAgainWithMonotonicallyIncreasingEpoch() {
        JsonObject doc = ratedMatch();
        doc.addProperty("host", "bob");
        doc.addProperty("hostSeen", 20_000);
        doc.addProperty("hostEpoch", 5);
        JsonObject players = new JsonObject();
        JsonObject bob = new JsonObject();
        bob.addProperty("joinedAt", 2);
        bob.addProperty("lastSeen", 20_000);
        players.add("bob", bob);
        JsonObject cara = new JsonObject();
        cara.addProperty("joinedAt", 3);
        cara.addProperty("lastSeen", 40_000);
        players.add("cara", cara);
        JsonObject dan = new JsonObject();
        dan.addProperty("joinedAt", 4);
        dan.addProperty("lastSeen", 40_000);
        players.add("dan", dan);
        doc.add("players", players);

        JsonObject transfer = new JsonObject();
        transfer.addProperty("host", "cara");
        transfer.addProperty("hostSeen", 40_000);
        transfer.addProperty("hostMigrationAt", 40_000);
        transfer.addProperty("hostEpoch", 6);

        assertTrue(Lobby.isValidDraftPlusHostMigration(doc, "cara", transfer));
    }

    @Test
    void returningPlayerCanClaimLobbyAfterVoluntaryExitWithNoOnlineSuccessor() {
        JsonObject doc = ratedMatch();
        doc.addProperty("host", "");
        doc.addProperty("hostSeen", 0);
        doc.addProperty("hostEpoch", 9);
        JsonObject players = new JsonObject();
        JsonObject bob = new JsonObject();
        bob.addProperty("joinedAt", 2);
        bob.addProperty("lastSeen", 20_000);
        players.add("bob", bob);
        doc.add("players", players);

        JsonObject transfer = new JsonObject();
        transfer.addProperty("host", "bob");
        transfer.addProperty("hostSeen", 20_000);
        transfer.addProperty("hostMigrationFrom", "");
        transfer.addProperty("hostMigrationAt", 20_000);
        transfer.addProperty("hostEpoch", 10);

        assertTrue(Lobby.isValidDraftPlusHostMigration(doc, "bob", transfer));
    }
    @Test
    void candidateCannotTakeOverWhileCurrentHostLeaseIsStillLive() {
        JsonObject doc = ratedMatch();
        doc.addProperty("host", "alice");
        doc.addProperty("hostSeen", 20_000);
        doc.addProperty("hostEpoch", 2);
        JsonObject players = new JsonObject();
        JsonObject bob = new JsonObject();
        bob.addProperty("joinedAt", 1);
        bob.addProperty("lastSeen", 30_000);
        players.add("bob", bob);
        doc.add("players", players);

        JsonObject transfer = new JsonObject();
        transfer.addProperty("host", "bob");
        transfer.addProperty("hostSeen", 31_000);
        transfer.addProperty("hostMigrationAt", 31_000);
        transfer.addProperty("hostEpoch", 3);

        assertFalse(Lobby.isValidDraftPlusHostMigration(doc, "bob", transfer));
    }

    @Test
    void expiredOrNonElectedCandidateCannotClaimLease() {
        JsonObject doc = ratedMatch();
        doc.addProperty("host", "alice");
        doc.addProperty("hostEpoch", 8);
        JsonObject players = new JsonObject();
        JsonObject bob = new JsonObject();
        bob.addProperty("joinedAt", 1);
        bob.addProperty("lastSeen", 1);
        players.add("bob", bob);
        doc.add("players", players);

        JsonObject transfer = new JsonObject();
        transfer.addProperty("host", "bob");
        transfer.addProperty("hostSeen", 20_000);
        transfer.addProperty("hostMigrationAt", 20_000);
        transfer.addProperty("hostEpoch", 9);

        assertFalse(Lobby.isValidDraftPlusHostMigration(doc, "bob", transfer));
        transfer.addProperty("hostEpoch", 10);
        assertFalse(Lobby.isValidDraftPlusHostMigration(doc, "bob", transfer));
    }


    @Test
    void repeatedHostMigrationPreservesFrozenMatchStateAndSettlementConfirmation() {
        JsonObject doc = ratedMatch();
        doc.addProperty("host", "alice");
        doc.addProperty("hostSeen", 1_000);
        doc.addProperty("hostEpoch", 7);

        JsonObject state = new JsonObject();
        state.addProperty("phase", "COMPLETE");
        state.addProperty("pickIndex", 25);
        doc.add("draftPlusState", state);

        JsonObject frozen = new JsonObject();
        frozen.addProperty("rated", true);
        frozen.addProperty("rolesEnabled", true);
        frozen.addProperty("chaosEnabled", true);
        doc.add("draftPlusFrozenSettings", frozen);

        JsonObject roles = new JsonObject();
        roles.addProperty("alice", "miner");
        roles.addProperty("bob", "guardian");
        doc.add("draftPlusRoles", roles);
        doc.addProperty("draftPlusSeed", "match-seed-42");
        doc.addProperty("draftPlusSettledSeed", "match-seed-42");

        JsonObject transfer = new JsonObject();
        transfer.addProperty("host", "bob");
        transfer.addProperty("hostSeen", 20_000);
        transfer.addProperty("hostEpoch", 8);
        Lobby.applyFirebasePatch(doc, transfer);

        assertTrue("bob".equals(Lobby.str(doc, "host")));
        assertTrue(Lobby.num(doc, "hostEpoch") == 8);
        assertTrue("COMPLETE".equals(Lobby.str(Lobby.obj(doc, "draftPlusState"), "phase")));
        assertTrue(Lobby.num(Lobby.obj(doc, "draftPlusState"), "pickIndex") == 25);
        assertTrue(Lobby.bool(Lobby.obj(doc, "draftPlusFrozenSettings"), "rated"));
        assertTrue(Lobby.bool(Lobby.obj(doc, "draftPlusFrozenSettings"), "rolesEnabled"));
        assertTrue("miner".equals(Lobby.str(Lobby.obj(doc, "draftPlusRoles"), "alice")));
        assertTrue("guardian".equals(Lobby.str(Lobby.obj(doc, "draftPlusRoles"), "bob")));
        assertTrue("match-seed-42".equals(Lobby.str(doc, "draftPlusSettledSeed")));
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
