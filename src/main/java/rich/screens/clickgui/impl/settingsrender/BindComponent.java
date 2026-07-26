package rich.screens.clickgui.impl.settingsrender;

import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;
import rich.modules.module.setting.implement.BindSetting;
import rich.screens.clickgui.impl.theme.ClickGuiTheme;
import rich.util.interfaces.AbstractSettingComponent;
import rich.util.render.Render2D;
import rich.util.render.font.Fonts;

import java.awt.*;

public class BindComponent extends AbstractSettingComponent {
    private boolean listening = false;
    private float listeningAnimation = 0f;
    private float hoverAnimation = 0f;
    private float bindHoverAnimation = 0f;
    private float pulseAnimation = 0f;
    private float textChangeAnimation = 0f;
    private String previousBindText = "";
    private String currentBindText = "";

    private long lastUpdateTime = System.currentTimeMillis();
    private static final float ANIMATION_SPEED = 8f;
    private static final float FAST_ANIMATION_SPEED = 12f;
    private static final float BIND_BOX_WIDTH = 32f;
    private static final float BIND_BOX_HEIGHT = 10f;

    public static final int SCROLL_UP_BIND = 1000;
    public static final int SCROLL_DOWN_BIND = 1001;
    public static final int MIDDLE_MOUSE_BIND = 1002;

    public BindComponent(BindSetting setting) {
        super(setting);
        BindSetting bindSetting = (BindSetting) getSetting();
        this.currentBindText = getBindDisplayName(bindSetting.getKey(), bindSetting.getType());
        this.previousBindText = this.currentBindText;
    }

    private float getDeltaTime() {
        long currentTime = System.currentTimeMillis();
        float dt = Math.min((currentTime - lastUpdateTime) / 1000f, 0.1f);
        lastUpdateTime = currentTime;
        return dt;
    }

    private float lerp(float current, float target, float speed) {
        float diff = target - current;
        if (Math.abs(diff) < 0.001f) return target;
        return current + diff * Math.min(speed, 1f);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        float deltaTime = getDeltaTime();
        boolean bindHovered = isBindHover(mouseX, mouseY);

        hoverAnimation = lerp(hoverAnimation, isHover(mouseX, mouseY) ? 1f : 0f, deltaTime * ANIMATION_SPEED);
        bindHoverAnimation = lerp(bindHoverAnimation, bindHovered ? 1f : 0f, deltaTime * ANIMATION_SPEED);
        listeningAnimation = lerp(listeningAnimation, listening ? 1f : 0f, deltaTime * FAST_ANIMATION_SPEED);
        if (listening) pulseAnimation += deltaTime * 4f;
        else pulseAnimation = lerp(pulseAnimation, 0f, deltaTime * ANIMATION_SPEED);

        BindSetting bindSetting = (BindSetting) getSetting();
        String newBindText = listening ? "..." : getBindDisplayName(bindSetting.getKey(), bindSetting.getType());
        if (!newBindText.equals(currentBindText)) {
            previousBindText = currentBindText;
            currentBindText = newBindText;
            textChangeAnimation = 0f;
        }
        textChangeAnimation = lerp(textChangeAnimation, 1f, deltaTime * FAST_ANIMATION_SPEED);

        int iconAlpha = (int)(200 * alphaMultiplier);
        Fonts.GUI_ICONS.draw("L", x + 1.5f, y + height / 2 - 6f, 6,
                ((iconAlpha & 0xFF) << 24) | (ClickGuiTheme.SETTINGS_DESC_ARGB & 0xFFFFFF));

        int nameAlpha = (int)(220 * alphaMultiplier);
        Fonts.BOLD.draw(getSetting().getName(), x + 9.5f, y + height / 2 - 7.5f, 6,
                ((nameAlpha & 0xFF) << 24) | (ClickGuiTheme.SETTINGS_TITLE_ARGB & 0xFFFFFF));

        String description = getSetting().getDescription();
        if (description != null && !description.isEmpty()) {
            int descAlpha = (int)(120 * alphaMultiplier);
            Fonts.BOLD.draw(description, x + 0.5f, y + height / 2 + 0.5f, 5,
                    ((descAlpha & 0xFF) << 24) | (ClickGuiTheme.SETTINGS_DESC_ARGB & 0xFFFFFF));
        }

        renderBindBox(bindSetting);
    }

