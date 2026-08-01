package excel.screens.clickgui.cs.impl;

import excel.modules.module.setting.implement.SelectSetting;
import excel.screens.clickgui.cs.Component;
import excel.screens.clickgui.cs.DisplayUtils;
import excel.screens.clickgui.cs.MathUtil;
import excel.screens.clickgui.cs.ThemeStyle;
import excel.util.render.font.Fonts;

import java.awt.Color;
import java.util.HashMap;

public class ModeComponent extends Component {

    public SelectSetting option;

    public boolean opened;
    public HashMap<String, Float> animation = new HashMap<>();

    public ModeComponent(SelectSetting option) {
        this.option = option;
        for (String s : option.getList()) {
            animation.put(s, 0f);
        }
        this.setting = option;
    }

    @Override
    public void drawComponent(int mouseX, int mouseY) {
        float off = 4;
        float offset = 17 - 8;
        for (String s : option.getList()) {
            offset += 9;
        }
        if (!opened) offset = 0;
        Fonts.REGULAR.draw(option.getName(), x + 5, y + 3, 14, -1);

        off += Fonts.REGULAR.getHeight(14) / 2f + 2;
        height = 15 + offset + 7 + (opened ? 3 : 0);
        DisplayUtils.drawShadow(x + 5, y + off, width - 10, 14, 10, new Color(26, 29, 33, 50).getRGB());
        DisplayUtils.drawRoundedRect(x + 5 - 0.5f, y + off - 0.5f, width - 10 + 1, 15, 4, new Color(53, 55, 60).getRGB());
        DisplayUtils.drawRoundedRect(x + 5, y + off, width - 10, 14, 4, new Color(26, 29, 33).getRGB());
        if (offset > 0) {
            DisplayUtils.drawShadow(x + 5, y + off + 17, width - 10, offset, 12, new Color(0, 0, 0, 100).getRGB());
            DisplayUtils.drawRoundedRect(x + 5, y + off + 17, width - 10, offset, 4, new Color(17, 18, 21).getRGB());
        }
        Fonts.REGULAR.draw(option.getSelected(), x + 10, y + 20 - 4, 14, -1);
        if (opened) {
            int i = 1;
            int accent = ThemeStyle.getAccentRGB();
            for (String s : option.getList()) {
                boolean hovered = MathUtil.isInRegion(mouseX, mouseY, x, y + off + 20 + i, width, 8);
                animation.put(s, MathUtil.lerp(animation.get(s), hovered ? 2 : 0, 10));
                Fonts.REGULAR.draw(s, x + 9 + animation.get(s), y + off + 23.5f + i, 14, option.getSelected().equals(s) ? accent : new Color(163, 176, 188).getRGB());
                i += 9;
            }
        }
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        float off = 3;
        off += Fonts.REGULAR.getHeight(14) / 2f + 2;
        if (MathUtil.isInRegion(mouseX, mouseY, x + 5, y + off, width - 10, 15)) {
            opened = !opened;
        }

        if (!opened) return;
        int i = 1;
        for (String s : option.getList()) {
            if (MathUtil.isInRegion(mouseX, mouseY, x, y + off + 20 + i, width, 8)) {
                option.setSelected(s);
            }
            i += 9;
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
