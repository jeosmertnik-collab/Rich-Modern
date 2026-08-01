package excel.screens.clickgui.impl.configs.handler;

import lombok.Getter;
import lombok.Setter;
import excel.util.config.ConfigSystem;
import excel.util.config.impl.ConfigPath;
import excel.util.config.impl.drag.DragConfig;
import excel.util.config.impl.friend.FriendConfig;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@Getter
@Setter
public class ConfigDataHandler {

    private static final String PRESETS_DIR = "presets";

    private final List<String> configs = new ArrayList<>();
    private final ConfigAnimationHandler animationHandler;

    private String selectedConfig = null;
    private boolean isCreating = false;
    private String newConfigName = "";

    private double scrollOffset = 0;
    private double targetScrollOffset = 0;
    private float scrollTopFade = 0f;
    private float scrollBottomFade = 0f;

    public ConfigDataHandler(ConfigAnimationHandler animationHandler) {
        this.animationHandler = animationHandler;
    }

    public void refreshConfigs() {
        List<String> oldConfigs = new ArrayList<>(configs);
        configs.clear();
        Path presetsPath = getPresetsRoot();
        if (Files.exists(presetsPath)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(presetsPath)) {
                for (Path entry : stream) {
                    if (Files.isDirectory(entry)) {
                        configs.add(entry.getFileName().toString());
                    }
                }
            } catch (IOException ignored) {}
        }

        for (String config : configs) {
            if (!oldConfigs.contains(config)) {
                animationHandler.getItemAppearAnimations().put(config, 0f);
            }
        }
    }

    public void updateScroll(float deltaTime) {
        scrollOffset += (targetScrollOffset - scrollOffset) * 12f * deltaTime;
    }

    public void updateScrollFades(float visibleHeight) {
        float contentHeight = configs.size() * 27f;

        if (contentHeight <= visibleHeight) {
            scrollTopFade = 0f;
            scrollBottomFade = 0f;
            return;
        }

        float maxScroll = contentHeight - visibleHeight;
        scrollTopFade = (float) Math.min(1f, -scrollOffset / 20f);
        scrollBottomFade = (float) Math.min(1f, (maxScroll + scrollOffset) / 20f);
    }

    public void handleScroll(double vertical, float visibleHeight) {
        float contentHeight = configs.size() * 27f;
        float maxScroll = Math.max(0, contentHeight - visibleHeight);
        targetScrollOffset += vertical * 25;
        targetScrollOffset = Math.max(-maxScroll, Math.min(0, targetScrollOffset));
    }

    public boolean saveConfig(String name) {
        if (name.equalsIgnoreCase("autoconfig")) {
            return false;
        }

        try {
            Path presetDir = getPresetDir(name);
            if (Files.exists(presetDir)) {
                deleteDirectory(presetDir);
            }
            Files.createDirectories(presetDir);
            copyConfigsTo(getConfigRoot(), presetDir);
            refreshConfigs();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean loadConfig(String name) {
        try {
            Path presetDir = getPresetDir(name);
            if (!Files.exists(presetDir)) {
                return false;
            }
            ConfigSystem.getInstance().save();
            copyConfigsTo(presetDir, getConfigRoot());
            ConfigSystem.getInstance().load();
            FriendConfig.getInstance().load();
            DragConfig.getInstance().load();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean refreshConfig(String name) {
        try {
            Path presetDir = getPresetDir(name);
            if (!Files.exists(presetDir)) {
                return false;
            }
            deleteDirectory(presetDir);
            Files.createDirectories(presetDir);
            copyConfigsTo(getConfigRoot(), presetDir);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean deleteConfig(String name) {
        try {
            Path presetDir = getPresetDir(name);
            if (Files.exists(presetDir)) {
                deleteDirectory(presetDir);
                if (name.equals(selectedConfig)) {
                    selectedConfig = null;
                }
                refreshConfigs();
                return true;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    public void toggleCreating() {
        isCreating = !isCreating;
        if (!isCreating) {
            newConfigName = "";
        }
    }

    public void appendChar(char chr) {
        if (newConfigName.length() < 32 && (Character.isLetterOrDigit(chr) || chr == '_' || chr == '-')) {
            newConfigName += chr;
        }
    }

    public void removeLastChar() {
        if (!newConfigName.isEmpty()) {
            newConfigName = newConfigName.substring(0, newConfigName.length() - 1);
        }
    }

    public void clearNewConfigName() {
        newConfigName = "";
    }

    private Path getConfigRoot() {
        return ConfigPath.getConfigDirectory().getParent();
    }

    private Path getPresetsRoot() {
        return getConfigRoot().resolve(PRESETS_DIR);
    }

    private Path getPresetDir(String name) {
        return getPresetsRoot().resolve(name);
    }

    private void copyConfigsTo(Path sourceDir, Path targetDir) throws IOException {
        Files.createDirectories(targetDir);
        try (Stream<Path> stream = Files.walk(sourceDir)) {
            stream.filter(Files::isRegularFile)
                    .filter(source -> !source.startsWith(getPresetsRoot()))
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
}
