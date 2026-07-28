package rich.util.vk;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class VkApi {

    private static final String API = "https://api.vk.com/method/";
    private static final String V = "5.131";
    private final HttpClient http;
    private final Gson gson;
    private String token;

    public VkApi(String token) {
        this.token = token;
        this.http = HttpClient.newHttpClient();
        this.gson = new Gson();
    }

    public String getToken() {
        return token;
    }

    public VkUser getCurrentUser() {
        JsonObject resp = call("users.get", "");
        if (resp == null) return null;
        JsonArray arr = resp.getAsJsonArray("response");
        if (arr == null || arr.size() == 0) return null;
        JsonObject u = arr.get(0).getAsJsonObject();
        VkUser user = new VkUser();
        user.id = u.get("id").getAsInt();
        user.firstName = g(u, "first_name");
        user.lastName = g(u, "last_name");
        return user;
    }

    public List<VkTrack> fetchTracks() {
        VkUser user = getCurrentUser();
        if (user == null) return new ArrayList<>();

        List<VkTrack> all = new ArrayList<>();
        int offset = 0;
        int batch = 100;

        while (true) {
            List<VkTrack> batchList = fetchTracksBatch(user.id, batch, offset);
            if (batchList == null || batchList.isEmpty()) break;
            all.addAll(batchList);
            if (batchList.size() < batch) break;
            offset += batch;
        }

        return all;
    }

    private List<VkTrack> fetchTracksBatch(int ownerId, int count, int offset) {
        String code = "return API.audio.get({\"owner_id\":" + ownerId + ",\"count\":" + count + ",\"offset\":" + offset + "});";
        String resp = rawCall("execute", "code=" + encode(code));

        if (resp == null) return null;

        try {
            JsonObject root = gson.fromJson(resp, JsonObject.class);
            if (root.has("error")) return null;

            JsonArray items = null;
            if (root.has("response")) {
                JsonElement re = root.get("response");
                if (re.isJsonArray()) {
                    items = re.getAsJsonArray();
                } else if (re.isJsonObject() && re.getAsJsonObject().has("items")) {
                    items = re.getAsJsonObject().getAsJsonArray("items");
                }
            }

            if (items == null) return null;

            List<VkTrack> tracks = new ArrayList<>();
            for (int i = 0; i < items.size(); i++) {
                JsonObject t = items.get(i).getAsJsonObject();
                if (!t.has("url") || t.get("url").getAsString().isEmpty()) continue;
                VkTrack track = new VkTrack();
                track.id = g(t, "id");
                track.ownerId = g(t, "owner_id");
                track.title = g(t, "title");
                track.artist = g(t, "artist");
                track.duration = i(t, "duration");
                track.url = g(t, "url");
                track.albumId = g(t, "album_id");
                tracks.add(track);
            }
            return tracks;
        } catch (Exception e) {
            return null;
        }
    }

    public void refreshToken(String newToken) {
        this.token = newToken;
    }

    private JsonObject call(String method, String params) {
        String resp = rawCall(method, params);
        if (resp == null) return null;
        try {
            return gson.fromJson(resp, JsonObject.class);
        } catch (Exception e) {
            return null;
        }
    }

    private String rawCall(String method, String params) {
        try {
            String url = API + method + "?" + params + "&access_token=" + token + "&v=" + V;
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(java.time.Duration.ofSeconds(15))
                    .GET()
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            return resp.body();
        } catch (IOException | InterruptedException e) {
            return null;
        }
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String g(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? "" : e.getAsString();
    }

    private static int i(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? 0 : e.getAsInt();
    }

    private static JsonObject child(JsonObject o, String key) {
        return o.has(key) ? o.getAsJsonObject(key) : null;
    }

    public static class VkUser {
        public int id;
        public String firstName;
        public String lastName;
    }

    public static class VkTrack {
        public String id;
        public String ownerId;
        public String title;
        public String artist;
        public int duration;
        public String url;
        public String albumId;

        public String getFullId() {
            return ownerId + "_" + id;
        }
    }
}
