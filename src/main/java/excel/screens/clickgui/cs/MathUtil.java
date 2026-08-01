package excel.screens.clickgui.cs;

public class MathUtil {
    public static float lerp(float current, float target, float speed) {
        if (speed <= 0) return target;
        return current + (target - current) / speed;
    }

    public static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    public static float round(float value, float increment) {
        if (increment <= 0) return value;
        return Math.round(value / increment) * increment;
    }

    public static float calculateXPosition(float center, float width) {
        return center - width / 2f;
    }

    public static boolean isInRegion(float mouseX, float mouseY, float x, float y, float width, float height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    public static boolean isHovered(float mouseX, float mouseY, float x, float y, float width, float height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }
}
