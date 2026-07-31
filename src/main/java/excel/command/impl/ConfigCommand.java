package excel.command.impl;

import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import excel.command.Command;
import excel.command.CommandManager;
import excel.command.helpers.Paginator;
import excel.command.helpers.TabCompleteHelper;
import excel.util.config.ConfigSystem;
import excel.util.config.impl.ConfigPath;
import excel.util.config.impl.drag.DragConfig;
import excel.util.config.impl.friend.FriendConfig;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import static excel.command.impl.HelpCommand.getLine;

public class ConfigCommand extends Command {

    private static final String PRESETS_DIR = "presets";

    public ConfigCommand() {
        super("config", "Управление конфигурациями", "cfg");
    }

    @Override
    public void execute(String label, String[] args) {
        CommandManager manager = CommandManager.getInstance();

        String arg = args.length > 0 ? args[0].toLowerCase(Locale.US) : "list";

        switch (arg) {
            case "save" -> {
                if (args.length < 2) {
                    ConfigSystem.getInstance().save();
                    logDirect("Конфигурация сохранена!");
                    return;
                }
                String name = args[1];
                try {
                    Path configRoot = getConfigRoot();
                    Path presetDir = getPresetDir(name);
                    if (Files.exists(presetDir)) {
                        deleteDirectory(presetDir);
                    }
                    Files.createDirectories(presetDir);
                    copyConfigsTo(configRoot, presetDir);
                    logDirect("Пресет §b" + name + " §fсохранён! Все настройки скопированы.");
                } catch (Exception e) {
                    logDirect("Ошибка при сохранении пресета: " + e.getMessage(), Formatting.RED);
                }
            }
            case "load" -> {
                if (args.length < 2) {
                    logDirect("Использование: config load <name>", Formatting.RED);
                    return;
                }
                String name = args[1];
                Path presetDir = getPresetDir(name);
                if (!Files.exists(presetDir)) {
                    logDirect("Пресет §b" + name + " §fне найден!", Formatting.RED);
                    return;
                }
                try {
                    ConfigSystem.getInstance().save();
                    Path configRoot = getConfigRoot();
                    copyConfigsTo(presetDir, configRoot);
                    ConfigSystem.getInstance().load();
                    FriendConfig.getInstance().load();
                    DragConfig.getInstance().load();
                    logDirect("Пресет §b" + name + " §fзагружен!");
                } catch (Exception e) {
                    logDirect("Ошибка при загрузке пресета: " + e.getMessage(), Formatting.RED);
                }
            }
            case "delete" -> {
                if (args.length < 2) {
                    logDirect("Использование: config delete <name>", Formatting.RED);
                    return;
                }
                String name = args[1];
                Path presetDir = getPresetDir(name);
                if (!Files.exists(presetDir)) {
                    logDirect("Пресет §b" + name + " §fне найден!", Formatting.RED);
                    return;
                }
                try {
                    deleteDirectory(presetDir);
                    logDirect("Пресет §b" + name + " §fудалён!");
                } catch (Exception e) {
                    logDirect("Ошибка при удалении пресета: " + e.getMessage(), Formatting.RED);
                }
            }
            case "list" -> {
                int page = 1;
                if (args.length > 1) {
                    try {
                        page = Integer.parseInt(args[1]);
                    } catch (NumberFormatException ignored) {}
                }

                List<String> presets = getPresets();

                if (presets.isEmpty()) {
                    logDirect("Пресеты не найдены!", Formatting.RED);
                    return;
                }

                Paginator<String> paginator = new Paginator<>(presets);
                paginator.setPage(page);

                paginator.display(
                        () -> {
                            logDirectRaw(Text.literal(getLine()));
                            logDirect("§f§lСПИСОК ПРЕСЕТОВ");
                            logDirectRaw(Text.literal(getLine()));
                        },
                        preset -> {
                            MutableText component = Text.literal("  §b● §f" + preset);

                            MutableText hoverText = Text.literal("§7Нажмите чтобы загрузить §f" + preset);
                            String loadCommand = manager.getPrefix() + "config load " + preset;

                            component.setStyle(component.getStyle()
                                    .withHoverEvent(new HoverEvent.ShowText(hoverText))
                                    .withClickEvent(new ClickEvent.RunCommand(loadCommand)));

                            return component;
                        },
                        manager.getPrefix() + label + " list"
                );
            }
            case "dir" -> {
                try {
                    Path configRoot = getConfigRoot();
                    String os = System.getProperty("os.name").toLowerCase();
                    ProcessBuilder pb;
                    if (os.contains("win")) {
                        pb = new ProcessBuilder("explorer", configRoot.toAbsolutePath().toString());
                    } else if (os.contains("mac")) {
                        pb = new ProcessBuilder("open", configRoot.toAbsolutePath().toString());
                    } else {
                        pb = new ProcessBuilder("xdg-open", configRoot.toAbsolutePath().toString());
                    }
                    pb.start();
                    logDirect("Папка с конфигами открыта!");
                } catch (IOException e) {
                    logDirect("Папка с конфигами не найдена! " + e.getMessage(), Formatting.RED);
                }
            }
            default -> {
                logDirectRaw(Text.literal(getLine()));
                logDirect("§f§lИСПОЛЬЗОВАНИЕ");
                logDirectRaw(Text.literal(getLine()));
                logDirect("§7> config save <name> §8- §fСохраняет пресет (все файлы).");
                logDirect("§7> config load <name> §8- §fЗагружает пресет.");
                logDirect("§7> config delete <name> §8- §fУдаляет пресет.");
                logDirect("§7> config list §8- §fСписок пресетов.");
                logDirect("§7> config dir §8- §fОткрывает папку с конфигами.");
                logDirectRaw(Text.literal(getLine()));
            }
        }
    }

