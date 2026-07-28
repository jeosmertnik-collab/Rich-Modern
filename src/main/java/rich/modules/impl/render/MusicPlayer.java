package rich.modules.impl.render;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import net.minecraft.util.Util;
import rich.events.api.EventHandler;
import rich.events.impl.TickEvent;
import rich.modules.module.ModuleStructure;
import rich.modules.module.category.ModuleCategory;
import rich.modules.module.setting.implement.BooleanSetting;
import rich.modules.module.setting.implement.SliderSettings;
import rich.util.vk.VkApi;
import rich.util.vk.VkApi.VkTrack;

import javazoom.jl.decoder.*;
import javazoom.jl.player.AudioDevice;
import javazoom.jl.player.FactoryRegistry;

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

@Getter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MusicPlayer extends ModuleStructure {

    public static MusicPlayer getInstance() {
        return rich.util.Instance.get(MusicPlayer.class);
    }

    SliderSettings volume = new SliderSettings("Громкость", "Громкость музыки")
            .range(0f, 100f).setValue(50f);

    BooleanSetting shuffle = new BooleanSetting("Перемешать", "Случайный порядок треков");

    BooleanSetting repeat = new BooleanSetting("Повтор", "Повторять текущий трек");

    @NonFinal List<VkTrack> playlist = new ArrayList<>();
    @NonFinal int currentTrackIndex = -1;
    @NonFinal String currentTrackName = "";
    @NonFinal boolean playing = false;
    @NonFinal long positionMs = 0;
    @NonFinal long durationMs = 0;
    @NonFinal boolean userToggled = false;
    @NonFinal boolean vkReady = false;

    @NonFinal private Clip currentClip;
    @NonFinal private Thread playbackThread;
    @NonFinal private volatile boolean stopRequested = false;

    @NonFinal private VkApi vkApi;
    @NonFinal private HttpClient httpClient;

    public MusicPlayer() {
        super("Music Player", "Музыкальный проигрыватель", ModuleCategory.RENDER);
        settings(volume, shuffle, repeat);
    }

    private String readToken() {
        try {
            File tokenFile = new File("Rich/configs/vk_token.txt");
            if (tokenFile.exists()) {
                return new String(Files.readAllBytes(tokenFile.toPath()), StandardCharsets.UTF_8).trim();
            }
            File altFile = new File("../Rich/configs/vk_token.txt");
            if (altFile.exists()) {
                return new String(Files.readAllBytes(altFile.toPath()), StandardCharsets.UTF_8).trim();
            }
            return "";
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    public void activate() {
        stopRequested = false;
        userToggled = true;
        vkReady = false;

        String token = readToken();
        if (token.isEmpty()) {
            currentTrackName = "VK: no token";
            return;
        }

        vkApi = new VkApi(token);
        httpClient = HttpClient.newHttpClient();

        new Thread(() -> {
            try {
                List<VkTrack> tracks = vkApi.fetchTracks();
                if (tracks != null && !tracks.isEmpty()) {
                    playlist.clear();
                    playlist.addAll(tracks);
                    if (shuffle.isValue()) Collections.shuffle(playlist);
                    currentTrackIndex = 0;
                    vkReady = true;
                    updateTrackName();
                    playCurrentTrack();
                } else {
                    currentTrackName = "VK: no tracks found";
                }
            } catch (Exception e) {
                currentTrackName = "VK error: " + e.getMessage();
            }
        }, "VK-FetchThread").start();
    }

    @Override
    public void deactivate() {
        userToggled = false;
        stopPlayback();
    }

    @EventHandler
    public void onTick(TickEvent e) {
        if (mc.player == null) return;
        if (currentClip != null && currentClip.isOpen()) {
            positionMs = currentClip.getMicrosecondPosition() / 1000;
            if (!currentClip.isActive() || positionMs >= durationMs - 100) {
                if (repeat.isValue()) {
                    playCurrentTrack();
                } else {
                    nextTrack();
                }
            }
        }
    }

    public void playCurrentTrack() {
        if (playlist.isEmpty() || !vkReady) return;
        if (currentTrackIndex < 0 || currentTrackIndex >= playlist.size()) {
            currentTrackIndex = 0;
        }
        stopPlayback();
        updateTrackName();

        VkTrack track = playlist.get(currentTrackIndex);
        stopRequested = false;

        playbackThread = new Thread(() -> {
            try {
                playVkTrack(track);
            } catch (Exception ex) {
                playing = false;
            }
        }, "MusicPlayer-Thread");
        playbackThread.setDaemon(true);
        playbackThread.start();
    }

    private void playVkTrack(VkTrack track) throws Exception {
        String trackUrl = track.url;

        if (trackUrl == null || trackUrl.isEmpty()) {
            playing = false;
            return;
        }

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(trackUrl))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .timeout(java.time.Duration.ofSeconds(30))
                .GET()
                .build();

        HttpResponse<InputStream> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofInputStream());

        try (InputStream is = resp.body();
             BufferedInputStream bis = new BufferedInputStream(is)) {

            Bitstream bitstream = new Bitstream(bis);
            Decoder decoder = new Decoder();
            AudioDevice audioDevice = FactoryRegistry.systemRegistry().createAudioDevice();

            this.currentClip = null;
            playing = true;
            positionMs = 0;

            Header header = bitstream.readFrame();
            if (header == null) {
                playing = false;
                return;
            }

            int channels = (header.mode() == Header.SINGLE_CHANNEL) ? 1 : 2;
            SampleBuffer sampleBuffer = new SampleBuffer(header.frequency(), channels);
            decoder.setOutputBuffer(sampleBuffer);

            decoder.decodeFrame(header, bitstream);
            short[] samples = sampleBuffer.getBuffer();
            int length = sampleBuffer.getBufferLength();
            audioDevice.write(samples, 0, length);

            long startTime = System.currentTimeMillis();
            int frameIndex = 1;

            while (!stopRequested) {
                header = bitstream.readFrame();
                if (header == null) break;

                decoder.decodeFrame(header, bitstream);
                samples = sampleBuffer.getBuffer();
                length = sampleBuffer.getBufferLength();
                audioDevice.write(samples, 0, length);

                frameIndex++;
                if (frameIndex % 10 == 0) {
                    positionMs = System.currentTimeMillis() - startTime;
                }
            }

            durationMs = track.duration > 0 ? (long) track.duration * 1000 : System.currentTimeMillis() - startTime;
            audioDevice.flush();
            playing = false;

            if (!stopRequested) {
                Util.getMainWorkerExecutor().execute(() -> {
                    if (!isState()) return;
                    if (repeat.isValue()) {
                        playCurrentTrack();
                    } else {
                        nextTrack();
                    }
                });
            }
        }
    }

    public void stopPlayback() {
        stopRequested = true;
        playing = false;
        if (currentClip != null) {
            try {
                if (currentClip.isOpen()) {
                    currentClip.stop();
                    currentClip.close();
                }
            } catch (Exception e) {}
            currentClip = null;
        }
        if (playbackThread != null && playbackThread.isAlive()) {
            playbackThread.interrupt();
            playbackThread = null;
        }
        positionMs = 0;
    }

    public void nextTrack() {
        if (playlist.isEmpty() || !vkReady) return;
        if (shuffle.isValue()) {
            currentTrackIndex = (int) (Math.random() * playlist.size());
        } else {
            currentTrackIndex = (currentTrackIndex + 1) % playlist.size();
        }
        if (userToggled) {
            playCurrentTrack();
        } else {
            updateTrackName();
        }
    }

    public void prevTrack() {
        if (playlist.isEmpty() || !vkReady) return;
        if (positionMs > 3000) {
            playCurrentTrack();
            return;
        }
        currentTrackIndex = (currentTrackIndex - 1 + playlist.size()) % playlist.size();
        if (userToggled) {
            playCurrentTrack();
        } else {
            updateTrackName();
        }
    }

    public void togglePlayPause() {
        if (currentClip == null) return;
        if (playing) {
            currentClip.stop();
            playing = false;
        } else {
            currentClip.start();
            playing = true;
        }
    }

    public void updateVolume() {
        if (currentClip != null && currentClip.isOpen()) {
            FloatControl gainControl = (FloatControl) currentClip.getControl(FloatControl.Type.MASTER_GAIN);
            float dB = (float) (Math.log(Math.max(volume.getValue(), 0.01) / 100.0) / Math.log(10.0) * 20.0);
            dB = Math.max(gainControl.getMinimum(), Math.min(gainControl.getMaximum(), dB));
            gainControl.setValue(dB);
        }
    }

    public float getProgress() {
        if (durationMs <= 0) return 0;
        return Math.min((float) positionMs / durationMs, 1.0f);
    }

    private void updateTrackName() {
        if (playlist.isEmpty() || currentTrackIndex < 0 || currentTrackIndex >= playlist.size()) {
            currentTrackName = !vkReady && readToken().isEmpty() ? "VK: enter token in launcher" : "No tracks";
            return;
        }
        VkTrack track = playlist.get(currentTrackIndex);
        currentTrackName = (track.artist.isEmpty() ? "" : track.artist + " - ") + track.title;
    }

    public String formatTime(long ms) {
        long totalSec = ms / 1000;
        long min = totalSec / 60;
        long sec = totalSec % 60;
        return String.format("%d:%02d", min, sec);
    }
}
