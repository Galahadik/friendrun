package dev.friendrun;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.Test;

final class DraftPlusStreamTest {
    @Test
    void eventStreamClientFollowsFirebaseTemporaryRedirects() {
        assertEquals(java.net.http.HttpClient.Redirect.NORMAL, Lobby.STREAM_HTTP.followRedirects());
    }

    @Test
    void shortSseDisconnectsUseIncreasingReconnectBackoff() {
        assertEquals(500L, Lobby.sseReconnectDelayMillis(1));
        assertEquals(1_000L, Lobby.sseReconnectDelayMillis(2));
        assertEquals(2_000L, Lobby.sseReconnectDelayMillis(3));
        assertEquals(4_000L, Lobby.sseReconnectDelayMillis(4));
        assertEquals(8_000L, Lobby.sseReconnectDelayMillis(5));
        assertEquals(10_000L, Lobby.sseReconnectDelayMillis(6));
        assertEquals(10_000L, Lobby.sseReconnectDelayMillis(12));
    }

    @Test
    void onlyAStableSseConnectionResetsReconnectBackoff() {
        long opened = 1_000_000L;
        assertFalse(Lobby.shouldResetSseReconnectBackoff(opened, opened + Duration.ofSeconds(29).toNanos()));
        assertTrue(Lobby.shouldResetSseReconnectBackoff(opened, opened + Duration.ofSeconds(30).toNanos()));
        assertFalse(Lobby.shouldResetSseReconnectBackoff(0L, Duration.ofMinutes(2).toNanos()));
    }
}
