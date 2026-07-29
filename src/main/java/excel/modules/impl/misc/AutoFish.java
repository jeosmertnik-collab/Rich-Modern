package excel.modules.impl.misc;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.sound.SoundEvents;
import excel.events.api.EventHandler;
import excel.events.impl.PacketEvent;
import excel.events.impl.TickEvent;
import excel.modules.module.ModuleStructure;
import excel.modules.module.category.ModuleCategory;
import excel.modules.module.setting.implement.BooleanSetting;
import excel.modules.module.setting.implement.SliderSettings;
import excel.util.timer.StopWatch;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class AutoFish extends ModuleStructure {

    BooleanSetting autoCast = new BooleanSetting("Auto Cast", "Auto recast after catching")
            .setValue(true);

    SliderSettings delay = new SliderSettings("Delay", "Delay between reel-in and recast in ms")
            .range(100f, 1000f).setValue(300f);

    BooleanSetting soundCheck = new BooleanSetting("Sound Check", "Enable/disable sound detection")
            .setValue(true);

    final StopWatch reelTimer = new StopWatch();
    final StopWatch recastTimer = new StopWatch();
    boolean pendingRecast;
    double prevBobberY = -1;

    public AutoFish() {
        super("AutoFish", "Automatically fishes when the bobber splashes", ModuleCategory.MISC);
        settings(autoCast, delay, soundCheck);
    }

    @Override
    public void activate() {
        reelTimer.reset();
        pendingRecast = false;
        prevBobberY = -1;
    }

    @EventHandler
    public void onPacket(PacketEvent event) {
        if (event.getType() != PacketEvent.Type.RECEIVE) return;
        if (mc.player == null || mc.world == null) return;
        if (!soundCheck.isValue()) return;
        if (!hasFishingRod()) return;

        if (event.getPacket() instanceof PlaySoundS2CPacket packet) {
            if (packet.getSound().value() == SoundEvents.ENTITY_FISHING_BOBBER_SPLASH) {
                if (reelTimer.finished(1500)) {
                    handleCatch();
                }
            }
        }
    }

    @EventHandler
    public void onTick(TickEvent e) {
        if (mc.player == null || mc.world == null) return;

        if (pendingRecast && recastTimer.finished(delay.getValue())) {
            doCast();
        }

        if (!soundCheck.isValue()) {
            checkEntityBite();
        }
    }

    private void checkEntityBite() {
        FishingBobberEntity bobber = findBobber();
        if (bobber == null) {
            prevBobberY = -1;
            return;
        }

        double y = bobber.getY();
        if (prevBobberY != -1 && prevBobberY - y > 0.3 && reelTimer.finished(1500)) {
            handleCatch();
        }
        prevBobberY = y;
    }

    private FishingBobberEntity findBobber() {
        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof FishingBobberEntity fb && fb.getOwner() == mc.player) {
                return fb;
            }
        }
        return null;
    }

    private boolean hasFishingRod() {
        return mc.player.getMainHandStack().getItem() == Items.FISHING_ROD
                || mc.player.getOffHandStack().getItem() == Items.FISHING_ROD;
    }

    private void handleCatch() {
        mc.doItemUse();
        reelTimer.reset();

        if (autoCast.isValue()) {
            pendingRecast = true;
            recastTimer.reset();
        }
    }

    private void doCast() {
        if (!hasFishingRod()) return;
        mc.doItemUse();
        pendingRecast = false;
        reelTimer.reset();
    }
}
