package excel.screens.hud;

import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;
import excel.client.draggables.AbstractHudElement;
import excel.modules.impl.render.Hud;
import excel.util.animations.Direction;
import excel.util.lang.Lang;
import excel.util.render.Render2D;
import excel.util.render.font.Fonts;

import java.awt.*;

public class Keystrokes extends AbstractHudElement {

    private static final float KEY_SIZE = 18;
    private static final float KEY_GAP = 3;
    private static final float KEY_RADIUS = 3;
    private static final float PRESS_ANIM_SPEED = 12f;

    private final float[] keyPressAnim = new float[6];
    private final int[] keyCodes = {
            GLFW.GLFW_KEY_W,
            GLFW.GLFW_KEY_A,
            GLFW.GLFW_KEY_S,
            GLFW.GLFW_KEY_D,
            GLFW.GLFW_MOUSE_BUTTON_LEFT,
            GLFW.GLFW_MOUSE_BUTTON_RIGHT
    };
    private final String[] keyLabels = {"W", "A", "S", "D", "LMB", "RMB"};

    private int accentR = 100, accentG = 150, accentB = 255;
    private void updateAccent() {
        int c = Hud.getInstance().getAccentRGB();
        accentR = (c >> 16) & 0xFF;
        accentG = (c >> 8) & 0xFF;
        accentB = c & 0xFF;
    }

    private float animatedWidth = 80;
    private float animatedHeight = 23;
    private long lastUpdateTime = System.currentTimeMillis();

    public Keystrokes() {
        super("Keystrokes", 10, 200, 80, 23, true);
        stopAnimation();
    }

    @Override
    public boolean visible() {
        return !scaleAnimation.isFinished(Direction.BACKWARDS);
    }

    @Override
    public void tick() {
        boolean anyPressed = false;
        if (mc.currentScreen != null && !isChat(mc.currentScreen)) {
            startAnimation();
            return;
        }

        long handle = mc.getWindow().getHandle();
        for (int i = 0; i < keyCodes.length; i++) {
            if (GLFW.glfwGetKey(handle, keyCodes[i]) == GLFW.GLFW_PRESS ||
                GLFW.glfwGetMouseButton(handle, keyCodes[i]) == GLFW.GLFW_PRESS) {
                anyPressed = true;
            }
        }

        if (anyPressed || isChat(mc.currentScreen)) {
            startAnimation();
        } else {
            stopAnimation();
        }
    }

    private float lerp(float current, float target, float deltaTime) {
        float factor = (float) (1.0 - Math.pow(0.001, deltaTime * PRESS_ANIM_SPEED));
        return current + (target - current) * factor;
    }

