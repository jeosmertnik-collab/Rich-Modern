package rich.screens.hud;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import rich.client.draggables.AbstractHudElement;
import rich.modules.impl.combat.Aura;
import rich.modules.impl.render.Hud;
import rich.util.ColorUtil;
import rich.util.network.Network;
import rich.util.render.Render2D;
import rich.util.render.font.Fonts;
import rich.util.render.item.ItemRender;
import rich.util.timer.StopWatch;

import java.awt.*;

public class TargetHud extends AbstractHudElement {

    private final StopWatch stopWatch = new StopWatch();
    private LivingEntity lastTarget;

    private float healthAnimation = 0;
    private float trailAnimation = 0;
    private float absorptionAnimation = 0;
    private float displayedHealth = 0;
    private long lastUpdateTime = System.currentTimeMillis();
    private long startTime = System.currentTimeMillis();

    private static final float ITEM_SCALE = 0.35f;
    private static final float ICON_SIZE = 9f;
    private static final float SLOT_SIZE = 9f;
    private static final float SLOT_GAP = 2f;

    public TargetHud() {
        super("TargetHud", 10, 80, 160, 42, true);
    }

    @Override
    public boolean visible() {
        return true;
    }

    @Override
    public void tick() {
        LivingEntity auraTarget = Aura.target;
        if (auraTarget != null) {
            lastTarget = auraTarget;
            startAnimation();
            stopWatch.reset();
        } else if (isChat(mc.currentScreen)) {
            lastTarget = mc.player;
            startAnimation();
            stopWatch.reset();
        } else if (stopWatch.finished(10)) {
            stopAnimation();
        }
    }

    private int getAccentRGB() {
        if (Hud.getInstance() != null) {
            return Hud.getInstance().getAccentRGB();
        }
        return 0x6496FF;
    }

    private float lerp(float current, float target, float deltaTime, float speed) {
        float factor = (float) (1.0 - Math.pow(0.001, deltaTime * speed));
        return current + (target - current) * factor;
    }

    private float smoothLerp(float current, float target, float deltaTime, float speed) {
        float diff = target - current;
        float smoothFactor = (float) (1.0 - Math.pow(0.0001, deltaTime * speed));
        return current + diff * smoothFactor;
    }

    private float snapToStep(float value, float step) {
        return Math.round(value / step) * step;
    }

    private float getHealth(LivingEntity entity) {
        if (entity.isInvisible() && !Network.isSpookyTime() && !Network.isCopyTime()) {
            return entity.getMaxHealth();
        }
        return entity.getHealth();
    }

    private String getHealthString(float health) {
        if (lastTarget != null && lastTarget.isInvisible() && !Network.isSpookyTime() && !Network.isCopyTime()) {
            return "??";
        }
        if (health >= 100) {
            return String.valueOf((int) health);
        } else if (health >= 10) {
            return String.format("%.1f", health);
        } else {
            return String.format("%.2f", health);
        }
    }

    @Override
    public void drawDraggable(DrawContext context, int alpha) {
        if (alpha <= 0) return;
        if (lastTarget == null) return;

        long currentTime = System.currentTimeMillis();
        float deltaTime = (currentTime - lastUpdateTime) / 1000.0f;
        lastUpdateTime = currentTime;
        deltaTime = Math.min(deltaTime, 0.1f);

        float x = getX();
        float y = getY();

        int accentRGB = getAccentRGB();
        float faceSize = 20;
        float faceX = x + 7;
        float faceY = y + (42 - faceSize) / 2f;
        float contentX = faceX + faceSize + 6;
        float nameY = y + 11;

        boolean hasItems = lastTarget instanceof net.minecraft.entity.player.PlayerEntity;

        float totalWidth = 160;
        float totalHeight = 42;

        setWidth((int) totalWidth);
        setHeight((int) totalHeight);

        float scaleAlpha = scaleAnimation.getOutput().floatValue();

        drawBackground(x, y, totalWidth, totalHeight, scaleAlpha, accentRGB);
        drawFace(x, y, scaleAlpha, faceSize, faceY);
        drawContent(x, y, scaleAlpha, deltaTime, contentX, nameY, totalWidth);

        if (hasItems) {
            drawItems(context, x, y, totalWidth, totalHeight, scaleAlpha, accentRGB);
        }
    }

