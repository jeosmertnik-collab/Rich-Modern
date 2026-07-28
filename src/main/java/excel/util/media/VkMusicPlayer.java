package excel.util.media;

import javazoom.jl.decoder.*;
import javazoom.jl.player.AudioDevice;
import javazoom.jl.player.FactoryRegistry;
import excel.util.vk.VkApi;
import excel.util.vk.VkApi.VkTrack;

import javax.sound.sampled.*;
import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class VkMusicPlayer {

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private VkApi vkApi;
    private List<VkTrack> playlist = new ArrayList<>();
    private int currentTrackIndex = -1;
    private boolean playing = false;
    private boolean paused = false;
    private boolean vkReady = false;
    private long positionMs = 0;
    private long durationMs = 0;
    private float volume = 50f;
    private String currentTrackName = "";
    private boolean shuffle = false;
    private boolean repeat = false;

    private Thread playbackThread;
    private volatile boolean stopRequested = false;
    private final Object pauseLock = new Object();

    private SourceDataLine sourceLine;
    private AudioDevice audioDevice;

    public void init() {
        String token = readToken();
        if (token.isEmpty()) {
            currentTrackName = "VK: no token";
            return;
        }
        vkApi = new VkApi(token);
        new Thread(() -> {
            try {
                List<VkTrack> tracks = vkApi.fetchTracks();
                if (tracks != null && !tracks.isEmpty()) {
                    playlist.clear();
                    playlist.addAll(tracks);
                    if (shuffle) Collections.shuffle(playlist);
                    currentTrackIndex = 0;
                    vkReady = true;
                    updateTrackName();
                    playCurrent();
                } else {
                    currentTrackName = "VK: no tracks";
                }
            } catch (Exception e) {
                currentTrackName = "VK error: " + e.getMessage();
            }
        }, "VK-Init").start();
    }

    private String readToken() {
        String[] paths = {
            "Excel/configs/vk_token.txt",
            "../Excel/configs/vk_token.txt",
            "Rich/configs/vk_token.txt",
            "../Rich/configs/vk_token.txt",
        };
        for (String p : paths) {
            try {
                File f = new File(p);
                if (f.exists()) return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).trim();
            } catch (Exception ignored) {}
        }
        return "";
    }

    public void playCurrent() {
        if (playlist.isEmpty() || !vkReady) return;
        if (currentTrackIndex < 0 || currentTrackIndex >= playlist.size()) currentTrackIndex = 0;
        stopPlayback();
        updateTrackName();

        VkTrack track = playlist.get(currentTrackIndex);
        stopRequested = false;
        paused = false;

        playbackThread = new Thread(() -> playTrack(track), "VK-Playback");
        playbackThread.setDaemon(true);
        playbackThread.start();
    }

    private void playTrack(VkTrack track) {
        try {
            String trackUrl = track.url;
            if (trackUrl == null || trackUrl.isEmpty()) { playing = false; return; }

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(trackUrl))
                    .header("User-Agent", "Mozilla/5.0")
                    .timeout(java.time.Duration.ofSeconds(30))
                    .GET()
                    .build();

            HttpResponse<InputStream> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofInputStream());

            try (InputStream is = resp.body(); BufferedInputStream bis = new BufferedInputStream(is)) {

                Bitstream bitstream = new Bitstream(bis);
                Decoder decoder = new Decoder();

                Header header = bitstream.readFrame();
                if (header == null) { playing = false; return; }

                int freq = header.frequency();
                int ch = (header.mode() == Header.SINGLE_CHANNEL) ? 1 : 2;
                SampleBuffer buf = new SampleBuffer(freq, ch);
                decoder.setOutputBuffer(buf);

                decoder.decodeFrame(header, bitstream);
                short[] samples = buf.getBuffer();
                int len = buf.getBufferLength();
                byte[] pcm = toBytes(samples, len);

                AudioFormat fmt = new AudioFormat(freq, 16, ch, true, false);
                DataLine.Info info = new DataLine.Info(SourceDataLine.class, fmt);
                sourceLine = (SourceDataLine) AudioSystem.getLine(info);
                sourceLine.open(fmt);
                sourceLine.start();

                applyVolume();
                playing = true;
                long startTime = System.currentTimeMillis();
                positionMs = 0;
                sourceLine.write(pcm, 0, pcm.length);

                while (!stopRequested) {
                    synchronized (pauseLock) {
                        while (paused && !stopRequested) {
                            pauseLock.wait();
                        }
                    }
                    if (stopRequested) break;

                    header = bitstream.readFrame();
                    if (header == null) break;

                    decoder.decodeFrame(header, bitstream);
                    samples = buf.getBuffer();
                    len = buf.getBufferLength();
                    pcm = toBytes(samples, len);
                    sourceLine.write(pcm, 0, pcm.length);

                    positionMs = System.currentTimeMillis() - startTime;
                }

                durationMs = track.duration > 0 ? track.duration * 1000L : positionMs;
                sourceLine.drain();
                sourceLine.close();
                sourceLine = null;
                playing = false;

                if (!stopRequested && !paused) {
                    if (repeat) {
                        playCurrent();
                    } else {
                        nextTrack();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            playing = false;
        }
    }

    public void stopPlayback() {
        stopRequested = true;
        paused = false;
        synchronized (pauseLock) { pauseLock.notifyAll(); }
        if (sourceLine != null) {
            sourceLine.stop();
            sourceLine.close();
            sourceLine = null;
        }
        if (playbackThread != null && playbackThread.isAlive()) {
            playbackThread.interrupt();
            try { playbackThread.join(500); } catch (InterruptedException ignored) {}
            playbackThread = null;
        }
        playing = false;
        positionMs = 0;
    }

    public void togglePlayPause() {
        if (playing) {
            paused = true;
            playing = false;
            if (sourceLine != null) sourceLine.stop();
        } else if (paused) {
            paused = false;
            playing = true;
            synchronized (pauseLock) { pauseLock.notifyAll(); }
            if (sourceLine != null) sourceLine.start();
        } else {
            playCurrent();
        }
    }

    public void nextTrack() {
        if (playlist.isEmpty() || !vkReady) return;
        if (shuffle) {
            currentTrackIndex = (int) (Math.random() * playlist.size());
        } else {
            currentTrackIndex = (currentTrackIndex + 1) % playlist.size();
        }
        playCurrent();
    }

    public void prevTrack() {
        if (playlist.isEmpty() || !vkReady) return;
        if (positionMs > 3000) { playCurrent(); return; }
        currentTrackIndex = (currentTrackIndex - 1 + playlist.size()) % playlist.size();
        playCurrent();
    }

    public void setVolume(float vol) {
        this.volume = Math.max(0, Math.min(100, vol));
        applyVolume();
    }

    public float getVolume() {
        return volume;
    }

    private void applyVolume() {
        if (sourceLine != null && sourceLine.isOpen()) {
            try {
                if (sourceLine.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                    FloatControl gain = (FloatControl) sourceLine.getControl(FloatControl.Type.MASTER_GAIN);
                    float dB = (float) (Math.log(Math.max(volume, 0.01) / 100.0) / Math.log(10.0) * 20.0);
                    gain.setValue(Math.max(gain.getMinimum(), Math.min(gain.getMaximum(), dB)));
                }
            } catch (Exception ignored) {}
        }
    }

    private void updateTrackName() {
        if (playlist.isEmpty() || currentTrackIndex < 0 || currentTrackIndex >= playlist.size()) {
            currentTrackName = vkReady ? "No tracks" : "VK: enter token in launcher";
            return;
        }
        VkTrack t = playlist.get(currentTrackIndex);
        currentTrackName = (t.artist.isEmpty() ? "" : t.artist + " - ") + t.title;
    }

    public String getCurrentTrackName() { return currentTrackName; }
    public long getPositionMs() { return positionMs; }
    public long getDurationMs() { return durationMs > 0 ? durationMs : positionMs; }
    public boolean isPlaying() { return playing; }
    public boolean isPaused() { return paused; }
    public boolean isVkReady() { return vkReady; }
    public List<VkTrack> getPlaylist() { return playlist; }
    public int getCurrentTrackIndex() { return currentTrackIndex; }
    public boolean isShuffle() { return shuffle; }
    public void setShuffle(boolean s) { this.shuffle = s; }
    public boolean isRepeat() { return repeat; }
    public void setRepeat(boolean r) { this.repeat = r; }

    private byte[] toBytes(short[] samples, int length) {
        byte[] out = new byte[length * 2];
        for (int i = 0; i < length; i++) {
            out[i * 2] = (byte) (samples[i] & 0xFF);
            out[i * 2 + 1] = (byte) ((samples[i] >> 8) & 0xFF);
        }
        return out;
    }
}
