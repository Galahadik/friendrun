package dev.friendrun;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.util.Objects;

/**
 * Small Firebase Realtime Database compare-and-set helper for one JSON node.
 * Firebase REST only supports ETag preconditions on PUT/DELETE, not PATCH.
 */
final class FirebaseConditionalState {
    private FirebaseConditionalState() {}

    interface Transport {
        Response send(HttpRequest request) throws IOException;
    }

    record Response(int statusCode, String body, String etag) {}

    static boolean putIfUnchanged(
        Transport transport, URI uri, JsonElement expected, JsonElement replacement
    ) throws IOException {
        Objects.requireNonNull(transport, "transport");
        Objects.requireNonNull(uri, "uri");
        Objects.requireNonNull(replacement, "replacement");

        HttpRequest get = HttpRequest.newBuilder(uri)
            .header("X-Firebase-ETag", "true")
            .timeout(java.time.Duration.ofSeconds(5))
            .GET()
            .build();
        Response snapshot = transport.send(get);
        if (snapshot.statusCode() / 100 != 2)
            throw new IOException("Firebase conditional GET failed: HTTP " + snapshot.statusCode());
        if (snapshot.etag() == null || snapshot.etag().isBlank())
            throw new IOException("Firebase did not return an ETag for conditional update");

        JsonElement actual = parse(snapshot.body());
        JsonElement wanted = expected == null ? JsonNull.INSTANCE : expected;
        if (!actual.equals(wanted)) return false;

        HttpRequest put = HttpRequest.newBuilder(uri)
            .header("Content-Type", "application/json")
            .header("If-Match", snapshot.etag())
            .timeout(java.time.Duration.ofSeconds(5))
            .PUT(HttpRequest.BodyPublishers.ofString(replacement.toString()))
            .build();
        Response committed = transport.send(put);
        if (committed.statusCode() == 412) return false;
        if (committed.statusCode() / 100 != 2)
            throw new IOException("Firebase conditional PUT failed: HTTP " + committed.statusCode());
        return true;
    }

    static boolean deleteIfUnchanged(
        Transport transport, URI uri, JsonElement expected
    ) throws IOException {
        Objects.requireNonNull(transport, "transport");
        Objects.requireNonNull(uri, "uri");

        HttpRequest get = HttpRequest.newBuilder(uri)
            .header("X-Firebase-ETag", "true")
            .timeout(java.time.Duration.ofSeconds(5))
            .GET()
            .build();
        Response snapshot = transport.send(get);
        if (snapshot.statusCode() / 100 != 2)
            throw new IOException("Firebase conditional GET failed: HTTP " + snapshot.statusCode());
        if (snapshot.etag() == null || snapshot.etag().isBlank())
            throw new IOException("Firebase did not return an ETag for conditional delete");

        JsonElement actual = parse(snapshot.body());
        JsonElement wanted = expected == null ? JsonNull.INSTANCE : expected;
        if (!actual.equals(wanted)) return false;

        HttpRequest delete = HttpRequest.newBuilder(uri)
            .header("If-Match", snapshot.etag())
            .timeout(java.time.Duration.ofSeconds(5))
            .DELETE()
            .build();
        Response committed = transport.send(delete);
        if (committed.statusCode() == 412) return false;
        if (committed.statusCode() / 100 != 2)
            throw new IOException("Firebase conditional DELETE failed: HTTP " + committed.statusCode());
        return true;
    }

    private static JsonElement parse(String body) {
        if (body == null || body.isBlank() || body.trim().equals("null")) return JsonNull.INSTANCE;
        return JsonParser.parseString(body);
    }
}
