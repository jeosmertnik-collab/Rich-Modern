package excel.util.config.impl.background;

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

public class BackgroundConfig {
    private static BackgroundConfig instance;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Path configPath;

    private String backgroundType = "SOLID";
    private int solidColor = 0x0C0F12;
    private int gradientTop = 0x0C0F12;
    private int gradientBottom = 0x1a1f2e;
    private String backgroundImage = "";
    private boolean particlesEnabled = true;

    private BackgroundConfig() {
        Path configDir = Paths.get("Excel", "configs");
        try {
            Files.createDirectories(configDir);
        } catch (IOException ignored) {}
        configPath = configDir.resolve("background.json");
    }

    public static BackgroundConfig getInstance() {
        if (instance == null) {
            instance = new BackgroundConfig();
        }
        return instance;
    }

    public void save() {
        try {
            JsonObject root = new JsonObject();
            root.addProperty("backgroundType", backgroundType);
            root.addProperty("solidColor", solidColor);
            root.addProperty("gradientTop", gradientTop);
            root.addProperty("gradientBottom", gradientBottom);
            root.addProperty("backgroundImage", backgroundImage);
            root.addProperty("particlesEnabled", particlesEnabled);
            Files.writeString(configPath, gson.toJson(root), StandardCharsets.UTF_8);
            Logger.success("BackgroundConfig: background.json saved successfully!");
        } catch (IOException e) {
            Logger.error("BackgroundConfig: Save failed! " + e.getMessage());
        }
    }

    public void load() {
        try {
            if (!Files.exists(configPath)) {
                Logger.info("BackgroundConfig: No config file found, using defaults.");
                return;
            }

            String json = Files.readString(configPath, StandardCharsets.UTF_8);
            if (json == null || json.trim().isEmpty()) {
                Logger.error("BackgroundConfig: Config file is empty.");
                return;
            }

            JsonObject root = JsonParser.parseString(json).getAsJsonObject();

            if (root.has("backgroundType"))
                backgroundType = root.get("backgroundType").getAsString();
            if (root.has("solidColor"))
                solidColor = root.get("solidColor").getAsInt();
            if (root.has("gradientTop"))
                gradientTop = root.get("gradientTop").getAsInt();
            if (root.has("gradientBottom"))
                gradientBottom = root.get("gradientBottom").getAsInt();
            if (root.has("backgroundImage"))
                backgroundImage = root.get("backgroundImage").getAsString();
            if (root.has("particlesEnabled"))
                particlesEnabled = root.get("particlesEnabled").getAsBoolean();

            Logger.success("BackgroundConfig: background.json loaded successfully!");
        } catch (Exception e) {
            Logger.error("BackgroundConfig: Load failed! " + e.getMessage());
        }
    }

    public String getBackgroundType() {
        return backgroundType;
    }

    public void setBackgroundType(String backgroundType) {
        this.backgroundType = backgroundType;
    }

    public int getSolidColor() {
        return solidColor;
    }

    public void setSolidColor(int solidColor) {
        this.solidColor = solidColor;
    }

    public int getGradientTop() {
        return gradientTop;
    }

    public void setGradientTop(int gradientTop) {
        this.gradientTop = gradientTop;
    }

    public int getGradientBottom() {
        return gradientBottom;
    }

    public void setGradientBottom(int gradientBottom) {
        this.gradientBottom = gradientBottom;
    }

    public String getBackgroundImage() {
        return backgroundImage;
    }

    public void setBackgroundImage(String backgroundImage) {
        this.backgroundImage = backgroundImage;
    }

    public boolean isParticlesEnabled() {
        return particlesEnabled;
    }

    public void setParticlesEnabled(boolean particlesEnabled) {
        this.particlesEnabled = particlesEnabled;
    }
}
