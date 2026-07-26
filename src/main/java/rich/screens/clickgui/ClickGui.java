package rich.screens.clickgui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import org.joml.Matrix4fStack;
import org.lwjgl.glfw.GLFW;
import rich.IMinecraft;
import rich.Initialization;
import rich.modules.module.category.ModuleCategory;
import rich.modules.module.ModuleStructure;
import rich.modules.impl.render.Hud;
import rich.screens.clickgui.impl.DragHandler;
import rich.screens.clickgui.impl.autobuy.autobuyui.AutoBuyRenderer;
import rich.screens.clickgui.impl.background.BackgroundComponent;
import rich.screens.clickgui.impl.configs.ConfigsRenderer;
import rich.screens.clickgui.impl.module.ModuleComponent;
import rich.screens.clickgui.impl.settingsrender.BindComponent;
import rich.screens.clickgui.impl.settingsrender.TextComponent;
import rich.screens.clickgui.impl.theme.ClickGuiTheme;
import rich.screens.changelog.ChangelogScreen;
import rich.util.animations.Direction;
import rich.util.lang.Lang;
import rich.util.animations.GuiAnimation;
import rich.util.interfaces.AbstractSettingComponent;
import rich.util.math.FrameRateCounter;
import rich.util.render.Render2D;
import rich.util.render.font.Fonts;
import rich.util.render.shader.Scissor;
import rich.util.render.gif.GifRender;

import java.util.ArrayList;
import java.util.List;

public class ClickGui extends Screen implements IMinecraft {
    public static ClickGui INSTANCE = new ClickGui();
    private static final int FIXED_GUI_SCALE = 2;

    private final BackgroundComponent background = new BackgroundComponent();
    private final ModuleComponent moduleComponent = new ModuleComponent();
    private final AutoBuyRenderer autoBuyRenderer = new AutoBuyRenderer();
    private final ConfigsRenderer configsRenderer = new ConfigsRenderer();
    private final DragHandler dragHandler = new DragHandler();
    private ModuleCategory selectedCategory = ModuleCategory.COMBAT;

    private final GuiAnimation openAnimation = new GuiAnimation();
    private boolean closing = false;
    private boolean waitingForSlide = false;
    private boolean slideTriggered = false;

    private float hintAlphaAnimation = 0f;
    private long lastHintUpdateTime = System.currentTimeMillis();
    private static final float HINT_ANIM_SPEED = 6f;
    private static final float OFFSET_THRESHOLD = 5f;

    private int lastMouseX;
    private int lastMouseY;
    private float lastDelta;

    public ClickGui() {
        super(Text.of("MenuScreen"));
    }

    public boolean isClosing() {
        return closing;
    }

    @Override
    protected void init() {
        super.init();
        closing = false;
        waitingForSlide = false;
        slideTriggered = false;
        openAnimation.setMs(250).setValue(1.0).setDirection(Direction.FORWARDS).reset();
        hintAlphaAnimation = 0f;
        lastHintUpdateTime = System.currentTimeMillis();

        long handle = mc.getWindow().getHandle();
        double centerX = mc.getWindow().getWidth() / 2.0;
        double centerY = mc.getWindow().getHeight() / 2.0;
        GLFW.glfwSetCursorPos(handle, centerX, centerY);

        background.setSearchActive(false);
        autoBuyRenderer.resetForClose();
        updateModules();
    }

    private void updateModules() {
        List<ModuleStructure> modules = new ArrayList<>();
        try {
            var repo = Initialization.getInstance().getManager().getModuleRepository();
            if (repo != null) {
                for (ModuleStructure m : repo.modules()) {
                    if (m.getCategory() == selectedCategory) modules.add(m);
                }
            }
        } catch (Exception ignored) {}
        moduleComponent.updateModules(modules, selectedCategory);
    }

    public void openGui() {
        if (mc.currentScreen == null) {
            closing = false;
            waitingForSlide = false;
            slideTriggered = false;
            openAnimation.setMs(250).setValue(1.0).setDirection(Direction.FORWARDS).reset();

            try {
                Hud hud = Hud.getInstance();
                if (hud != null) {
                    hud.applyLanguage();
                    ClickGuiTheme.applyStyle(hud.getStyle());
                    ClickGuiTheme.applyAccent(hud.accentColor);
                }
            } catch (Exception ignored) {}

            if (ChangelogScreen.shouldShow()) {
                mc.setScreen(new ChangelogScreen());
                return;
            }

            mc.setScreen(this);
        }
    }

