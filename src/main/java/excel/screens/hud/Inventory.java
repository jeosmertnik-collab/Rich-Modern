package excel.screens.hud;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import excel.client.draggables.AbstractHudElement;
import excel.modules.impl.render.Hud;
import excel.util.animations.Direction;
import excel.util.render.Render2D;
import excel.util.render.font.Fonts;
import excel.util.render.item.ItemRender;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class Inventory extends AbstractHudElement {

    private static final int SLOT_SIZE = 10;
    private static final int SLOTS_PER_ROW = 9;
    private static final int INVENTORY_ROWS = 3;
    private static final float ITEM_SCALE = 0.4f;

    private int accentR = 100, accentG = 150, accentB = 255;
    private void updateAccent() {
        int c = Hud.getInstance().getAccentRGB();
        accentR = (c >> 16) & 0xFF;
        accentG = (c >> 8) & 0xFF;
        accentB = c & 0xFF;
    }

    public Inventory() {
        super("Inventory", 20, 60, 200, 80, true);
        stopAnimation();
    }

    @Override
    public boolean visible() {
        return !scaleAnimation.isFinished(Direction.BACKWARDS);
    }

    @Override
    public void tick() {
        startAnimation();
    }

    @Override
    public void drawDraggable(DrawContext context, int alpha) {
        updateAccent();
        if (alpha <= 0) return;
        if (mc.player == null) return;

        float alphaFactor = alpha / 255.0f;

        float x = getX();
        float y = getY();

        float padding = 5;
        float slotGap = 1;
        float headerHeight = 16;

        float slotsWidth = SLOTS_PER_ROW * SLOT_SIZE + (SLOTS_PER_ROW - 1) * slotGap;
        float slotsHeight = INVENTORY_ROWS * SLOT_SIZE + (INVENTORY_ROWS - 1) * slotGap;

        float contentWidth = Math.max(slotsWidth + padding * 2, 100);
        float totalHeight = headerHeight + slotsHeight + padding * 2 + 4;

        setWidth((int) contentWidth);
        setHeight((int) totalHeight);

        int bgAlpha = (int) (120 * alphaFactor);

        Render2D.gradientRect(x, y, getWidth(), totalHeight,
                new int[]{
                        new Color(25, 30, 40, bgAlpha).getRGB(),
                        new Color(15, 20, 30, bgAlpha).getRGB(),
                        new Color(25, 30, 40, bgAlpha).getRGB(),
                        new Color(15, 20, 30, bgAlpha).getRGB()
                },
                4);

        Render2D.glowOutline(x, y, getWidth(), totalHeight, 1.0f,
                new Color(accentR, accentG, accentB, (int)(80 * alphaFactor)).getRGB(), 4, 1.0f, 3.0f);

        Fonts.SFPRO_REGULAR.draw("Inventory", x + 8, y + 4, 5,
                new Color(220, 230, 255, (int)(255 * alphaFactor)).getRGB());

        float slotsStartX = x + (getWidth() - slotsWidth) / 2f;
        float slotsStartY = y + headerHeight + padding;

        List<CountLabel> countLabels = new ArrayList<>();

        for (int row = 0; row < INVENTORY_ROWS; row++) {
            for (int col = 0; col < SLOTS_PER_ROW; col++) {
                int slotIndex = 9 + row * SLOTS_PER_ROW + col;

                float slotX = slotsStartX + col * (SLOT_SIZE + slotGap);
                float slotY = slotsStartY + row * (SLOT_SIZE + slotGap);

                ItemStack stack = mc.player.getInventory().getStack(slotIndex);

                Render2D.rect(slotX, slotY, SLOT_SIZE, SLOT_SIZE,
                        new Color(20, 25, 35, (int)(200 * alphaFactor)).getRGB(), 1.5f);
                Render2D.outline(slotX, slotY, SLOT_SIZE, SLOT_SIZE, 0.3f,
                        new Color(accentR, accentG, accentB, (int)(30 * alphaFactor)).getRGB(), 1.5f);

                if (!stack.isEmpty()) {
                    float itemSize = 16 * ITEM_SCALE;
                    float itemX = slotX + (SLOT_SIZE - itemSize) / 2;
                    float itemY = slotY + (SLOT_SIZE - itemSize) / 2;

                    if (ItemRender.needsContextRender(stack)) {
                        ItemRender.drawItemWithContext(context, stack, itemX, itemY, ITEM_SCALE, alphaFactor);
                    } else {
                        ItemRender.drawItem(stack, itemX, itemY, ITEM_SCALE, alphaFactor);
                    }

                    int count = stack.getCount();
                    if (count > 1) {
                        countLabels.add(new CountLabel(slotX, slotY, count));
                    }
                }
            }
        }

        int textAlpha = (int) (255 * alphaFactor);

        for (CountLabel label : countLabels) {
            String countText = String.valueOf(label.count);
            int textWidth = mc.textRenderer.getWidth(countText);
            int textX = (int) (label.slotX + SLOT_SIZE - textWidth - 1);
            int textY = (int) (label.slotY + SLOT_SIZE - mc.textRenderer.fontHeight + 1);

            context.drawText(mc.textRenderer, countText, textX, textY, (textAlpha << 24) | 0xFFFFFF, true);
        }
    }

    private record CountLabel(float slotX, float slotY, int count) {}
}
