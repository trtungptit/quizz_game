package quizgame.server.net;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import quizgame.common.protocol.Envelope;
import quizgame.common.protocol.ErrorInfo;

/** Tạo JSON wire giữ đúng dạng response và push-event trong protocol. */
public final class ProtocolMessages {
    private static final Gson GSON = new GsonBuilder().serializeNulls().create();
    private ProtocolMessages() { }

    public static String response(String type, Long reqId, JsonElement data, ErrorInfo error) {
        JsonObject response = new JsonObject();
        response.addProperty("type", type);
        if (reqId == null) response.add("reqId", JsonNull.INSTANCE); else response.addProperty("reqId", reqId);
        response.addProperty("ok", error == null);
        response.add("data", data == null ? JsonNull.INSTANCE : data);
        if (error != null) response.add("error", GSON.toJsonTree(error));
        return GSON.toJson(response);
    }

    public static String event(String type, String dataJson) {
        JsonObject event = new JsonObject();
        event.addProperty("type", type);
        event.add("data", dataJson == null ? JsonNull.INSTANCE : com.google.gson.JsonParser.parseString(dataJson));
        return GSON.toJson(event);
    }

    public static String event(String type, JsonElement data) {
        JsonObject event = new JsonObject();
        event.addProperty("type", type);
        event.add("data", data == null ? JsonNull.INSTANCE : data);
        return GSON.toJson(event);
    }
}
