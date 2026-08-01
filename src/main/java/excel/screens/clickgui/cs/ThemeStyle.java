package excel.screens.clickgui.cs;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class ThemeStyle {

    public static final StyleManager MANAGER = new StyleManager();

    public static class Style {
        private final String name;
        private final int accent;

        public Style(String name, int accent) {
            this.name = name;
            this.accent = accent;
        }

        public String getStyleName() {
            return name;
        }

        public Color getFirstColor() {
            return new Color(accent);
        }

        public int getColor(int degree) {
            float[] hsb = Color.RGBtoHSB((accent >> 16) & 0xFF, (accent >> 8) & 0xFF, accent & 0xFF, null);
            float hue = (hsb[0] + degree / 360f) % 1f;
            return Color.HSBtoRGB(hue, hsb[1], hsb[2]) | 0xFF000000;
        }
    }

    public static class StyleManager {
        private final List<Style> styles = new ArrayList<>();
        private int current = 0;

        public StyleManager() {
            styles.add(new Style("Blue", 0xFF4AA6DA));
            styles.add(new Style("Purple", 0xFFAA5AFF));
            styles.add(new Style("Green", 0xFF4ADAA5));
            styles.add(new Style("Red", 0xFFFF5050));
            styles.add(new Style("Orange", 0xFFFF963C));
            styles.add(new Style("Pink", 0xFFFF5AA0));
        }

        public Style getCurrentStyle() {
            return styles.get(current);
        }

        public List<Style> getStyleList() {
            return styles;
        }

        public void setCurrentStyle(Style style) {
            int i = styles.indexOf(style);
            if (i >= 0) current = i;
        }
    }

    public static Style getCurrentStyle() {
        return MANAGER.getCurrentStyle();
    }

    public static int getAccentRGB() {
        return getCurrentStyle().getFirstColor().getRGB();
    }
}
