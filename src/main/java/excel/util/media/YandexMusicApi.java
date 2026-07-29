package excel.util.media;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class YandexMusicApi {

    private static final String API = "https://api.music.yandex.net/";
    private static final String FIRST_PLAYLIST_ID = "3";
    private final HttpClient http;
    private final Gson gson;
    private String token;
    private long uid;

    public YandexMusicApi(String token) {
        this.token = token;
        this.http = HttpClient.newHttpClient();
        this.gson = new Gson();
    }

    public boolean init() {
        JsonObject status = get("account/status");
        if (status == null) return false;
        try {
            JsonObject account = status.getAsJsonObject("account");
            if (account == null) return false;
            uid = account.get("uid").getAsLong();
            return uid > 0;
        } catch (Exception e) {
            return false;
        }
    }

    public List<YandexTrack> fetchTracks() {
        List<YandexTrack> all = new ArrayList<>();
        int offset = 0;
        int batch = 50;
        while (true) {
            String path = "users/" + uid + "/likes/tracks?offset=" + offset + "&count=" + batch;
            JsonObject resp = get(path);
            if (resp == null) break;
            List<YandexTrack> batchList = parseTracks(resp);
            if (batchList.isEmpty()) break;
            all.addAll(batchList);
            offset += batch;
        }
        return all;
    }

    private List<YandexTrack> parseTracks(JsonObject resp) {
        List<YandexTrack> tracks = new ArrayList<>();
        try {
            JsonObject result = resp.getAsJsonObject("result");
            if (result == null) return tracks;
            JsonArray items = result.getAsJsonArray("items");
            if (items == null) return tracks;
            for (int i = 0; i < items.size(); i++) {
                JsonObject item = items.get(i).getAsJsonObject();
                JsonObject track = item.getAsJsonObject("track");
                if (track == null) continue;
                YandexTrack t = parseTrack(track);
                if (t != null) tracks.add(t);
            }
        } catch (Exception ignored) {}
        return tracks;
    }

    private YandexTrack parseTrack(JsonObject track) {
        try {
            YandexTrack t = new YandexTrack();
            t.id = stringOrEmpty(track, "id");
            t.title = stringOrEmpty(track, "title");
            t.duration = intOrZero(track, "durationMs") / 1000;

            JsonArray artists = track.getAsJsonArray("artists");
            if (artists != null && artists.size() > 0) {
                t.artist = stringOrEmpty(artists.get(0).getAsJsonObject(), "name");
            }

            JsonObject album = track.getAsJsonObject("album");
            if (album != null) {
                t.albumId = stringOrEmpty(album, "id");
                t.coverUri = stringOrEmpty(album, "coverUri");
            }

            return t;
        } catch (Exception e) {
            return null;
        }
    }

    public boolean resolveDownloadUrl(YandexTrack track) {
        try {
            JsonObject resp = get("tracks/" + track.id + "/download-info");
            if (resp == null) return false;
            JsonObject result = resp.getAsJsonObject("result");
            if (result == null) return false;
            JsonArray downloadInfo = result.getAsJsonArray(track.id);
            if (downloadInfo == null || downloadInfo.size() == 0) return false;

            JsonObject best = null;
            int bestBitrate = 0;
            for (int i = 0; i < downloadInfo.size(); i++) {
                JsonObject info = downloadInfo.get(i).getAsJsonObject();
                int bitrate = intOrZero(info, "bitrateInKbps");
                String codec = stringOrEmpty(info, "codec");
                if (!"mp3".equals(codec)) continue;
                if (bitrate > bestBitrate) {
                    bestBitrate = bitrate;
                    best = info;
                }
            }
            if (best == null) return false;

            String encodedUrl = stringOrEmpty(best, "downloadInfoUrl");
            if (encodedUrl.isEmpty()) return false;

            String realUrl = fetchDirectUrl(encodedUrl);
            if (realUrl == null || realUrl.isEmpty()) return false;

            track.downloadUrl = realUrl;
            track.bitrate = bestBitrate;
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private String fetchDirectUrl(String downloadInfoUrl) {
        try {
            String url = downloadInfoUrl.contains("?") ? downloadInfoUrl + "&format=json" : downloadInfoUrl + "?format=json";
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "OAuth " + token)
                    .header("User-Agent", "Mozilla/5.0")
                    .header("X-Yandex-Music-Client", "WindowsPhone/9.0")
                    .timeout(java.time.Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            String body = resp.body();
            if (body == null || body.isEmpty()) return null;

            JsonObject info = gson.fromJson(body, JsonObject.class);
            if (info == null) return null;

            String host = stringOrEmpty(info, "host");
            String path = stringOrEmpty(info, "path");
            String ts = stringOrEmpty(info, "ts");
            String s = stringOrEmpty(info, "s");

            if (host.isEmpty() || path.isEmpty()) return null;
            String decodedPath = decodeXorPath(path, s);
            if (decodedPath == null) return null;
            return "https://" + host + "/get-mp3/" + ts + "/" + decodedPath;
        } catch (Exception e) {
            return null;
        }
    }

    private String decodeXorPath(String path, String s) {
        try {
            String b64 = path.substring(1);
            byte[] decoded = Base64.getDecoder().decode(b64);
            int key = s.length();
            for (int i = 0; i < decoded.length; i++) {
                decoded[i] ^= (byte) key;
            }
            return new String(decoded, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    private JsonObject get(String path) {
        try {
            String url = API + path;
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "OAuth " + token)
                    .header("User-Agent", "Mozilla/5.0")
                    .header("X-Yandex-Music-Client", "WindowsPhone/9.0")
                    .timeout(java.time.Duration.ofSeconds(15))
                    .GET()
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            return gson.fromJson(resp.body(), JsonObject.class);
        } catch (IOException | InterruptedException e) {
            return null;
        }
    }

    private static String stringOrEmpty(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? "" : e.getAsString();
    }

    private static int intOrZero(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? 0 : e.getAsInt();
    }
}