    private void drawBackground(float x, float y, float w, float h, float alpha, int accentRGB) {
        int alphaInt = (int) (120 * alpha);

        Render2D.gradientRect(x, y, w, h,
                new int[]{
                        new Color(25, 30, 40, alphaInt).getRGB(),
                        new Color(15, 20, 30, alphaInt).getRGB(),
                        new Color(25, 30, 40, alphaInt).getRGB(),
                        new Color(15, 20, 30, alphaInt).getRGB()
                },
                4);

        Render2D.glowOutline(x, y, w, h, 1.0f,
                new Color(accentRGB >> 16 & 0xFF, accentRGB >> 8 & 0xFF, accentRGB & 0xFF, (int)(100 * alpha)).getRGB(), 4, 1.0f, 3.0f);
    }

    private void drawFace(float x, float y, float alpha, float faceSize, float faceY) {
        EntityRenderer<? super LivingEntity, ?> baseRenderer = mc.getEntityRenderDispatcher().getRenderer(lastTarget);
        if (!(baseRenderer instanceof LivingEntityRenderer<?, ?, ?>)) {
            return;
        }

        @SuppressWarnings("unchecked")
        LivingEntityRenderer<LivingEntity, LivingEntityRenderState, ?> renderer =
                (LivingEntityRenderer<LivingEntity, LivingEntityRenderState, ?>) baseRenderer;

        LivingEntityRenderState state = renderer.getAndUpdateRenderState(lastTarget, lastTickDelta);
        Identifier textureLocation = renderer.getTexture(state);

        float faceX = x + 7;

        float hurtPercent = lastTarget.hurtTime > 0 ? lastTarget.hurtTime / 10.0f : 0.0f;
        int r = 255;
        int g = (int) (255 * (1.0f - hurtPercent));
        int b = (int) (255 * (1.0f - hurtPercent));
        int color = new Color(r, g, b, (int) (255 * alpha)).getRGB();

        float u0 = 8f / 64f;
        float v0 = 8f / 64f;
        float u1 = 16f / 64f;
        float v1 = 16f / 64f;

        Render2D.texture(textureLocation, faceX, faceY, faceSize, faceSize,
                u0, v0, u1, v1, color, 0, 4f);

        float hatScale = 1.1f;
        float hatSize = faceSize * hatScale;
        float hatOffset = (hatSize - faceSize) / 2f;

        float hatU0 = 40f / 64f;
        float hatV0 = 8f / 64f;
        float hatU1 = 48f / 64f;
        float hatV1 = 16f / 64f;

        Render2D.texture(textureLocation, faceX - hatOffset, faceY - hatOffset, hatSize, hatSize,
                hatU0, hatV0, hatU1, hatV1, color, 0f, 4f);
    }

