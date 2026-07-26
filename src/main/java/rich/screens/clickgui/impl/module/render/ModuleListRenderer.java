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

import java.util.List;
import java.util.Map;

public class ModuleListRenderer {

    private static final float MODULE_ITEM_HEIGHT = 26f;
    private static final float MODULE_LIST_CORNER_RADIUS = ClickGuiTheme.PANEL_CORNER_RADIUS;
    private static final float CORNER_INSET = 3f;
    private static final float STATE_BALL_SIZE = 3f;
    private static final float STATE_TEXT_OFFSET = 6f;
    private static final float BIND_BOX_HEIGHT = 9f;
    private static final float BIND_BOX_MIN_WIDTH = 18f;
    private static final float BIND_BOX_PADDING = 6f;
    private static final float BIND_WIDTH_ANIM_SPEED = 12f;

    private final ModuleAnimationHandler animationHandler;
    private final ModuleBindHandler bindHandler;
    private final ModuleDisplayHelper displayHelper;

    public ModuleListRenderer(ModuleAnimationHandler animationHandler, ModuleBindHandler bindHandler, ModuleDisplayHelper displayHelper) {
        this.animationHandler = animationHandler;
        this.bindHandler = bindHandler;
        this.displayHelper = displayHelper;
    }

    public void render(DrawContext context, List<ModuleStructure> displayModules, ModuleStructure selectedModule,
                       ModuleStructure bindingModule, float x, float y, float width, float height,
                       float mouseX, float mouseY, int guiScale, float alphaMultiplier,
                       ModuleAnimationHandler animHandler, ModuleScrollHandler scrollHandler) {

        int panelBg = ClickGuiTheme.panelAlpha(0.4f * alphaMultiplier);
        int panelBorder = ClickGuiTheme.borderAlpha(0.6f * alphaMultiplier);
        Render2D.rect(x, y, width, height, panelBg, MODULE_LIST_CORNER_RADIUS);
        Render2D.outline(x, y, width, height, 0.5f, panelBorder, MODULE_LIST_CORNER_RADIUS);

        float topInset = CORNER_INSET;
        float bottomInset = CORNER_INSET;
        float sideInset = CORNER_INSET;

        Scissor.enable(x + sideInset, y + topInset - 1.5f, width - sideInset * 2, height - topInset - bottomInset + 3, guiScale);

        if (animHandler.isCategoryTransitioning() && !animHandler.getOldModules().isEmpty()) {
            float oldAlpha = (1f - animHandler.getCategoryTransitionProgress()) * alphaMultiplier;
            float oldOffsetX = animHandler.easeInCubic(animHandler.getCategoryTransitionProgress()) * -animHandler.getCategorySlideDistance();
            float oldScale = 1f - animHandler.getCategoryTransitionProgress() * 0.1f;

            renderModuleItems(context, animHandler.getOldModules(), animHandler.getOldModuleAnimations(),
                    selectedModule, bindingModule, x, y, width, height, mouseX, mouseY,
                    oldAlpha, oldOffsetX, oldScale, (float) animHandler.getOldModuleDisplayScroll(), false, topInset, bottomInset, animHandler);
        }

        float newAlpha;
        float newOffsetX;
        float newScale;

        if (animHandler.isCategoryTransitioning()) {
            float entryProgress = Math.max(0f, (animHandler.getCategoryTransitionProgress() - 0.2f) / 0.8f);
            entryProgress = animHandler.easeOutQuart(entryProgress);
            newAlpha = entryProgress * alphaMultiplier;
            newOffsetX = (1f - entryProgress) * animHandler.getCategorySlideDistance();
            newScale = 0.9f + entryProgress * 0.1f;
        } else {
            newAlpha = alphaMultiplier;
            newOffsetX = 0f;
            newScale = 1f;
        }

        renderModuleItems(context, displayModules, animHandler.getModuleAnimations(),
                selectedModule, bindingModule, x, y, width, height, mouseX, mouseY,
                newAlpha, newOffsetX, newScale, (float) scrollHandler.getModuleDisplayScroll(), true, topInset, bottomInset, animHandler);

        Scissor.disable();

        renderScrollFade(x, y + topInset, width, height - topInset - bottomInset,
                scrollHandler.getModuleScrollTopFade() * alphaMultiplier,
                scrollHandler.getModuleScrollBottomFade() * alphaMultiplier, 80, 15);
    }

