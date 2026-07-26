package rich.screens.clickgui.impl.background.render;

import net.minecraft.client.gui.DrawContext;
import rich.modules.impl.render.Hud;
import rich.util.render.Render2D;
import rich.util.render.font.Fonts;

import java.awt.*;

public class BackgroundRenderer {

    private boolean changelogOpen = false;
    private float changelogAlpha = 0f;
    private float changelogScroll = 0f;

    public void toggleChangelog() {
        changelogOpen = !changelogOpen;
        if (!changelogOpen) {
            changelogScroll = 0f;
        }
    }

    public void closeChangelog() {
        changelogOpen = false;
        changelogScroll = 0f;
    }

    public void scrollChangelog(float amount) {
        changelogScroll += amount;
        float maxScroll = 0;
        changelogScroll = Math.max(maxScroll, Math.min(0, changelogScroll));
    }

    public boolean isChangelogHovered(float mouseX, float mouseY, float bgX, float bgY) {
        return mouseX >= bgX + 12.5f && mouseX <= bgX + 82.5f
                && mouseY >= bgY + 218f && mouseY <= bgY + 236f;
    }

    public boolean isChangelogOpen() { return changelogOpen; }

    public boolean isCloseButtonHovered(float mouseX, float mouseY, float bgX, float bgY) {
        float popupX = bgX + 100;
        float popupY = bgY + 50;
        float btnX = popupX + 185;
        float btnY = popupY + 5;
        return mouseX >= btnX && mouseX <= btnX + 10 && mouseY >= btnY && mouseY <= btnY + 10;
    }

    public boolean isPopupHovered(float mouseX, float mouseY, float bgX, float bgY) {
        if (changelogAlpha < 0.01f) return false;
        float popupX = bgX + 100;
        float popupY = bgY + 50;
        return mouseX >= popupX && mouseX <= popupX + 200
                && mouseY >= popupY && mouseY <= popupY + 150;
    }

    private ThemeColors getThemeColors() {
        Hud hud = Hud.getInstance();
        String preset = hud != null ? hud.getThemePreset() : "Тёмная";
        Color bg, panel, outline;
        boolean blur;

        switch (preset) {
            case "Светлая" -> {
                bg = new Color(240, 240, 245, 255);
                panel = new Color(220, 220, 225, 60);
                outline = new Color(180, 180, 190, 255);
                blur = false;
            }
            case "Синяя" -> {
                bg = new Color(15, 20, 35, 255);
                panel = new Color(25, 35, 55, 60);
                outline = new Color(60, 90, 140, 255);
                blur = true;
            }
            case "Кастомная" -> {
                int bgC = hud.customBgColor.getColor();
                int panelC = hud.customPanelColor.getColor();
                int outlineC = hud.customOutlineColor.getColor();
                bg = new Color((bgC >> 16) & 0xFF, (bgC >> 8) & 0xFF, bgC & 0xFF, 255);
                panel = new Color((panelC >> 16) & 0xFF, (panelC >> 8) & 0xFF, panelC & 0xFF, 60);
                outline = new Color((outlineC >> 16) & 0xFF, (outlineC >> 8) & 0xFF, outlineC & 0xFF, 255);
                blur = hud.customBlur.isValue();
            }
            default -> {
                bg = new Color(18, 18, 28, 255);
                panel = new Color(128, 128, 128, 25);
                outline = new Color(55, 55, 55, 255);
                blur = true;
            }
        }
        return new ThemeColors(bg, panel, outline, blur);
    }

    private record ThemeColors(Color bg, Color panel, Color outline, boolean blur) {}

    private int getAccentR() {
        int rgb = Hud.getInstance() != null ? Hud.getInstance().getAccentRGB() : 0x6496FF;
        return (rgb >> 16) & 0xFF;
    }

    private int getAccentG() {
        int rgb = Hud.getInstance() != null ? Hud.getInstance().getAccentRGB() : 0x6496FF;
        return (rgb >> 8) & 0xFF;
    }

