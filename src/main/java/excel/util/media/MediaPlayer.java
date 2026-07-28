package excel.util.media;

import excel.util.vk.VkApi.VkTrack;

import java.util.List;

public class MediaPlayer {

    private final VkMusicPlayer player;

    public MediaPlayer() {
        this.player = new VkMusicPlayer();
    }

    public void init() {
        player.init();
    }

    public VkMusicPlayer getPlayer() {
        return player;
    }

    public String getLastTitle() {
        String name = player.getCurrentTrackName();
        int dash = name.indexOf(" - ");
        if (dash >= 0) return name.substring(dash + 3);
        return name;
    }

    public String getArtist() {
        String name = player.getCurrentTrackName();
        int dash = name.indexOf(" - ");
        if (dash >= 0) return name.substring(0, dash);
        return "";
    }

    public boolean fullNullCheck() {
        return !player.isVkReady() || player.getPlaylist() == null || player.getPlaylist().isEmpty();
    }

    public double getDuration() {
        return player.getDurationMs() / 1000.0;
    }

    public double getPosition() {
        return player.getPositionMs() / 1000.0;
    }

    public boolean isPlaying() { return player.isPlaying(); }
    public void togglePlayPause() { player.togglePlayPause(); }
    public void nextTrack() { player.nextTrack(); }
    public void prevTrack() { player.prevTrack(); }
    public void setVolume(float vol) { player.setVolume(vol); }
    public float getVolume() { return player.getVolume(); }
    public List<VkTrack> getPlaylist() { return player.getPlaylist(); }
    public int getCurrentTrackIndex() { return player.getCurrentTrackIndex(); }
}