    @Override
    public void drawDraggable(DrawContext context, int alpha) {
        updateAccent();
        if (alpha <= 0) return;

        float alphaFactor = alpha / 255.0f;

        long currentTime = System.currentTimeMillis();
        float deltaTime = (currentTime - lastUpdateTime) / 1000.0f;
        lastUpdateTime = currentTime;
        deltaTime = Math.min(deltaTime, 0.1f);

        float x = getX();
        float y = getY();

        long handle = mc.getWindow().getHandle();
        for (int i = 0; i < keyCodes.length; i++) {
            boolean pressed = GLFW.glfwGetKey(handle, keyCodes[i]) == GLFW.GLFW_PRESS ||
                              GLFW.glfwGetMouseButton(handle, keyCodes[i]) == GLFW.GLFW_PRESS;
            float target = pressed ? 1f : 0f;
            keyPressAnim[i] = lerp(keyPressAnim[i], target, deltaTime);
        }

        float totalWidth = 3 * KEY_SIZE + 2 * KEY_GAP;
        float totalHeight = 2 * KEY_SIZE + KEY_GAP + 20;

        float targetWidth = totalWidth + 16;
        float targetHeight = totalHeight + 6;

        animatedWidth = lerp(animatedWidth, targetWidth, deltaTime);
        animatedHeight = lerp(animatedHeight, targetHeight, deltaTime);

        if (Math.abs(animatedWidth - targetWidth) < 0.3f) animatedWidth = targetWidth;
        if (Math.abs(animatedHeight - targetHeight) < 0.3f) animatedHeight = targetHeight;

        setWidth((int) Math.ceil(animatedWidth));
        setHeight((int) Math.ceil(animatedHeight));

        int bgAlpha = (int) (255 * alphaFactor);

        Render2D.gradientRect(x, y, getWidth(), getHeight(),
                new int[]{
                        new Color(25, 30, 40, bgAlpha).getRGB(),
                        new Color(15, 20, 30, bgAlpha).getRGB(),
                        new Color(25, 30, 40, bgAlpha).getRGB(),
                        new Color(15, 20, 30, bgAlpha).getRGB()
                }, 4);

        Render2D.glowOutline(x, y, getWidth(), getHeight(), 1.0f,
                new Color(accentR, accentG, accentB, (int)(bgAlpha * 0.4f)).getRGB(), 4, 1.0f, 3.0f);

        Fonts.SFPRO_REGULAR.draw(Lang.get().get("hud_keystrokes"), x + 8, y + 6.5f, 6,
                new Color(220, 230, 255, bgAlpha).getRGB());

        float keysStartX = x + (getWidth() - totalWidth) / 2f;
        float keysStartY = y + 20;

        drawKey(context, keysStartX + (KEY_SIZE + KEY_GAP), keysStartY, KEY_SIZE, KEY_SIZE, keyLabels[0], keyPressAnim[0], bgAlpha);

        drawKey(context, keysStartX, keysStartY + KEY_SIZE + KEY_GAP, KEY_SIZE, KEY_SIZE, keyLabels[1], keyPressAnim[1], bgAlpha);
        drawKey(context, keysStartX + KEY_SIZE + KEY_GAP, keysStartY + KEY_SIZE + KEY_GAP, KEY_SIZE, KEY_SIZE, keyLabels[2], keyPressAnim[2], bgAlpha);
        drawKey(context, keysStartX + 2 * (KEY_SIZE + KEY_GAP), keysStartY + KEY_SIZE + KEY_GAP, KEY_SIZE, KEY_SIZE, keyLabels[3], keyPressAnim[3], bgAlpha);

        float mouseY = keysStartY + 2 * (KEY_SIZE + KEY_GAP) + 2;
        float mouseWidth = (totalWidth - KEY_GAP) / 2f;
        drawKey(context, keysStartX, mouseY, mouseWidth, KEY_SIZE - 2, keyLabels[4], keyPressAnim[4], bgAlpha);
        drawKey(context, keysStartX + mouseWidth + KEY_GAP, mouseY, mouseWidth, KEY_SIZE - 2, keyLabels[5], keyPressAnim[5], bgAlpha);
    }

    private void drawKey(DrawContext context, float kx, float ky, float kw, float kh, String label, float pressAnim, int bgAlpha) {
        float press = Math.max(0, Math.min(1, pressAnim));

        int keyBgR = (int) (35 - 15 * press);
        int keyBgG = (int) (40 - 10 * press);
        int keyBgB = (int) (50 + 20 * press);
        int keyBg = new Color(keyBgR, keyBgG, keyBgB, bgAlpha).getRGB();

        float yOffset = press * 1.5f;

        Render2D.gradientRect(kx, ky + yOffset, kw, kh,
                new int[]{keyBg, keyBg, keyBg, keyBg}, KEY_RADIUS);

        Render2D.outline(kx, ky + yOffset, kw, kh, 0.25f,
                new Color(accentR, accentG, accentB, (int)(120 * bgAlpha / 255.0f)).getRGB(), KEY_RADIUS);

        float labelWidth = Fonts.SFPRO_REGULAR.getWidth(label, 5);
        float labelX = kx + (kw - labelWidth) / 2f;
        float labelY = ky + yOffset + (kh - 6) / 2f;

        int textColor = new Color(220, 230, 255, bgAlpha).getRGB();
        Fonts.SFPRO_REGULAR.draw(label, labelX, labelY, 5, textColor);
    }
}