    private void renderModuleItems(DrawContext context, List<ModuleStructure> moduleList, Map<ModuleStructure, Float> animations,
                                   ModuleStructure selectedModule, ModuleStructure bindingModule,
                                   float x, float y, float width, float height, float mouseX, float mouseY,
                                   float alphaMultiplier, float offsetX, float scale, float scrollOffset,
                                   boolean interactive, float topInset, float bottomInset, ModuleAnimationHandler animHandler) {
        if (alphaMultiplier <= 0.01f) return;

        float startY = y + topInset + 2f + scrollOffset;
        float centerY = y + height / 2f;
        float visibleTop = y + topInset;
        float visibleBottom = y + height - bottomInset;

        for (int i = 0; i < moduleList.size(); i++) {
            ModuleStructure module = moduleList.get(i);
            float modY = startY + i * (MODULE_ITEM_HEIGHT + 2);

            if (modY + MODULE_ITEM_HEIGHT < visibleTop || modY > visibleBottom) continue;

            float itemProgress = animations.getOrDefault(module, 1f);
            float posAnim = animHandler.getPositionAnimations().getOrDefault(module, 1f);
            float alphaAnim = animHandler.getModuleAlphaAnimations().getOrDefault(module, 1f);
            float combinedAlpha = itemProgress * alphaMultiplier * alphaAnim;

            if (combinedAlpha <= 0.01f) continue;

            float itemAnimOffset = (1f - itemProgress) * 20f;
            float posAnimOffset = (1f - easeOutCubic(posAnim)) * 15f;

            float scaledModY = centerY + (modY - centerY) * scale;
            float scaledHeight = MODULE_ITEM_HEIGHT * scale;

            float animX = x + 3 + offsetX + itemAnimOffset + posAnimOffset;

            boolean selected = interactive && module == selectedModule;
            boolean isHighlighted = interactive && module == animHandler.getHighlightedModule() && animHandler.getHighlightAnimation() > 0.01f;
            float hoverAnim = interactive ? animHandler.getHoverAnimations().getOrDefault(module, 0f) : 0f;
            float stateAnim = interactive ? animHandler.getStateAnimations().getOrDefault(module, module.isState() ? 1f : 0f) : (module.isState() ? 1f : 0f);
            float selectedIconAnim = interactive ? animHandler.getSelectedIconAnimations().getOrDefault(module, 0f) : 0f;
            float favoriteAnim = interactive ? animHandler.getFavoriteAnimations().getOrDefault(module, 0f) : 0f;
            boolean hasSettings = displayHelper.hasSettings(module);

            float scaledWidth = (width - 6) * scale;

            int bgColor;
            if (selected) {
                bgColor = ClickGuiTheme.moduleSelectedBg(alphaMultiplier * combinedAlpha);
            } else {
                int hoverBoost = (int) (hoverAnim * 20);
                bgColor = ClickGuiTheme.moduleBg(alphaMultiplier * combinedAlpha * (0.6f + hoverAnim * 0.4f));
            }

            Render2D.rect(animX, scaledModY, scaledWidth, scaledHeight, bgColor, 5);

            if (selected) {
                float pulseValue = (float) (Math.sin(animHandler.getSelectedPulseAnimation()) * 0.5 + 0.5);
                float highlightBoost = isHighlighted ? animHandler.getHighlightAnimation() * 0.5f : 0f;

                int outlineAlpha = (int) ((60 + 40 * pulseValue + 40 * highlightBoost) * combinedAlpha);
                int outlineColor = ((outlineAlpha & 0xFF) << 24) | (ClickGuiTheme.ACCENT_ARGB & 0xFFFFFF);
                Render2D.outline(animX, scaledModY, scaledWidth, scaledHeight, 0.5f, outlineColor, 5);

                float glowAlpha = (20 + 20 * highlightBoost) * combinedAlpha;
                int glowColor = ClickGuiTheme.glowColor(glowAlpha);
                Render2D.rect(animX, scaledModY, scaledWidth, scaledHeight, glowColor, 5);
            } else if (hoverAnim > 0.01f) {
                int outlineAlpha = (int) (40 * hoverAnim * combinedAlpha);
                int outlineColor = ((outlineAlpha & 0xFF) << 24) | (ClickGuiTheme.PANEL_BORDER_LIGHT_ARGB & 0xFFFFFF);
                Render2D.outline(animX, scaledModY, scaledWidth, scaledHeight, 0.5f, outlineColor, 5);
            }

            float stateTextOffset = stateAnim * STATE_TEXT_OFFSET;

            if (stateAnim > 0.01f) {
                float ballAlpha = stateAnim * 200 * combinedAlpha;
                float ballX = animX + 4;
                float ballY = scaledModY + (scaledHeight - STATE_BALL_SIZE * scale) / 2f + 1F;
                int ballColor = ((int) ballAlpha << 24) | (ClickGuiTheme.ACCENT_ARGB & 0xFFFFFF);
                Render2D.rect(ballX, ballY, STATE_BALL_SIZE * scale, STATE_BALL_SIZE * scale,
                        ballColor, STATE_BALL_SIZE * scale / 2f);
            }

            String name = module.getName();

            int textBrightness;
            int textAlphaValue;
            if (stateAnim > 0.5f) {
                textBrightness = 240;
                textAlphaValue = (int) (255 * combinedAlpha);
            } else if (hoverAnim > 0.01f) {
                textBrightness = 200;
                textAlphaValue = (int) (200 * combinedAlpha);
            } else {
                textBrightness = 160;
                textAlphaValue = (int) (180 * combinedAlpha);
            }

            if (isHighlighted) {
                textBrightness = (int) Math.min(255, textBrightness + 30 * animHandler.getHighlightAnimation());
            }

            int textColor = ((Math.min(255, textAlphaValue) & 0xFF) << 24) | ((textBrightness & 0xFF) << 16) | ((textBrightness & 0xFF) << 8) | (textBrightness & 0xFF);

            float textX = animX + 5 + stateTextOffset;
            float textY = scaledModY + (scaledHeight - 6f * scale) / 2f;
            Fonts.BOLD.draw(name, textX, textY, 6 * scale, textColor);

            if (interactive) {
                renderBindBox(module, bindingModule, animX, scaledModY, scaledWidth, scaledHeight, scale, combinedAlpha, stateTextOffset, animHandler);

                float iconBaseX = animX + scaledWidth - 14;
                float iconY = scaledModY + (scaledHeight - 8f * scale) / 2f;

                float starX;
                if (hasSettings) {
                    starX = iconBaseX - 12;
                } else {
                    starX = iconBaseX;
                }

                int starR = (int) (80 + (255 - 80) * favoriteAnim);
                int starG = (int) (80 + (215 - 80) * favoriteAnim);
                int starB = (int) (80 + (0 - 80) * favoriteAnim);
                float starAlpha = (80 + 120 * favoriteAnim + 55 * hoverAnim) * combinedAlpha;

                Fonts.GUI_ICONS.draw("D", starX, iconY + 1, 8 * scale, new java.awt.Color(starR, starG, starB, (int) starAlpha).getRGB());

                if (hasSettings) {
                    if (selectedIconAnim > 0.01f) {
                        float gearAlpha = (150 + 50 * (isHighlighted ? animHandler.getHighlightAnimation() : 0f)) * selectedIconAnim * combinedAlpha;
                        Fonts.GUI_ICONS.draw("B", iconBaseX, iconY + 1, 8 * scale, new java.awt.Color(200, 200, 200, (int) gearAlpha).getRGB());
                    }

                    if (selectedIconAnim < 0.99f) {
                        float dotsAlpha = 120 * (1f - selectedIconAnim) * combinedAlpha;
                        Fonts.BOLD.draw("...", iconBaseX + 1f, iconY - 1f, 7 * scale, new java.awt.Color(150, 150, 150, (int) dotsAlpha).getRGB());
                    }
                }
            }
        }
    }

