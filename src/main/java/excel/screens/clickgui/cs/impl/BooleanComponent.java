package excel.screens.clickgui.cs.impl;

import excel.modules.module.setting.implement.BooleanSetting;
import excel.screens.clickgui.cs.Component;
import excel.screens.clickgui.cs.DisplayUtils;
import excel.screens.clickgui.cs.MathUtil;
import excel.screens.clickgui.cs.ThemeStyle;
import excel.util.ColorUtil;
import excel.util.render.font.Fonts;

import java.awt.Color;

public class BooleanComponent extends Component {

    public BooleanSetting option;

    public BooleanComponent(BooleanSetting option) {
        this.option = option;
        this.setting = option;
    }

    public float animationToggle;

    @Override
    public void drawComponent(int mouseX, int mouseY) {
        height = 15;
        float off = 0.5f;
        animationToggle = MathUtil.lerp(animationToggle, option.isValue() ? 1 : 0, 10);

        int accent = ThemeStyle.getAccentRGB();
        int color = ColorUtil.lerp(animationToggle, new Color(26, 29, 33).getRGB(), new Color(accent).getRGB());

        DisplayUtils.drawShadow(x + 5 + 120, y + 1 + off, 10, 10, 8, ColorUtil.applyAlpha(color, 50));
        DisplayUtils.drawRoundedRect(x + 5 + 120, y + 1 + off, 10, 10, 2f, color);

        Fonts.REGULAR.draw(option.getName(), x + 7.5f, y + 4.5f + off, 13, -1);
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (MathUtil.isInRegion(mouseX, mouseY, x, y, width, 15)) {
            option.setValue(!option.isValue());
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
}