    private void renderBindBox(BindSetting bindSetting) {
        float bindBoxX = x + width - BIND_BOX_WIDTH - 2;
        float bindBoxY = y + height / 2 - BIND_BOX_HEIGHT / 2;

        int bgAlpha = (int) ((30 + bindHoverAnimation * 15 + listeningAnimation * 20) * alphaMultiplier);
        int bgCol;
        if (listening) {
            float pulse = (float)(Math.sin(pulseAnimation) * 0.15 + 0.85);
            int aR = (ClickGuiTheme.ACCENT_ARGB >> 16) & 0xFF;
            int aG = (ClickGuiTheme.ACCENT_ARGB >> 8) & 0xFF;
            int aB = ClickGuiTheme.ACCENT_ARGB & 0xFF;
            bgCol = new Color(aR, aG, aB, (int)(bgAlpha * pulse * 0.6f)).getRGB();
        } else {
            bgCol = ((bgAlpha & 0xFF) << 24) | (ClickGuiTheme.PANEL_BORDER_LIGHT_ARGB & 0xFFFFFF);
        }
        Render2D.rect(bindBoxX, bindBoxY, BIND_BOX_WIDTH, BIND_BOX_HEIGHT, bgCol, 3f);

        int outlineAlpha;
        if (listening) {
            float pulse = (float)(Math.sin(pulseAnimation) * 0.3 + 0.7);
            outlineAlpha = (int) (180 * pulse * listeningAnimation * alphaMultiplier);
            int aR = (ClickGuiTheme.ACCENT_ARGB >> 16) & 0xFF;
            int aG = (ClickGuiTheme.ACCENT_ARGB >> 8) & 0xFF;
            int aB = ClickGuiTheme.ACCENT_ARGB & 0xFF;
            Render2D.outline(bindBoxX, bindBoxY, BIND_BOX_WIDTH, BIND_BOX_HEIGHT, 0.5f,
                    new Color(aR, aG, aB, outlineAlpha).getRGB(), 3f);
        } else if (bindSetting.getKey() != GLFW.GLFW_KEY_UNKNOWN && bindSetting.getKey() != -1) {
            outlineAlpha = (int) ((80 + bindHoverAnimation * 40) * alphaMultiplier);
            int aR = (ClickGuiTheme.ACCENT_ARGB >> 16) & 0xFF;
            int aG = (ClickGuiTheme.ACCENT_ARGB >> 8) & 0xFF;
            int aB = ClickGuiTheme.ACCENT_ARGB & 0xFF;
            Render2D.outline(bindBoxX, bindBoxY, BIND_BOX_WIDTH, BIND_BOX_HEIGHT, 0.5f,
                    new Color(aR, aG, aB, outlineAlpha).getRGB(), 3f);
        } else {
            outlineAlpha = (int) ((50 + bindHoverAnimation * 30) * alphaMultiplier);
            int oCol = ((outlineAlpha & 0xFF) << 24) | (ClickGuiTheme.PANEL_BORDER_LIGHT_ARGB & 0xFFFFFF);
            Render2D.outline(bindBoxX, bindBoxY, BIND_BOX_WIDTH, BIND_BOX_HEIGHT, 0.5f, oCol, 3f);
        }

        renderBindText(bindBoxX, bindBoxY, bindSetting);

        if (listening) renderListeningIndicator(bindBoxX, bindBoxY);
    }

