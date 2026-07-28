package excel.modules.impl.player;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.*;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import excel.events.api.EventHandler;
import excel.events.impl.TickEvent;
import excel.modules.module.ModuleStructure;
import excel.modules.module.category.ModuleCategory;
import excel.modules.module.setting.implement.BooleanSetting;
import excel.modules.module.setting.implement.MultiSelectSetting;
import excel.modules.module.setting.implement.SliderSettings;
import excel.util.inventory.InventoryUtils;
import excel.util.network.Network;

import java.util.*;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class InventoryManager extends ModuleStructure {

    BooleanSetting dropJunk = new BooleanSetting("Выбрасывать мусор", "Автоматически выбрасывать мусорные предметы").setValue(true);
    BooleanSetting sortHotbar = new BooleanSetting("Сортировать хотбар", "Расставлять предметы по слотам хотбара").setValue(true);
    BooleanSetting stackItems = new BooleanSetting("Стакать предметы", "Объединять стакуемые предметы").setValue(true);

    MultiSelectSetting hotbarLayout = new MultiSelectSetting("Раскладка хотбара", "Что расставлять в хотбаре")
            .value("Меч", "Тотем", "Блоки", "Еда", "Жемчуг", "Эндер-глаз")
            .selected("Меч", "Тотем", "Блоки", "Еда")
            .visible(() -> sortHotbar.isValue());

    SliderSettings delay = new SliderSettings("Задержка (мс)", "Задержка между действиями").setValue(50).range(0, 200);

    MultiSelectSetting junkItems = new MultiSelectSetting("Мусор", "Предметы для выбрасывания")
            .value("Грязь", "Булыжник", "Гранит", "Диорит", "Андезит", "Древний фрагмент", "Песок", "Гравий")
            .selected("Грязь", "Гранит", "Диорит", "Андезит");

    private static final Set<Item> JUNK_BY_NAME = Set.of(
            Items.DIRT, Items.COBBLESTONE, Items.GRANITE, Items.DIORITE,
            Items.ANDESITE, Items.SAND, Items.GRAVEL, Items.TUFF
    );

    private static final Set<Item> FOOD_ITEMS = Set.of(
            Items.GOLDEN_APPLE, Items.ENCHANTED_GOLDEN_APPLE, Items.COOKED_BEEF,
            Items.COOKED_PORKCHOP, Items.COOKED_MUTTON, Items.COOKED_CHICKEN,
            Items.COOKED_RABBIT, Items.COOKED_COD, Items.COOKED_SALMON,
            Items.BAKED_POTATO, Items.BREAD, Items.PUMPKIN_PIE,
            Items.GOLDEN_CARROT, Items.MUSHROOM_STEW, Items.RABBIT_STEW,
            Items.BEETROOT_SOUP, Items.COOKIE, Items.CAKE
    );

    private static final Set<Item> BLOCK_ITEMS = Set.of(
            Items.OAK_PLANKS, Items.SPRUCE_PLANKS, Items.BIRCH_PLANKS,
            Items.COBBLESTONE, Items.DEEPSLATE, Items.STONE, Items.OBSIDIAN,
            Items.CRYING_OBSIDIAN, Items.END_STONE, Items.NETHERRACK
    );

    private long lastActionTime = 0;
    int currentPhase = 0;

    public InventoryManager() {
        super("InventoryManager", "Менеджер инвентаря (FunTime)", ModuleCategory.PLAYER);
        settings(dropJunk, sortHotbar, stackItems, hotbarLayout, delay, junkItems);
    }

    @EventHandler
    public void onTick(TickEvent e) {
        if (mc.player == null || mc.world == null) return;
        if (mc.currentScreen != null) return;
        if (!Network.isFunTime()) return;

        long now = System.currentTimeMillis();
        if (now - lastActionTime < delay.getValue()) return;

        if (dropJunk.isValue() && dropJunkItems()) {
            lastActionTime = now;
            return;
        }

        if (stackItems.isValue() && stackInventoryItems()) {
            lastActionTime = now;
            return;
        }

        if (sortHotbar.isValue() && sortHotbarSlots()) {
            lastActionTime = now;
        }
    }

    private boolean dropJunkItems() {
        if (mc.player == null || mc.interactionManager == null) return false;

        for (int i = 9; i < 45; i++) {
            Slot slot = mc.player.playerScreenHandler.getSlot(i);
            if (slot == null || slot.getStack().isEmpty()) continue;

            Item item = slot.getStack().getItem();
            if (isJunkItem(slot.getStack())) {
                mc.interactionManager.clickSlot(
                        mc.player.playerScreenHandler.syncId,
                        i, 1, SlotActionType.THROW, mc.player
                );
                return true;
            }
        }
        return false;
    }

    private boolean isJunkItem(ItemStack stack) {
        Item item = stack.getItem();
        if (JUNK_BY_NAME.contains(item) && !stack.hasEnchantments()) {
            String selected = junkItems.getSelected().toString().toLowerCase();
            if (item == Items.DIRT && selected.contains("грязь")) return true;
            if (item == Items.COBBLESTONE && selected.contains("булыжник")) return true;
            if (item == Items.GRANITE && selected.contains("гранит")) return true;
            if (item == Items.DIORITE && selected.contains("диорит")) return true;
            if (item == Items.ANDESITE && selected.contains("андезит")) return true;
            if (item == Items.SAND && selected.contains("песок")) return true;
            if (item == Items.GRAVEL && selected.contains("гравий")) return true;
        }
        return false;
    }

    private boolean stackInventoryItems() {
        if (mc.player == null || mc.interactionManager == null) return false;
        int syncId = mc.player.playerScreenHandler.syncId;

        for (int i = 9; i < 45; i++) {
            Slot slotA = mc.player.playerScreenHandler.getSlot(i);
            if (slotA == null || slotA.getStack().isEmpty()) continue;
            if (!slotA.getStack().isStackable()) continue;
            if (slotA.getStack().getCount() >= slotA.getStack().getMaxCount()) continue;

            for (int j = i + 1; j < 45; j++) {
                Slot slotB = mc.player.playerScreenHandler.getSlot(j);
                if (slotB == null || slotB.getStack().isEmpty()) continue;
                if (slotA.getStack().getItem() != slotB.getStack().getItem()) continue;
                if (slotA.getStack().getCount() >= slotA.getStack().getMaxCount()) break;

                mc.interactionManager.clickSlot(syncId, j, 0, SlotActionType.PICKUP, mc.player);
                mc.interactionManager.clickSlot(syncId, i, 0, SlotActionType.PICKUP, mc.player);

                if (!mc.player.currentScreenHandler.getCursorStack().isEmpty()) {
                    mc.interactionManager.clickSlot(syncId, j, 0, SlotActionType.PICKUP, mc.player);
                }
                return true;
            }
        }
        return false;
    }

    private boolean sortHotbarSlots() {
        if (mc.player == null || mc.interactionManager == null) return false;
        int syncId = mc.player.playerScreenHandler.syncId;

        Map<Integer, Item> desiredSlots = buildDesiredLayout();
        if (desiredSlots.isEmpty()) return false;

        for (Map.Entry<Integer, Item> entry : desiredSlots.entrySet()) {
            int hotbarSlot = entry.getKey();
            int screenSlot = hotbarSlot + 36;
            Item desiredItem = entry.getValue();

            Slot currentSlot = mc.player.playerScreenHandler.getSlot(screenSlot);
            if (currentSlot == null) continue;

            if (!currentSlot.getStack().isEmpty() && currentSlot.getStack().getItem() == desiredItem) continue;

            int foundSlot = findItemInInventory(desiredItem);
            if (foundSlot == -1) continue;

            mc.interactionManager.clickSlot(syncId, foundSlot, hotbarSlot, SlotActionType.SWAP, mc.player);
            return true;
        }
        return false;
    }

    private Map<Integer, Item> buildDesiredLayout() {
        Map<Integer, Item> layout = new LinkedHashMap<>();
        int slot = 0;

        if (hotbarLayout.isSelected("Меч")) {
            Item sword = findBestSword();
            if (sword != null) layout.put(slot++, sword);
        }
        if (hotbarLayout.isSelected("Тотем")) {
            layout.put(slot++, Items.TOTEM_OF_UNDYING);
        }
        if (hotbarLayout.isSelected("Блоки")) {
            Item blocks = findBestBlocks();
            if (blocks != null) layout.put(slot++, blocks);
        }
        if (hotbarLayout.isSelected("Еда")) {
            Item food = findBestFood();
            if (food != null) layout.put(slot++, food);
        }
        if (hotbarLayout.isSelected("Жемчуг")) {
            layout.put(slot++, Items.ENDER_PEARL);
        }
        if (hotbarLayout.isSelected("Эндер-глаз")) {
            layout.put(slot++, Items.ENDER_EYE);
        }

        return layout;
    }

    private Item findBestSword() {
        Item[] swords = {Items.NETHERITE_SWORD, Items.DIAMOND_SWORD, Items.IRON_SWORD, Items.STONE_SWORD, Items.WOODEN_SWORD};
        for (Item sword : swords) {
            if (findItemInInventory(sword) != -1 || findItemInHotbar(sword) != -1) return sword;
        }
        return null;
    }

    private Item findBestBlocks() {
        for (Item item : BLOCK_ITEMS) {
            if (findItemInInventory(item) != -1) return item;
        }
        return null;
    }

    private Item findBestFood() {
        Item[] foodPriority = {Items.GOLDEN_APPLE, Items.ENCHANTED_GOLDEN_APPLE, Items.GOLDEN_CARROT,
                Items.COOKED_BEEF, Items.COOKED_PORKCHOP, Items.BREAD};
        for (Item food : foodPriority) {
            if (findItemInInventory(food) != -1) return food;
        }
        for (Item food : FOOD_ITEMS) {
            if (findItemInInventory(food) != -1) return food;
        }
        return null;
    }

    private int findItemInInventory(Item item) {
        for (int i = 9; i < 36; i++) {
            Slot slot = mc.player.playerScreenHandler.getSlot(i);
            if (slot != null && !slot.getStack().isEmpty() && slot.getStack().getItem() == item) {
                return i;
            }
        }
        return -1;
    }

    private int findItemInHotbar(Item item) {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.getItem() == item) {
                return i;
            }
        }
        return -1;
    }
}
