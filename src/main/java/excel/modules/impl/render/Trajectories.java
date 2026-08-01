package excel.modules.impl.render;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.item.*;
import net.minecraft.util.math.Vec3d;
import excel.IMinecraft;
import excel.events.api.EventHandler;
import excel.events.impl.WorldRenderEvent;
import excel.modules.module.ModuleStructure;
import excel.modules.module.category.ModuleCategory;
import excel.modules.module.setting.implement.BooleanSetting;
import excel.modules.module.setting.implement.ColorSetting;
import excel.modules.module.setting.implement.SliderSettings;
import excel.util.render.Render3D;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class Trajectories extends ModuleStructure implements IMinecraft {

    final ColorSetting lineColor = new ColorSetting("Цвет линии", "Цвет траектории")
            .value(0xFFFFFFFF);

    final ColorSetting landingColor = new ColorSetting("Цвет точки", "Цвет точки приземления")
            .value(0xFFFF6464);

    final SliderSettings maxDistance = new SliderSettings("Дистанция", "Максимальная дистанция отрисовки")
            .range(10f, 200f).setValue(100f);

    final BooleanSetting showLanding = new BooleanSetting("Точка приземления", "Показывать точку приземления")
            .setValue(true);

    public Trajectories() {
        super("Trajectories", "Показывает траекторию полёта снарядов", ModuleCategory.RENDER);
        settings(lineColor, landingColor, maxDistance, showLanding);
    }

    @EventHandler
    public void onWorldRender(WorldRenderEvent e) {
        if (mc.player == null || mc.world == null) return;

        ItemStack stack = mc.player.getMainHandStack();
        TrajectoryData data = getTrajectoryData(stack);
        if (data == null) return;

        Vec3d eyePos = mc.player.getCameraPosVec(e.getPartialTicks());
        Vec3d lookVec = mc.player.getRotationVec(e.getPartialTicks());

        simulateAndRender(eyePos, lookVec, data);
    }

    private void simulateAndRender(Vec3d start, Vec3d direction, TrajectoryData data) {
        Vec3d vel = direction.multiply(data.initialSpeed);
        Vec3d pos = start;

        float maxDist = maxDistance.getValue();
        float groundY = (float) mc.player.getY() - 1f;
        int lineColorValue = lineColor.getColor();

        Vec3d landing = null;
        Vec3d prevPos = start;

        for (int tick = 0; tick < 200; tick++) {
            vel = new Vec3d(vel.x * data.drag, vel.y * data.drag - data.gravity, vel.z * data.drag);
            pos = pos.add(vel);

            if (pos.distanceTo(start) > maxDist) break;

            if (tick % 2 == 0) {
                Render3D.drawLine(prevPos, pos, lineColorValue, 1.5f, false);
            }

            if (pos.y <= groundY && landing == null) {
                double t = (groundY - prevPos.y) / (pos.y - prevPos.y);
                landing = prevPos.add(pos.subtract(prevPos).multiply(t));
            }

            prevPos = pos;
        }

        if (showLanding.isValue() && landing != null && landing.distanceTo(start) <= maxDist) {
            Render3D.drawRadiusCircle(landing, 0.4f, landingColor.getColor());
        }
    }

    private TrajectoryData getTrajectoryData(ItemStack stack) {
        if (stack.isEmpty()) return null;
        Item item = stack.getItem();

        if (item instanceof BowItem) {
            if (!mc.player.isUsingItem()) return null;
            float charge = Math.min(1f, mc.player.getItemUseTime() / 20f);
            return new TrajectoryData(3.0f * charge, 0.05f, 0.99f);
        }

        if (item instanceof CrossbowItem) {
            if (!((CrossbowItem) item).isCharged(stack)) return null;
            return new TrajectoryData(3.0f, 0.05f, 0.99f);
        }

        if (item instanceof TridentItem) {
            return new TrajectoryData(2.5f, 0.05f, 0.99f);
        }

        if (item == Items.SNOWBALL || item == Items.EGG || item == Items.ENDER_PEARL) {
            return new TrajectoryData(1.5f, 0.03f, 0.99f);
        }

        if (item == Items.SPLASH_POTION || item == Items.LINGERING_POTION) {
            return new TrajectoryData(0.5f, 0.05f, 0.99f);
        }

        if (item == Items.FISHING_ROD) {
            return new TrajectoryData(1.5f, 0.03f, 0.99f);
        }

        return null;
    }

    private record TrajectoryData(float initialSpeed, float gravity, float drag) {
    }
}
