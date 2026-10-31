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
}
