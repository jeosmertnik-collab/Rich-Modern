package rich.screens.menu;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerWarningScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.input.CharInput;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import rich.Initialization;
import rich.screens.account.AccountEntry;
import rich.screens.account.AccountRenderer;
import rich.util.config.impl.account.AccountConfig;
import rich.util.render.Render2D;
import rich.util.render.font.Fonts;
import rich.util.session.SessionChanger;

import java.awt.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class MainMenuScreen extends Screen {

    private static final Identifier BACKGROUND_TEXTURE = Identifier.of("rich", "textures/menu/backmenu.png");
    private static final Identifier LOGO_TEXTURE = Identifier.of("rich", "images/elements/logo.png");
    private static final float FIXED_GUI_SCALE = 2.0f;

    private static final long TRANSITION_DURATION = 600L;
    private static final long LOADING_MIN_DURATION = 2000L;
    private static final long LOADING_TEXT_DISPLAY = 2000L;
    private static final long LOADING_TEXT_TRANSITION = 350L;
    private static final float ZOOM_LEVEL = 1.08f;

    public static final int PARTICLE_COUNT = 50;

    private enum Phase { LOADING, TRANSITION, MENU }
    private Phase phase = Phase.LOADING;

    private long screenStartTime = 0L;
    private boolean initialized = false;
    private long lastRenderTime = 0L;

    private static float overlayProgress = 0f;
    private static boolean overlayComplete = false;

    private float animatedProgress = 0f;
    private float loadingAlpha = 1f;
    private float menuAlpha = 0f;
    private float transitionProgress = 0f;

    private int loadingTextIndex = 0;
    private float currentTextOffsetY = 0f;
    private float currentTextAlpha = 1f;
    private float newTextOffsetY = -12f;
    private float newTextAlpha = 0f;
    private long lastTextChangeTime = 0L;
    private boolean isTextTransitioning = false;
    private long textTransitionStartTime = 0L;
    private boolean allTextsShown = false;
    private long lastTextShownTime = 0L;
    private boolean loadingFadingOut = false;

    private float pulseTime = 0f;
    private float progressGlow = 0f;

    private static final String[] LOADING_TEXTS = {
            "Запуск паста клиент",
            "Панчан ответь",
            "Панчан где связь",
            "Панчан молодец"
    };

    private final List<LoadingParticle> loadingParticles = new ArrayList<>();

    private static final int MENU_PARTICLE_COUNT = 50;
    private static final float MENU_PARTICLE_FADE_THRESHOLD = 0.8f;
    private final List<MenuParticle> menuParticles = new ArrayList<>();
    private boolean menuParticlesInitialized = false;

    private enum View { MAIN_MENU, ALT_SCREEN }
    private View currentView = View.MAIN_MENU;

    private int hoveredButton = -1;
    private float[] buttonHoverProgress = new float[6];

    private final AccountRenderer accountRenderer;
    private final AccountConfig accountConfig;
    private String nicknameText = "";
    private boolean nicknameFieldFocused = false;
    private float scrollOffset = 0f;
    private float targetScrollOffset = 0f;

    private int lastWindowWidth = 0;
    private int lastWindowHeight = 0;

    private static class LoadingParticle {
        float x, y, speed, size, alpha, drift;

        LoadingParticle() { reset(true); }

        void reset(boolean randomY) {
            x = (float) (Math.random() * 2000);
            y = randomY ? (float) (Math.random() * 1200) : 1200 + (float) (Math.random() * 100);
            speed = 8f + (float) (Math.random() * 25f);
            size = 1f + (float) (Math.random() * 2.5f);
            alpha = 0.08f + (float) (Math.random() * 0.2f);
            drift = -15f + (float) (Math.random() * 30f);
        }

        void update(float dt) {
            y -= speed * dt;
            x += drift * dt;
            if (y < -20) reset(false);
        }
    }

    private static class MenuParticle {
        float x, y, vx, vy, size, alpha, lifetime, maxLifetime;
        boolean isDead = false;

        MenuParticle(float x, float y) {
            this.x = x;
            this.y = y;
            Random rand = new Random();
            this.vx = (rand.nextFloat() - 0.5f) * 0.2f;
            this.vy = (rand.nextFloat() - 0.5f) * 0.2f;
            this.size = 0.5f + rand.nextFloat() * 1f;
            this.alpha = 0.2f + rand.nextFloat() * 0.3f;
            this.maxLifetime = 10f + rand.nextFloat() * 10f;
            this.lifetime = 0f;
        }

        void update(float delta, int width, int height) {
            lifetime += delta / 60f;
            if (lifetime > maxLifetime * MENU_PARTICLE_FADE_THRESHOLD) {
                float fadeProgress = (lifetime - maxLifetime * MENU_PARTICLE_FADE_THRESHOLD) / (maxLifetime * (1f - MENU_PARTICLE_FADE_THRESHOLD));
                alpha = Math.max(0, alpha * (1f - fadeProgress));
            }
            if (lifetime >= maxLifetime) { isDead = true; return; }
            x += vx * delta;
            y += vy * delta;
            if (x < -10 || x > width + 10 || y < -10 || y > height + 10) isDead = true;
        }
    }

    public MainMenuScreen() {
        super(Text.literal("Main Menu"));
        for (int i = 0; i < 6; i++) buttonHoverProgress[i] = 0f;
        this.accountRenderer = new AccountRenderer();
        this.accountConfig = AccountConfig.getInstance();
        this.accountConfig.load();
    }

    @Override
    protected void init() {
        initialized = false;
        phase = Phase.LOADING;
        loadingAlpha = 1f;
        menuAlpha = 0f;
        transitionProgress = 0f;
        loadingFadingOut = false;
        allTextsShown = false;
        menuParticlesInitialized = false;
        currentView = View.MAIN_MENU;
        lastTextChangeTime = Util.getMeasuringTimeMs();

        for (int i = 0; i < 20; i++) loadingParticles.add(new LoadingParticle());

        try {
            String gameDir = System.getProperty("user.dir");
            java.io.File nickFile = new java.io.File(gameDir, "Rich/configs/lastnick.txt");
            if (nickFile.exists()) {
                String nick = new String(java.nio.file.Files.readAllBytes(nickFile.toPath())).trim();
                if (!nick.isEmpty()) {
                    nicknameText = nick;
                    SessionChanger.changeUsername(nick);
                }
            }
        } catch (Exception ignored) {
        }
    }

    public static void setOverlayProgress(float progress) {
        overlayProgress = MathHelper.clamp(progress, 0f, 1f);
    }

    public static void markOverlayComplete() {
        overlayComplete = true;
    }

    public static void resetOverlayState() {
        overlayProgress = 0f;
        overlayComplete = false;
    }

    private int getFixedScaledWidth() {
        if (client == null || client.getWindow() == null) return 960;
        return (int) Math.ceil((double) client.getWindow().getFramebufferWidth() / FIXED_GUI_SCALE);
    }

    private int getFixedScaledHeight() {
        if (client == null || client.getWindow() == null) return 540;
        return (int) Math.ceil((double) client.getWindow().getFramebufferHeight() / FIXED_GUI_SCALE);
    }

    private float toFixedCoord(double coord) {
        float currentScale = (float) client.getWindow().getScaleFactor();
        return (float) (coord * currentScale / FIXED_GUI_SCALE);
    }

    private float easeOutCubic(float t) {
        return 1f - (float) Math.pow(1f - t, 3);
    }

    private float easeOutBack(float t) {
        float c1 = 1.70158f;
        float c3 = c1 + 1f;
        return 1f + c3 * (float) Math.pow(t - 1, 3) + c1 * (float) Math.pow(t - 1, 2);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        long currentTime = Util.getMeasuringTimeMs();

        if (!initialized) {
            screenStartTime = currentTime;
            lastRenderTime = currentTime;
            initialized = true;
            lastTextChangeTime = currentTime;
        }

        float deltaTime = Math.min(delta, 0.05f);
        lastRenderTime = currentTime;

        int fixedWidth = getFixedScaledWidth();
        int fixedHeight = getFixedScaledHeight();

        if (lastWindowWidth != fixedWidth || lastWindowHeight != fixedHeight) {
            menuParticles.clear();
            menuParticlesInitialized = false;
            lastWindowWidth = fixedWidth;
            lastWindowHeight = fixedHeight;
        }

        pulseTime += deltaTime;
        progressGlow += deltaTime * 3f;
        animatedProgress = MathHelper.lerp(deltaTime * 5f, animatedProgress, overlayProgress);

        updatePhase(deltaTime, currentTime);

        float scaledMouseX = toFixedCoord(mouseX);
        float scaledMouseY = toFixedCoord(mouseY);

        Render2D.beginOverlay();

        if (phase == Phase.LOADING) {
            renderLoadingPhase(fixedWidth, fixedHeight, deltaTime, currentTime);
        } else if (phase == Phase.TRANSITION) {
            updateTransition(deltaTime);
            float la = loadingAlpha;
            float ma = menuAlpha;
            if (la > 0.001f) renderLoadingContent(fixedWidth, fixedHeight, deltaTime, currentTime, la);
            if (ma > 0.001f) renderMenuContent(fixedWidth, fixedHeight, scaledMouseX, scaledMouseY, currentTime, ma);
        } else {
            updateMenuParticles(deltaTime, fixedWidth, fixedHeight);
            updateButtonAnimations(deltaTime, scaledMouseX, scaledMouseY, fixedWidth, fixedHeight);
            renderMenuContent(fixedWidth, fixedHeight, scaledMouseX, scaledMouseY, currentTime, 1f);
        }

        Render2D.endOverlay();
    }

    private void updatePhase(float deltaTime, long currentTime) {
        if (phase == Phase.LOADING) {
            updateLoadingAnimations(deltaTime, currentTime);

            long elapsed = currentTime - screenStartTime;
            if (allTextsShown && overlayComplete && !loadingFadingOut && elapsed >= LOADING_MIN_DURATION) {
                loadingFadingOut = true;
            }

            if (loadingFadingOut) {
                loadingAlpha -= deltaTime * 2.5f;
                if (loadingAlpha <= 0f) {
                    loadingAlpha = 0f;
                    phase = Phase.TRANSITION;
                    transitionProgress = 0f;
                }
            }
        } else if (phase == Phase.TRANSITION) {
            transitionProgress += deltaTime / (TRANSITION_DURATION / 1000f);
            if (transitionProgress >= 1f) {
                transitionProgress = 1f;
                phase = Phase.MENU;
                menuAlpha = 1f;
                initMenuParticles(fixedWidth(), fixedHeight());
            }
        }
    }

    private void updateTransition(float deltaTime) {
        float t = easeOutCubic(MathHelper.clamp(transitionProgress, 0f, 1f));
        loadingAlpha = 1f - t;
        menuAlpha = t;
    }

    private void updateLoadingAnimations(float deltaTime, long currentTime) {
        for (LoadingParticle p : loadingParticles) p.update(deltaTime);

        if (!allTextsShown) {
            if (!isTextTransitioning) {
                long elapsed = currentTime - lastTextChangeTime;
                if (loadingTextIndex >= LOADING_TEXTS.length - 1) {
                    allTextsShown = true;
                    lastTextShownTime = currentTime;
                } else if (elapsed >= LOADING_TEXT_DISPLAY) {
                    isTextTransitioning = true;
                    textTransitionStartTime = currentTime;
                }
            }

            if (isTextTransitioning) {
                long elapsed = currentTime - textTransitionStartTime;
                float raw = MathHelper.clamp((float) elapsed / LOADING_TEXT_TRANSITION, 0f, 1f);
                float eased = easeOutCubic(raw);

                currentTextOffsetY = 14f * eased;
                currentTextAlpha = MathHelper.clamp(1f - eased * 1.5f, 0f, 1f);
                newTextOffsetY = -12f * (1f - eased);
                newTextAlpha = MathHelper.clamp(eased * 1.3f, 0f, 1f);

                if (raw >= 1f) {
                    isTextTransitioning = false;
                    loadingTextIndex++;
                    currentTextOffsetY = 0f;
                    currentTextAlpha = 1f;
                    newTextOffsetY = -12f;
                    newTextAlpha = 0f;
                    lastTextChangeTime = currentTime;

                    if (loadingTextIndex >= LOADING_TEXTS.length - 1) {
                        allTextsShown = true;
                        lastTextShownTime = currentTime;
                    }
                }
            }
        }
    }

    private void renderLoadingPhase(int width, int height, float deltaTime, long currentTime) {
        Render2D.backgroundImage(1f, ZOOM_LEVEL);

        renderLoadingContent(width, height, deltaTime, currentTime, 1f);
    }

    private void renderLoadingContent(int width, int height, float deltaTime, long currentTime, float alpha) {
        for (LoadingParticle p : loadingParticles) {
            int a = (int) (p.alpha * alpha * 255);
            if (a <= 0) continue;
            Render2D.rect(p.x, p.y, p.size, p.size, withAlpha(0xFFFFFFFF, a), p.size / 2f);
        }

        float centerX = width / 2f;
        float centerY = height / 2f - 40;
        int textAlpha = (int) (alpha * 255);
        float iconFontSize = 44f;
        float breathe = (float) Math.sin(pulseTime * 1.2f) * 1.5f;

        String icon = "A";
        float iconW = Fonts.ICONS.getWidth(icon, iconFontSize);
        float iconH = Fonts.ICONS.getHeight(iconFontSize);
        float iconX = centerX - iconW / 2f;
        float iconY = centerY - iconH / 2f + breathe;

        float glowPulse = 0.4f + 0.2f * (float) Math.sin(pulseTime * 1.5f);
        int glowAlpha = (int) (textAlpha * glowPulse);
        Fonts.ICONS.draw(icon, iconX, iconY + 3, iconFontSize, withAlpha(0xFFFF7276, glowAlpha));

        int shadowColor = withAlpha(0xFF000000, textAlpha / 3);
        Fonts.ICONS.draw(icon, iconX + 2, iconY + 2, iconFontSize, shadowColor);

        int mainColor = withAlpha(0xFFFFFFFF, textAlpha);
        Fonts.ICONS.draw(icon, iconX, iconY, iconFontSize, mainColor);

        float subtitleY = height / 2f - 8;
        int subtitleAlpha = (int) (alpha * 180);
        float subtitleFontSize = 9f;
        String subtitle = "R I C H  C L I E N T";
        float subtitleW = Fonts.BOLD.getWidth(subtitle, subtitleFontSize);

        float lineW = 30;
        float lineY = subtitleY + subtitleFontSize / 2f;
        float gap = 8;
        int lineAlpha = (int) (alpha * 40);
        int lineColor = withAlpha(0xFFFF7276, lineAlpha);

        Render2D.rect(centerX - subtitleW / 2f - gap - lineW, lineY, lineW, 0.5f, lineColor, 0);
        Render2D.rect(centerX + subtitleW / 2f + gap, lineY, lineW, 0.5f, lineColor, 0);

        Fonts.BOLD.draw(subtitle, centerX - subtitleW / 2f, subtitleY, subtitleFontSize, withAlpha(0xFFFFFFFF, subtitleAlpha));

        float barY = height / 2f + 12;
        float barW = 180;
        float barH = 3;
        float barX = centerX - barW / 2f;

        int bgAlpha = (int) (alpha * 60);
        Render2D.rect(barX, barY, barW, barH, withAlpha(0xFFFFFFFF, bgAlpha), 1.5f);

        if (animatedProgress > 0) {
            float filledW = barW * MathHelper.clamp(animatedProgress, 0f, 1f);
            int a1 = (int) (alpha * 200);
            int a2 = (int) (alpha * 140);
            Render2D.gradientRect(barX, barY, filledW, barH,
                    new int[]{
                            withAlpha(0xFFFF7276, a1),
                            withAlpha(0xFFFF9A76, a2),
                            withAlpha(0xFFFF7276, a1),
                            withAlpha(0xFFFF9A76, a2)
                    }, 1.5f);

            float glowPos = (float) ((progressGlow * 0.3f) % 1.0);
            float glowX = barX + filledW * glowPos;
            float glowW = 20;
            if (glowX + glowW > barX + filledW) glowW = barX + filledW - glowX;
            if (glowW > 0) {
                int shineA = (int) (alpha * 80);
                Render2D.rect(glowX, barY, glowW, barH, withAlpha(0xFFFFFFFF, shineA), 1.5f);
            }
        }

        String pct = (int) (animatedProgress * 100) + "%";
        float pctW = Fonts.BOLD.getWidth(pct, 6f);
        int pctAlpha = (int) (alpha * 120);
        Fonts.BOLD.draw(pct, barX + barW + 8, barY - 1, 6f, withAlpha(0xFFFFFFFF, pctAlpha));

        float textFontSize = 10f;
        float textBaseY = height / 2f + 30;

        if (currentTextAlpha > 0.01f && loadingTextIndex < LOADING_TEXTS.length) {
            String currentText = LOADING_TEXTS[loadingTextIndex];
            float currentWidth = Fonts.REGULARNEW.getWidth(currentText, textFontSize);
            int a = (int) (alpha * currentTextAlpha * 220);
            Fonts.REGULARNEW.draw(currentText, centerX - currentWidth / 2f, textBaseY + currentTextOffsetY, textFontSize, withAlpha(0xFFFFFFFF, a));
        }

        if (isTextTransitioning && newTextAlpha > 0.01f) {
            int nextIndex = loadingTextIndex + 1;
            if (nextIndex < LOADING_TEXTS.length) {
                String nextText = LOADING_TEXTS[nextIndex];
                float nextWidth = Fonts.REGULARNEW.getWidth(nextText, textFontSize);
                int a = (int) (alpha * newTextAlpha * 220);
                Fonts.REGULARNEW.draw(nextText, centerX - nextWidth / 2f, textBaseY + newTextOffsetY, textFontSize, withAlpha(0xFFFFFFFF, a));
            }
        }

        float bottomY = height - 20;
        int bottomAlpha = (int) (alpha * 50);
        float bottomFontSize = 6f;

        String version = "PastaRicha \u00b7 1.21.11";
        Fonts.BOLD.draw(version, 15, bottomY, bottomFontSize, withAlpha(0xFFFFFFFF, bottomAlpha));

        String copyright = "Panhan Client";
        float copyrightW = Fonts.BOLD.getWidth(copyright, bottomFontSize);
        Fonts.BOLD.draw(copyright, width - copyrightW - 15, bottomY, bottomFontSize, withAlpha(0xFFFFFFFF, bottomAlpha));

        if (!allTextsShown) {
            int dotCount = ((int) (pulseTime * 2f)) % 4;
            String dots = ".".repeat(dotCount);
            float dotsW = Fonts.BOLD.getWidth("...", bottomFontSize);
            Fonts.BOLD.draw(dots, width / 2f - dotsW / 2f, bottomY, bottomFontSize, withAlpha(0xFFFF7276, (int) (alpha * 80)));
        }
    }

    private void renderMenuContent(int screenWidth, int screenHeight, float mouseX, float mouseY, long currentTime, float alpha) {
        if (alpha < 0.001f) return;

        drawMenuBackground(screenWidth, screenHeight, alpha);

        float centerX = screenWidth / 2f;
        float centerY = screenHeight / 2f;
        String username = getDisplayName();

        long menuStartTime = screenStartTime + LOADING_MIN_DURATION + TRANSITION_DURATION;
        float menuElapsed = currentTime - menuStartTime;

        float logoAlpha = easeOutBack(clampProgress(menuElapsed, 0, 500)) * alpha;
        float logoScale = MathHelper.lerp(easeOutBack(clampProgress(menuElapsed, 0, 500)), 0.3f, 1f);
        float logoSize = 35 * logoScale;
        float logoY = centerY - 110;

        if (logoAlpha > 0.01f) {
            int a = (int) (logoAlpha * 255);
            int[] logoColors = {withAlpha(0xFFFFFF, a), withAlpha(0xFFFFFF, a), withAlpha(0xFFFFFF, a), withAlpha(0xFFFFFF, a)};
            float[] logoRadii = {0, 0, 0, 0};

            Initialization.getInstance().getManager().getRenderCore().getTexturePipeline()
                    .drawTexture(LOGO_TEXTURE, centerX - logoSize / 2f, logoY, logoSize, logoSize,
                            0, 0, 1, 1, logoColors, logoRadii, 1f);
        }

        float greetingT = easeOutCubic(clampProgress(menuElapsed, 150, 650));
        float greetingSlideY = (1f - greetingT) * 15f;
        if (greetingT > 0.01f) {
            String timeOfDay = getTimeOfDay();
            String greeting = "Good " + timeOfDay + ", ";
            float greetingWidth = Fonts.BOLD.getWidth(greeting, 14f);
            float usernameWidth = Fonts.BOLD.getWidth(username, 14f);
            float totalGreetingWidth = greetingWidth + usernameWidth;
            float greetingStartX = centerX - totalGreetingWidth / 2f;
            float greetingY = centerY - 55 + greetingSlideY;
            int ga = (int) (greetingT * alpha * 255);

            Fonts.BOLD.draw(greeting, greetingStartX, greetingY, 14f, withAlpha(0xFFFFFF, ga));
            Fonts.BOLD.draw(username, greetingStartX + greetingWidth, greetingY, 14f, withAlpha(0x64B4FF, ga));
        }

        float subtextT = easeOutCubic(clampProgress(menuElapsed, 300, 800));
        float subtextSlideY = (1f - subtextT) * 12f;
        if (subtextT > 0.01f) {
            String welcomeText = "Welcome to ";
            String clientName = "Rich Modern";
            String restText = ", the best client.";
            float welcomeWidth = Fonts.REGULAR.getWidth(welcomeText, 9f);
            float clientWidth = Fonts.BOLD.getWidth(clientName, 9f);
            float restWidth = Fonts.REGULAR.getWidth(restText, 9f);
            float totalWidth = welcomeWidth + clientWidth + restWidth;
            float startX = centerX - totalWidth / 2f;
            float textY = centerY - 35 + subtextSlideY;
            int sa = (int) (subtextT * alpha * 255);

            Fonts.REGULAR.draw(welcomeText, startX, textY, 9f, withAlpha(0xB4B4B4, sa));
            Fonts.BOLD.draw(clientName, startX + welcomeWidth, textY, 9f, withAlpha(0x6366F1, sa));
            Fonts.REGULAR.draw(restText, startX + welcomeWidth + clientWidth, textY, 9f, withAlpha(0xB4B4B4, sa));
        }

        float buttonWidth = 220;
        float buttonHeight = 30;
        float buttonSpacing = 7;
        float buttonStartY = centerY;

        for (int i = 0; i < 2; i++) {
            float btnT = easeOutCubic(clampProgress(menuElapsed, 400 + i * 70, 900 + i * 70));
            float btnSlide = (1f - btnT) * 20f;
            if (btnT > 0.01f) {
                float y = buttonStartY + i * (buttonHeight + buttonSpacing) + btnSlide;
                drawButton(centerX - buttonWidth / 2f, y, buttonWidth, buttonHeight,
                        i == 0 ? "Singleplayer" : "Multiplayer", i, mouseX, mouseY,
                        new Color(30, 35, 45), false, btnT * alpha);
            }
        }

        float swapAccountsY = buttonStartY + (buttonHeight + buttonSpacing) * 2 + 5;
        float btn2T = easeOutCubic(clampProgress(menuElapsed, 400 + 2 * 70, 900 + 2 * 70));
        float btn2Slide = (1f - btn2T) * 20f;
        if (btn2T > 0.01f) {
            drawButton(centerX - buttonWidth / 2f, swapAccountsY + btn2Slide, buttonWidth, buttonHeight,
                    "Swap Accounts", 2, mouseX, mouseY, new Color(99, 102, 241), true, btn2T * alpha);
        }

        float bottomY = swapAccountsY + buttonHeight + 5;
        float bottomButtonWidth = 65;
        float bottomButtonHeight = 20;
        float bottomSpacing = 10;
        float totalBottomWidth = bottomButtonWidth * 3 + bottomSpacing * 2;
        float bottomStartX = centerX - totalBottomWidth / 2f;

        String[] smallLabels = {"Options", "Proxies", "Exit"};
        for (int i = 0; i < 3; i++) {
            float btnT = easeOutCubic(clampProgress(menuElapsed, 400 + (i + 3) * 70, 900 + (i + 3) * 70));
            float btnSlide = (1f - btnT) * 20f;
            if (btnT > 0.01f) {
                float x = bottomStartX + i * (bottomButtonWidth + bottomSpacing);
                drawSmallButton(x, bottomY + btnSlide, bottomButtonWidth, bottomButtonHeight,
                        smallLabels[i], i + 3, mouseX, mouseY, btnT * alpha);
            }
        }

        float footerT = easeOutCubic(clampProgress(menuElapsed, 900, 1400));
        if (footerT > 0.01f) {
            int fa = (int) (footerT * alpha * 100);
            Fonts.REGULAR.drawCentered("By logging into your account, you agree to all of our policies,",
                    centerX, screenHeight - 17, 6.5f, withAlpha(0x646464, fa));
            Fonts.REGULAR.drawCentered("including our Privacy Policy and Terms of Service",
                    centerX, screenHeight - 8, 6.5f, withAlpha(0x646464, fa));
        }
    }

    private float clampProgress(float time, long start, long end) {
        if (time < start) return 0f;
        if (time >= end) return 1f;
        return (time - start) / (float) (end - start);
    }

    private void drawMenuBackground(int screenWidth, int screenHeight, float alpha) {
        int bgAlpha = (int) (alpha * 255);
        Render2D.rect(0, 0, screenWidth, screenHeight, withAlpha(0x0C0F12, bgAlpha));

        initMenuParticles(screenWidth, screenHeight);

        for (MenuParticle p : menuParticles) {
            if (!p.isDead && p.alpha > 0.01f) {
                int a = (int) (p.alpha * alpha * 255);
                Render2D.rect(p.x, p.y, p.size, p.size, withAlpha(0xFFFFFF, a));
            }
        }
    }

    private void initMenuParticles(int width, int height) {
        if (menuParticlesInitialized) return;
        Random rand = new Random();
        for (int i = 0; i < MENU_PARTICLE_COUNT; i++) {
            menuParticles.add(new MenuParticle(rand.nextFloat() * width, rand.nextFloat() * height));
        }
        menuParticlesInitialized = true;
    }

    private void updateMenuParticles(float deltaTime, int width, int height) {
        for (MenuParticle p : menuParticles) p.update(deltaTime * 60f, width, height);
        menuParticles.removeIf(p -> p.isDead);
        Random rand = new Random();
        while (menuParticles.size() < MENU_PARTICLE_COUNT) {
            menuParticles.add(new MenuParticle(rand.nextFloat() * width, rand.nextFloat() * height));
        }
    }

    private String getDisplayName() {
        String active = accountConfig.getActiveAccountName();
        if (active != null && !active.isEmpty()) return active;
        return nicknameText.isEmpty() ? "user" : nicknameText;
    }

    private String getTimeOfDay() {
        int hour = java.time.LocalTime.now().getHour();
        if (hour >= 5 && hour < 12) return "morning";
        if (hour >= 12 && hour < 17) return "afternoon";
        if (hour >= 17 && hour < 21) return "evening";
        return "night";
    }

    private void drawButton(float x, float y, float width, float height, String text, int index,
                            float mouseX, float mouseY, Color baseColor, boolean isAccent, float alpha) {
        float hoverProgress = buttonHoverProgress[index];
        int brightness = (int) (hoverProgress * 15);
        Color bgColor = new Color(
                Math.min(255, baseColor.getRed() + brightness),
                Math.min(255, baseColor.getGreen() + brightness),
                Math.min(255, baseColor.getBlue() + brightness));

        int bgA = (int) (alpha * 255);
        Render2D.rect(x, y, width, height, withAlpha(bgColor.getRGB() & 0xFFFFFF, bgA), 8);

        if (!isAccent) {
            int outlineA = (int) (alpha * 80);
            Render2D.outline(x, y, width, height, 1f, withAlpha(0x323741, outlineA), 8);
        }

        if (hoverProgress > 0.01f) {
            int borderA = (int) (alpha * hoverProgress * 100);
            int borderColor = isAccent ? withAlpha(0x8A82F5, borderA) : withAlpha(0x646E82, borderA);
            Render2D.outline(x, y, width, height, 1f, borderColor, 8);
        }

        float fontSize = 9f;
        float textHeight = Fonts.REGULAR.getHeight(fontSize);
        int textA = (int) (alpha * (isAccent ? 255 : 160));
        int textColor = isAccent ? withAlpha(0xFFFFFF, textA) : withAlpha(0xA0A0A0, textA);
        Fonts.REGULAR.drawCentered(text, x + width / 2f, y + (height - textHeight) / 2f, fontSize, textColor);
    }

    private void drawSmallButton(float x, float y, float width, float height, String text, int index,
                                 float mouseX, float mouseY, float alpha) {
        float hoverProgress = buttonHoverProgress[index];
        int baseAlpha = (int) (alpha * 150);
        int hoverAlpha = (int) (baseAlpha + hoverProgress * 50);
        float fontSize = 8f;
        float textHeight = Fonts.REGULAR.getHeight(fontSize);
        Fonts.REGULAR.drawCentered(text, x + width / 2f, y + (height - textHeight) / 2f, fontSize, withAlpha(0xB4B4B4, hoverAlpha));
    }

    private boolean isMouseOver(float mouseX, float mouseY, float x, float y, float width, float height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    private void updateButtonAnimations(float deltaTime, float mouseX, float mouseY, int screenWidth, int screenHeight) {
        float centerX = screenWidth / 2f;
        float centerY = screenHeight / 2f;
        float lerpSpeed = 1f - (float) Math.pow(0.001f, deltaTime);

        if (currentView == View.MAIN_MENU) {
            float buttonWidth = 220;
            float buttonHeight = 30;
            float buttonSpacing = 7;
            float buttonStartY = centerY;

            for (int i = 0; i < 2; i++) {
                float y = buttonStartY + i * (buttonHeight + buttonSpacing);
                boolean hovered = isMouseOver(mouseX, mouseY, centerX - buttonWidth / 2f, y, buttonWidth, buttonHeight);
                buttonHoverProgress[i] = MathHelper.lerp(lerpSpeed, buttonHoverProgress[i], hovered ? 1f : 0f);
            }

            float swapAccountsY = buttonStartY + (buttonHeight + buttonSpacing) * 2 + 5;
            boolean swapHovered = isMouseOver(mouseX, mouseY, centerX - buttonWidth / 2f, swapAccountsY, buttonWidth, buttonHeight);
            buttonHoverProgress[2] = MathHelper.lerp(lerpSpeed, buttonHoverProgress[2], swapHovered ? 1f : 0f);

            float bottomY = swapAccountsY + buttonHeight + 5;
            float bottomButtonWidth = 65;
            float bottomButtonHeight = 20;
            float bottomSpacing = 10;
            float totalBottomWidth = bottomButtonWidth * 3 + bottomSpacing * 2;
            float bottomStartX = centerX - totalBottomWidth / 2f;

            for (int i = 0; i < 3; i++) {
                float x = bottomStartX + i * (bottomButtonWidth + bottomSpacing);
                boolean hovered = isMouseOver(mouseX, mouseY, x, bottomY, bottomButtonWidth, bottomButtonHeight);
                buttonHoverProgress[i + 3] = MathHelper.lerp(lerpSpeed, buttonHoverProgress[i + 3], hovered ? 1f : 0f);
            }
        } else {
            for (int i = 0; i < 6; i++) {
                buttonHoverProgress[i] = MathHelper.lerp(lerpSpeed, buttonHoverProgress[i], 0f);
            }
        }
    }

    private int fixedWidth() { return getFixedScaledWidth(); }
    private int fixedHeight() { return getFixedScaledHeight(); }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (phase != Phase.MENU) return false;

        float scaledMouseX = toFixedCoord(click.x());
        float scaledMouseY = toFixedCoord(click.y());

        if (currentView == View.MAIN_MENU) {
            if (click.button() == 0) {
                int fixedWidth = getFixedScaledWidth();
                int fixedHeight = getFixedScaledHeight();
                float centerX = fixedWidth / 2f;
                float centerY = fixedHeight / 2f;

                float buttonWidth = 220;
                float buttonHeight = 30;
                float buttonSpacing = 7;
                float buttonStartY = centerY;

                for (int i = 0; i < 2; i++) {
                    float y = buttonStartY + i * (buttonHeight + buttonSpacing);
                    if (isMouseOver(scaledMouseX, scaledMouseY, centerX - buttonWidth / 2f, y, buttonWidth, buttonHeight)) {
                        handleMainMenuButtonClick(i);
                        return true;
                    }
                }

                float swapAccountsY = buttonStartY + (buttonHeight + buttonSpacing) * 2 + 5;
                if (isMouseOver(scaledMouseX, scaledMouseY, centerX - buttonWidth / 2f, swapAccountsY, buttonWidth, buttonHeight)) {
                    handleMainMenuButtonClick(2);
                    return true;
                }

                float bottomY = swapAccountsY + buttonHeight + 5;
                float bottomButtonWidth = 65;
                float bottomButtonHeight = 20;
                float bottomSpacing = 10;
                float totalBottomWidth = bottomButtonWidth * 3 + bottomSpacing * 2;
                float bottomStartX = centerX - totalBottomWidth / 2f;

                for (int i = 0; i < 3; i++) {
                    float x = bottomStartX + i * (bottomButtonWidth + bottomSpacing);
                    if (isMouseOver(scaledMouseX, scaledMouseY, x, bottomY, bottomButtonWidth, bottomButtonHeight)) {
                        handleMainMenuButtonClick(i + 3);
                        return true;
                    }
                }
            }
        } else if (currentView == View.ALT_SCREEN) {
            return handleAltScreenClick(scaledMouseX, scaledMouseY, click);
        }

        return super.mouseClicked(click, doubled);
    }

    private void handleMainMenuButtonClick(int index) {
        switch (index) {
            case 0 -> this.client.setScreen(new SelectWorldScreen(this));
            case 1 -> {
                Screen screen = this.client.options.skipMultiplayerWarning
                        ? new MultiplayerScreen(this) : new MultiplayerWarningScreen(this);
                this.client.setScreen(screen);
            }
            case 2 -> currentView = View.ALT_SCREEN;
            case 3 -> this.client.setScreen(new OptionsScreen(this, this.client.options));
            case 4 -> {}
            case 5 -> this.client.scheduleStop();
        }
    }

    private boolean handleAltScreenClick(float mouseX, float mouseY, Click click) {
        int screenWidth = getFixedScaledWidth();
        int screenHeight = getFixedScaledHeight();

        float totalWidth = 100 + 5 + 300;
        float centerX = screenWidth / 2f;
        float centerY = screenHeight / 2f;
        float startX = centerX - totalWidth / 2f;
        float startY = centerY - (100 + 5 + 58) / 2f;

        float fieldX = startX + 5;
        float fieldY = startY + 38;
        float fieldHeight = 14;
        float addButtonSize = 14;
        float buttonGap = 3;
        float fieldWidth = 100 - 10 - addButtonSize - buttonGap;

        if (accountRenderer.isMouseOver(mouseX, mouseY, fieldX, fieldY, fieldWidth, fieldHeight)) {
            nicknameFieldFocused = true;
            return true;
        } else {
            nicknameFieldFocused = false;
        }

        float addButtonX = fieldX + fieldWidth + buttonGap;

        if (accountRenderer.isMouseOver(mouseX, mouseY, addButtonX, fieldY, addButtonSize, addButtonSize)) {
            if (!nicknameText.isEmpty()) {
                addAccount(nicknameText);
                nicknameText = "";
            }
            return true;
        }

        float buttonWidth = 100 - 10;
        float buttonHeight = 16;
        float randomButtonY = fieldY + fieldHeight + 6;

        if (accountRenderer.isMouseOver(mouseX, mouseY, startX + 5, randomButtonY, buttonWidth, buttonHeight)) {
            addAccount(generateRandomNickname());
            nicknameText = "";
            return true;
        }

        float clearButtonY = randomButtonY + buttonHeight + 5;

        if (accountRenderer.isMouseOver(mouseX, mouseY, startX + 5, clearButtonY, buttonWidth, buttonHeight)) {
            accountConfig.clearAllAccounts();
            targetScrollOffset = 0f;
            scrollOffset = 0f;
            return true;
        }

        float rightPanelX = startX + 105;
        float accountListX = rightPanelX + 5;
        float accountListY = startY + 26;
        float accountListWidth = 290;
        float accountListHeight = 134;

        if (!accountRenderer.isMouseOver(mouseX, mouseY, accountListX, accountListY, accountListWidth, accountListHeight)) {
            return false;
        }

        float cardWidth = (accountListWidth - 5) / 2f;
        float cardHeight = 40;
        float cardGap = 5;

        List<AccountEntry> sortedAccounts = accountConfig.getSortedAccounts();

        for (int i = 0; i < sortedAccounts.size(); i++) {
            int col = i % 2;
            int row = i / 2;

            float cardX = accountListX + col * (cardWidth + cardGap);
            float cardY = accountListY + row * (cardHeight + cardGap) - scrollOffset;

            if (cardY + cardHeight < accountListY || cardY > accountListY + accountListHeight) continue;

            float btnSize = 12;
            float buttonYPos = cardY + cardHeight - btnSize - 5;
            float pinButtonX = cardX + cardWidth - btnSize * 2 - 8;
            float deleteButtonX = cardX + cardWidth - btnSize - 5;

            if (accountRenderer.isMouseOver(mouseX, mouseY, pinButtonX, buttonYPos, btnSize, btnSize)) {
                AccountEntry entry = sortedAccounts.get(i);
                entry.togglePinned();
                if (entry.isPinned()) setActiveAccount(entry);
                accountConfig.save();
                return true;
            }

            if (accountRenderer.isMouseOver(mouseX, mouseY, deleteButtonX, buttonYPos, btnSize, btnSize)) {
                accountConfig.removeAccountByIndex(i);
                return true;
            }

            if (accountRenderer.isMouseOver(mouseX, mouseY, cardX, cardY, cardWidth, cardHeight)) {
                setActiveAccount(sortedAccounts.get(i));
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (phase != Phase.MENU || currentView != View.ALT_SCREEN) return false;

        float scaledMouseX = toFixedCoord(mouseX);
        float scaledMouseY = toFixedCoord(mouseY);
        int screenWidth = getFixedScaledWidth();
        int screenHeight = getFixedScaledHeight();

        float centerX = screenWidth / 2f;
        float centerY = screenHeight / 2f;
        float startX = centerX - 202.5f;
        float startY = centerY - 81.5f;

        float rightPanelX = startX + 105;

        if (accountRenderer.isMouseOver(scaledMouseX, scaledMouseY, rightPanelX, startY, 300, 165)) {
            float cardHeight = 40;
            float cardGap = 5;
            float accountListHeight = 134;
            int rows = (int) Math.ceil(accountConfig.getSortedAccounts().size() / 2.0);
            float maxScroll = Math.max(0, rows * (cardHeight + cardGap) - accountListHeight);

            targetScrollOffset -= (float) verticalAmount * 25;
            targetScrollOffset = MathHelper.clamp(targetScrollOffset, 0, maxScroll);

            float scrollDiff = targetScrollOffset - scrollOffset;
            scrollOffset += scrollDiff * Math.min(1f, 0.016f * 12f);

            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (phase != Phase.MENU) return false;

        if (currentView == View.ALT_SCREEN) {
            if (nicknameFieldFocused) {
                int keyCode = input.key();

                if (keyCode == 259) {
                    if (!nicknameText.isEmpty()) {
                        nicknameText = nicknameText.substring(0, nicknameText.length() - 1);
                    }
                    return true;
                }

                if (keyCode == 256) {
                    nicknameFieldFocused = false;
                    return true;
                }

                if (keyCode == 257 || keyCode == 335) {
                    if (!nicknameText.isEmpty()) {
                        addAccount(nicknameText);
                        nicknameText = "";
                    }
                    nicknameFieldFocused = false;
                    return true;
                }
            }

            if (input.key() == 256) {
                currentView = View.MAIN_MENU;
                accountConfig.save();
                return true;
            }
        }

        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (phase != Phase.MENU) return false;

        if (currentView == View.ALT_SCREEN && nicknameFieldFocused) {
            int codepoint = input.codepoint();
            if (Character.isLetterOrDigit(codepoint) || codepoint == '_') {
                if (nicknameText.length() < 16) {
                    nicknameText += Character.toString(codepoint);
                }
                return true;
            }
        }
        return super.charTyped(input);
    }

    private void setActiveAccount(AccountEntry account) {
        accountConfig.setActiveAccount(account.getName(), account.getDate(), account.getSkin());
        SessionChanger.changeUsername(account.getName());
    }

    private void addAccount(String nickname) {
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
        String date = now.format(formatter);

        AccountEntry entry = new AccountEntry(nickname, date, null);
        accountConfig.addAccount(entry);
        setActiveAccount(entry);
        SessionChanger.changeUsername(nickname);
    }

    private String generateRandomNickname() {
        Random random = new Random();
        StringBuilder username = new StringBuilder();
        char[] vowels = {'a', 'e', 'i', 'o', 'u'};
        char[] consonants = {'b', 'c', 'd', 'f', 'g', 'h', 'j', 'k', 'l', 'm', 'n', 'p', 'r', 's', 't', 'v', 'w', 'x', 'y', 'z'};

        String finalUsername = null;
        int attempts = 0;
        final int MAX_ATTEMPTS = 10;

        List<AccountEntry> existingAccounts = accountConfig.getAccounts();

        do {
            username.setLength(0);
            int length = 6 + random.nextInt(5);
            boolean startWithVowel = random.nextBoolean();

            for (int i = 0; i < length; i++) {
                if (i % 2 == 0) {
                    username.append(startWithVowel ? vowels[random.nextInt(vowels.length)] : consonants[random.nextInt(consonants.length)]);
                } else {
                    username.append(startWithVowel ? consonants[random.nextInt(consonants.length)] : vowels[random.nextInt(vowels.length)]);
                }
            }

            if (random.nextInt(100) < 30) {
                username.append(random.nextInt(100));
            }

            String tempUsername = username.substring(0, 1).toUpperCase() + username.substring(1);
            attempts++;

            boolean exists = false;
            for (AccountEntry account : existingAccounts) {
                if (account.getName().equalsIgnoreCase(tempUsername)) {
                    exists = true;
                    break;
                }
            }

            if (!exists) {
                finalUsername = tempUsername;
                break;
            }

        } while (attempts < MAX_ATTEMPTS);

        if (finalUsername == null) {
            finalUsername = username.substring(0, 1).toUpperCase() + username.substring(1) + (System.currentTimeMillis() % 1000);
        }

        return finalUsername;
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (MathHelper.clamp(alpha, 0, 255) << 24);
    }
}
