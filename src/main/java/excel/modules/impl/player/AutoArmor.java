package excel.modules.impl.player;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import excel.events.api.EventHandler;
import excel.events.impl.TickEvent;
import excel.modules.module.ModuleStructure;
import excel.modules.module.category.ModuleCategory;
import excel.modules.module.setting.implement.BooleanSetting;
import excel.modules.module.setting.implement.SliderSettings;
import excel.util.Instance;

import java.util.Map;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class AutoArmor extends ModuleStructure {

    public static AutoArmor getInstance() {
        return Instance.get(AutoArmor.class);
    }

    BooleanSetting onlyHelmet = new BooleanSetting("Только шлем", "Менять только шлем").setValue(false);
    BooleanSetting onlyChest = new BooleanSetting("Только нагрудник", "Менять только нагрудник").setValue(false);
    BooleanSetting onlyLegs = new BooleanSetting("Только поножи", "Менять только поножи").setValue(false);
    BooleanSetting onlyBoots = new BooleanSetting("Только ботинки", "Менять только ботинки").setValue(false);
    SliderSettings delay = new SliderSettings("Задержка (мс)", "Задержка между свопами").setValue(50).range(0, 500);

    private static final Map<Item, Integer> ARMOR_PROTECTION = Map.ofEntries(
            Map.entry(Items.LEATHER_HELMET, 1), Map.entry(Items.LEATHER_CHESTPLATE, 3),
            Map.entry(Items.LEATHER_LEGGINGS, 2), Map.entry(Items.LEATHER_BOOTS, 1),
            Map.entry(Items.CHAINMAIL_HELMET, 2), Map.entry(Items.CHAINMAIL_CHESTPLATE, 5),
            Map.entry(Items.CHAINMAIL_LEGGINGS, 4), Map.entry(Items.CHAINMAIL_BOOTS, 1),
            Map.entry(Items.IRON_HELMET, 2), Map.entry(Items.IRON_CHESTPLATE, 6),
            Map.entry(Items.IRON_LEGGINGS, 5), Map.entry(Items.IRON_BOOTS, 2),
            Map.entry(Items.GOLDEN_HELMET, 2), Map.entry(Items.GOLDEN_CHESTPLATE, 6),
            Map.entry(Items.GOLDEN_LEGGINGS, 5), Map.entry(Items.GOLDEN_BOOTS, 2),
            Map.entry(Items.DIAMOND_HELMET, 3), Map.entry(Items.DIAMOND_CHESTPLATE, 8),
            Map.entry(Items.DIAMOND_LEGGINGS, 6), Map.entry(Items.DIAMOND_BOOTS, 3),
            Map.entry(Items.NETHERITE_HELMET, 3), Map.entry(Items.NETHERITE_CHESTPLATE, 8),
            Map.entry(Items.NETHERITE_LEGGINGS, 6), Map.entry(Items.NETHERITE_BOOTS, 3),
            Map.entry(Items.TURTLE_HELMET, 2)
    );

    private static final EquipmentSlot[] SLOT_BY_INDEX = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    long lastSwapTime = 0;

    public AutoArmor() {
        super("AutoArmor", "Автоматическая надевка брони", ModuleCategory.PLAYER);
        settings(onlyHelmet, onlyChest, onlyLegs, onlyBoots, delay);
    }

    @EventHandler
    public void onTick(TickEvent e) {
        if (mc.player == null || mc.world == null) return;
        if (mc.currentScreen != null) return;

        long now = System.currentTimeMillis();
        if (now - lastSwapTime < delay.getValue()) return;

        if (mc.player.currentScreenHandler == null) return;

        if (!onlyHelmet.isValue() && swapBestArmor(0)) { lastSwapTime = now; return; }
        if (!onlyChest.isValue() && swapBestArmor(1)) { lastSwapTime = now; return; }
        if (!onlyLegs.isValue() && swapBestArmor(2)) { lastSwapTime = now; return; }
        if (!onlyBoots.isValue() && swapBestArmor(3)) { lastSwapTime = now; return; }

        if (onlyHelmet.isValue()) { swapBestArmor(0); lastSwapTime = now; return; }
        if (onlyChest.isValue()) { swapBestArmor(1); lastSwapTime = now; return; }
        if (onlyLegs.isValue()) { swapBestArmor(2); lastSwapTime = now; return; }
        if (onlyBoots.isValue()) { swapBestArmor(3); lastSwapTime = now; }
    }

    private boolean swapBestArmor(int equipmentSlotIndex) {
        int armorSlot = 8 - equipmentSlotIndex;
        ItemStack currentArmor = mc.player.getInventory().getStack(armorSlot);
        int currentProtection = getProtection(currentArmor);

        int bestSlot = -1;
        int bestProtection = currentProtection;

        for (int i = 9; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;

            EquippableComponent equippable = stack.get(DataComponentTypes.EQUIPPABLE);
            if (equippable == null) continue;

            EquipmentSlot slot = equippable.slot();
            if (slot != SLOT_BY_INDEX[equipmentSlotIndex]) continue;

            int prot = getProtection(stack);
            if (prot > bestProtection) {
                bestProtection = prot;
                bestSlot = i;
            }
        }

        if (bestSlot == -1) return false;

        mc.interactionManager.clickSlot(
                mc.player.playerScreenHandler.syncId,
                bestSlot,
                armorSlot < 9 ? armorSlot : 0,
                SlotActionType.SWAP,
                mc.player
        );
        return true;
    }

    private int getProtection(ItemStack stack) {
        if (stack.isEmpty()) return -1;

        Integer prot = ARMOR_PROTECTION.get(stack.getItem());
        if (prot == null) return -1;

        int durability = stack.getMaxDamage() - stack.getDamage();
        return prot * 100 + durability;
    }
}
