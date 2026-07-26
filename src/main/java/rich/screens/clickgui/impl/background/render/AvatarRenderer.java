package rich.screens.clickgui.impl.background.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import rich.screens.clickgui.impl.theme.ClickGuiTheme;
import rich.util.render.Render2D;
import rich.util.render.shader.Scissor;
import rich.util.render.font.Fonts;
import rich.util.render.gif.GifRender;
import antidaunleak.api.UserProfile;

import java.awt.*;

public class AvatarRenderer {

    private static final int FORCED_GUI_SCALE = 2;
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static final float AVATAR_SIZE = 24f;
    private static final float AVATAR_X = 14f;
    private static final float AVATAR_Y = 12f;

    public void render(DrawContext context, float bgX, float bgY, float alphaMultiplier) {
        int alpha = (int) (255 * alphaMultiplier);
        int alphaText = (int) (220 * alphaMultiplier);

        UserProfile userProfile = UserProfile.getInstance();
        String username = userProfile.profile("username");
        String uid = userProfile.profile("uid");

        if (username == null || username.isEmpty() || username.equals("null")) {
            username = mc.getSession().getUsername();
        }
        if (uid == null || uid.isEmpty() || uid.equals("null")) {
            uid = username;
        }

        int accentR = (ClickGuiTheme.ACCENT_ARGB >> 16) & 0xFF;
        int accentG = (ClickGuiTheme.ACCENT_ARGB >> 8) & 0xFF;
        int accentB = ClickGuiTheme.ACCENT_ARGB & 0xFF;

        int glowAlpha = (int) (25 * alphaMultiplier);
        Render2D.rect(bgX + AVATAR_X - 3, bgY + AVATAR_Y - 3, AVATAR_SIZE + 6, AVATAR_SIZE + 6,
                new Color(accentR, accentG, accentB, glowAlpha).getRGB(), (AVATAR_SIZE + 6) / 2f);

        Render2D.rect(bgX + AVATAR_X - 1, bgY + AVATAR_Y - 1, AVATAR_SIZE + 2, AVATAR_SIZE + 2,
                new Color(40, 40, 50, (int) (80 * alphaMultiplier)).getRGB(), (AVATAR_SIZE + 2) / 2f);

        Render2D.rect(bgX + AVATAR_X, bgY + AVATAR_Y, AVATAR_SIZE, AVATAR_SIZE,
                new Color(20, 22, 30, alpha).getRGB(), AVATAR_SIZE / 2f);

        context.getMatrices().pushMatrix();
        GifRender.drawAvatar(bgX + AVATAR_X + 0.5f, bgY + AVATAR_Y + 0.5f, AVATAR_SIZE - 1, AVATAR_SIZE - 1, AVATAR_SIZE / 2f, applyAlpha(-1, alpha));
        context.getMatrices().popMatrix();

        float onlineDotX = bgX + AVATAR_X + AVATAR_SIZE - 3.5f;
        float onlineDotY = bgY + AVATAR_Y + AVATAR_SIZE - 3.5f;
        Render2D.rect(onlineDotX, onlineDotY, 5, 5, new Color(34, 197, 94, (int) (255 * alphaMultiplier)).getRGB(), 2.5f);
        Render2D.rect(onlineDotX - 1, onlineDotY - 1, 7, 7, new Color(34, 197, 94, (int) (30 * alphaMultiplier)).getRGB(), 3.5f);

        float textX = bgX + AVATAR_X + AVATAR_SIZE + 10f;
        float textY = bgY + AVATAR_Y + 1f;

        Scissor.enable(textX, textY - 2, 55f, 22f, FORCED_GUI_SCALE);
        Fonts.BOLD.draw(username, textX, textY, 6f, new Color(240, 232, 240, alphaText).getRGB());
        Fonts.BOLD.draw("UID: " + uid, textX, textY + 9f, 4.5f, new Color(120, 120, 140, (int) (180 * alphaMultiplier)).getRGB());
        Scissor.disable();
    }

    private int applyAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }
}
