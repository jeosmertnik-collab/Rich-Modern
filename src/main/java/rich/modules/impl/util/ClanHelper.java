package rich.modules.impl.util;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import net.minecraft.block.Blocks;
import net.minecraft.item.BlockItem;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import rich.events.api.EventHandler;
import rich.events.impl.TickEvent;
import rich.modules.module.ModuleStructure;
import rich.modules.module.category.ModuleCategory;
import rich.modules.module.setting.implement.BooleanSetting;
import rich.modules.module.setting.implement.SliderSettings;
import rich.util.inventory.InventoryUtils;
import rich.util.timer.StopWatch;

@Getter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ClanHelper extends ModuleStructure {

    SliderSettings delay = new SliderSettings("Задержка (мс)", "Задержка между циклами")
            .range(50f, 500f).setValue(100f);

    BooleanSetting swingHandSetting = new BooleanSetting("Анимация руки", "Показывать анимацию удара");

    BooleanSetting onlyOnFunTime = new BooleanSetting("Только FunTime", "Работать только на сервере FunTime");

    StopWatch timer = new StopWatch();

    @NonFinal int savedSlot = -1;

    public ClanHelper() {
        super("Clan Helper", "Ставит факел под ноги и сразу ломает", ModuleCategory.UTIL);
        settings(delay, swingHandSetting, onlyOnFunTime);
    }

    @Override
    public void activate() {
        timer.reset();
        savedSlot = -1;
    }

    @Override
    public void deactivate() {
        if (savedSlot != -1 && mc.player != null) {
            InventoryUtils.selectSlot(savedSlot);
            savedSlot = -1;
        }
    }

    @EventHandler
    public void onTick(TickEvent e) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        if (mc.currentScreen != null) return;
        if (onlyOnFunTime.isValue() && mc.getNetworkHandler() != null
                && mc.getNetworkHandler().getServerInfo() != null
                && !mc.getNetworkHandler().getServerInfo().address.toLowerCase().contains("funtime")) return;

        if (!timer.finished((long) delay.getValue())) return;

        int torchSlot = findTorchSlot();
        if (torchSlot == -1) return;

        BlockPos below = mc.player.getBlockPos().down();
        if (canPlace(below)) {
            placeTorch(below, torchSlot);
            breakTorch(below);
        }

        timer.reset();
    }

    private boolean canPlace(BlockPos pos) {
        return mc.world.getBlockState(pos).isAir();
    }

    private void placeTorch(BlockPos pos, int slot) {
        if (savedSlot == -1) savedSlot = mc.player.getInventory().getSelectedSlot();
        InventoryUtils.selectSlot(slot);

        Vec3d hitVec = new Vec3d(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
        BlockHitResult hitResult = new BlockHitResult(hitVec, Direction.DOWN, pos, false);

        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hitResult);
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    private void breakTorch(BlockPos pos) {
        mc.interactionManager.updateBlockBreakingProgress(pos, Direction.UP);
        mc.interactionManager.attackBlock(pos, Direction.UP);
        if (swingHandSetting.isValue()) mc.player.swingHand(Hand.MAIN_HAND);
    }

    private boolean isTorch(net.minecraft.item.Item item) {
        return item instanceof BlockItem blockItem && blockItem.getBlock() == Blocks.TORCH;
    }

    private int findTorchSlot() {
        int current = mc.player.getInventory().getSelectedSlot();
        if (isTorch(mc.player.getInventory().getStack(current).getItem())) {
            return current;
        }
        for (int i = 0; i < 9; i++) {
            if (isTorch(mc.player.getInventory().getStack(i).getItem())) {
                return i;
            }
        }
        return -1;
    }
}
