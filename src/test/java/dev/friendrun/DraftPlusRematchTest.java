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
