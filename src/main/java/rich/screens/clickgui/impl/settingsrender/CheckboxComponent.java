package rich.screens.clickgui.impl.settingsrender;

import net.minecraft.client.gui.DrawContext;
import rich.modules.module.setting.implement.BooleanSetting;
import rich.screens.clickgui.impl.theme.ClickGuiTheme;
import rich.util.interfaces.AbstractSettingComponent;
import rich.util.render.Render2D;
import rich.util.render.font.Fonts;

import java.awt.*;

public class CheckboxComponent extends AbstractSettingComponent {
    private final BooleanSetting booleanSetting;
    private float checkAnimation = 0f;
    private float hoverAnimation = 0f;
    private float stretchAnimation = 0f;
    private float velocity = 0f;

    public CheckboxComponent(BooleanSetting setting) {
        super(setting);
        this.booleanSetting = setting;
        this.checkAnimation = setting.isValue() ? 1f : 0f;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        boolean hovered = isHover(mouseX, mouseY);
        float hoverTarget = hovered ? 1f : 0f;
        hoverAnimation += (hoverTarget - hoverAnimation) * 0.2f;
        hoverAnimation = clamp(hoverAnimation, 0f, 1f);

        float target = booleanSetting.isValue() ? 1f : 0f;
        float oldCheck = checkAnimation;
        float speed = 0.35f;
        checkAnimation += (target - checkAnimation) * speed;
        if (Math.abs(target - checkAnimation) < 0.001f) checkAnimation = target;
        velocity = checkAnimation - oldCheck;

        float absVelocity = Math.abs(velocity);
        float targetStretch = absVelocity * 30f;
        targetStretch = clamp(targetStretch, 0f, 1f);
        float stretchSpeed = targetStretch > stretchAnimation ? 0.5f : 0.2f;
        stretchAnimation += (targetStretch - stretchAnimation) * stretchSpeed;
        stretchAnimation = clamp(stretchAnimation, 0f, 1f);

        int iconAlpha = (int)(200 * alphaMultiplier);
        Fonts.GUI_ICONS.draw("T", x + 0.5f, y + height / 2 - 11f, 11,
                ((iconAlpha & 0xFF) << 24) | (ClickGuiTheme.SETTINGS_DESC_ARGB & 0xFFFFFF));

        int nameAlpha = (int)(220 * alphaMultiplier);
        int nameB = (int)(180 + 40 * checkAnimation);
        int nameColor = ((Math.min(255, nameAlpha) & 0xFF) << 24) | ((nameB & 0xFF) << 16) | ((nameB & 0xFF) << 8) | (nameB & 0xFF);
        Fonts.BOLD.draw(booleanSetting.getName(), x + 9.5f, y + height / 2 - 7.5f, 6, nameColor);

        int descAlpha = (int)(120 * alphaMultiplier);
        Fonts.BOLD.draw(booleanSetting.getDescription(), x + 0.5f, y + height / 2 + 0.5f, 5,
                ((descAlpha & 0xFF) << 24) | (ClickGuiTheme.SETTINGS_DESC_ARGB & 0xFFFFFF));

        float toggleW = 20f;
        float toggleH = 11f;
        float toggleX = x + width - toggleW - 2;
        float toggleY = y + height / 2 - toggleH / 2;

        int trackA = (int) ((50 + 40 * hoverAnimation) * alphaMultiplier);
        if (checkAnimation > 0.5f) {
            int aR = (ClickGuiTheme.ACCENT_ARGB >> 16) & 0xFF;
            int aG = (ClickGuiTheme.ACCENT_ARGB >> 8) & 0xFF;
            int aB = ClickGuiTheme.ACCENT_ARGB & 0xFF;
            Render2D.rect(toggleX, toggleY, toggleW, toggleH,
                    new Color(aR, aG, aB, (int) (trackA * 1.5f)).getRGB(), 5.5f);
        } else {
            Render2D.rect(toggleX, toggleY, toggleW, toggleH,
                    ((trackA & 0xFF) << 24) | (ClickGuiTheme.PANEL_BORDER_LIGHT_ARGB & 0xFFFFFF), 5.5f);
        }

        float knobBaseSize = toggleH - 3f;
        float maxStretchExtra = 4f;
        float stretchExtra = stretchAnimation * maxStretchExtra;
        float knobWidth = knobBaseSize + stretchExtra;
        float knobHeight = knobBaseSize - (stretchAnimation * 1f);
        float padding = 1.5f;
        float travelDistance = toggleW - knobBaseSize - (padding * 2);

        float knobBaseX = toggleX + padding;
        float stretchOffset = velocity > 0 ? -stretchExtra * 0.3f : velocity < 0 ? stretchExtra * 0.3f : 0;
        float knobX = knobBaseX + (travelDistance * checkAnimation) - (stretchExtra * checkAnimation) + stretchOffset;
        float knobY = toggleY + (toggleH - knobHeight) / 2f;

        int knobAlpha = (int) (255 * alphaMultiplier);
        Render2D.rect(knobX, knobY, knobWidth, knobHeight,
                new Color(255, 255, 255, knobAlpha).getRGB(), knobWidth / 2f);
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isHover(mouseX, mouseY) && button == 0) {
            booleanSetting.setValue(!booleanSetting.isValue());
            return true;
        }
        return false;
    }

    @Override
    public void tick() {
    }

    @Override
    public boolean isHover(double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }
}