    @Override
    public Stream<String> tabComplete(String label, String[] args) {
        if (args.length == 1) {
            return new TabCompleteHelper()
                    .append("save", "load", "delete", "list", "dir")
                    .sortAlphabetically()
                    .filterPrefix(args[0])
                    .stream();
        }
        if (args.length == 2) {
            String action = args[0].toLowerCase();
            if (action.equals("load") || action.equals("delete")) {
                return new TabCompleteHelper()
                        .append(getPresets().toArray(new String[0]))
                        .filterPrefix(args[1])
                        .stream();
            }
        }
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "Управление пресетами конфигов";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "Сохраняй и загружай полные пресеты настроек (модули, друзья, HUD и т.д.)",
                "Использование:",
                "> config save <name> - Сохраняет пресет (все файлы).",
                "> config load <name> - Загружает пресет.",
                "> config delete <name> - Удаляет пресет.",
                "> config list - Список пресетов.",
                "> config dir - Открывает папку с конфигами."
        );
    }

    private Path getConfigRoot() {
        return ConfigPath.getConfigDirectory().getParent();
    }

    private Path getPresetDir(String name) {
        return getConfigRoot().resolve(PRESETS_DIR).resolve(name);
    }

    private List<String> getPresets() {
        List<String> presets = new ArrayList<>();
        Path presetsPath = getConfigRoot().resolve(PRESETS_DIR);
        if (Files.exists(presetsPath)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(presetsPath)) {
                for (Path entry : stream) {
                    if (Files.isDirectory(entry)) {
                        presets.add(entry.getFileName().toString());
                    }
                }
            } catch (IOException ignored) {}
        }
        return presets;
    }

    private void copyConfigsTo(Path sourceDir, Path targetDir) throws IOException {
        Files.createDirectories(targetDir);
        try (Stream<Path> stream = Files.walk(sourceDir)) {
            stream.filter(Files::isRegularFile)
                    .filter(source -> !source.startsWith(getConfigRoot().resolve(PRESETS_DIR)))
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
