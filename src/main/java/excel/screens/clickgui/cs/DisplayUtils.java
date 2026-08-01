package excel.screens.clickgui.cs;

import excel.util.ColorUtil;
import excel.util.render.Render2D;

import java.awt.Color;

public class DisplayUtils {

    public static void drawRect(float x, float y, float width, float height, int color) {
        Render2D.rect(x, y, width, height, color);
    }

    public static void drawRectW(float x, float y, float width, float height, int color) {
        Render2D.rect(x, y, width, height, color);
    }

    public static void drawRoundedRect(float x, float y, float width, float height, float radius, int color) {
        Render2D.rect(x, y, width, height, color, radius);
    }

    public static void drawRoundedRect(float x, float y, float width, float height, float topLeft, float topRight, float bottomRight, float bottomLeft, int color) {
        Render2D.rect(x, y, width, height, color, topLeft, topRight, bottomRight, bottomLeft);
    }

    public static void drawRoundedRectWithOutline(float x, float y, float width, float height, float radius, int color, int outlineColor, float outlineWidth) {
        Render2D.rect(x, y, width, height, color, radius);
        Render2D.outline(x, y, width, height, outlineWidth, outlineColor, radius);
    }

    public static void drawShadow(float x, float y, float width, float height, float blurRadius, int color) {
        Render2D.blur(x, y, width, height, Math.max(blurRadius, 2f), Math.min(blurRadius, 20f), color);
    }

    public static void drawCircle(float x, float y, float radius, int color) {
        Render2D.rect(x - radius, y - radius, radius * 2, radius * 2, color, radius);
    }

    public static void drawCircle2(float x, float y, float a, float b, float c, float d, boolean e) {
        drawCircle(x, y, 4f, new Color(80, 85, 95).getRGB());
    }

    public static void drawRectVerticalW(float x, float y, float width, float height, int topColor, int bottomColor) {
        Render2D.gradientRect(x, y, width, height, new int[]{topColor, topColor, bottomColor, bottomColor}, 0);
    }

    public static void drawRectHorizontalW(float x, float y, float width, float height, int leftColor, int rightColor) {
        Render2D.gradientRect(x, y, width, height, new int[]{leftColor, rightColor, rightColor, leftColor}, 0);
    }

    public static void drawGradientRound(float x, float y, float width, float height, float radius, int colorTopLeft, int colorTopRight, int colorBottomLeft, int colorBottomRight) {
        Render2D.gradientRect(x, y, width, height, new int[]{colorTopLeft, colorTopRight, colorBottomRight, colorBottomLeft}, radius);
    }

    public static int reAlphaInt(int color, int alpha) {
        return ColorUtil.applyAlpha(color, alpha);
    }
}
