package excel.screens.clickgui.impl.background.render;

import net.minecraft.client.gui.DrawContext;
import excel.modules.impl.render.Hud;
import excel.util.render.Render2D;
import excel.util.render.font.Fonts;

import java.awt.*;

public class BackgroundRenderer {

    private int getAccentR() {
        int c = Hud.getInstance().getAccentRGB();
        return (c >> 16) & 0xFF;
    }

    private int getAccentG() {
        int c = Hud.getInstance().getAccentRGB();
        return (c >> 8) & 0xFF;
    }

    private int getAccentB() {
        return Hud.getInstance().getAccentRGB() & 0xFF;
    }

    public void render(DrawContext context, float bgX, float bgY, float alphaMultiplier) {
        int baseAlpha = (int) (160 * alphaMultiplier);
        int aR = getAccentR(), aG = getAccentG(), aB = getAccentB();
        int[] gradientColors = {
                new Color(40, 40, 50, baseAlpha).getRGB(),
                new Color(20, 20, 30, baseAlpha).getRGB(),
                new Color(40, 40, 50, baseAlpha).getRGB(),
                new Color(20, 20, 30, baseAlpha).getRGB(),
                new Color(40, 40, 45, baseAlpha).getRGB()
        };

        Render2D.gradientRect(bgX, bgY, 400, 250, gradientColors, 15);
    }

    public void renderCategoryPanel(float bgX, float bgY, float bgHeight, float alphaMultiplier) {
        int panelAlpha = (int) (25 * alphaMultiplier);
        int blurAlpha = (int) (155 * alphaMultiplier);
        int aR = getAccentR(), aG = getAccentG(), aB = getAccentB();

        Render2D.rect(bgX + 7.5f, bgY + 7.5f, 80, bgHeight - 15, new Color(128, 128, 128, panelAlpha).getRGB(), 10);
        Render2D.outline(bgX + 7.5f, bgY + 7.5f, 80, bgHeight - 15, 0.5f, new Color(aR, aG, aB, (int) (120 * alphaMultiplier)).getRGB(), 10);

        Render2D.outline(bgX + 12.5f, bgY + 220.5f, 70, 17, 0.5f, new Color(aR, aG, aB, (int) (100 * alphaMultiplier)).getRGB(), 5);

        Fonts.GUI_ICONS.draw("X", bgX + 21.15f, bgY + 217.5f, 19, new Color(aR, aG, aB, (int) (140 * alphaMultiplier)).getRGB());
        Fonts.GUI_ICONS.draw("Y", bgX + 40f, bgY + 217f, 20, new Color(aR, aG, aB, (int) (140 * alphaMultiplier)).getRGB());
        Fonts.GUI_ICONS.draw("Z", bgX + 60f, bgY + 217f, 20, new Color(aR, aG, aB, (int) (140 * alphaMultiplier)).getRGB());

        Render2D.blur(bgX + 12.5f, bgY + 220.5f, 70, 17, 4, 5, new Color(25, 25, 25, blurAlpha).getRGB());

        float textSize = 5f;
        String changelogText = "v1.0.14";
        float textWidth = Fonts.BOLD.getWidth(changelogText, textSize);
        float textHeight = Fonts.BOLD.getHeight(textSize);
        float centerX = bgX + 12.5f + (70 - textWidth) / 2f;
        float centerY = bgY + 220.5f + (17 - textHeight) / 2f;
        Fonts.BOLD.draw(changelogText, centerX, centerY, textSize, new Color(aR, aG, aB, (int) (255 * alphaMultiplier)).getRGB());
    }
}