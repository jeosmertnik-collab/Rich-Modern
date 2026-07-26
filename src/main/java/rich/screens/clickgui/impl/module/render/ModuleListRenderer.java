package rich.screens.clickgui.impl.module.render;

import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;
import rich.modules.module.ModuleStructure;
import rich.screens.clickgui.impl.module.handler.ModuleAnimationHandler;
import rich.screens.clickgui.impl.module.handler.ModuleBindHandler;
import rich.screens.clickgui.impl.module.handler.ModuleScrollHandler;
import rich.screens.clickgui.impl.module.util.ModuleDisplayHelper;
import rich.screens.clickgui.impl.theme.ClickGuiTheme;
import rich.util.render.Render2D;
import rich.util.render.shader.Scissor;
import rich.util.render.font.Fonts;

import java.awt.*;
import java.util.List;
import java.util.Map;

public class ModuleListRenderer {

    private static final float ITEM_H = 32f;
    private static final float CORNER_R = ClickGuiTheme.PANEL_CORNER_RADIUS;
    private static final float INSET = 3f;
    private static final float TOGGLE_W = 20f;
    private static final float TOGGLE_H = 11f;
    private static final float TOGGLE_R = 5.5f;
    private static final float KNOB_SIZE = 8f;
    private static final float STRIPE_W = 2.5f;
    private static final float BIND_H = 9f;
    private static final float BIND_MIN_W = 18f;
    private static final float BIND_PAD = 6f;
    private static final float BIND_SPEED = 12f;

    private final ModuleAnimationHandler animationHandler;
    private final ModuleBindHandler bindHandler;
    private final ModuleDisplayHelper displayHelper;

    public ModuleListRenderer(ModuleAnimationHandler ah, ModuleBindHandler bh, ModuleDisplayHelper dh) {
        this.animationHandler = ah;
        this.bindHandler = bh;
        this.displayHelper = dh;
    }

    public void render(DrawContext ctx, List<ModuleStructure> mods, ModuleStructure sel,
                       ModuleStructure binding, float x, float y, float w, float h,
                       float mx, float my, int gs, float alpha,
                       ModuleAnimationHandler ah, ModuleScrollHandler sh) {

        int panelBg = ClickGuiTheme.panelAlpha(0.3f * alpha);
        int panelBd = ClickGuiTheme.borderAlpha(0.45f * alpha);
        Render2D.rect(x, y, w, h, panelBg, CORNER_R);
        Render2D.outline(x, y, w, h, 0.5f, panelBd, CORNER_R);

        Scissor.enable(x + INSET, y + INSET - 1.5f, w - INSET * 2, h - INSET * 2 + 3, gs);

        if (ah.isCategoryTransitioning() && !ah.getOldModules().isEmpty()) {
            float oA = (1f - ah.getCategoryTransitionProgress()) * alpha;
            float oX = ah.easeInCubic(ah.getCategoryTransitionProgress()) * -ah.getCategorySlideDistance();
            float oS = 1f - ah.getCategoryTransitionProgress() * 0.1f;
            renderItems(ctx, ah.getOldModules(), ah.getOldModuleAnimations(), sel, binding,
                    x, y, w, h, mx, my, oA, oX, oS, (float) ah.getOldModuleDisplayScroll(), false, ah);
        }

        float nA, nX, nS;
        if (ah.isCategoryTransitioning()) {
            float ep = Math.max(0f, (ah.getCategoryTransitionProgress() - 0.2f) / 0.8f);
            ep = ah.easeOutQuart(ep);
            nA = ep * alpha;
            nX = (1f - ep) * ah.getCategorySlideDistance();
            nS = 0.92f + ep * 0.08f;
        } else {
            nA = alpha;
            nX = 0f;
            nS = 1f;
        }

        renderItems(ctx, mods, ah.getModuleAnimations(), sel, binding,
                x, y, w, h, mx, my, nA, nX, nS, (float) sh.getModuleDisplayScroll(), true, ah);

        Scissor.disable();
        renderFade(x, y + INSET, w, h - INSET * 2, sh.getModuleScrollTopFade() * alpha, sh.getModuleScrollBottomFade() * alpha);
    }