    private void drawContent(float x, float y, float alpha, float deltaTime, float contentX, float nameY, float totalWidth) {
        int accentRGB = getAccentRGB();

        float hp = getHealth(lastTarget);
        float maxHp = lastTarget.getMaxHealth();
        float absorp = lastTarget.getAbsorptionAmount();

        boolean isInvisible = lastTarget.isInvisible() && !Network.isSpookyTime() && !Network.isCopyTime();

        float targetDisplayHealth;
        if (isInvisible) {
            targetDisplayHealth = maxHp;
        } else {
            targetDisplayHealth = hp + absorp;
        }
        displayedHealth = lerp(displayedHealth, targetDisplayHealth, deltaTime, 5f);
        float snappedHealth = snapToStep(displayedHealth, 0.25f);

        String hpStr = getHealthString(snappedHealth);

        String name = lastTarget.getName().getString();
        float hpWidth = Fonts.SFPRO_REGULAR.getWidth(hpStr, 5.5f);

        Fonts.SFPRO_REGULAR.draw(name, contentX, nameY, 5.5f,
                new Color(220, 230, 255, (int) (255 * alpha)).getRGB());

        int hpColor = new Color(180, 190, 210, (int) (255 * alpha)).getRGB();
        Fonts.SFPRO_REGULAR.draw(hpStr, x + totalWidth - 10 - hpWidth, nameY, 5.5f, hpColor);

        float targetHealth;
        if (isInvisible) {
            targetHealth = 1.0f;
        } else {
            targetHealth = hp / maxHp;
        }
        healthAnimation = smoothLerp(healthAnimation, targetHealth, deltaTime, 2.5f);

        if (targetHealth > trailAnimation) {
            trailAnimation = targetHealth;
        }
        trailAnimation = smoothLerp(trailAnimation, targetHealth, deltaTime, 2.8f);

        float targetAbsorption;
        if (isInvisible) {
            targetAbsorption = 0;
        } else {
            targetAbsorption = absorp / maxHp;
        }
        absorptionAnimation = smoothLerp(absorptionAnimation, targetAbsorption, deltaTime, 2.5f);

        float barX = contentX;
        float barY = nameY + 12f;
        float barWidth = totalWidth - contentX - 10;
        float barHeight = 4;
        float barRadius = 2;

        Render2D.rect(barX, barY, barWidth, barHeight,
                new Color(30, 35, 45, (int) (180 * alpha)).getRGB(), barRadius);

        float healthPercent = Math.max(0, Math.min(1, healthAnimation));
        float trailPercent = Math.max(0, Math.min(1, trailAnimation));

        if (trailPercent > healthPercent) {
            int trailColor = new Color(50, 55, 65, (int) (140 * alpha)).getRGB();
            Render2D.rect(barX, barY, barWidth * trailPercent, barHeight, trailColor, barRadius);
        }

        if (healthPercent > 0.01f) {
            long elapsed = System.currentTimeMillis() - startTime;
            float waveSpeed = 1500f;
            float wavePhase = (elapsed % (long) waveSpeed) / waveSpeed * (float) Math.PI * 2f;

            int[] colors = new int[4];
            for (int i = 0; i < 2; i++) {
                float charWave = (float) Math.sin(wavePhase - i * 1.5f);
                float waveFactor = (charWave + 1f) / 2f;

                int cr = (accentRGB >> 16) & 0xFF;
                int cg = (accentRGB >> 8) & 0xFF;
                int cb = accentRGB & 0xFF;

                int r = (int) (cr * (0.5f + 0.5f * waveFactor));
                int g = (int) (cg * (0.5f + 0.5f * waveFactor));
                int b = (int) (cb * (0.5f + 0.5f * waveFactor));

                colors[i * 2] = new Color(r, g, b, (int) (255 * alpha)).getRGB();
                colors[i * 2 + 1] = new Color(r, g, b, (int) (255 * alpha)).getRGB();
            }

            Render2D.gradientRect(barX, barY, barWidth * healthPercent, barHeight, colors, barRadius);
        }

        float absorptionPercent = Math.max(0, Math.min(1, absorptionAnimation));
        if (absorptionPercent > 0.01f && !Network.isFunTime()) {
            long elapsed = System.currentTimeMillis() - startTime;
            float waveSpeed = 1200f;
            float wavePhase = (elapsed % (long) waveSpeed) / waveSpeed * (float) Math.PI * 2f;

            int[] goldColors = new int[4];
            for (int i = 0; i < 2; i++) {
                float charWave = (float) Math.sin(wavePhase - i * 1.5f);
                float waveFactor = (charWave + 1f) / 2f;

                int cr = 255;
                int cg = (int) (165 + 50 * waveFactor);
                int cb = 0;

                goldColors[i * 2] = new Color(cr, cg, cb, (int) (200 * alpha)).getRGB();
                goldColors[i * 2 + 1] = new Color(cr, cg, cb, (int) (200 * alpha)).getRGB();
            }

            Render2D.gradientRect(barX, barY, barWidth * absorptionPercent, barHeight, goldColors, barRadius);
        }
    }

