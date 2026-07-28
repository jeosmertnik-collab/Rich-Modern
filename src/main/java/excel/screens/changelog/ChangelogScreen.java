package excel.screens.changelog;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;
import excel.IMinecraft;
import excel.util.lang.Lang;
import excel.util.render.Render2D;
import excel.util.render.font.Fonts;

import java.awt.*;
import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

public class ChangelogScreen extends Screen implements IMinecraft {

    private static final String VERSION = "1.0.14";

    private static final List<ChangelogEntry> ENTRIES = new ArrayList<>();

    static {
        ENTRIES.add(new ChangelogEntry("1.0.14", "26.07.2026", new String[]{
                "Changelog теперь popup меню",
                "TargetHud компактный с предметами",
                "LeafFarmer — автофарм листвы мотыгой",
                "Лаунчер запоминает логин и пароль"
        }));
        ENTRIES.add(new ChangelogEntry("1.0.13", "26.07.2026", new String[]{
                "ClickGUI полностью заменён из Rich-Modern1",
                "Новая тема HUD: SFPRO шрифт, синий акцент",
                "TargetHud показывает экипировку (броня + руки)",
                "Цвет акцента меняется через настройки Hud",
                "ThirdPersonHud показывает ник аккаунта",
                "Changelog в панели конфигов ClickGUI",
                "Все HUD элементы обновлены под новую тему"
        }));
        ENTRIES.add(new ChangelogEntry("1.0.01", "25.07.2026", new String[]{
                "ClickGUI полностью переработан - новый дизайн 700x420",
                "Выбор цвета акцента интерфейса (8 пресетов + палитра)",
                "TargetHud синхронизируется с акцентным цветом",
                "IP сервера отображается в HUD (Info)",
                "Changelog при открытии ClickGUI",
                "Модули: BackSword, Wings (8 типов), ClanHelper",
                "AI Assistant (Home) с Ollama + TTS",
                "Discord Rich Presence (зеркало сервера)",
                "Electron лаунчер с подпиской и обновлениями",
                "Русский и English интерфейс"
        }));
    }

    private long startTime;
    private boolean initialized = false;

    private static final long ANIM_DURATION = 400;

    public ChangelogScreen() {
        super(Text.of("Changelog"));
    }

    public static boolean shouldShow() {
        try {
            Path configDir = Paths.get("Excel", "configs");
            if (!Files.exists(configDir)) Files.createDirectories(configDir);
            Path flagFile = configDir.resolve("changelog_seen.txt");
            if (Files.exists(flagFile)) {
                String seen = Files.readString(flagFile).trim();
                return !seen.equals(VERSION);
            }
            return true;
        } catch (Exception e) {
            return true;
        }
    }

