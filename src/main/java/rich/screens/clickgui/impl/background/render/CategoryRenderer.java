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
    private static final String[] MAIN_CATEGORY_NAMES = {"Combat", "Movement", "Render", "Player", "Util"};
    private static final String[] MAIN_CATEGORY_ICONS = {"a", "b", "c", "d", "e"};

    private static final ModuleCategory[] EXTRA_CATEGORIES = {
            ModuleCategory.AUTOBUY
    };
    private static final String[] EXTRA_CATEGORY_NAMES = {"AutoBuy"};
    private static final String[] EXTRA_CATEGORY_ICONS = {"g"};

    private final Map<ModuleCategory, Float> categoryAnimations = new HashMap<>();

    private static final float ANIMATION_SPEED = 8f;
    private static final float MAX_OFFSET = 5f;
    private static final float BALL_SIZE = 3f;
    private static final float TEXT_SIZE = 6f;
    private static final float ICON_SIZE = 6f;
    private static final float ICON_SPACING = 4f;
    private static final float SECTION_TEXT_SIZE = 5f;
    private static final float EXTRA_CATEGORY_OFFSET = 10f;

    public CategoryRenderer() {
        for (ModuleCategory cat : MAIN_CATEGORIES) {
            categoryAnimations.put(cat, 0f);
        }
        for (ModuleCategory cat : EXTRA_CATEGORIES) {
            categoryAnimations.put(cat, 0f);
        }
    }

    public void updateAnimations(ModuleCategory selectedCategory, float deltaTime) {
        for (ModuleCategory cat : MAIN_CATEGORIES) {
            updateCategoryAnimation(cat, selectedCategory, deltaTime);
        }
        for (ModuleCategory cat : EXTRA_CATEGORIES) {
            updateCategoryAnimation(cat, selectedCategory, deltaTime);
        }
    }

    private void updateCategoryAnimation(ModuleCategory cat, ModuleCategory selected, float deltaTime) {
        float target = cat == selected ? 1f : 0f;
        float current = categoryAnimations.getOrDefault(cat, 0f);

        float diff = target - current;
        float change = diff * ANIMATION_SPEED * deltaTime;

        if (Math.abs(diff) < 0.001f) {
            categoryAnimations.put(cat, target);
        } else {
            categoryAnimations.put(cat, current + change);
        }
    }

    public void render(float bgX, float bgY, ModuleCategory selectedCategory, float alphaMultiplier) {
        float inset = ClickGuiTheme.PANEL_INSET;
        float catW = ClickGuiTheme.CATEGORY_PANEL_WIDTH;
        float centerX = bgX + inset + catW / 2f;

        renderMainCategories(centerX, bgY, alphaMultiplier);
        renderExtraCategories(centerX, bgY, alphaMultiplier);
    }

    private void renderMainCategories(float centerX, float bgY, float alphaMultiplier) {
        float startY = bgY + 70f;
        for (int i = 0; i < MAIN_CATEGORY_NAMES.length; i++) {
            ModuleCategory cat = MAIN_CATEGORIES[i];
            float animation = categoryAnimations.getOrDefault(cat, 0f);
            float textY = startY + i * 38f;
            renderCategoryItem(centerX, textY, MAIN_CATEGORY_NAMES[i], MAIN_CATEGORY_ICONS[i], animation, alphaMultiplier);
        }
    }

    private void renderExtraCategories(float centerX, float bgY, float alphaMultiplier) {
        float startY = bgY + 70f + MAIN_CATEGORY_NAMES.length * 38f + 10f;
        for (int i = 0; i < EXTRA_CATEGORY_NAMES.length; i++) {
            ModuleCategory cat = EXTRA_CATEGORIES[i];
            float animation = categoryAnimations.getOrDefault(cat, 0f);
            float textY = startY + i * 38f;
            renderCategoryItem(centerX, textY, EXTRA_CATEGORY_NAMES[i], EXTRA_CATEGORY_ICONS[i], animation, alphaMultiplier);
        }
    }

    private void renderCategoryItem(float centerX, float textY, String name, String icon, float animation, float alphaMultiplier) {
        float offsetX = animation * MAX_OFFSET;

        int baseGray = 120;
        int targetWhite = 255;
        int colorValue = (int) (baseGray + (targetWhite - baseGray) * animation);
        int alpha = (int) ((128 + 127 * animation) * alphaMultiplier);
        Color textColor = new Color(colorValue, colorValue, colorValue, alpha);

        float iconWidth = Fonts.CATEGORY_ICONS.getWidth(icon, ICON_SIZE);
        float textWidth = Fonts.BOLD.getWidth(name, TEXT_SIZE);
        float totalWidth = iconWidth + ICON_SPACING + textWidth;
        float startX = centerX - totalWidth / 2f + offsetX;

        Fonts.CATEGORY_ICONS.draw(icon, startX, textY + 0.5f, ICON_SIZE, textColor.getRGB());

        if (animation > 0.01f) {
            float lineWidth = totalWidth * animation;
            float lineAlpha = animation * 50 * alphaMultiplier;
            float lineX = centerX - lineWidth / 2f + offsetX;
            Render2D.rect(lineX, textY + 11f, lineWidth, 0.5f, new Color(255, 255, 255, (int) lineAlpha).getRGB(), 0);

            float ballAlpha = animation * 200 * alphaMultiplier;
            float ballX = centerX - totalWidth / 2f - 10f + offsetX;
            float ballY = textY + 2.5f;
            Render2D.rect(ballX, ballY, BALL_SIZE, BALL_SIZE, new Color(255, 255, 255, (int) ballAlpha).getRGB(), BALL_SIZE / 2f);
        }

        Fonts.BOLD.draw(name, startX + iconWidth + ICON_SPACING, textY, TEXT_SIZE, textColor.getRGB());
    }

    public ModuleCategory getCategoryAtPosition(double mouseX, double mouseY, float bgX, float bgY) {
        float inset = ClickGuiTheme.PANEL_INSET;
        float catW = ClickGuiTheme.CATEGORY_PANEL_WIDTH;
        float catLeft = bgX + inset;
        float catRight = bgX + inset + catW;

        if (mouseX < catLeft || mouseX > catRight) return null;

        float startY = bgY + 70f;
        for (int i = 0; i < MAIN_CATEGORY_NAMES.length; i++) {
            float catY = startY + i * 38f;
            if (mouseY >= catY - 5 && mouseY <= catY + 15) {
                return MAIN_CATEGORIES[i];
            }
        }

        float extraStartY = startY + MAIN_CATEGORY_NAMES.length * 38f + 10f;
        for (int i = 0; i < EXTRA_CATEGORIES.length; i++) {
            float catY = extraStartY + i * 38f;
            if (mouseY >= catY - 5 && mouseY <= catY + 15) {
                return EXTRA_CATEGORIES[i];
            }
        }

        return null;
    }
}
