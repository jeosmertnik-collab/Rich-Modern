package excel.modules.impl.misc;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.entry.RegistryEntry;
import excel.events.api.EventHandler;
import excel.events.impl.TickEvent;
import excel.modules.module.ModuleStructure;
import excel.modules.module.category.ModuleCategory;
import excel.modules.module.setting.implement.BooleanSetting;
import excel.modules.module.setting.implement.SliderSettings;
import excel.util.ai.TtsHelper;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Getter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class VoiceAlert extends ModuleStructure {

    BooleanSetting sayAll = new BooleanSetting("Говорить всё", "Говорить все оповещения (HP, еда, тотем)")
            .setValue(false);

    SliderSettings hpThreshold = new SliderSettings("Порог HP", "Говорить когда HP ниже")
            .range(2f, 18f).setValue(8f);

    BooleanSetting badPotionAlert = new BooleanSetting("Дебафы", "Говорить при плохих эффектах")
            .setValue(true);

    BooleanSetting lowFoodAlert = new BooleanSetting("Мало еды", "Говорить когда голодный")
            .setValue(true);

    SliderSettings foodThreshold = new SliderSettings("Порог еды", "Говорить когда еда ниже")
            .range(2f, 16f).setValue(6f);

    BooleanSetting totemAlert = new BooleanSetting("Тотем", "Говорить когда тотем сработал")
            .setValue(true);

    float lastHealth = -1;
    long lastHpSpeakTime = 0;
    long lastFoodSpeakTime = 0;
    final Map<StatusEffect, Long> lastEffects = new ConcurrentHashMap<>();
    boolean wasTotemActive = false;

    private static final Set<RegistryEntry<StatusEffect>> BAD_EFFECTS = new HashSet<>(Arrays.asList(
            StatusEffects.POISON,
            StatusEffects.WITHER,
            StatusEffects.WEAKNESS,
            StatusEffects.MINING_FATIGUE,
            StatusEffects.NAUSEA,
            StatusEffects.BLINDNESS,
            StatusEffects.DARKNESS,
            StatusEffects.SLOWNESS,
            StatusEffects.HUNGER,
            StatusEffects.GLOWING,
            StatusEffects.LEVITATION,
            StatusEffects.INSTANT_DAMAGE
    ));

    private static final Map<RegistryEntry<StatusEffect>, String> EFFECT_NAMES = new HashMap<>();
    static {
        EFFECT_NAMES.put(StatusEffects.POISON, "Отравление");
        EFFECT_NAMES.put(StatusEffects.WITHER, "Иссушение");
        EFFECT_NAMES.put(StatusEffects.WEAKNESS, "Слабость");
        EFFECT_NAMES.put(StatusEffects.MINING_FATIGUE, "Усталость копателя");
        EFFECT_NAMES.put(StatusEffects.NAUSEA, "Тошнота");
        EFFECT_NAMES.put(StatusEffects.BLINDNESS, "Слепота");
        EFFECT_NAMES.put(StatusEffects.DARKNESS, "Тьма");
        EFFECT_NAMES.put(StatusEffects.SLOWNESS, "Замедление");
        EFFECT_NAMES.put(StatusEffects.HUNGER, "Голод");
        EFFECT_NAMES.put(StatusEffects.GLOWING, "Свечение");
        EFFECT_NAMES.put(StatusEffects.LEVITATION, "Левитация");
        EFFECT_NAMES.put(StatusEffects.INSTANT_DAMAGE, "Мгновенный урон");
    }

    public VoiceAlert() {
        super("VoiceAlert", "Голосовые оповещения (дебафы и урон)", ModuleCategory.MISC);
        settings(sayAll, hpThreshold, badPotionAlert, lowFoodAlert, foodThreshold, totemAlert);
    }

    @Override
    public void activate() {
        lastHealth = -1;
        lastEffects.clear();
        wasTotemActive = false;
    }

    @Override
    public void deactivate() {
        lastEffects.clear();
    }

    @EventHandler
    public void onTick(TickEvent e) {
        if (mc.player == null || mc.world == null) return;

        checkBadEffects();
        checkHealth();

        if (sayAll.isValue()) {
            checkFood();
            checkTotem();
        }
    }

    private void checkHealth() {
        float health = mc.player.getHealth();
        float threshold = hpThreshold.getValue();

        if (health <= threshold && health < mc.player.getMaxHealth()) {
            long now = System.currentTimeMillis();
            if (now - lastHpSpeakTime > 8000) {
                lastHpSpeakTime = now;
                int hp = Math.round(health);
                TtsHelper.speak("Внимание, мало хп. Осталось " + hp + " сердец.");
            }
        }
        lastHealth = health;
    }

    private void checkBadEffects() {
        if (!badPotionAlert.isValue()) return;

        Collection<StatusEffectInstance> currentEffects = mc.player.getStatusEffects();
        Set<StatusEffect> currentSet = new HashSet<>();

        for (StatusEffectInstance inst : currentEffects) {
            StatusEffect effect = inst.getEffectType().value();
            currentSet.add(effect);

            RegistryEntry<StatusEffect> regEntry = inst.getEffectType();
            if (BAD_EFFECTS.contains(regEntry)) {
                long lastTime = lastEffects.getOrDefault(effect, 0L);
                long now = System.currentTimeMillis();
                if (now - lastTime > 15000) {
                    lastEffects.put(effect, now);
                    String name = EFFECT_NAMES.getOrDefault(regEntry, effect.getName().getString());
                    int level = inst.getAmplifier() + 1;
                    String levelStr = level > 1 ? ", уровень " + level : "";
                    TtsHelper.speak("Наложен эффект: " + name + levelStr);
                }
            }
        }

        lastEffects.keySet().removeIf(e -> !currentSet.contains(e));
    }

    private void checkFood() {
        if (!lowFoodAlert.isValue()) return;
        if (mc.player.getHungerManager().getFoodLevel() <= foodThreshold.getValue()) {
            long now = System.currentTimeMillis();
            if (now - lastFoodSpeakTime > 20000) {
                lastFoodSpeakTime = now;
                TtsHelper.speak("Мало еды. Поешь.");
            }
        }
    }

    private void checkTotem() {
        if (!totemAlert.isValue()) return;
        boolean hasTotem = mc.player.getOffHandStack().getItem() ==
                net.minecraft.item.Items.TOTEM_OF_UNDYING;

        if (wasTotemActive && !hasTotem) {
            TtsHelper.speak("Тотем сработал!");
        }
        wasTotemActive = hasTotem;
    }
}