    public static void markSeen() {
        try {
            Path configDir = Paths.get("Excel", "configs");
            if (!Files.exists(configDir)) Files.createDirectories(configDir);
            Files.writeString(configDir.resolve("changelog_seen.txt"), VERSION);
        } catch (Exception ignored) {}
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        long now = Util.getMeasuringTimeMs();
        if (!initialized) {
            startTime = now;
            initialized = true;
        }

        float elapsed = now - startTime;
        float animProgress = Math.min(1f, elapsed / ANIM_DURATION);

        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();

        int dimAlpha = (int) (140 * animProgress);
        Render2D.rect(0, 0, sw, sh, (dimAlpha << 24), 0);

        float panelW = 320f;
        float panelH = 300f;
        float panelX = (sw - panelW) / 2f;
        float panelY = (sh - panelH) / 2f;

        float slideOffset = (1f - easeOutBack(animProgress)) * 30f;
        panelY += slideOffset;

        int bgAlpha = (int) (220 * animProgress);
        Render2D.rect(panelX, panelY, panelW, panelH, (bgAlpha << 24) | 0x0E0E1A, 10);

        int borderAlpha = (int) (80 * animProgress);
        Render2D.outline(panelX, panelY, panelW, panelH, 0.5f,
                (borderAlpha << 24) | (0x6496FF), 10);

        int accentLineAlpha = (int) (150 * animProgress);
        Render2D.rect(panelX, panelY, panelW, 2,
                (accentLineAlpha << 24) | (0x6496FF), 10);

        int titleAlpha = (int) (255 * animProgress);
        Fonts.BOLD.draw(Lang.get().get("changelog"), panelX + 14, panelY + 12, 8f,
                (titleAlpha << 24) | 0xFFFFFF);

        String versionText = "v" + VERSION;
        float versionW = Fonts.BOLD.getWidth(versionText, 5f);
        Fonts.BOLD.draw(versionText, panelX + panelW - versionW - 14, panelY + 15, 5f,
                ((int) (160 * animProgress) << 24) | (0x6496FF));

        Render2D.rect(panelX + 14, panelY + 30, panelW - 28, 0.5f,
                ((int) (40 * animProgress) << 24) | 0x444460, 0);

        float contentY = panelY + 38;

        for (ChangelogEntry entry : ENTRIES) {
            int dateAlpha = (int) (140 * animProgress);
            Fonts.BOLD.draw(entry.date, panelX + 14, contentY, 5f,
                    (dateAlpha << 24) | 0x888898);
            contentY += 12;

            for (String line : entry.lines) {
                int bulletAlpha = (int) (180 * animProgress);
                int accentRgb = 0x006496FF;
                Render2D.rect(panelX + 18, contentY + 2.5f, 2.5f, 2.5f,
                        (bulletAlpha << 24) | accentRgb, 1.25f);

                Fonts.BOLD.draw(line, panelX + 26, contentY, 5.5f,
                        ((int) (220 * animProgress) << 24) | 0xD0D0E0);
                contentY += 11;
            }
            contentY += 4;
        }

        float closeBtnW = 60f;
        float closeBtnH = 16f;
        float closeBtnX = panelX + (panelW - closeBtnW) / 2f;
        float closeBtnY = panelY + panelH - closeBtnH - 12;

        boolean closeHovered = mouseX >= closeBtnX && mouseX <= closeBtnX + closeBtnW
                && mouseY >= closeBtnY && mouseY <= closeBtnY + closeBtnH;

        int closeBgAlpha = (int) ((closeHovered ? 80 : 40) * animProgress);
        Render2D.rect(closeBtnX, closeBtnY, closeBtnW, closeBtnH,
                (closeBgAlpha << 24) | (0x6496FF), 4);

        int closeBorderAlpha = (int) (60 * animProgress);
        Render2D.outline(closeBtnX, closeBtnY, closeBtnW, closeBtnH, 0.3f,
                (closeBorderAlpha << 24) | (0x6496FF), 4);

        String closeText = Lang.get().get("ok");
        float closeTextW = Fonts.BOLD.getWidth(closeText, 5.5f);
        int closeTextAlpha = (int) (255 * animProgress);
        Fonts.BOLD.draw(closeText, closeBtnX + (closeBtnW - closeTextW) / 2f, closeBtnY + 4, 5.5f,
                (closeTextAlpha << 24) | 0xFFFFFF);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        long now = Util.getMeasuringTimeMs();
        float animProgress = Math.min(1f, (now - startTime) / (float) ANIM_DURATION);

        float sw = mc.getWindow().getScaledWidth();
        float sh = mc.getWindow().getScaledHeight();
        float panelW = 320f;
        float panelH = 300f;
        float panelX = (sw - panelW) / 2f;
        float panelY = (sh - panelH) / 2f + (1f - easeOutBack(animProgress)) * 30f;

        float closeBtnW = 60f;
        float closeBtnH = 16f;
        float closeBtnX = panelX + (panelW - closeBtnW) / 2f;
        float closeBtnY = panelY + panelH - closeBtnH - 12;

        if (click.x() >= closeBtnX && click.x() <= closeBtnX + closeBtnW
                && click.y() >= closeBtnY && click.y() <= closeBtnY + closeBtnH) {
            markSeen();
            mc.setScreen(null);
            return true;
        }

        markSeen();
        mc.setScreen(null);
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.key() == GLFW.GLFW_KEY_ENTER
                || input.key() == GLFW.GLFW_KEY_ESCAPE
                || input.key() == GLFW.GLFW_KEY_SPACE) {
            markSeen();
            mc.setScreen(null);
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private float easeOutBack(float x) {
        float c1 = 1.70158f;
        float c3 = c1 + 1f;
        return 1f + c3 * (float) Math.pow(x - 1, 3) + c1 * (float) Math.pow(x - 1, 2);
    }

    private static class ChangelogEntry {
        final String version;
        final String date;
        final String[] lines;

        ChangelogEntry(String version, String date, String[] lines) {
            this.version = version;
            this.date = date;
            this.lines = lines;
        }
    }
}
