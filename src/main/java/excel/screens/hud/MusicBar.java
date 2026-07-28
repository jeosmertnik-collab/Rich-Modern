package excel.screens.hud;

import net.minecraft.client.gui.DrawContext;
import excel.client.draggables.AbstractHudElement;
import excel.Initialization;
import excel.util.media.MediaPlayer;
import excel.util.render.Render2D;
import excel.util.render.font.Fonts;

import java.awt.Color;

public class MusicBar extends AbstractHudElement {
    public MusicBar() {
        super("MusicBar", 394, 4, 165, 16, true);
    }

    @Override
    public void drawDraggable(DrawContext context, int alpha) {
        var mediaPlayer = Initialization.getInstance().getManager().getMediaPlayer();

        String track = mediaPlayer.getLastTitle() +
                (mediaPlayer.getArtist().isEmpty() ? "" : " - " + mediaPlayer.getArtist());

        boolean mediaNull = mediaPlayer.fullNullCheck();

        float y = getY();
        float baseHeight = 30f;
        float padding = 2f;
        float progressBarHeight = 3f;
        float round = 6f;

        float textWidth = Fonts.BOLD.getWidth(track, 7);
        float containerWidth = textWidth + padding * 2 + 8;
        float x = getX();
        setWidth((int) containerWidth);
        int[] color = new int[]{new Color(30, 35, 50).getRGB()};
        setHeight((int) baseHeight);
        float xStateFont = x + padding;
        float yStateFont = y + (baseHeight / 2f) - (Fonts.BOLD.getHeight(7f) / 2f) + 1;
        double cur = mediaPlayer.getDuration();
        double pos = mediaPlayer.getPosition();

        if (!mediaNull) {
            Render2D.gradientRect(x, y, containerWidth, baseHeight, color, round);
            Fonts.BOLD.draw(track, xStateFont + 2, yStateFont - 6, 7F, new Color(255, 255, 255).getRGB());

            if (cur > 0) {
                float progressPercent = (float) Math.min(1.0, Math.max(0.0, pos / cur));
                float barMaxWidth = containerWidth - (padding * 2);
                float barCurrentWidth = barMaxWidth * progressPercent;

                float barX = x + padding;
                float barY = y + baseHeight - progressBarHeight - 3;
                float barOffset = 3;
                float xOffsetValue = 2;
                Render2D.gradientRect(barX + xOffsetValue, barY - barOffset, barMaxWidth - xOffsetValue, progressBarHeight, new int[]{new Color(5, 61, 78).getRGB()}, 1f);
                Render2D.gradientRect(barX + xOffsetValue, barY - barOffset, barCurrentWidth, progressBarHeight, new int[]{new Color(11, 117, 204).getRGB()}, 1f);

                String timeText = formatTime(pos) + " / " + formatTime(cur);
                Fonts.BOLD.draw(timeText, xStateFont + xOffsetValue, barY + progressBarHeight + 1 - barOffset - 12, 6F, new Color(200, 200, 200).getRGB());
            }
        }
    }

    private String formatTime(double seconds) {
        int totalSeconds = (int) seconds;
        int minutes = totalSeconds / 60;
        int secs = totalSeconds % 60;
        return String.format("%d:%02d", minutes, secs);
    }
}
