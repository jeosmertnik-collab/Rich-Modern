package excel.screens.hud;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import excel.client.draggables.AbstractHudElement;
import excel.modules.impl.render.Hud;
import excel.util.animations.Direction;
import excel.util.lang.Lang;
import excel.util.render.Render2D;
import excel.util.render.font.Fonts;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class Radar extends AbstractHudElement {

    private static final float MAP_SIZE = 70;
    private static final float MAP_RADIUS = MAP_SIZE / 2f;
    private static final float RANGE = 64.0f;
    private static final float DOT_SIZE = 2.5f;
    private static final float PLAYER_DOT_SIZE = 3.5f;

    private float animatedWidth = 80;
    private float animatedHeight = 23;
    private long lastUpdateTime = System.currentTimeMillis();

    private int accentR = 100, accentG = 150, accentB = 255;
    private void updateAccent() {
        int c = Hud.getInstance().getAccentRGB();
        accentR = (c >> 16) & 0xFF;
        accentG = (c >> 8) & 0xFF;
        accentB = c & 0xFF;
    }

    public Radar() {
        super("Radar", 400, 10, 80, 23, true);
        stopAnimation();
    }

    @Override
    public boolean visible() {
        return !scaleAnimation.isFinished(Direction.BACKWARDS);
    }

    @Override
    public void tick() {
        if (mc.player == null) {
            stopAnimation();
            return;
        }
        startAnimation();
    }

    private float lerp(float current, float target, float deltaTime) {
        float factor = (float) (1.0 - Math.pow(0.001, deltaTime * 8.0));
        return current + (target - current) * factor;
    }

    @Override
    public void drawDraggable(DrawContext context, int alpha) {
        if (alpha <= 0) return;
        if (mc.player == null) return;
        updateAccent();

        float alphaFactor = alpha / 255.0f;

        long currentTime = System.currentTimeMillis();
        float deltaTime = (currentTime - lastUpdateTime) / 1000.0f;
        lastUpdateTime = currentTime;
        deltaTime = Math.min(deltaTime, 0.1f);

        float x = getX();
        float y = getY();

        float targetWidth = MAP_SIZE + 16;
        float targetHeight = MAP_SIZE + 30;

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

        Fonts.SFPRO_REGULAR.draw(Lang.get().get("hud_radar"), x + 8, y + 6.5f, 6,
                new Color(220, 230, 255, bgAlpha).getRGB());

        float mapX = x + (getWidth() - MAP_SIZE) / 2f;
        float mapY = y + 20;

        Render2D.rect(mapX, mapY, MAP_SIZE, MAP_SIZE,
                new Color(15, 20, 30, (int) (200 * alphaFactor)).getRGB(), MAP_RADIUS);

        Render2D.outline(mapX, mapY, MAP_SIZE, MAP_SIZE, 0.35f,
                new Color(accentR, accentG, accentB, (int)(bgAlpha * 0.3f)).getRGB(), MAP_RADIUS);

        float centerX = mapX + MAP_RADIUS;
        float centerY = mapY + MAP_RADIUS;

        float crossAlpha = 0.3f * alphaFactor;
        int crossColor = new Color(accentR, accentG, accentB, (int) (255 * crossAlpha)).getRGB();
        Render2D.rect(centerX - MAP_RADIUS, centerY, MAP_SIZE, 0.5f, crossColor, 0);
        Render2D.rect(centerX, centerY - MAP_RADIUS, 0.5f, MAP_SIZE, crossColor, 0);

        float ringRadius = MAP_RADIUS * 0.5f;
        Render2D.arcOutline(centerX, centerY, ringRadius, 0.3f, 360f, 0f, 0.3f,
                0, new Color(accentR, accentG, accentB, (int)(60 * alphaFactor)).getRGB());

        Render2D.rect(centerX - 1.5f, centerY - 1.5f, 3, 3,
                new Color(220, 230, 255, bgAlpha).getRGB(), 1.5f);

        List<PlayerEntity> nearbyPlayers = getNearbyPlayers();
        float playerYaw = mc.player.getYaw();

        for (PlayerEntity player : nearbyPlayers) {
            double dx = player.getX() - mc.player.getX();
            double dz = player.getZ() - mc.player.getZ();
            double dist = Math.sqrt(dx * dx + dz * dz);

            if (dist > RANGE) continue;

            float radarX = (float) (dx / RANGE) * MAP_RADIUS;
            float radarZ = (float) (dz / RANGE) * MAP_RADIUS;

            double rad = Math.toRadians(-playerYaw);
            float rotX = (float) (radarX * Math.cos(rad) - radarZ * Math.sin(rad));
            float rotZ = (float) (radarX * Math.sin(rad) + radarZ * Math.cos(rad));

            float dotX = centerX + rotX;
            float dotY = centerY + rotZ;

            dotX = MathHelper.clamp(dotX, mapX + 2, mapX + MAP_SIZE - 2);
            dotY = MathHelper.clamp(dotY, mapY + 2, mapY + MAP_SIZE - 2);

            boolean isInCombat = player.hurtTime > 0;
            int dotColor;
            if (isInCombat) {
                dotColor = new Color(255, 60, 60, bgAlpha).getRGB();
            } else {
                dotColor = new Color(80, 200, 80, bgAlpha).getRGB();
            }

            Render2D.rect(dotX - PLAYER_DOT_SIZE / 2f, dotY - PLAYER_DOT_SIZE / 2f,
                    PLAYER_DOT_SIZE, PLAYER_DOT_SIZE, dotColor, PLAYER_DOT_SIZE / 2f);

            String name = player.getName().getString();
            float nameWidth = Fonts.SFPRO_REGULAR.getWidth(name, 4);
            Fonts.SFPRO_REGULAR.draw(name, dotX - nameWidth / 2f, dotY - PLAYER_DOT_SIZE - 5, 4,
                    new Color(220, 230, 255, bgAlpha).getRGB());
        }

        String playerCountText = String.valueOf(nearbyPlayers.size());
        float countWidth = Fonts.SFPRO_REGULAR.getWidth(playerCountText, 5);
        Fonts.SFPRO_REGULAR.draw(playerCountText, mapX + MAP_SIZE - countWidth - 4, mapY + 3, 5,
                new Color(180, 190, 210, bgAlpha).getRGB());
    }

    private List<PlayerEntity> getNearbyPlayers() {
        List<PlayerEntity> players = new ArrayList<>();
        if (mc.world == null || mc.player == null) return players;

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player) continue;
            double dx = player.getX() - mc.player.getX();
            double dz = player.getZ() - mc.player.getZ();
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist <= RANGE) {
                players.add(player);
            }
        }
        return players;
    }
}
