package rich.screens.clickgui.impl.module.render;

import net.minecraft.client.gui.DrawContext;
import rich.modules.module.ModuleStructure;
import rich.screens.clickgui.impl.module.handler.ModuleAnimationHandler;
import rich.screens.clickgui.impl.module.handler.ModuleScrollHandler;
import rich.screens.clickgui.impl.settingsrender.ColorComponent;
import rich.screens.clickgui.impl.settingsrender.MultiSelectComponent;
import rich.screens.clickgui.impl.settingsrender.SelectComponent;
import rich.screens.clickgui.impl.theme.ClickGuiTheme;
import rich.util.interfaces.AbstractSettingComponent;
import rich.util.lang.Lang;
import rich.util.render.Render2D;
import rich.util.render.shader.Scissor;
import rich.util.render.font.Fonts;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class SettingsPanelRenderer {

    private static final float SETTINGS_PANEL_CORNER_RADIUS = 10f;
    private static final float CORNER_INSET = 4f;
    private static final int SETTING_HEIGHT = 16;
    private static final int SETTING_SPACING = 2;

    private final ModuleAnimationHandler animationHandler;

    public SettingsPanelRenderer(ModuleAnimationHandler animationHandler) {
        this.animationHandler = animationHandler;
    }

    public void render(DrawContext context, ModuleStructure selectedModule, List<AbstractSettingComponent> settingComponents,
                       float x, float y, float width, float height, float mouseX, float mouseY, float delta,
                       int guiScale, float alphaMultiplier, ModuleScrollHandler scrollHandler, ModuleAnimationHandler animHandler) {

        animHandler.updateSettingAnimations(settingComponents);
        animHandler.updateVisibilityAnimations(settingComponents);

        int panelAlpha = (int) (20 * alphaMultiplier);
        int panelBg = ((panelAlpha & 0xFF) << 24) | (ClickGuiTheme.PANEL_BG_ARGB & 0xFFFFFF);
        Render2D.rect(x, y, width, height, panelBg, SETTINGS_PANEL_CORNER_RADIUS);

        int outlineAlpha = (int) (120 * alphaMultiplier);
        int borderCol = ((outlineAlpha & 0xFF) << 24) | (ClickGuiTheme.PANEL_BORDER_ARGB & 0xFFFFFF);
        Render2D.outline(x, y, width, height, 0.5f, borderCol, SETTINGS_PANEL_CORNER_RADIUS);

        int ar = (ClickGuiTheme.ACCENT_ARGB >> 16) & 0xFF;
        int ag = (ClickGuiTheme.ACCENT_ARGB >> 8) & 0xFF;
        int ab = ClickGuiTheme.ACCENT_ARGB & 0xFF;
        int glowA = (int) (12 * alphaMultiplier);
        if (glowA > 0) {
            Render2D.rect(x + 4, y, width - 8, 1, new Color(ar, ag, ab, glowA).getRGB(), 0);
            Render2D.rect(x + 4, y + height - 1, width - 8, 1, new Color(ar, ag, ab, glowA).getRGB(), 0);
        }

        if (selectedModule == null) {
            String text = Lang.get().get("select_module_hint");
            float textSize = 6f;
            float textWidth = Fonts.BOLD.getWidth(text, textSize);
            float textHeight = Fonts.BOLD.getHeight(textSize);
            float centerX = x + (width - textWidth) / 2f;
            float centerY = y + (height - textHeight) / 2f;
            int emptyAlpha = (int) (120 * alphaMultiplier);
            Fonts.BOLD.draw(text, centerX, centerY, textSize, ((emptyAlpha & 0xFF) << 24) | (ClickGuiTheme.SETTINGS_EMPTY_TEXT_ARGB & 0xFFFFFF));
            return;
        }

        int titleAlpha = (int) (220 * alphaMultiplier);
        Fonts.BOLD.draw(selectedModule.getName(), x + 10, y + 10, 7, ((titleAlpha & 0xFF) << 24) | (ClickGuiTheme.SETTINGS_TITLE_ARGB & 0xFFFFFF));

        String desc = selectedModule.getDescription();
        if (desc != null && !desc.isEmpty()) {
            int descAlpha = (int) (140 * alphaMultiplier);
            String truncated = desc.length() > 52 ? desc.substring(0, 52) + "..." : desc;
            Fonts.BOLD.draw(truncated, x + 17, y + 22, 5, ((descAlpha & 0xFF) << 24) | (ClickGuiTheme.SETTINGS_DESC_ARGB & 0xFFFFFF));
            Fonts.GUI_ICONS.draw("C", x + 10, y + 22, 6, ((descAlpha & 0xFF) << 24) | (ClickGuiTheme.SETTINGS_DESC_ARGB & 0xFFFFFF));
        }

        int divAlpha = (int) (50 * alphaMultiplier);
        int divCol = ((divAlpha & 0xFF) << 24) | (ClickGuiTheme.SETTINGS_DIVIDER_ARGB & 0xFFFFFF);
        Render2D.rect(x + 10, y + 32, width - 20, 1f, divCol, 10);

        float sideInset = CORNER_INSET;
        float bottomInset = CORNER_INSET + 3;

        float clipY = y + 33;
        float clipH = height - 28 - bottomInset;

        float clipX = x + sideInset;
        float clipW = width - sideInset * 2;

        Scissor.enable(clipX, clipY, clipW, clipH, guiScale);

        List<Float> finalYPositions = new ArrayList<>();
        List<Float> animatedHeights = new ArrayList<>();
        float posY = y + 40f + (float) scrollHandler.getSettingDisplayScroll();

        for (AbstractSettingComponent c : settingComponents) {
            float heightAnim = animHandler.getHeightAnimations().getOrDefault(c, c.getSetting().isVisible() ? 1f : 0f);

            if (heightAnim <= 0.001f) {
                finalYPositions.add(null);
                animatedHeights.add(0f);
                continue;
            }

            finalYPositions.add(posY);

            float baseHeight = getComponentBaseHeight(c);
            float layoutHeight = baseHeight * heightAnim;
            animatedHeights.add(layoutHeight);
            posY += layoutHeight + SETTING_SPACING * heightAnim;
        }

        float visibleTop = clipY;
        float visibleBottom = clipY + clipH;

        for (int i = 0; i < settingComponents.size(); i++) {
            AbstractSettingComponent c = settingComponents.get(i);
            Float startY = finalYPositions.get(i);

            if (startY == null) continue;

            float visAnim = animHandler.getVisibilityAnimations().getOrDefault(c, c.getSetting().isVisible() ? 1f : 0f);
            float heightAnim = animHandler.getHeightAnimations().getOrDefault(c, c.getSetting().isVisible() ? 1f : 0f);

            if (visAnim <= 0.001f && heightAnim <= 0.001f) continue;

            float animatedHeight = animatedHeights.get(i);

            float progress = animHandler.getSettingAnimations().getOrDefault(c, 1f);
            float componentAlpha = progress * visAnim * alphaMultiplier;

            c.position(x + 8, startY);
            c.size(width - 16f, SETTING_HEIGHT);
            c.setAlphaMultiplier(componentAlpha);

            if (startY + animatedHeight >= visibleTop && startY <= visibleBottom && componentAlpha > 0.01f) {
                float itemClipTop = Math.max(startY, visibleTop);
                float itemClipBottom = Math.min(startY + animatedHeight, visibleBottom);
                float itemClipHeight = itemClipBottom - itemClipTop;

                if (itemClipHeight > 0.5f) {
                    Scissor.enable(clipX, itemClipTop, clipW, itemClipHeight, guiScale);
                    context.getMatrices().pushMatrix();
                    c.render(context, (int) mouseX, (int) mouseY, delta);
                    context.getMatrices().popMatrix();
                    Scissor.disable();
                }
            }
        }

        Scissor.disable();

        boolean hasVisibleSettings = false;
        for (AbstractSettingComponent c : settingComponents) {
            float visAnim = animHandler.getVisibilityAnimations().getOrDefault(c, 0f);
            if (visAnim > 0.01f) {
                hasVisibleSettings = true;
                break;
            }
        }

        if (!hasVisibleSettings) {
            String text = Lang.get().get("no_settings_desc");
            float textSize = 6f;
            float textWidth = Fonts.BOLD.getWidth(text, textSize);
            float textHeight = Fonts.BOLD.getHeight(textSize);
            float centerX = x + (width - textWidth) / 2f;
            float centerY = y + (height - textHeight) / 2f + 10f;
            int emptyAlpha = (int) (120 * alphaMultiplier);
            Fonts.BOLD.draw(text, centerX, centerY, textSize, ((emptyAlpha & 0xFF) << 24) | (ClickGuiTheme.SETTINGS_EMPTY_TEXT_ARGB & 0xFFFFFF));
        }

        renderScrollFade(x + sideInset, clipY, width - sideInset * 2, clipH,
                scrollHandler.getSettingScrollTopFade() * alphaMultiplier,
                scrollHandler.getSettingScrollBottomFade() * alphaMultiplier);
    }

    public float calculateTotalHeight(List<AbstractSettingComponent> settingComponents, ModuleAnimationHandler animHandler) {
        float total = 0;
        for (AbstractSettingComponent c : settingComponents) {
            float heightAnim = animHandler.getHeightAnimations().getOrDefault(c, c.getSetting().isVisible() ? 1f : 0f);
            if (heightAnim <= 0.001f) continue;

            float baseHeight = getComponentBaseHeight(c);
            total += (baseHeight + SETTING_SPACING) * heightAnim;
        }
        return total;
    }

    private float getComponentBaseHeight(AbstractSettingComponent c) {
        if (c instanceof SelectComponent) return ((SelectComponent) c).getTotalHeight();
        if (c instanceof MultiSelectComponent) return ((MultiSelectComponent) c).getTotalHeight();
        if (c instanceof ColorComponent) return ((ColorComponent) c).getTotalHeight();
        return SETTING_HEIGHT;
    }

    private void renderScrollFade(float x, float y, float w, float h, float topFade, float bottomFade) {
        int accentR = (ClickGuiTheme.ACCENT_ARGB >> 16) & 0xFF;
        int accentG = (ClickGuiTheme.ACCENT_ARGB >> 8) & 0xFF;
        int accentB = ClickGuiTheme.ACCENT_ARGB & 0xFF;

        if (topFade > 0.01f) {
            for (int i = 0; i < 10; i++) {
                float fadeAlpha = 50 * topFade * (1f - i / 10f);
                Render2D.rect(x, y + i, w, 1, ((int) fadeAlpha << 24) | (ClickGuiTheme.BG_TOP_ARGB & 0xFFFFFF), 0);
            }
        }
        if (bottomFade > 0.01f) {
            for (int i = 0; i < 10; i++) {
                float fadeAlpha = 50 * bottomFade * (i / 10f);
                Render2D.rect(x, y + h - 10 + i, w, 1, ((int) fadeAlpha << 24) | (ClickGuiTheme.BG_TOP_ARGB & 0xFFFFFF), 0);
            }
        }
    }
}
