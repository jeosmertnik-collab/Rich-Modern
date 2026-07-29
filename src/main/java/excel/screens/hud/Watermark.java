package excel.screens.hud;

import net.minecraft.client.gui.DrawContext;
import excel.client.draggables.AbstractHudElement;
import excel.modules.impl.render.Hud;
import excel.util.render.Render2D;
import excel.util.render.font.Fonts;
import excel.util.tps.TPSCalculate;

import java.awt.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class Watermark extends AbstractHudElement {

    private String lastFps = "";
    private String oldFps = "";
    private long fpsAnimationStart = 0;

    private String lastTime = "";
    private String oldTime = "";
    private long timeAnimationStart = 0;

    private String lastTps = "";
    private String oldTps = "";
    private long tpsAnimationStart = 0;

    private static final long ANIMATION_DURATION = 300;
    private static final float ANIMATION_OFFSET = 10.0f;

    private int accentR = 100, accentG = 150, accentB = 255;
    private void updateAccent() {
        int c = Hud.getInstance().getAccentRGB();
        accentR = (c >> 16) & 0xFF;
        accentG = (c >> 8) & 0xFF;
        accentB = c & 0xFF;
    }

    public Watermark() {
        super("Watermark", 10, 10, 200, 24, false);
        startAnimation();
    }

    @Override
    public void tick() {
    }

    private int clampAlpha(float alpha) {
        return Math.max(0, Math.min(255, (int) (alpha * 255)));
    }

    @Override
    public void drawDraggable(DrawContext context, int alpha) {
        if (alpha <= 0) return;
        updateAccent();

        float x = -5;
        float y = 5;

        String fpsNumber = String.valueOf(mc.getCurrentFps());
        String fpsText = "fps";
        String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));

        boolean showTps = Hud.getInstance() != null && Hud.getInstance().showTps.isValue();

        float tpsValue = 20.0f;
        if (TPSCalculate.getInstance() != null) {
            tpsValue = TPSCalculate.getInstance().getTpsRounded();
        }
        String tpsNumber = String.format("%.1f", tpsValue);
        String tpsText = "tps";

        long currentTime = System.currentTimeMillis();

        if (!fpsNumber.equals(lastFps)) {
            oldFps = lastFps;
            lastFps = fpsNumber;
            fpsAnimationStart = currentTime;
        }

        if (!time.equals(lastTime)) {
            oldTime = lastTime;
            lastTime = time;
            timeAnimationStart = currentTime;
        }

        if (!tpsNumber.equals(lastTps)) {
            oldTps = lastTps;
            lastTps = tpsNumber;
            tpsAnimationStart = currentTime;
        }

        float fpsAnimation = Math.min(1.0f, (currentTime - fpsAnimationStart) / (float) ANIMATION_DURATION);
        float timeAnimation = Math.min(1.0f, (currentTime - timeAnimationStart) / (float) ANIMATION_DURATION);
        float tpsAnimation = Math.min(1.0f, (currentTime - tpsAnimationStart) / (float) ANIMATION_DURATION);

        String richText = mc.getSession().getUsername();
        float richWidth = Fonts.SFPRO_REGULAR.getWidth(richText, 6);
        float fpsNumberWidth = Fonts.SFPRO_REGULAR.getWidth(fpsNumber, 6);
        float fpsTextWidth = Fonts.SFPRO_REGULAR.getWidth(fpsText, 6);
        float timeWidth = Fonts.SFPRO_REGULAR.getWidth(time, 6);
        float tpsNumberWidth = Fonts.SFPRO_REGULAR.getWidth(tpsNumber, 6);
        float tpsTextWidth = Fonts.SFPRO_REGULAR.getWidth(tpsText, 6);

        float totalWidth = 10 + 12 + 2 + richWidth + 8 + 12 + fpsNumberWidth + 2 + fpsTextWidth + 8 + 12 + timeWidth;

        if (showTps) {
            totalWidth += 8 + 12 + 2 + tpsNumberWidth + 2 + tpsTextWidth + 6;
        }

        totalWidth += 12;

        setWidth((int) totalWidth);
        setHeight(22);

        int bgAlpha = 120;
        int outlineAlpha = 100;

        Render2D.gradientRect(x + 12, y + 3, totalWidth, 20,
                new int[]{
                        new Color(25, 30, 40, bgAlpha).getRGB(),
                        new Color(15, 20, 30, bgAlpha).getRGB(),
                        new Color(25, 30, 40, bgAlpha).getRGB(),
                        new Color(15, 20, 30, bgAlpha).getRGB()
                },
                4);

        Render2D.glowOutline(x + 12, y + 3, totalWidth, 20, 1.0f,
                new Color(accentR, accentG, accentB, outlineAlpha).getRGB(), 4, 1.0f, 3.0f);

        float textY = y + 7;
        float offsetX = x + 12 + 5;

        Fonts.BOLD.draw("Excel", offsetX, textY + 2, 9, new Color(accentR, accentG, accentB, 255).getRGB());
        offsetX += Fonts.BOLD.getWidth("Excel", 9) + 3;

        // Rich text
        Fonts.SFPRO_REGULAR.draw(richText, offsetX, textY + 3, 6, new Color(220, 230, 255, 255).getRGB());
        offsetX += richWidth + 4;

        Fonts.TEST.draw("»", offsetX, textY + 1.5f, 8, new Color(accentR, accentG, accentB, 255).getRGB());
        offsetX += 8;

        Fonts.CATEGORY_ICONS.draw("b", offsetX, textY + 2f, 9, new Color(180, 190, 210, 255).getRGB());
        offsetX += 12;

        float fpsOffsetX = offsetX;
        drawAnimatedTextPerChar(fpsNumber, oldFps, fpsOffsetX, textY + 3, 6, fpsAnimation);
        offsetX += fpsNumberWidth + 2;

        Fonts.SFPRO_REGULAR.draw(fpsText, offsetX, textY + 3, 6, new Color(180, 190, 210, 255).getRGB());
        offsetX += fpsTextWidth + 4;

        Fonts.TEST.draw("»", offsetX, textY + 1.5f, 8, new Color(accentR, accentG, accentB, 255).getRGB());
        offsetX += 8;

        Fonts.CATEGORY_ICONS.draw("n", offsetX, textY + 2f, 9, new Color(180, 190, 210, 255).getRGB());
        offsetX += 12;

        float timeOffsetX = offsetX;
        drawAnimatedTextPerChar(time, oldTime, timeOffsetX, textY + 3, 6, timeAnimation);

        if (showTps) {
            offsetX += timeWidth + 4;

            Fonts.TEST.draw("»", offsetX, textY + 1.5f, 8, new Color(accentR, accentG, accentB, 255).getRGB());
            offsetX += 8;

            Fonts.ICONSTYPETHO.draw("t", offsetX, textY + 0.5f, 12, new Color(180, 190, 210, 255).getRGB());
            offsetX += 12 + 2;

            drawAnimatedTextPerChar(tpsNumber, oldTps, offsetX, textY + 3, 6, tpsAnimation);
            offsetX += tpsNumberWidth + 2;

            Fonts.SFPRO_REGULAR.draw(tpsText, offsetX, textY + 3, 6, new Color(180, 190, 210, 255).getRGB());
        }
    }

    private void drawAnimatedTextPerChar(String newText, String oldText, float x, float y, float size, float progress) {
        if (oldText.isEmpty() || progress >= 1.0f) {
            Fonts.SFPRO_REGULAR.draw(newText, x, y, size, new Color(220, 230, 255, 255).getRGB());
            return;
        }

        float offsetX = x;
        int maxLen = Math.max(newText.length(), oldText.length());

        String paddedNew = padLeft(newText, maxLen);
        String paddedOld = padLeft(oldText, maxLen);

        for (int i = 0; i < paddedNew.length(); i++) {
            char newChar = paddedNew.charAt(i);
            char oldChar = paddedOld.charAt(i);

            if (newChar == ' ' && oldChar == ' ') {
                continue;
            }

            float charWidth = Fonts.SFPRO_REGULAR.getWidth(String.valueOf(newChar != ' ' ? newChar : oldChar), size);

            boolean isNewDigit = Character.isDigit(newChar) || newChar == '.';
            boolean isOldDigit = Character.isDigit(oldChar) || oldChar == '.';
            boolean hasChanged = newChar != oldChar;

            if (!hasChanged || (!isNewDigit && !isOldDigit)) {
                if (newChar != ' ') {
                    Fonts.SFPRO_REGULAR.draw(String.valueOf(newChar), offsetX, y, size, new Color(220, 230, 255, 255).getRGB());
                }
            } else {
                float easedProgress = easeOutBack(progress);

                if (oldChar != ' ' && isOldDigit) {
                    float oldAlpha = 1.0f - easedProgress;
                    float oldOffsetY = easedProgress * ANIMATION_OFFSET;
                    int oldAlphaClamped = clampAlpha(oldAlpha);
                    if (oldAlphaClamped > 0) {
                        int oldColor = new Color(220, 230, 255, oldAlphaClamped).getRGB();
                        Fonts.SFPRO_REGULAR.draw(String.valueOf(oldChar), offsetX, y + oldOffsetY, size, oldColor);
                    }
                }

                if (newChar != ' ' && isNewDigit) {
                    float newAlpha = easedProgress;
                    float newOffsetY = (1.0f - easedProgress) * -ANIMATION_OFFSET;
                    int newAlphaClamped = clampAlpha(newAlpha);
                    if (newAlphaClamped > 0) {
                        int newColor = new Color(220, 230, 255, newAlphaClamped).getRGB();
                        Fonts.SFPRO_REGULAR.draw(String.valueOf(newChar), offsetX, y + newOffsetY, size, newColor);
                    }
                }
            }

            if (newChar != ' ') {
                offsetX += charWidth;
            }
        }
    }

    private String padLeft(String text, int length) {
        if (text.length() >= length) {
            return text;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length - text.length(); i++) {
            sb.append(' ');
        }
        sb.append(text);
        return sb.toString();
    }

    private float easeOutCubic(float t) {
        return 1.0f - (float) Math.pow(1.0 - t, 3);
    }

    private float easeOutBack(float t) {
        float c1 = 1.70158f;
        float c3 = c1 + 1;
        return 1 + c3 * (float) Math.pow(t - 1, 3) + c1 * (float) Math.pow(t - 1, 2);
    }
}
