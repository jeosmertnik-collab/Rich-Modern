package excel.util.discord;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import excel.Initialization;
import excel.events.api.EventManager;
import excel.events.api.EventHandler;
import excel.events.impl.GameLeftEvent;
import excel.events.impl.WorldLoadEvent;
import excel.modules.module.ModuleRepository;
import excel.modules.module.ModuleStructure;
import excel.util.network.Network;

import java.lang.reflect.Method;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class DiscordPresence {

    private static final String APPLICATION_ID = "1395029393345388645";
    private static DiscordPresence instance;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "Discord-RPC");
        t.setDaemon(true);
        return t;
    });
    private volatile boolean running = false;
    private long startTime;

    private boolean available = false;
    private Object handlers;
    private Method initMethod, clearMethod, runCallbacksMethod, updateMethod, shutdownMethod;
    private Class<?> rpcClass, presenceClass, handlersClass;

    public void init() {
        instance = this;
        try {
            handlersClass = Class.forName("net.arikia.dev.drpc.DiscordEventHandlers");
            rpcClass = Class.forName("net.arikia.dev.drpc.DiscordRPC");
            presenceClass = Class.forName("net.arikia.dev.drpc.DiscordRichPresence");

            initMethod = rpcClass.getMethod("discordInitialize", String.class, handlersClass, boolean.class);
            clearMethod = rpcClass.getMethod("discordClearPresence");
            runCallbacksMethod = rpcClass.getMethod("discordRunCallbacks");
            updateMethod = rpcClass.getMethod("discordUpdatePresence", presenceClass);
            shutdownMethod = rpcClass.getMethod("discordShutdown");

            handlers = handlersClass.getConstructor().newInstance();
            initMethod.invoke(null, APPLICATION_ID, handlers, false);
            clearMethod.invoke(null);
            startTime = System.currentTimeMillis() / 1000;
            available = true;
            running = true;

            scheduler.scheduleAtFixedRate(() -> {
                try {
                    runCallbacksMethod.invoke(null);
                } catch (Exception ignored) {}
            }, 2, 2, TimeUnit.SECONDS);

            scheduler.scheduleAtFixedRate(this::update, 5, 5, TimeUnit.SECONDS);

            EventManager.register(this);
        } catch (Throwable t) {
            System.err.println("[Rich] Failed to initialize Discord RPC: " + t.getMessage());
            running = false;
            available = false;
        }
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        if (!running) return;
        update();
    }

    @EventHandler
    public void onGameLeft(GameLeftEvent event) {
        if (!running) return;
        update();
    }

    public void update() {
        if (!running || !available) return;
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            Object presence = presenceClass.getConstructor().newInstance();

            presenceClass.getField("startTimestamp").set(presence, startTime);
            presenceClass.getField("largeImageKey").set(presence, "rich_logo");
            presenceClass.getField("largeImageText").set(presence, "Excel Client");

            ClientPlayerEntity player = mc.player;
            if (player != null && mc.world != null) {
                String server = Network.getServer();
                String username = player.getName().getString();
                int health = (int) player.getHealth();
                int maxHealth = (int) player.getMaxHealth();

                int enabledCount = 0;
                int totalCount = 0;
                try {
                    ModuleRepository repo = Initialization.getInstance().getManager().getModuleRepository();
                    if (repo != null) {
                        for (ModuleStructure m : repo.modules()) {
                            totalCount++;
                            if (m.isState()) enabledCount++;
                        }
                    }
                } catch (Exception ignored) {}

                String details = username + " | " + health + "/" + maxHealth + " HP";
                String moduleInfo = "Modules: " + enabledCount + "/" + totalCount;

                if (!server.equals("Vanilla")) {
                    presenceClass.getField("state").set(presence, server);
                } else {
                    presenceClass.getField("state").set(presence, "Singleplayer");
                }
                presenceClass.getField("details").set(presence, details);
                presenceClass.getField("smallImageKey").set(presence, "modules");
                presenceClass.getField("smallImageText").set(presence, moduleInfo);
            } else {
                presenceClass.getField("state").set(presence, "Main Menu");
                presenceClass.getField("details").set(presence, "Selecting server...");
                presenceClass.getField("smallImageKey").set(presence, "menu");
                presenceClass.getField("smallImageText").set(presence, "Excel Client");
            }

            updateMethod.invoke(null, presence);
        } catch (Exception ignored) {}
    }

    public void shutdown() {
        running = false;
        try {
            EventManager.unregister(this);
        } catch (Exception ignored) {}
        try {
            scheduler.shutdownNow();
        } catch (Exception ignored) {}
        try {
            if (available && shutdownMethod != null) {
                shutdownMethod.invoke(null);
            }
        } catch (Exception ignored) {}
    }

    public static DiscordPresence getInstance() {
        return instance;
    }
}
