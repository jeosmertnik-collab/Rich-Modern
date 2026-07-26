package rich.screens.clickgui.impl.settingsrender;

import net.minecraft.client.gui.DrawContext;
import rich.modules.module.setting.implement.SelectSetting;
import rich.screens.clickgui.impl.theme.ClickGuiTheme;
import rich.util.interfaces.AbstractSettingComponent;
import rich.util.render.Render2D;
import rich.util.render.shader.Scissor;
import rich.util.render.font.Fonts;

import java.awt.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SelectComponent extends AbstractSettingComponent {
    private final SelectSetting selectSetting;
    private boolean expanded = false;
    private float expandAnimation = 0f;
    private float hoverAnimation = 0f;

    private float descScrollOffset = 0f;
    private boolean descScrollingRight = true;
    private long descScrollPauseTime = 0;

    private float arrowRotation = 0f;

    private final Map<String, Float> optionHoverAnimations = new HashMap<>();
    private final Map<String, Float> selectAnimations = new HashMap<>();

    private String previousSelected = "";
    private float selectedTextAlpha = 1f;
    private float selectedTextSlide = 1f;
    private float newSelectedTextAlpha = 0f;
    private float newSelectedTextSlide = 0f;
    private String animatingFromText = "";
    private boolean isAnimatingSelection = false;

    private long lastUpdateTime = System.currentTimeMillis();
    private static final float ANIMATION_SPEED = 8f;
    private static final float COLLAPSE_SPEED = 15f;
    private static final float BOX_WIDTH = 65f;
    private static final float OPTION_HEIGHT = 14f;
    private static final long SCROLL_PAUSE_DURATION = 2000;
    private static final float SCROLL_PIXELS_PER_SECOND = 20f;
    private static final float DESC_PADDING = 8f;
    private static final float SELECTION_ANIMATION_SPEED = 10f;

    public SelectComponent(SelectSetting setting) {
        super(setting);
        this.selectSetting = setting;
        this.previousSelected = setting.getSelected();
        for (String option : setting.getList()) {
            optionHoverAnimations.put(option, 0f);
            selectAnimations.put(option, setting.isSelected(option) ? 1f : 0f);
        }
    }

    private float getDeltaTime() {
        long currentTime = System.currentTimeMillis();
        float dt = Math.min((currentTime - lastUpdateTime) / 1000f, 0.1f);
        lastUpdateTime = currentTime;
        return dt;
    }

    private float lerp(float current, float target, float speed) {
        float diff = target - current;
        if (Math.abs(diff) < 0.001f) return target;
        return current + diff * Math.min(speed, 1f);
    }

    private void updateSelectionAnimation(float deltaTime) {
        String currentSelected = selectSetting.getSelected();
        if (!currentSelected.equals(previousSelected) && !isAnimatingSelection) {
            animatingFromText = previousSelected;
            isAnimatingSelection = true;
            selectedTextAlpha = 1f; selectedTextSlide = 1f;
            newSelectedTextAlpha = 0f; newSelectedTextSlide = 0f;
        }
        if (isAnimatingSelection) {
            selectedTextAlpha = lerp(selectedTextAlpha, 0f, deltaTime * SELECTION_ANIMATION_SPEED);
            selectedTextSlide = lerp(selectedTextSlide, 0f, deltaTime * SELECTION_ANIMATION_SPEED);
            if (selectedTextAlpha < 0.5f) {
                newSelectedTextAlpha = lerp(newSelectedTextAlpha, 1f, deltaTime * SELECTION_ANIMATION_SPEED);
                newSelectedTextSlide = lerp(newSelectedTextSlide, 1f, deltaTime * SELECTION_ANIMATION_SPEED);
            }
            if (newSelectedTextAlpha > 0.99f && newSelectedTextSlide > 0.99f) {
                isAnimatingSelection = false;
                previousSelected = currentSelected;
                selectedTextAlpha = 1f; selectedTextSlide = 1f;
                newSelectedTextAlpha = 1f; newSelectedTextSlide = 1f;
            }
        } else {
            previousSelected = currentSelected;
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        float deltaTime = getDeltaTime();
        updateSelectionAnimation(deltaTime);
        hoverAnimation = lerp(hoverAnimation, isMainHover(mouseX, mouseY) ? 1f : 0f, deltaTime * ANIMATION_SPEED);
        expandAnimation = lerp(expandAnimation, expanded ? 1f : 0f, deltaTime * (expanded ? ANIMATION_SPEED : COLLAPSE_SPEED));
        arrowRotation = lerp(arrowRotation, expanded ? 90f : 0f, deltaTime * ANIMATION_SPEED);

        int iconAlpha = (int)(200 * alphaMultiplier);
        Fonts.GUI_ICONS.draw("J", x - 0.5f, y + height / 2 - 8.5f, 9,
                ((iconAlpha & 0xFF) << 24) | (ClickGuiTheme.SETTINGS_DESC_ARGB & 0xFFFFFF));

        int nameAlpha = (int)(220 * alphaMultiplier);
        Fonts.BOLD.draw(selectSetting.getName(), x + 9.5f, y + height / 2 - 7.5f, 6,
                ((nameAlpha & 0xFF) << 24) | (ClickGuiTheme.SETTINGS_TITLE_ARGB & 0xFFFFFF));

        String description = selectSetting.getDescription();
        if (description != null && !description.isEmpty()) {
            renderScrollingDescription(description, deltaTime);
        }

        float boxX = x + width - BOX_WIDTH - 2;
        float boxY = y + height / 2 - 5;
        float boxHeight = 10f;

        int bgAlpha = 25 + (int)(hoverAnimation * 15);
        int bgCol = ((bgAlpha & 0xFF) << 24) | (ClickGuiTheme.PANEL_BORDER_LIGHT_ARGB & 0xFFFFFF);
        Render2D.rect(boxX, boxY, BOX_WIDTH, boxHeight, bgCol, 3f);

        int outlineAlpha = 60 + (int)(hoverAnimation * 40);
        int oCol = ((outlineAlpha & 0xFF) << 24) | (ClickGuiTheme.PANEL_BORDER_LIGHT_ARGB & 0xFFFFFF);
        Render2D.outline(boxX, boxY, BOX_WIDTH, boxHeight, 0.5f, oCol, 3f);

        renderAnimatedSelectedText(boxX, boxY, boxHeight);
        if (expandAnimation > 0.01f) renderExpandedOptions(context, mouseX, mouseY, boxX, boxY + boxHeight + 2, deltaTime);
    }

    private void renderAnimatedSelectedText(float boxX, float boxY, float boxHeight) {
        float maxTextWidth = BOX_WIDTH - 14;
        float textY = boxY + boxHeight / 2 - 2.5f;
        Scissor.enable(boxX + 2, boxY, maxTextWidth + 2, boxHeight, 2);

        int textCol;
        if (isAnimatingSelection) {
            if (selectedTextAlpha > 0.01f) {
                String displayOld = truncateText(animatingFromText, maxTextWidth);
                float slideOffset = (1f - selectedTextSlide) * -15f;
                int alpha = (int)(200 * selectedTextAlpha * alphaMultiplier);
                Fonts.BOLD.draw(displayOld, boxX + 4 + slideOffset, textY, 5,
                        ((alpha & 0xFF) << 24) | (ClickGuiTheme.SETTINGS_DESC_ARGB & 0xFFFFFF));
            }
            if (newSelectedTextAlpha > 0.01f) {
                String displayNew = truncateText(selectSetting.getSelected(), maxTextWidth);
                float slideOffset = (1f - newSelectedTextSlide) * 20f;
                int alpha = (int)(200 * newSelectedTextAlpha * alphaMultiplier);
                textCol = ((alpha & 0xFF) << 24) | (ClickGuiTheme.SETTINGS_TITLE_ARGB & 0xFFFFFF);
                Fonts.BOLD.draw(displayNew, boxX + 4 + slideOffset, textY, 5, textCol);
            }
        } else {
            String displaySelected = truncateText(selectSetting.getSelected(), maxTextWidth);
            int alpha = (int)(200 * alphaMultiplier);
            textCol = ((alpha & 0xFF) << 24) | (ClickGuiTheme.SETTINGS_DESC_ARGB & 0xFFFFFF);
            Fonts.BOLD.draw(displaySelected, boxX + 4, textY, 5, textCol);
        }
        Scissor.disable();
    }

    private String truncateText(String text, float maxWidth) {
        if (Fonts.BOLD.getWidth(text, 5) <= maxWidth) return text;
        String truncated = text;
        while (Fonts.BOLD.getWidth(truncated + "..", 5) > maxWidth && truncated.length() > 1) truncated = truncated.substring(0, truncated.length() - 1);
        return truncated + "..";
    }

    private void renderScrollingDescription(String description, float deltaTime) {
        float descY = y + height / 2 + 0.5f;
        float boxX = x + width - BOX_WIDTH - 2;
        float availableWidth = boxX - x - DESC_PADDING;
        float descWidth = Fonts.BOLD.getWidth(description, 5);
        int descAlpha = (int)(120 * alphaMultiplier);
        int descCol = ((descAlpha & 0xFF) << 24) | (ClickGuiTheme.SETTINGS_DESC_ARGB & 0xFFFFFF);

        if (descWidth <= availableWidth) {
            descScrollOffset = 0;
            Fonts.BOLD.draw(description, x + 0.5f, descY, 5, descCol);
        } else {
            updateDescScrollAnimation(deltaTime, descWidth, availableWidth);
            float maxScroll = descWidth - availableWidth + 5;
            float currentScroll = descScrollOffset * maxScroll;
            Scissor.enable(x, descY - 2, availableWidth, 10, 2);
            Fonts.BOLD.draw(description, x + 0.5f - currentScroll, descY, 5, descCol);
            Scissor.disable();
        }
    }

    private void updateDescScrollAnimation(float deltaTime, float textWidth, float availableWidth) {
        long currentTime = System.currentTimeMillis();
        if (descScrollPauseTime > 0) { if (currentTime - descScrollPauseTime < SCROLL_PAUSE_DURATION) return; descScrollPauseTime = 0; }
        float scrollDistance = textWidth - availableWidth + 5;
        if (scrollDistance <= 0) { descScrollOffset = 0; return; }
        float scrollSpeed = SCROLL_PIXELS_PER_SECOND / scrollDistance;
        if (descScrollingRight) { descScrollOffset += deltaTime * scrollSpeed; if (descScrollOffset >= 1f) { descScrollOffset = 1f; descScrollingRight = false; descScrollPauseTime = currentTime; } }
        else { descScrollOffset -= deltaTime * scrollSpeed; if (descScrollOffset <= 0f) { descScrollOffset = 0f; descScrollingRight = true; descScrollPauseTime = currentTime; } }
    }

    private void renderExpandedOptions(DrawContext context, int mouseX, int mouseY, float boxX, float startY, float deltaTime) {
        List<String> options = selectSetting.getList();
        float fullPanelHeight = options.size() * OPTION_HEIGHT;
        float visibleHeight = fullPanelHeight * expandAnimation;
        float panelAlpha = expandAnimation * alphaMultiplier;

        int panelBgAlpha = (int)(200 * panelAlpha);
        int panelBg = ((panelBgAlpha & 0xFF) << 24) | (ClickGuiTheme.PANEL_BG_SOLID_ARGB & 0xFFFFFF);
        Render2D.rect(boxX, startY, BOX_WIDTH, visibleHeight, panelBg, 3f);
        int panelOutlineAlpha = (int)(100 * panelAlpha);
        int panelOutline = ((panelOutlineAlpha & 0xFF) << 24) | (ClickGuiTheme.PANEL_BORDER_ARGB & 0xFFFFFF);
        Render2D.outline(boxX, startY, BOX_WIDTH, visibleHeight, 0.5f, panelOutline, 3f);

        if (visibleHeight < 1f) return;
        Scissor.enable(boxX, startY, BOX_WIDTH, visibleHeight, 2);

        int aR = (ClickGuiTheme.ACCENT_ARGB >> 16) & 0xFF;
        int aG = (ClickGuiTheme.ACCENT_ARGB >> 8) & 0xFF;
        int aB = ClickGuiTheme.ACCENT_ARGB & 0xFF;

        float optionY = startY;
        for (int i = 0; i < options.size(); i++) {
            String option = options.get(i);
            boolean optionHovered = mouseX >= boxX && mouseX <= boxX + BOX_WIDTH &&
                    mouseY >= optionY && mouseY <= optionY + OPTION_HEIGHT && expandAnimation > 0.8f;

            float hoverAnim = optionHoverAnimations.getOrDefault(option, 0f);
            hoverAnim = lerp(hoverAnim, optionHovered ? 1f : 0f, deltaTime * ANIMATION_SPEED);
            optionHoverAnimations.put(option, hoverAnim);

            boolean isSelected = selectSetting.isSelected(option);
            float selectAnim = selectAnimations.getOrDefault(option, 0f);
            selectAnim = lerp(selectAnim, isSelected ? 1f : 0f, deltaTime * 10f);
            selectAnimations.put(option, selectAnim);

            if (hoverAnim > 0.01f) {
                int hoverBgAlpha = (int)(30 * hoverAnim * panelAlpha);
                Render2D.rect(boxX + 2, optionY + 1, BOX_WIDTH - 4, OPTION_HEIGHT - 2,
                        new Color(aR, aG, aB, hoverBgAlpha).getRGB(), 2f);
            }

            float checkSize = 6f;
            float checkX = boxX + 5;
            float checkY = optionY + OPTION_HEIGHT / 2 - checkSize / 2;
            int checkBgAlpha = (int)((30 + hoverAnim * 15) * panelAlpha);
            int checkBg = ((checkBgAlpha & 0xFF) << 24) | (ClickGuiTheme.PANEL_BORDER_ARGB & 0xFFFFFF);
            Render2D.rect(checkX, checkY, checkSize, checkSize, checkBg, 2f);

            if (selectAnim > 0.01f) {
                float innerSize = (checkSize - 2) * selectAnim;
                float innerX = checkX + (checkSize - innerSize) / 2;
                float innerY = checkY + (checkSize - innerSize) / 2;
                int innerAlpha = (int)(220 * selectAnim * panelAlpha);
                Render2D.rect(innerX, innerY, innerSize, innerSize,
                        new Color(aR, aG, aB, innerAlpha).getRGB(), 1.5f);
            }

            float textX = checkX + checkSize + 4;
            float textY = optionY + OPTION_HEIGHT / 2 - 2.5f;
            float availableTextWidth = BOX_WIDTH - checkSize - 14;
            String displayOption = option;
            if (Fonts.BOLD.getWidth(option, 5) > availableTextWidth) {
                while (Fonts.BOLD.getWidth(displayOption + "..", 5) > availableTextWidth && displayOption.length() > 1) displayOption = displayOption.substring(0, displayOption.length() - 1);
                displayOption += "..";
            }
            int textAlpha = (int)(200 * panelAlpha);
            int textColor = ((textAlpha & 0xFF) << 24) | (ClickGuiTheme.SETTINGS_TITLE_ARGB & 0xFFFFFF);
            Fonts.BOLD.draw(displayOption, textX, textY, 5, textColor);
            optionY += OPTION_HEIGHT;
        }
        Scissor.disable();
    }

    private boolean isMainHover(double mouseX, double mouseY) {
        float boxX = x + width - BOX_WIDTH - 2;
        float boxY = y + height / 2 - 5;
        return mouseX >= boxX && mouseX <= boxX + BOX_WIDTH && mouseY >= boxY && mouseY <= boxY + 10f;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (isMainHover(mouseX, mouseY)) { expanded = !expanded; return true; }
            if (expanded && expandAnimation > 0.8f) {
                float boxX = x + width - BOX_WIDTH - 2;
                float boxY = y + height / 2 - 5;
                float optionY = boxY + 12f;
                for (String option : selectSetting.getList()) {
                    if (mouseX >= boxX && mouseX <= boxX + BOX_WIDTH && mouseY >= optionY && mouseY <= optionY + OPTION_HEIGHT) {
                        selectSetting.setSelected(option);
                        expanded = false;
                        return true;
                    }
                    optionY += OPTION_HEIGHT;
                }
            }
        }
        return false;
    }

    @Override
    public void tick() {}

    @Override
    public boolean isHover(double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    public float getTotalHeight() {
        return height + selectSetting.getList().size() * OPTION_HEIGHT * expandAnimation;
    }
}
