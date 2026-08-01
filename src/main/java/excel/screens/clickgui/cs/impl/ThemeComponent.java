package excel.screens.clickgui.cs.impl;

import excel.screens.clickgui.cs.Component;
import excel.screens.clickgui.cs.DisplayUtils;
import excel.screens.clickgui.cs.ThemeStyle;
import excel.screens.clickgui.cs.ThemeStyle.Style;
import excel.util.render.font.Fonts;

import java.awt.Color;

public class ThemeComponent extends Component {

    public Style style;
    public boolean opened;

    public ThemeComponent(Style style) {
        this.style = style;
    }

    @Override
    public void drawComponent(int mouseX, int mouseY) {
        int color1 = style.getColor(0);
        int color2 = style.getColor(90);
        int color3 = style.getColor(180);
        int color4 = style.getColor(270);

        String input = style.getStyleName();
        String extracted = "";

        if (input.contains(" ") || input.contains("-")) {
            int spaceIndex = input.indexOf(" ");
            int dashIndex = input.indexOf("-");

            int splitIndex = (spaceIndex >= 0 && dashIndex >= 0)
                    ? Math.min(spaceIndex, dashIndex)
                    : (spaceIndex >= 0 ? spaceIndex : dashIndex);

            extracted = input.substring(splitIndex + 1).trim();
            input = input.substring(0, splitIndex).trim();
        }

        int borderColor = ThemeStyle.MANAGER.getCurrentStyle() == style
                ? new Color(32, 36, 42).brighter().getRGB()
                : new Color(80, 85, 95).getRGB();

        DisplayUtils.drawRoundedRect(x + 2, y - 1, width - 5 + 2, height + 2, 6, borderColor);
        DisplayUtils.drawRoundedRect(x + 3, y, width - 5, height, 6, new Color(10, 10, 10).getRGB());

        String label = Fonts.REGULAR.getWidth(style.getStyleName(), 14) > 48 ? input : style.getStyleName();
        Fonts.REGULAR.draw(label, x + 6, y + 5, 14, -1);
        if (Fonts.REGULAR.getWidth(style.getStyleName(), 14) > 48) {
            Fonts.REGULAR.draw(extracted, x + 6, y + 12, 14, -1);
        }

        DisplayUtils.drawGradientRound(x + 3, y + height * 0.45f, width - 5, height * 0.55f, 7, color1, color2, color3, color4);
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton == 0 && isHovered(mouseX, mouseY)) {
            ThemeStyle.MANAGER.setCurrentStyle(style);
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
