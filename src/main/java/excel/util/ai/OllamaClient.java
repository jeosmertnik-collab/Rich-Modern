package excel.util.ai;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class OllamaClient {

    private static final Gson GSON = new Gson();
    private static final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "OllamaClient");
        t.setDaemon(true);
        return t;
    });

    private String baseUrl = "http://localhost:11434";
    private String model = "llama3.2";
    private final List<ChatMessage> conversationHistory = new ArrayList<>();
    private String systemPrompt = "";

    public OllamaClient() {
    }

    public OllamaClient(String baseUrl, String model) {
        this.baseUrl = baseUrl;
        this.model = model;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public void setSystemPrompt(String prompt) {
        this.systemPrompt = prompt;
        conversationHistory.clear();
    }

    public void sendMessage(String userMessage, Consumer<String> onResponse, Consumer<String> onError) {
        executor.submit(() -> {
            try {
                conversationHistory.add(new ChatMessage("user", userMessage));
                String response = callOllama();
                conversationHistory.add(new ChatMessage("assistant", response));
                onResponse.accept(response);
            } catch (Exception e) {
                onError.accept("Ошибка: " + e.getMessage());
            }
        });
    }

    public void sendStreamingMessage(String userMessage, Consumer<String> onToken, Consumer<String> onComplete, Consumer<String> onError) {
        executor.submit(() -> {
            try {
                conversationHistory.add(new ChatMessage("user", userMessage));
                StringBuilder fullResponse = new StringBuilder();
                callOllamaStreaming(token -> {
                    fullResponse.append(token);
                    onToken.accept(token);
                });
                conversationHistory.add(new ChatMessage("assistant", fullResponse.toString()));
                onComplete.accept(fullResponse.toString());
            } catch (Exception e) {
                onError.accept("Ошибка: " + e.getMessage());
            }
        });
    }

    public boolean isAvailable() {
        try {
            URL url = new URL(baseUrl + "/api/tags");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(2000);
            int code = conn.getResponseCode();
            conn.disconnect();
            return code == 200;
        } catch (Exception e) {
            return false;
        }
    }

    public List<String> getAvailableModels() {
        List<String> models = new ArrayList<>();
        try {
            URL url = new URL(baseUrl + "/api/tags");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(3000);

            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();
            conn.disconnect();

            JsonObject root = JsonParser.parseReader(new StringReader(sb.toString())).getAsJsonObject();
            JsonArray modelsArray = root.getAsJsonArray("models");
            if (modelsArray != null) {
                for (int i = 0; i < modelsArray.size(); i++) {
                    JsonObject model = modelsArray.get(i).getAsJsonObject();
                    models.add(model.get("name").getAsString());
                }
            }
        } catch (Exception ignored) {
        }
        return models;
    }

    public void clearHistory() {
        conversationHistory.clear();
    }

    private String callOllama() throws IOException {
        JsonObject request = buildRequest(false);
        String json = GSON.toJson(request);

        URL url = new URL(baseUrl + "/api/chat");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(60000);

        OutputStream os = conn.getOutputStream();
        os.write(json.getBytes(StandardCharsets.UTF_8));
        os.flush();
        os.close();

        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        reader.close();
        conn.disconnect();

        JsonObject response = JsonParser.parseReader(new StringReader(sb.toString())).getAsJsonObject();
        return response.getAsJsonObject("message").get("content").getAsString();
    }

    private void callOllamaStreaming(Consumer<String> onToken) throws IOException {
        JsonObject request = buildRequest(true);
        String json = GSON.toJson(request);

        URL url = new URL(baseUrl + "/api/chat");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(120000);

        OutputStream os = conn.getOutputStream();
        os.write(json.getBytes(StandardCharsets.UTF_8));
        os.flush();
        os.close();

        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isEmpty()) continue;
            try {
                JsonObject obj = JsonParser.parseReader(new StringReader(line)).getAsJsonObject();
                if (obj.has("message")) {
                    String content = obj.getAsJsonObject("message").get("content").getAsString();
                    onToken.accept(content);
                }
            } catch (Exception ignored) {
            }
        }
        reader.close();
        conn.disconnect();
    }

    private JsonObject buildRequest(boolean stream) {
        JsonObject request = new JsonObject();
        request.addProperty("model", model);
        request.addProperty("stream", stream);

        JsonArray messages = new JsonArray();

        if (!systemPrompt.isEmpty()) {
            JsonObject sysMsg = new JsonObject();
            sysMsg.addProperty("role", "system");
            sysMsg.addProperty("content", systemPrompt);
            messages.add(sysMsg);
        }

        for (ChatMessage msg : conversationHistory) {
            JsonObject msgObj = new JsonObject();
            msgObj.addProperty("role", msg.role);
            msgObj.addProperty("content", msg.content);
            messages.add(msgObj);
        }

        request.add("messages", messages);
        return request;
    }

    public record ChatMessage(String role, String content) {
    }
}