    private void drawItems(DrawContext context, float x, float y, float totalWidth, float totalHeight, float alpha, int accentRGB) {
        if (!(lastTarget instanceof net.minecraft.entity.player.PlayerEntity player)) return;

        float itemsY = y + 26;
        float itemsX = x + 7;

        EquipmentSlot[] armorSlots = {
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
        };

        int slotColor = new Color(20, 25, 35, (int)(180 * alpha)).getRGB();
        int outlineColor = new Color(accentRGB >> 16 & 0xFF, accentRGB >> 8 & 0xFF, accentRGB & 0xFF, (int)(60 * alpha)).getRGB();

        for (int i = 0; i < armorSlots.length; i++) {
            float slotX = itemsX + i * (SLOT_SIZE + SLOT_GAP);
            ItemStack stack = player.getEquippedStack(armorSlots[i]);

            Render2D.rect(slotX, itemsY, SLOT_SIZE, SLOT_SIZE, slotColor, 2);
            Render2D.outline(slotX, itemsY, SLOT_SIZE, SLOT_SIZE, 0.3f, outlineColor, 2);

            if (!stack.isEmpty()) {
                float itemSize = 16 * ITEM_SCALE;
                float itemX = slotX + (SLOT_SIZE - itemSize) / 2;
                float itemY = itemsY + (SLOT_SIZE - itemSize) / 2;
                if (ItemRender.needsContextRender(stack)) {
                    ItemRender.drawItemWithContext(context, stack, itemX, itemY, ITEM_SCALE, alpha);
                } else {
                    ItemRender.drawItem(stack, itemX, itemY, ITEM_SCALE, alpha);
                }
            }
        }

        float mainHandX = itemsX + 4 * (SLOT_SIZE + SLOT_GAP) + 6;
        ItemStack mainHand = player.getMainHandStack();
        Render2D.rect(mainHandX, itemsY, SLOT_SIZE + 4, SLOT_SIZE, slotColor, 2);
        Render2D.outline(mainHandX, itemsY, SLOT_SIZE + 4, SLOT_SIZE, 0.3f, outlineColor, 2);
        if (!mainHand.isEmpty()) {
            float itemSize = 16 * ITEM_SCALE;
            float itemX = mainHandX + (SLOT_SIZE + 4 - itemSize) / 2;
            float itemY = itemsY + (SLOT_SIZE - itemSize) / 2;
            if (ItemRender.needsContextRender(mainHand)) {
                ItemRender.drawItemWithContext(context, mainHand, itemX, itemY, ITEM_SCALE, alpha);
            } else {
                ItemRender.drawItem(mainHand, itemX, itemY, ITEM_SCALE, alpha);
            }
        }

        float offHandX = mainHandX + SLOT_SIZE + 10;
        ItemStack offHand = player.getOffHandStack();
        Render2D.rect(offHandX, itemsY, SLOT_SIZE, SLOT_SIZE, slotColor, 2);
        Render2D.outline(offHandX, itemsY, SLOT_SIZE, SLOT_SIZE, 0.3f, outlineColor, 2);
        if (!offHand.isEmpty()) {
            float itemSize = 16 * ITEM_SCALE;
            float itemX = offHandX + (SLOT_SIZE - itemSize) / 2;
            float itemY = itemsY + (SLOT_SIZE - itemSize) / 2;
            if (ItemRender.needsContextRender(offHand)) {
                ItemRender.drawItemWithContext(context, offHand, itemX, itemY, ITEM_SCALE, alpha);
            } else {
                ItemRender.drawItem(offHand, itemX, itemY, ITEM_SCALE, alpha);
            }
        }
    }
}
