package excel.screens.clickgui.cs.impl;

import excel.IMinecraft;
import excel.modules.module.setting.implement.TextSetting;
import excel.screens.clickgui.cs.Component;
import excel.screens.clickgui.cs.DisplayUtils;
import excel.util.render.font.Fonts;
import net.minecraft.client.gui.screen.Screen;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;

public class TextComponent extends Component implements IMinecraft {

    public TextSetting option;

    public boolean isTyping;

    public TextComponent(TextSetting option) {
        this.option = option;
        this.setting = option;
    }

    String text = "";

    @Override
    public void drawComponent(int mouseX, int mouseY) {
        height = 15;
        text = option.getText() == null ? "" : option.getText();

        String display = text;
        if (!isTyping && text.isEmpty()) {
            display = option.getDescription() == null ? "" : option.getDescription();
        }

        float widthT = Fonts.REGULAR.getWidth(display, 14) + 4;

        DisplayUtils.drawRoundedRect(x + 7.5f - 0.5f, y + 12 - 0.5f, widthT + 1, 11, 2, new Color(53, 55, 60).getRGB());
        int boxColor = isTyping ? new Color(17, 18, 21).brighter().brighter().getRGB() : new Color(17, 18, 21).brighter().getRGB();
        DisplayUtils.drawRoundedRect(x + 7.5f, y + 12, widthT, 10, 2, boxColor);

        Fonts.REGULAR.drawCentered(display, x + 7.5f + (widthT / 2), y + 16, 14, -1);
        Fonts.REGULAR.draw(option.getName(), x + 7.5f, y + 6, 14, -1);
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (isHovered(mouseX, mouseY)) {
            isTyping = !isTyping;
        } else {
            isTyping = false;
        }
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY, int mouseButton) {

    }

    @Override
    public void keyTyped(int keyCode, int scanCode, int modifiers) {
        if (isTyping) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (!text.isEmpty()) {
                    option.setText(text.substring(0, text.length() - 1));
                }
            } else if (keyCode == GLFW.GLFW_KEY_DELETE) {
                option.setText("");
            } else if (keyCode == GLFW.GLFW_KEY_ENTER) {
                isTyping = false;
            } else if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0 && keyCode == GLFW.GLFW_KEY_V) {
                String clip = mc.keyboard.getClipboard();
                if (clip != null) option.setText(clip);
            } else if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0 && keyCode == GLFW.GLFW_KEY_C) {
                GLFW.glfwSetClipboardString(mc.getWindow().getHandle(), text);
            }
        }
    }

    @Override
    public void charTyped(char codePoint, int modifiers) {
        if (isTyping && text.length() < 60) {
            text += codePoint;
            option.setText(text);
        }
    }
}
