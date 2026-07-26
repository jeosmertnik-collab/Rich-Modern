package rich.screens.clickgui.impl.background.render;

import rich.modules.module.category.ModuleCategory;
import rich.screens.clickgui.impl.theme.ClickGuiTheme;
import rich.util.render.Render2D;
import rich.util.render.font.Fonts;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;

public class CategoryRenderer {

    private static final ModuleCategory[] MAIN_CATEGORIES = {
            ModuleCategory.COMBAT, ModuleCategory.MOVEMENT, ModuleCategory.RENDER, ModuleCategory.PLAYER, ModuleCategory.MISC
    };
    private static final String[] MAIN_CATEGORY_ICONS = {"a", "b", "c", "d", "e"};

    private static final ModuleCategory[] EXTRA_CATEGORIES = {
            ModuleCategory.AUTOBUY
    };
    private static final String[] EXTRA_CATEGORY_ICONS = {"g"};

    private final Map<ModuleCategory, Float> animations = new HashMap<>();

    private static final float SPEED = 10f;
    private static final float ITEM_SIZE = 32f;
    private static final float ITEM_GAP = 4f;
    private static final float ICON_SIZE = 9f;
    private static final float DOT_SIZE = 3f;

    public CategoryRenderer() {
        for (ModuleCategory c : MAIN_CATEGORIES) animations.put(c, 0f);
        for (ModuleCategory c : EXTRA_CATEGORIES) animations.put(c, 0f);
    }

    public void updateAnimations(ModuleCategory selected, float dt) {
        for (ModuleCategory c : MAIN_CATEGORIES) updateAnim(c, selected, dt);
        for (ModuleCategory c : EXTRA_CATEGORIES) updateAnim(c, selected, dt);
    }

    private void updateAnim(ModuleCategory cat, ModuleCategory selected, float dt) {
        float target = cat == selected ? 1f : 0f;
        float cur = animations.getOrDefault(cat, 0f);
        float diff = target - cur;
        if (Math.abs(diff) < 0.001f) animations.put(cat, target);
        else animations.put(cat, cur + diff * SPEED * dt);
    }

    public void render(float bgX, float bgY, ModuleCategory selectedCategory, float alphaMultiplier) {
        float inset = ClickGuiTheme.PANEL_INSET;
        float catW = ClickGuiTheme.CATEGORY_PANEL_WIDTH;
        float centerX = bgX + inset + catW / 2f;
        float startY = bgY + 50f;

        for (int i = 0; i < MAIN_CATEGORY_ICONS.length; i++) {
            float y = startY + i * (ITEM_SIZE + ITEM_GAP);
            renderIcon(centerX, y, MAIN_CATEGORIES[i], MAIN_CATEGORY_ICONS[i], alphaMultiplier);
        }

        float extraStart = startY + MAIN_CATEGORY_ICONS.length * (ITEM_SIZE + ITEM_GAP) + 8f;
        for (int i = 0; i < EXTRA_CATEGORY_ICONS.length; i++) {
            float y = extraStart + i * (ITEM_SIZE + ITEM_GAP);
            renderIcon(centerX, y, EXTRA_CATEGORIES[i], EXTRA_CATEGORY_ICONS[i], alphaMultiplier);
        }
    }

