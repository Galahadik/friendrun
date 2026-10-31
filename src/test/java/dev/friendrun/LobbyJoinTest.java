package dev.friendrun;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

final class LobbyJoinTest {
    private static JsonObject lobby() {
        JsonObject doc = new JsonObject();
        doc.addProperty("host", "alice");
        doc.addProperty("hostSeen", 90_000L);
        doc.addProperty("sessionId", "session-1");
        doc.addProperty("launched", false);
        doc.addProperty("gameMode", 0);
        JsonObject players = new JsonObject();
        JsonObject bob = new JsonObject();
        bob.addProperty("joinNonce", "nonce-1");
        players.add("bob", bob);
        doc.add("players", players);
        return doc;
    }

    @Test
    void acceptsJoinOnlyIntoTheSameLiveUnlaunchedSession() {
        JsonObject original = lobby();
        JsonObject current = lobby();
        assertTrue(Lobby.isJoinSnapshotValid(original, current, "bob", "nonce-1", 100_000L));

        current.addProperty("sessionId", "session-2");
        assertFalse(Lobby.isJoinSnapshotValid(original, current, "bob", "nonce-1", 100_000L));
    }

    @Test
    void rejectsJoinWhenLobbyWasClosedStartedOrBecamePrivate() {
        JsonObject original = lobby();

        JsonObject noHost = lobby();
        noHost.addProperty("host", "");
        assertFalse(Lobby.isJoinSnapshotValid(original, noHost, "bob", "nonce-1", 100_000L));

        JsonObject launched = lobby();
        launched.addProperty("launched", true);
        assertFalse(Lobby.isJoinSnapshotValid(original, launched, "bob", "nonce-1", 100_000L));

        JsonObject solo = lobby();
        solo.addProperty("soloLock", true);
        assertFalse(Lobby.isJoinSnapshotValid(original, solo, "bob", "nonce-1", 100_000L));

        JsonObject stale = lobby();
        stale.addProperty("hostSeen", 1L);
        assertFalse(Lobby.isJoinSnapshotValid(original, stale, "bob", "nonce-1", 100_000L));
    }

    @Test
    void rejectsJoinIfItsOwnPlayerEntryWasReplaced() {
        JsonObject original = lobby();
        JsonObject current = lobby();
        current.getAsJsonObject("players").getAsJsonObject("bob").addProperty("joinNonce", "other-client");
        assertFalse(Lobby.isJoinSnapshotValid(original, current, "bob", "nonce-1", 100_000L));
    }

    @Test
    void supportsLegacySessionsWithoutSessionIdUsingHostLeaseFingerprint() {
        JsonObject original = lobby();
        original.remove("sessionId");
        JsonObject current = original.deepCopy();
        assertTrue(Lobby.isJoinSnapshotValid(original, current, "bob", "nonce-1", 100_000L));

        current.addProperty("hostSeen", 89_999L);
        assertFalse(Lobby.isJoinSnapshotValid(original, current, "bob", "nonce-1", 100_000L));
    }
}
