package dev.friendrun;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.http.HttpClient;
import org.junit.jupiter.api.Test;

final class DraftPlusStreamTest {
    @Test
    void eventStreamClientFollowsFirebaseTemporaryRedirects() {
        assertEquals(HttpClient.Redirect.NORMAL, Lobby.STREAM_HTTP.followRedirects());
    }
}