    private void renderBindBox(ModuleStructure module, ModuleStructure bindingModule, float moduleX, float moduleY,
                               float moduleWidth, float moduleHeight, float scale, float combinedAlpha,
                               float stateTextOffset, ModuleAnimationHandler animHandler) {
        boolean isBinding = module == bindingModule;
        int key = module.getKey();

        float bindAlpha = animHandler.getBindBoxAlphaAnimations().getOrDefault(module, 0f);

        if (bindAlpha <= 0.01f && !isBinding && (key == GLFW.GLFW_KEY_UNKNOWN || key == -1)) {
            return;
        }

        String bindText;
        if (isBinding) {
            bindText = "...";
        } else {
            bindText = bindHandler.getBindDisplayName(key);
        }

        float textWidth = Fonts.BOLD.getWidth(bindText, 5 * scale);
        float targetWidth = Math.max(BIND_BOX_MIN_WIDTH, textWidth + BIND_BOX_PADDING * 2);

        float currentWidth = animHandler.getBindBoxWidthAnimations().getOrDefault(module, targetWidth);

        float widthDiff = targetWidth - currentWidth;
        if (Math.abs(widthDiff) > 0.1f) {
            currentWidth += widthDiff * BIND_WIDTH_ANIM_SPEED * 0.016f;
            animHandler.getBindBoxWidthAnimations().put(module, currentWidth);
        } else {
            currentWidth = targetWidth;
            animHandler.getBindBoxWidthAnimations().put(module, currentWidth);
        }

        float boxHeight = BIND_BOX_HEIGHT * scale;
        float boxWidth = currentWidth * scale * bindAlpha;

        float nameWidth = Fonts.BOLD.getWidth(module.getName(), 6 * scale);
        float boxX = moduleX + 5 + stateTextOffset + nameWidth;
        float boxY = moduleY + (moduleHeight - boxHeight) / 2f + 0.5f;

        float finalAlpha = combinedAlpha * bindAlpha;

        int bgAlpha = (int) (30 * finalAlpha);
        int bgColor = ((bgAlpha & 0xFF) << 24) | (ClickGuiTheme.PANEL_BG_ARGB & 0xFFFFFF);
        Render2D.rect(boxX + 3, boxY + 0.5f, boxWidth - 6, boxHeight, bgColor, 3f * scale);

        int outlineAlpha = (int) (60 * finalAlpha);
        int outlineColor = ((outlineAlpha & 0xFF) << 24) | (ClickGuiTheme.PANEL_BORDER_ARGB & 0xFFFFFF);
        Render2D.outline(boxX + 3, boxY + 0.5f, boxWidth - 6, boxHeight, 0.5f, outlineColor, 3f * scale);

        if (bindAlpha > 0.5f) {
            int textAlpha = (int) (160 * finalAlpha);
            int textColor = ((textAlpha & 0xFF) << 24) | (ClickGuiTheme.MODULE_TEXT_ARGB & 0xFFFFFF);

            float textX = boxX + (boxWidth - textWidth) / 2f;
            float textY = boxY + (boxHeight - 5f * scale) / 2f;
            Fonts.BOLD.draw(bindText, textX, textY, 5 * scale, textColor);
        }
    }

