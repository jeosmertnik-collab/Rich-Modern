package excel.modules.impl.misc;

import excel.modules.module.ModuleStructure;
import excel.modules.module.category.ModuleCategory;
import org.lwjgl.glfw.GLFW;

public class AiAssistant extends ModuleStructure {

    public AiAssistant() {
        super("AI Assistant", "AI чат-ассистент для помощи с PvP и модулями", ModuleCategory.MISC);
        this.setKey(GLFW.GLFW_KEY_UNKNOWN);
    }
}
