package dev.friendrun;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import org.junit.jupiter.api.Test;

final class FirebaseConditionalStateTest {
    private static final URI NODE = URI.create("https://example.firebaseio.com/friendrun/test/draftPlusState.json");

    private static final class FakeTransport implements FirebaseConditionalState.Transport {
        private final Queue<FirebaseConditionalState.Response> responses = new ArrayDeque<>();
        private final List<HttpRequest> requests = new ArrayList<>();

        FakeTransport response(int status, String body, String etag) {
            responses.add(new FirebaseConditionalState.Response(status, body, etag));
            return this;
        }

        @Override
        public FirebaseConditionalState.Response send(HttpRequest request) throws IOException {
            requests.add(request);
            FirebaseConditionalState.Response response = responses.poll();
            if (response == null) throw new IOException("Unexpected request");
            return response;
        }
    }

    @Test
    void writesOnlyWhenSnapshotMatchesAndSendsIfMatchOnPut() throws Exception {
        JsonObject current = new JsonObject();
        current.addProperty("phase", "PLAY");
        JsonObject next = new JsonObject();
        next.addProperty("phase", "COMPLETE");
        FakeTransport transport = new FakeTransport()
            .response(200, current.toString(), "\"etag-1\"")
            .response(200, next.toString(), "\"etag-2\"");

        assertTrue(FirebaseConditionalState.putIfUnchanged(transport, NODE, current, next));
        assertEquals(2, transport.requests.size());
        assertEquals("true", transport.requests.get(0).headers().firstValue("X-Firebase-ETag").orElseThrow());
        assertEquals("\"etag-1\"", transport.requests.get(1).headers().firstValue("If-Match").orElseThrow());
        assertEquals("PUT", transport.requests.get(1).method());
    }

    @Test
    void skipsWriteWhenSnapshotIsAlreadyStale() throws Exception {
        JsonObject expected = new JsonObject();
        expected.addProperty("phase", "PLAY");
        JsonObject actual = new JsonObject();
        actual.addProperty("phase", "COMPLETE");
        FakeTransport transport = new FakeTransport().response(200, actual.toString(), "\"etag-2\"");

        assertFalse(FirebaseConditionalState.putIfUnchanged(transport, NODE, expected, new JsonObject()));
        assertEquals(1, transport.requests.size());
    }

    @Test
    void treatsEtagConflictAsARejectedCommit() throws Exception {
        JsonObject current = new JsonObject();
        current.addProperty("phase", "PLAY");
        FakeTransport transport = new FakeTransport()
            .response(200, current.toString(), "\"etag-1\"")
            .response(412, current.toString(), "\"etag-2\"");

        assertFalse(FirebaseConditionalState.putIfUnchanged(transport, NODE, current, new JsonObject()));
        assertEquals(2, transport.requests.size());
    }
    @Test
    void deletesOnlyWhenSnapshotMatchesAndUsesIfMatch() throws Exception {
        JsonObject current = new JsonObject();
        current.addProperty("host", "alice");
        FakeTransport transport = new FakeTransport()
            .response(200, current.toString(), "\"etag-1\"")
            .response(200, "null", "\"etag-null\"");

        assertTrue(FirebaseConditionalState.deleteIfUnchanged(transport, NODE, current));
        assertEquals(2, transport.requests.size());
        assertEquals("true", transport.requests.get(0).headers().firstValue("X-Firebase-ETag").orElseThrow());
        assertEquals("\"etag-1\"", transport.requests.get(1).headers().firstValue("If-Match").orElseThrow());
        assertEquals("DELETE", transport.requests.get(1).method());
    }

    @Test
    void staleSnapshotOrEtagConflictPreventsConditionalDelete() throws Exception {
        JsonObject expected = new JsonObject();
        expected.addProperty("hostSeen", 1);
        JsonObject actual = new JsonObject();
        actual.addProperty("hostSeen", 2);
        FakeTransport changedBeforeDelete = new FakeTransport()
            .response(200, actual.toString(), "\"etag-2\"");
        assertFalse(FirebaseConditionalState.deleteIfUnchanged(changedBeforeDelete, NODE, expected));
        assertEquals(1, changedBeforeDelete.requests.size());

        FakeTransport changedDuringDelete = new FakeTransport()
            .response(200, expected.toString(), "\"etag-1\"")
            .response(412, actual.toString(), "\"etag-2\"");
        assertFalse(FirebaseConditionalState.deleteIfUnchanged(changedDuringDelete, NODE, expected));
    }

    @Test
    void canCreateAnAbsentHistoryNodeWithFirebaseNullEtag() throws Exception {
        JsonObject history = new JsonObject();
        history.addProperty("id", "match-1");
        FakeTransport transport = new FakeTransport()
            .response(200, "null", "null_etag")
            .response(200, history.toString(), null);

        assertTrue(FirebaseConditionalState.putIfUnchanged(transport, NODE, com.google.gson.JsonNull.INSTANCE, history));
        assertEquals("null_etag", transport.requests.get(1).headers().firstValue("If-Match").orElseThrow());
    }

}
