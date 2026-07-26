package rich.screens.clickgui.impl.background.render;

import rich.modules.module.category.ModuleCategory;
import rich.screens.clickgui.impl.background.search.SearchHandler;
import rich.screens.clickgui.impl.theme.ClickGuiTheme;
import rich.util.lang.Lang;
import rich.util.render.Render2D;
import rich.util.render.shader.Scissor;
import rich.util.render.font.Fonts;

import java.awt.*;

public class HeaderRenderer {

    private static final float SLIDE_DIST = 6f;

    public void render(float bgX, float bgY, float bgW, ModuleCategory sel,
                       ModuleCategory prev, ModuleCategory cur, float trans,
                       SearchHandler sh, float alpha) {
        renderAccentLine(bgX, bgY, bgW, alpha);
        renderLogo(bgX, bgY, alpha);
        renderSearchBox(bgX, bgY, bgW, sh, alpha);
        renderLabel(bgX, bgY, prev, cur, trans, sh, alpha);
    }

    private void renderLogo(float bgX, float bgY, float alpha) {
        float inset = ClickGuiTheme.PANEL_INSET;
        float catW = ClickGuiTheme.CATEGORY_PANEL_WIDTH;
        float lx = bgX + inset + catW / 2f;
        float ly = bgY + inset + 4f;

        int ar = (ClickGuiTheme.ACCENT_ARGB >> 16) & 0xFF;
        int ag = (ClickGuiTheme.ACCENT_ARGB >> 8) & 0xFF;
        int ab = ClickGuiTheme.ACCENT_ARGB & 0xFF;
        int logoA = (int) (200 * alpha);
        if (logoA > 0) {
            int logoColor = ((logoA & 0xFF) << 24) | ((ar & 0xFF) << 16) | ((ag & 0xFF) << 8) | (ab & 0xFF);
            Fonts.BOLD.draw("RICH", lx - Fonts.BOLD.getWidth("RICH", 7) / 2f, ly, 7, logoColor);
        }
    }

    private void renderAccentLine(float bgX, float bgY, float bgW, float alpha) {
        float inset = ClickGuiTheme.PANEL_INSET;
        float catW = ClickGuiTheme.CATEGORY_PANEL_WIDTH;
        float x = bgX + inset + catW + 6f;
        float y = bgY + inset;
        float h = ClickGuiTheme.BG_HEIGHT - inset * 2;

        int a = (int) (50 * alpha);
        int ar = (ClickGuiTheme.ACCENT_ARGB >> 16) & 0xFF;
        int ag = (ClickGuiTheme.ACCENT_ARGB >> 8) & 0xFF;
        int ab = ClickGuiTheme.ACCENT_ARGB & 0xFF;
        Render2D.rect(x, y, 0.5f, h, new Color(ar, ag, ab, a).getRGB(), 0);
    }

