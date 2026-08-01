package excel.screens.clickgui.cs.impl;

import excel.modules.module.setting.implement.SliderSettings;
import excel.screens.clickgui.cs.Component;
import excel.screens.clickgui.cs.DisplayUtils;
import excel.screens.clickgui.cs.MathUtil;
import excel.screens.clickgui.cs.ThemeStyle;
import excel.util.render.font.Fonts;

import java.awt.Color;

public class SliderComponent extends Component {

    public SliderSettings option;

    public SliderComponent(SliderSettings option) {
        this.option = option;
        this.setting = option;
    }

    boolean drag;

    float anim;

    @Override
    public void drawComponent(int mouseX, int mouseY) {
        height = 18;
        float sliderWidth = ((option.getValue() - option.getMin()) / (option.getMax() - option.getMin())) * (width - 12);
        anim = MathUtil.lerp(anim, sliderWidth, 10);
        Fonts.REGULAR.draw(option.getName(), x + 6, y + 4, 13, -1);
        String valueStr = String.valueOf(option.isInteger() ? option.getInt() : (float) (Math.round(option.getValue() * 100f) / 100f));
        Fonts.REGULAR.draw(valueStr, x + width - Fonts.REGULAR.getWidth(valueStr, 14) - 6, y + 4, 14, -1);
        DisplayUtils.drawRoundedRect(x + 7, y + 13, width - 12, 4, 2, new Color(10, 10, 10).getRGB());
        int accent = ThemeStyle.getAccentRGB();
        DisplayUtils.drawShadow(x + 7, y + 15, anim, 1, 8, accent);
        float rightRadius = (option.getMax() == option.getValue()) ? 2 : 0;
        DisplayUtils.drawRoundedRect(x + 6, y + 14, anim, 1, 2, 2, rightRadius, rightRadius, accent);
        DisplayUtils.drawCircle(x + 6 + anim, y + 14.75f, 3.5f, accent);
        if (drag) {
            float increment = option.isInteger() ? 1f : 0.01f;
            float draggingValue = MathUtil.clamp(MathUtil.round((mouseX - x - 4) / (width - 12)
                    * (option.getMax() - option.getMin()) + option.getMin(), increment), option.getMin(), option.getMax());
            option.setValue(draggingValue);
        }
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (isHovered(mouseX, mouseY)) {
            drag = true;
        }
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY, int mouseButton) {
        drag = false;
    }

    @Override
    public void keyTyped(int keyCode, int scanCode, int modifiers) {

    }

    @Override
    public void charTyped(char codePoint, int modifiers) {

    }
}
