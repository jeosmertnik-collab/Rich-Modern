package excel.modules.impl.misc;

import antidaunleak.api.annotation.Native;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.EntityHitResult;
import excel.events.api.EventHandler;
import excel.events.impl.KeyEvent;
import excel.modules.module.ModuleStructure;
import excel.modules.module.category.ModuleCategory;
import excel.modules.module.setting.implement.BindSetting;
import excel.util.repository.friend.FriendUtils;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ClickFriend extends ModuleStructure {

    BindSetting friendBind = new BindSetting("Добавить друга", "Добавить/удалить друга");

    public ClickFriend() {
        super("ClickFriend", "ПКМ по другу для действий", ModuleCategory.MISC);
        settings(friendBind);
    }

    @EventHandler
    @Native(type = Native.Type.VMProtectBeginMutation)
    public void onKey(KeyEvent e) {
        if (e.isKeyDown(friendBind.getKey()) && mc.crosshairTarget instanceof EntityHitResult result && result.getEntity() instanceof PlayerEntity player) {
            String name = player.getName().getString();
            if (FriendUtils.isFriend(name)) FriendUtils.removeFriendAndSave(name);
            else FriendUtils.addFriendAndSave(name);
        }
    }
}