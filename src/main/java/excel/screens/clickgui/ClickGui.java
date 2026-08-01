package excel.screens.clickgui;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import excel.IMinecraft;
import excel.Initialization;
import excel.modules.module.ModuleStructure;
import excel.modules.module.category.ModuleCategory;
import excel.screens.clickgui.cs.Component;
import excel.screens.clickgui.cs.DisplayUtils;
import excel.screens.clickgui.cs.MathUtil;
import excel.screens.clickgui.cs.ModuleComponent;
import excel.screens.clickgui.cs.ThemeStyle;
import excel.screens.clickgui.cs.ThemeStyle.Style;
import excel.screens.clickgui.cs.impl.ColorComponent;
import excel.screens.clickgui.cs.impl.ThemeComponent;
import excel.util.render.Render2D;
import excel.util.render.font.Fonts;
import excel.util.render.shader.Scissor;

import java.awt.Color;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class ClickGui extends Screen implements IMinecraft {
    public static ClickGui INSTANCE = new ClickGui();
    private static final int FIXED_GUI_SCALE = 2;
    private static final float PANEL_H = 385f;

    private final List<ModuleComponent> objects = new ArrayList<>();
    private ModuleCategory current = ModuleCategory.COMBAT;

    public static boolean typing;
    private String searchText = "";
    private boolean themerender;

    private boolean closing = false;
    private float openAnimation = 0f;

    private float scroll, scrollT, animateScroll, animateScrollT;

    private float xPanel, yPanel;

    private int lastMouseX;
    private int lastMouseY;
    private float lastDelta;

    public ClickGui() {
        super(Text.of("CS GUI"));
    }

    public boolean isClosing() {
        return closing;
    }

    @Override
    protected void init() {
        super.init();
        closing = false;
        typing = false;
        ColorComponent.opened = null;
        ModuleComponent.binding = null;
        scroll = scrollT = animateScroll = animateScrollT = 0;
        openAnimation = 0f;
        buildObjects();

        long handle = mc.getWindow().getHandle();
        GLFW.glfwSetCursorPos(handle, mc.getWindow().getWidth() / 2.0, mc.getWindow().getHeight() / 2.0);
    }

    private void buildObjects() {
        objects.clear();
        try {
            var repo = Initialization.getInstance().getManager().getModuleRepository();
            if (repo != null) {
                for (ModuleStructure m : repo.modules()) {
                    objects.add(new ModuleComponent(m));
                }
            }
        } catch (Exception ignored) {
        }
    }

    public void openGui() {
        if (mc.currentScreen == null) {
            closing = false;
            mc.setScreen(this);
        }
    }

    private List<ModuleComponent> visibleModules() {
        List<ModuleComponent> out = new ArrayList<>();
        for (ModuleComponent m : objects) {
            if (!searchText.isEmpty()) {
                if (m.function.getName().toLowerCase().contains(searchText.toLowerCase())) out.add(m);
            } else {
                if (m.function.getCategory() == current) out.add(m);
            }
        }
        return out;
    }

    private float[] layout() {
        int vw = mc.getWindow().getWidth() / FIXED_GUI_SCALE;
        int vh = mc.getWindow().getHeight() / FIXED_GUI_SCALE;
        float width = (themerender ? 1150 : 850) / FIXED_GUI_SCALE + 20 + 40;
        float height = PANEL_H;
        float x = vw / 2f - width / 2f;
        float y = vh / 2f - height / 2f;
        return new float[]{x, y, width, height};
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        lastDelta = delta;
    }

    public void renderOverlay(DrawContext context, RenderTickCounter tickCounter) {
        if (mc.getWindow() == null) return;

        float anim = MathUtil.lerp(openAnimation, closing ? 0 : 1, 10);
        openAnimation = anim;

        if (closing && anim < 0.02f) {
            closing = false;
            typing = false;
            ModuleComponent.binding = null;
            ColorComponent.opened = null;
            mc.currentScreen = null;
            return;
        }

        int guiScale = mc.getWindow().calculateScaleFactor(mc.options.getGuiScale().getValue(), mc.forcesUnicodeFont());
        float scale = (float) FIXED_GUI_SCALE / guiScale;
        float mx = lastMouseX / scale, my = lastMouseY / scale;

        int dimAlpha = (int) (120 * anim);
        if (dimAlpha > 0) {
            Render2D.rect(0, 0, 5000, 5000, new Color(0, 0, 0, dimAlpha).getRGB(), 0);
        }

        context.getMatrices().pushMatrix();
        context.getMatrices().scale(scale, scale);

        float[] l = layout();
        float x = l[0], y = l[1], width = l[2], height = l[3];
        if (closing) {
            y += (1 - anim) * 30f;
        } else {
            y += (1 - anim) * -15f;
        }
        xPanel = x;
        yPanel = y;

        int mxI = (int) mx, myI = (int) my;

        renderBackground(x, y, width, height, mxI, myI);
        if (themerender) renderThemes(x, y, width, height, mxI, myI);
        renderCategories(x, y, width, height, mxI, myI);
        renderComponents(x, y, width, height, mxI, myI);
        renderSearchBar(x, y, width, height, mxI, myI);
        if (ColorComponent.opened != null) {
            ColorComponent.opened.draw(mxI, myI);
        }

        Scissor.reset();
        context.getMatrices().popMatrix();
    }

    private void renderBackground(float x, float y, float width, float height, int mouseX, int mouseY) {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");
        String timeString = sdf.format(new Date());

        DisplayUtils.drawShadow(x, y, width, height, 20, new Color(22, 24, 28).getRGB());
        DisplayUtils.drawRoundedRectWithOutline(x, y, width, height, 20, new Color(10, 10, 10).getRGB(), new Color(80, 85, 95).getRGB(), 0.75f);
        if (themerender) {
            DisplayUtils.drawRectVerticalW(x + 462, y, 0.75f, height, new Color(53, 55, 60).getRGB(), new Color(53, 55, 60).getRGB());
        }

        int accent = ThemeStyle.getAccentRGB();
        DisplayUtils.drawCircle(x + 32, y + 30, 5, new Color(80, 85, 95).getRGB());
        DisplayUtils.drawCircle(x + 32, y + 30, 3, accent);
        Fonts.BOLD.draw("Excel", x + 46, y + 22.5f, 19, -1);
        DisplayUtils.drawRoundedRectWithOutline(x + 47.8f, y + 30.5f, 8, 8, 3, new Color(10, 10, 10).getRGB(), new Color(80, 85, 95).getRGB(), 0.75f);
        Fonts.BOLD.draw("R", x + 47.5f, y + 33.5f, 12, new Color(100, 100, 100).getRGB());
        Fonts.BOLD.draw("Сменить тему", x + 49.5f, y + 33.5f, 12, new Color(100, 100, 100).getRGB());
        Fonts.BOLD.draw(timeString, x + 47.5f, y + 45.5f, 12, new Color(100, 100, 100).getRGB());

        float avatarSize = (height - 5) / 13;
        DisplayUtils.drawCircle(x + 10 + avatarSize / 2f, y + 286 + avatarSize / 2f, avatarSize / 2f, accent);
        DisplayUtils.drawCircle(x + 10 + avatarSize / 2f, y + 286 + avatarSize / 2f, avatarSize / 2f - 3, new Color(10, 10, 10).getRGB());
        DisplayUtils.drawCircle(x + 10 + avatarSize / 2f, y + 286 + avatarSize / 2f, avatarSize / 2f - 5, accent);
        Fonts.BOLD.draw("Пользователь", x + 42, y + 287.5f, 16, -1);
        Fonts.BOLD.draw("Role: User", x + 42, y + 297.5f, 12, new Color(100, 100, 100).getRGB());
        Fonts.BOLD.draw("Version: Excel 1.0.04", x + 42, y + 305, 12, new Color(100, 100, 100).getRGB());
    }

    private void renderThemes(float x, float y, float width, float height, int mouseX, int mouseY) {
        Style style = ThemeStyle.MANAGER.getCurrentStyle();
        List<Style> styles = ThemeStyle.MANAGER.getStyleList();

        Fonts.BOLD.drawCentered("Theme", x + 510, y + 30, 38, style.getFirstColor().getRGB());
        Fonts.BOLD.draw("Этот раздел добавлен для игры с", x + 472, y + 55, 15, new Color(100, 100, 100).getRGB());
        Fonts.BOLD.draw("цветами клиента. Цвет - новое", x + 472, y + 65, 15, new Color(100, 100, 100).getRGB());
        Fonts.BOLD.draw("настроение", x + 472, y + 75, 15, new Color(100, 100, 100).getRGB());

        DisplayUtils.drawRoundedRectWithOutline(x + 472, y + 85, 153, height - 100, 10, new Color(13, 10, 10).getRGB(), new Color(80, 85, 95).getRGB(), 0.75f);
        animateScrollT = MathUtil.lerp(animateScrollT, scrollT, 10);

        float startX = x + 487;
        float startY = y + 95 + animateScrollT;
        float boxWidth = 55;
        float boxHeight = 40;
        float padding = 10;
        int columns = 2;

        for (int i = 0; i < styles.size(); i++) {
            int row = i / columns;
            int col = i % columns;
            float boxX = startX + col * (boxWidth + padding);
            float boxY = startY + row * (boxHeight + padding);

            ThemeComponent themeComponent = new ThemeComponent(styles.get(i));
            themeComponent.setPosition(boxX, boxY, boxWidth, boxHeight);
            themeComponent.drawComponent(mouseX, mouseY);
        }

        int size1 = styles.size() * 30;
        if (size1 < boxHeight) {
            scrollT = 0;
        } else {
            scrollT = MathUtil.clamp(scrollT, -(size1 - height), 0);
        }
    }

    private void renderCategories(float x, float y, float width, float height, int mouseX, int mouseY) {
        float heightCategory = 30f;
        int accent = ThemeStyle.getAccentRGB();
        for (ModuleCategory t : ModuleCategory.values()) {
            if (t == current) {
                DisplayUtils.drawRoundedRectWithOutline(x - 14, y + 75.5f + t.ordinal() * heightCategory, 131, 24, 8, new Color(10, 10, 10).getRGB(), new Color(80, 85, 95).getRGB(), 0.75f);
                DisplayUtils.drawRoundedRect(x - 18.5f, y + 80.25f + t.ordinal() * heightCategory, 23.5f, 14.5f, 5, accent);
            }
            Fonts.BOLD.draw(t.getReadableName(), x + 28, y + 85.5f + t.ordinal() * heightCategory, 20, t == current ? accent : new Color(63, 75, 78).getRGB());
        }
        DisplayUtils.drawRectHorizontalW(x + 100, y + 32, 5, height - 32, new Color(12, 13, 15, 50).getRGB(), new Color(12, 13, 15, 0).getRGB());
    }

    private void renderComponents(float x, float y, float width, float height, int mouseX, int mouseY) {
        Scissor.enable(x, y + 32, width, height - 64, FIXED_GUI_SCALE);
        drawComponents(mouseX, mouseY);
        Scissor.reset();
    }

    private void drawComponents(int mouseX, int mouseY) {
        List<ModuleComponent> moduleList = visibleModules();
        List<ModuleComponent> first = new ArrayList<>();
        List<ModuleComponent> second = new ArrayList<>();
        for (ModuleComponent m : moduleList) {
            if (objects.indexOf(m) % 2 == 0) {
                first.add(m);
            } else {
                second.add(m);
            }
        }

        animateScroll = MathUtil.lerp(animateScroll, scroll, 15);

        float offset1 = yPanel + 14 + animateScroll;
        float size1 = 0;
        for (ModuleComponent component : first) {
            component.parent = this;
            component.setPosition(xPanel + 152, offset1, 142.5f, 37);
            component.drawComponent(mouseX, mouseY);
            if (!component.components.isEmpty()) {
                for (Component settingComp : component.components) {
                    if (settingComp.setting != null && settingComp.setting.isVisible()) {
                        offset1 += settingComp.height;
                        size1 += settingComp.height;
                    }
                }
            }
            offset1 += component.height + 8;
            size1 += component.height + 8;
        }

        float offset2 = yPanel + 14 + animateScroll;
        float size2 = 0;
        for (ModuleComponent component : second) {
            component.parent = this;
            component.setPosition(xPanel + 309, offset2, 142.5f, 37);
            component.drawComponent(mouseX, mouseY);
            if (!component.components.isEmpty()) {
                for (Component settingComp : component.components) {
                    if (settingComp.setting != null && settingComp.setting.isVisible()) {
                        offset2 += settingComp.height;
                        size2 += settingComp.height;
                    }
                }
            }
            offset2 += component.height + 8;
            size2 += component.height + 8;
        }

        float max = Math.max(size1, size2);
        float height = 650 / FIXED_GUI_SCALE + 20;
        if (max < height) {
            scroll = 0;
        } else {
            scroll = MathUtil.clamp(scroll, -(max - height + 50), 0);
        }
    }

    private void renderSearchBar(float x, float y, float width, float height, int mouseX, int mouseY) {
        DisplayUtils.drawShadow(x + 20, y + 47, 100, 18, 12, new Color(17, 18, 21).getRGB());
        DisplayUtils.drawRoundedRectWithOutline(x + 20, y + 47, 100, 18, 8, new Color(17, 18, 21).getRGB(), new Color(80, 85, 95).getRGB(), 0.75f);

        Scissor.enable(x + 20, y + 47, 100, 18, FIXED_GUI_SCALE);
        String display;
        boolean cursor = typing && System.currentTimeMillis() % 1000 > 500;
        if (searchText.isEmpty()) {
            display = typing ? (cursor ? "_" : "") : "Поиск...";
        } else {
            display = cursor ? searchText + "_" : searchText;
        }
        Fonts.BOLD.draw(display, x + 30, y + 54, 12, new Color(53, 55, 60).getRGB());
        Scissor.reset();
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (closing) return false;

        int guiScale = mc.getWindow().calculateScaleFactor(mc.options.getGuiScale().getValue(), mc.forcesUnicodeFont());
        float scale = (float) FIXED_GUI_SCALE / guiScale;
        float mx = (float) (click.x() / scale), my = (float) (click.y() / scale);
        int button = click.button();

        if (ColorComponent.opened != null) {
            if (!ColorComponent.opened.click((int) mx, (int) my)) {
                ColorComponent.opened = null;
            }
            return true;
        }

        float[] l = layout();
        float x = l[0], y = l[1], width = l[2], height = l[3];

        float heightCategory = 30f;
        for (ModuleCategory t : ModuleCategory.values()) {
            if (MathUtil.isInRegion(mx, my, x, y + 73.5f + t.ordinal() * heightCategory, 117.5f, heightCategory)) {
                if (current == t) continue;
                current = t;
                scroll = 0;
                searchText = "";
                ColorComponent.opened = null;
                typing = false;
                break;
            }
        }

        if (MathUtil.isInRegion(mx, my, x, y + 32, width, height - 64)) {
            for (ModuleComponent m : visibleModules()) {
                m.mouseClicked((int) mx, (int) my, button);
            }
        }
        if (ModuleComponent.binding != null) {
            if (button > 2) {
                ModuleComponent.binding.function.setKey(-100 + button);
            }
            ModuleComponent.binding = null;
        }

        if (MathUtil.isInRegion(mx, my, x + 20, y + 47, 100, 18)) {
            typing = !typing;
        } else {
            typing = false;
        }

        return true;
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (closing) return false;

        int guiScale = mc.getWindow().calculateScaleFactor(mc.options.getGuiScale().getValue(), mc.forcesUnicodeFont());
        float scale = (float) FIXED_GUI_SCALE / guiScale;
        float mx = (float) (click.x() / scale), my = (float) (click.y() / scale);
        int button = click.button();

        for (ModuleComponent m : visibleModules()) {
            m.mouseReleased((int) mx, (int) my, button);
        }
        if (ColorComponent.opened != null) {
            ColorComponent.opened.unclick((int) mx, (int) my);
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (closing) return false;

        int guiScale = mc.getWindow().calculateScaleFactor(mc.options.getGuiScale().getValue(), mc.forcesUnicodeFont());
        float scale = (float) FIXED_GUI_SCALE / guiScale;
        float mx = (float) (mouseX / scale), my = (float) (mouseY / scale);

        float[] l = layout();
        float x = l[0], y = l[1], width = l[2], height = l[3];
        float delta = (float) vertical;

        if (MathUtil.isInRegion(mx, my, x + 117.5f, y + 32, width - 117.5f - 18 + (themerender ? -95 : 0), height - 32)) {
            scroll += delta * 30;
        }
        if (themerender) {
            if (MathUtil.isInRegion(mx, my, x + 381, y + 85, 158, height - 100)) {
                scrollT += delta * 15;
            }
        }

        ColorComponent.opened = null;
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }

        if (closing) return false;

        if (input.key() == GLFW.GLFW_KEY_R) {
            themerender = !themerender;
            return true;
        }

        if (typing) {
            if ((input.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0 && input.key() == GLFW.GLFW_KEY_V) {
                String clip = mc.keyboard.getClipboard();
                if (clip != null) searchText += clip;
                return true;
            }
            if (input.key() == GLFW.GLFW_KEY_BACKSPACE) {
                if (!searchText.isEmpty()) {
                    searchText = searchText.substring(0, searchText.length() - 1);
                }
                return true;
            }
            if (input.key() == GLFW.GLFW_KEY_DELETE) {
                searchText = "";
                return true;
            }
            if (input.key() == GLFW.GLFW_KEY_ENTER) {
                typing = false;
                return true;
            }
            return true;
        }

        if (ModuleComponent.binding != null) {
            if (input.key() == GLFW.GLFW_KEY_DELETE) {
                ModuleComponent.binding.function.setKey(GLFW.GLFW_KEY_UNKNOWN);
            } else {
                ModuleComponent.binding.function.setKey(input.key());
            }
            ModuleComponent.binding = null;
            return true;
        }

        for (ModuleComponent m : visibleModules()) {
            m.keyTyped(input.key(), input.scancode(), input.modifiers());
        }

        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (closing) return false;

        if (typing) {
            searchText += (char) input.codepoint();
            return true;
        }

        for (ModuleComponent m : visibleModules()) {
            m.charTyped((char) input.codepoint(), input.modifiers());
        }
        return super.charTyped(input);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        if (!closing) {
            closing = true;
            typing = false;
            ColorComponent.opened = null;
            ModuleComponent.binding = null;

            long handle = mc.getWindow().getHandle();
            double centerX = mc.getWindow().getWidth() / 2.0;
            double centerY = mc.getWindow().getHeight() / 2.0;
            GLFW.glfwSetInputMode(handle, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);
            GLFW.glfwSetCursorPos(handle, centerX, centerY);
        }
    }
}
