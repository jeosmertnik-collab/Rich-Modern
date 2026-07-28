package excel.util.ai;

import java.io.File;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TtsHelper {

    private static final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "TTS-Worker");
        t.setDaemon(true);
        return t;
    });

    private static final File TEMP_DIR = new File(System.getProperty("java.io.tmpdir"), "excel-tts");

    private static volatile boolean enabled = true;
    private static volatile boolean aiVoiceEnabled = true;
    private static volatile String aiVoice = "ru-RU-SvetlanaNeural";
    private static volatile String alertVoice = "ru-RU-DmitryNeural";
    private static volatile int volume = 100;

    static {
        if (!TEMP_DIR.exists()) TEMP_DIR.mkdirs();
    }

    public static void speak(String text) {
        if (!enabled || text == null || text.isEmpty()) return;
        executor.submit(() -> runTts(text, alertVoice));
    }

    public static void speakAi(String text) {
        speakAiDelayed(text, 0);
    }

    public static void speakAiDelayed(String text, long delayMs) {
        if (!enabled || !aiVoiceEnabled || text == null || text.isEmpty()) return;
        executor.submit(() -> {
            try {
                if (delayMs > 0) Thread.sleep(delayMs);
            } catch (InterruptedException ignored) {
                return;
            }
            runTts(text, aiVoice);
        });
    }

    private static void runTts(String text, String voice) {
        try {
            String id = UUID.randomUUID().toString().substring(0, 8);
            File outFile = new File(TEMP_DIR, id + ".mp3");

            String escaped = text.replace("\"", "\\\"");
            ProcessBuilder edgeTts = new ProcessBuilder(
                    "edge-tts",
                    "--voice", voice,
                    "--volume", "+" + volume + "%",
                    "--text", escaped,
                    "--write-media", outFile.getAbsolutePath()
            );
            edgeTts.redirectErrorStream(true);
            edgeTts.redirectOutput(ProcessBuilder.Redirect.DISCARD);

            Process p = edgeTts.start();
            p.waitFor();

            if (outFile.exists() && outFile.length() > 0) {
                playAudio(outFile);
            }
        } catch (Exception ignored) {
        }
    }

    private static void playAudio(File file) {
        try {
            String ps = String.format(
                    "Add-Type -AssemblyName PresentationCore; " +
                    "$p = New-Object System.Windows.Media.MediaPlayer; " +
                    "$p.Open([uri]'%s'); " +
                    "$p.Play(); " +
                    "Start-Sleep -Milliseconds 500; " +
                    "while ($p.Position -lt $p.NaturalDuration.TimeSpan) { Start-Sleep -Milliseconds 200 }; " +
                    "$p.Close(); " +
                    "Remove-Item '%s' -Force -ErrorAction SilentlyContinue",
                    file.getAbsolutePath().replace("\\", "\\\\"),
                    file.getAbsolutePath().replace("\\", "\\\\")
            );
            ProcessBuilder pb = new ProcessBuilder(
                    "powershell", "-NoProfile", "-NonInteractive", "-Command", ps
            );
            pb.redirectErrorStream(true);
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            Process p = pb.start();
            p.waitFor();
        } catch (Exception ignored) {
        }
    }

    public static void setEnabled(boolean val) {
        enabled = val;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setAiVoiceEnabled(boolean val) {
        aiVoiceEnabled = val;
    }

    public static boolean isAiVoiceEnabled() {
        return aiVoiceEnabled;
    }

    public static void setAiVoice(String voice) {
        aiVoice = voice;
    }

    public static String getAiVoice() {
        return aiVoice;
    }

    public static void setAlertVoice(String voice) {
        alertVoice = voice;
    }

    public static String getAlertVoice() {
        return alertVoice;
    }

    public static void setVolume(int v) {
        volume = Math.max(0, Math.min(100, v));
    }

    public static int getVolume() {
        return volume;
    }

    public static void testAiVoice() {
        executor.submit(() -> runTts("Тест голоса. Привет, я AI ассистент Excel Client.", aiVoice));
    }

    public static void testAlertVoice() {
        executor.submit(() -> runTts("Тест оповещения. Внимание, низкое здоровье.", alertVoice));
    }

    public static String[] getAvailableAiVoices() {
        return new String[]{
                "ru-RU-SvetlanaNeural",
                "ru-RU-DmitryNeural",
                "en-US-JennyNeural",
                "en-US-GuyNeural",
                "uk-UA-PolinaNeural",
                "de-DE-KatjaNeural",
                "zh-CN-XiaoxiaoNeural",
                "ja-JP-NanamiNeural"
        };
    }

    public static String[] getAvailableAlertVoices() {
        return new String[]{
                "ru-RU-DmitryNeural",
                "ru-RU-SvetlanaNeural",
                "en-US-GuyNeural",
                "en-US-JennyNeural",
                "uk-UA-PolinaNeural"
        };
    }

    public static String voiceDisplayName(String voice) {
        if (voice == null) return "?";
        return switch (voice) {
            case "ru-RU-SvetlanaNeural" -> "Svetlana (RU Female)";
            case "ru-RU-DmitryNeural" -> "Dmitry (RU Male)";
            case "en-US-JennyNeural" -> "Jenny (EN Female)";
            case "en-US-GuyNeural" -> "Guy (EN Male)";
            case "uk-UA-PolinaNeural" -> "Polina (UA Female)";
            case "de-DE-KatjaNeural" -> "Katja (DE Female)";
            case "zh-CN-XiaoxiaoNeural" -> "Xiaoxiao (ZH Female)";
            case "ja-JP-NanamiNeural" -> "Nanami (JA Female)";
            default -> voice;
        };
    }
}