    private void renderBindText(float boxX, float boxY, BindSetting bindSetting) {
        float textY = boxY + BIND_BOX_HEIGHT / 2 - 2.5f;
        float centerX = boxX + BIND_BOX_WIDTH / 2;

        int textAlpha;
        if (listening) {
            float pulse = (float)(Math.sin(pulseAnimation * 2) * 0.2 + 0.8);
            textAlpha = (int)(220 * pulse * alphaMultiplier);
            int aR = (ClickGuiTheme.ACCENT_ARGB >> 16) & 0xFF;
            int aG = (ClickGuiTheme.ACCENT_ARGB >> 8) & 0xFF;
            int aB = ClickGuiTheme.ACCENT_ARGB & 0xFF;
            Fonts.BOLD.drawCentered(currentBindText, centerX, textY, 5,
                    new Color(aR, aG, aB, textAlpha).getRGB());
        } else if (bindSetting.getKey() != GLFW.GLFW_KEY_UNKNOWN && bindSetting.getKey() != -1) {
            textAlpha = (int)(200 * alphaMultiplier);
            int aR = (ClickGuiTheme.ACCENT_ARGB >> 16) & 0xFF;
            int aG = (ClickGuiTheme.ACCENT_ARGB >> 8) & 0xFF;
            int aB = ClickGuiTheme.ACCENT_ARGB & 0xFF;
            Fonts.BOLD.drawCentered(currentBindText, centerX, textY, 5,
                    new Color(aR, aG, aB, textAlpha).getRGB());
        } else {
            textAlpha = (int)(140 * alphaMultiplier);
            Fonts.BOLD.drawCentered(currentBindText, centerX, textY, 5,
                    ((textAlpha & 0xFF) << 24) | (ClickGuiTheme.SETTINGS_DESC_ARGB & 0xFFFFFF));
        }
    }

    private void renderListeningIndicator(float boxX, float boxY) {
        float dotSpacing = 3f;
        float dotSize = 1.5f;
        float startX = boxX + (BIND_BOX_WIDTH - dotSpacing * 2) / 2 - dotSize / 2;
        float dotY = boxY + BIND_BOX_HEIGHT - 5.5f;

        int aR = (ClickGuiTheme.ACCENT_ARGB >> 16) & 0xFF;
        int aG = (ClickGuiTheme.ACCENT_ARGB >> 8) & 0xFF;
        int aB = ClickGuiTheme.ACCENT_ARGB & 0xFF;

        for (int i = 0; i < 3; i++) {
            float pulse = (float)(Math.sin(pulseAnimation + i * 0.5f) * 0.5 + 0.5);
            float currentDotSize = dotSize * (0.5f + pulse * 0.5f);
            int alpha = (int)(150 * (0.3f + pulse * 0.7f) * listeningAnimation * alphaMultiplier);
            float dotX = startX + i * dotSpacing + (dotSize - currentDotSize) / 2;
            Render2D.rect(dotX, dotY + (dotSize - currentDotSize) / 2, currentDotSize, currentDotSize,
                    new Color(aR, aG, aB, alpha).getRGB(), currentDotSize / 2);
        }
    }

