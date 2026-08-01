package excel.screens.clickgui.cs;

import excel.modules.module.ModuleStructure;
import excel.modules.module.setting.Setting;
import excel.modules.module.setting.implement.BindSetting;
import excel.modules.module.setting.implement.BooleanSetting;
import excel.modules.module.setting.implement.ColorSetting;
import excel.modules.module.setting.implement.MultiSelectSetting;
import excel.modules.module.setting.implement.SelectSetting;
import excel.modules.module.setting.implement.SliderSettings;
import excel.modules.module.setting.implement.TextSetting;
import excel.screens.clickgui.ClickGui;
import excel.screens.clickgui.cs.impl.BindComponent;
import excel.screens.clickgui.cs.impl.BooleanComponent;
import excel.screens.clickgui.cs.impl.ColorComponent;
import excel.screens.clickgui.cs.impl.ListComponent;
import excel.screens.clickgui.cs.impl.ModeComponent;
import excel.screens.clickgui.cs.impl.SliderComponent;
import excel.screens.clickgui.cs.impl.TextComponent;
import excel.util.ColorUtil;
import excel.util.render.font.Fonts;
import excel.util.string.KeyHelper;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import static excel.screens.clickgui.cs.DisplayUtils.reAlphaInt;

public class ModuleComponent extends Component {

    public ModuleStructure function;

    public List<Component> components = new ArrayList<>();

    public ModuleComponent(ModuleStructure function) {
        this.function = function;
        for (Setting s : function.settings()) {
            if (s instanceof BooleanSetting bool) {
                components.add(new BooleanComponent(bool));
            } else if (s instanceof SliderSettings slider) {
                components.add(new SliderComponent(slider));
            } else if (s instanceof BindSetting bind) {
                components.add(new BindComponent(bind));
            } else if (s instanceof SelectSetting mode) {
                components.add(new ModeComponent(mode));
            } else if (s instanceof MultiSelectSetting mode) {
                components.add(new ListComponent(mode));
            } else if (s instanceof TextSetting string) {
                components.add(new TextComponent(string));
            } else if (s instanceof ColorSetting colorSetting) {
                components.add(new ColorComponent(colorSetting));
            }
        }
    }

    public float animationToggle;
    public static ModuleComponent binding;

    public static String keyToString(int key) {
        if (key == GLFW.GLFW_KEY_UNKNOWN || key == 0) return "NONE";
        return KeyHelper.getKeyName(key);
    }

    @Override
    public void drawComponent(int mouseX, int mouseY) {
        float totalHeight = 2;
        for (Component component : components) {
            if (component.setting != null && component.setting.isVisible()) {
                totalHeight += component.height;
            }
        }

        float off = 2f;

        for (Component c : components) {
            c.function = function;
            c.parent = parent;
        }

        animationToggle = MathUtil.lerp(animationToggle, function.isState() ? 1 : 0, 10);
        DisplayUtils.drawRoundedRectWithOutline(x, y, width, height + totalHeight, 8, new Color(10, 10, 10).getRGB(), new Color(80, 85, 95).getRGB(), 0.75f);

        int accent = ThemeStyle.getAccentRGB();
        if (function.isState()) {
            Fonts.BOLD.draw(GradientUtil.gradient(function.getName()), x + 7.5f, y + 9f, 16, accent);
        } else {
            Fonts.BOLD.draw(function.getName(), x + 7.5f, y + 9f, 16, -1);
        }

        String bind = keyToString(function.getKey());
        String fullBind = "Бинд: " + bind;
        float bindWidth = Fonts.REGULAR.getWidth(fullBind, 14);
        DisplayUtils.drawRoundedRect(x + 7f - 0.5f, y + 20 + off - 0.5f, 10 + bindWidth + 1, 15, 2, new Color(80, 85, 95).getRGB());
        DisplayUtils.drawRoundedRect(x + 7.5f, y + 20 + off, 10 + bindWidth, 14, 2, new Color(10, 10, 10).getRGB());
        Fonts.REGULAR.drawCentered(fullBind, x + 7.5f + (10 + bindWidth) / 2f, y + 27 + off - 1, 14, -1);

        int color = ColorUtil.lerp(animationToggle, new Color(10, 10, 10).getRGB(), new Color(accent).getRGB());
        float activeWidth = Fonts.REGULAR.getWidth("Активно", 14);
        DisplayUtils.drawShadow(x + 20 + bindWidth, y + 20 + off, activeWidth + 10, 14, 8, reAlphaInt(color, 50));
        DisplayUtils.drawRoundedRect(x + 20 + bindWidth - 0.5f, y + 20 + off - 0.5f, activeWidth + 10 + 1, 15, 2, new Color(80, 85, 95).getRGB());
        DisplayUtils.drawRoundedRect(x + 20 + bindWidth, y + 20 + off, activeWidth + 10, 14, 2, color);
        Fonts.REGULAR.draw("Активно", x + 25f + bindWidth, y + 27f - 1 + off, 14, function.isState() ? new Color(0, 0, 0).getRGB() : -1);

        float offsetY = 0;
        for (Component component : components) {
            if (component.setting != null && component.setting.isVisible()) {
                component.setPosition(x, y + height + offsetY, width, 20);
                component.drawComponent(mouseX, mouseY);
                offsetY += component.height;
            }
        }
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        String bind = keyToString(function.getKey());
        float bindWidth = Fonts.REGULAR.getWidth("Бинд: " + bind, 14);
        float activeWidth = Fonts.REGULAR.getWidth("Активно", 14);

        if (MathUtil.isInRegion(mouseX, mouseY, x + 20 + bindWidth, y + 22, activeWidth + 10, 16) && mouseButton <= 1) {
            function.switchState();
        }

        if (binding == this && mouseButton > 2) {
            function.setKey(-100 + mouseButton);
            binding = null;
        }

        if (MathUtil.isInRegion(mouseX, mouseY, x + 7.5f, y + 22, 10 + bindWidth, 16)) {
            if (mouseButton == 0) {
                ClickGui.typing = false;
                binding = this;
            }
        }
        components.forEach(component -> component.mouseClicked(mouseX, mouseY, mouseButton));
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY, int mouseButton) {
        components.forEach(component -> component.mouseReleased(mouseX, mouseY, mouseButton));
    }

    @Override
    public void keyTyped(int keyCode, int scanCode, int modifiers) {
        components.forEach(component -> component.keyTyped(keyCode, scanCode, modifiers));
    }

    @Override
    public void charTyped(char codePoint, int modifiers) {
        components.forEach(component -> component.charTyped(codePoint, modifiers));
    }
}
