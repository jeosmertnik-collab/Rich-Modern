package excel.modules.impl.render;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.TridentEntity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.entity.projectile.thrown.PotionEntity;
import net.minecraft.entity.projectile.thrown.ThrownEntity;
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
import excel.util.render.font.Fonts;

import java.util.ArrayList;
import java.util.List;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class Trajectories extends ModuleStructure implements IMinecraft {

    final ColorSetting lineColor = new ColorSetting("Цвет линии", "Цвет траектории")
            .value(0xFFFFFFFF);

    final ColorSetting landingColor = new ColorSetting("Цвет точки", "Цвет точки приземления")
            .value(0xFFFF6464);

    final ColorSetting projectileLineColor = new ColorSetting("Цвет линии снаряда", "Цвет траектории летящих снарядов")
            .value(0xFF64B5F6);

    final SliderSettings maxDistance = new SliderSettings("Дистанция", "Максимальная дистанция отрисовки")
            .range(10f, 200f).setValue(100f);

    final BooleanSetting showLanding = new BooleanSetting("Точка приземления", "Показывать точку приземления")
            .setValue(true);

    final BooleanSetting showProjectileTrajectory = new BooleanSetting("Снаряды в полёте", "Показывать траекторию летящих снарядов")
            .setValue(true);

    final BooleanSetting showIcon = new BooleanSetting("Иконка", "Показывать иконку предмета")
            .setValue(true);

    final BooleanSetting showDistance = new BooleanSetting("Дистанция", "Показывать дистанцию до снаряда")
            .setValue(true);

    public Trajectories() {
        super("Trajectories", "Показывает траекторию полёта снарядов", ModuleCategory.RENDER);
        settings(lineColor, landingColor, projectileLineColor, maxDistance, showLanding, showProjectileTrajectory, showIcon, showDistance);
    }

    @EventHandler
    public void onWorldRender(WorldRenderEvent e) {
        if (mc.player == null || mc.world == null) return;

        ItemStack stack = mc.player.getMainHandStack();
        TrajectoryData data = getTrajectoryData(stack);
        if (data != null) {
            Vec3d eyePos = mc.player.getCameraPosVec(e.getPartialTicks());
            Vec3d lookVec = mc.player.getRotationVec(e.getPartialTicks());
            simulateAndRender(eyePos, lookVec, data, lineColor.getColor());
        }

        if (showProjectileTrajectory.isValue()) {
            float tickDelta = e.getPartialTicks();
            MatrixStack matrixStack = e.getStack();
            for (Entity entity : mc.world.getEntities()) {
                if (!isProjectile(entity)) continue;
                renderInFlightProjectile(entity, tickDelta, matrixStack);
            }
        }
    }

    private void simulateAndRender(Vec3d start, Vec3d direction, TrajectoryData data, int color) {
        Vec3d vel = direction.multiply(data.initialSpeed);
        Vec3d pos = start;

        float maxDist = maxDistance.getValue();
        float groundY = (float) mc.player.getY() - 1f;

        Vec3d landing = null;
        Vec3d prevPos = start;

        for (int tick = 0; tick < 200; tick++) {
            vel = new Vec3d(vel.x * data.drag, vel.y * data.drag - data.gravity, vel.z * data.drag);
            pos = pos.add(vel);

            if (pos.distanceTo(start) > maxDist) break;

            if (tick % 2 == 0) {
                Render3D.drawLine(prevPos, pos, color, 1.5f, false);
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

    private void renderInFlightProjectile(Entity entity, float tickDelta, MatrixStack matrixStack) {
        Vec3d pos = entity.getLerpedPos(tickDelta);
        Vec3d velocity = entity.getVelocity();
        double speed = velocity.length();

        if (speed < 0.01) return;

        TrajectoryData data = getEntityTrajectoryData(entity);
        if (data == null) return;

        float maxDist = maxDistance.getValue();
        int lineColorValue = projectileLineColor.getColor();

        Vec3d simPos = pos;
        Vec3d simVel = velocity;
        Vec3d landing = null;
        Vec3d prevPos = pos;

        List<Vec3d> path = new ArrayList<>();
        path.add(pos);

        for (int tick = 0; tick < 200; tick++) {
            simVel = new Vec3d(simVel.x * data.drag, simVel.y * data.drag - data.gravity, simVel.z * data.drag);
            simPos = simPos.add(simVel);

            if (simPos.distanceTo(pos) > maxDist * 2) break;

            path.add(simPos);

            if (mc.world != null && simPos.y <= mc.world.getBottomY() && landing == null) {
                double t = (mc.world.getBottomY() - prevPos.y) / (simPos.y - prevPos.y);
                landing = prevPos.add(simPos.subtract(prevPos).multiply(t));
            }

            if (landing != null) break;
            prevPos = simPos;
            if (speed < 0.01 && simVel.length() < 0.01) break;
        }

        for (int i = 1; i < path.size(); i++) {
            Render3D.drawLine(path.get(i - 1), path.get(i), lineColorValue, 1.5f, false);
        }

        if (showLanding.isValue() && landing != null && landing.distanceTo(pos) <= maxDist * 2) {
            Render3D.drawRadiusCircle(landing, 0.4f, landingColor.getColor());
        }

        if (showIcon.isValue() || showDistance.isValue()) {
            renderProjectileLabel(entity, pos, matrixStack);
        }
    }

    private void renderProjectileLabel(Entity entity, Vec3d pos, MatrixStack matrixStack) {
        Vec3d camPos = Render3D.lastCameraPos;
        if (camPos == null) return;

        double dist = camPos.distanceTo(pos);

        matrixStack.push();
        matrixStack.translate(pos.x - camPos.x, pos.y - camPos.y + 0.5, pos.z - camPos.z);
        matrixStack.multiply(Render3D.lastCameraRotation);
        float s = -0.025f;
        matrixStack.scale(s, s, s);

        float yOff = 0;

        if (showIcon.isValue()) {
            String label = getEntityLabel(entity);
            Fonts.BOLD.drawCentered(label, 0, yOff, 20f, 0xFFFFFFFF);
            yOff -= 22;
        }

        if (showDistance.isValue()) {
            String distStr = String.format("%.1f m", dist);
            Fonts.BOLD.drawCentered(distStr, 0, yOff, 16f, 0xCCFFFFFF);
        }

        matrixStack.pop();
    }

    private String getEntityLabel(Entity entity) {
        if (entity instanceof TridentEntity) {
            return "\uD83D\uDD32 \u0422\u0440\u0438\u0437\u0443\u0431\u0435\u0446";
        }
        if (entity instanceof EnderPearlEntity) {
            return "\u25CF \u0416\u0435\u043C\u0447\u0443\u0433";
        }
        if (entity instanceof ArrowEntity) {
            return "\u2192 \u0421\u0442\u0440\u0435\u043B\u0430";
        }
        if (entity instanceof PotionEntity) {
            return "\u25D8 \u0417\u0435\u043B\u044C\u0435";
        }
        if (entity instanceof ThrownEntity) {
            return "\u25CB \u0421\u043D\u0430\u0440\u044F\u0434";
        }
        return "\u25E6 \u0421\u043D\u0430\u0440\u044F\u0434";
    }

    private TrajectoryData getEntityTrajectoryData(Entity entity) {
        if (entity instanceof ArrowEntity) {
            return new TrajectoryData(0.05f, 0.99f);
        }
        if (entity instanceof TridentEntity) {
            return new TrajectoryData(0.05f, 0.99f);
        }
        if (entity instanceof EnderPearlEntity) {
            return new TrajectoryData(0.03f, 0.99f);
        }
        if (entity instanceof PotionEntity) {
            return new TrajectoryData(0.05f, 0.99f);
        }
        if (entity instanceof ThrownEntity) {
            return new TrajectoryData(0.03f, 0.99f);
        }
        return null;
    }

    private boolean isProjectile(Entity entity) {
        return entity instanceof ArrowEntity || entity instanceof TridentEntity
                || entity instanceof ThrownEntity || entity instanceof EnderPearlEntity
                || entity instanceof PotionEntity;
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
        TrajectoryData(float gravity, float drag) {
            this(0, gravity, drag);
        }
    }
}
