package excel.screens.ai;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import excel.IMinecraft;
import excel.util.ai.OllamaClient;
import excel.util.ai.TtsHelper;
import excel.util.render.Render2D;
import excel.util.render.font.Fonts;
import excel.util.repository.way.Way;
import excel.util.repository.way.WayRepository;
import net.minecraft.util.math.BlockPos;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AiChatScreen extends Screen implements IMinecraft {

    private static final float PANEL_WIDTH = 420;
    private static final float PANEL_HEIGHT = 320;
    private static final float INPUT_HEIGHT = 22;
    private static final float HEADER_HEIGHT = 30;
    private static final float LINE_HEIGHT = 10;
    private static final float NAME_SIZE = 7f;
    private static final float MSG_SIZE = 7f;

    private static final long TTS_DELAY_MS = 400;

    private static final Pattern WP_PATTERN = Pattern.compile(
            "(?:waypoint|метк[уаи]|точк[уаи]|вейпоинт|вейпоинта|вейпоинту|вейпоинтом|вейпоинте)\\s*[=:]*\\s*(-?\\d{1,7})\\s*[,; ]\\s*(-?\\d{1,7})\\s*[,; ]\\s*(-?\\d{1,7})",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern COORD_EXTRACT = Pattern.compile(
            "(-?\\d{1,7})\\s+(-?\\d{1,7})\\s+(-?\\d{1,7})"
    );

    private static final Pattern DEL_PATTERN = Pattern.compile(
            "(?:удали|удалить|убери|убрать|delete|remove|del|rm)\\s+(?:вейпоинт|waypoint|метк[уаи]|точк[уаи])?\\s*(-?\\d{1,7})\\s*[,; ]\\s*(-?\\d{1,7})\\s*[,; ]\\s*(-?\\d{1,7})",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern DEL_PATTERN_NAME = Pattern.compile(
            "(?:удали|удалить|убери|убрать|delete|remove|del|rm)\\s+(?:вейпоинт|waypoint|метк[уаи]|точк[уаи])\\s*[=:]*\\s*(.+)",
            Pattern.CASE_INSENSITIVE
    );

    private boolean voiceMode = true;
    private boolean settingsOpen = false;

    private float voiceDropdownScroll = 0;
    private boolean voiceDropdownOpen = false;
    private int selectedVoiceIndex = 0;
    private float volumeSlider = TtsHelper.getVolume() / 100f;

    private float settingsScroll = 0;

    private final OllamaClient client;
    private final List<ChatEntry> messages = new ArrayList<>();
    private String inputText = "";
    private int cursorPos = 0;
    private float scrollOffset = 0;
    private boolean isWaitingForResponse = false;
    private float typingDots = 0;

    public AiChatScreen() {
        super(Text.of("AI Chat"));
        this.client = new OllamaClient();
        this.client.setSystemPrompt(
                "Ты AI-ассистент мода Excel Client для Minecraft 1.21. " +
                "Ты знаешь все модули клиента: Aura, Velocity, AutoTotem, ESP, BlockESP, " +
                "StorageESP, Jesus, Fly, Speed, Strafe, TargetESP, HitBox, " +
                "BowSpammer, AutoGApple, ClickPearl, FreeCam, AutoSprint, " +
                "InventoryMove, ChestStealer, NoFallDamage, AutoPotion, NoSlow, " +
                "AutoDuel, TriggerBot, and more. " +
                "Рекомендуй оптимальные наборы модулей для PvP, фарма, мира. " +
                "Если пользователь даёт координаты для вейпоинта, ответь в формате: WP:x y z " +
                "Если пользователь просит удалить вейпоинт по координатам, ответь: DELWP:x y z " +
                "Если пользователь просит список вейпоинтов, ответь: LISTWP " +
                "Отвечай кратко на русском. Не используй markdown."
        );
        this.client.setModel("llama3.2");
    }

    @Override
    public void init() {
        super.init();
        messages.clear();
        messages.add(new ChatEntry("assistant", "Привет! Я AI-ассистент Excel Client. Помогаю с модулями, PvP и вейпоинтами."));
        isWaitingForResponse = false;
        scrollOffset = 0;
        settingsOpen = false;
        voiceDropdownOpen = false;
        selectedVoiceIndex = findVoiceIndex(TtsHelper.getAiVoice());
    }

    private int findVoiceIndex(String voice) {
        String[] voices = TtsHelper.getAvailableAiVoices();
        for (int i = 0; i < voices.length; i++) {
            if (voices[i].equals(voice)) return i;
        }
        return 0;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        Render2D.rect(0, 0, width * 4, height * 4, new Color(0, 0, 0, 200).getRGB(), 0);

        float px = (width - PANEL_WIDTH) / 2f;
        float py = (height - PANEL_HEIGHT) / 2f;

        drawPanel(px, py);
        drawHeader(px, py);

        if (settingsOpen) {
            drawSettings(px, py, mouseX, mouseY, delta);
        } else {
            drawMessages(px, py);
            drawInput(px, py);
        }
    }

    private void drawPanel(float x, float y) {
        Render2D.gradientRect(x, y, PANEL_WIDTH, PANEL_HEIGHT,
                new int[]{
                        new Color(25, 25, 30, 245).getRGB(),
                        new Color(15, 15, 20, 245).getRGB(),
                        new Color(25, 25, 30, 245).getRGB(),
                        new Color(15, 15, 20, 245).getRGB()
                }, 8);
        Render2D.outline(x, y, PANEL_WIDTH, PANEL_HEIGHT, 0.5f, new Color(70, 130, 255, 180).getRGB(), 8);
    }

    private void drawHeader(float x, float y) {
        Render2D.rect(x, y, PANEL_WIDTH, HEADER_HEIGHT, new Color(20, 20, 28, 230).getRGB(), 8);

        String title = "AI Assistant";
        float titleW = Fonts.BOLD.getWidth(title, 8f);
        Fonts.BOLD.draw(title, x + PANEL_WIDTH / 2 - titleW / 2, y + 9, 8f, 0xFFFFFFFF);

        String closeText = "ESC";
        float closeW = Fonts.BOLD.getWidth(closeText, 6f);
        Fonts.BOLD.draw(closeText, x + PANEL_WIDTH - closeW - 10, y + 10, 6f, new Color(180, 180, 180, 180).getRGB());

        float gearX = x + PANEL_WIDTH - 30;
        float gearY = y + 8;
        Fonts.GUI_ICONS.draw("B", gearX, gearY, 10, settingsOpen
                ? new Color(100, 180, 255, 220).getRGB()
                : new Color(150, 150, 150, 180).getRGB());

        if (isWaitingForResponse) {
            typingDots += 0.06f;
            int count = (int) (typingDots * 3) % 4;
            StringBuilder dots = new StringBuilder();
            for (int i = 0; i < count; i++) dots.append(".");
            Fonts.BOLD.draw("Печатает" + dots, x + 10, y + 10, 6f, new Color(150, 200, 255, 200).getRGB());
        }

        Render2D.rect(x + 6, y + HEADER_HEIGHT - 1, PANEL_WIDTH - 12, 1, new Color(60, 130, 255, 120).getRGB(), 0);
    }

    private void drawMessages(float x, float y) {
        float msgY = y + HEADER_HEIGHT + 4;
        float msgH = PANEL_HEIGHT - HEADER_HEIGHT - INPUT_HEIGHT - 12;
        float msgX = x + 8;
        float msgW = PANEL_WIDTH - 16;

        List<ChatEntry> snapshot = new ArrayList<>(messages);

        List<float[]> entryHeights = new ArrayList<>();
        for (ChatEntry entry : snapshot) {
            List<String> lines = wrapText(entry.text, msgW - 20);
            float h = LINE_HEIGHT + lines.size() * LINE_HEIGHT + 6;
            entryHeights.add(new float[]{h});
        }

        float totalH = 0;
        for (float[] eh : entryHeights) totalH += eh[0];

        float maxScroll = Math.max(0, totalH - msgH + LINE_HEIGHT);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));

        float renderedY = msgY - scrollOffset;
        for (int i = 0; i < snapshot.size(); i++) {
            ChatEntry entry = snapshot.get(i);
            float entryH = entryHeights.get(i)[0];

            if (renderedY + entryH < msgY) {
                renderedY += entryH;
                continue;
            }
            if (renderedY > msgY + msgH) break;

            boolean isUser = entry.role.equals("user");
            boolean isSystem = entry.role.equals("system");
            int nameColor;
            String name;
            if (isSystem) {
                nameColor = new Color(255, 200, 80, 220).getRGB();
                name = "System";
            } else if (isUser) {
                nameColor = new Color(100, 180, 255, 220).getRGB();
                name = "You";
            } else {
                nameColor = new Color(100, 255, 140, 220).getRGB();
                name = "AI";
            }

            if (renderedY + LINE_HEIGHT > msgY && renderedY < msgY + msgH) {
                Fonts.BOLD.draw(name, msgX + 4, renderedY, NAME_SIZE, nameColor);
            }
            renderedY += LINE_HEIGHT;

            int textColor = isUser ? new Color(200, 200, 210, 230).getRGB()
                    : isSystem ? new Color(255, 220, 130, 220).getRGB()
                    : new Color(220, 225, 230, 230).getRGB();
            List<String> lines = wrapText(entry.text, msgW - 20);
            for (String line : lines) {
                if (renderedY + LINE_HEIGHT > msgY && renderedY < msgY + msgH) {
                    Fonts.BOLD.draw(line, msgX + 8, renderedY, MSG_SIZE, textColor);
                }
                renderedY += LINE_HEIGHT;
            }
            renderedY += 4;
        }
    }

    private void drawInput(float x, float y) {
        float inputY = y + PANEL_HEIGHT - INPUT_HEIGHT - 6;
        float inputX = x + 6;
        float inputW = PANEL_WIDTH - 12;

        Render2D.rect(inputX, inputY, inputW, INPUT_HEIGHT, new Color(10, 10, 15, 230).getRGB(), 4);
        Render2D.outline(inputX, inputY, inputW, INPUT_HEIGHT, 0.3f, new Color(80, 140, 255, 180).getRGB(), 4);

        float textW = Fonts.BOLD.getWidth(inputText, MSG_SIZE);
        float cursorX = inputX + 8 + textW;
        if ((System.currentTimeMillis() / 500) % 2 == 0) {
            Render2D.rect(cursorX, inputY + 3, 1, INPUT_HEIGHT - 6, new Color(255, 255, 255, 200).getRGB(), 0);
        }

        if (!inputText.isEmpty()) {
            Fonts.BOLD.draw(inputText, inputX + 8, inputY + 4, MSG_SIZE, 0xFFDDDDDD);
        } else {
            Fonts.BOLD.draw("Напиши сообщение...", inputX + 8, inputY + 4, MSG_SIZE, new Color(80, 80, 100, 150).getRGB());
        }
    }

    private void drawSettings(float x, float y, float mouseX, float mouseY, float delta) {
        float sy = y + HEADER_HEIGHT + 6;
        float sx = x + 12;
        float sw = PANEL_WIDTH - 24;
        float sectionH = 20;

        float cy = sy - settingsScroll;

        Fonts.BOLD.draw("Голосовые настройки", sx, cy, 8f, new Color(100, 180, 255, 240).getRGB());
        cy += 16;

        Fonts.BOLD.draw("Голос ИИ:", sx, cy, 6.5f, new Color(200, 200, 210, 220).getRGB());
        cy += 12;

        float dropdownX = sx;
        float dropdownY = cy;
        float dropdownW = sw;
        float dropdownH = 16;

        Render2D.rect(dropdownX, dropdownY, dropdownW, dropdownH, new Color(15, 15, 22, 220).getRGB(), 4);
        Render2D.outline(dropdownX, dropdownY, dropdownW, dropdownH, 0.3f,
                voiceDropdownOpen ? new Color(100, 180, 255, 200).getRGB() : new Color(60, 60, 80, 180).getRGB(), 4);

        String[] voices = TtsHelper.getAvailableAiVoices();
        String currentVoiceName = TtsHelper.voiceDisplayName(voices[selectedVoiceIndex]);
        Fonts.BOLD.draw(currentVoiceName, dropdownX + 6, dropdownY + 3, 6f, new Color(220, 220, 230, 230).getRGB());

        String arrow = voiceDropdownOpen ? "^" : "v";
        Fonts.BOLD.draw(arrow, dropdownX + dropdownW - 12, dropdownY + 3, 6f, new Color(150, 150, 160, 180).getRGB());

        if (voiceDropdownOpen) {
            float listY = dropdownY + dropdownH + 2;
            float listH = Math.min(voices.length, 5) * 14 + 4;
            Render2D.rect(dropdownX, listY, dropdownW, listH, new Color(15, 15, 22, 240).getRGB(), 4);
            Render2D.outline(dropdownX, listY, dropdownW, listH, 0.3f, new Color(60, 60, 80, 180).getRGB(), 4);

            for (int i = 0; i < voices.length; i++) {
                float itemY = listY + 2 + i * 14;
                if (itemY + 14 > listY + listH) break;

                boolean hovered = mouseX >= dropdownX && mouseX <= dropdownX + dropdownW
                        && mouseY >= itemY && mouseY <= itemY + 13;
                boolean selected = i == selectedVoiceIndex;

                if (selected) {
                    Render2D.rect(dropdownX + 2, itemY, dropdownW - 4, 13, new Color(70, 130, 255, 50).getRGB(), 3);
                } else if (hovered) {
                    Render2D.rect(dropdownX + 2, itemY, dropdownW - 4, 13, new Color(255, 255, 255, 15).getRGB(), 3);
                }

                int textColor = selected ? new Color(100, 180, 255, 230).getRGB()
                        : hovered ? new Color(220, 220, 230, 220).getRGB()
                        : new Color(160, 160, 170, 200).getRGB();
                Fonts.BOLD.draw(TtsHelper.voiceDisplayName(voices[i]), dropdownX + 8, itemY + 2, 5.5f, textColor);
            }
        }

        cy += dropdownH + 10;

        Fonts.BOLD.draw("Голос оповещений:", sx, cy, 6.5f, new Color(200, 200, 210, 220).getRGB());
        cy += 12;

        String alertVoiceName = TtsHelper.voiceDisplayName(TtsHelper.getAlertVoice());
        Render2D.rect(sx, cy, sw, 16, new Color(15, 15, 22, 220).getRGB(), 4);
        Render2D.outline(sx, cy, sw, 16, 0.3f, new Color(60, 60, 80, 180).getRGB(), 4);
        Fonts.BOLD.draw(alertVoiceName, sx + 6, cy + 3, 6f, new Color(220, 220, 230, 230).getRGB());
        cy += 22;

        Fonts.BOLD.draw("Громкость: " + TtsHelper.getVolume() + "%", sx, cy, 6.5f, new Color(200, 200, 210, 220).getRGB());
        cy += 12;

        float sliderX = sx;
        float sliderW = sw;
        float sliderH = 6;

        Render2D.rect(sliderX, cy, sliderW, sliderH, new Color(30, 30, 40, 200).getRGB(), 3);

        float fillW = sliderW * volumeSlider;
        Render2D.rect(sliderX, cy, fillW, sliderH, new Color(70, 130, 255, 200).getRGB(), 3);

        float knobX = sliderX + fillW - 4;
        float knobY = cy - 2;
        Render2D.rect(knobX, knobY, 8, 10, new Color(200, 210, 255, 240).getRGB(), 4);
        cy += 16;

        float btnW = (sw - 8) / 2f;

        boolean testAiHover = mouseX >= sx && mouseX <= sx + btnW && mouseY >= cy && mouseY <= cy + 18;
        Render2D.rect(sx, cy, btnW, 18, testAiHover ? new Color(70, 130, 255, 120).getRGB() : new Color(30, 30, 45, 200).getRGB(), 4);
        Render2D.outline(sx, cy, btnW, 18, 0.3f, new Color(70, 130, 255, 150).getRGB(), 4);
        String testAiText = "Тест AI голоса";
        float testAiW = Fonts.BOLD.getWidth(testAiText, 6f);
        Fonts.BOLD.draw(testAiText, sx + (btnW - testAiW) / 2, cy + 5, 6f, new Color(200, 210, 255, 230).getRGB());

        float btn2X = sx + btnW + 8;
        boolean testAlertHover = mouseX >= btn2X && mouseX <= btn2X + btnW && mouseY >= cy && mouseY <= cy + 18;
        Render2D.rect(btn2X, cy, btnW, 18, testAlertHover ? new Color(255, 160, 60, 120).getRGB() : new Color(45, 30, 15, 200).getRGB(), 4);
        Render2D.outline(btn2X, cy, btnW, 18, 0.3f, new Color(255, 160, 60, 150).getRGB(), 4);
        String testAlertText = "Тест оповещения";
        float testAlertW = Fonts.BOLD.getWidth(testAlertText, 6f);
        Fonts.BOLD.draw(testAlertText, btn2X + (btnW - testAlertW) / 2, cy + 5, 6f, new Color(255, 200, 150, 230).getRGB());
        cy += 26;

        boolean voiceOn = TtsHelper.isAiVoiceEnabled();
        boolean voiceToggleHover = mouseX >= sx && mouseX <= sx + 120 && mouseY >= cy && mouseY <= cy + 16;
        Render2D.rect(sx, cy, 120, 16, voiceToggleHover ? new Color(40, 40, 55, 200).getRGB() : new Color(20, 20, 30, 200).getRGB(), 4);
        Render2D.outline(sx, cy, 120, 16, 0.3f, new Color(60, 60, 80, 150).getRGB(), 4);
        String toggleText = voiceOn ? "Голос: ВКЛ" : "Голос: ВЫКЛ";
        int toggleColor = voiceOn ? new Color(100, 255, 140, 220).getRGB() : new Color(255, 100, 100, 220).getRGB();
        Fonts.BOLD.draw(toggleText, sx + 8, cy + 4, 6f, toggleColor);

        Render2D.rect(x + 6, y + PANEL_HEIGHT - INPUT_HEIGHT - 6 - 2, PANEL_WIDTH - 12, 1, new Color(40, 40, 60, 150).getRGB(), 0);
    }

    private List<String> wrapText(String text, float maxWidth) {
        List<String> lines = new ArrayList<>();
        String[] words = text.split(" ");
        StringBuilder currentLine = new StringBuilder();

        for (String word : words) {
            String test = currentLine.isEmpty() ? word : currentLine + " " + word;
            if (Fonts.BOLD.getWidth(test, MSG_SIZE) > maxWidth && !currentLine.isEmpty()) {
                lines.add(currentLine.toString());
                currentLine = new StringBuilder(word);
            } else {
                if (!currentLine.isEmpty()) currentLine.append(" ");
                currentLine.append(word);
            }
        }
        if (!currentLine.isEmpty()) {
            lines.add(currentLine.toString());
        }
        if (lines.isEmpty()) {
            lines.add("");
        }
        return lines;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (settingsOpen) {
            settingsScroll -= vertical * 14;
            settingsScroll = Math.max(0, settingsScroll);
        } else {
            scrollOffset -= vertical * LINE_HEIGHT * 3;
            scrollOffset = Math.max(0, scrollOffset);
        }
        return true;
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (click.button() != 0) return super.mouseClicked(click, doubled);

        float px = (width - PANEL_WIDTH) / 2f;
        float py = (height - PANEL_HEIGHT) / 2f;

        float gearX = px + PANEL_WIDTH - 30;
        float gearY = py + 8;
        if ((float) click.x() >= gearX && (float) click.x() <= gearX + 12 && (float) click.y() >= gearY && (float) click.y() <= gearY + 12) {
            settingsOpen = !settingsOpen;
            voiceDropdownOpen = false;
            settingsScroll = 0;
            return true;
        }

        if (settingsOpen) {
            handleSettingsClick((float) click.x(), (float) click.y());
            return true;
        }

        return super.mouseClicked(click, doubled);
    }

    private void handleSettingsClick(float mouseX, float mouseY) {
        float px = (width - PANEL_WIDTH) / 2f;
        float py = (height - PANEL_HEIGHT) / 2f;
        float sx = px + 12;
        float sw = PANEL_WIDTH - 24;
        float sy = py + HEADER_HEIGHT + 6 - settingsScroll;

        float cy = sy + 28;

        float dropdownX = sx;
        float dropdownY = cy;
        float dropdownW = sw;
        float dropdownH = 16;

        if (mouseX >= dropdownX && mouseX <= dropdownX + dropdownW && mouseY >= dropdownY && mouseY <= dropdownY + dropdownH) {
            voiceDropdownOpen = !voiceDropdownOpen;
            return;
        }

        if (voiceDropdownOpen) {
            String[] voices = TtsHelper.getAvailableAiVoices();
            float listY = dropdownY + dropdownH + 2;
            for (int i = 0; i < voices.length; i++) {
                float itemY = listY + 2 + i * 14;
                if (mouseX >= dropdownX && mouseX <= dropdownX + dropdownW && mouseY >= itemY && mouseY <= itemY + 13) {
                    selectedVoiceIndex = i;
                    TtsHelper.setAiVoice(voices[i]);
                    voiceDropdownOpen = false;
                    return;
                }
            }
            voiceDropdownOpen = false;
        }

        cy += dropdownH + 34;

        float sliderX = sx;
        float sliderW = sw;
        if (mouseX >= sliderX && mouseX <= sliderX + sliderW && mouseY >= cy - 4 && mouseY <= cy + 14) {
            volumeSlider = (float) Math.max(0, Math.min(1, (mouseX - sliderX) / sliderW));
            TtsHelper.setVolume((int) (volumeSlider * 100));
            return;
        }
        cy += 22;

        float btnW = (sw - 8) / 2f;
        if (mouseX >= sx && mouseX <= sx + btnW && mouseY >= cy && mouseY <= cy + 18) {
            TtsHelper.testAiVoice();
            return;
        }

        float btn2X = sx + btnW + 8;
        if (mouseX >= btn2X && mouseX <= btn2X + btnW && mouseY >= cy && mouseY <= cy + 18) {
            TtsHelper.testAlertVoice();
            return;
        }
        cy += 26;

        if (mouseX >= sx && mouseX <= sx + 120 && mouseY >= cy && mouseY <= cy + 16) {
            TtsHelper.setAiVoiceEnabled(!TtsHelper.isAiVoiceEnabled());
            return;
        }
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (settingsOpen) {
            if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
                settingsOpen = false;
                voiceDropdownOpen = false;
                return true;
            }
            return false;
        }

        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            super.close();
            return true;
        }

        if (input.key() == GLFW.GLFW_KEY_ENTER) {
            sendMessage();
            return true;
        }

        if (input.key() == GLFW.GLFW_KEY_BACKSPACE) {
            if (!inputText.isEmpty() && cursorPos > 0) {
                inputText = inputText.substring(0, cursorPos - 1) + inputText.substring(cursorPos);
                cursorPos--;
            }
            return true;
        }

        if (input.key() == GLFW.GLFW_KEY_DELETE) {
            if (!inputText.isEmpty() && cursorPos < inputText.length()) {
                inputText = inputText.substring(0, cursorPos) + inputText.substring(cursorPos + 1);
            }
            return true;
        }

        if (input.key() == GLFW.GLFW_KEY_LEFT) {
            cursorPos = Math.max(0, cursorPos - 1);
            return true;
        }
        if (input.key() == GLFW.GLFW_KEY_RIGHT) {
            cursorPos = Math.min(inputText.length(), cursorPos + 1);
            return true;
        }

        if (input.key() == GLFW.GLFW_KEY_HOME) {
            cursorPos = 0;
            return true;
        }
        if (input.key() == GLFW.GLFW_KEY_END) {
            cursorPos = inputText.length();
            return true;
        }

        if (input.key() == GLFW.GLFW_KEY_V && (input.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0) {
            String clipboard = mc.keyboard.getClipboard();
            if (clipboard != null && !clipboard.isEmpty()) {
                String sanitized = clipboard.replaceAll("[\\r\\n]", " ");
                inputText = inputText.substring(0, cursorPos) + sanitized + inputText.substring(cursorPos);
                cursorPos += sanitized.length();
            }
            return true;
        }

        return false;
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (settingsOpen) return false;
        char c = (char) input.codepoint();
        if (c >= 32) {
            inputText = inputText.substring(0, cursorPos) + c + inputText.substring(cursorPos);
            cursorPos++;
        }
        return true;
    }

    private void sendMessage() {
        String text = inputText.trim();
        if (text.isEmpty() || isWaitingForResponse) return;

        messages.add(new ChatEntry("user", text));
        inputText = "";
        cursorPos = 0;
        scrollToBottom();

        Matcher delCoord = DEL_PATTERN.matcher(text);
        if (delCoord.find()) {
            String result = deleteWaypoint(delCoord.group(1), delCoord.group(2), delCoord.group(3));
            messages.add(new ChatEntry("system", result));
            trimMessages();
            scrollToBottom();
            return;
        }

        Matcher delName = DEL_PATTERN_NAME.matcher(text);
        if (delName.find()) {
            String query = delName.group(1).trim();
            String result = deleteWaypointByName(query);
            messages.add(new ChatEntry("system", result));
            trimMessages();
            scrollToBottom();
            return;
        }

        Matcher userWp = WP_PATTERN.matcher(text);
        if (userWp.find()) {
            String name = createWaypoint(userWp.group(1), userWp.group(2), userWp.group(3));
            if (name != null) {
                messages.add(new ChatEntry("system", "Вейпоинт создан: " + name));
            } else {
                messages.add(new ChatEntry("system", "Не удалось создать вейпоинт"));
            }
            trimMessages();
            scrollToBottom();
            return;
        }

        Matcher userCoord = COORD_EXTRACT.matcher(text);
        if (userCoord.find() && text.matches(".*-?\\d+\\s+-?\\d+\\s+-?\\d+.*")) {
            String name = createWaypoint(userCoord.group(1), userCoord.group(2), userCoord.group(3));
            if (name != null) {
                messages.add(new ChatEntry("system", "Вейпоинт создан: " + name));
            } else {
                messages.add(new ChatEntry("system", "Не удалось создать вейпоинт"));
            }
            trimMessages();
            scrollToBottom();
            return;
        }

        if (text.toLowerCase().contains("список вейпоинтов") || text.toLowerCase().contains("list waypoint")
                || text.toLowerCase().contains("покажи вейпоинты") || text.toLowerCase().contains("какие вейпоинты")) {
            listWaypoints();
            trimMessages();
            scrollToBottom();
            return;
        }

        if (text.toLowerCase().contains("удали все вейпоинты") || text.toLowerCase().contains("очисти вейпоинты")
                || text.toLowerCase().contains("удалить все вейпоинты") || text.toLowerCase().contains("clear waypoints")
                || text.toLowerCase().contains("delete all waypoints")) {
            WayRepository repo = WayRepository.getInstance();
            int count = repo.size();
            repo.clearListAndSave();
            messages.add(new ChatEntry("system", "Все вейпоинты удалены (" + count + " шт.)"));
            trimMessages();
            scrollToBottom();
            return;
        }

        String lowerText = text.toLowerCase();
        if (lowerText.equals("говори") || lowerText.equals("say") || lowerText.equals("talk")
                || lowerText.equals("голос") || lowerText.equals("включай голос")) {
            voiceMode = true;
            messages.add(new ChatEntry("system", "Голос ИИ включён"));
            trimMessages();
            scrollToBottom();
            return;
        }
        if (lowerText.equals("молчи") || lowerText.equals("quiet") || lowerText.equals("silent")
                || lowerText.equals("выключай голос") || lowerText.equals("без голоса")) {
            voiceMode = false;
            messages.add(new ChatEntry("system", "Голос ИИ выключен"));
            trimMessages();
            scrollToBottom();
            return;
        }

        isWaitingForResponse = true;

        client.sendStreamingMessage(text,
                token -> {
                },
                response -> {
                    isWaitingForResponse = false;
                    messages.add(new ChatEntry("assistant", response));
                    if (voiceMode) TtsHelper.speakAiDelayed(response, TTS_DELAY_MS);
                    handleWaypointResponse(response);
                    trimMessages();
                    scrollToBottom();
                },
                error -> {
                    isWaitingForResponse = false;
                    messages.add(new ChatEntry("system", error));
                    scrollToBottom();
                }
        );
    }

    private void scrollToBottom() {
        scrollOffset = Float.MAX_VALUE;
    }

    private void handleWaypointResponse(String response) {
        String lower = response.toLowerCase();

        if (lower.contains("deleteallwp") || lower.contains("очистить вейпоинты")) {
            WayRepository repo = WayRepository.getInstance();
            repo.clearListAndSave();
            messages.add(new ChatEntry("system", "Все вейпоинты удалены по команде ИИ"));
            return;
        }

        if (lower.contains("listwp") || lower.contains("список вейпоинтов")) {
            listWaypoints();
            return;
        }

        Matcher delMatcher = Pattern.compile(
                "DELWP:\\s*(-?\\d{1,7})\\s+(-?\\d{1,7})\\s+(-?\\d{1,7})"
        ).matcher(response);
        if (delMatcher.find()) {
            String result = deleteWaypoint(delMatcher.group(1), delMatcher.group(2), delMatcher.group(3));
            messages.add(new ChatEntry("system", result));
            return;
        }

        Matcher wpMatcher = WP_PATTERN.matcher(response);
        if (wpMatcher.find()) {
            createWaypoint(wpMatcher.group(1), wpMatcher.group(2), wpMatcher.group(3));
            return;
        }

        Matcher wpTag = Pattern.compile("WP:\\s*(-?\\d{1,7})\\s+(-?\\d{1,7})\\s+(-?\\d{1,7})").matcher(response);
        if (wpTag.find()) {
            createWaypoint(wpTag.group(1), wpTag.group(2), wpTag.group(3));
            return;
        }
    }

    private String createWaypoint(String xStr, String yStr, String zStr) {
        try {
            int x = Integer.parseInt(xStr);
            int y = Integer.parseInt(yStr);
            int z = Integer.parseInt(zStr);

            if (mc.getNetworkHandler() == null || mc.getNetworkHandler().getServerInfo() == null) {
                return "Нет подключения к серверу";
            }
            String server = mc.getNetworkHandler().getServerInfo().address;
            String name = x + " " + y + " " + z;
            BlockPos pos = new BlockPos(x, y, z);

            WayRepository repo = WayRepository.getInstance();
            if (repo.hasWay(name)) {
                return "Вейпоинт уже существует: " + name;
            }
            repo.addWayAndSave(name, pos, server);
            return "Вейпоинт создан: " + name;
        } catch (NumberFormatException ignored) {
            return "Неверные координаты";
        }
    }

    private String deleteWaypoint(String xStr, String yStr, String zStr) {
        try {
            int x = Integer.parseInt(xStr);
            int y = Integer.parseInt(yStr);
            int z = Integer.parseInt(zStr);

            WayRepository repo = WayRepository.getInstance();
            String name = x + " " + y + " " + z;

            if (repo.hasWay(name)) {
                repo.deleteWayAndSave(name);
                return "Вейпоинт удалён: " + name;
            }

            for (Way way : new ArrayList<>(repo.getWayList())) {
                if (way.name().contains(x + " ") && way.name().contains(z + "")) {
                    repo.deleteWayAndSave(way.name());
                    return "Вейпоинт удалён: " + way.name();
                }
            }
            return "Вейпоинт не найден: " + name;
        } catch (NumberFormatException ignored) {
            return "Неверные координаты";
        }
    }

    private String deleteWaypointByName(String query) {
        WayRepository repo = WayRepository.getInstance();
        String q = query.toLowerCase().trim();

        for (Way way : new ArrayList<>(repo.getWayList())) {
            if (way.name().toLowerCase().contains(q)) {
                repo.deleteWayAndSave(way.name());
                return "Вейпоинт удалён: " + way.name();
            }
        }
        return "Вейпоинт не найден: " + query;
    }

    private void listWaypoints() {
        WayRepository repo = WayRepository.getInstance();
        String server = repo.getCurrentServer();
        if (server.isEmpty()) {
            messages.add(new ChatEntry("system", "Нет подключения к серверу"));
            return;
        }

        List<String> names = repo.getWayNamesForServer(server);
        if (names.isEmpty()) {
            messages.add(new ChatEntry("system", "Нет вейпоинтов на этом сервере"));
            return;
        }

        StringBuilder sb = new StringBuilder("Вейпоинты (" + names.size() + "):\n");
        for (int i = 0; i < names.size(); i++) {
            sb.append(i + 1).append(". ").append(names.get(i));
            if (i < names.size() - 1) sb.append("\n");
        }
        messages.add(new ChatEntry("system", sb.toString()));
    }

    private void trimMessages() {
        while (messages.size() > 50) {
            messages.remove(0);
        }
    }

    @Override
    public void close() {
        super.close();
    }

    @Override
    public boolean shouldPause() {
        return true;
    }

    private static class ChatEntry {
        final String role;
        final String text;

        ChatEntry(String role, String text) {
            this.role = role;
            this.text = text;
        }
    }
}
