package excel.auth;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

public class AuthManager {

    private static AuthManager instance;
    private static final Gson GSON = new Gson();
    private static final String AUTH_KEY = "rich_auth_token";
    private static final String NICK_KEY = "rich_auth_nickname";

    private String serverUrl = "http://localhost:5000";
    private String token;
    private String nickname;
    private boolean authenticated;
    private Process serverProcess;

    public AuthManager() {
        instance = this;
        loadLocal();
        startServerIfNeeded();
    }

    private void startServerIfNeeded() {
        if (isPortInUse(5000)) {
            System.out.println("[Rich] Auth server already running on port 5000");
            return;
        }

        new Thread(() -> {
            try {
                String gameDir = System.getProperty("user.dir");
                File webDir = new File(gameDir, "web");
                if (!webDir.exists()) {
                    File runDir = new File(gameDir, "run");
                    webDir = new File(runDir, "web");
                }
                if (!webDir.exists() || !new File(webDir, "app.py").exists()) {
                    System.out.println("[Rich] web/app.py not found, skipping auth server");
                    return;
                }

                ProcessBuilder pb = new ProcessBuilder("python", "app.py");
                pb.directory(webDir);
                pb.redirectErrorStream(true);
                serverProcess = pb.start();

                new Thread(() -> {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(serverProcess.getInputStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            System.out.println("[Rich Auth] " + line);
                        }
                    } catch (Exception ignored) {}
                }).start();

                for (int i = 0; i < 20; i++) {
                    if (isPortInUse(5000)) {
                        System.out.println("[Rich] Auth server started on port 5000");
                        return;
                    }
                    Thread.sleep(200);
                }
                System.out.println("[Rich] Auth server may not have started properly");
            } catch (Exception e) {
                System.out.println("[Rich] Failed to start auth server: " + e.getMessage());
            }
        }, "AuthServer-Starter").start();
    }

    private boolean isPortInUse(int port) {
        try (Socket s = new Socket("127.0.0.1", port)) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static AuthManager getInstance() {
        return instance;
    }

    public void setServerUrl(String url) {
        this.serverUrl = url.replaceAll("/+$", "");
    }

    public String getServerUrl() {
        return serverUrl;
    }

    public CompletableFuture<AuthResult> login(String nickname, String password) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                JsonObject body = new JsonObject();
                body.addProperty("nickname", nickname);
                body.addProperty("password", password);

                JsonObject response = post("/api/login", body);
                boolean success = response.has("success") && response.get("success").getAsBoolean();

                if (success) {
                    this.token = response.get("token").getAsString();
                    this.nickname = response.get("nickname").getAsString();
                    this.authenticated = true;
                    saveLocal();
                    return new AuthResult(true, "Welcome, " + this.nickname + "!", this.nickname);
                } else {
                    String error = response.has("error") ? response.get("error").getAsString() : "Unknown error";
                    boolean userNotFound = error.contains("not found");
                    return new AuthResult(false, error, userNotFound);
                }
            } catch (Exception e) {
                return new AuthResult(false, "Connection error: " + e.getMessage(), false);
            }
        });
    }

    public CompletableFuture<AuthResult> register(String nickname, String password, String confirmPassword) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                JsonObject body = new JsonObject();
                body.addProperty("nickname", nickname);
                body.addProperty("password", password);
                body.addProperty("confirmPassword", confirmPassword);

                JsonObject response = post("/api/register", body);
                boolean success = response.has("success") && response.get("success").getAsBoolean();

                if (success) {
                    this.token = response.get("token").getAsString();
                    this.nickname = response.get("nickname").getAsString();
                    this.authenticated = true;
                    saveLocal();
                    return new AuthResult(true, "Account created! Welcome, " + this.nickname, this.nickname);
                } else {
                    String error = response.has("error") ? response.get("error").getAsString() : "Unknown error";
                    return new AuthResult(false, error, false);
                }
            } catch (Exception e) {
                return new AuthResult(false, "Connection error: " + e.getMessage(), false);
            }
        });
    }

    public CompletableFuture<Boolean> validateToken() {
        return CompletableFuture.supplyAsync(() -> {
            if (token == null || token.isEmpty()) return false;
            try {
                JsonObject body = new JsonObject();
                body.addProperty("token", token);

                JsonObject response = post("/api/auth", body);
                boolean valid = response.has("success") && response.get("success").getAsBoolean();
                if (valid && response.has("nickname")) {
                    this.nickname = response.get("nickname").getAsString();
                }
                this.authenticated = valid;
                return valid;
            } catch (Exception e) {
                this.authenticated = false;
                return false;
            }
        });
    }

    public void logout() {
        this.token = null;
        this.nickname = null;
        this.authenticated = false;
        clearLocal();
    }

    public boolean isAuthenticated() {
        return authenticated && token != null;
    }

    public String getNickname() {
        return nickname;
    }

    public String getToken() {
        return token;
    }

    private JsonObject post(String path, JsonObject body) throws IOException {
        URL url = new URL(serverUrl + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);

        byte[] data = body.toString().getBytes(StandardCharsets.UTF_8);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(data);
        }

        int code = conn.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        if (is == null) {
            JsonObject err = new JsonObject();
            err.addProperty("error", "No response from server");
            return err;
        }

        String json;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            json = sb.toString();
        }

        return GSON.fromJson(json, JsonObject.class);
    }

    private void saveLocal() {
        try {
            File dir = new File(System.getProperty("user.home"), ".excel-client");
            dir.mkdirs();
            File file = new File(dir, "auth.dat");
            try (PrintWriter pw = new PrintWriter(file)) {
                pw.println(token != null ? token : "");
                pw.println(nickname != null ? nickname : "");
            }
        } catch (Exception ignored) {}
    }

    private void loadLocal() {
        try {
            File file = new File(System.getProperty("user.home"), ".excel-client/auth.dat");
            if (!file.exists()) return;
            try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
                token = br.readLine();
                nickname = br.readLine();
                if (token != null && !token.isEmpty()) {
                    this.authenticated = true;
                }
            }
        } catch (Exception ignored) {}
    }

    private void clearLocal() {
        try {
            File file = new File(System.getProperty("user.home"), ".excel-client/auth.dat");
            if (file.exists()) file.delete();
        } catch (Exception ignored) {}
    }

    public static class AuthResult {
        public final boolean success;
        public final String message;
        public final Object extra;

        public AuthResult(boolean success, String message, Object extra) {
            this.success = success;
            this.message = message;
            this.extra = extra;
        }
    }
}