    @Override
    public void tick() {
        GifRender.tick();
        moduleComponent.tick();
        try {
            Hud hud = Hud.getInstance();
            if (hud != null) hud.applyLanguage();
        } catch (Exception ignored) {}
        super.tick();
    }

    private final float[] bgResult = new float[4];

    private float[] calculateBackground(float scale) {
        int vw = mc.getWindow().getWidth() / FIXED_GUI_SCALE;
        int vh = mc.getWindow().getHeight() / FIXED_GUI_SCALE;
        bgResult[0] = (vw - BackgroundComponent.BG_WIDTH) / 2f + dragHandler.getOffsetX();
        bgResult[1] = (vh - BackgroundComponent.BG_HEIGHT) / 2f + dragHandler.getOffsetY();
        bgResult[2] = vw;
        bgResult[3] = vh;
        return bgResult;
    }

    private boolean isAnyBindListening() {
        for (AbstractSettingComponent c : moduleComponent.getSettingComponents()) {
            if (c instanceof BindComponent bindComponent && bindComponent.isListening()) {
                return true;
            }
        }
        return false;
    }

    private void updateHintAnimation() {
        long currentTime = System.currentTimeMillis();
        float deltaTime = Math.min((currentTime - lastHintUpdateTime) / 1000f, 0.1f);
        lastHintUpdateTime = currentTime;

        float offsetX = Math.abs(dragHandler.getOffsetX());
        float offsetY = Math.abs(dragHandler.getOffsetY());
        boolean shouldShow = (offsetX > OFFSET_THRESHOLD || offsetY > OFFSET_THRESHOLD);

        float target = shouldShow ? 1f : 0f;
        float diff = target - hintAlphaAnimation;

        if (Math.abs(diff) < 0.001f) {
            hintAlphaAnimation = target;
        } else {
            hintAlphaAnimation += diff * HINT_ANIM_SPEED * deltaTime;
            hintAlphaAnimation = Math.max(0f, Math.min(1f, hintAlphaAnimation));
        }
    }

    private boolean isModuleCategory(ModuleCategory category) {
        return category != ModuleCategory.AUTOBUY ;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        lastDelta = delta;

        FrameRateCounter.INSTANCE.recordFrame();

        if (waitingForSlide && selectedCategory == ModuleCategory.AUTOBUY) {
            if (!slideTriggered) {
                autoBuyRenderer.triggerSlideOut();
                slideTriggered = true;
            }

            if (autoBuyRenderer.isSlideOutComplete()) {
                waitingForSlide = false;
                slideTriggered = false;
                startActualClose();
            }
        }

        if (closing && !waitingForSlide && openAnimation.isFinished(Direction.BACKWARDS)) {
            closing = false;
            TextComponent.typing = false;
            moduleComponent.setBindingModule(null);
            dragHandler.stopDrag();
            autoBuyRenderer.resetForClose();
            mc.currentScreen = null;
        }
    }

