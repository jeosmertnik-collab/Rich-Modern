package excel.modules.impl.combat;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import excel.modules.module.ModuleStructure;
import excel.modules.module.category.ModuleCategory;
import excel.util.Instance;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class NoInteract extends ModuleStructure {
    public static NoInteract getInstance() {
        return Instance.get(NoInteract.class);
    }

    public NoInteract() {
        super("NoInteract", "Запрещает взаимодействие с сущностями", ModuleCategory.COMBAT);
    }
}