    private int getAccentB() {
        int rgb = Hud.getInstance() != null ? Hud.getInstance().getAccentRGB() : 0x6496FF;
        return rgb & 0xFF;
    }

    private int accentColor(int alpha) {
        return new Color(getAccentR(), getAccentG(), getAccentB(), alpha).getRGB();
    }

    public void render(DrawContext context, float bgX, float bgY, float alphaMultiplier) {
        ThemeColors theme = getThemeColors();
        int baseAlpha = (int) (theme.bg.getAlpha() * alphaMultiplier);

        int[] gradientColors = {
                new Color(theme.bg.getRed(), theme.bg.getGreen(), theme.bg.getBlue(), baseAlpha).getRGB(),
                new Color(0, 0, 0, baseAlpha).getRGB(),
                new Color(theme.bg.getRed(), theme.bg.getGreen(), theme.bg.getBlue(), baseAlpha).getRGB(),
                new Color(0, 0, 0, baseAlpha).getRGB(),
                new Color(theme.bg.getRed(), theme.bg.getGreen(), theme.bg.getBlue(), baseAlpha).getRGB()
        };

        Render2D.gradientRect(bgX, bgY, 400, 250, gradientColors, 15);
    }

    public void renderCategoryPanel(float bgX, float bgY, float bgHeight, float alphaMultiplier) {
        ThemeColors theme = getThemeColors();
        int panelAlpha = (int) (theme.panel.getAlpha() * alphaMultiplier);
        int outlineAlpha = (int) (theme.outline.getAlpha() * alphaMultiplier);
        int blurAlpha = (int) (155 * alphaMultiplier);

        int panelR = theme.panel.getRed();
        int panelG = theme.panel.getGreen();
        int panelB = theme.panel.getBlue();
        int outlineR = theme.outline.getRed();
        int outlineG = theme.outline.getGreen();
        int outlineB = theme.outline.getBlue();

        Render2D.rect(bgX + 7.5f, bgY + 7.5f, 80, bgHeight - 15, new Color(panelR, panelG, panelB, panelAlpha).getRGB(), 10);
        Render2D.outline(bgX + 7.5f, bgY + 7.5f, 80, bgHeight - 15, 0.5f, new Color(outlineR, outlineG, outlineB, outlineAlpha).getRGB(), 10);

        Render2D.outline(bgX + 12.5f, bgY + 220.5f, 70, 17, 0.5f, new Color(outlineR, outlineG, outlineB, outlineAlpha).getRGB(), 5);

        Fonts.GUI_ICONS.draw("X", bgX + 21.15f, bgY + 217.5f, 19, new Color(58, 58, 58, outlineAlpha).getRGB());
        Fonts.GUI_ICONS.draw("Y", bgX + 40f, bgY + 217f, 20, new Color(58, 58, 58, outlineAlpha).getRGB());
        Fonts.GUI_ICONS.draw("Z", bgX + 60f, bgY + 217f, 20, new Color(58, 58, 58, outlineAlpha).getRGB());

        if (theme.blur) {
            Render2D.blur(bgX + 12.5f, bgY + 220.5f, 70, 17, 4, 5, new Color(25, 25, 25, blurAlpha).getRGB());
        }

        int accentAlpha2 = (int) (220 * alphaMultiplier);
        int labelAlpha = (int) (160 * alphaMultiplier);

        Fonts.SFPRO_REGULAR.draw("Changelog", bgX + 15f, bgY + 221.5f, 4.5f, accentColor(accentAlpha2));
        Fonts.SFPRO_REGULAR.draw(">", bgX + 70f, bgY + 221.5f, 4.5f, accentColor(labelAlpha));

        renderChangelogPopup(bgX, bgY, alphaMultiplier);
    }

