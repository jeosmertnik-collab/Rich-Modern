package excel.screens.clickgui.cs.impl;

import excel.modules.module.setting.implement.BindSetting;
import excel.screens.clickgui.cs.Component;
import excel.screens.clickgui.cs.DisplayUtils;
import excel.screens.clickgui.cs.ModuleComponent;
import excel.util.render.font.Fonts;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;

public class BindComponent extends Component {

    public BindSetting option;
    boolean bind;

    public BindComponent(BindSetting option) {
        this.option = option;
        this.setting = option;
    }

    @Override
    public void drawComponent(int mouseX, int mouseY) {
        height = 12;

        String bindString = ModuleComponent.keyToString(option.getKey());
        if (bindString == null) {
            bindString = "";
        }

        float width = Fonts.REGULAR.getWidth(bindString, 14) + 4;
        int bg = bind ? new Color(17, 18, 21).brighter().brighter().getRGB() : new Color(17, 18, 21).brighter().getRGB();
        DisplayUtils.drawRoundedRect(x + 5, y + 2, width, 10, 2, bg);
        Fonts.REGULAR.drawCentered(bindString, x + 5 + (width / 2), y + 6, 14, -1);
        Fonts.REGULAR.draw(option.getName(), x + 5 + width + 3, y + 6, 14, -1);
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (bind && mouseButton > 1) {
            option.setKey(-100 + mouseButton);
            bind = false;
        }
        if (isHovered(mouseX, mouseY) && mouseButton == 0) {
            bind = true;
        }
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY, int mouseButton) {

    }

    @Override
    public void keyTyped(int keyCode, int scanCode, int modifiers) {
        if (bind) {
            if (keyCode == GLFW.GLFW_KEY_DELETE) {
                option.setKey(GLFW.GLFW_KEY_UNKNOWN);
                bind = false;
                return;
            }
            option.setKey(keyCode);
            bind = false;
        }
    }

    @Override
    public void charTyped(char codePoint, int modifiers) {

    }
}