    private void renderIcon(float centerX, float itemY, ModuleCategory cat, String icon, float alphaMultiplier) {
        float anim = animations.getOrDefault(cat, 0f);
        float itemX = centerX - ITEM_SIZE / 2f;
        float inset = ClickGuiTheme.PANEL_INSET;
        float catW = ClickGuiTheme.CATEGORY_PANEL_WIDTH;
        float bgX = centerX - catW / 2f;

        int hoverAlpha = (int) (12 * alphaMultiplier);
        if (hoverAlpha > 0 && anim < 0.5f) {
            Render2D.rect(bgX + inset, itemY, catW, ITEM_SIZE,
                    new Color(255, 255, 255, hoverAlpha).getRGB(), 8f);
        }

        int accentR = (ClickGuiTheme.ACCENT_ARGB >> 16) & 0xFF;
        int accentG = (ClickGuiTheme.ACCENT_ARGB >> 8) & 0xFF;
        int accentB = ClickGuiTheme.ACCENT_ARGB & 0xFF;

        if (anim > 0.01f) {
            int bgAlpha = (int) (40 * anim * alphaMultiplier);
            Render2D.rect(bgX + inset, itemY, catW, ITEM_SIZE,
                    new Color(accentR, accentG, accentB, bgAlpha).getRGB(), 8f);

            float ringAlphaF = anim * alphaMultiplier;
            for (int ring = 3; ring >= 1; ring--) {
                float ringSize = ITEM_SIZE + ring * 8f;
                int ringAlpha = (int) (8 * ringAlphaF / ring);
                if (ringAlpha > 0) {
                    Render2D.rect(centerX - ringSize / 2f, itemY + (ITEM_SIZE - ringSize) / 2f,
                            ringSize, ringSize, new Color(accentR, accentG, accentB, ringAlpha).getRGB(), ringSize / 2f);
                }
            }
        }

        int iconAlpha;
        int iconBrightness;
        if (anim > 0.5f) {
            iconBrightness = 255;
            iconAlpha = (int) (255 * alphaMultiplier);
        } else {
            iconBrightness = (int) (80 + 100 * anim);
            iconAlpha = (int) (100 + 100 * anim * alphaMultiplier);
        }

        int iconColor = ((Math.min(255, iconAlpha) & 0xFF) << 24)
                | ((Math.min(255, iconBrightness) & 0xFF) << 16)
                | ((Math.min(255, iconBrightness) & 0xFF) << 8)
                | (Math.min(255, iconBrightness) & 0xFF);

        float iconX = centerX - ICON_SIZE / 2f;
        float iconY = itemY + (ITEM_SIZE - ICON_SIZE) / 2f;
        Fonts.CATEGORY_ICONS.draw(icon, iconX, iconY, ICON_SIZE, iconColor);

        if (anim > 0.8f) {
            float dotAlpha = ((anim - 0.8f) / 0.2f) * 200 * alphaMultiplier;
            float dotX = centerX - DOT_SIZE / 2f;
            float dotY = itemY + ITEM_SIZE - DOT_SIZE - 2f;
            Render2D.rect(dotX, dotY, DOT_SIZE, DOT_SIZE,
                    new Color(accentR, accentG, accentB, (int) dotAlpha).getRGB(), DOT_SIZE / 2f);
        }
    }

    public ModuleCategory getCategoryAtPosition(double mouseX, double mouseY, float bgX, float bgY) {
        float inset = ClickGuiTheme.PANEL_INSET;
        float catW = ClickGuiTheme.CATEGORY_PANEL_WIDTH;
        float centerX = bgX + inset + catW / 2f;
        float startY = bgY + 50f;

        if (mouseX < bgX + inset || mouseX > bgX + inset + catW) return null;

        for (int i = 0; i < MAIN_CATEGORY_ICONS.length; i++) {
            float y = startY + i * (ITEM_SIZE + ITEM_GAP);
            if (mouseY >= y - 2 && mouseY <= y + ITEM_SIZE + 2) return MAIN_CATEGORIES[i];
        }

        float extraStart = startY + MAIN_CATEGORY_ICONS.length * (ITEM_SIZE + ITEM_GAP) + 8f;
        for (int i = 0; i < EXTRA_CATEGORY_ICONS.length; i++) {
            float y = extraStart + i * (ITEM_SIZE + ITEM_GAP);
            if (mouseY >= y - 2 && mouseY <= y + ITEM_SIZE + 2) return EXTRA_CATEGORIES[i];
        }

        return null;
    }
}
