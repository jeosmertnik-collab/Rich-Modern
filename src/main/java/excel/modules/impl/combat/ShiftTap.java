package excel.modules.impl.combat;

import antidaunleak.api.annotation.Native;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import net.minecraft.client.MinecraftClient;
import excel.events.api.EventHandler;
import excel.events.impl.AttackEvent;
import excel.events.impl.TickEvent;
import excel.modules.module.ModuleStructure;
import excel.modules.module.category.ModuleCategory;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ShiftTap extends ModuleStructure {

    @NonFinal
    long shiftTapEndTime = 0;
    @NonFinal
    boolean isModuleControllingSneak = false;
    @NonFinal
    int shiftTapDuration = 100;

    MinecraftClient mc = MinecraftClient.getInstance();

    public ShiftTap() {
        super("ShiftTap", "Автоматический крит при приседании", ModuleCategory.COMBAT);
    }

    @Native(type = Native.Type.VMProtectBeginMutation)
    private void startShiftTap() {
        shiftTapEndTime = System.currentTimeMillis() + 25;
        if (!isModuleControllingSneak) {
            mc.options.sneakKey.setPressed(true);
            isModuleControllingSneak = true;
        }
    }

    @Native(type = Native.Type.VMProtectBeginMutation)
    private void stopShiftTap() {
        if (isModuleControllingSneak) {
            mc.options.sneakKey.setPressed(false);
            isModuleControllingSneak = false;
        }
    }

    @EventHandler
    @Native(type = Native.Type.VMProtectBeginMutation)
    public void onAttack(AttackEvent event) {
        if (mc.player == null) {
            return;
        }
        startShiftTap();
    }

    @EventHandler
    public void onTick(TickEvent event) {
        if (mc.player == null || mc.player.isSpectator()) {
            stopShiftTap();
            return;
        }

        long currentTime = System.currentTimeMillis();
        if (isModuleControllingSneak && currentTime > shiftTapEndTime) {
            stopShiftTap();
        }
    }
}