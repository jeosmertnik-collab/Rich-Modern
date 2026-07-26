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

    private static final float HEADER_SLIDE_DISTANCE = 8f;

    public void render(float bgX, float bgY, float bgWidth, ModuleCategory selectedCategory,
                       ModuleCategory previousCategory, ModuleCategory currentCategory,
                       float headerTransition, SearchHandler searchHandler, float alphaMultiplier) {

        renderHeaderPanel(bgX, bgY, bgWidth, alphaMultiplier);
        renderSearchBox(bgX, bgY, bgWidth, searchHandler, alphaMultiplier);
        renderCategoryLabel(bgX, bgY, previousCategory, currentCategory, headerTransition, searchHandler, alphaMultiplier);
    }

    private void renderHeaderPanel(float bgX, float bgY, float bgWidth, float alphaMultiplier) {
        int panelAlpha = (int) (ClickGuiTheme.PANEL_BG_ARGB >>> 24 & 0xFF);
        panelAlpha = (int) (panelAlpha * 0.4f * alphaMultiplier);
        int outlineAlpha = (int) (60 * alphaMultiplier);

        float headerX = bgX + ClickGuiTheme.PANEL_INSET + ClickGuiTheme.CATEGORY_PANEL_WIDTH + ClickGuiTheme.PANEL_INSET;
        float headerY = bgY + 8f;
        float headerW = bgWidth - ClickGuiTheme.CATEGORY_PANEL_WIDTH - ClickGuiTheme.PANEL_INSET * 3;
        float headerH = 24f;

        int bgColor = ((panelAlpha & 0xFF) << 24) | (ClickGuiTheme.PANEL_BG_ARGB & 0xFFFFFF);
        Render2D.rect(headerX, headerY, headerW, headerH, bgColor, ClickGuiTheme.PANEL_CORNER_RADIUS);

        int borderColor = ((outlineAlpha & 0xFF) << 24) | (ClickGuiTheme.PANEL_BORDER_ARGB & 0xFFFFFF);
        Render2D.outline(headerX, headerY, headerW, headerH, 0.5f, borderColor, ClickGuiTheme.PANEL_CORNER_RADIUS);
    }

    private void renderSearchBox(float bgX, float bgY, float bgWidth, SearchHandler searchHandler, float alphaMultiplier) {
        float searchBoxX = bgX + bgWidth - ClickGuiTheme.PANEL_INSET - 80f;
        float searchBoxY = bgY + 12f;
        float searchBoxW = 72f;
        float searchBoxH = 16f;

        int outlineAlpha = (int) (255 * alphaMultiplier);
        int panelAlpha = (int) (25 * alphaMultiplier);

        Color searchOutline = searchHandler.isSearchActive()
                ? new Color(180, 180, 180, outlineAlpha)
                : new Color(55, 55, 55, outlineAlpha);

        int searchBgAlpha = (int) ((25 + searchHandler.getSearchFocusAnimation() * 15) * alphaMultiplier);
        int searchBgColor = ((searchBgAlpha & 0xFF) << 24) | (ClickGuiTheme.SEARCH_BG_ARGB & 0xFFFFFF);
        Render2D.rect(searchBoxX, searchBoxY, searchBoxW, searchBoxH, searchBgColor, 4);

        int searchBorderColor = searchHandler.isSearchActive()
                ? ((outlineAlpha & 0xFF) << 24) | (ClickGuiTheme.ACCENT_ARGB & 0xFFFFFF)
                : ((outlineAlpha & 0xFF) << 24) / 3 | (ClickGuiTheme.PANEL_BORDER_ARGB & 0xFFFFFF);
        Render2D.outline(searchBoxX, searchBoxY, searchBoxW, searchBoxH, 0.5f, searchBorderColor, 4);

        float textAreaX = searchBoxX + 5;

        if (searchHandler.isSearchActive() && !searchHandler.getSearchText().isEmpty()) {
            renderSearchText(searchBoxX, searchBoxY, searchBoxW, searchBoxH, textAreaX, searchHandler, alphaMultiplier);
        } else if (searchHandler.isSearchActive()) {
            renderSearchPlaceholder(searchBoxX, searchBoxY, searchBoxH, textAreaX, searchHandler, alphaMultiplier, true);
        } else {
            int placeholderAlpha = (int) (128 * alphaMultiplier);
            int placeholderColor = ((placeholderAlpha & 0xFF) << 24) | (ClickGuiTheme.SEARCH_TEXT_ARGB & 0xFFFFFF);
            Fonts.BOLD.draw(Lang.get().get("search"), textAreaX, searchBoxY + 5f, 5, placeholderColor);
        }

        int dividerAlpha = (int) (panelAlpha);
        Render2D.rect(searchBoxX + 53, searchBoxY + 3.5f, 1, searchBoxH - 7, ((dividerAlpha & 0xFF) << 24) | 0x808080, 8);
        Fonts.ICONS.draw("U", searchBoxX + 55, searchBoxY + 1.5f, 12, ((outlineAlpha & 0xFF) << 24) | 0x808080);
    }

    private void renderSearchText(float searchBoxX, float searchBoxY, float searchBoxW, float searchBoxH,
                                  float textAreaX, SearchHandler searchHandler, float alphaMultiplier) {
        Scissor.enable(searchBoxX + 3, searchBoxY, searchBoxW - 20, searchBoxH, 2);

        if (searchHandler.hasSearchSelection() && searchHandler.getSearchSelectionAnimation() > 0.01f) {
            renderSearchSelection(textAreaX, searchBoxY, searchBoxH, searchHandler, alphaMultiplier);
        }

        int textAlpha = (int) (255 * alphaMultiplier);
        Fonts.BOLD.draw(searchHandler.getSearchText(), textAreaX, searchBoxY + 5f, 5,
                ((textAlpha & 0xFF) << 24) | (ClickGuiTheme.SEARCH_TEXT_ACTIVE_ARGB & 0xFFFFFF));
        Scissor.disable();

        if (!searchHandler.hasSearchSelection()) {
            renderSearchCursor(textAreaX, searchBoxY, searchBoxH, searchHandler, alphaMultiplier);
        }
    }

    private void renderSearchSelection(float textAreaX, float searchBoxY, float searchBoxH,
                                       SearchHandler searchHandler, float alphaMultiplier) {
        int start = searchHandler.getSearchSelectionStart();
        int end = searchHandler.getSearchSelectionEnd();
        String beforeSelection = searchHandler.getSearchText().substring(0, start);
        String selection = searchHandler.getSearchText().substring(start, end);

        float selectionX = textAreaX + Fonts.BOLD.getWidth(beforeSelection, 5);
        float selectionWidth = Fonts.BOLD.getWidth(selection, 5);

        int selAlpha = (int) (100 * searchHandler.getSearchSelectionAnimation() * alphaMultiplier);
        Render2D.rect(selectionX, searchBoxY + 2, selectionWidth, searchBoxH - 4,
                ((selAlpha & 0xFF) << 24) | (ClickGuiTheme.ACCENT_ARGB & 0xFFFFFF), 2f);
    }

    private void renderSearchCursor(float textAreaX, float searchBoxY, float searchBoxH,
                                    SearchHandler searchHandler, float alphaMultiplier) {
        float cursorAlpha = (float) (Math.sin(searchHandler.getSearchCursorBlink() * Math.PI * 2) * 0.5 + 0.5);
        if (cursorAlpha > 0.3f) {
            String beforeCursor = searchHandler.getSearchText().substring(0, searchHandler.getSearchCursorPosition());
            float cursorX = textAreaX + Fonts.BOLD.getWidth(beforeCursor, 5);
            int cursorAlphaInt = (int) (180 * cursorAlpha * alphaMultiplier);
            Render2D.rect(cursorX, searchBoxY + 3, 0.5f, searchBoxH - 6, ((cursorAlphaInt & 0xFF) << 24) | 0xB4B4B9, 0);
        }
    }

    private void renderSearchPlaceholder(float searchBoxX, float searchBoxY, float searchBoxH,
                                         float textAreaX, SearchHandler searchHandler, float alphaMultiplier, boolean showCursor) {
        int placeholderAlpha = (int) (100 * alphaMultiplier);
        Fonts.BOLD.draw(Lang.get().get("search_type"), textAreaX, searchBoxY + 5f, 5,
                ((placeholderAlpha & 0xFF) << 24) | (ClickGuiTheme.SEARCH_TEXT_ARGB & 0xFFFFFF));

        if (showCursor) {
            float cursorAlpha = (float) (Math.sin(searchHandler.getSearchCursorBlink() * Math.PI * 2) * 0.5 + 0.5);
            if (cursorAlpha > 0.3f) {
                int cursorAlphaInt = (int) (180 * cursorAlpha * alphaMultiplier);
                Render2D.rect(textAreaX, searchBoxY + 3, 0.5f, searchBoxH - 6,
                        ((cursorAlphaInt & 0xFF) << 24) | 0xB4B4B9, 0);
            }
        }
    }

    private void renderCategoryLabel(float bgX, float bgY, ModuleCategory previousCategory,
                                     ModuleCategory currentCategory, float headerTransition,
                                     SearchHandler searchHandler, float alphaMultiplier) {
        float labelX = bgX + ClickGuiTheme.PANEL_INSET + ClickGuiTheme.CATEGORY_PANEL_WIDTH + ClickGuiTheme.PANEL_INSET + 6f;
        float labelY = bgY + 16f;

        float categoryAlpha = searchHandler.getNormalPanelAlpha() * alphaMultiplier;
        if (categoryAlpha > 0.01f) {
            float eased = easeOutQuart(headerTransition);

            if (previousCategory != null && headerTransition < 1f) {
                float oldAlpha = (1f - eased) * categoryAlpha;
                float oldOffsetY = eased * HEADER_SLIDE_DISTANCE;

                int oldAlphaInt = (int) (128 * oldAlpha);
                if (oldAlphaInt > 0) {
                    String oldName = previousCategory.getReadableName();
                    int color = ((oldAlphaInt & 0xFF) << 24) | (ClickGuiTheme.CATEGORY_TEXT_ARGB & 0xFFFFFF);
                    Fonts.BOLD.draw(oldName, labelX, labelY + oldOffsetY, 7, color);
                }
            }

            if (currentCategory != null) {
                float newAlpha = eased * categoryAlpha;
                float newOffsetY = (1f - eased) * -HEADER_SLIDE_DISTANCE;

                int newAlphaInt = (int) (128 * newAlpha);
                if (newAlphaInt > 0) {
                    String newName = currentCategory.getReadableName();
                    int color = ((newAlphaInt & 0xFF) << 24) | (ClickGuiTheme.CATEGORY_TEXT_SELECTED_ARGB & 0xFFFFFF);
                    Fonts.BOLD.draw(newName, labelX, labelY + newOffsetY, 7, color);
                }
            }
        }

        float searchLabelAlpha = searchHandler.getSearchPanelAlpha() * alphaMultiplier;
        if (searchLabelAlpha > 0.01f) {
            int searchLabelAlphaInt = (int) (180 * searchLabelAlpha);
            if (searchLabelAlphaInt > 0) {
                String searchLabel = Lang.get().get("search_results");
                String searchText = searchHandler.getSearchText();
                if (!searchText.isEmpty()) {
                    searchLabel = Lang.get().get("results_for") + " \"" + (searchText.length() > 12 ? searchText.substring(0, 12) + "..." : searchText) + "\"";
                }
                int color = ((searchLabelAlphaInt & 0xFF) << 24) | (ClickGuiTheme.CATEGORY_TEXT_ARGB & 0xFFFFFF);
                Fonts.BOLD.draw(searchLabel, labelX, labelY, 7, color);
            }
        }
    }

    private float easeOutQuart(float x) {
        return 1f - (float) Math.pow(1 - x, 4);
    }

    public boolean isSearchBoxHovered(double mouseX, double mouseY, float bgX, float bgY, float bgWidth) {
        float searchBoxX = bgX + bgWidth - ClickGuiTheme.PANEL_INSET - 80f;
        float searchBoxY = bgY + 12f;
        float searchBoxW = 72f;
        float searchBoxH = 16f;

        return mouseX >= searchBoxX && mouseX <= searchBoxX + searchBoxW &&
                mouseY >= searchBoxY && mouseY <= searchBoxY + searchBoxH;
    }
}
