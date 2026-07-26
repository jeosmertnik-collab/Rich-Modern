package rich.screens.clickgui.impl.background.render;

import net.minecraft.client.gui.DrawContext;
import rich.screens.clickgui.impl.theme.ClickGuiTheme;
import rich.util.render.Render2D;
import rich.util.render.font.Fonts;

import java.awt.*;

public class BackgroundRenderer {

    public void render(DrawContext context, float bgX, float bgY, float alphaMultiplier) {
        int baseAlpha = (int) (255 * alphaMultiplier);

        int r1 = (ClickGuiTheme.BG_TOP_ARGB >> 16) & 0xFF;
        int g1 = (ClickGuiTheme.BG_TOP_ARGB >> 8) & 0xFF;
        int b1 = ClickGuiTheme.BG_TOP_ARGB & 0xFF;

        int r2 = (ClickGuiTheme.BG_CENTER_ARGB >> 16) & 0xFF;
        int g2 = (ClickGuiTheme.BG_CENTER_ARGB >> 8) & 0xFF;
        int b2 = ClickGuiTheme.BG_CENTER_ARGB & 0xFF;

        int r3 = (ClickGuiTheme.BG_BOTTOM_ARGB >> 16) & 0xFF;
        int g3 = (ClickGuiTheme.BG_BOTTOM_ARGB >> 8) & 0xFF;
        int b3 = ClickGuiTheme.BG_BOTTOM_ARGB & 0xFF;

        int[] gradientColors = {
                new Color(r1, g1, b1, baseAlpha).getRGB(),
                new Color(r2, g2, b2, baseAlpha).getRGB(),
                new Color(r1, g1, b1, baseAlpha).getRGB(),
                new Color(r2, g2, b2, baseAlpha).getRGB(),
                new Color(r3, g3, b3, baseAlpha).getRGB()
        };

        Render2D.gradientRect(bgX, bgY, ClickGuiTheme.BG_WIDTH, ClickGuiTheme.BG_HEIGHT, gradientColors, ClickGuiTheme.CORNER_RADIUS);
    }

    public void renderCategoryPanel(float bgX, float bgY, float bgHeight, float alphaMultiplier) {
        int panelAlpha = (int) (20 * alphaMultiplier);
        int outlineAlpha = (int) (255 * alphaMultiplier);

        float catW = ClickGuiTheme.CATEGORY_PANEL_WIDTH;
        float inset = ClickGuiTheme.PANEL_INSET;
        float radius = ClickGuiTheme.PANEL_CORNER_RADIUS;

        int catBg = new Color(10, 10, 18, panelAlpha).getRGB();
        int catBorder = new Color(40, 40, 60, outlineAlpha).getRGB();

        Render2D.rect(bgX + inset, bgY + inset, catW, bgHeight - inset * 2, catBg, radius);
        Render2D.outline(bgX + inset, bgY + inset, catW, bgHeight - inset * 2, 0.5f, catBorder, radius);
    }
}
