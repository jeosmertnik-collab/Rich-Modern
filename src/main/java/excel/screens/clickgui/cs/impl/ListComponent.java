package excel.screens.clickgui.cs.impl;

import excel.modules.module.setting.implement.MultiSelectSetting;
import excel.screens.clickgui.cs.Component;
import excel.screens.clickgui.cs.DisplayUtils;
import excel.screens.clickgui.cs.MathUtil;
import excel.screens.clickgui.cs.ThemeStyle;
import excel.util.render.font.Fonts;

import java.awt.Color;
import java.util.ArrayList;

public class ListComponent extends Component {

    public MultiSelectSetting option;

    public boolean opened;

    public ListComponent(MultiSelectSetting option) {
        this.option = option;
        this.setting = option;
    }

    @Override
    public void drawComponent(int mouseX, int mouseY) {
        float off = 4;
        float offset = 17 - 8;
        for (String s : option.getList()) {
            offset += 9;
        }
        if (!opened) offset = -1;
        Fonts.REGULAR.draw(option.getName(), x + 5, y + 3, 14, -1);
        off += Fonts.REGULAR.getHeight(14) / 2f + 2;
        height = 15 + (offset < 0 ? 0 : offset) + 7 + (opened ? 3 : 0);
        DisplayUtils.drawShadow(x + 5, y + off, width - 10, 14, 10, new Color(26, 29, 33, 50).getRGB());
        DisplayUtils.drawRoundedRect(x + 5 - 0.5f, y + off - 0.5f, width - 10 + 1, 15, 4, new Color(53, 55, 60).getRGB());
        DisplayUtils.drawRoundedRect(x + 5, y + off, width - 10, 14, 4, new Color(26, 29, 33).getRGB());
        if (offset >= 0) {
            DisplayUtils.drawShadow(x + 5, y + off + 17, width - 10, offset, 12, new Color(0, 0, 0, 100).getRGB());
            DisplayUtils.drawRoundedRect(x + 5 - 0.5f, y + off + 17 - 0.5f, width - 10 + 1, offset + 1, 4, new Color(53, 55, 60).getRGB());
            DisplayUtils.drawRoundedRect(x + 5, y + off + 17, width - 10, offset, 4, new Color(17, 18, 21).getRGB());
        }
        String names = String.join(", ", option.getSelected());
        Fonts.REGULAR.draw(names, x + 10, y + 20 - 4, 14, -1);
        if (opened) {
            int i = 1;
            int accent = ThemeStyle.getAccentRGB();
            for (String s : option.getList()) {
                boolean hovered = MathUtil.isInRegion(mouseX, mouseY, x, y + off + 20 + i, width, 8);
                Fonts.REGULAR.draw(s, x + 9 + (hovered ? 2 : 0), y + off + 23.5f + i, 14, option.isSelected(s) ? accent : new Color(163, 176, 188).getRGB());
                i += 9;
            }
        }
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        float off = 4 + Fonts.REGULAR.getHeight(14) / 2f + 2;

        if (MathUtil.isInRegion(mouseX, mouseY, x + 5, y + off, width - 10, 15)) {
            opened = !opened;
        }

        if (!opened) return;
        int i = 1;
        for (String s : option.getList()) {
            if (MathUtil.isInRegion(mouseX, mouseY, x, y + off + 20 + i, width, 8)) {
                ArrayList<String> selected = new ArrayList<>(option.getSelected());
                if (selected.contains(s)) {
                    selected.remove(s);
                } else {
                    selected.add(s);
                }
                option.setSelected(selected);
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
