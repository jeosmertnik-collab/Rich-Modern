package excel.util.ai;

import java.io.File;

public class OllamaManager {

    private static Process ollamaProcess;
    private static boolean started = false;

    public static synchronized void ensureRunning() {
        if (started) return;
        started = true;

        new Thread(() -> {
            try {
                Thread.sleep(2000);
                if (isServerRunning()) return;

                String ollamaPath = findOllama();
                if (ollamaPath == null) return;

                ProcessBuilder pb = new ProcessBuilder(ollamaPath, "serve");
                pb.redirectErrorStream(true);
                pb.directory(new File(System.getProperty("user.home")));
                ollamaProcess = pb.start();

                Thread.sleep(3000);
            } catch (Exception ignored) {
            }
        }, "OllamaAutoStart").start();
    }

    private static boolean isServerRunning() {
        try {
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection)
                    new java.net.URL("http://localhost:11434/api/tags").openConnection();
            conn.setConnectTimeout(1000);
            conn.setReadTimeout(1000);
            boolean ok = conn.getResponseCode() == 200;
            conn.disconnect();
            return ok;
        } catch (Exception e) {
            return false;
        }
    }

    private static String findOllama() {
        String appData = System.getenv("LOCALAPPDATA");
        if (appData != null) {
            File local = new File(appData, "Programs\\Ollama\\ollama.exe");
            if (local.exists()) return local.getAbsolutePath();
        }

        try {
            Process p = Runtime.getRuntime().exec(new String[]{"where", "ollama"});
            java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(p.getInputStream()));
            String line = reader.readLine();
            reader.close();
            if (line != null && !line.isEmpty()) return line.trim();
        } catch (Exception ignored) {
        }

        return null;
    }

    public static void shutdown() {
        if (ollamaProcess != null) {
            ollamaProcess.destroyForcibly();
            ollamaProcess = null;
        }
    }

    public static boolean isRunning() {
        return isServerRunning();
    }
}
