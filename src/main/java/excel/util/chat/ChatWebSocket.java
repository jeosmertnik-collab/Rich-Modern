package excel.util.chat;

import lombok.Getter;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

public class ChatWebSocket {

    private static ChatWebSocket instance;
    private WebSocket ws;
    private String username;
    private HttpClient client;
    private boolean connected;

    @Getter
    private final List<ChatMessage> messages = new ArrayList<>();

    private Consumer<ChatMessage> onMessage;

    public static ChatWebSocket getInstance() {
        if (instance == null) instance = new ChatWebSocket();
        return instance;
    }

    public void connect(String username, Consumer<ChatMessage> callback) {
        if (connected) return;
        this.username = username;
        this.onMessage = callback;
        this.client = HttpClient.newHttpClient();

        try {
            ws = client.newWebSocketBuilder()
                    .buildAsync(URI.create("ws://localhost:4000"), new Listener())
                    .join();
            connected = true;

            sendAuth();

            ChatMessage sys = new ChatMessage("system", "Подключение к чату...", System.currentTimeMillis());
            messages.add(sys);
        } catch (Exception e) {
            ChatMessage sys = new ChatMessage("system", "Ошибка: " + e.getMessage(), System.currentTimeMillis());
            messages.add(sys);
        }
    }

    public void disconnect() {
        if (ws != null) {
            ws.sendClose(1000, "bye");
            ws = null;
        }
        connected = false;
        ChatMessage sys = new ChatMessage("system", "Чат отключен", System.currentTimeMillis());
        messages.add(sys);
    }

    public void sendMessage(String text) {
        if (!connected || ws == null) return;
        try {
            String json = "{\"type\":\"message\",\"text\":\"" + escapeJson(text) + "\"}";
            ws.sendText(json, true);
        } catch (Exception e) {
            ChatMessage sys = new ChatMessage("system", "Ошибка отправки", System.currentTimeMillis());
            messages.add(sys);
        }
    }

    private void sendAuth() {
        try {
            String json = "{\"type\":\"auth\",\"username\":\"" + escapeJson(username) + "\"}";
            ws.sendText(json, true);
        } catch (Exception e) {}
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    public boolean isConnected() {
        return connected;
    }

    private class Listener implements WebSocket.Listener {
        private final StringBuilder buffer = new StringBuilder();

        @Override
        public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
            buffer.append(data);
            if (last) {
                String json = buffer.toString();
                buffer.setLength(0);
                try {
                    String type = extractField(json, "type");
                    if ("message".equals(type)) {
                        String msgUser = extractField(json, "username");
                        String text = extractField(json, "text");
                        long time = Long.parseLong(extractField(json, "time"));
                        ChatMessage cm = new ChatMessage(msgUser, text, time);
                        messages.add(cm);
                        if (messages.size() > 200) messages.remove(0);
                        if (onMessage != null) onMessage.accept(cm);
                    } else if ("system".equals(type)) {
                        String text = extractField(json, "text");
                        ChatMessage cm = new ChatMessage("system", text, System.currentTimeMillis());
                        messages.add(cm);
                        if (messages.size() > 200) messages.remove(0);
                        if (onMessage != null) onMessage.accept(cm);
                    }
                } catch (Exception e) {}
            }
            return WebSocket.Listener.super.onText(ws, data, last);
        }

        @Override
        public void onError(WebSocket ws, Throwable error) {
            connected = false;
            ChatMessage sys = new ChatMessage("system", "Ошибка соединения", System.currentTimeMillis());
            messages.add(sys);
        }

        @Override
        public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
            connected = false;
            ChatMessage sys = new ChatMessage("system", "Соединение закрыто", System.currentTimeMillis());
            messages.add(sys);
            return WebSocket.Listener.super.onClose(ws, statusCode, reason);
        }

        private String extractField(String json, String field) {
            String search = "\"" + field + "\":\"";
            int start = json.indexOf(search);
            if (start == -1) {
                search = "\"" + field + "\":";
                start = json.indexOf(search);
                if (start == -1) return "";
                start += search.length();
                int end = json.indexOf(",", start);
                if (end == -1) end = json.indexOf("}", start);
                if (end == -1) return "";
                return json.substring(start, end).trim();
            }
            start += search.length();
            StringBuilder val = new StringBuilder();
            for (int i = start; i < json.length(); i++) {
                char c = json.charAt(i);
                if (c == '\\' && i + 1 < json.length()) {
                    val.append(json.charAt(i + 1));
                    i++;
                } else if (c == '"') {
                    break;
                } else {
                    val.append(c);
                }
            }
            return val.toString();
        }
    }

    @Getter
    public static class ChatMessage {
        private final String username;
        private final String text;
        private final long time;

        public ChatMessage(String username, String text, long time) {
            this.username = username;
            this.text = text;
            this.time = time;
        }
    }
}