package rich.modules.impl.misc;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import rich.events.api.EventHandler;
import rich.events.impl.TickEvent;
import rich.modules.impl.combat.aura.Angle;
import rich.modules.impl.combat.aura.AngleConfig;
import rich.modules.impl.combat.aura.AngleConnection;
import rich.modules.module.ModuleStructure;
import rich.modules.module.category.ModuleCategory;
import rich.modules.module.setting.implement.BooleanSetting;
import rich.modules.module.setting.implement.SliderSettings;
import rich.util.math.TaskPriority;
import rich.util.timer.TimerUtil;

import java.util.Set;

public class LeafFarmer extends ModuleStructure {

    private final SliderSettings range = new SliderSettings("Диапазон", "Диапазон поиска блоков")
            .range(2, 6).setValue(4);

    private final SliderSettings delay = new SliderSettings("Задержка", "Задержка между сломами (мс)")
            .range(50, 500).setValue(100);

    private final BooleanSetting rotate = new BooleanSetting("Поворот", "Поворачиваться к блоку")
            .setValue(true);

    private BlockPos currentTarget = null;
    private final TimerUtil breakTimer = TimerUtil.create();

    private static final Set<Block> LEAF_BLOCKS = Set.of(
            Blocks.OAK_LEAVES,
            Blocks.BIRCH_LEAVES,
            Blocks.SPRUCE_LEAVES,
            Blocks.JUNGLE_LEAVES,
            Blocks.ACACIA_LEAVES,
            Blocks.DARK_OAK_LEAVES,
            Blocks.MANGROVE_LEAVES,
            Blocks.AZALEA_LEAVES,
            Blocks.FLOWERING_AZALEA_LEAVES,
            Blocks.CHERRY_LEAVES,
            Blocks.PALE_OAK_LEAVES
    );

    public LeafFarmer() {
        super("LeafFarmer", "Автоматическое фарм листьев", ModuleCategory.MISC);
        settings(range, delay, rotate);
    }

    @Override
    public void activate() {
        currentTarget = null;
        breakTimer.resetCounter();
        super.activate();
    }

    @EventHandler
    public void onTick(TickEvent event) {
        if (mc.player == null || mc.world == null) return;

        if (!isHoldingHoe()) {
            currentTarget = null;
            return;
        }

        if (currentTarget != null && !isLeafBlock(currentTarget)) {
            currentTarget = null;
        }

        if (currentTarget == null) {
            currentTarget = findNearestLeaf();
        }

        if (currentTarget == null) return;

        if (!breakTimer.hasTimeElapsed((long) delay.getValue())) return;

        if (rotate.isValue()) {
            Angle angle = Angle.fromTargetHead(
                    mc.player.getEyePos(),
                    Vec3d.ofCenter(currentTarget),
                    0.5
            );
            AngleConnection.INSTANCE.rotateTo(angle, AngleConfig.DEFAULT, TaskPriority.HIGH_IMPORTANCE_1, this);
        }

        mc.interactionManager.attackBlock(currentTarget, Direction.UP);
        mc.player.swingHand(Hand.MAIN_HAND);

        currentTarget = null;
        breakTimer.resetCounter();
    }

    private boolean isHoldingHoe() {
        if (mc.player == null) return false;
        var item = mc.player.getMainHandStack().getItem();
        return item == Items.WOODEN_HOE
                || item == Items.STONE_HOE
                || item == Items.IRON_HOE
                || item == Items.GOLDEN_HOE
                || item == Items.DIAMOND_HOE
                || item == Items.NETHERITE_HOE;
    }

    private boolean isLeafBlock(BlockPos pos) {
        return LEAF_BLOCKS.contains(mc.world.getBlockState(pos).getBlock());
    }

    private BlockPos findNearestLeaf() {
        if (mc.player == null) return null;

        BlockPos playerPos = mc.player.getBlockPos();
        int r = range.getInt();
        BlockPos nearest = null;
        double nearestDist = Double.MAX_VALUE;

        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos pos = playerPos.add(x, y, z);
                    if (isLeafBlock(pos)) {
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