    private void renderScrollFade(float x, float y, float w, float h, float topFade, float bottomFade, int alpha, int size) {
        if (topFade > 0.01f) {
            for (int i = 0; i < size; i++) {
                float fadeAlpha = alpha * topFade * (1f - i / (float) size);
                int fadeColor = ((int) fadeAlpha << 24) | (ClickGuiTheme.BG_TOP_ARGB & 0xFFFFFF);
                Render2D.rect(x, y + i, w, 1, fadeColor, 0);
            }
        }
        if (bottomFade > 0.01f) {
            for (int i = 0; i < size; i++) {
                float fadeAlpha = alpha * bottomFade * (i / (float) size);
                int fadeColor = ((int) fadeAlpha << 24) | (ClickGuiTheme.BG_TOP_ARGB & 0xFFFFFF);
                Render2D.rect(x, y + h - size + i, w, 1, fadeColor, 0);
            }
        }
    }

    public ModuleStructure getModuleAtPosition(List<ModuleStructure> displayModules, double mouseX, double mouseY,
                                               float listX, float listY, float listWidth, float listHeight,
                                               double scrollOffset, boolean isTransitioning) {
        if (isTransitioning) return null;
        if (mouseX < listX || mouseX > listX + listWidth || mouseY < listY || mouseY > listY + listHeight) return null;

        float startY = listY + CORNER_INSET + 2f + (float) scrollOffset;
        for (int i = 0; i < displayModules.size(); i++) {
            float modY = startY + i * (MODULE_ITEM_HEIGHT + 2);
            if (mouseX >= listX + 3 && mouseX <= listX + listWidth - 3 && mouseY >= modY && mouseY <= modY + MODULE_ITEM_HEIGHT) {
                return displayModules.get(i);
            }
        }
        return null;
    }

