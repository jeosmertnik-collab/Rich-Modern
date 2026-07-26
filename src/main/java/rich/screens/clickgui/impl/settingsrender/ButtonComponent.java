package rich.screens.clickgui.impl.settingsrender;

import net.minecraft.client.gui.DrawContext;
import rich.modules.module.setting.implement.ButtonSetting;
import rich.screens.clickgui.impl.theme.ClickGuiTheme;
import rich.util.interfaces.AbstractSettingComponent;
import rich.util.render.Render2D;
import rich.util.render.font.Fonts;

import java.awt.*;

public class ButtonComponent extends AbstractSettingComponent {
    private final ButtonSetting buttonSetting;
    private float pressAnimation = 0f;
    private float hoverAnimation = 0f;
    private float rippleAnimation = 0f;
    private float rippleX = 0f;
    private float rippleY = 0f;
    private boolean wasPressed = false;
    private boolean rippleActive = false;

    private long lastUpdateTime = System.currentTimeMillis();
    private static final float ANIMATION_SPEED = 8f;
    private static final float FAST_ANIMATION_SPEED = 12f;
    private static final float BUTTON_WIDTH = 65f;
    private static final float BUTTON_HEIGHT = 12f;

    public ButtonComponent(ButtonSetting setting) {
        super(setting);
        this.buttonSetting = setting;
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

    private int clamp(int v) { return Math.max(0, Math.min(255, v)); }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        float deltaTime = getDeltaTime();
        boolean hovered = isButtonHover(mouseX, mouseY);
        hoverAnimation = lerp(hoverAnimation, hovered ? 1f : 0f, deltaTime * ANIMATION_SPEED);
        pressAnimation = lerp(pressAnimation, wasPressed ? 1f : 0f, deltaTime * FAST_ANIMATION_SPEED);
        if (rippleActive) { rippleAnimation += deltaTime * 3f; if (rippleAnimation >= 1f) { rippleAnimation = 0f; rippleActive = false; } }
        if (pressAnimation < 0.05f && wasPressed) wasPressed = false;

        int iconAlpha = (int)(200 * alphaMultiplier);
        Fonts.GUI_ICONS.draw("U", x + 0.5f, y + height / 2 - 12f, 13,
                ((iconAlpha & 0xFF) << 24) | (ClickGuiTheme.SETTINGS_DESC_ARGB & 0xFFFFFF));

        int nameAlpha = (int)(220 * alphaMultiplier);
        Fonts.BOLD.draw(buttonSetting.getName(), x + 9.5f, y + height / 2 - 7.5f, 6,
                ((nameAlpha & 0xFF) << 24) | (ClickGuiTheme.SETTINGS_TITLE_ARGB & 0xFFFFFF));

        String description = buttonSetting.getDescription();
        if (description != null && !description.isEmpty()) {
            int descAlpha = (int)(120 * alphaMultiplier);
            Fonts.BOLD.draw(description, x + 0.5f, y + height / 2 + 0.5f, 5,
                    ((descAlpha & 0xFF) << 24) | (ClickGuiTheme.SETTINGS_DESC_ARGB & 0xFFFFFF));
        }

        renderButton(mouseX, mouseY);
    }

    private void renderButton(int mouseX, int mouseY) {
        float buttonX = x + width - BUTTON_WIDTH - 2;
        float buttonY = y + height / 2 - BUTTON_HEIGHT / 2;
        float pressOffset = pressAnimation * 1f;
        float btnY = buttonY + pressOffset;

        int aR = (ClickGuiTheme.ACCENT_ARGB >> 16) & 0xFF;
        int aG = (ClickGuiTheme.ACCENT_ARGB >> 8) & 0xFF;
        int aB = ClickGuiTheme.ACCENT_ARGB & 0xFF;

        int bgAlpha = clamp((int) ((25 + hoverAnimation * 20 + pressAnimation * 15) * alphaMultiplier));
        int bgCol;
        if (hoverAnimation > 0.01f) {
            bgCol = new Color(
                    clamp((int)(aR * 0.3f + hoverAnimation * aR * 0.3f)),
                    clamp((int)(aG * 0.3f + hoverAnimation * aG * 0.3f)),
                    clamp((int)(aB * 0.3f + hoverAnimation * aB * 0.3f)),
                    bgAlpha
            ).getRGB();
        } else {
            bgCol = ((bgAlpha & 0xFF) << 24) | (ClickGuiTheme.PANEL_BORDER_LIGHT_ARGB & 0xFFFFFF);
        }
        Render2D.rect(buttonX, btnY, BUTTON_WIDTH, BUTTON_HEIGHT, bgCol, 4f);

        if (rippleActive && rippleAnimation > 0) {
            float currentRippleSize = 20 * rippleAnimation;
            float rippleAlpha = (1f - rippleAnimation) * 0.4f;
            int rippleAlphaInt = clamp((int)(255 * rippleAlpha * alphaMultiplier));
            float localRippleX = rippleX - buttonX;
            float localRippleY = rippleY - btnY;
            Render2D.rect(buttonX + localRippleX - currentRippleSize / 2, btnY + localRippleY - currentRippleSize / 2,
                    currentRippleSize, currentRippleSize,
                    new Color(aR, aG, aB, rippleAlphaInt).getRGB(), currentRippleSize / 2);
        }

        int outlineAlpha = clamp((int) ((50 + hoverAnimation * 60 + pressAnimation * 40) * alphaMultiplier));
        Render2D.outline(buttonX, btnY, BUTTON_WIDTH, BUTTON_HEIGHT, 0.5f,
                new Color(aR, aG, aB, outlineAlpha).getRGB(), 4f);

        String buttonText = buttonSetting.getButtonName() != null ? buttonSetting.getButtonName() : "Run";
        float textWidth = Fonts.BOLD.getWidth(buttonText, 5);
        float textX = buttonX + (BUTTON_WIDTH - textWidth) / 2;
        float textY = btnY + BUTTON_HEIGHT / 2 - 3f;
        int textAlpha = clamp((int) ((180 + hoverAnimation * 50) * alphaMultiplier));
        Render2D.rect(textX - 5, textY + 1, 3, 3, new Color(aR, aG, aB, (int)(textAlpha * 0.6f)).getRGB(), 1f);
        Fonts.BOLD.draw(buttonText, textX, textY, 5,
                new Color(255, 255, 255, textAlpha).getRGB());
    }

    private boolean isButtonHover(double mouseX, double mouseY) {
        float buttonX = x + width - BUTTON_WIDTH - 2;
        float buttonY = y + height / 2 - BUTTON_HEIGHT / 2;
        return mouseX >= buttonX && mouseX <= buttonX + BUTTON_WIDTH &&
                mouseY >= buttonY && mouseY <= buttonY + BUTTON_HEIGHT;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isButtonHover(mouseX, mouseY) && button == 0) {
            if (buttonSetting.getRunnable() != null) buttonSetting.getRunnable().run();
            wasPressed = true;
            pressAnimation = 1f;
            rippleActive = true;
            rippleAnimation = 0f;
            rippleX = (float) mouseX;
            rippleY = (float) mouseY;
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
}
