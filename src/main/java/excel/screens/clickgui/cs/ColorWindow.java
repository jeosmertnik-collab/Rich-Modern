package excel.screens.clickgui.cs;

import excel.screens.clickgui.cs.impl.ColorComponent;
import excel.util.ColorUtil;
import excel.util.render.font.Fonts;

import java.awt.Color;

public class ColorWindow {

    private ColorComponent component;
    private final float[] hsb = new float[3];
    private float alpha;
    private boolean dragging;
    private boolean draggingHue;
    private boolean draggingAlpha;

    public ColorWindow(ColorComponent component) {
        this.component = component;
        sync();
    }

    public static float[] copied = new float[3];

    private void sync() {
        hsb[0] = component.option.getHue();
        hsb[1] = component.option.getSaturation();
        hsb[2] = component.option.getBrightness();
        alpha = component.option.getAlpha();
    }

    private void apply() {
        component.option.setHue(hsb[0]);
        component.option.setSaturation(hsb[1]);
        component.option.setBrightness(hsb[2]);
        component.option.setAlpha(alpha);
    }

    public void draw(int mouseX, int mouseY) {
        float width = 114;
        float x = component.x + component.width - 10 + 8 / 2f;
        float y = component.y + component.height / 2f + 8 / 2f;

        DisplayUtils.drawShadow(x, y, width, 160.5f, 9, new Color(0, 0, 0).getRGB());
        DisplayUtils.drawRoundedRect(x, y, width, 160.5f, 3, new Color(17, 18, 21).getRGB());
        DisplayUtils.drawGradientRound(x + 4, y + 4, width - 8, width - 8, 2, Color.WHITE.getRGB(), Color.HSBtoRGB(hsb[0], 1, 1) | 0xFF000000, Color.BLACK.getRGB(), Color.BLACK.getRGB());

        if (dragging) {
            float saturation = MathUtil.clamp((mouseX - x - 4), 0, width - 8) / (width - 8);
            float brightness = MathUtil.clamp((mouseY - y - 4), 0, width - 8) / (width - 8);
            hsb[1] = saturation;
            hsb[2] = 1 - brightness;
        }

        float circleX = x + 4 + hsb[1] * (width - 8);
        float circleY = y + 4 + (1 - hsb[2]) * (width - 8);

        DisplayUtils.drawCircle(circleX + 1, circleY + 1, 8, Color.BLACK.getRGB());
        DisplayUtils.drawCircle(circleX + 1, circleY + 1, 6, ColorUtil.applyAlpha(component.option.getColor(), 255));

        for (int i = 0; i < width - 12; i++) {
            float hue = i / (width - 12);
            DisplayUtils.drawCircle(x + 6 + i, y + width + 6, 6, Color.HSBtoRGB(hue, 1, 1) | 0xFF000000);
        }
        for (int i = 0; i < width - 12; i++) {
            float hue = i / (width - 12);
            DisplayUtils.drawCircle(x + 6 + i, y + width + 18, 6, ColorUtil.lerp(1 - hue, ColorUtil.applyAlpha(component.option.getColor(), 255), new Color(17, 18, 21).getRGB()));
        }
        DisplayUtils.drawCircle(x + 6 + alpha * (width - 12), y + width + 18, 8, Color.BLACK.getRGB());
        DisplayUtils.drawCircle(x + 6 + alpha * (width - 12), y + width + 18, 6, ColorUtil.lerp(alpha, new Color(17, 18, 21).getRGB(), ColorUtil.applyAlpha(component.option.getColor(), 255)));
        if (draggingHue) {
            float hue = MathUtil.clamp((mouseX - x - 6), 0, width - 12) / (width - 12);
            hsb[0] = hue;
        }
        if (draggingAlpha) {
            float hue = MathUtil.clamp((mouseX - x - 6), 0, width - 12) / (width - 12);
            alpha = hue;
        }

        DisplayUtils.drawRoundedRect(x + 4, y + 160.5f - 18 - 4, 51, 18, 3, new Color(40, 45, 51).getRGB());
        DisplayUtils.drawRoundedRect(x + 4 + 51 + 4, y + 160.5f - 18 - 4, 51, 18, 3, new Color(40, 45, 51).getRGB());
        Fonts.REGULAR.drawCentered("Copy", x + 4 + 51 / 2f, y + 160.5f - 14.5f, 14, -1);
        Fonts.REGULAR.drawCentered("Paste", x + 4 + 51 + 4 + 51 / 2f, y + 160.5f - 14.5f, 14, -1);
        DisplayUtils.drawCircle(x + 6 + hsb[0] * (width - 12), y + width + 6, 8, Color.BLACK.getRGB());
        DisplayUtils.drawCircle(x + 6 + hsb[0] * (width - 12), y + width + 6, 6, Color.HSBtoRGB(hsb[0], 1, 1) | 0xFF000000);

        if (dragging || draggingAlpha || draggingHue) {
            apply();
        }
    }

    public void onConfigUpdate() {
        sync();
    }

    public boolean click(int mouseX, int mouseY) {
        float width = 114;
        float x = component.x + component.width - 10 + 8 / 2f;
        float y = component.y + component.height / 2f + 8 / 2f;

        if (!MathUtil.isInRegion(mouseX, mouseY, x, y, width, 160.5f)) {
            return false;
        }

        if (MathUtil.isInRegion(mouseX, mouseY, x + 4, y + width + 1, width - 8, 6)) {
            draggingHue = true;
            return true;
        }
        if (MathUtil.isInRegion(mouseX, mouseY, x + 4, y + 160.5f - 18 - 4, 51, 18)) {
            copied[0] = hsb[0];
            copied[1] = hsb[1];
            copied[2] = hsb[2];
            return true;
        }
        if (MathUtil.isInRegion(mouseX, mouseY, x + 4 + 51 + 4, y + 160.5f - 18 - 4, 51, 18)) {
            if (copied != null && copied.length >= 3) {
                hsb[0] = copied[0];
                hsb[1] = copied[1];
                hsb[2] = copied[2];
                apply();
                sync();
            }
            return true;
        }
        if (MathUtil.isInRegion(mouseX, mouseY, x + 4, y + width + 13, width - 8, 6)) {
            draggingAlpha = true;
            return true;
        }
        if (MathUtil.isInRegion(mouseX, mouseY, x + 4, y + 4, width - 8, width - 8)) {
            dragging = true;
            return true;
        }
        return true;
    }

    public void unclick(int mouseX, int mouseY) {
        dragging = false;
        draggingHue = false;
        draggingAlpha = false;
    }
}
