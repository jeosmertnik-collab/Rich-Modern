package excel.screens.clickgui.cs.impl;

import excel.modules.module.setting.implement.ColorSetting;
import excel.screens.clickgui.cs.ColorWindow;
import excel.screens.clickgui.cs.Component;
import excel.screens.clickgui.cs.DisplayUtils;
import excel.screens.clickgui.cs.MathUtil;
import excel.util.render.font.Fonts;

public class ColorComponent extends Component {

    public static ColorWindow opened;
    public ColorSetting option;
    public ColorWindow setted;

    public ColorComponent(ColorSetting option) {
        this.option = option;
        setted = new ColorWindow(this);
        this.setting = option;
    }

    @Override
    public void drawComponent(int mouseX, int mouseY) {
        height = 15;
        Fonts.REGULAR.draw(option.getName(), x + 5, y + height / 2f - 1, 14, -1);
        float size = 8;
        DisplayUtils.drawRoundedRect(x + width - 10 - size / 2f, y + height / 2f - size / 2f, size, size, 3, option.getColor());
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        float size = 12;
        if (MathUtil.isInRegion(mouseX, mouseY, x + width - 10 - size / 2f, y + height / 2f - size / 2f, size, size)) {
            if (setted == opened) {
                opened = null;
                return;
            }
            opened = setted;
        }
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY, int mouseButton) {

    }

    @Override
    public void keyTyped(int keyCode, int scanCode, int modifiers) {

    }

    @Override
    public void charTyped(char codePoint, int modifiers) {

    }

    @Override
    public void onConfigUpdate() {
        super.onConfigUpdate();
        setted.onConfigUpdate();
    }
}
