package excel.screens.hud;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import excel.client.draggables.AbstractHudElement;
import excel.util.animations.Direction;
import excel.util.lang.Lang;
import excel.util.render.Render2D;
import excel.util.render.font.Fonts;
import excel.util.render.item.ItemRender;
import excel.modules.impl.render.Hud;

import java.awt.*;

public class Durability extends AbstractHudElement {

    private float animatedWidth = 80;
    private float animatedHeight = 23;
    private long lastUpdateTime = System.currentTimeMillis();

    private float displayDurability = 1.0f;

    private static final float ANIMATION_SPEED = 8.0f;
    private static final float ITEM_SCALE = 0.5f;

    private int accentR = 100, accentG = 150, accentB = 255;
    private void updateAccent() {
        int c = Hud.getInstance().getAccentRGB();
        accentR = (c >> 16) & 0xFF;
        accentG = (c >> 8) & 0xFF;
        accentB = c & 0xFF;
    }

    public Durability() {
        super("Durability", 10, 250, 80, 23, true);
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

        ItemStack mainHand = mc.player.getMainHandStack();
        boolean hasDurabilityItem = !mainHand.isEmpty() && mainHand.isDamageable();

        if (hasDurabilityItem || isChat(mc.currentScreen)) {
            startAnimation();
        } else {
            stopAnimation();
        }
    }

    private float lerp(float current, float target, float deltaTime) {
        float factor = (float) (1.0 - Math.pow(0.001, deltaTime * ANIMATION_SPEED));
        return current + (target - current) * factor;
    }

    @Override
    public void drawDraggable(DrawContext context, int alpha) {
        updateAccent();
        if (alpha <= 0) return;
        if (mc.player == null) return;

        float alphaFactor = alpha / 255.0f;

        long currentTime = System.currentTimeMillis();
        float deltaTime = (currentTime - lastUpdateTime) / 1000.0f;
        lastUpdateTime = currentTime;
        deltaTime = Math.min(deltaTime, 0.1f);

        ItemStack mainHand = mc.player.getMainHandStack();
        boolean showExample = mainHand.isEmpty() && isChat(mc.currentScreen);

        float x = getX();
        float y = getY();

        float targetWidth = 80;
        float targetHeight = 23;

        if (showExample) {
            String name = "Diamond Sword";
            float nameWidth = Fonts.SFPRO_REGULAR.getWidth(name, 6);
            targetWidth = Math.max(nameWidth + 60, targetWidth);
        } else if (!mainHand.isEmpty() && mainHand.isDamageable()) {
            String name = mainHand.getName().getString();
            float nameWidth = Fonts.SFPRO_REGULAR.getWidth(name, 6);
            targetWidth = Math.max(nameWidth + 60, targetWidth);
        }

        animatedWidth = lerp(animatedWidth, targetWidth, deltaTime);
        animatedHeight = lerp(animatedHeight, targetHeight, deltaTime);

        if (Math.abs(animatedWidth - targetWidth) < 0.3f) animatedWidth = targetWidth;
        if (Math.abs(animatedHeight - targetHeight) < 0.3f) animatedHeight = targetHeight;

        setWidth((int) Math.ceil(animatedWidth));

        boolean hasContent = showExample || (!mainHand.isEmpty() && mainHand.isDamageable());
        float totalHeight = hasContent ? animatedHeight + 18 : animatedHeight;
        setHeight((int) Math.ceil(totalHeight));

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

        Fonts.ICONS.draw("C", x + 8, y + 6, 10, new Color(accentR, accentG, accentB, bgAlpha).getRGB());
        Fonts.SFPRO_REGULAR.draw(Lang.get().get("hud_durability"), x + 22, y + 6.5f, 6,
                new Color(220, 230, 255, bgAlpha).getRGB());

        if (showExample) {
            drawDurabilityRow(x, y + 20, 0.75f, "Diamond Sword", bgAlpha, null, context);
        } else if (!mainHand.isEmpty() && mainHand.isDamageable()) {
            int maxDmg = mainHand.getMaxDamage();
            int currentDmg = mainHand.getDamage();
            float dur = 1.0f - (float) currentDmg / (float) maxDmg;

            displayDurability = lerp(displayDurability, dur, deltaTime);

            drawDurabilityRow(x, y + 20, displayDurability, mainHand.getName().getString(), bgAlpha, mainHand, context);
        }
    }

    private void drawDurabilityRow(float x, float y, float durability, String name, int bgAlpha, ItemStack stack, DrawContext context) {
        float rowWidth = getWidth() - 16;
        float rowX = x + 8;

        float durWidth = Fonts.SFPRO_REGULAR.getWidth(String.valueOf(Math.round(durability * 100)), 5.5f);
        float pctWidth = Fonts.SFPRO_REGULAR.getWidth("%", 5);

        float nameX = rowX + 16;
        Fonts.SFPRO_REGULAR.draw(name, nameX, y + 1, 6, new Color(220, 230, 255, bgAlpha).getRGB());

        float durX = x + getWidth() - 8 - durWidth - pctWidth;
        Fonts.SFPRO_REGULAR.draw(String.valueOf(Math.round(durability * 100)), durX, y + 1, 5.5f,
                new Color(220, 230, 255, bgAlpha).getRGB());
        Fonts.SFPRO_REGULAR.draw("%", durX + durWidth, y + 1, 5,
                new Color(180, 190, 210, bgAlpha).getRGB());

        float barX = nameX;
        float barY = y + 9;
        float barWidth = rowWidth - 16;
        float barHeight = 3;
        float barRadius = 1.5f;

        Render2D.rect(barX, barY, barWidth, barHeight,
                new Color(20, 25, 35, bgAlpha).getRGB(), barRadius);

        float durPct = MathHelper.clamp(durability, 0f, 1f);
        if (durPct > 0.01f) {
            int barColor;
            if (durPct > 0.6f) {
                barColor = new Color(80, 200, 80, bgAlpha).getRGB();
            } else if (durPct > 0.3f) {
                barColor = new Color(220, 180, 40, bgAlpha).getRGB();
            } else {
                barColor = new Color(200, 50, 50, bgAlpha).getRGB();
            }
            Render2D.rect(barX, barY, barWidth * durPct, barHeight, barColor, barRadius);
        }

        if (stack != null) {
            if (ItemRender.needsContextRender(stack)) {
                ItemRender.drawItemWithContext(context, stack, rowX, y - 2, ITEM_SCALE, bgAlpha / 255f);
            } else {
                ItemRender.drawItem(stack, rowX, y - 2, ITEM_SCALE, bgAlpha / 255f);
            }
        }
    }
}
