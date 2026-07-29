package excel.util.config.impl.cosmetics;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import excel.util.config.impl.consolelogger.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class CosmeticsConfig {
    private static CosmeticsConfig instance;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Path configPath;

    private String character = "default";
    private String wing = "none";
    private String mask = "none";

    private CosmeticsConfig() {
        Path configDir = Paths.get("Excel", "configs");
        try {
            Files.createDirectories(configDir);
        } catch (IOException ignored) {}
        configPath = configDir.resolve("cosmetics.json");
    }

    public static CosmeticsConfig getInstance() {
        if (instance == null) {
            instance = new CosmeticsConfig();
        }
        return instance;
    }

    public void load() {
        try {
            if (!Files.exists(configPath)) {
                Logger.info("CosmeticsConfig: No config file found, using defaults.");
                return;
            }
            String json = Files.readString(configPath, StandardCharsets.UTF_8);
            if (json == null || json.trim().isEmpty()) return;

            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (root.has("character")) character = root.get("character").getAsString();
            if (root.has("wing")) wing = root.get("wing").getAsString();
            if (root.has("mask")) mask = root.get("mask").getAsString();
            Logger.success("CosmeticsConfig: loaded successfully!");
        } catch (Exception e) {
            Logger.error("CosmeticsConfig: Load failed! " + e.getMessage());
        }
    }

    public String getCharacter() { return character; }
    public String getWing() { return wing; }
    public String getMask() { return mask; }
}
