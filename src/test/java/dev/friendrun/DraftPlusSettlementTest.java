package dev.friendrun;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

final class DraftPlusSettlementTest {
    @Test
    void missingMarkerAllowsFirstSettlementAttempt() {
        assertFalse(Lobby.hasDraftPlusSettlementMarker(new JsonObject(), "lobby_session_match"));
    }

    @Test
    void exactMatchMarkerPreventsDuplicateSettlement() {
        JsonObject ratings = new JsonObject();
        JsonObject ledger = new JsonObject();
        ledger.add("lobby_session_match", new JsonObject());
        ratings.add("__friendrun_draftplus_settlements_v1__", ledger);

        assertTrue(Lobby.hasDraftPlusSettlementMarker(ratings, "lobby_session_match"));
    }

    @Test
    void markerForAnotherMatchDoesNotBlockCurrentSettlement() {
        JsonObject ratings = new JsonObject();
        JsonObject ledger = new JsonObject();
        ledger.add("lobby_session_previous", new JsonObject());
        ratings.add("__friendrun_draftplus_settlements_v1__", ledger);

        assertFalse(Lobby.hasDraftPlusSettlementMarker(ratings, "lobby_session_current"));
    }

    @Test
    void blankOrMissingMatchIdCannotMatchLedgerEntry() {
        JsonObject ratings = new JsonObject();
        JsonObject ledger = new JsonObject();
        ledger.add("", new JsonObject());
        ratings.add("__friendrun_draftplus_settlements_v1__", ledger);

        assertFalse(Lobby.hasDraftPlusSettlementMarker(ratings, ""));
        assertFalse(Lobby.hasDraftPlusSettlementMarker(ratings, null));
    }
}
