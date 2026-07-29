package excel.util.config.impl.cosmetics;

public class CosmeticsManager {
    private static CosmeticsManager instance;
    private final CosmeticsConfig config;

    private CosmeticsManager() {
        config = CosmeticsConfig.getInstance();
    }

    public static CosmeticsManager getInstance() {
        if (instance == null) {
            instance = new CosmeticsManager();
        }
        return instance;
    }

    public void init() {
        config.load();
    }

    public String getSelectedWing() {
        return config.getWing();
    }

    public String getSelectedMask() {
        return config.getMask();
    }

    public String getSelectedCharacter() {
        return config.getCharacter();
    }

    public boolean isWingEnabled() {
        return !"none".equals(config.getWing());
    }

    public boolean isMaskEnabled() {
        return !"none".equals(config.getMask());
    }
}
