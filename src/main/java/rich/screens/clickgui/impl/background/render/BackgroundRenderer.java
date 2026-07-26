package rich.screens.clickgui.impl.background.render;

import net.minecraft.client.gui.DrawContext;
import rich.screens.clickgui.impl.theme.ClickGuiTheme;
import rich.util.render.Render2D;

import java.awt.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public class BackgroundRenderer {

    private float animPhase = 0f;
    private long lastTime = System.currentTimeMillis();

    private final List<float[]> particles = new ArrayList<>();
    private static final int MAX_PARTICLES = 14;
    private float scanlineY = 0f;
    private static final Random rng = new Random();

    public void render(DrawContext context, float bgX, float bgY, float alphaMultiplier) {
        long now = System.currentTimeMillis();
        float dt = Math.min((now - lastTime) / 1000f, 0.1f);
        lastTime = now;
        animPhase += dt;

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

        int accentR = (ClickGuiTheme.ACCENT_ARGB >> 16) & 0xFF;
        int accentG = (ClickGuiTheme.ACCENT_ARGB >> 8) & 0xFF;
        int accentB = ClickGuiTheme.ACCENT_ARGB & 0xFF;

        float glowPhase = (float) (Math.sin(animPhase * 0.5) * 0.5 + 0.5);
        int glowAlpha = (int) (15 * glowPhase * alphaMultiplier);
        int glowColor = new Color(accentR, accentG, accentB, glowAlpha).getRGB();
        Render2D.rect(bgX + 4, bgY, ClickGuiTheme.BG_WIDTH - 8, 1.5f, glowColor, 1f);

        float cornerDotPhase = (float) (Math.sin(animPhase * 0.3 + 1) * 0.5 + 0.5);
        int dotAlpha = (int) (35 * cornerDotPhase * alphaMultiplier);
        if (dotAlpha > 0) {
            Render2D.rect(bgX + ClickGuiTheme.BG_WIDTH - 16, bgY + 12, 3, 3,
                    new Color(accentR, accentG, accentB, dotAlpha).getRGB(), 1.5f);
        }

        renderParticles(bgX, bgY, alphaMultiplier, dt, accentR, accentG, accentB);
        renderScanline(bgX, bgY, alphaMultiplier, dt, accentR, accentG, accentB);
    }

    private void renderParticles(float bgX, float bgY, float alpha, float dt, int aR, int aG, int aB) {
        if (particles.size() < MAX_PARTICLES && rng.nextFloat() < 0.12f) {
            float px = bgX + 10 + rng.nextFloat() * (ClickGuiTheme.BG_WIDTH - 20);
            float py = bgY + ClickGuiTheme.BG_HEIGHT + 5;
            float size = 1f + rng.nextFloat() * 1.5f;
            float speed = 10 + rng.nextFloat() * 8;
            float life = 4 + rng.nextFloat() * 5;
            float drift = (rng.nextFloat() - 0.5f) * 6;
            float baseA = 15 + rng.nextFloat() * 30;
            particles.add(new float[]{px, py, size, speed, life, life, drift, baseA});
        }
        Iterator<float[]> it = particles.iterator();
        while (it.hasNext()) {
            float[] p = it.next();
            p[1] -= p[3] * dt;
            p[0] += p[6] * dt;
            p[4] -= dt;
            if (p[4] <= 0 || p[1] < bgY - 5) { it.remove(); continue; }
            float ratio = p[4] / p[5];
            float fadeIn = Math.min(1f, ratio * 3f);
            float fadeOut = Math.min(1f, (1f - ratio) * 3f);
            int a = (int) (p[7] * fadeIn * fadeOut * alpha);
            if (a > 1) {
                Render2D.rect(p[0], p[1], p[2], p[2], new Color(aR, aG, aB, a).getRGB(), p[2] / 2f);
            }
        }
    }

    private void renderScanline(float bgX, float bgY, float alpha, float dt, int aR, int aG, int aB) {
        scanlineY += 30 * dt;
        if (scanlineY > ClickGuiTheme.BG_HEIGHT + 5) scanlineY = -3;
        float ly = bgY + scanlineY;
        int la = (int) (10 * alpha);
        if (la > 0) {
            int c = new Color(aR, aG, aB, la).getRGB();
            Render2D.rect(bgX + 3, ly, ClickGuiTheme.BG_WIDTH - 6, 0.5f, c, 0);
            int fa = (int) (4 * alpha);
            if (fa > 0) {
                int fc = new Color(aR, aG, aB, fa).getRGB();
                Render2D.rect(bgX + 5, ly - 1, ClickGuiTheme.BG_WIDTH - 10, 1, fc, 0);
                Render2D.rect(bgX + 5, ly + 0.5f, ClickGuiTheme.BG_WIDTH - 10, 1, fc, 0);
            }
        }
    }

    public void renderCategoryPanel(float bgX, float bgY, float bgHeight, float alphaMultiplier) {
        int panelAlpha = (int) (20 * alphaMultiplier);
        int outlineAlpha = (int) (60 * alphaMultiplier);

        float catW = ClickGuiTheme.CATEGORY_PANEL_WIDTH;
        float inset = ClickGuiTheme.PANEL_INSET;
        float radius = ClickGuiTheme.PANEL_CORNER_RADIUS;

        int catBg = new Color(6, 10, 18, panelAlpha).getRGB();
        int catBorder = new Color(20, 32, 56, outlineAlpha).getRGB();

        Render2D.rect(bgX + inset, bgY + inset, catW, bgHeight - inset * 2, catBg, radius);
        Render2D.outline(bgX + inset, bgY + inset, catW, bgHeight - inset * 2, 0.5f, catBorder, radius);
    }
}
