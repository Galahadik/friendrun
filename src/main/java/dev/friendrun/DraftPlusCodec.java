package dev.friendrun;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;

/** Firebase JSON codec for the independent Draft+ flow state. */
public final class DraftPlusCodec {
    private DraftPlusCodec() {}

    public static JsonObject encode(DraftPlusFlow.State state) {
        JsonObject out = new JsonObject();
        out.addProperty("format", state.format().name());
        JsonArray players = new JsonArray();
        for (DraftPlusRules.Player player : state.players()) {
            JsonObject p = new JsonObject();
            p.addProperty("id", player.id());
            p.addProperty("team", player.teamIndex());
            p.addProperty("captain", player.captain());
            p.addProperty("joinedOrder", player.joinedOrder());
            players.add(p);
        }
        out.add("players", players);
        out.add("available", strings(state.availableGoals()));
        out.add("banned", strings(state.bannedGoals()));
        out.add("board", strings(state.boardGoals()));
        out.add("owners", strings(state.owners()));
        out.addProperty("phase", state.phase().name());
        out.addProperty("banIndex", state.banIndex());
        out.addProperty("firstBanActor", state.firstBanActor());
        out.addProperty("firstDraftActor", state.firstDraftActor());
        out.addProperty("pickIndex", state.pickIndex());
        out.addProperty("currentActor", state.currentActor());
        out.addProperty("offerA", state.offerA());
        out.addProperty("offerB", state.offerB());
        out.addProperty("winner", state.winner());
        return out;
    }

    public static DraftPlusFlow.State decode(JsonObject json) {
        if (json == null || json.isEmpty()) return null;
        DraftPlusRules.Format format = DraftPlusRules.Format.valueOf(json.get("format").getAsString());
        List<DraftPlusRules.Player> players = new ArrayList<>();
        for (JsonElement element : array(json, "players")) {
            JsonObject p = element.getAsJsonObject();
            players.add(new DraftPlusRules.Player(
                p.get("id").getAsString(), p.get("team").getAsInt(),
                p.get("captain").getAsBoolean(), p.get("joinedOrder").getAsInt()));
        }
        return new DraftPlusFlow.State(format, players, readStrings(json, "available"),
            readStrings(json, "banned"), readStrings(json, "board"), readStrings(json, "owners"),
            DraftPlusFlow.Phase.valueOf(json.get("phase").getAsString()),
            integer(json, "banIndex"), integer(json, "firstBanActor"), integer(json, "firstDraftActor"),
            integer(json, "pickIndex"), string(json, "currentActor"), string(json, "offerA"),
            string(json, "offerB"), string(json, "winner"));
    }

    private static JsonArray strings(List<String> values) {
        JsonArray array = new JsonArray();
        for (String value : values) array.add(value);
        return array;
    }

    private static JsonArray array(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonArray() ? value.getAsJsonArray() : new JsonArray();
    }

    private static List<String> readStrings(JsonObject object, String key) {
        List<String> result = new ArrayList<>();
        for (JsonElement value : array(object, key)) result.add(value.getAsString());
        return result;
    }

    private static int integer(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? 0 : value.getAsInt();
    }

    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? "" : value.getAsString();
    }
}
