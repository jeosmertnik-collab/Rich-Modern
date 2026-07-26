package rich.screens.clickgui.impl.background.search;

import net.minecraft.client.gui.DrawContext;
import rich.modules.module.ModuleStructure;
import rich.screens.clickgui.impl.theme.ClickGuiTheme;
import rich.util.render.Render2D;
import rich.util.render.shader.Scissor;
import rich.util.render.font.Fonts;

import java.awt.*;
import java.util.List;

public class SearchRenderer {

    private final SearchHandler searchHandler;

    public SearchRenderer(SearchHandler searchHandler) {
        this.searchHandler = searchHandler;
    }

    public void render(DrawContext context, float bgX, float bgY, float bgWidth, float bgHeight,
                       float mouseX, float mouseY, int guiScale, float alphaMultiplier) {

        if (searchHandler.getSearchPanelAlpha() <= 0.01f) return;

        float panelX = bgX + 88f;
        float panelY = bgY + 38f;
        float panelW = bgWidth - 96f;
        float panelH = bgHeight - 46f;
        float resultAlpha = searchHandler.getSearchPanelAlpha() * alphaMultiplier;

        int panelBgAlpha = (int) (20 * resultAlpha);
        int panelBg = ((panelBgAlpha & 0xFF) << 24) | (ClickGuiTheme.PANEL_BG_ARGB & 0xFFFFFF);
        Render2D.rect(panelX, panelY, panelW, panelH, panelBg, 10f);
        int outlineAlpha = (int) (120 * resultAlpha);
        int outlineCol = ((outlineAlpha & 0xFF) << 24) | (ClickGuiTheme.PANEL_BORDER_ARGB & 0xFFFFFF);
        Render2D.outline(panelX, panelY, panelW, panelH, 0.5f, outlineCol, 10f);

        List<ModuleStructure> results = searchHandler.getSearchResults();
        if (results.isEmpty()) {
            String noResults = searchHandler.getSearchText().isEmpty() ? "Start typing to search..." : "No modules found";
            int textAlpha = (int) (120 * resultAlpha);
            Fonts.BOLD.draw(noResults, panelX + (panelW - Fonts.BOLD.getWidth(noResults, 6)) / 2f,
                    panelY + (panelH - 6) / 2f, 6, ((textAlpha & 0xFF) << 24) | (ClickGuiTheme.SETTINGS_EMPTY_TEXT_ARGB & 0xFFFFFF));
            return;
        }

        Scissor.enable(panelX + 3, panelY + 3, panelW - 6, panelH - 6, 2);
        renderResults(panelX, panelY, panelW, panelH, mouseX, mouseY, resultAlpha);
        Scissor.disable();
    }

    private void renderResults(float panelX, float panelY, float panelW, float panelH,
                               float mouseX, float mouseY, float resultAlpha) {
        List<ModuleStructure> results = searchHandler.getSearchResults();
        float startY = panelY + 5 + searchHandler.getSearchScrollOffset();
        float resultHeight = searchHandler.getSearchResultHeight();

        int aR = (ClickGuiTheme.ACCENT_ARGB >> 16) & 0xFF;
        int aG = (ClickGuiTheme.ACCENT_ARGB >> 8) & 0xFF;
        int aB = ClickGuiTheme.ACCENT_ARGB & 0xFF;

        int newHoveredIndex = -1;
        for (int i = 0; i < results.size(); i++) {
            ModuleStructure module = results.get(i);
            float itemY = startY + i * (resultHeight + 2);
            if (itemY + resultHeight < panelY || itemY > panelY + panelH) continue;
            float itemAnim = searchHandler.getSearchResultAnimations().getOrDefault(module, 0f);
            float itemAlpha = itemAnim * resultAlpha;
            if (itemAlpha <= 0.01f) continue;

            float itemOffsetX = (1f - itemAnim) * 20f;
            boolean hovered = mouseX >= panelX + 5 && mouseX <= panelX + panelW - 5 &&
                    mouseY >= itemY && mouseY <= itemY + resultHeight;
            if (hovered) newHoveredIndex = i;
            boolean selected = module == searchHandler.getSelectedSearchModule();

            float itemX = panelX + 5 + itemOffsetX;
            float itemW = panelW - 10;

            int bgAlpha;
            if (selected) bgAlpha = (int) (50 * itemAlpha);
            else if (hovered) bgAlpha = (int) (35 * itemAlpha);
            else bgAlpha = (int) (20 * itemAlpha);

            int bgCol;
            if (selected || hovered) bgCol = new Color(aR, aG, aB, bgAlpha).getRGB();
            else bgCol = ((bgAlpha & 0xFF) << 24) | (ClickGuiTheme.PANEL_BORDER_LIGHT_ARGB & 0xFFFFFF);

            Render2D.rect(itemX, itemY, itemW, resultHeight, bgCol, 6);

            if (selected) {
                int oAlpha = (int) (80 * itemAlpha);
                Render2D.outline(itemX, itemY, itemW, resultHeight, 0.5f,
                        new Color(aR, aG, aB, oAlpha).getRGB(), 6);
            }

            int textAlpha = module.isState() ? (int) (255 * itemAlpha) : (int) (200 * itemAlpha);
            int textColor = module.isState()
                    ? ((textAlpha & 0xFF) << 24) | (ClickGuiTheme.SETTINGS_TITLE_ARGB & 0xFFFFFF)
                    : ((textAlpha & 0xFF) << 24) | (ClickGuiTheme.SETTINGS_DESC_ARGB & 0xFFFFFF);
            Fonts.BOLD.draw(module.getName(), itemX + 5, itemY + 3, 6, textColor);

            int catAlpha = (int) (160 * itemAlpha);
            Fonts.BOLD.draw(module.getCategory().getReadableName(), itemX + 5, itemY + 11, 4,
                    ((catAlpha & 0xFF) << 24) | (ClickGuiTheme.SETTINGS_DESC_ARGB & 0xFFFFFF));

            if (module.isState()) {
                int indicatorAlpha = (int) (200 * itemAlpha);
                Render2D.rect(itemX + itemW - 10, itemY + resultHeight / 2 - 2, 4, 4,
                        new Color(aR, aG, aB, indicatorAlpha).getRGB(), 2);
            }
        }
        searchHandler.setHoveredSearchIndex(newHoveredIndex);
    }

    public ModuleStructure getModuleAtPosition(double mouseX, double mouseY, float bgX, float bgY,
                                               float bgWidth, float bgHeight, SearchHandler handler) {
        if (!handler.isSearchActive() || handler.getSearchResults().isEmpty()) return null;
        float panelX = bgX + 88f;
        float panelY = bgY + 38f;
        float panelW = bgWidth - 96f;
        float panelH = bgHeight - 46f;
        if (mouseX < panelX + 5 || mouseX > panelX + panelW - 5 || mouseY < panelY || mouseY > panelY + panelH) return null;
        float startY = panelY + 5 + handler.getSearchScrollOffset();
        float resultHeight = handler.getSearchResultHeight();
        List<ModuleStructure> results = handler.getSearchResults();
        for (int i = 0; i < results.size(); i++) {
            float itemY = startY + i * (resultHeight + 2);
            if (mouseY >= itemY && mouseY <= itemY + resultHeight) return results.get(i);
        }
        return null;
    }
}
