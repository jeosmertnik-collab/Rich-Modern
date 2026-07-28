package excel.modules.impl.render;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.*;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import excel.events.api.EventHandler;
import excel.events.impl.DrawEvent;
import excel.events.impl.TickEvent;
import excel.events.impl.WorldRenderEvent;
import excel.modules.module.ModuleStructure;
import excel.modules.module.category.ModuleCategory;
import excel.modules.module.setting.implement.BooleanSetting;
import excel.modules.module.setting.implement.ColorSetting;
import excel.modules.module.setting.implement.SliderSettings;
import excel.util.math.Projection;
import excel.util.render.Render2D;
import excel.util.render.Render3D;
import excel.util.render.font.Fonts;

import java.util.*;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class StorageESP extends ModuleStructure {

    BooleanSetting chests = new BooleanSetting("Сундуки", "Подсветка сундуков").setValue(true);
    BooleanSetting enderChests = new BooleanSetting("Эндер-честы", "Подсветка эндер-честов").setValue(true);
    BooleanSetting shulkers = new BooleanSetting("Шалкеры", "Подсветка шалкеров").setValue(true);
    BooleanSetting barrels = new BooleanSetting("Бочки", "Подсветка бочек").setValue(true);
    BooleanSetting signs = new BooleanSetting("Таблички", "Подсветка табличек").setValue(false);

    SliderSettings range = new SliderSettings("Дальность", "Радиус отображения").setValue(48).range(8, 128);
    SliderSettings scanInterval = new SliderSettings("Интервал скана (мс)", "Как часто сканировать").setValue(1000).range(200, 5000);

    BooleanSetting showNames = new BooleanSetting("Имена", "Показывать названия").setValue(true);
    BooleanSetting showBoxes = new BooleanSetting("Боксы", "Показывать 3D боксы").setValue(true);

    ColorSetting chestColor = new ColorSetting("Цвет сундука", "Цвет подсветки сундуков").value(0xFFDAA520);
    ColorSetting enderColor = new ColorSetting("Цвет эндер-честа", "Цвет подсветки эндер-честов").value(0xFF9933FF);
    ColorSetting shulkerColor = new ColorSetting("Цвет шалкера", "Цвет подсветки шалкеров").value(0xFFFF3333);
    ColorSetting barrelColor = new ColorSetting("Цвет бочки", "Цвет подсветки бочек").value(0xFF8B4513);
    ColorSetting signColor = new ColorSetting("Цвет таблички", "Цвет подсветки табличек").value(0xFFC8C8C8);

    Map<BlockPos, StorageType> storageBlocks = new HashMap<>();
    long lastScanTime = 0;

    public StorageESP() {
        super("StorageESP", "Подсветка хранилищ", ModuleCategory.RENDER);
        settings(chests, enderChests, shulkers, barrels, signs, range, scanInterval, showNames, showBoxes,
                chestColor, enderColor, shulkerColor, barrelColor, signColor);
    }

    @Override
    public void deactivate() {
        storageBlocks.clear();
    }

    @EventHandler
    public void onTick(TickEvent e) {
        if (mc.world == null || mc.player == null) return;

        long now = System.currentTimeMillis();
        if (now - lastScanTime < scanInterval.getValue()) return;
        lastScanTime = now;

        storageBlocks.clear();
        BlockPos playerPos = mc.player.getBlockPos();
        int r = (int) range.getValue();
        int chunkMinX = (playerPos.getX() - r) >> 4;
        int chunkMaxX = (playerPos.getX() + r) >> 4;
        int chunkMinZ = (playerPos.getZ() - r) >> 4;
        int chunkMaxZ = (playerPos.getZ() + r) >> 4;
        double rSq = range.getValue() * range.getValue();

        for (int cx = chunkMinX; cx <= chunkMaxX; cx++) {
            for (int cz = chunkMinZ; cz <= chunkMaxZ; cz++) {
                if (!mc.world.getChunkManager().isChunkLoaded(cx, cz)) continue;
                for (BlockEntity be : mc.world.getChunk(cx, cz).getBlockEntities().values()) {
                    BlockPos pos = be.getPos();
                    double distSq = mc.player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                    if (distSq > rSq) continue;

                    StorageType type = getStorageType(be);
                    if (type != null) {
                        storageBlocks.put(pos.toImmutable(), type);
                    }
                }
            }
        }
    }

    @EventHandler
    public void onWorldRender(WorldRenderEvent e) {
        if (!showBoxes.isValue() || storageBlocks.isEmpty()) return;
        float tickDelta = e.getPartialTicks();

        for (Map.Entry<BlockPos, StorageType> entry : storageBlocks.entrySet()) {
            BlockPos pos = entry.getKey();
            int color = getColor(entry.getValue()) | 0xFF000000;
            Box box = new Box(pos);
            Render3D.drawBox(box, color, 1.5f, true, false, false);
            Render3D.drawBox(box, color, 1.5f, true, false, true);
        }
    }

    @EventHandler
    public void onDraw(DrawEvent e) {
        if (!showNames.isValue() || storageBlocks.isEmpty()) return;
        float tickDelta = e.getPartialTicks();
        float size = 5f;

        for (Map.Entry<BlockPos, StorageType> entry : storageBlocks.entrySet()) {
            BlockPos pos = entry.getKey();
            double centerX = pos.getX() + 0.5;
            double centerY = pos.getY() + 1.2;
            double centerZ = pos.getZ() + 0.5;

            Vec3d worldPos = new Vec3d(centerX, centerY, centerZ);
            Vec3d screenPos = Projection.worldSpaceToScreenSpace(worldPos);
            if (screenPos.z <= 0 || screenPos.z >= 1) continue;

            String text = entry.getValue().displayName;
            float textWidth = Fonts.TEST.getWidth(text, size);
            double screenX = screenPos.x;

            float posX = (float) screenX - textWidth / 2;
            float posY = (float) screenPos.y - Fonts.TEST.getHeight(size) - 2;

            int color = getColor(entry.getValue());
            Render2D.rect(posX - 3, posY - 1, textWidth + 6, Fonts.TEST.getHeight(size) + 2, 0x80000000, 2f);
            Fonts.TEST.draw(text, posX, posY, size, color);
        }
    }

    private StorageType getStorageType(BlockEntity be) {
        if (be instanceof ChestBlockEntity && chests.isValue()) return StorageType.CHEST;
        if (be instanceof EnderChestBlockEntity && enderChests.isValue()) return StorageType.ENDER_CHEST;
        if (be instanceof ShulkerBoxBlockEntity && shulkers.isValue()) return StorageType.SHULKER;
        if (be instanceof BarrelBlockEntity && barrels.isValue()) return StorageType.BARREL;
        if (be instanceof SignBlockEntity && signs.isValue()) return StorageType.SIGN;
        return null;
    }

    private int getColor(StorageType type) {
        return switch (type) {
            case CHEST -> chestColor.getColorNoAlpha();
            case ENDER_CHEST -> enderColor.getColorNoAlpha();
            case SHULKER -> shulkerColor.getColorNoAlpha();
            case BARREL -> barrelColor.getColorNoAlpha();
            case SIGN -> signColor.getColorNoAlpha();
        };
    }

    enum StorageType {
        CHEST("Сундук"),
        ENDER_CHEST("Эндер-чест"),
        SHULKER("Шалкер"),
        BARREL("Бочка"),
        SIGN("Табличка");

        final String displayName;

        StorageType(String displayName) {
            this.displayName = displayName;
        }
    }
}
