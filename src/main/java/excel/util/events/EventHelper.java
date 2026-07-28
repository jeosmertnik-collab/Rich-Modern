package excel.util.events;

import lombok.experimental.UtilityClass;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import excel.events.api.EventManager;
import excel.events.impl.TickEvent;
import excel.events.impl.WorldRenderEvent;
import excel.IMinecraft;

@UtilityClass
public class EventHelper implements IMinecraft {

    public boolean isOnGround() {
        return mc.player != null && mc.player.isOnGround();
    }

    public boolean isMoving() {
        if (mc.player == null) return false;
        Vec2f movement = mc.player.input.getMovementInput();
        return movement.x != 0 || movement.y != 0;
    }

    public boolean isMovingForward() {
        if (mc.player == null) return false;
        return mc.player.input.getMovementInput().y > 0;
    }

    public boolean isMovingBackward() {
        if (mc.player == null) return false;
        return mc.player.input.getMovementInput().y < 0;
    }

    public boolean isStrafing() {
        if (mc.player == null) return false;
        return mc.player.input.getMovementInput().x != 0;
    }

    public boolean isInLiquid() {
        return mc.player != null && (mc.player.isSubmergedInWater() || mc.player.isInLava());
    }

    public boolean isFallFlying() {
        return mc.player != null && mc.player.isGliding();
    }

    public boolean isSneaking() {
        return mc.player != null && mc.player.isSneaking();
    }

    public boolean isSprinting() {
        return mc.player != null && mc.player.isSprinting();
    }

    public boolean isUsingItem() {
        return mc.player != null && mc.player.isUsingItem();
    }

    public boolean isHandActive() {
        return mc.player != null && mc.player.handSwinging;
    }

    public float getSpeed() {
        if (mc.player == null) return 0;
        double dx = mc.player.getX() - mc.player.lastX;
        double dz = mc.player.getZ() - mc.player.lastZ;
        float yaw = mc.player.getYaw();
        float forward = (float) (dx * Math.cos(Math.toRadians(-yaw)) - dz * Math.sin(Math.toRadians(-yaw)));
        float strafe = (float) (dx * Math.sin(Math.toRadians(-yaw)) + dz * Math.cos(Math.toRadians(-yaw)));
        return (float) Math.sqrt(forward * forward + strafe * strafe) * 20f;
    }

    public double getDirectionalSpeed() {
        if (mc.player == null) return 0;
        double dx = mc.player.getX() - mc.player.lastX;
        double dz = mc.player.getZ() - mc.player.lastZ;
        return Math.sqrt(dx * dx + dz * dz) * 20.0;
    }

    public float getHealth() {
        return mc.player != null ? mc.player.getHealth() : 0;
    }

    public float getHealth(LivingEntity entity) {
        return entity.getHealth();
    }

    public float getMaxHealth() {
        return mc.player != null ? mc.player.getMaxHealth() : 20;
    }

    public float getHealthPercent() {
        if (mc.player == null) return 0;
        return mc.player.getHealth() / mc.player.getMaxHealth();
    }

    public int getArmor() {
        return mc.player != null ? mc.player.getArmor() : 0;
    }

    public float getDistanceTo(LivingEntity entity) {
        if (mc.player == null || entity == null) return Float.MAX_VALUE;
        return mc.player.distanceTo(entity);
    }

    public float getDistanceTo(double x, double y, double z) {
        if (mc.player == null) return Float.MAX_VALUE;
        double dx = mc.player.getX() - x;
        double dy = mc.player.getY() - y;
        double dz = mc.player.getZ() - z;
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public boolean canSeeTarget(LivingEntity target) {
        return mc.player != null && mc.player.canSee(target);
    }

    public int getPing() {
        if (mc.getNetworkHandler() == null || mc.player == null) return 0;
        var entry = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
        return entry != null ? entry.getLatency() : 0;
    }

    public int getPing(LivingEntity entity) {
        if (mc.getNetworkHandler() == null) return 0;
        var entry = mc.getNetworkHandler().getPlayerListEntry(entity.getUuid());
        return entry != null ? entry.getLatency() : 0;
    }

    public float getAngleTo(Vec3d target) {
        if (mc.player == null) return 0;
        Vec3d eyePos = mc.player.getEyePos();
        double dx = target.x - eyePos.x;
        double dz = target.z - eyePos.z;
        return (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
    }

    public float wrapAngle(float angle) {
        return MathHelper.wrapDegrees(angle);
    }

    public int getBlockLightLevel() {
        if (mc.world == null || mc.player == null) return 15;
        return mc.world.getLightLevel(mc.player.getBlockPos());
    }

    public int getTickRate() {
        return 20;
    }
}
