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

    private static final long ENTRANCE_LOGO_DELAY = 0;
    private static final long ENTRANCE_GREETING_DELAY = 150;
    private static final long ENTRANCE_SUBTEXT_DELAY = 300;
    private static final long ENTRANCE_BUTTON_START_DELAY = 400;
    private static final long ENTRANCE_BUTTON_STAGGER = 70;
    private static final long ENTRANCE_FOOTER_DELAY = 900;
    private static final long ENTRANCE_DURATION = 500;

    private long screenStartTime = 0L;
    private boolean initialized = false;
    private long lastRenderTime = 0L;

    private float screenFadeIn = 0f;

    private enum View { MAIN_MENU, ALT_SCREEN }
    private View currentView = View.MAIN_MENU;

    private float[] buttonHoverProgress = new float[6];

    private final AccountRenderer accountRenderer;
    private final AccountConfig accountConfig;
    private String nicknameText = "";
    private boolean nicknameFieldFocused = false;
    private float scrollOffset = 0f;
    private float targetScrollOffset = 0f;

    private static final int PARTICLE_COUNT = 50;
    private static final float PARTICLE_FADE_THRESHOLD = 0.8f;
    private final List<MenuParticle> particles = new ArrayList<>();
    private boolean particlesInitialized = false;
    private int lastWindowWidth = 0;
    private int lastWindowHeight = 0;

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
            if (lifetime > maxLifetime * PARTICLE_FADE_THRESHOLD) {
                float fadeProgress = (lifetime - maxLifetime * PARTICLE_FADE_THRESHOLD) / (maxLifetime * (1f - PARTICLE_FADE_THRESHOLD));
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
        screenFadeIn = 0f;
        currentView = View.MAIN_MENU;
        particlesInitialized = false;

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

    private float getEntranceProgress(long currentTime, long delay) {
        long elapsed = currentTime - screenStartTime - delay;
        if (elapsed < 0) return 0f;
        return MathHelper.clamp((float) elapsed / ENTRANCE_DURATION, 0f, 1f);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        long currentTime = Util.getMeasuringTimeMs();

        if (!initialized) {
            screenStartTime = currentTime;
            lastRenderTime = currentTime;
            initialized = true;
        }

        float deltaTime = Math.min(delta, 0.05f);
        lastRenderTime = currentTime;

        int fixedWidth = getFixedScaledWidth();
        int fixedHeight = getFixedScaledHeight();

        if (lastWindowWidth != fixedWidth || lastWindowHeight != fixedHeight) {
            particles.clear();
            particlesInitialized = false;
            lastWindowWidth = fixedWidth;
            lastWindowHeight = fixedHeight;
        }

        screenFadeIn = MathHelper.lerp(deltaTime * 4f, screenFadeIn, 1f);
        if (screenFadeIn > 0.99f) screenFadeIn = 1f;

        float scaledMouseX = toFixedCoord(mouseX);
        float scaledMouseY = toFixedCoord(mouseY);

        for (MenuParticle p : particles) {
            p.update(deltaTime * 60f, fixedWidth, fixedHeight);
        }
        particles.removeIf(p -> p.isDead);
        Random rand = new Random();
        while (particles.size() < PARTICLE_COUNT) {
            particles.add(new MenuParticle(rand.nextFloat() * fixedWidth, rand.nextFloat() * fixedHeight));
        }

        updateButtonAnimations(deltaTime, scaledMouseX, scaledMouseY, fixedWidth, fixedHeight);

        Render2D.beginOverlay();

        drawBackground(fixedWidth, fixedHeight);

        if (currentView == View.MAIN_MENU) {
            renderMainMenu(fixedWidth, fixedHeight, scaledMouseX, scaledMouseY, currentTime);
        } else {
            renderAltScreen(fixedWidth, fixedHeight, scaledMouseX, scaledMouseY, currentTime);
        }

        Render2D.endOverlay();
    }

    private void drawBackground(int screenWidth, int screenHeight) {
        initParticles(screenWidth, screenHeight);

        int bgAlpha = (int) (screenFadeIn * 255);
        Render2D.rect(0, 0, screenWidth, screenHeight, withAlpha(0x0C0F12, bgAlpha));

        for (MenuParticle p : particles) {
            if (!p.isDead && p.alpha > 0.01f) {
                int alpha = (int) (p.alpha * screenFadeIn * 255);
                Render2D.rect(p.x, p.y, p.size, p.size, withAlpha(0xFFFFFF, alpha));
            }
        }
    }

    private void initParticles(int width, int height) {
        if (particlesInitialized) return;
        Random rand = new Random();
        for (int i = 0; i < PARTICLE_COUNT; i++) {
            particles.add(new MenuParticle(rand.nextFloat() * width, rand.nextFloat() * height));
        }
        particlesInitialized = true;
    }

    private void renderMainMenu(int screenWidth, int screenHeight, float mouseX, float mouseY, long currentTime) {
        float centerX = screenWidth / 2f;
        float centerY = screenHeight / 2f;

        String username = getDisplayName();

        float logoAlpha = easeOutBack(getEntranceProgress(currentTime, ENTRANCE_LOGO_DELAY)) * screenFadeIn;
        float logoScale = MathHelper.lerp(easeOutBack(getEntranceProgress(currentTime, ENTRANCE_LOGO_DELAY)), 0.3f, 1f);
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

        float greetingAlpha = easeOutCubic(getEntranceProgress(currentTime, ENTRANCE_GREETING_DELAY)) * screenFadeIn;
        float greetingSlideY = (1f - easeOutCubic(getEntranceProgress(currentTime, ENTRANCE_GREETING_DELAY))) * 15f;

        if (greetingAlpha > 0.01f) {
            String timeOfDay = getTimeOfDay();
            String greeting = "Good " + timeOfDay + ", ";

            float greetingWidth = Fonts.BOLD.getWidth(greeting, 14f);
            float usernameWidth = Fonts.BOLD.getWidth(username, 14f);
            float totalGreetingWidth = greetingWidth + usernameWidth;

            float greetingStartX = centerX - totalGreetingWidth / 2f;
            float greetingY = centerY - 55 + greetingSlideY;
            int ga = (int) (greetingAlpha * 255);

            Fonts.BOLD.draw(greeting, greetingStartX, greetingY, 14f, withAlpha(0xFFFFFF, ga));
            Fonts.BOLD.draw(username, greetingStartX + greetingWidth, greetingY, 14f, withAlpha(0x64B4FF, ga));
        }

        float subtextAlpha = easeOutCubic(getEntranceProgress(currentTime, ENTRANCE_SUBTEXT_DELAY)) * screenFadeIn;
        float subtextSlideY = (1f - easeOutCubic(getEntranceProgress(currentTime, ENTRANCE_SUBTEXT_DELAY))) * 12f;

        if (subtextAlpha > 0.01f) {
            String welcomeText = "Welcome to ";
            String clientName = "Rich Modern";
            String restText = ", the best client.";

            float welcomeWidth = Fonts.REGULAR.getWidth(welcomeText, 9f);
            float clientWidth = Fonts.BOLD.getWidth(clientName, 9f);
            float restWidth = Fonts.REGULAR.getWidth(restText, 9f);
            float totalWidth = welcomeWidth + clientWidth + restWidth;

            float startX = centerX - totalWidth / 2f;
            float textY = centerY - 35 + subtextSlideY;
            int sa = (int) (subtextAlpha * 255);

            Fonts.REGULAR.draw(welcomeText, startX, textY, 9f, withAlpha(0xB4B4B4, sa));
            Fonts.BOLD.draw(clientName, startX + welcomeWidth, textY, 9f, withAlpha(0x6366F1, sa));
            Fonts.REGULAR.draw(restText, startX + welcomeWidth + clientWidth, textY, 9f, withAlpha(0xB4B4B4, sa));
        }

        float buttonWidth = 220;
        float buttonHeight = 30;
        float buttonSpacing = 7;
        float buttonStartY = centerY;

        for (int i = 0; i < 2; i++) {
            float btnAlpha = easeOutCubic(getEntranceProgress(currentTime, ENTRANCE_BUTTON_START_DELAY + i * ENTRANCE_BUTTON_STAGGER)) * screenFadeIn;
            float btnSlide = (1f - easeOutCubic(getEntranceProgress(currentTime, ENTRANCE_BUTTON_START_DELAY + i * ENTRANCE_BUTTON_STAGGER))) * 20f;
            if (btnAlpha > 0.01f) {
                float y = buttonStartY + i * (buttonHeight + buttonSpacing) + btnSlide;
                drawButton(centerX - buttonWidth / 2f, y, buttonWidth, buttonHeight,
                        i == 0 ? "Singleplayer" : "Multiplayer", i, mouseX, mouseY, new Color(30, 35, 45), false, btnAlpha);
            }
        }

        float swapAccountsY = buttonStartY + (buttonHeight + buttonSpacing) * 2 + 5;
        float btn2Alpha = easeOutCubic(getEntranceProgress(currentTime, ENTRANCE_BUTTON_START_DELAY + 2 * ENTRANCE_BUTTON_STAGGER)) * screenFadeIn;
        float btn2Slide = (1f - easeOutCubic(getEntranceProgress(currentTime, ENTRANCE_BUTTON_START_DELAY + 2 * ENTRANCE_BUTTON_STAGGER))) * 20f;
        if (btn2Alpha > 0.01f) {
            drawButton(centerX - buttonWidth / 2f, swapAccountsY + btn2Slide, buttonWidth, buttonHeight,
                    "Swap Accounts", 2, mouseX, mouseY, new Color(99, 102, 241), true, btn2Alpha);
        }

        float bottomY = swapAccountsY + buttonHeight + 5;
        float bottomButtonWidth = 65;
        float bottomButtonHeight = 20;
        float bottomSpacing = 10;

        float totalBottomWidth = bottomButtonWidth * 3 + bottomSpacing * 2;
        float bottomStartX = centerX - totalBottomWidth / 2f;

        String[] smallLabels = {"Options", "Proxies", "Exit"};
        for (int i = 0; i < 3; i++) {
            float btnAlpha = easeOutCubic(getEntranceProgress(currentTime, ENTRANCE_BUTTON_START_DELAY + (i + 3) * ENTRANCE_BUTTON_STAGGER)) * screenFadeIn;
            float btnSlide = (1f - easeOutCubic(getEntranceProgress(currentTime, ENTRANCE_BUTTON_START_DELAY + (i + 3) * ENTRANCE_BUTTON_STAGGER))) * 20f;
            if (btnAlpha > 0.01f) {
                float x = bottomStartX + i * (bottomButtonWidth + bottomSpacing);
                drawSmallButton(x, bottomY + btnSlide, bottomButtonWidth, bottomButtonHeight,
                        smallLabels[i], i + 3, mouseX, mouseY, btnAlpha);
            }
        }

        float footerAlpha = easeOutCubic(getEntranceProgress(currentTime, ENTRANCE_FOOTER_DELAY)) * screenFadeIn;
        if (footerAlpha > 0.01f) {
            int fa = (int) (footerAlpha * 100);
            Fonts.REGULAR.drawCentered("By logging into your account, you agree to all of our policies,",
                    centerX, screenHeight - 17, 6.5f, withAlpha(0x646464, fa));
            Fonts.REGULAR.drawCentered("including our Privacy Policy and Terms of Service",
                    centerX, screenHeight - 8, 6.5f, withAlpha(0x646464, fa));
        }
    }

    private String getTimeOfDay() {
        int hour = java.time.LocalTime.now().getHour();
        if (hour >= 5 && hour < 12) return "morning";
        if (hour >= 12 && hour < 17) return "afternoon";
        if (hour >= 17 && hour < 21) return "evening";
        return "night";
    }

    private String getDisplayName() {
        String active = accountConfig.getActiveAccountName();
        if (active != null && !active.isEmpty()) return active;
        return nicknameText.isEmpty() ? "user" : nicknameText;
    }

    private void drawButton(float x, float y, float width, float height, String text, int index,
                            float mouseX, float mouseY, Color baseColor, boolean isAccent, float alpha) {
        float hoverProgress = buttonHoverProgress[index];

        int brightness = (int) (hoverProgress * 15);
        Color bgColor = new Color(
                Math.min(255, baseColor.getRed() + brightness),
                Math.min(255, baseColor.getGreen() + brightness),
                Math.min(255, baseColor.getBlue() + brightness)
        );

        int bgAlpha = (int) (alpha * 255);
        Render2D.rect(x, y, width, height, withAlpha(bgColor.getRGB() & 0xFFFFFF, bgAlpha), 8);

        if (!isAccent) {
            int outlineA = (int) (alpha * 80);
            Render2D.outline(x, y, width, height, 1f, withAlpha(0x323741, outlineA), 8);
        }

        if (hoverProgress > 0.01f) {
            int borderAlpha = (int) (alpha * hoverProgress * 100);
            int borderColor = isAccent ? withAlpha(0x8A82F5, borderAlpha) : withAlpha(0x646E82, borderAlpha);
            Render2D.outline(x, y, width, height, 1f, borderColor, 8);
        }

        float fontSize = 9f;
        float textHeight = Fonts.REGULAR.getHeight(fontSize);
        int textAlpha = (int) (alpha * (isAccent ? 255 : 160));
        int textColor = isAccent ? withAlpha(0xFFFFFF, textAlpha) : withAlpha(0xA0A0A0, textAlpha);
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

    private void renderAltScreen(int screenWidth, int screenHeight, float mouseX, float mouseY, long currentTime) {
        float totalWidth = 405;
        float totalHeight = 163;

        float centerX = screenWidth / 2f;
        float centerY = screenHeight / 2f;

        float startX = centerX - totalWidth / 2f;
        float startY = centerY - totalHeight / 2f;

        accountRenderer.renderLeftPanelTop(startX, startY, 100, 100,
                1f, nicknameText, nicknameFieldFocused, mouseX, mouseY, currentTime);

        accountRenderer.renderLeftPanelBottom(startX, startY + 105, 100, 58,
                1f, accountConfig.getActiveAccountName(), accountConfig.getActiveAccountDate(), accountConfig.getActiveAccountSkin());

        List<AccountEntry> sortedAccounts = accountConfig.getSortedAccounts();
        accountRenderer.renderRightPanel(startX + 105, startY, 300, 165,
                1f, sortedAccounts, scrollOffset, mouseX, mouseY, 1f, (int) FIXED_GUI_SCALE);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
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
                        ? new MultiplayerScreen(this)
                        : new MultiplayerWarningScreen(this);
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
        if (currentView != View.ALT_SCREEN) return false;

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
