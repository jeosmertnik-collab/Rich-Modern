package excel.modules.impl.render;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector4d;
import excel.events.api.EventHandler;
import excel.events.impl.DrawEvent;
import excel.events.impl.TickEvent;
import excel.events.impl.WorldRenderEvent;
import excel.modules.module.ModuleStructure;
import excel.modules.module.category.ModuleCategory;
import excel.modules.module.setting.implement.BooleanSetting;
import excel.modules.module.setting.implement.ColorSetting;
import excel.modules.module.setting.implement.SliderSettings;
import excel.util.Instance;
import excel.util.math.Projection;
import excel.util.render.Render2D;
import excel.util.render.Render3D;
import excel.util.render.font.Fonts;
import excel.util.string.PlayerInteractionHelper;

import java.util.ArrayList;
import java.util.List;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ItemEsp extends ModuleStructure {

    public static ItemEsp getInstance() {
        return Instance.get(ItemEsp.class);
    }

    List<Entity> items = new ArrayList<>();

    BooleanSetting itemEntities = new BooleanSetting("Предметы", "Подсветка предметов на земле").setValue(true);
    BooleanSetting itemFrames = new BooleanSetting("Рамки", "Подсветка рамок с предметами").setValue(true);
    BooleanSetting minecarts = new BooleanSetting("Вагонетки", "Подсветка вагонеток").setValue(true);
    BooleanSetting showNames = new BooleanSetting("Имена", "Показывать имена предметов").setValue(true);
    BooleanSetting showBoxes = new BooleanSetting("Боксы", "Показывать боксы").setValue(true);
    SliderSettings range = new SliderSettings("Дальность", "Дальность отображения").setValue(64).range(16, 256);
    ColorSetting itemColor = new ColorSetting("Цвет предмета", "Цвет подсветки предметов").value(0xFFFFAA00);
    ColorSetting frameColor = new ColorSetting("Цвет рамки", "Цвет подсветки рамок").value(0xFF00FFFF);
    ColorSetting cartColor = new ColorSetting("Цвет вагонетки", "Цвет подсветки вагонеток").value(0xFFFFFF00);

    public ItemEsp() {
        super("ItemESP", "Подсветка предметов", ModuleCategory.RENDER);
        settings(itemEntities, itemFrames, minecarts, showNames, showBoxes, range, itemColor, frameColor, cartColor);
    }

    @EventHandler
    public void onTick(TickEvent e) {
        items.clear();
        if (mc.world == null || mc.player == null) return;

        float maxRange = range.getValue();

        for (Entity entity : PlayerInteractionHelper.streamEntities().toList()) {
            double dist = mc.player.distanceTo(entity);
            if (dist > maxRange) continue;

            if (entity instanceof ItemEntity && itemEntities.isValue()) {
                items.add(entity);
            } else if (entity instanceof ItemFrameEntity && itemFrames.isValue()) {
                items.add(entity);
            } else if (entity instanceof AbstractMinecartEntity && minecarts.isValue()) {
                items.add(entity);
            }
        }
    }

    @EventHandler
    public void onWorldRender(WorldRenderEvent e) {
        if (!showBoxes.isValue()) return;
        float tickDelta = e.getPartialTicks();

        for (Entity entity : items) {
            double interpX = MathHelper.lerp(tickDelta, entity.lastX, entity.getX());
            double interpY = MathHelper.lerp(tickDelta, entity.lastY, entity.getY());
            double interpZ = MathHelper.lerp(tickDelta, entity.lastZ, entity.getZ());

            Box box = entity.getBoundingBox().offset(-entity.getX() + interpX, -entity.getY() + interpY, -entity.getZ() + interpZ);

            int color = getItemColor(entity) | 0xFF000000;
            Render3D.drawBox(box, color, 1.5f, true, false, false);
            Render3D.drawBox(box, color, 1.5f, true, false, true);
        }
    }

    @EventHandler
    public void onDraw(DrawEvent e) {
        if (!showNames.isValue()) return;
        DrawContext context = e.getDrawContext();
        float tickDelta = e.getPartialTicks();
        float size = 5f;

        for (Entity entity : items) {
            Vector4d vec = Projection.getVector4D(entity, tickDelta);
            if (Projection.cantSee(vec)) continue;

            String name = getItemName(entity);
            float textWidth = Fonts.TEST.getWidth(name, size);
            double centerX = Projection.centerX(vec);

            float posX = (float) centerX - textWidth / 2;
            float posY = (float) vec.y - Fonts.TEST.getHeight(size) - 2;

            Render2D.rect(posX - 3, posY - 1, textWidth + 6, Fonts.TEST.getHeight(size) + 2, 0x80000000, 2f);

            int color = getItemColor(entity);
            Fonts.TEST.draw(name, posX, posY, size, color);
        }
    }

    private String getItemName(Entity entity) {
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

    private int getItemColor(Entity entity) {
        if (entity instanceof ItemEntity) {
            return itemColor.getColorNoAlpha();
        } else if (entity instanceof ItemFrameEntity) {
            return frameColor.getColorNoAlpha();
        } else if (entity instanceof AbstractMinecartEntity) {
            return cartColor.getColorNoAlpha();
        }
        return 0xFFFFFFFF;
    }
}