    private String getBindDisplayName(int key, int type) {
        if (key == GLFW.GLFW_KEY_UNKNOWN || key == -1) return "None";
        if (key == SCROLL_UP_BIND) return "ScrollUp";
        if (key == SCROLL_DOWN_BIND) return "ScrollDn";
        if (key == MIDDLE_MOUSE_BIND) return "MMB";

        if (type == 0) {
            return switch (key) {
                case GLFW.GLFW_MOUSE_BUTTON_LEFT -> "LMB";
                case GLFW.GLFW_MOUSE_BUTTON_RIGHT -> "RMB";
                case GLFW.GLFW_MOUSE_BUTTON_MIDDLE -> "MMB";
                case GLFW.GLFW_MOUSE_BUTTON_4 -> "M4";
                case GLFW.GLFW_MOUSE_BUTTON_5 -> "M5";
                default -> "M" + key;
            };
        }

        String keyName = GLFW.glfwGetKeyName(key, 0);
        if (keyName == null) {
            return switch (key) {
                case GLFW.GLFW_KEY_LEFT_SHIFT -> "LShift";
                case GLFW.GLFW_KEY_RIGHT_SHIFT -> "RShift";
                case GLFW.GLFW_KEY_LEFT_CONTROL -> "LCtrl";
                case GLFW.GLFW_KEY_RIGHT_CONTROL -> "RCtrl";
                case GLFW.GLFW_KEY_LEFT_ALT -> "LAlt";
                case GLFW.GLFW_KEY_RIGHT_ALT -> "RAlt";
                case GLFW.GLFW_KEY_SPACE -> "Space";
                case GLFW.GLFW_KEY_TAB -> "Tab";
                case GLFW.GLFW_KEY_CAPS_LOCK -> "Caps";
                case GLFW.GLFW_KEY_ENTER -> "Enter";
                case GLFW.GLFW_KEY_BACKSPACE -> "Back";
                case GLFW.GLFW_KEY_INSERT -> "Ins";
                case GLFW.GLFW_KEY_DELETE -> "Del";
                case GLFW.GLFW_KEY_HOME -> "Home";
                case GLFW.GLFW_KEY_END -> "End";
                case GLFW.GLFW_KEY_PAGE_UP -> "PgUp";
                case GLFW.GLFW_KEY_PAGE_DOWN -> "PgDn";
                case GLFW.GLFW_KEY_UP -> "Up";
                case GLFW.GLFW_KEY_DOWN -> "Down";
                case GLFW.GLFW_KEY_LEFT -> "Left";
                case GLFW.GLFW_KEY_RIGHT -> "Right";
                case GLFW.GLFW_KEY_F1 -> "F1";
                case GLFW.GLFW_KEY_F2 -> "F2";
                case GLFW.GLFW_KEY_F3 -> "F3";
                case GLFW.GLFW_KEY_F4 -> "F4";
                case GLFW.GLFW_KEY_F5 -> "F5";
                case GLFW.GLFW_KEY_F6 -> "F6";
                case GLFW.GLFW_KEY_F7 -> "F7";
                case GLFW.GLFW_KEY_F8 -> "F8";
                case GLFW.GLFW_KEY_F9 -> "F9";
                case GLFW.GLFW_KEY_F10 -> "F10";
                case GLFW.GLFW_KEY_F11 -> "F11";
                case GLFW.GLFW_KEY_F12 -> "F12";
                case GLFW.GLFW_KEY_ESCAPE -> "Esc";
                default -> "Key" + key;
            };
        }
        return keyName.toUpperCase();
    }

    private boolean isBindHover(double mouseX, double mouseY) {
        float bindBoxX = x + width - BIND_BOX_WIDTH - 2;
        float bindBoxY = y + height / 2 - BIND_BOX_HEIGHT / 2;
        return mouseX >= bindBoxX && mouseX <= bindBoxX + BIND_BOX_WIDTH &&
                mouseY >= bindBoxY && mouseY <= bindBoxY + BIND_BOX_HEIGHT;
    }

    public void handleScrollBind(double vertical) {
        if (listening) {
            BindSetting bindSetting = (BindSetting) getSetting();
            bindSetting.setKey(vertical > 0 ? SCROLL_UP_BIND : SCROLL_DOWN_BIND);
            bindSetting.setType(2);
            listening = false;
        }
    }

    public void handleMiddleMouseBind() {
        if (listening) {
            BindSetting bindSetting = (BindSetting) getSetting();
            bindSetting.setKey(MIDDLE_MOUSE_BIND);
            bindSetting.setType(2);
            listening = false;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isBindHover(mouseX, mouseY)) {
            if (button == 1) {
                ((BindSetting) getSetting()).setKey(GLFW.GLFW_KEY_UNKNOWN);
                ((BindSetting) getSetting()).setType(1);
                listening = false;
                return true;
            } else if (listening) {
                ((BindSetting) getSetting()).setKey(button);
                ((BindSetting) getSetting()).setType(0);
                listening = false;
                return true;
            } else if (button == 0) {
                listening = true;
                return true;
            }
        } else if (listening) {
            listening = false;
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (listening) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) { listening = false; return true; }
            else if (keyCode == GLFW.GLFW_KEY_BACKSPACE || keyCode == GLFW.GLFW_KEY_DELETE) {
                ((BindSetting) getSetting()).setKey(GLFW.GLFW_KEY_UNKNOWN);
                ((BindSetting) getSetting()).setType(1);
                listening = false;
                return true;
            } else if (keyCode != GLFW.GLFW_KEY_UNKNOWN) {
                ((BindSetting) getSetting()).setKey(keyCode);
                ((BindSetting) getSetting()).setType(1);
                listening = false;
                return true;
            }
            return true;
        }
        return false;
    }

    @Override
    public void tick() {}

    @Override
    public boolean isHover(double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    public boolean isListening() { return listening; }
}
