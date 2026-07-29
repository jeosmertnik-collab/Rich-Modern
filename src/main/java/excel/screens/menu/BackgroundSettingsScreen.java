package excel.screens.menu;

import com.mojang.blaze3d.systems.RenderSystem;
import excel.util.config.impl.background.BackgroundConfig;
import excel.util.render.Render2D;
import excel.util.render.font.Fonts;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class BackgroundSettingsScreen extends Screen {

    private static final int[] PRESET_COLORS = {
            0x0C0F12, 0x1a1f2e, 0x2d2d3f, 0x3a3a5c,
            0x1e1b4b, 0x2d1b69, 0x4a1942, 0x7f1d3b,
            0x0d47a1, 0x1565c0, 0x00838f, 0x00695c,
            0x2e7d32, 0x558b2f, 0x827717, 0xef6c00,
            0xd84315, 0x37474f, 0x546e7a, 0x78909c,
            0x263238, 0x4e342e, 0x5d4037, 0x616161
    };

    private BackgroundConfig bg;
    private long startTime;
    private static final long ANIM_DURATION = 400;

    private float openProgress;
    private String currentHex = "";
    private String gradTopHex = "";
    private String gradBottomHex = "";
    private String imagePath = "";
    private boolean hexFocused = false;
    private boolean gradTopFocused = false;
    private boolean gradBottomFocused = false;
    private boolean imageFocused = false;
    private int activeColorSlot = 0;

    private float buttonHoverProgress;
    private float[] typeHoverProgress = new float[3];
    private int[] presetsScrollOffset = new int[24];
    private float[] presetHoverProgress = new float[24];

    public BackgroundSettingsScreen() {
        super(Text.of("Background Settings"));
    }

    @Override
    protected void init() {
        super.init();
        startTime = Util.getMeasuringTimeMs();
        bg = BackgroundConfig.getInstance();

        currentHex = colorToHex(bg.getSolidColor());
        gradTopHex = colorToHex(bg.getGradientTop());
        gradBottomHex = colorToHex(bg.getGradientBottom());
        imagePath = bg.getBackgroundImage();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        long now = Util.getMeasuringTimeMs();
        openProgress = Math.min(1f, (now - startTime) / ANIM_DURATION);
        float anim = easeOutBack(openProgress);

        int sw = client.getWindow().getScaledWidth();
        int sh = client.getWindow().getScaledHeight();

        int bgAlpha = (int) (openProgress * 160);
        Render2D.rect(0, 0, sw, sh, (bgAlpha << 24) | 0x000000);

        float panelW = 420f;
        float panelH = 340f;
        float panelX = (sw - panelW) / 2f;
        float panelY = (sh - panelH) / 2f;
        panelY += (1f - anim) * 40f;

        int panelBg = (int) (openProgress * 220) << 24 | 0x0E0E1A;
        int borderClr = (int) (openProgress * 200) << 24 | 0x6496FF;

        Render2D.rect(panelX, panelY, panelW, panelH, panelBg, 12);
        Render2D.outline(panelX, panelY, panelW, panelH, 0.5f, borderClr, 12);

        int titleA = (int)(openProgress * 255);
        Fonts.BOLD.draw("Background", panelX + 20, panelY + 14, 11f, (titleA << 24) | 0xFFFFFF);
        Fonts.REGULARNEW.draw("Customize your main menu background", panelX + 20, panelY + 28, 6.5f, (titleA << 24) | 0x808890);

        float contentX = panelX + 20;
        float contentY = panelY + 52;

        drawTypeSelector(contentX, contentY, mouseX, mouseY, titleA);

        contentY += 38;

        String curType = bg.getBackgroundType();
        if ("GRADIENT".equals(curType)) {
            drawColorField(contentX, contentY, "Gradient Top", gradTopHex, gradTopFocused, 0, mouseX, mouseY, titleA);
            drawColorField(contentX + 180, contentY, "Gradient Bottom", gradBottomHex, gradBottomFocused, 1, mouseX, mouseY, titleA);
            contentY += 68;
        } else if ("IMAGE".equals(curType)) {
            drawTextField(contentX, contentY, 380, "Image path (e.g. excel:textures/bg.png)", imagePath, imageFocused, mouseX, mouseY, titleA);
            contentY += 40;
        } else {
            drawColorField(contentX, contentY, "Color", currentHex, hexFocused, 0, mouseX, mouseY, titleA);
            contentY += 68;
        }

        drawParticlesToggle(contentX, contentY, mouseX, mouseY, titleA);
        contentY += 28;

        drawPresetColors(contentX, contentY, mouseX, mouseY, titleA);
        contentY += 80;

        float backBtnX = panelX + panelW - 90;
        float backBtnY = panelY + panelH - 34;
        float backBtnW = 70;
        float backBtnH = 22;

        boolean backHovered = isHover(mouseX, mouseY, backBtnX, backBtnY, backBtnW, backBtnH);
        buttonHoverProgress = MathHelper.lerp(0.1f, buttonHoverProgress, backHovered ? 1f : 0f);
        int backBrightness = (int)(buttonHoverProgress * 20);
        int backColor = (20 + backBrightness) << 24 | 0x6496FF;
        Render2D.rect(backBtnX, backBtnY, backBtnW, backBtnH, backColor, 6);
        int backTA = (int)(openProgress * 255);
        Fonts.REGULAR.drawCentered("Back", backBtnX + backBtnW / 2f, backBtnY + 6, 7f, (backTA << 24) | 0xFFFFFF);
    }

    private void drawTypeSelector(float x, float y, int mouseX, int mouseY, int alpha) {
        String[] types = {"SOLID", "GRADIENT", "IMAGE"};
        String curType = bg.getBackgroundType();

        for (int i = 0; i < 3; i++) {
            float bx = x + i * 128;
            float bw = 124;
            float bh = 26;

            boolean hovered = isHover(mouseX, mouseY, bx, y, bw, bh);
            typeHoverProgress[i] = MathHelper.lerp(0.1f, typeHoverProgress[i], hovered ? 1f : 0f);
            int hoverBright = (int)(typeHoverProgress[i] * 12);

            boolean selected = types[i].equals(curType);
            int bgColor = selected ? 0x306496FF : ((20 + hoverBright) << 24 | 0x1A1E2E);
            int borderColor = selected ? ((alpha * 200 / 255) << 24 | 0x6496FF) : ((alpha * 80 / 255) << 24 | 0x3A3E5E);

            Render2D.rect(bx, y, bw, bh, bgColor, 6);
            Render2D.outline(bx, y, bw, bh, 0.5f, borderColor, 6);

            int textColor = selected ? ((alpha << 24) | 0xFFFFFF) : ((alpha << 24) | 0xAAAAAA);
            Fonts.REGULAR.drawCentered(types[i], bx + bw / 2f, y + 7, 7.5f, textColor);
        }
    }

    private void drawColorField(float x, float y, String label, String hex, boolean focused, int slot, int mouseX, int mouseY, int alpha) {
        Fonts.REGULARNEW.draw(label, x, y, 6f, (alpha << 24) | 0xAAAAAA);

        float swatchSize = 16;
        float swatchY = y + 10;
        int color;
        try {
            color = hexToColorInt(hex);
        } catch (Exception e) {
            color = 0;
        }
        int swatchAlpha = (int)(0.8f * alpha);
        Render2D.rect(x, swatchY, swatchSize, swatchSize, (swatchAlpha << 24) | color, 4);
        Render2D.outline(x, swatchY, swatchSize, swatchSize, 0.5f, (alpha << 24) | 0x4A4E5E, 4);

        float inputX = x + swatchSize + 8;
        float inputW = 120;
        float inputH = 16;
        boolean inputHovered = isHover(mouseX, mouseY, inputX, swatchY, inputW, inputH);
        int inputBg = focused ? 0x301A2E3E : (inputHovered ? 0x301A2E3E : 0x201A1E2E);
        Render2D.rect(inputX, swatchY, inputW, inputH, inputBg, 4);
        int inputBorder = focused ? ((alpha * 200 / 255) << 24 | 0x6496FF) : ((alpha * 60 / 255) << 24 | 0x3A3E5E);
        Render2D.outline(inputX, swatchY, inputW, inputH, 0.5f, inputBorder, 4);

        String display = focused && ((Util.getMeasuringTimeMs() / 500) % 2 == 0) ? hex + "|" : hex;
        if (!focused && hex.isEmpty()) display = "#000000";
        Fonts.REGULARNEW.draw(display, inputX + 4, swatchY + 4, 6f, (alpha << 24) | 0xCCCCCC);
    }

    private void drawTextField(float x, float y, float w, String placeholder, String value, boolean focused, int mouseX, int mouseY, int alpha) {
        float inputH = 24;
        boolean hovered = isHover(mouseX, mouseY, x, y, w, inputH);
        int inputBg = focused ? 0x301A2E3E : (hovered ? 0x301A2E3E : 0x201A1E2E);
        Render2D.rect(x, y, w, inputH, inputBg, 6);
        int border = focused ? ((alpha * 200 / 255) << 24 | 0x6496FF) : ((alpha * 60 / 255) << 24 | 0x3A3E5E);
        Render2D.outline(x, y, w, inputH, 0.5f, border, 6);

        String display = focused && ((Util.getMeasuringTimeMs() / 500) % 2 == 0) ? value + "|" : value;
        if (!focused && value.isEmpty()) {
            Fonts.REGULARNEW.draw(placeholder, x + 6, y + 7, 6.5f, (alpha << 24) | 0x606060);
        } else {
            Fonts.REGULARNEW.draw(display, x + 6, y + 7, 6.5f, (alpha << 24) | 0xCCCCCC);
        }
    }

    private void drawParticlesToggle(float x, float y, int mouseX, int mouseY, int alpha) {
        boolean hovered = isHover(mouseX, mouseY, x, y, 200, 18);
        int bgAlpha = hovered ? 0x30 : 0x20;
        Render2D.rect(x, y, 200, 18, bgAlpha << 24 | 0x1A1E2E, 4);

        boolean enabled = bg.isParticlesEnabled();
        float toggleX = x + 170;
        float toggleW = 24;
        float toggleH = 12;
        float toggleY = y + 3;
        int toggleBg = enabled ? ((alpha * 180 / 255) << 24 | 0x6496FF) : ((alpha * 80 / 255) << 24 | 0x3A3E5E);
        Render2D.rect(toggleX, toggleY, toggleW, toggleH, toggleBg, 6);
        float knobX = enabled ? toggleX + toggleW - 10 : toggleX + 2;
        Render2D.rect(knobX, toggleY + 2, 8, 8, (alpha << 24) | 0xFFFFFF, 4);

        Fonts.REGULARNEW.draw("Particles", x + 8, y + 5, 6.5f, (alpha << 24) | 0xCCCCCC);
    }

    private void drawPresetColors(float x, float y, int mouseX, int mouseY, int alpha) {
        Fonts.REGULARNEW.draw("Presets", x, y, 6f, (alpha << 24) | 0x808890);

        int cols = 8;
        float cellSize = 18;
        float gap = 4;
        float startX = x;
        float startY = y + 12;

        for (int i = 0; i < PRESET_COLORS.length; i++) {
            int row = i / cols;
            int col = i % cols;
            float px = startX + col * (cellSize + gap);
            float py = startY + row * (cellSize + gap);

            boolean hovered = isHover(mouseX, mouseY, px, py, cellSize, cellSize);
            presetHoverProgress[i] = MathHelper.lerp(0.12f, presetHoverProgress[i], hovered ? 1f : 0f);
            int hoverGlow = (int)(presetHoverProgress[i] * 40);

            int presetAlpha = (int)(0.85f * alpha);
            Render2D.rect(px, py, cellSize, cellSize, (presetAlpha << 24) | PRESET_COLORS[i], 4);
            if (hovered) {
                Render2D.outline(px, py, cellSize, cellSize, 1f, (alpha << 24) | 0x6496FF, 4);
            }
        }
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (click.button() != 0) return super.mouseClicked(click, doubled);

        int mx = (int) click.x();
        int my = (int) click.y();
        int sw = client.getWindow().getScaledWidth();
        int sh = client.getWindow().getScaledHeight();

        float panelW = 420f;
        float panelH = 340f;
        float panelX = (sw - panelW) / 2f;
        float panelY = (sh - panelH) / 2f;

        float contentX = panelX + 20;
        float contentY = panelY + 52;

        if (!isHover(mx, my, contentX, contentY, 380, 280)) {
            hexFocused = false;
            gradTopFocused = false;
            gradBottomFocused = false;
            imageFocused = false;
        }

        if (clickTypeSelector(contentX, contentY, mx, my)) return true;

        contentY += 38;
        String curType = bg.getBackgroundType();

        if ("GRADIENT".equals(curType)) {
            if (clickColorField(contentX, contentY, 0, mx, my)) hexFocused = false;
            if (clickColorField(contentX + 180, contentY, 1, mx, my)) gradBottomFocused = true;
            contentY += 68;
        } else if ("IMAGE".equals(curType)) {
            if (clickTextField(contentX, contentY, 380, mx, my)) imageFocused = true;
            contentY += 40;
        } else {
            if (clickColorField(contentX, contentY, 0, mx, my)) hexFocused = true;
            contentY += 68;
        }

        if (clickParticlesToggle(contentX, contentY, mx, my)) return true;
        contentY += 28;

        if (clickPresetColors(contentX, contentY, mx, my)) return true;
        contentY += 80;

        float backBtnX = panelX + panelW - 90;
        float backBtnY = panelY + panelH - 34;
        float backBtnW = 70;
        float backBtnH = 22;
        if (isHover(mx, my, backBtnX, backBtnY, backBtnW, backBtnH)) {
            bg.save();
            client.setScreen(null);
            return true;
        }

        return super.mouseClicked(click, doubled);
    }

    private boolean clickTypeSelector(float x, float y, int mx, int my) {
        for (int i = 0; i < 3; i++) {
            float bx = x + i * 128;
            if (isHover(mx, my, bx, y, 124, 26)) {
                String[] types = {"SOLID", "GRADIENT", "IMAGE"};
                bg.setBackgroundType(types[i]);
                return true;
            }
        }
        return false;
    }

    private boolean clickColorField(float x, float y, int slot, int mx, int my) {
        float swatchSize = 16;
        float swatchY = y + 10;
        float inputX = x + swatchSize + 8;
        float inputW = 120;
        float inputH = 16;

        if (isHover(mx, my, x, swatchY, swatchSize, swatchSize)) {
            activeColorSlot = slot;
            return true;
        }
        if (isHover(mx, my, inputX, swatchY, inputW, inputH)) {
            return true;
        }
        return false;
    }

    private boolean clickTextField(float x, float y, float w, int mx, int my) {
        return isHover(mx, my, x, y, w, 24);
    }

    private boolean clickParticlesToggle(float x, float y, int mx, int my) {
        if (isHover(mx, my, x, y, 200, 18)) {
            bg.setParticlesEnabled(!bg.isParticlesEnabled());
            return true;
        }
        return false;
    }

    private boolean clickPresetColors(float x, float y, int mx, int my) {
        int cols = 8;
        float cellSize = 18;
        float gap = 4;
        float startX = x;
        float startY = y + 12;

        for (int i = 0; i < PRESET_COLORS.length; i++) {
            int row = i / cols;
            int col = i % cols;
            float px = startX + col * (cellSize + gap);
            float py = startY + row * (cellSize + gap);

            if (isHover(mx, my, px, py, cellSize, cellSize)) {
                int clr = PRESET_COLORS[i];
                String curType = bg.getBackgroundType();
                if ("GRADIENT".equals(curType)) {
                    if (gradBottomFocused) {
                        bg.setGradientBottom(clr);
                        gradBottomHex = colorToHex(clr);
                    } else {
                        bg.setGradientTop(clr);
                        gradTopHex = colorToHex(clr);
                    }
                } else {
                    bg.setSolidColor(clr);
                    currentHex = colorToHex(clr);
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int key = input.key();
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            bg.save();
            client.setScreen(null);
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_TAB) {
            applyHexInput();
            return true;
        }
        if (key == GLFW.GLFW_KEY_BACKSPACE) {
            hexType("");
            return true;
        }
        if (key == GLFW.GLFW_KEY_DELETE) {
            String curType = bg.getBackgroundType();
            if ("IMAGE".equals(curType)) {
                imagePath = "";
                bg.setBackgroundImage("");
            } else {
                if (gradBottomFocused) { gradBottomHex = ""; }
                else if (gradTopFocused) { gradTopHex = ""; }
                else { currentHex = ""; }
            }
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        int cp = input.codepoint();
        String c = Character.toString((char) cp);
        if ("0123456789abcdefABCDEF".contains(c)) {
            hexType(String.valueOf(c));
            return true;
        }
        return super.charTyped(input);
    }

    private void hexType(String ch) {
        String curType = bg.getBackgroundType();
        if ("GRADIENT".equals(curType)) {
            if (gradBottomFocused) {
                if (ch.isEmpty() && gradBottomHex.length() > 0) {
                    gradBottomHex = gradBottomHex.substring(0, gradBottomHex.length() - 1);
                } else if (gradBottomHex.length() < 6) {
                    gradBottomHex += ch;
                }
                if (gradBottomHex.length() == 6) {
                    try {
                        bg.setGradientBottom(hexToColorInt(gradBottomHex));
                    } catch (Exception ignored) {}
                }
            } else {
                if (ch.isEmpty() && gradTopHex.length() > 0) {
                    gradTopHex = gradTopHex.substring(0, gradTopHex.length() - 1);
                } else if (gradTopHex.length() < 6) {
                    gradTopHex += ch;
                }
                if (gradTopHex.length() == 6) {
                    try {
                        bg.setGradientTop(hexToColorInt(gradTopHex));
                    } catch (Exception ignored) {}
                }
            }
        } else if ("IMAGE".equals(curType)) {
            if (ch.isEmpty() && imagePath.length() > 0) {
                imagePath = imagePath.substring(0, imagePath.length() - 1);
            } else {
                imagePath += ch;
            }
            bg.setBackgroundImage(imagePath);
        } else {
            if (ch.isEmpty() && currentHex.length() > 0) {
                currentHex = currentHex.substring(0, currentHex.length() - 1);
            } else if (currentHex.length() < 6) {
                currentHex += ch;
            }
            if (currentHex.length() == 6) {
                try {
                    bg.setSolidColor(hexToColorInt(currentHex));
                } catch (Exception ignored) {}
            }
        }
    }

    private void applyHexInput() {
        String curType = bg.getBackgroundType();
        try {
            if ("GRADIENT".equals(curType)) {
                if (gradTopHex.length() == 6) {
                    bg.setGradientTop(hexToColorInt(gradTopHex));
                }
                if (gradBottomHex.length() == 6) {
                    bg.setGradientBottom(hexToColorInt(gradBottomHex));
                }
            } else if (!"IMAGE".equals(curType)) {
                if (currentHex.length() == 6) {
                    bg.setSolidColor(hexToColorInt(currentHex));
                }
            }
        } catch (Exception ignored) {}
    }

    private int hexToColorInt(String hex) {
        if (hex == null || hex.isEmpty()) return 0;
        String h = hex.startsWith("#") ? hex.substring(1) : hex;
        if (h.length() < 6) h = "0".repeat(6 - h.length()) + h;
        return (int) Long.parseLong(h, 16);
    }

    private String colorToHex(int color) {
        return String.format("%06X", color & 0xFFFFFF);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private boolean isHover(double mx, double my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private float easeOutBack(float t) {
        float c1 = 1.70158f;
        float c3 = c1 + 1;
        return 1f + c3 * (float) Math.pow(t - 1, 3) + c1 * (float) Math.pow(t - 1, 2);
    }
}