    public void renderOverlay(DrawContext context, RenderTickCounter tickCounter) {
        if (mc.getWindow() == null) return;

        float delta = lastDelta;
        int mouseX = lastMouseX;
        int mouseY = lastMouseY;

        float scrollSpeed = Math.min(1f, 60f / Math.max(FrameRateCounter.INSTANCE.getFps(), 1));
        float animValue = openAnimation.getOutput().floatValue();

        int screenWidth = mc.getWindow().getScaledWidth();
        int screenHeight = mc.getWindow().getScaledHeight();

        context.createNewRootLayer();
        Render2D.beginOverlay();

        Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        modelViewStack.identity();

        int dimAlpha = (int) (220 * (animValue < 0.1f ? 0f : animValue));
        if (dimAlpha > 0) {
            Render2D.rect(0, 0, 5000, 5000, (dimAlpha << 24), 0);
        }

        int guiScale = mc.getWindow().calculateScaleFactor(mc.options.getGuiScale().getValue(), mc.forcesUnicodeFont());
        float scale = (float) FIXED_GUI_SCALE / guiScale;

        modelViewStack.scale(scale, scale, 1.0f);

        float mx = mouseX / scale, my = mouseY / scale;

        if (!closing || waitingForSlide) {
            dragHandler.update(mx, my);
        }

        updateHintAnimation();

        context.getMatrices().pushMatrix();
        context.getMatrices().scale(scale, scale);

        float[] bg = calculateBackground(scale);
        float bgX = Math.round(bg[0]);
        float bgY = Math.round(bg[1]);
        int vw = (int) bg[2];
        int vh = (int) bg[3];

        float yOffset;
        if (closing && !waitingForSlide) {
            yOffset = (1f - animValue) * 30f;
        } else {
            yOffset = (1f - animValue) * -15f;
        }
        bgY += yOffset;

        float alphaMultiplier = animValue < 0.1f ? 0f : animValue;

        background.render(context, bgX, bgY, selectedCategory, delta, alphaMultiplier);
        background.renderCategoryPanel(bgX, bgY, alphaMultiplier);

        background.renderHeader(bgX, bgY, selectedCategory, alphaMultiplier);
        background.renderCategoryNames(bgX, bgY, selectedCategory, alphaMultiplier);

        float mlX = bgX + ClickGuiTheme.CATEGORY_PANEL_WIDTH + ClickGuiTheme.PANEL_INSET * 2 + 4;
        float mlY = bgY + ClickGuiTheme.PANEL_TOP_OFFSET;
        float mlW = ClickGuiTheme.MODULE_LIST_WIDTH;
        float mlH = BackgroundComponent.BG_HEIGHT - ClickGuiTheme.PANEL_TOP_OFFSET - ClickGuiTheme.PANEL_INSET;

        float spX = mlX + mlW + ClickGuiTheme.PANEL_INSET;
        float spY = bgY + ClickGuiTheme.PANEL_TOP_OFFSET;
        float spW = ClickGuiTheme.SETTINGS_PANEL_WIDTH;
        float spH = BackgroundComponent.BG_HEIGHT - ClickGuiTheme.PANEL_TOP_OFFSET - ClickGuiTheme.PANEL_INSET;

        float normalAlpha = background.getNormalPanelAlpha();
        float searchAlpha = background.getSearchPanelAlpha();

        if (normalAlpha > 0.01f) {
            configsRenderer.render(context, bgX, bgY, mx, my, delta, FIXED_GUI_SCALE, alphaMultiplier * normalAlpha, selectedCategory);

            boolean isAutoBuySliding = autoBuyRenderer.isSliding();
            boolean shouldRenderModules = isModuleCategory(selectedCategory);
            boolean slidingToModuleCategory = isAutoBuySliding && isModuleCategory(selectedCategory);

            if (shouldRenderModules || slidingToModuleCategory) {
                moduleComponent.updateScroll(delta, scrollSpeed);
                moduleComponent.updateScrollFades(delta, scrollSpeed, mlH, spH);
                moduleComponent.renderModuleList(context, mlX, mlY, mlW, mlH, mx, my, FIXED_GUI_SCALE, alphaMultiplier * normalAlpha);
                moduleComponent.renderSettingsPanel(context, spX, spY, spW, spH, mx, my, delta, FIXED_GUI_SCALE, alphaMultiplier * normalAlpha);
            }

            autoBuyRenderer.render(context, bgX, bgY, mx, my, delta, FIXED_GUI_SCALE, alphaMultiplier * normalAlpha, selectedCategory);
        }

        if (searchAlpha > 0.01f) {
            background.renderSearchResults(context, bgX, bgY, mx, my, FIXED_GUI_SCALE, alphaMultiplier);
        }

        Scissor.reset();

        if (!closing && normalAlpha > 0.01f && isModuleCategory(selectedCategory)) {
            renderTooltip(bgX, bgY, mx, my, mlX, mlY, mlW, mlH, alphaMultiplier);
        }

        String uid = antidaunleak.api.UserProfile.getInstance().profile("uid");
        String displayName = (uid != null && !uid.isEmpty() && !uid.equals("null"))
                ? Lang.get().get("uid") + ": " + uid
                : Lang.get().get("uid") + ": " + mc.getSession().getUsername();
        if (displayName.length() > 6) {
            float uidW = Fonts.BOLD.getWidth(displayName, 5f);
            float uidX = bgX + ClickGuiTheme.BG_WIDTH / 2f - uidW / 2f;
            float uidY = bgY - 8f;
            int uidAlpha = (int) (180 * alphaMultiplier);
            Fonts.BOLD.draw(displayName, uidX, uidY, 5f, (uidAlpha << 24) | 0xA0A0B4);
        }

        float finalHintAlpha = hintAlphaAnimation * alphaMultiplier;
        if (finalHintAlpha > 0.01f) {
            int hintAlpha = (int) (255 * finalHintAlpha);
            float centerX = vw / 2f;
            float centerY = vh / 2f;
            float textY = centerY + BackgroundComponent.BG_HEIGHT / 2f + 10f;
//            Fonts.TEST.drawCentered("Press CTRL + ALT to reset position", centerX, textY + 65, 6, new Color(150, 150, 150, hintAlpha).getRGB());
        }

        context.getMatrices().popMatrix();
        modelViewStack.popMatrix();
        Render2D.endOverlay();
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (closing) return false;

        int guiScale = mc.getWindow().calculateScaleFactor(mc.options.getGuiScale().getValue(), mc.forcesUnicodeFont());
        float scale = (float) FIXED_GUI_SCALE / guiScale;
        double mx = click.x() / scale, my = click.y() / scale;

        float[] bg = calculateBackground(scale);
        float bgX = Math.round(bg[0]), bgY = Math.round(bg[1]);

        if (background.isSearchBoxHovered(mx, my, bgX, bgY) && click.button() == 0) {
            background.setSearchActive(true);
            return true;
        }

        if (background.isSearchActive()) {
            if (click.button() == 0) {
                ModuleStructure searchModule = background.getSearchModuleAtPosition(mx, my, bgX, bgY);
                if (searchModule != null) {
                    searchModule.switchState();
                    return true;
                }

            float panelX = bgX + ClickGuiTheme.CATEGORY_PANEL_WIDTH + ClickGuiTheme.PANEL_INSET * 2 + 4;
            float panelY = bgY + ClickGuiTheme.PANEL_TOP_OFFSET;
            float panelW = ClickGuiTheme.BG_WIDTH - ClickGuiTheme.CATEGORY_PANEL_WIDTH - ClickGuiTheme.PANEL_INSET * 3 - 4;
            float panelH = BackgroundComponent.BG_HEIGHT - ClickGuiTheme.PANEL_TOP_OFFSET - ClickGuiTheme.PANEL_INSET;

            if (mx >= panelX && mx <= panelX + panelW && my >= panelY && my <= panelY + panelH) {
                return true;
            }

            if (!background.isSearchBoxHovered(mx, my, bgX, bgY)) {
                background.setSearchActive(false);
            }
        } else if (click.button() == 1) {
                ModuleStructure searchModule = background.getSearchModuleAtPosition(mx, my, bgX, bgY);
                if (searchModule != null) {
                    background.setSearchActive(false);
                    selectedCategory = searchModule.getCategory();
                    moduleComponent.selectModuleFromSearch(searchModule);
                    updateModules();
                    return true;
                }
            }
            return true;
        }

        if (selectedCategory == ModuleCategory.AUTOBUY) {
            if (autoBuyRenderer.mouseClicked(mx, my, click.button(), bgX, bgY, selectedCategory)) {
                return true;
            }
        }

//        if (selectedCategory == ModuleCategory.CONFIGS) {
//            if (configsRenderer.mouseClicked(mx, my, click.button(), bgX, bgY, selectedCategory)) {
//                return true;
//            }
//        }

        float mlX = bgX + ClickGuiTheme.CATEGORY_PANEL_WIDTH + ClickGuiTheme.PANEL_INSET * 2 + 4;
        float mlY = bgY + ClickGuiTheme.PANEL_TOP_OFFSET;
        float mlW = ClickGuiTheme.MODULE_LIST_WIDTH;
        float mlH = BackgroundComponent.BG_HEIGHT - ClickGuiTheme.PANEL_TOP_OFFSET - ClickGuiTheme.PANEL_INSET - 2f;

        if (click.button() == 2) {
            if (isAnyBindListening()) {
                for (AbstractSettingComponent c : moduleComponent.getSettingComponents()) {
                    if (c instanceof BindComponent bindComponent && bindComponent.isListening()) {
                        bindComponent.handleMiddleMouseBind();
                        return true;
                    }
                }
            }

            if (moduleComponent.getBindingModule() != null) {
                return true;
            }

            ModuleStructure module = moduleComponent.getModuleAtPosition(mx, my, mlX, mlY, mlW, mlH);
            if (module != null) {
                moduleComponent.setBindingModule(module);
                return true;
            }

            if (dragHandler.startDrag(mx, my, bgX, bgY, BackgroundComponent.BG_WIDTH, BackgroundComponent.BG_HEIGHT)) {
                return true;
            }
        }

        ModuleCategory cat = background.getCategoryAtPosition(mx, my, bgX, bgY);
        if (cat != null) {
            selectedCategory = cat;
            updateModules();
            return true;
        }

        if (isModuleCategory(selectedCategory)) {
            ModuleStructure toggleModule = moduleComponent.getModuleAtPosition(mx, my, mlX, mlY, mlW, mlH);
            if (toggleModule != null && click.button() == 0) {
                toggleModule.switchState();
                return true;
            }

            ModuleStructure starModule = moduleComponent.getModuleForStarClick(mx, my, mlX, mlY, mlW, mlH);
            if (starModule != null && click.button() == 0) {
                moduleComponent.toggleFavorite(starModule);
                return true;
            }

            ModuleStructure module = moduleComponent.getModuleAtPosition(mx, my, mlX, mlY, mlW, mlH);
            if (module != null) {
                if (click.button() == 1) moduleComponent.selectModule(module);
                else if (click.button() == 0) moduleComponent.selectModule(module);
                return true;
            }

            float spX2 = mlX + mlW + ClickGuiTheme.PANEL_INSET;
            float spY2 = bgY + ClickGuiTheme.PANEL_TOP_OFFSET;
            float spW2 = ClickGuiTheme.SETTINGS_PANEL_WIDTH;
            float spH2 = BackgroundComponent.BG_HEIGHT - ClickGuiTheme.PANEL_TOP_OFFSET - ClickGuiTheme.PANEL_INSET - 2f;
            if (mx >= spX2 && mx <= spX2 + spW2 && my >= spY2 && my <= spY2 + spH2) {
                for (AbstractSettingComponent c : moduleComponent.getSettingComponents()) {
                    if (c.getSetting().isVisible() && c.mouseClicked(mx, my, click.button())) return true;
                }
            }
        }

        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (closing) return false;

        if (selectedCategory == ModuleCategory.AUTOBUY) {
            autoBuyRenderer.mouseReleased(click.x(), click.y(), click.button());
        }

//        if (selectedCategory == ModuleCategory.CONFIGS) {
//            configsRenderer.mouseReleased(click.x(), click.y(), click.button());
//        }

        for (AbstractSettingComponent c : moduleComponent.getSettingComponents()) {
            if (c.getSetting().isVisible() && c.mouseReleased(click.x(), click.y(), click.button())) {
                return true;
            }
        }

        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (closing) return false;

        if (isAnyBindListening()) {
            for (AbstractSettingComponent c : moduleComponent.getSettingComponents()) {
                if (c instanceof BindComponent bindComponent && bindComponent.isListening()) {
                    bindComponent.handleScrollBind(vertical);
                    return true;
                }
            }
        }

        if (moduleComponent.getBindingModule() != null) {
            return true;
        }

        int guiScale = mc.getWindow().calculateScaleFactor(mc.options.getGuiScale().getValue(), mc.forcesUnicodeFont());
        float scale = (float) FIXED_GUI_SCALE / guiScale;
        double mx = mouseX / scale, my = mouseY / scale;

        float[] bg = calculateBackground(scale);
        float bgX = Math.round(bg[0]), bgY = Math.round(bg[1]);

        if (background.isSearchActive()) {
            float panelX = bgX + ClickGuiTheme.CATEGORY_PANEL_WIDTH + ClickGuiTheme.PANEL_INSET * 2 + 4;
            float panelY = bgY + ClickGuiTheme.PANEL_TOP_OFFSET;
            float panelW = ClickGuiTheme.BG_WIDTH - ClickGuiTheme.CATEGORY_PANEL_WIDTH - ClickGuiTheme.PANEL_INSET * 3 - 4;
            float panelH = BackgroundComponent.BG_HEIGHT - ClickGuiTheme.PANEL_TOP_OFFSET - ClickGuiTheme.PANEL_INSET;

            if (mx >= panelX && mx <= panelX + panelW && my >= panelY && my <= panelY + panelH) {
                background.handleSearchScroll(vertical, panelH);
                return true;
            }
        }

        if (selectedCategory == ModuleCategory.AUTOBUY) {
            if (autoBuyRenderer.mouseScrolled(mx, my, vertical, bgX, bgY, selectedCategory)) {
                return true;
            }
        }

//        if (selectedCategory == ModuleCategory.CONFIGS) {
//            if (configsRenderer.mouseScrolled(mx, my, vertical, bgX, bgY, selectedCategory)) {
//                return true;
//            }
//        }

        float mlX = bgX + ClickGuiTheme.CATEGORY_PANEL_WIDTH + ClickGuiTheme.PANEL_INSET * 2 + 4;
        float mlY = bgY + ClickGuiTheme.PANEL_TOP_OFFSET;
        float mlW = ClickGuiTheme.MODULE_LIST_WIDTH;
        float mlH = BackgroundComponent.BG_HEIGHT - ClickGuiTheme.PANEL_TOP_OFFSET - ClickGuiTheme.PANEL_INSET - 2f;
        if (mx >= mlX && mx <= mlX + mlW && my >= mlY && my <= mlY + mlH) {
            moduleComponent.handleModuleScroll(vertical, mlH);
            return true;
        }

        float spX = mlX + mlW + ClickGuiTheme.PANEL_INSET;
        float spY = bgY + ClickGuiTheme.PANEL_TOP_OFFSET;
        float spW = ClickGuiTheme.SETTINGS_PANEL_WIDTH;
        float spH = BackgroundComponent.BG_HEIGHT - ClickGuiTheme.PANEL_TOP_OFFSET - ClickGuiTheme.PANEL_INSET - 2f;
        if (mx >= spX && mx <= spX + spW && my >= spY && my <= spY + spH) {
            moduleComponent.handleSettingScroll(vertical, spH);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            if (autoBuyRenderer.isEditing()) {
                return true;
            }
            if (configsRenderer.isEditing()) {
                return true;
            }
            if (background.isSearchActive()) {
                background.setSearchActive(false);
                return true;
            }
            close();
            return true;
        }

        if (closing) return false;

        if (selectedCategory == ModuleCategory.AUTOBUY) {
            if (autoBuyRenderer.keyPressed(input.key(), input.scancode(), input.modifiers())) {
                return true;
            }
        }

//        if (selectedCategory == ModuleCategory.CONFIGS) {
//            if (configsRenderer.keyPressed(input.key(), input.scancode(), input.modifiers())) {
//                return true;
//            }
//        }

        if (background.isSearchActive()) {
            if (background.handleSearchKey(input.key())) {
                return true;
            }
        }

        if (dragHandler.isResetNeeded(input.key(), input.modifiers())) {
            dragHandler.reset();
            return true;
        }

        ModuleStructure binding = moduleComponent.getBindingModule();
        if (binding != null) {
            binding.setKey(input.key() == GLFW.GLFW_KEY_DELETE ? GLFW.GLFW_KEY_UNKNOWN : input.key());
            moduleComponent.setBindingModule(null);
            return true;
        }

        for (AbstractSettingComponent c : moduleComponent.getSettingComponents()) {
            if (c.getSetting().isVisible() && c.keyPressed(input.key(), input.scancode(), input.modifiers())) return true;
        }

        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (closing) return false;

        if (selectedCategory == ModuleCategory.AUTOBUY) {
            if (autoBuyRenderer.charTyped((char) input.codepoint(), input.modifiers())) {
                return true;
            }
        }

//        if (selectedCategory == ModuleCategory.CONFIGS) {
//            if (configsRenderer.charTyped((char) input.codepoint(), input.modifiers())) {
//                return true;
//            }
//        }

        if (background.isSearchActive()) {
            if (background.handleSearchChar((char) input.codepoint())) {
                return true;
            }
        }

        for (AbstractSettingComponent c : moduleComponent.getSettingComponents()) {
            if (c.getSetting().isVisible() && c.charTyped((char) input.codepoint(), input.modifiers())) return true;
        }
        return super.charTyped(input);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private void renderTooltip(float bgX, float bgY, float mx, float my,
                               float mlX, float mlY, float mlW, float mlH, float alphaMultiplier) {
        ModuleStructure hovered = moduleComponent.getModuleAtPosition(mx, my, mlX, mlY, mlW, mlH);
        if (hovered == null) return;
        if (hovered.getDescription() == null || hovered.getDescription().isEmpty()) return;

        String name = hovered.getName();
        String desc = hovered.getDescription();

        float nameW = Fonts.BOLD.getWidth(name, 6f);
        float descW = Fonts.BOLD.getWidth(desc, 5f);
        float maxTextW = Math.max(nameW, descW);
        float tipW = maxTextW + 16;
        float tipH = 26f;

        float tipX = mx + 10;
        float tipY = my - tipH - 6;

        if (tipX + tipW > bgX + ClickGuiTheme.BG_WIDTH) {
            tipX = bgX + ClickGuiTheme.BG_WIDTH - tipW - 4;
        }
        if (tipY < bgY - 4) {
            tipY = my + 18;
        }
        if (tipX < bgX) {
            tipX = bgX + 4;
        }

        int bgAlpha = (int) (240 * alphaMultiplier);
        Render2D.rect(tipX, tipY, tipW, tipH, (bgAlpha << 24) | 0x0D0D18, 5);

        int borderAlpha = (int) (100 * alphaMultiplier);
        int accentRGB = ClickGuiTheme.ACCENT_ARGB & 0xFFFFFF;
        Render2D.outline(tipX, tipY, tipW, tipH, 0.4f, (borderAlpha << 24) | accentRGB, 5);

        int lineAlpha = (int) (60 * alphaMultiplier);
        Render2D.rect(tipX + 6, tipY + 13, tipW - 12, 0.5f, (lineAlpha << 24) | 0x444460, 0);

        int nameAlpha = (int) (255 * alphaMultiplier);
        Fonts.BOLD.draw(name, tipX + 6, tipY + 3, 6f, (nameAlpha << 24) | 0xFFFFFF);

        int descAlpha = (int) (180 * alphaMultiplier);
        Fonts.BOLD.draw(desc, tipX + 6, tipY + 15, 5f, (descAlpha << 24) | 0x9898B0);
    }

    private void renderStatsBar(float bgX, float bgY, float alphaMultiplier) {
        int bx = Math.round(bgX);
        int barY = Math.round(bgY + ClickGuiTheme.BG_HEIGHT + 2);
        float barW = ClickGuiTheme.BG_WIDTH;
        float barH = 12f;

        int barAlpha = (int) (240 * alphaMultiplier);
        Render2D.rect(bx, barY, barW, barH, (barAlpha << 24) | 0x0C0C14, 4);

        int enabledCount = 0;
        int totalCount = 0;
        try {
            var repo = Initialization.getInstance().getManager().getModuleRepository();
            if (repo != null) {
                for (var m : repo.modules()) {
                    totalCount++;
                    if (m.isState()) enabledCount++;
                }
            }
        } catch (Exception ignored) {}

        String server = "";
        if (mc.getNetworkHandler() != null && mc.getNetworkHandler().getServerInfo() != null) {
            server = mc.getNetworkHandler().getServerInfo().address;
            if (server.length() > 20) server = server.substring(0, 20) + "...";
        }

        String fps = mc.getCurrentFps() + " FPS";

        int textAlpha = (int) (200 * alphaMultiplier);
        int textCol = (textAlpha << 24) | 0xB4B4C8;

        Fonts.BOLD.draw(Lang.get().get("modules_count") + ": " + enabledCount + "/" + totalCount, bx + 6, barY + 2, 5f, textCol);

        if (!fps.isEmpty()) {
            Fonts.BOLD.draw(fps, bx + barW / 2 - Fonts.BOLD.getWidth(fps, 5f) / 2, barY + 2, 5f, textCol);
        }

        if (!server.isEmpty()) {
            float serverWidth = Fonts.BOLD.getWidth(server, 5f);
            Fonts.BOLD.draw(server, bx + barW - serverWidth - 6, barY + 2, 5f, textCol);
        }
    }

    private void startActualClose() {
        openAnimation.setDirection(Direction.BACKWARDS);
        openAnimation.reset();

        long handle = mc.getWindow().getHandle();
        double centerX = mc.getWindow().getWidth() / 2.0;
        double centerY = mc.getWindow().getHeight() / 2.0;

        GLFW.glfwSetInputMode(handle, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);
        GLFW.glfwSetCursorPos(handle, centerX, centerY);

        TextComponent.typing = false;
        moduleComponent.setBindingModule(null);
        background.setSearchActive(false);
        dragHandler.stopDrag();
    }

    @Override
    public void close() {
        if (!closing) {
            closing = true;

            if (selectedCategory == ModuleCategory.AUTOBUY) {
                waitingForSlide = true;
                slideTriggered = false;
            } else {
                waitingForSlide = false;
                startActualClose();
            }
        }
    }
}