    private void renderChangelogPopup(float bgX, float bgY, float alphaMultiplier) {
        float targetAlpha = changelogOpen ? 1f : 0f;
        changelogAlpha += (targetAlpha - changelogAlpha) * 0.15f;
        if (changelogAlpha < 0.01f && !changelogOpen) {
            changelogAlpha = 0f;
            return;
        }

        int overlayAlpha = (int) (204 * changelogAlpha * alphaMultiplier);
        Render2D.rect(0, 0, 4000, 4000, new Color(0, 0, 0, overlayAlpha).getRGB(), 0);

        float popupW = 200;
        float popupH = 150;
        float popupX = bgX + (400 - popupW) / 2f;
        float popupY = bgY + (250 - popupH) / 2f;

        int bgAlpha = (int) (255 * changelogAlpha * alphaMultiplier);
        int outAlpha = (int) (255 * changelogAlpha * alphaMultiplier);
        int accentA = (int) (255 * changelogAlpha * alphaMultiplier);

        Render2D.rect(popupX, popupY, popupW, popupH, new Color(18, 18, 28, bgAlpha).getRGB(), 8);
        Render2D.outline(popupX, popupY, popupW, popupH, 0.8f, new Color(55, 55, 55, outAlpha).getRGB(), 8);
        Render2D.rect(popupX + 5, popupY + 22, popupW - 10, 1.5f,
                new Color(100, 150, 255, (int)(180 * changelogAlpha * alphaMultiplier)).getRGB(), 1);

        Fonts.SFPRO_REGULAR.draw("Changelog", popupX + 10, popupY + 7, 5f, new Color(220, 230, 255, accentA).getRGB());
        Fonts.SFPRO_REGULAR.draw("X", popupX + popupW - 15, popupY + 6, 5.5f, new Color(200, 80, 80, accentA).getRGB());

        String[] entries = {
                "v1.0.14",
                "Changelog popup menu",
                "Compact TargetHud + items",
                "LeafFarmer auto-farm leaves",
                "Launcher remembers login",
                "",
                "v1.0.13",
                "ClickGUI replaced from Rich-Modern1",
                "New HUD theme SFPRO+blue",
                "TargetHud shows items",
                "Accent color configurable",
                "ThirdPersonHud shows account name",
                "Config panel changelog",
                "",
                "v1.0.01",
                "ClickGUI redesigned",
                "Accent color picker",
                "TargetHud syncs accent",
                "Server IP in HUD",
                "Modules: BackSword/Wings/ClanHelper",
                "AI Assistant",
                "Discord RPC",
                "Electron launcher",
                "Russian+English"
        };

        float entryLineH = 5.5f;
        float contentTop = popupY + 28;
        float contentBottom = popupY + popupH - 5;
        float contentH = contentBottom - contentTop;
        float totalContentH = entries.length * entryLineH;
        float maxScroll = Math.max(0, totalContentH - contentH);
        changelogScroll = Math.max(-maxScroll, Math.min(0, changelogScroll));

        float scrollY = contentTop + changelogScroll;
        for (String line : entries) {
            if (line.isEmpty()) {
                scrollY += entryLineH * 0.5f;
                continue;
            }
            if (scrollY + entryLineH > contentTop - entryLineH && scrollY < contentBottom + entryLineH) {
                if (line.startsWith("v")) {
                    Fonts.SFPRO_REGULAR.draw(line, popupX + 10, scrollY, 4f,
                            new Color(100, 150, 255, accentA).getRGB());
                } else {
                    Fonts.SFPRO_REGULAR.draw(line, popupX + 10, scrollY, 3.8f,
                            new Color(150, 150, 150, (int)(200 * changelogAlpha * alphaMultiplier)).getRGB());
                }
            }
            scrollY += entryLineH;
        }

        if (maxScroll > 0) {
            float sbX = popupX + popupW - 6;
            float sbY = contentTop;
            float sbH = contentH;
            Render2D.rect(sbX, sbY, 2, sbH, new Color(40, 40, 50, (int)(120 * changelogAlpha * alphaMultiplier)).getRGB(), 1);
            float thumbH = Math.max(10, sbH * (contentH / totalContentH));
            float thumbY = sbY + (-changelogScroll / maxScroll) * (sbH - thumbH);
            Render2D.rect(sbX, thumbY, 2, thumbH, new Color(100, 150, 255, (int)(160 * changelogAlpha * alphaMultiplier)).getRGB(), 1);
        }
    }
}