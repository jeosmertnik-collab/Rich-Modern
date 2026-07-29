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
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class Breadcrumbs extends ModuleStructure implements IMinecraft {

    final ColorSetting lineColor = new ColorSetting("Цвет линии", "Цвет трейла")
            .value(0xFF64B5F6);
    final SliderSettings maxTrails = new SliderSettings("Длина трейла", "Максимум точек на снаряд")
            .range(10f, 200f).setValue(80f);
    final BooleanSetting showDistance = new BooleanSetting("Дистанция", "Показывать расстояние полёта")
            .setValue(true);

    private final Map<Entity, List<Vec3d>> trails = new HashMap<>();
    private final Map<Entity, Integer> removalTimer = new HashMap<>();

    public Breadcrumbs() {
        super("Breadcrumbs", "Траектория полёта снарядов", ModuleCategory.RENDER);
        settings(lineColor, maxTrails, showDistance);
    }

    @Override
    public void activate() {
        trails.clear();
        removalTimer.clear();
        super.activate();
    }

    @Override
    public void deactivate() {
        trails.clear();
        removalTimer.clear();
        super.deactivate();
    }

    @EventHandler
    public void onWorldRender(WorldRenderEvent e) {
        if (mc.player == null || mc.world == null) return;

        float tickDelta = e.getPartialTicks();
        MatrixStack stack = e.getStack();
        int lineColorVal = lineColor.getColor();

        updateTrails(tickDelta);

        for (Entity entity : mc.world.getEntities()) {
            if (!isProjectile(entity)) continue;
            List<Vec3d> trail = trails.get(entity);
            if (trail == null || trail.size() < 2) continue;

            boolean alive = entity.isAlive() && !entity.isRemoved();
            int alpha = alive ? 255 : (int) (200 * Math.max(0, 1 - (removalTimer.getOrDefault(entity, 0) / 40f)));

            for (int i = 1; i < trail.size(); i++) {
                Vec3d prev = trail.get(i - 1);
                Vec3d cur = trail.get(i);
                float width = 1.5f + ((float) i / trail.size()) * 2f;
                int col = (alpha << 24) | (lineColorVal & 0xFFFFFF);
                int colFade = ((int) (alpha * ((float) i / trail.size())) << 24) | (lineColorVal & 0xFFFFFF);
                Render3D.drawLineGradient(prev, cur, col, colFade, width, false);
            }

            if (showDistance.isValue() && alive) {
                Vec3d first = trail.getFirst();
                Vec3d last = trail.getLast();
                double dist = first.distanceTo(last);
                if (dist > 0.5) {
                    stack.push();
                    Vec3d camPos = Render3D.lastCameraPos;
                    stack.translate(last.x - camPos.x, last.y - camPos.y + 0.3, last.z - camPos.z);
                    stack.multiply(Render3D.lastCameraRotation);
                    float s = -0.02f;
                    stack.scale(s, s, s);
                    String distStr = String.format("%.1f m", dist);
                    Fonts.BOLD.drawCentered(distStr, 0, 0, 18f, (int) (255 * 0.9f) << 24 | 0xFFFFFF);
                    stack.pop();
                }
            }
        }
    }

    private void updateTrails(float tickDelta) {
        for (Entity entity : mc.world.getEntities()) {
            if (!isProjectile(entity)) continue;
            List<Vec3d> trail = trails.computeIfAbsent(entity, k -> new ArrayList<>());
            Vec3d pos = entity.getLerpedPos(tickDelta);
            if (trail.isEmpty() || pos.distanceTo(trail.getLast()) > 0.05) {
                trail.add(pos);
                if (trail.size() > maxTrails.getValue()) trail.removeFirst();
            }
            removalTimer.put(entity, Math.max(0, removalTimer.getOrDefault(entity, 0) - 1));
        }

        Iterator<Map.Entry<Entity, List<Vec3d>>> it = trails.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Entity, List<Vec3d>> entry = it.next();
            Entity entity = entry.getKey();
            if (entity.isRemoved() || !entity.isAlive()) {
                int timer = removalTimer.getOrDefault(entity, 0);
                if (timer > 40) {
                    it.remove();
                    removalTimer.remove(entity);
                } else {
                    removalTimer.put(entity, timer + 1);
                }
            }
        }
    }

    private boolean isProjectile(Entity entity) {
        return entity instanceof ArrowEntity || entity instanceof TridentEntity
                || entity instanceof ThrownEntity || entity instanceof EnderPearlEntity
                || entity instanceof PotionEntity;
    }
}
