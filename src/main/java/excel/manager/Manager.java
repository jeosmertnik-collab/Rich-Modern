package excel.manager;

import lombok.Getter;
import excel.client.draggables.HudManager;
import excel.command.CommandManager;
import excel.events.api.EventManager;
import excel.modules.impl.combat.aura.attack.StrikerConstructor;
import excel.modules.module.*;
import excel.screens.clickgui.ClickGui;
import excel.util.config.ConfigSystem;
import excel.util.config.impl.bind.BindConfig;
import excel.util.config.impl.background.BackgroundConfig;
import excel.util.config.impl.cosmetics.CosmeticsManager;
import excel.util.config.impl.blockesp.BlockESPConfig;
import excel.util.config.impl.drag.DragConfig;
import excel.util.config.impl.friend.FriendConfig;
import excel.util.config.impl.prefix.PrefixConfig;
import excel.util.config.impl.proxy.ProxyConfig;
import excel.util.config.impl.staff.StaffConfig;
import excel.util.modules.ModuleProvider;
import excel.util.modules.ModuleSwitcher;
import excel.util.media.MediaPlayer;
import excel.util.render.shader.RenderCore;
import excel.util.render.shader.Scissor;
import excel.util.render.font.FontInitializer;
import excel.util.repository.macro.MacroRepository;
import excel.util.repository.way.WayRepository;
import excel.util.tps.TPSCalculate;

/**
 *  © 2026 Copyright Rich Client 2.0
 *        All Rights Reserved ®
 */

@Getter
public class Manager {
    public StrikerConstructor attackPerpetrator = new StrikerConstructor();
    private EventManager eventManager;
    private RenderCore renderCore;
    private Scissor scissor;
    private ModuleProvider moduleProvider;
    private ModuleRepository moduleRepository;
    private ModuleSwitcher moduleSwitcher;
    private ClickGui clickgui;
    private ConfigSystem configSystem;
    private CommandManager commandManager;
    private TPSCalculate tpsCalculate;
    private HudManager hudManager = new HudManager();
    private MediaPlayer mediaPlayer = new MediaPlayer();

    public void init() {
        MacroRepository.getInstance().init();
        WayRepository.getInstance().init();
        BlockESPConfig.getInstance().load();
        FriendConfig.getInstance().load();
        BackgroundConfig.getInstance().load();
        CosmeticsManager.getInstance().init();
        PrefixConfig.getInstance().load();
        StaffConfig.getInstance().load();
        ProxyConfig.getInstance().load();
        DragConfig.getInstance().load();
        BindConfig.getInstance();

        FontInitializer.register();

        tpsCalculate = new TPSCalculate();

        clickgui = new ClickGui();
        eventManager = new EventManager();
        renderCore = new RenderCore();
        scissor = new Scissor();
        hudManager = new HudManager();
        hudManager.initElements();
        moduleRepository = new ModuleRepository();
        moduleRepository.setup();
        moduleProvider = new ModuleProvider(moduleRepository.modules());
        moduleSwitcher = new ModuleSwitcher(moduleRepository.modules(), eventManager);
        configSystem = new ConfigSystem();
        configSystem.init();
        commandManager = new CommandManager();
        commandManager.init();

        mediaPlayer.init();
    }
}