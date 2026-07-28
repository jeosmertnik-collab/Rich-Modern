package excel.modules.impl.movement;

import antidaunleak.api.annotation.Native;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import net.minecraft.util.math.Vec3d;
import excel.events.api.EventHandler;
import excel.events.impl.PacketEvent;
import excel.events.impl.TickEvent;
import excel.modules.impl.combat.Aura;
import excel.modules.module.ModuleStructure;
import excel.modules.module.category.ModuleCategory;
import excel.util.Instance;
import excel.util.timer.StopWatch;

import java.util.Random;

@Getter
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ElytraMotion extends ModuleStructure {

    public static Fly getInstance() {
        return Instance.get(Fly.class);
    }
    @NonFinal

    StopWatch timer = new StopWatch();
    @NonFinal
    Vec3d targetPosition = null;
    @NonFinal
    Random random = new Random();
    @NonFinal double rotationAngle = 0.0;

    public ElytraMotion() {
        super("ElytraMotion", "Управление полётом элитры", ModuleCategory.MOVEMENT);
        this.settings();
    }

    @EventHandler
    @Native(type = Native.Type.VMProtectBeginUltra)
    public void onTick(TickEvent e) {
        if (!state || mc.player == null || mc.world == null || !mc.player.isGliding()) return;

        Aura aura = Instance.get(Aura.class);

        if (aura.isState()) {
            handleAuraMotion(aura);
        }
    }

    @Native(type = Native.Type.VMProtectBeginUltra)
    private void handleAuraMotion(Aura aura) {
        if (aura.isState() && aura.target != null && mc.player.distanceTo(aura.target) < aura.getAttackrange().getValue() - 1F) {
            mc.player.setVelocity(0, 0.02, 0);
        }
    }

    @EventHandler
    public void onPacket(PacketEvent e) {
        Aura aura = Instance.get(Aura.class);
        if (aura.isState() && aura.target != null && mc.player.distanceTo(aura.target) < aura.getAttackrange().getValue() - 1F) {
            switch (e.getPacket()) {
                default -> {
                }
            }
        }
    }

    @Override
    @Native(type = Native.Type.VMProtectBeginMutation)
    public void deactivate() {
        super.deactivate();
    }
}