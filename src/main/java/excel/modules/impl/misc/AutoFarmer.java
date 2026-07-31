package excel.modules.impl.misc;

import net.minecraft.block.*;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import excel.events.api.EventHandler;
import excel.events.impl.TickEvent;
import excel.modules.module.ModuleStructure;
import excel.modules.module.category.ModuleCategory;
import excel.modules.module.setting.implement.BooleanSetting;
import excel.modules.module.setting.implement.SliderSettings;
import excel.util.inventory.InventoryUtils;
import excel.util.timer.TimerUtil;

public class AutoFarmer extends ModuleStructure {

    private final SliderSettings radius = new SliderSettings("Радиус", "Радиус поиска культур")
            .range(2, 6).setValue(4);

    private final SliderSettings delay = new SliderSettings("Задержка (мс)", "Задержка между действиями")
            .range(50, 500).setValue(150);

    private final BooleanSetting autoReplant = new BooleanSetting("Auto Replant", "Сажать заново после слома")
            .setValue(true);

    private final BooleanSetting curveWalk = new BooleanSetting("Curve Walk", "Ходьба по кривой (обходит античит)")
            .setValue(true);

    private BlockPos currentTarget = null;
    private final TimerUtil actionTimer = TimerUtil.create();
    private float curvePhase = 0f;
    private int phase = 0;

    public AutoFarmer() {
        super("AutoFarmer", "Авто-ферма с обходом античита", ModuleCategory.MISC);
        settings(radius, delay, autoReplant, curveWalk);
    }

    @Override
    public void activate() {
        currentTarget = null;
        actionTimer.resetCounter();
        curvePhase = 0f;
        phase = 0;
    }

    @EventHandler
    public void onTick(TickEvent event) {
        if (mc.player == null || mc.world == null) return;

        if (phase == 0) {
            if (currentTarget != null && !isGrownCrop(currentTarget)) {
                currentTarget = null;
            }

            if (currentTarget == null) {
                currentTarget = findNearestCrop();
            }

            if (currentTarget == null) {
                if (curveWalk.isValue()) {
                    doCurveWalk();
                }
                return;
            }

            Vec3d targetCenter = Vec3d.ofCenter(currentTarget);
            double dist = mc.player.getEyePos().distanceTo(targetCenter);

            if (dist > 4.5) {
                moveToward(targetCenter);
                return;
            }

            if (!actionTimer.hasTimeElapsed((long) delay.getValue())) return;

            phase = 1;
            breakTarget(currentTarget);
        } else if (phase == 1) {
            if (autoReplant.isValue()) {
                phase = 2;
                replant(currentTarget);
            } else {
                currentTarget = null;
                actionTimer.resetCounter();
                phase = 0;
            }
        } else if (phase == 2) {
            currentTarget = null;
            actionTimer.resetCounter();
            phase = 0;
        }
    }

    private void breakTarget(BlockPos pos) {
        Vec3d breakVec = new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        facePosition(breakVec);
        mc.interactionManager.updateBlockBreakingProgress(pos, Direction.UP);
        mc.interactionManager.attackBlock(pos, Direction.UP);
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    private void moveToward(Vec3d target) {
        if (mc.player == null) return;

        Vec3d diff = target.subtract(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        double horizontalDist = MathHelper.sqrt((float) (diff.x * diff.x + diff.z * diff.z));
        if (horizontalDist < 0.1) return;

        double speed = 0.6;

        if (curveWalk.isValue()) {
            curvePhase += 0.15f;
            float sin = MathHelper.sin(curvePhase);
            diff = diff.add(sin * 0.3, 0, MathHelper.cos(curvePhase) * 0.3);
        }

        double motionX = (diff.x / horizontalDist) * speed;
        double motionZ = (diff.z / horizontalDist) * speed;

        mc.player.setVelocity(motionX, mc.player.getVelocity().y, motionZ);

        float targetYaw = (float) Math.toDegrees(Math.atan2(-diff.x, diff.z));
        mc.player.setYaw(targetYaw);
        mc.player.setHeadYaw(targetYaw);
    }

    private void doCurveWalk() {
        if (mc.player == null) return;
        curvePhase += 0.1f;
        float sin = MathHelper.sin(curvePhase);
        float cos = MathHelper.cos(curvePhase);
        double mx = sin * 0.1;
        double mz = cos * 0.1;
        mc.player.setVelocity(mx, mc.player.getVelocity().y, mz);

        mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                mc.player.getX() + mx, mc.player.getY(), mc.player.getZ() + mz, false, false));
    }

    private void facePosition(Vec3d target) {
        Vec3d eyes = mc.player.getCameraPosVec(1f);
        double dx = target.x - eyes.x;
        double dy = target.y - eyes.y;
        double dz = target.z - eyes.z;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, dist));
        mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(yaw, pitch, mc.player.isOnGround(), false));
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
        facePosition(hitVec);
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