    public boolean isStarClicked(List<ModuleStructure> displayModules, double mouseX, double mouseY,
                                 float listX, float listY, float listWidth, float listHeight,
                                 double scrollOffset, ModuleDisplayHelper displayHelper, boolean isTransitioning) {
        if (isTransitioning) return false;

        float startY = listY + CORNER_INSET + 2f + (float) scrollOffset;
        for (int i = 0; i < displayModules.size(); i++) {
            ModuleStructure module = displayModules.get(i);
            float modY = startY + i * (MODULE_ITEM_HEIGHT + 2);

            if (mouseY >= modY && mouseY <= modY + MODULE_ITEM_HEIGHT) {
                float scaledWidth = listWidth - 6;
                float animX = listX + 3;
                boolean hasSettings = displayHelper.hasSettings(module);

                float starX;
                if (hasSettings) {
                    starX = animX + scaledWidth - 14 - 12;
                } else {
                    starX = animX + scaledWidth - 14;
                }

                if (mouseX >= starX && mouseX <= starX + 10) {
                    return true;
                }
            }
        }
        return false;
    }

    public ModuleStructure getModuleForStarClick(List<ModuleStructure> displayModules, double mouseX, double mouseY,
                                                 float listX, float listY, float listWidth, float listHeight,
                                                 double scrollOffset, ModuleDisplayHelper displayHelper, boolean isTransitioning) {
        if (isTransitioning) return null;

        float startY = listY + CORNER_INSET + 2f + (float) scrollOffset;
        for (int i = 0; i < displayModules.size(); i++) {
            ModuleStructure module = displayModules.get(i);
            float modY = startY + i * (MODULE_ITEM_HEIGHT + 2);

            if (mouseY >= modY && mouseY <= modY + MODULE_ITEM_HEIGHT) {
                float scaledWidth = listWidth - 6;
                float animX = listX + 3;
                boolean hasSettings = displayHelper.hasSettings(module);

                float starX;
                if (hasSettings) {
                    starX = animX + scaledWidth - 14 - 12;
                } else {
                    starX = animX + scaledWidth - 14;
                }

                if (mouseX >= starX && mouseX <= starX + 10) {
                    return module;
                }
            }
        }
        return null;
    }

    private float easeOutCubic(float x) {
        return 1f - (float) Math.pow(1 - x, 3);
    }
}
