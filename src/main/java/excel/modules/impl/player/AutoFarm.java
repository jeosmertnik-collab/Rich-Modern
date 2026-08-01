package excel.modules.impl.player;

import net.minecraft.block.*;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import excel.events.api.EventHandler;
import excel.events.impl.TickEvent;
import excel.modules.module.ModuleStructure;
import excel.modules.module.category.ModuleCategory;
import excel.modules.module.setting.implement.BooleanSetting;
import excel.modules.module.setting.implement.SliderSettings;
import excel.util.inventory.InventoryUtils;
import excel.util.timer.TimerUtil;

public class AutoFarm extends ModuleStructure {

    private final SliderSettings radius = new SliderSettings("Radius", "Scan radius for crops")
            .range(1, 6).setValue(4);

    private final BooleanSetting autoReplant = new BooleanSetting("Auto Replant", "Replant after breaking")
            .setValue(true);

    private final BooleanSetting onlyWhileSneaking = new BooleanSetting("Only While Sneaking", "Only auto-farm when sneaking")
            .setValue(false);

    private BlockPos currentTarget = null;
    private final TimerUtil breakTimer = TimerUtil.create();
    private static final long BREAK_DELAY = 100;

    public AutoFarm() {
        super("AutoFarm", "Automatically breaks fully-grown crops and replants them", ModuleCategory.PLAYER);
        settings(radius, autoReplant, onlyWhileSneaking);
    }

    @Override
    public void activate() {
        currentTarget = null;
        breakTimer.resetCounter();
    }

    @EventHandler
    public void onTick(TickEvent event) {
        if (mc.player == null || mc.world == null) return;
        if (onlyWhileSneaking.isValue() && !mc.player.isSneaking()) return;

        if (currentTarget != null && !isGrownCrop(currentTarget)) {
            currentTarget = null;
        }

        if (currentTarget == null) {
            currentTarget = findNearestCrop();
        }

        if (currentTarget == null) return;

        if (!breakTimer.hasTimeElapsed(BREAK_DELAY)) return;

        mc.interactionManager.updateBlockBreakingProgress(currentTarget, Direction.UP);
        mc.interactionManager.attackBlock(currentTarget, Direction.UP);
        mc.player.swingHand(Hand.MAIN_HAND);

        if (autoReplant.isValue()) {
            replant(currentTarget);
        }

        currentTarget = null;
        breakTimer.resetCounter();
    }

    private boolean isGrownCrop(BlockPos pos) {
        BlockState state = mc.world.getBlockState(pos);
        Block block = state.getBlock();

        if (block == Blocks.WHEAT || block == Blocks.CARROTS || block == Blocks.POTATOES) {
            return state.get(CropBlock.AGE) >= 7;
        }
        if (block == Blocks.BEETROOTS) {
            return state.get(BeetrootsBlock.AGE) >= 3;
        }
        if (block == Blocks.NETHER_WART) {
            return state.get(NetherWartBlock.AGE) >= 3;
        }
        if (block == Blocks.COCOA) {
            return state.get(CocoaBlock.AGE) >= 2;
        }
        if (block == Blocks.MELON || block == Blocks.PUMPKIN) {
            return true;
        }
        if (block == Blocks.SUGAR_CANE) {
            return mc.world.getBlockState(pos.down()).getBlock() == Blocks.SUGAR_CANE;
        }
        if (block == Blocks.CACTUS) {
            return mc.world.getBlockState(pos.down()).getBlock() == Blocks.CACTUS;
        }

        return false;
    }

    private void replant(BlockPos pos) {
        BlockState state = mc.world.getBlockState(pos);
        Block block = state.getBlock();

        Item seed = getSeedForCrop(block);
        if (seed == null) return;

        int slot = findSeedSlot(seed);
        if (slot == -1) return;

        InventoryUtils.selectSlot(slot);

        BlockPos targetPos = pos.down();
        Vec3d hitVec = new Vec3d(targetPos.getX() + 0.5, targetPos.getY() + 1.0, targetPos.getZ() + 0.5);
        BlockHitResult hitResult = new BlockHitResult(hitVec, Direction.UP, targetPos, false);
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hitResult);
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    private Item getSeedForCrop(Block block) {
        if (block == Blocks.WHEAT) return Items.WHEAT_SEEDS;
        if (block == Blocks.CARROTS) return Items.CARROT;
        if (block == Blocks.POTATOES) return Items.POTATO;
        if (block == Blocks.BEETROOTS) return Items.BEETROOT_SEEDS;
        if (block == Blocks.NETHER_WART) return Items.NETHER_WART;
        return null;
    }

    private int findSeedSlot(Item seed) {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.getItem() == seed) {
                return i;
            }
        }
        return -1;
    }

    private BlockPos findNearestCrop() {
        if (mc.player == null) return null;

        BlockPos playerPos = mc.player.getBlockPos();
        int r = radius.getInt();
        BlockPos nearest = null;
        double nearestDist = Double.MAX_VALUE;

        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos pos = playerPos.add(x, y, z);
                    if (isGrownCrop(pos)) {
                        double dist = mc.player.getEyePos().distanceTo(Vec3d.ofCenter(pos));
                        if (dist < nearestDist) {
                            nearestDist = dist;
                            nearest = pos;
                        }
                    }
                }
            }
        }

        return nearest;
    }
}
