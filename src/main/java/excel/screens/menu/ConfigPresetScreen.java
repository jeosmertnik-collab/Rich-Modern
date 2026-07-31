package excel.screens.menu;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;
import excel.util.config.ConfigSystem;
import excel.util.config.impl.ConfigPath;
import excel.util.config.impl.drag.DragConfig;
import excel.util.config.impl.friend.FriendConfig;
import excel.util.render.Render2D;
import excel.util.render.font.Fonts;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class ConfigPresetScreen extends Screen {

    private static final long ANIM_DURATION = 400;
    private static final Path PRESETS_DIR = ConfigPath.getConfigDirectory().getParent().resolve("presets");

    private final Screen parent;
    private long startTime;
    private float openProgress;

    private String inputText = "";
    private boolean inputFocused = false;
    private List<String> presets = new ArrayList<>();
    private float[] presetHover;
    private float[] deleteHover;
    private String statusMessage = "";
    private long statusTime = 0;
    private static final long STATUS_DURATION = 2000;

    public ConfigPresetScreen(Screen parent) {
        super(Text.of("Config Presets"));
        this.parent = parent;
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {}

    @Override
    protected void init() {
        super.init();
        startTime = Util.getMeasuringTimeMs();
        refreshPresets();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        long now = Util.getMeasuringTimeMs();
        openProgress = Math.min(1f, (now - startTime) / ANIM_DURATION);

        int sw = client.getWindow().getScaledWidth();
        int sh = client.getWindow().getScaledHeight();

        Render2D.rect(0, 0, sw, sh, ((int)(openProgress * 80) << 24) | 0x000000);

        float panelW = 400;
        float panelH = 300;
        float panelX = (sw - panelW) / 2f;
        float panelY = (sh - panelH) / 2f;
        panelY += (1f - easeOutBack(openProgress)) * 40f;

        int panelBg = (int) (openProgress * 220) << 24 | 0x0E0E1A;
        Render2D.rect(panelX, panelY, panelW, panelH, panelBg, 10);
        Render2D.outline(panelX, panelY, panelW, panelH, 1f, ((int)(openProgress * 120) << 24) | 0x323741, 10);

        float titleY = panelY + 18;
        Fonts.BOLD.drawCentered("Config Presets", panelX + panelW / 2f, titleY - 4, 12, withAlpha(0xFFFFFF, (int)(openProgress * 255)));

        float inputY = panelY + 45;
        float inputX = panelX + 15;
        float inputW = panelW - 30;
        float inputH = 28;

        int inputBg = withAlpha(0x1A1A2E, (int)(openProgress * 200));
        Render2D.rect(inputX, inputY, inputW, inputH, inputBg, 6);
        Render2D.outline(inputX, inputY, inputW, inputH, 1f,
                inputFocused ? withAlpha(0x6496FF, (int)(openProgress * 180)) : withAlpha(0x323741, (int)(openProgress * 80)), 6);

        String displayText = inputText.isEmpty() && !inputFocused ? "Название пресета..." : inputText;
        int textColor = inputText.isEmpty() && !inputFocused ? withAlpha(0x646464, (int)(openProgress * 180)) : withAlpha(0xFFFFFF, (int)(openProgress * 220));
        Fonts.REGULAR.draw(displayText, inputX + 10, inputY + (inputH - Fonts.REGULAR.getHeight(8)) / 2f, 8, textColor);

        float btnY = inputY;
        float saveBtnX = inputX + inputW - 70;
        float saveBtnW = 70;
        boolean saveHovered = isHover(mouseX, mouseY, saveBtnX, btnY, saveBtnW, inputH);
        int saveBg = saveHovered ? withAlpha(0x4CAF50, (int)(openProgress * 200)) : withAlpha(0x388E3C, (int)(openProgress * 160));
        Render2D.rect(saveBtnX, btnY, saveBtnW, inputH, saveBg, 6);
        Fonts.BOLD.drawCentered("Save", saveBtnX + saveBtnW / 2f, btnY + (inputH - Fonts.BOLD.getHeight(8)) / 2f, 8, withAlpha(0xFFFFFF, (int)(openProgress * 255)));

        float listY = inputY + inputH + 10;
        float listH = panelY + panelH - 10 - listY;
        float itemH = 30;

        int visibleCount = Math.min(presets.size(), (int)(listH / itemH));
        for (int i = 0; i < visibleCount; i++) {
            float itemY = listY + i * itemH;
            boolean hovered = isHover(mouseX, mouseY, inputX, itemY, inputW, itemH - 2);

            if (presetHover == null || i >= presetHover.length) break;
            presetHover[i] = MathHelper.lerp(0.15f, presetHover[i], hovered ? 1f : 0f);

            int itemBg = (int)(presetHover[i] * 30) << 24 | 0xFFFFFF;
            Render2D.rect(inputX, itemY, inputW, itemH - 2, withAlpha(0x12121E, (int)(openProgress * 150) + (int)(presetHover[i] * 40)), 6);

            Fonts.REGULAR.draw(presets.get(i), inputX + 12, itemY + (itemH - Fonts.REGULAR.getHeight(8)) / 2f, 8, withAlpha(0xCCCCCC, (int)(openProgress * 220)));

            float loadBtnX = inputX + inputW - 130;
            float loadBtnW = 55;
            float deleteBtnX = inputX + inputW - 70;
            float deleteBtnW = 55;

            boolean loadHovered = isHover(mouseX, mouseY, loadBtnX, itemY + 3, loadBtnW, itemH - 8);
            int loadBg = loadHovered ? withAlpha(0x4CAF50, (int)(openProgress * 180)) : withAlpha(0x2E7D32, (int)(openProgress * 120));
            Render2D.rect(loadBtnX, itemY + 3, loadBtnW, itemH - 8, loadBg, 4);
            Fonts.BOLD.drawCentered("Load", loadBtnX + loadBtnW / 2f, itemY + (itemH - Fonts.BOLD.getHeight(7)) / 2f, 7, withAlpha(0xFFFFFF, (int)(openProgress * 220)));

            boolean delHovered = isHover(mouseX, mouseY, deleteBtnX, itemY + 3, deleteBtnW, itemH - 8);
            int delBg = delHovered ? withAlpha(0xEF5350, (int)(openProgress * 180)) : withAlpha(0xC62828, (int)(openProgress * 120));
            Render2D.rect(deleteBtnX, itemY + 3, deleteBtnW, itemH - 8, delBg, 4);
            Fonts.BOLD.drawCentered("Del", deleteBtnX + deleteBtnW / 2f, itemY + (itemH - Fonts.BOLD.getHeight(7)) / 2f, 7, withAlpha(0xFFFFFF, (int)(openProgress * 220)));
        }

        if (!statusMessage.isEmpty() && now - statusTime < STATUS_DURATION) {
            float statusAlpha = Math.min(1f, (STATUS_DURATION - (now - statusTime)) / 500f);
            Fonts.REGULAR.drawCentered(statusMessage, panelX + panelW / 2f, panelY + panelH + 15, 8, withAlpha(0xA0A0A0, (int)(openProgress * statusAlpha * 255)));
        }
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (click.button() != 0) return super.mouseClicked(click, doubled);
        float mx = (float) click.x();
        float my = (float) click.y();

        int sw = client.getWindow().getScaledWidth();
        int sh = client.getWindow().getScaledHeight();

        float panelW = 400;
        float panelH = 300;
        float panelX = (sw - panelW) / 2f;
        float panelY = (sh - panelH) / 2f;
        panelY += (1f - easeOutBack(openProgress)) * 40f;

        float inputY = panelY + 45;
        float inputX = panelX + 15;
        float inputW = panelW - 30;
        float inputH = 28;

        if (isHover(mx, my, inputX, inputY, inputW - 70, inputH)) {
            inputFocused = true;
            return true;
        }

        float saveBtnX = inputX + inputW - 70;
        if (isHover(mx, my, saveBtnX, inputY, 70, inputH)) {
            savePreset();
            return true;
        }

        float listY = inputY + inputH + 10;
        float itemH = 30;
        int visibleCount = Math.min(presets.size(), (int)((panelY + panelH - 10 - listY) / itemH));

        for (int i = 0; i < visibleCount; i++) {
            float itemY = listY + i * itemH;
            float loadBtnX = inputX + inputW - 130;
            float deleteBtnX = inputX + inputW - 70;

            if (isHover(mx, my, loadBtnX, itemY + 3, 55, itemH - 8)) {
                loadPreset(presets.get(i));
                return true;
            }
            if (isHover(mx, my, deleteBtnX, itemY + 3, 55, itemH - 8)) {
                deletePreset(presets.get(i));
                return true;
            }
        }

        inputFocused = false;
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (inputFocused) {
            int cp = input.codepoint();
            if (cp >= 32 && cp < 127 && inputText.length() < 30) {
                inputText += (char) cp;
            }
            return true;
        }
        return super.charTyped(input);
    }

    @Override
    public boolean keyPressed(KeyInput keyInput) {
        if (inputFocused) {
            int key = keyInput.key();
            if (key == GLFW.GLFW_KEY_BACKSPACE && !inputText.isEmpty()) {
                inputText = inputText.substring(0, inputText.length() - 1);
                return true;
            }
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                savePreset();
                return true;
            }
        }
        if (keyInput.key() == GLFW.GLFW_KEY_ESCAPE) {
            client.setScreen(parent);
            return true;
        }
        return super.keyPressed(keyInput);
    }

    private void savePreset() {
        String name = inputText.trim();
        if (name.isEmpty()) {
            showStatus("Введи название пресета");
            return;
        }
        try {
            Path configRoot = ConfigPath.getConfigDirectory().getParent();
            Path presetDir = PRESETS_DIR.resolve(name);
            if (Files.exists(presetDir)) {
                deleteDirectory(presetDir);
            }
            Files.createDirectories(presetDir);
            copyConfigsTo(configRoot, presetDir);
            showStatus("Пресет \"" + name + "\" сохранён!");
            inputText = "";
            refreshPresets();
        } catch (Exception e) {
            showStatus("Ошибка: " + e.getMessage());
        }
    }

    private void loadPreset(String name) {
        try {
            Path presetDir = PRESETS_DIR.resolve(name);
            if (!Files.exists(presetDir)) {
                showStatus("Пресет не найден!");
                return;
            }
            ConfigSystem.getInstance().save();
            Path configRoot = ConfigPath.getConfigDirectory().getParent();
            copyConfigsTo(presetDir, configRoot);
            ConfigSystem.getInstance().load();
            FriendConfig.getInstance().load();
            DragConfig.getInstance().load();
            showStatus("Пресет \"" + name + "\" загружен!");
        } catch (Exception e) {
            showStatus("Ошибка: " + e.getMessage());
        }
    }

    private void deletePreset(String name) {
        try {
            Path presetDir = PRESETS_DIR.resolve(name);
            if (Files.exists(presetDir)) {
                deleteDirectory(presetDir);
            }
            showStatus("Пресет \"" + name + "\" удалён!");
            refreshPresets();
        } catch (Exception e) {
            showStatus("Ошибка: " + e.getMessage());
        }
    }

    private void refreshPresets() {
        presets = getPresets();
        presetHover = new float[presets.size()];
        deleteHover = new float[presets.size()];
    }

    private void showStatus(String msg) {
        statusMessage = msg;
        statusTime = Util.getMeasuringTimeMs();
    }

    private List<String> getPresets() {
        List<String> list = new ArrayList<>();
        if (Files.exists(PRESETS_DIR)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(PRESETS_DIR)) {
                for (Path entry : stream) {
                    if (Files.isDirectory(entry)) {
                        list.add(entry.getFileName().toString());
                    }
                }
            } catch (IOException ignored) {}
        }
        return list;
    }

    private void copyConfigsTo(Path sourceDir, Path targetDir) throws IOException {
        Files.createDirectories(targetDir);
        try (Stream<Path> stream = Files.walk(sourceDir)) {
            stream.filter(Files::isRegularFile)
                    .filter(source -> !source.startsWith(PRESETS_DIR))
                    .forEach(source -> {
                try {
                    Path relative = sourceDir.relativize(source);
                    Path target = targetDir.resolve(relative);
                    Files.createDirectories(target.getParent());
                    Files.copy(source, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }

    private void deleteDirectory(Path dir) throws IOException {
        try (Stream<Path> stream = Files.walk(dir)) {
            stream.sorted((a, b) -> b.compareTo(a)).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {}
            });
        }
    }

    private boolean isHover(float mx, float my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private int withAlpha(int color, int alpha) {
        alpha = MathHelper.clamp(alpha, 0, 255);
        return (alpha << 24) | (color & 0xFFFFFF);
    }

    private float easeOutBack(float t) {
        float c1 = 1.70158f;
        float c3 = c1 + 1f;
        return 1 + c3 * (float) Math.pow(t - 1, 3) + c1 * (float) Math.pow(t - 1, 2);
    }
}
