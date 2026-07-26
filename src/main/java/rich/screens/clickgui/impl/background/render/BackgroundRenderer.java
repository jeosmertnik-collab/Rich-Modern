package rich.screens.clickgui.impl.background.render;

import net.minecraft.client.gui.DrawContext;
import rich.util.render.Render2D;
import rich.util.render.font.Fonts;

import java.awt.*;

public class BackgroundRenderer {

    public void render(DrawContext context, float bgX, float bgY, float alphaMultiplier) {
        int baseAlpha = (int) (255 * alphaMultiplier);
        int[] gradientColors = {
                new Color(26, 26, 26, baseAlpha).getRGB(),
                new Color(0, 0, 0, baseAlpha).getRGB(),
                new Color(26, 26, 26, baseAlpha).getRGB(),
                new Color(0, 0, 0, baseAlpha).getRGB(),
                new Color(26, 26, 20, baseAlpha).getRGB()
        };

        Render2D.gradientRect(bgX, bgY, 400, 250, gradientColors, 15);
    }

    public void renderCategoryPanel(float bgX, float bgY, float bgHeight, float alphaMultiplier) {
        int panelAlpha = (int) (25 * alphaMultiplier);
        int outlineAlpha = (int) (255 * alphaMultiplier);
        int blurAlpha = (int) (155 * alphaMultiplier);

        Render2D.rect(bgX + 7.5f, bgY + 7.5f, 80, bgHeight - 15, new Color(128, 128, 128, panelAlpha).getRGB(), 10);
        Render2D.outline(bgX + 7.5f, bgY + 7.5f, 80, bgHeight - 15, 0.5f, new Color(55, 55, 55, outlineAlpha).getRGB(), 10);

        Render2D.outline(bgX + 12.5f, bgY + 220.5f, 70, 17, 0.5f, new Color(55, 55, 55, outlineAlpha).getRGB(), 5);

        Fonts.GUI_ICONS.draw("X", bgX + 21.15f, bgY + 217.5f, 19, new Color(58, 58, 58, outlineAlpha).getRGB());
        Fonts.GUI_ICONS.draw("Y", bgX + 40f, bgY + 217f, 20, new Color(58, 58, 58, outlineAlpha).getRGB());
        Fonts.GUI_ICONS.draw("Z", bgX + 60f, bgY + 217f, 20, new Color(58, 58, 58, outlineAlpha).getRGB());

        Render2D.blur(bgX + 12.5f, bgY + 220.5f, 70, 17, 4, 5, new Color(25, 25, 25, blurAlpha).getRGB());

        float textSize = 4.5f;
        float lineY = bgY + 221.5f;
        float lineHeight = 5.5f;
        int textAlpha = (int) (180 * alphaMultiplier);
        int accentAlpha = (int) (220 * alphaMultiplier);

        String[] changelog = {
                "v1.0.12 - Changelog",
                "",
                "ClickGUI rework",
                "Accent color picker",
                "New HUD theme (SFPRO)",
                "TargetHud items",
                "Account name in HUD",
                "Config panel changelog"
        };

        for (String line : changelog) {
            if (line.isEmpty()) {
                lineY += lineHeight * 0.5f;
                continue;
            }
            if (line.startsWith("v")) {
                Fonts.SFPRO_REGULAR.draw(line, bgX + 15f, lineY, textSize,
                        new Color(100, 150, 255, accentAlpha).getRGB());
            } else {
                Fonts.SFPRO_REGULAR.draw(line, bgX + 15f, lineY, textSize,
                        new Color(150, 150, 150, textAlpha).getRGB());
            }
            lineY += lineHeight;
        }
    }
}
