package rich.screens.clickgui.impl.settingsrender;

import net.minecraft.client.gui.DrawContext;
import rich.modules.module.setting.implement.MultiSelectSetting;
import rich.screens.clickgui.impl.theme.ClickGuiTheme;
import rich.util.interfaces.AbstractSettingComponent;
import rich.util.render.Render2D;
import rich.util.render.shader.Scissor;
import rich.util.render.font.Fonts;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MultiSelectComponent extends AbstractSettingComponent {
    private final MultiSelectSetting multiSelectSetting;
    private boolean expanded = false;
    private float expandAnimation = 0f;
    private float hoverAnimation = 0f;
    private float scrollOffset = 0f;
    private float scrollOffsetAnimated = 0f;
    private boolean scrollingRight = true;
    private long scrollPauseTime = 0;

    private float descScrollOffset = 0f;
    private boolean descScrollingRight = true;
    private long descScrollPauseTime = 0;

    private final Map<String, Float> optionHoverAnimations = new HashMap<>();
    private final Map<String, Float> checkAnimations = new HashMap<>();
    private final Map<String, Float> itemAlphaAnimations = new HashMap<>();
    private final Map<String, Float> itemXPositions = new HashMap<>();
    private final Map<String, Float> itemTargetPositions = new HashMap<>();
    private final Set<String> previousSelected = new HashSet<>();
    private float noneAlphaAnimation = 0f;

    private long lastUpdateTime = System.currentTimeMillis();
    private static final float ANIMATION_SPEED = 8f;
    private static final float COLLAPSE_SPEED = 15f;
    private static final long SCROLL_PAUSE_DURATION = 2000;
    private static final float BOX_WIDTH = 65f;
    private static final float OPTION_HEIGHT = 14f;
    private static final float SCROLL_PIXELS_PER_SECOND = 20f;
    private static final float DESC_PADDING = 8f;
    private static final float ITEM_ANIMATION_SPEED = 10f;
    private static final float POSITION_ANIMATION_SPEED = 8f;

    public MultiSelectComponent(MultiSelectSetting setting) {
        super(setting);
        this.multiSelectSetting = setting;
        for (String option : setting.getList()) {
            checkAnimations.put(option, setting.isSelected(option) ? 1f : 0f);
            optionHoverAnimations.put(option, 0f);
        }
        previousSelected.addAll(setting.getSelected());
        float initX = 0;
        for (String item : setting.getList()) {
            if (setting.isSelected(item)) {
                itemAlphaAnimations.put(item, 1f);
                itemXPositions.put(item, initX);
                itemTargetPositions.put(item, initX);
                initX += Fonts.BOLD.getWidth(item + ", ", 5);
            }
        }
        noneAlphaAnimation = setting.getSelected().isEmpty() ? 1f : 0f;
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

    private void updateItemAnimations(float deltaTime) {
        Set<String> currentSelected = new HashSet<>(multiSelectSetting.getSelected());
        for (String item : currentSelected) {
            if (!itemAlphaAnimations.containsKey(item)) {
                itemAlphaAnimations.put(item, 0f);
                float lastPos = 0;
                for (String existingItem : multiSelectSetting.getList()) {
                    if (itemXPositions.containsKey(existingItem)) {
                        float pos = itemXPositions.get(existingItem);
                        float endPos = pos + Fonts.BOLD.getWidth(existingItem + ", ", 5);
                        if (endPos > lastPos) lastPos = endPos;
                    }
                }
                itemXPositions.put(item, lastPos);
                itemTargetPositions.put(item, lastPos);
            }
        }
        for (String item : itemAlphaAnimations.keySet()) {
            float newAlpha = lerp(itemAlphaAnimations.get(item), currentSelected.contains(item) ? 1f : 0f, deltaTime * ITEM_ANIMATION_SPEED);
            itemAlphaAnimations.put(item, newAlpha);
        }

        List<String> visibleItems = new ArrayList<>();
        for (String item : multiSelectSetting.getList()) {
            if (itemAlphaAnimations.containsKey(item) && itemAlphaAnimations.get(item) > 0.01f) visibleItems.add(item);
        }

        float currentTargetX = 0;
        for (int i = 0; i < visibleItems.size(); i++) {
            String item = visibleItems.get(i);
            itemTargetPositions.put(item, currentTargetX);
            float textWidth = Fonts.BOLD.getWidth(item + (i < visibleItems.size() - 1 ? ", " : ""), 5);
            currentTargetX += textWidth * itemAlphaAnimations.getOrDefault(item, 0f);
        }
        for (String item : visibleItems) {
            itemXPositions.put(item, lerp(itemXPositions.getOrDefault(item, 0f), itemTargetPositions.getOrDefault(item, 0f), deltaTime * POSITION_ANIMATION_SPEED));
        }

        List<String> toRemove = new ArrayList<>();
        for (String item : itemAlphaAnimations.keySet()) {
            if (!currentSelected.contains(item) && itemAlphaAnimations.get(item) < 0.01f) toRemove.add(item);
        }
        for (String item : toRemove) { itemAlphaAnimations.remove(item); itemXPositions.remove(item); itemTargetPositions.remove(item); }

        boolean hasVisibleItems = false;
        for (Float alpha : itemAlphaAnimations.values()) { if (alpha > 0.01f) { hasVisibleItems = true; break; } }
        noneAlphaAnimation = lerp(noneAlphaAnimation, (!hasVisibleItems && currentSelected.isEmpty()) ? 1f : 0f, deltaTime * ITEM_ANIMATION_SPEED);
        previousSelected.clear();
        previousSelected.addAll(currentSelected);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        float deltaTime = getDeltaTime();
        updateItemAnimations(deltaTime);
        hoverAnimation = lerp(hoverAnimation, isMainHover(mouseX, mouseY) ? 1f : 0f, deltaTime * ANIMATION_SPEED);
        expandAnimation = lerp(expandAnimation, expanded ? 1f : 0f, deltaTime * (expanded ? ANIMATION_SPEED : COLLAPSE_SPEED));

        int iconAlpha = (int)(200 * alphaMultiplier);
        Fonts.GUI_ICONS.draw("I", x - 0.5f, y + height / 2 - 8.5f, 9,
                ((iconAlpha & 0xFF) << 24) | (ClickGuiTheme.SETTINGS_DESC_ARGB & 0xFFFFFF));

        int nameAlpha = (int)(220 * alphaMultiplier);
        Fonts.BOLD.draw(multiSelectSetting.getName(), x + 9.5f, y + height / 2 - 7.5f, 6,
                ((nameAlpha & 0xFF) << 24) | (ClickGuiTheme.SETTINGS_TITLE_ARGB & 0xFFFFFF));

        String description = multiSelectSetting.getDescription();
        if (description != null && !description.isEmpty()) renderScrollingDescription(description, deltaTime);

        float boxX = x + width - BOX_WIDTH - 2;
        float boxY = y + height / 2 - 5;
        float boxHeight = 10f;

        int bgAlpha = 25 + (int)(hoverAnimation * 15);
        int bgCol = ((bgAlpha & 0xFF) << 24) | (ClickGuiTheme.PANEL_BORDER_LIGHT_ARGB & 0xFFFFFF);
        Render2D.rect(boxX, boxY, BOX_WIDTH, boxHeight, bgCol, 3f);
        int outlineAlpha = 60 + (int)(hoverAnimation * 40);
        int oCol = ((outlineAlpha & 0xFF) << 24) | (ClickGuiTheme.PANEL_BORDER_LIGHT_ARGB & 0xFFFFFF);
        Render2D.outline(boxX, boxY, BOX_WIDTH, boxHeight, 0.5f, oCol, 3f);

        renderSelectedText(boxX, boxY, BOX_WIDTH, boxHeight, deltaTime);
        if (expandAnimation > 0.01f) renderExpandedOptions(context, mouseX, mouseY, boxX, boxY + boxHeight + 2, deltaTime);
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
            float currentScroll = descScrollOffset * (descWidth - availableWidth + 5);
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

    private void renderSelectedText(float boxX, float boxY, float boxWidth, float boxHeight, float deltaTime) {
        float textY = boxY + boxHeight / 2 - 2.5f;
        float availableWidth = boxWidth - 4;
        float baseX = boxX + 4;
        Scissor.enable(boxX + 1, boxY, availableWidth + 2, boxHeight, 2);

        if (noneAlphaAnimation > 0.01f) {
            int noneAlpha = (int)(200 * noneAlphaAnimation * alphaMultiplier);
            Fonts.BOLD.draw("None", baseX, textY, 5,
                    ((noneAlpha & 0xFF) << 24) | (ClickGuiTheme.SETTINGS_DESC_ARGB & 0xFFFFFF));
        }

        List<String> visibleItems = new ArrayList<>();
        for (String item : multiSelectSetting.getList()) {
            if (itemAlphaAnimations.containsKey(item) && itemAlphaAnimations.get(item) > 0.01f) visibleItems.add(item);
        }
        if (visibleItems.isEmpty()) { Scissor.disable(); return; }

        float totalWidth = 0;
        for (int i = 0; i < visibleItems.size(); i++) {
            String item = visibleItems.get(i);
            totalWidth += Fonts.BOLD.getWidth(item + (i < visibleItems.size() - 1 ? ", " : ""), 5) * itemAlphaAnimations.getOrDefault(item, 0f);
        }

        if (totalWidth <= availableWidth) {
            scrollOffset = 0;
            scrollOffsetAnimated = lerp(scrollOffsetAnimated, 0, deltaTime * POSITION_ANIMATION_SPEED);
        } else {
            updateScrollAnimation(deltaTime, totalWidth, availableWidth);
            scrollOffsetAnimated = lerp(scrollOffsetAnimated, scrollOffset, deltaTime * POSITION_ANIMATION_SPEED);
        }

        float maxScroll = Math.max(0, totalWidth - availableWidth + 5);
        float currentScroll = scrollOffsetAnimated * maxScroll;
        for (int i = 0; i < visibleItems.size(); i++) {
            String item = visibleItems.get(i);
            float itemAlpha = itemAlphaAnimations.getOrDefault(item, 0f);
            float itemX = itemXPositions.getOrDefault(item, 0f);
            String displayText = item + (i < visibleItems.size() - 1 ? ", " : "");
            int alpha = (int)(200 * itemAlpha * alphaMultiplier);
            if (alpha > 0) Fonts.BOLD.draw(displayText, baseX + itemX - currentScroll, textY, 5,
                    ((alpha & 0xFF) << 24) | (ClickGuiTheme.SETTINGS_DESC_ARGB & 0xFFFFFF));
        }
        Scissor.disable();
    }

    private void updateScrollAnimation(float deltaTime, float textWidth, float availableWidth) {
        long currentTime = System.currentTimeMillis();
        if (scrollPauseTime > 0) { if (currentTime - scrollPauseTime < SCROLL_PAUSE_DURATION) return; scrollPauseTime = 0; }
        float scrollDistance = textWidth - availableWidth + 5;
        if (scrollDistance <= 0) { scrollOffset = 0; return; }
        float scrollSpeed = SCROLL_PIXELS_PER_SECOND / scrollDistance;
        if (scrollingRight) { scrollOffset += deltaTime * scrollSpeed; if (scrollOffset >= 1f) { scrollOffset = 1f; scrollingRight = false; scrollPauseTime = currentTime; } }
        else { scrollOffset -= deltaTime * scrollSpeed; if (scrollOffset <= 0f) { scrollOffset = 0f; scrollingRight = true; scrollPauseTime = currentTime; } }
    }

    private void renderExpandedOptions(DrawContext context, int mouseX, int mouseY, float boxX, float startY, float deltaTime) {
        List<String> options = multiSelectSetting.getList();
        float visibleHeight = options.size() * OPTION_HEIGHT * expandAnimation;
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
            boolean isSelected = multiSelectSetting.isSelected(option);
            float checkAnim = checkAnimations.getOrDefault(option, 0f);
            checkAnim = lerp(checkAnim, isSelected ? 1f : 0f, deltaTime * 10f);
            checkAnimations.put(option, checkAnim);

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

            if (checkAnim > 0.01f) {
                float innerSize = (checkSize - 2) * checkAnim;
                int innerAlpha = (int)(220 * checkAnim * panelAlpha);
                Render2D.rect(checkX + (checkSize - innerSize) / 2, checkY + (checkSize - innerSize) / 2, innerSize, innerSize,
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
            Fonts.BOLD.draw(displayOption, textX, textY, 5,
                    ((textAlpha & 0xFF) << 24) | (ClickGuiTheme.SETTINGS_TITLE_ARGB & 0xFFFFFF));
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
                for (String option : multiSelectSetting.getList()) {
                    if (mouseX >= boxX && mouseX <= boxX + BOX_WIDTH && mouseY >= optionY && mouseY <= optionY + OPTION_HEIGHT) {
                        if (multiSelectSetting.isSelected(option)) multiSelectSetting.getSelected().remove(option);
                        else multiSelectSetting.getSelected().add(option);
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
        return height + multiSelectSetting.getList().size() * OPTION_HEIGHT * expandAnimation;
    }
}
