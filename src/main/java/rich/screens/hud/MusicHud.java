package rich.screens.hud;

import net.minecraft.client.gui.DrawContext;
import rich.client.draggables.AbstractHudElement;
import rich.modules.impl.render.Hud;
import rich.modules.impl.render.MusicPlayer;
import rich.util.render.Render2D;
import rich.util.render.font.Fonts;

import java.awt.*;

public class MusicHud extends AbstractHudElement {

    private int accentR = 100, accentG = 150, accentB = 255;

    private void updateAccent() {
        int c = Hud.getInstance().getAccentRGB();
        accentR = (c >> 16) & 0xFF;
        accentG = (c >> 8) & 0xFF;
        accentB = c & 0xFF;
    }

    public MusicHud() {
        super("MusicHud", 10, 200, 160, 32, true);
        stopAnimation();
    }

    @Override
    public boolean visible() {
        MusicPlayer player = MusicPlayer.getInstance();
        return player != null && player.isState();
    }

    @Override
    public void tick() {
        MusicPlayer player = MusicPlayer.getInstance();
        if (player != null && player.isState()) {
            startAnimation();
        } else {
            stopAnimation();
        }
    }

    @Override
    public void drawDraggable(DrawContext context, int alpha) {
        updateAccent();
        if (alpha <= 0) return;

        MusicPlayer player = MusicPlayer.getInstance();
        if (player == null) return;

        float alphaFactor = alpha / 255.0f;
        int bgAlpha = (int) (255 * alphaFactor);

        float x = getX();
        float y = getY();
        float w = 160;
        float h = player.getPlaylist().isEmpty() ? 20 : 32;

        setWidth((int) w);
        setHeight((int) h);

        Render2D.gradientRect(x, y, w, h,
                new int[]{
                        new Color(20, 22, 32, bgAlpha).getRGB(),
                        new Color(12, 14, 22, bgAlpha).getRGB(),
                        new Color(20, 22, 32, bgAlpha).getRGB(),
                        new Color(12, 14, 22, bgAlpha).getRGB()
                }, 5);

        Render2D.outline(x, y, w, h, 1.0f,
                new Color(accentR, accentG, accentB, (int) (bgAlpha * 0.35f)).getRGB(), 5);

        if (player.getPlaylist().isEmpty()) {
            Fonts.SFPRO_REGULAR.draw("VK Music", x + 8, y + 5, 5.5f,
                    new Color(180, 140, 140, bgAlpha).getRGB());
            Fonts.SFPRO_REGULAR.draw("Login via launcher", x + 8, y + 12, 4.5f,
                    new Color(120, 120, 140, bgAlpha).getRGB());
            return;
        }

        String trackName = player.getCurrentTrackName();
        if (trackName.isEmpty()) trackName = "No track";

        if (trackName.length() > 22) {
            trackName = trackName.substring(0, 20) + "..";
        }

        float textY = y + 5;
        Fonts.SFPRO_REGULAR.draw(trackName, x + 8, textY, 5.5f,
                new Color(220, 230, 255, bgAlpha).getRGB());

        String stateIcon = player.isPlaying() ? "a" : "b";
        int iconColor = new Color(accentR, accentG, accentB, bgAlpha).getRGB();
        Fonts.ICONS.draw(stateIcon, x + w - 16, textY - 0.5f, 7, iconColor);

        float barX = x + 8;
        float barY = y + 17;
        float barW = w - 16;
        float barH = 3;
        float barR = 1.5f;

        Render2D.rect(barX, barY, barW, barH,
                new Color(30, 35, 50, bgAlpha).getRGB(), barR);

        float progress = player.getProgress();
        if (progress > 0.005f) {
            Render2D.rect(barX, barY, barW * progress, barH,
                    new Color(accentR, accentG, accentB, bgAlpha).getRGB(), barR);
        }

        String timeStr = player.formatTime(player.getPositionMs()) + " / " + player.formatTime(player.getDurationMs());
        Fonts.SFPRO_REGULAR.draw(timeStr, barX, barY + 5, 4.5f,
                new Color(140, 150, 170, bgAlpha).getRGB());

        String trackInfo = (player.getCurrentTrackIndex() + 1) + "/" + player.getPlaylist().size();
        float infoWidth = Fonts.SFPRO_REGULAR.getWidth(trackInfo, 4.5f);
        Fonts.SFPRO_REGULAR.draw(trackInfo, x + w - 8 - infoWidth, barY + 5, 4.5f,
                new Color(140, 150, 170, bgAlpha).getRGB());
    }
}
