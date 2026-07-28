package excel.util.render;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector4d;
import excel.IMinecraft;
import excel.util.math.Projection;
import excel.util.render.font.Fonts;
import excel.util.repository.friend.FriendUtils;

public final class NameTagRenderer implements IMinecraft {

    private NameTagRenderer() {}

    public static void drawNameTag(PlayerEntity player, float tickDelta, float size, NameTagStyle style) {
        if (player == null) return;

        Vector4d vec = Projection.getVector4D(player, tickDelta);
        if (Projection.cantSee(vec)) return;

        float dist = (float) mc.gameRenderer.getCamera().getCameraPos().distanceTo(player.getBoundingBox().getCenter());
        if (dist < 1) return;

        double centerX = Projection.centerX(vec);
        double startY = vec.y - 2;

        drawNameTag(player, centerX, startY, size, dist, style);
    }

    public static void drawNameTag(PlayerEntity player, double centerX, double startY, float size, float dist, NameTagStyle style) {
        boolean isFriend = FriendUtils.isFriend(player);
        String name = player.getName().getString();

        StringBuilder sb = new StringBuilder();
        sb.append(name);

        if (style.showPing()) {
            int pingVal = mc.getNetworkHandler() != null
                    ? (mc.getNetworkHandler().getPlayerListEntry(player.getUuid()) != null
                    ? mc.getNetworkHandler().getPlayerListEntry(player.getUuid()).getLatency() : 0) : 0;
            sb.append(" [").append(pingVal).append("ms]");
        }

        if (style.showDistance()) {
            sb.append(" [").append(Math.round(dist)).append("m]");
        }

        String displayText = sb.toString();
        float textWidth = Fonts.TEST.getWidth(displayText, size);
        float textHeight = Fonts.TEST.getHeight(size);

        float posX = (float) centerX - textWidth / 2;
        float posY = (float) startY - textHeight;

        if (style.showBackground()) {
            Render2D.rect(posX - 4, posY - 2, textWidth + 8, textHeight + 4, 0x90000000, 3f);
        }

        int nameCol = isFriend ? style.friendColor() : style.nameColor();
        Fonts.TEST.draw(displayText, posX, posY, size, nameCol);

        if (style.showHealth()) {
            float hp = player.getHealth() + player.getAbsorptionAmount();
            float maxHp = player.getMaxHealth();
            String hpStr = String.format("%.1f", hp);
            float hpWidth = Fonts.TEST.getWidth(hpStr, size * 0.85f);

            float hpRatio = hp / maxHp;
            int hpColor;
            if (hpRatio > 0.6f) {
                hpColor = style.healthColorHigh();
            } else if (hpRatio > 0.3f) {
                hpColor = style.healthColorMid();
            } else {
                hpColor = style.healthColorLow();
            }

            Fonts.TEST.draw(hpStr, posX - hpWidth - 4, posY + 1, size * 0.85f, hpColor);
        }

        if (style.showArmor()) {
            float armorX = posX + textWidth + 4;
            int armorPts = player.getArmor();
            if (armorPts > 0) {
                String armorStr = "⛨" + armorPts;
                Fonts.TEST.draw(armorStr, armorX, posY + 1, size * 0.85f, 0xFFAAAAAA);
            }
        }
    }

    public record NameTagStyle(
            boolean showHealth,
            boolean showDistance,
            boolean showArmor,
            boolean showPing,
            boolean showBackground,
            float scale,
            int nameColor,
            int friendColor,
            int healthColorHigh,
            int healthColorMid,
            int healthColorLow
    ) {
        public static NameTagStyle DEFAULT = new NameTagStyle(
                true, true, true, true, true, 1.0f,
                0xFFFFFFFF, 0xFF00FF00,
                0xFF00FF00, 0xFFFFFF00, 0xFFFF0000
        );

        public static NameTagStyle simple() {
            return DEFAULT;
        }
    }
}
