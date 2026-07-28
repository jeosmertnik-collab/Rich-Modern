package excel.util.render;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import org.joml.Vector4d;
import excel.IMinecraft;
import excel.util.math.Projection;
import excel.util.render.font.Fonts;
import excel.util.string.PlayerInteractionHelper;

import java.util.List;

public final class ItemEspRenderer implements IMinecraft {

    private ItemEspRenderer() {}

    public static List<Entity> findNearbyItems(float range) {
        if (mc.world == null || mc.player == null) return List.of();
        return PlayerInteractionHelper.streamEntities()
                .filter(e -> mc.player.distanceTo(e) <= range)
                .filter(e -> e instanceof ItemEntity
                        || e instanceof ItemFrameEntity
                        || e instanceof AbstractMinecartEntity)
                .toList();
    }

    public static void renderBoxes(List<Entity> items, float tickDelta, int itemColor, int frameColor, int cartColor) {
        for (Entity entity : items) {
            double interpX = MathHelper.lerp(tickDelta, entity.lastX, entity.getX());
            double interpY = MathHelper.lerp(tickDelta, entity.lastY, entity.getY());
            double interpZ = MathHelper.lerp(tickDelta, entity.lastZ, entity.getZ());

            Box box = entity.getBoundingBox().offset(
                    -entity.getX() + interpX,
                    -entity.getY() + interpY,
                    -entity.getZ() + interpZ
            );

            int color = resolveColor(entity, itemColor, frameColor, cartColor) | 0xFF000000;
            Render3D.drawBox(box, color, 1.5f, true, false, false);
            Render3D.drawBox(box, color, 1.5f, true, false, true);
        }
    }

    public static void renderNames(List<Entity> items, float tickDelta, float size,
                                   int itemColor, int frameColor, int cartColor) {
        for (Entity entity : items) {
            Vector4d vec = Projection.getVector4D(entity, tickDelta);
            if (Projection.cantSee(vec)) continue;

            String name = getItemName(entity);
            float textWidth = Fonts.TEST.getWidth(name, size);
            double centerX = Projection.centerX(vec);

            float posX = (float) centerX - textWidth / 2;
            float posY = (float) vec.y - Fonts.TEST.getHeight(size) - 2;

            Render2D.rect(posX - 3, posY - 1, textWidth + 6, Fonts.TEST.getHeight(size) + 2, 0x80000000, 2f);

            int color = resolveColor(entity, itemColor, frameColor, cartColor);
            Fonts.TEST.draw(name, posX, posY, size, color);
        }
    }

    public static String getItemName(Entity entity) {
        if (entity instanceof ItemEntity itemEntity) {
            return itemEntity.getStack().getName().getString();
        } else if (entity instanceof ItemFrameEntity frame) {
            ItemStack stack = frame.getHeldItemStack();
            if (!stack.isEmpty()) return stack.getName().getString();
            return "Empty Frame";
        } else if (entity instanceof AbstractMinecartEntity) {
            return "Minecart";
        }
        return entity.getName().getString();
    }

    public static int resolveColor(Entity entity, int itemColor, int frameColor, int cartColor) {
        if (entity instanceof ItemEntity) {
            return itemColor;
        } else if (entity instanceof ItemFrameEntity) {
            return frameColor;
        } else if (entity instanceof AbstractMinecartEntity) {
            return cartColor;
        }
        return 0xFFFFFFFF;
    }
}