    private void renderItems(DrawContext ctx, List<ModuleStructure> list, Map<ModuleStructure, Float> anims,
                             ModuleStructure sel, ModuleStructure binding,
                             float x, float y, float w, float h, float mx, float my,
                             float alpha, float offX, float scale, float scroll, boolean inter, ModuleAnimationHandler ah) {
        if (alpha <= 0.01f) return;

        float startY = y + INSET + 2f + scroll;
        float centerY = y + h / 2f;
        float visTop = y + INSET;
        float visBot = y + h - INSET;
        float sw = w - 6;

        for (int i = 0; i < list.size(); i++) {
            ModuleStructure m = list.get(i);
            float my2 = startY + i * (ITEM_H + 2);
            if (my2 + ITEM_H < visTop || my2 > visBot) continue;

            float ip = anims.getOrDefault(m, 1f);
            float pa = ah.getPositionAnimations().getOrDefault(m, 1f);
            float aa = ah.getModuleAlphaAnimations().getOrDefault(m, 1f);
            float ca = ip * alpha * aa;
            if (ca <= 0.01f) continue;

            float iOff = (1f - ip) * 18f;
            float pOff = (1f - easeOutCubic(pa)) * 12f;
            float sY = centerY + (my2 - centerY) * scale;
            float sH = ITEM_H * scale;
            float aX = x + 3 + offX + iOff + pOff;

            boolean isSel = inter && m == sel;
            boolean isHi = inter && m == ah.getHighlightedModule() && ah.getHighlightAnimation() > 0.01f;
            float hover = inter ? ah.getHoverAnimations().getOrDefault(m, 0f) : 0f;
            float state = inter ? ah.getStateAnimations().getOrDefault(m, m.isState() ? 1f : 0f) : (m.isState() ? 1f : 0f);
            float fav = inter ? ah.getFavoriteAnimations().getOrDefault(m, 0f) : 0f;
            boolean hasSet = displayHelper.hasSettings(m);

            int bg;
            if (isSel) bg = ClickGuiTheme.moduleSelectedBg(ca);
            else bg = ClickGuiTheme.moduleBg(ca * (0.5f + hover * 0.5f));
            Render2D.rect(aX, sY, sw, sH, bg, 7);

            if (state > 0.5f) {
                int aR = (ClickGuiTheme.ACCENT_ARGB >> 16) & 0xFF;
                int aG = (ClickGuiTheme.ACCENT_ARGB >> 8) & 0xFF;
                int aB = ClickGuiTheme.ACCENT_ARGB & 0xFF;
                int stripeA = (int) (180 * state * ca);
                Render2D.rect(aX, sY + 4, STRIPE_W, sH - 8, new Color(aR, aG, aB, stripeA).getRGB(), 1.25f);
            }

            if (isSel) {
                float pulse = (float) (Math.sin(ah.getSelectedPulseAnimation()) * 0.5 + 0.5);
                float hlBoost = isHi ? ah.getHighlightAnimation() * 0.4f : 0f;
                int oA = (int) ((40 + 25 * pulse + 25 * hlBoost) * ca);
                int aR = (ClickGuiTheme.ACCENT_ARGB >> 16) & 0xFF;
                int aG = (ClickGuiTheme.ACCENT_ARGB >> 8) & 0xFF;
                int aB = ClickGuiTheme.ACCENT_ARGB & 0xFF;
                Render2D.outline(aX, sY, sw, sH, 0.5f, new Color(aR, aG, aB, oA).getRGB(), 7);
                int gA = (int) (12 * ca);
                Render2D.rect(aX, sY, sw, sH, ClickGuiTheme.glowColor(gA), 7);
            } else if (hover > 0.01f) {
                int oA = (int) (25 * hover * ca);
                int oC = ((oA & 0xFF) << 24) | (ClickGuiTheme.PANEL_BORDER_LIGHT_ARGB & 0xFFFFFF);
                Render2D.outline(aX, sY, sw, sH, 0.5f, oC, 7);
            }

            float toggleX = aX + sw - TOGGLE_W - 8;
            float toggleY = sY + (sH - TOGGLE_H) / 2f;
            renderToggle(toggleX, toggleY, state, ca);

            String name = m.getName();
            int tB;
            int tA;
            if (state > 0.5f) { tB = 245; tA = (int) (255 * ca); }
            else if (hover > 0.01f) { tB = 200; tA = (int) (210 * ca); }
            else { tB = 140; tA = (int) (170 * ca); }
            if (isHi) tB = (int) Math.min(255, tB + 25 * ah.getHighlightAnimation());

            int tc = ((Math.min(255, tA) & 0xFF) << 24) | ((tB & 0xFF) << 16) | ((tB & 0xFF) << 8) | (tB & 0xFF);
            float tX = aX + 8;
            float tY = sY + (sH - 5.5f * scale) / 2f;
            Fonts.BOLD.draw(name, tX, tY, 5.5f * scale, tc);

            if (inter && fav > 0.01f) {
                float fX = aX + 8 + Fonts.BOLD.getWidth(name, 5.5f * scale) + 4;
                float fY = sY + (sH - 6f * scale) / 2f;
                int fA = (int) ((60 + 140 * fav + 30 * hover) * ca);
                int fR = (int) (80 + (255 - 80) * fav);
                int fG = (int) (80 + (215 - 80) * fav);
                Fonts.GUI_ICONS.draw("D", fX, fY + 1, 7 * scale, new Color(fR, fG, 0, fA).getRGB());
            }
        }
    }

