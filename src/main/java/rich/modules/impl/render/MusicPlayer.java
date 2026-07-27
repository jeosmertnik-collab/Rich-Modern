package rich.modules.impl.render;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;
import rich.events.api.EventHandler;
import rich.events.impl.TickEvent;
import rich.modules.module.ModuleStructure;
import rich.modules.module.category.ModuleCategory;
import rich.modules.module.setting.implement.BooleanSetting;
import rich.modules.module.setting.implement.SliderSettings;

import javazoom.jl.decoder.*;
import javazoom.jl.player.AudioDevice;
import javazoom.jl.player.FactoryRegistry;

import javax.sound.sampled.*;
import java.io.*;
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

    @NonFinal List<File> playlist = new ArrayList<>();
    @NonFinal int currentTrackIndex = -1;
    @NonFinal String currentTrackName = "";
    @NonFinal boolean playing = false;
    @NonFinal long positionMs = 0;
    @NonFinal long durationMs = 0;
    @NonFinal boolean userToggled = false;

    @NonFinal private Clip currentClip;
    @NonFinal private Thread playbackThread;
    @NonFinal private volatile boolean stopRequested = false;

    private static final String MUSIC_DIR_NAME = "RichMusic";
    private static final String[] EXTENSIONS = {"mp3", "ogg", "wav"};

    public MusicPlayer() {
        super("Music Player", "Музыкальный проигрыватель", ModuleCategory.RENDER);
        settings(volume, shuffle, repeat);
    }

    @Override
    public void activate() {
        stopRequested = false;
        userToggled = true;
        scanMusicFiles();
        if (!playlist.isEmpty() && currentTrackIndex == -1) {
            currentTrackIndex = 0;
            updateTrackName();
        }
        if (!playlist.isEmpty()) {
            playCurrentTrack();
        }
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
        if (playlist.isEmpty()) return;
        if (currentTrackIndex < 0 || currentTrackIndex >= playlist.size()) {
            currentTrackIndex = 0;
        }
        stopPlayback();
        updateTrackName();

        File file = playlist.get(currentTrackIndex);
        stopRequested = false;

        playbackThread = new Thread(() -> {
            try {
                if (file.getName().toLowerCase().endsWith(".mp3")) {
                    playMp3(file);
                } else {
                    playWav(file);
                }
            } catch (Exception ex) {
                playing = false;
            }
        }, "MusicPlayer-Thread");
        playbackThread.setDaemon(true);
        playbackThread.start();
    }

    private void playMp3(File file) throws Exception {
        try (FileInputStream fis = new FileInputStream(file);
             BufferedInputStream bis = new BufferedInputStream(fis)) {
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

            durationMs = System.currentTimeMillis() - startTime;
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
        } catch (Exception e) {
            playing = false;
        }
    }

    private void playWav(File file) throws Exception {
        AudioInputStream stream = AudioSystem.getAudioInputStream(file);
        AudioFormat baseFormat = stream.getFormat();

        if (baseFormat.getEncoding() != AudioFormat.Encoding.PCM_SIGNED) {
            AudioFormat targetFormat = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    baseFormat.getSampleRate(),
                    16,
                    baseFormat.getChannels(),
                    baseFormat.getChannels() * 2,
                    baseFormat.getSampleRate(),
                    false
            );
            stream = AudioSystem.getAudioInputStream(targetFormat, stream);
        }

        Clip clip = AudioSystem.getClip();
        clip.open(stream);
        this.currentClip = clip;

        durationMs = clip.getMicrosecondLength() / 1000;
        positionMs = 0;
        playing = true;

        updateVolume();

        clip.addLineListener(event -> {
            if (event.getType() == LineEvent.Type.STOP && !stopRequested) {
                Util.getMainWorkerExecutor().execute(() -> {
                    if (!isState()) return;
                    if (repeat.isValue()) {
                        playCurrentTrack();
                    } else {
                        nextTrack();
                    }
                });
            }
        });

        clip.start();
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
        if (playlist.isEmpty()) return;
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
        if (playlist.isEmpty()) return;
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
            currentTrackName = "";
            return;
        }
        String name = playlist.get(currentTrackIndex).getName();
        int lastDot = name.lastIndexOf('.');
        currentTrackName = lastDot > 0 ? name.substring(0, lastDot) : name;
    }

    private void scanMusicFiles() {
        playlist.clear();
        File musicDir = new File(MUSIC_DIR_NAME);
        if (!musicDir.exists()) {
            musicDir.mkdirs();
            return;
        }
        scanDir(musicDir);
        if (shuffle.isValue()) {
            Collections.shuffle(playlist);
        }
    }

    private void scanDir(File dir) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.isDirectory()) {
                scanDir(file);
            } else if (isMusicFile(file)) {
                playlist.add(file);
            }
        }
    }

    private boolean isMusicFile(File file) {
        String name = file.getName().toLowerCase();
        for (String ext : EXTENSIONS) {
            if (name.endsWith("." + ext)) return true;
        }
        return false;
    }

    public String formatTime(long ms) {
        long totalSec = ms / 1000;
        long min = totalSec / 60;
        long sec = totalSec % 60;
        return String.format("%d:%02d", min, sec);
    }
}
