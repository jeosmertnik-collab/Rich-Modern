package rich.screens.hud;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import rich.client.draggables.AbstractHudElement;
import rich.modules.impl.combat.Aura;
import rich.modules.impl.render.Hud;
import rich.modules.module.setting.implement.ColorSetting;
import rich.util.ColorUtil;
import rich.util.network.Network;
import rich.util.render.Render2D;
import rich.util.render.font.Fonts;
import rich.util.render.item.ItemRender;
import rich.util.timer.StopWatch;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class TargetHud extends AbstractHudElement {

    private final StopWatch stopWatch = new StopWatch();
    private LivingEntity lastTarget;

    private float healthAnimation = 0;
    private float trailAnimation = 0;
    private float absorptionAnimation = 0;
    private float displayedHealth = 0;
    private long lastUpdateTime = System.currentTimeMillis();
    private long startTime = System.currentTimeMillis();

    private static final int PANEL_WIDTH = 130;
    private static final int PANEL_HEIGHT = 58;
    private static final int FACE_SIZE = 24;
    private static final int ITEM_SIZE = 8;
    private static final float HP_BAR_WIDTH = 80;
    private static final float HP_BAR_HEIGHT = 4;

    private static final int[] BG_GRADIENT_COLORS = new int[4];
    private static final int[] GOLD_COLORS = new int[4];
    private static final ItemStack[] GEAR_SLOTS = new ItemStack[6];

    public TargetHud() {
        super("TargetHud", 10, 80, PANEL_WIDTH, PANEL_HEIGHT, true);
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

    private float lerp(float current, float target, float deltaTime, float speed) {
        float factor = (float) (1.0 - Math.pow(0.001, deltaTime * speed));
        return current + (target - current) * factor;
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

    private int getHealthColor(float healthPercent) {
        float r, g;
        if (healthPercent > 0.5f) {
            float t = (healthPercent - 0.5f) * 2f;
            r = 255 * (1f - t);
            g = 255;
        } else {
            float t = healthPercent * 2f;
            r = 255;
            g = 255 * t;
        }
        return 0xFF000000 | ((int) r << 16) | ((int) g << 8) | 40;
    }

    private int getHealthColorAnimated(float healthPercent) {
        float r, g;
        if (healthPercent > 0.5f) {
            float t = (healthPercent - 0.5f) * 2f;
            r = 255 * (1f - t);
            g = 255;
        } else {
            float t = healthPercent * 2f;
            r = 255;
            g = 255 * t;
        }
        long elapsed = System.currentTimeMillis() - startTime;
        float wavePhase = (elapsed % 2000f) / 2000f * (float) Math.PI * 2f;
        float wave = (float) (Math.sin(wavePhase) * 0.08f + 1.0f);
        r = Math.min(255, r * wave);
        g = Math.min(255, g * wave);
        return 0xFF000000 | ((int) r << 16) | ((int) g << 8) | 40;
    }

    @Override
    public void drawDraggable(DrawContext context, int alpha) {
        if (alpha <= 0) return;
        if (lastTarget == null) return;

        long currentTime = System.currentTimeMillis();
        float deltaTime = (currentTime - lastUpdateTime) / 1000.0f;
        lastUpdateTime = currentTime;
        deltaTime = Math.min(deltaTime, 0.1f);

        setWidth(PANEL_WIDTH);
        setHeight(PANEL_HEIGHT);

        float x = getX();
        float y = getY();
        float scaleAlpha = scaleAnimation.getOutput().floatValue();

        drawBackground(x, y, scaleAlpha);
        drawFace(x, y, scaleAlpha);
        drawHealthBar(x, y, scaleAlpha, deltaTime);
        drawGear(context, x, y, scaleAlpha);
        drawPotionEffects(x, y, scaleAlpha);
    }

    private void drawBackground(float x, float y, float alpha) {
        int alphaInt = (int) (255 * alpha);

        BG_GRADIENT_COLORS[0] = (alphaInt << 24) | 0x343434;
        BG_GRADIENT_COLORS[1] = (alphaInt << 24) | 0x161616;
        BG_GRADIENT_COLORS[2] = (alphaInt << 24) | 0x343434;
        BG_GRADIENT_COLORS[3] = (alphaInt << 24) | 0x161616;
        Render2D.gradientRect(x + 2, y + 2, getWidth() - 4, getHeight() - 4,
                BG_GRADIENT_COLORS,
                6);

        int accentOutline = getAccentColor(alphaInt);
        Render2D.outline(x + 2, y + 2, getWidth() - 4, getHeight() - 4, 0.35f, accentOutline, 5);
    }

    private int getAccentColor(int alphaInt) {
        try {
            Hud hud = Hud.getInstance();
            if (hud != null) {
                ColorSetting accent = hud.accentColor;
                int rgb = accent.getColorNoAlpha() & 0x00FFFFFF;
                return (alphaInt << 24) | rgb;
            }
        } catch (Exception ignored) {}
        return (alphaInt << 24) | 0x6C5CE7;
    }

    private void drawFace(float x, float y, float alpha) {
        EntityRenderer<? super LivingEntity, ?> baseRenderer = mc.getEntityRenderDispatcher().getRenderer(lastTarget);
        if (!(baseRenderer instanceof LivingEntityRenderer<?, ?, ?>)) {
            return;
        }

        @SuppressWarnings("unchecked")
        LivingEntityRenderer<LivingEntity, LivingEntityRenderState, ?> renderer =
                (LivingEntityRenderer<LivingEntity, LivingEntityRenderState, ?>) baseRenderer;

        LivingEntityRenderState state = renderer.getAndUpdateRenderState(lastTarget, lastTickDelta);
        Identifier textureLocation = renderer.getTexture(state);

        float faceX = x + 9;
        float faceY = y + 8;

        float hurtPercent = lastTarget.hurtTime > 0 ? lastTarget.hurtTime / 10.0f : 0.0f;
        int r = 255;
        int g = (int) (255 * (1.0f - hurtPercent));
        int b = (int) (255 * (1.0f - hurtPercent));
        int color = ((int)(255 * alpha) << 24) | (r << 16) | (g << 8) | b;

        float u0 = 8f / 64f;
        float v0 = 8f / 64f;
        float u1 = 16f / 64f;
        float v1 = 16f / 64f;

        Render2D.texture(textureLocation, faceX, faceY, FACE_SIZE, FACE_SIZE,
                u0, v0, u1, v1, color, 0, 4f);

        float hatScale = 1.1f;
        float hatSize = FACE_SIZE * hatScale;
        float hatOffset = (hatSize - FACE_SIZE) / 2f;

        float hatU0 = 40f / 64f;
        float hatV0 = 8f / 64f;
        float hatU1 = 48f / 64f;
        float hatV1 = 16f / 64f;

        Render2D.texture(textureLocation, faceX - hatOffset, faceY - hatOffset, hatSize, hatSize,
                hatU0, hatV0, hatU1, hatV1, color, 0f, 4f);
    }

    private void drawHealthBar(float x, float y, float alpha, float deltaTime) {
        float faceX = x + 9;
        float contentX = faceX + FACE_SIZE + 6;
        float nameY = y + 13;

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
        float hpWidth = Fonts.BOLD.getWidth(hpStr, 5.5f);

        int alphaInt = (int) (255 * alpha);
        int accentCol = getAccentColor((int) (120 * alpha));
        Fonts.BOLD.draw(name, contentX, nameY, 5.5f, (alphaInt << 24) | 0xFFFFFF);
        Fonts.BOLD.draw(hpStr, x + getWidth() - 10 - hpWidth, nameY, 5.5f,
                (alphaInt << 24) | 0xD7D7D7);

        float barX = contentX;
        float barY = nameY + 12f;
        float barRadius = 2;

        Render2D.rect(barX, barY, HP_BAR_WIDTH, HP_BAR_HEIGHT,
                ((int)(200 * alpha) << 24) | 0x1E1E1E, barRadius);

        int accentGlow = getAccentColor((int) (40 * alpha));
        Render2D.rect(barX - 1, barY + HP_BAR_HEIGHT, HP_BAR_WIDTH + 2, 2, accentGlow, 1f);

        float targetHealth;
        if (isInvisible) {
            targetHealth = 1.0f;
        } else {
            targetHealth = hp / maxHp;
        }
        healthAnimation = lerp(healthAnimation, targetHealth, deltaTime, 3f);

        if (targetHealth > trailAnimation) {
            trailAnimation = targetHealth;
        }
        trailAnimation = lerp(trailAnimation, targetHealth, deltaTime, 3.5f);

        float targetAbsorption;
        if (isInvisible) {
            targetAbsorption = 0;
        } else {
            targetAbsorption = absorp / maxHp;
        }
        absorptionAnimation = lerp(absorptionAnimation, targetAbsorption, deltaTime, 3f);

        float healthPercent = Math.max(0, Math.min(1, healthAnimation));
        float trailPercent = Math.max(0, Math.min(1, trailAnimation));

        if (trailPercent > healthPercent) {
            int trailColor = ((int)(160 * alpha) << 24) | 0x373737;
            Render2D.rect(barX, barY, HP_BAR_WIDTH * trailPercent, HP_BAR_HEIGHT, trailColor, barRadius);
        }

        if (healthPercent > 0.01f) {
            int hpColor = getHealthColorAnimated(healthPercent);
            int hpColorAlpha = (hpColor & 0x00FFFFFF) | ((int)(255 * alpha) << 24);
            Render2D.rect(barX, barY, HP_BAR_WIDTH * healthPercent, HP_BAR_HEIGHT, hpColorAlpha, barRadius);
        }

        float absorptionPercent = Math.max(0, Math.min(1, absorptionAnimation));
        if (absorptionPercent > 0.01f && !Network.isFunTime()) {
            long elapsed = System.currentTimeMillis() - startTime;
            float waveSpeed = 1200f;
            float wavePhase = (elapsed % (long) waveSpeed) / waveSpeed * (float) Math.PI * 2f;

            for (int i = 0; i < 2; i++) {
                float charWave = (float) Math.sin(wavePhase - i * 1.5f);
                float waveFactor = (charWave + 1f) / 2f;
                int cr = 255;
                int cg = (int) (165 + 50 * waveFactor);
                int cb = 0;
                int goldArgb = ((int)(200 * alpha) << 24) | (cr << 16) | (cg << 8) | cb;
                GOLD_COLORS[i * 2] = goldArgb;
                GOLD_COLORS[i * 2 + 1] = goldArgb;
            }

            Render2D.gradientRect(barX, barY, HP_BAR_WIDTH * absorptionPercent, HP_BAR_HEIGHT, GOLD_COLORS, barRadius);
        }
    }

    private void drawGear(DrawContext context, float x, float y, float alpha) {
        float faceX = x + 9;
        float contentX = faceX + FACE_SIZE + 6;
        float gearY = y + 32;
        float alphaF = alpha;

        ItemStack helmet = lastTarget.getEquippedStack(EquipmentSlot.HEAD);
        ItemStack chest = lastTarget.getEquippedStack(EquipmentSlot.CHEST);
        ItemStack legs = lastTarget.getEquippedStack(EquipmentSlot.LEGS);
        ItemStack boots = lastTarget.getEquippedStack(EquipmentSlot.FEET);
        ItemStack mainHand = lastTarget.getEquippedStack(EquipmentSlot.MAINHAND);
        ItemStack offHand = lastTarget.getEquippedStack(EquipmentSlot.OFFHAND);

        GEAR_SLOTS[0] = helmet;
        GEAR_SLOTS[1] = chest;
        GEAR_SLOTS[2] = legs;
        GEAR_SLOTS[3] = boots;
        GEAR_SLOTS[4] = mainHand;
        GEAR_SLOTS[5] = offHand;
        float gearX = contentX;
        for (ItemStack stack : GEAR_SLOTS) {
            if (!stack.isEmpty()) {
                Render2D.rect(gearX, gearY, ITEM_SIZE + 1, ITEM_SIZE + 1,
                        ((int)(120 * alpha) << 24) | 0x1E1E1E, 1.5f);
                if (ItemRender.needsContextRender(stack)) {
                    ItemRender.drawItemWithContext(context, stack, gearX + 0.5f, gearY + 0.5f, 0.5f, alphaF);
                } else {
                    ItemRender.drawItem(stack, gearX + 0.5f, gearY + 0.5f, 0.5f, alphaF);
                }
            }
            gearX += ITEM_SIZE + 3;
        }
    }

    private void drawPotionEffects(float x, float y, float alpha) {
        if (lastTarget == mc.player) return;

        Collection<StatusEffectInstance> effects = lastTarget.getStatusEffects();
        if (effects.isEmpty()) return;

        float faceX = x + 9;
        float contentX = faceX + FACE_SIZE + 6;
        float effectY = y + 44;
        float effectX = contentX;
        int alphaInt = (int) (255 * alpha);
        int count = 0;

        for (StatusEffectInstance effect : effects) {
            if (count >= 4) break;
            if (!effect.shouldShowIcon()) continue;

            String name = effect.getEffectType().value().getName().getString();
            int amplifier = effect.getAmplifier();
            String levelStr = amplifier > 0 ? " " + toRoman(amplifier + 1) : "";
            String text = name + levelStr;

            boolean isNegative = effect.getEffectType().value().isBeneficial();
            int effectColor = isNegative
                    ? (alphaInt << 24) | 0xFF5050
                    : (alphaInt << 24) | 0x50FF78;

            float textWidth = Fonts.TEST.getWidth(text, 4f);
            if (effectX + textWidth > x + getWidth() - 5) break;

            Fonts.TEST.draw(text, effectX, effectY, 4f, effectColor);
            effectX += textWidth + 6;
            count++;
        }
    }

    private String toRoman(int number) {
        return switch (number) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> String.valueOf(number);
        };
    }
}