    private void renderSearchBox(float bgX, float bgY, float bgW, SearchHandler sh, float alpha) {
        float sx = bgX + bgW - ClickGuiTheme.PANEL_INSET - 90f;
        float sy = bgY + 14f;
        float sw = 82f;
        float sh2 = 20f;

        int outlineA = (int) (200 * alpha);
        int bgAlpha = (int) ((28 + sh.getSearchFocusAnimation() * 12) * alpha);
        int bgColor = ((bgAlpha & 0xFF) << 24) | (ClickGuiTheme.SEARCH_BG_ARGB & 0xFFFFFF);
        Render2D.rect(sx, sy, sw, sh2, bgColor, 6);

        int borderColor = sh.isSearchActive()
                ? ((outlineA & 0xFF) << 24) | (ClickGuiTheme.ACCENT_ARGB & 0xFFFFFF)
                : ((outlineA / 3 & 0xFF) << 24) | (ClickGuiTheme.PANEL_BORDER_ARGB & 0xFFFFFF);
        Render2D.outline(sx, sy, sw, sh2, 0.5f, borderColor, 6);

        float tx = sx + 7;
        if (sh.isSearchActive() && !sh.getSearchText().isEmpty()) {
            Scissor.enable(sx + 3, sy, sw - 6, sh2, 2);
            if (sh.hasSearchSelection() && sh.getSearchSelectionAnimation() > 0.01f) {
                int start = sh.getSearchSelectionStart();
                int end = sh.getSearchSelectionEnd();
                float selX = tx + Fonts.BOLD.getWidth(sh.getSearchText().substring(0, start), 5);
                float selW = Fonts.BOLD.getWidth(sh.getSearchText().substring(start, end), 5);
                int selA = (int) (80 * sh.getSearchSelectionAnimation() * alpha);
                int ar = (ClickGuiTheme.ACCENT_ARGB >> 16) & 0xFF;
                int ag = (ClickGuiTheme.ACCENT_ARGB >> 8) & 0xFF;
                int ab = ClickGuiTheme.ACCENT_ARGB & 0xFF;
                Render2D.rect(selX, sy + 2, selW, sh2 - 4, new Color(ar, ag, ab, selA).getRGB(), 2f);
            }
            int ta = (int) (240 * alpha);
            Fonts.BOLD.draw(sh.getSearchText(), tx, sy + 6f, 5, ((ta & 0xFF) << 24) | (ClickGuiTheme.SEARCH_TEXT_ACTIVE_ARGB & 0xFFFFFF));
            Scissor.disable();
            if (!sh.hasSearchSelection()) {
                float cursorA = (float) (Math.sin(sh.getSearchCursorBlink() * Math.PI * 2) * 0.5 + 0.5);
                if (cursorA > 0.3f) {
                    float cx = tx + Fonts.BOLD.getWidth(sh.getSearchText().substring(0, sh.getSearchCursorPosition()), 5);
                    int cA = (int) (160 * cursorA * alpha);
                    Render2D.rect(cx, sy + 3, 0.5f, sh2 - 6, ((cA & 0xFF) << 24) | 0xB0C8E0, 0);
                }
            }
        } else {
            int phA = (int) (80 * alpha);
            int phColor = ((phA & 0xFF) << 24) | (ClickGuiTheme.SEARCH_TEXT_ARGB & 0xFFFFFF);
            Fonts.ICONS.draw("U", sx + 6, sy + 4f, 12, phColor);
            if (sh.isSearchActive()) {
                float cursorA = (float) (Math.sin(sh.getSearchCursorBlink() * Math.PI * 2) * 0.5 + 0.5);
                if (cursorA > 0.3f) {
                    int cA = (int) (160 * cursorA * alpha);
                    Render2D.rect(tx, sy + 3, 0.5f, sh2 - 6, ((cA & 0xFF) << 24) | 0xB0C8E0, 0);
                }
            } else {
                Fonts.BOLD.draw(Lang.get().get("search"), tx + 2, sy + 6f, 5, phColor);
            }
        }
    }

    private void renderLabel(float bgX, float bgY, ModuleCategory prev, ModuleCategory cur,
                             float trans, SearchHandler sh, float alpha) {
        float lx = bgX + ClickGuiTheme.PANEL_INSET + ClickGuiTheme.CATEGORY_PANEL_WIDTH + ClickGuiTheme.PANEL_INSET + 10f;
        float ly = bgY + 18f;

        float catAlpha = sh.getNormalPanelAlpha() * alpha;
        if (catAlpha > 0.01f) {
            float ease = easeOut(trans);
            if (prev != null && trans < 1f) {
                float a = (1f - ease) * catAlpha;
                float oy = ease * SLIDE_DIST;
                if (a > 0.01f) {
                    int color = ((int) (100 * a) << 24) | (ClickGuiTheme.CATEGORY_TEXT_ARGB & 0xFFFFFF);
                    Fonts.BOLD.draw(prev.getReadableName(), lx, ly + oy, 7, color);
                }
            }
            if (cur != null) {
                float a = ease * catAlpha;
                float oy = (1f - ease) * -SLIDE_DIST;
                if (a > 0.01f) {
                    int color = ((int) (200 * a) << 24) | (ClickGuiTheme.CATEGORY_TEXT_SELECTED_ARGB & 0xFFFFFF);
                    Fonts.BOLD.draw(cur.getReadableName(), lx, ly + oy, 7, color);
                }
            }
        }

        float sAlpha = sh.getSearchPanelAlpha() * alpha;
        if (sAlpha > 0.01f) {
            int si = (int) (160 * sAlpha);
            String label = Lang.get().get("search_results");
            String st = sh.getSearchText();
            if (!st.isEmpty()) {
                label = Lang.get().get("results_for") + " \"" + (st.length() > 12 ? st.substring(0, 12) + "..." : st) + "\"";
            }
            int color = ((si & 0xFF) << 24) | (ClickGuiTheme.CATEGORY_TEXT_ARGB & 0xFFFFFF);
            Fonts.BOLD.draw(label, lx, ly, 7, color);
        }
    }

    private float easeOut(float x) {
        return 1f - (float) Math.pow(1 - x, 4);
    }

    public boolean isSearchBoxHovered(double mx, double my, float bgX, float bgY, float bgW) {
        float sx = bgX + bgW - ClickGuiTheme.PANEL_INSET - 90f;
        float sy = bgY + 14f;
        return mx >= sx && mx <= sx + 82 && my >= sy && my <= sy + 20;
    }
}
