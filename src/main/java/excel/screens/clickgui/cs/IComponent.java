package excel.screens.clickgui.cs;

public interface IComponent {

    void drawComponent(int mouseX, int mouseY);

    void mouseClicked(int mouseX, int mouseY, int mouseButton);

    void mouseReleased(int mouseX, int mouseY, int mouseButton);

    void keyTyped(int keyCode, int scanCode, int modifiers);

    void charTyped(char codePoint, int modifiers);
}