    private void renderToggle(float x, float y, float state, float alpha) {
        int aR = (ClickGuiTheme.ACCENT_ARGB >> 16) & 0xFF;
        int aG = (ClickGuiTheme.ACCENT_ARGB >> 8) & 0xFF;
        int aB = ClickGuiTheme.ACCENT_ARGB & 0xFF;

        int trackA = (int) (80 + 120 * state);
        int trackBg;
        if (state > 0.5f) {
            trackBg = new Color(aR, aG, aB, (int) (trackA * alpha)).getRGB();
        } else {
            trackBg = new Color(40, 50, 70, (int) (trackA * alpha)).getRGB();
        }
        Render2D.rect(x, y, TOGGLE_W, TOGGLE_H, trackBg, TOGGLE_R);

        float knobX = x + 1.5f + state * (TOGGLE_W - KNOB_SIZE - 3f);
        int knobA = (int) (255 * alpha);
        Render2D.rect(knobX, y + (TOGGLE_H - KNOB_SIZE) / 2f, KNOB_SIZE, KNOB_SIZE,
                new Color(255, 255, 255, knobA).getRGB(), KNOB_SIZE / 2f);
    }

    private void renderFade(float x, float y, float w, float h, float topF, float botF) {
        if (topF > 0.01f) for (int i = 0; i < 12; i++) {
            int a = (int) (60 * topF * (1f - i / 12f));
            Render2D.rect(x, y + i, w, 1, ((a & 0xFF) << 24) | (ClickGuiTheme.BG_TOP_ARGB & 0xFFFFFF), 0);
        }
        if (botF > 0.01f) for (int i = 0; i < 12; i++) {
            int a = (int) (60 * botF * (i / 12f));
            Render2D.rect(x, y + h - 12 + i, w, 1, ((a & 0xFF) << 24) | (ClickGuiTheme.BG_TOP_ARGB & 0xFFFFFF), 0);
        }
    }

    public ModuleStructure getModuleAtPosition(List<ModuleStructure> list, double mx, double my,
                                               float lx, float ly, float lw, float lh, double scroll, boolean trans) {
        if (trans) return null;
        if (mx < lx || mx > lx + lw || my < ly || my > ly + lh) return null;
        float sy = ly + INSET + 2f + (float) scroll;
        for (int i = 0; i < list.size(); i++) {
            float mY = sy + i * (ITEM_H + 2);
            if (mx >= lx + 3 && mx <= lx + lw - 3 && my >= mY && my <= mY + ITEM_H) return list.get(i);
        }
        return null;
    }

    public boolean isStarClicked(List<ModuleStructure> list, double mx, double my,
                                 float lx, float ly, float lw, float lh, double scroll, ModuleDisplayHelper dh, boolean trans) {
        return getModuleForStarClick(list, mx, my, lx, ly, lw, lh, scroll, dh, trans) != null;
    }

    public ModuleStructure getModuleForStarClick(List<ModuleStructure> list, double mx, double my,
                                                 float lx, float ly, float lw, float lh, double scroll, ModuleDisplayHelper dh, boolean trans) {
        if (trans) return null;
        float sy = ly + INSET + 2f + (float) scroll;
        for (int i = 0; i < list.size(); i++) {
            ModuleStructure m = list.get(i);
            float mY = sy + i * (ITEM_H + 2);
            if (my >= mY && my <= mY + ITEM_H) {
                float nw = Fonts.BOLD.getWidth(m.getName(), 5.5f);
                float fX = lx + 3 + 8 + nw + 4;
                if (mx >= fX && mx <= fX + 12) return m;
            }
        }
        return null;
    }

    private float easeOutCubic(float x) { return 1f - (float) Math.pow(1 - x, 3); }
}
