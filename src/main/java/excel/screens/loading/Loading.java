package excel.screens.loading;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import excel.util.render.Render2D;
import excel.util.render.font.Fonts;

import java.awt.*;

public class Loading {

    private static Loading instance;

    private static final float FIXED_GUI_SCALE = 2.0f;

    private static final String[] LOADING_TEXTS = {
            "Запуск паста клиент",
            "Excel ответь",
            "Excel где связь",
            "Excel молодец"
    };

    private static final long TEXT_DISPLAY_DURATION = 1200L;
    private static final long LAST_TEXT_DISPLAY_DURATION = 1200L;
    private static final long TEXT_TRANSITION_DURATION = 300L;
    private static final float ZOOM_LEVEL = 1.08f;

    private float animatedProgress = 0f;
    private float targetProgress = 0f;
    private float pulseTime = 0f;
    private long lastRenderTime = 0L;
    private long startTime = 0L;
    private boolean initialized = false;

    private int currentTextIndex = 0;
    private float currentTextOffsetY = 0f;
    private float currentTextAlpha = 1f;
    private float newTextOffsetY = -12f;
    private float newTextAlpha = 0f;
    private long lastTextChangeTime = 0L;
    private boolean isTransitioning = false;
    private long transitionStartTime = 0L;

    private float backgroundAlpha = 0f;
    private float contentAlpha = 0f;
    private boolean isFadingOut = false;
    private boolean readyToClose = false;

    private boolean resourcesLoaded = false;
    private boolean allTextsShown = false;
    private long lastTextShownTime = 0L;

    private final Particle[] particles = new Particle[40];
    private float progressGlow = 0f;

    public Loading() {
        instance = this;
        this.startTime = Util.getMeasuringTimeMs();
        this.lastTextChangeTime = this.startTime;
        initParticles();
    }

    public static Loading getInstance() {
        if (instance == null) {
            instance = new Loading();
        }
        return instance;
    }

    public static void resetInstance() {
        instance = null;
    }

    private void initParticles() {
        for (int i = 0; i < particles.length; i++) {
            particles[i] = new Particle();
        }
    }

    private static class Particle {
        float x, y, speed, size, alpha, drift;

        Particle() {
            reset(true);
        }

        void reset(boolean randomY) {
            x = (float) (Math.random() * 2000);
            y = randomY ? (float) (Math.random() * 1200) : 1200 + (float)(Math.random() * 100);
            speed = 8f + (float) (Math.random() * 25f);
            size = 1f + (float) (Math.random() * 2.5f);
            alpha = 0.08f + (float) (Math.random() * 0.2f);
            drift = -15f + (float)(Math.random() * 30f);
        }

        void update(float dt) {
            y -= speed * dt;
            x += drift * dt;
            if (y < -20) reset(false);
        }
    }

    private int getFixedScaledWidth() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null) return 960;
        return (int) Math.ceil((double) client.getWindow().getFramebufferWidth() / FIXED_GUI_SCALE);
    }

    private int getFixedScaledHeight() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null) return 540;
        return (int) Math.ceil((double) client.getWindow().getFramebufferHeight() / FIXED_GUI_SCALE);
    }

    public void render(int width, int height, float opacity) {
        long currentTime = Util.getMeasuringTimeMs();

        if (!initialized) {
            lastRenderTime = currentTime;
            initialized = true;
        }

        float deltaTime = (currentTime - lastRenderTime) / 1000f;
        lastRenderTime = currentTime;
        deltaTime = MathHelper.clamp(deltaTime, 0.001f, 0.1f);

        updateAnimations(deltaTime, currentTime);

        int fixedWidth = getFixedScaledWidth();
        int fixedHeight = getFixedScaledHeight();

        Render2D.beginOverlay();


        Render2D.backgroundImage(backgroundAlpha * opacity, ZOOM_LEVEL);
        float overlayAlpha = backgroundAlpha * opacity * 0.3f;
        Render2D.rect(0, 0, fixedWidth, fixedHeight,
                withAlpha(0xFF000000, (int)(overlayAlpha * 255)), 0);

        float finalContentAlpha = contentAlpha * opacity;

        if (finalContentAlpha > 0.001f) {
            renderParticles(fixedWidth, fixedHeight, finalContentAlpha, deltaTime);
            renderLogo(fixedWidth, fixedHeight, finalContentAlpha);
            renderSubtitle(fixedWidth, fixedHeight, finalContentAlpha);
            renderProgressBar(fixedWidth, fixedHeight, finalContentAlpha);
            renderLoadingText(fixedWidth, fixedHeight, finalContentAlpha, currentTime);
            renderBottomInfo(fixedWidth, fixedHeight, finalContentAlpha);
        }

        Render2D.endOverlay();
    }
    private void updateAnimations(float deltaTime, long currentTime) {
        pulseTime += deltaTime;
        animatedProgress = MathHelper.lerp(deltaTime * 5f, animatedProgress, targetProgress);
        progressGlow += deltaTime * 3f;
        backgroundAlpha = MathHelper.lerp(deltaTime * 4f, backgroundAlpha, 1f);
        if (backgroundAlpha > 0.99f) backgroundAlpha = 1f;

        if (!isFadingOut) {
            contentAlpha = MathHelper.lerp(deltaTime * 2.5f, contentAlpha, 1f);
            if (contentAlpha > 0.99f) contentAlpha = 1f;
        } else {
            contentAlpha -= deltaTime * 1.8f;
            if (contentAlpha < 0f) {
                contentAlpha = 0f;
                readyToClose = true;
            }
        }

        if (!isFadingOut) {
            updateTextAnimation(currentTime, deltaTime);
        }

        if (allTextsShown && resourcesLoaded && !isFadingOut) {
            long elapsed = currentTime - lastTextShownTime;
            if (elapsed >= LAST_TEXT_DISPLAY_DURATION) {
                isFadingOut = true;
            }
        }
    }

    private void updateTextAnimation(long currentTime, float deltaTime) {
        if (allTextsShown) return;

        if (!isTransitioning) {
            long elapsed = currentTime - lastTextChangeTime;

            if (currentTextIndex >= LOADING_TEXTS.length - 1) {
                if (!allTextsShown) {
                    allTextsShown = true;
                    lastTextShownTime = currentTime;
                }
                return;
            }

            if (elapsed >= TEXT_DISPLAY_DURATION) {
                isTransitioning = true;
                transitionStartTime = currentTime;
            }
        }

        if (isTransitioning) {
            long elapsed = currentTime - transitionStartTime;
            float rawProgress = MathHelper.clamp((float) elapsed / TEXT_TRANSITION_DURATION, 0f, 1f);
            float eased = easeOutCubic(rawProgress);

            currentTextOffsetY = 14f * eased;
            currentTextAlpha = MathHelper.clamp(1f - eased * 1.5f, 0f, 1f);

            newTextOffsetY = -12f * (1f - eased);
            newTextAlpha = MathHelper.clamp(eased * 1.3f, 0f, 1f);

            if (rawProgress >= 1f) {
                isTransitioning = false;
                currentTextIndex++;
                currentTextOffsetY = 0f;
                currentTextAlpha = 1f;
                newTextOffsetY = -12f;
                newTextAlpha = 0f;
                lastTextChangeTime = currentTime;

                if (currentTextIndex >= LOADING_TEXTS.length - 1) {
                    allTextsShown = true;
                    lastTextShownTime = currentTime;
                }
            }
        }
    }

    private void renderParticles(int width, int height, float opacity, float dt) {
        for (Particle p : particles) {
            p.update(dt);
            int alpha = (int)(p.alpha * opacity * 255);
            if (alpha <= 0) continue;

            int color = withAlpha(0xFFFFFFFF, alpha);
            Render2D.rect(p.x, p.y, p.size, p.size, color, p.size / 2f);
        }
    }

    private void renderLogo(int width, int height, float opacity) {
        float centerX = width / 2f;
        float centerY = height / 2f - 40;

        int textAlpha = (int) (opacity * 255);
        float fontSize = 44f;
        
        float breathe = (float) Math.sin(pulseTime * 1.2f) * 1.5f;

        String icon = "A";
        float iconW = Fonts.ICONS.getWidth(icon, fontSize);
        float iconH = Fonts.ICONS.getHeight(fontSize);

        float iconX = centerX - iconW / 2f;
        float iconY = centerY - iconH / 2f + breathe;

        float glowPulse = 0.4f + 0.2f * (float) Math.sin(pulseTime * 1.5f);
        int glowAlpha = (int)(textAlpha * glowPulse);
        int glowColor = withAlpha(0xFFFF7276, glowAlpha);
        Fonts.ICONS.draw(icon, iconX, iconY + 3, fontSize, glowColor);

        int shadowColor = withAlpha(0xFF000000, textAlpha / 3);
        Fonts.ICONS.draw(icon, iconX + 2, iconY + 2, fontSize, shadowColor);

        int mainColor = withAlpha(0xFFFFFFFF, textAlpha);
        Fonts.ICONS.draw(icon, iconX, iconY, fontSize, mainColor);
    }

    private void renderSubtitle(int width, int height, float opacity) {
        float centerX = width / 2f;
        float subtitleY = height / 2f - 8;

        int alpha = (int)(opacity * 180);
        float fontSize = 9f;

        String subtitle = "E X C E L  C L I E N T";
        float subtitleW = Fonts.BOLD.getWidth(subtitle, fontSize);
        
        float lineW = 30;
        float lineY = subtitleY + fontSize / 2f;
        float gap = 8;
        int lineAlpha = (int)(opacity * 40);
        int lineColor = withAlpha(0xFFFF7276, lineAlpha);

        Render2D.rect(centerX - subtitleW / 2f - gap - lineW, lineY, lineW, 0.5f, lineColor, 0);
        Render2D.rect(centerX + subtitleW / 2f + gap, lineY, lineW, 0.5f, lineColor, 0);

        Fonts.BOLD.draw(subtitle, centerX - subtitleW / 2f, subtitleY,
                fontSize, withAlpha(0xFFFFFFFF, alpha));
    }

    private void renderProgressBar(int width, int height, float opacity) {
        float centerX = width / 2f;
        float barY = height / 2f + 12;
        float barW = 180;
        float barH = 3;
        float barX = centerX - barW / 2f;

        int bgAlpha = (int)(opacity * 60);
        Render2D.rect(barX, barY, barW, barH, withAlpha(0xFFFFFFFF, bgAlpha), 1.5f);
        
        if (animatedProgress > 0) {
            float filledW = barW * MathHelper.clamp(animatedProgress, 0f, 1f);
            
            int a1 = (int)(opacity * 200);
            int a2 = (int)(opacity * 140);
            Render2D.gradientRect(barX, barY, filledW, barH,
                    new int[]{
                            withAlpha(0xFFFF7276, a1),
                            withAlpha(0xFFFF9A76, a2),
                            withAlpha(0xFFFF7276, a1),
                            withAlpha(0xFFFF9A76, a2)
                    }, 1.5f);
            
            float glowPos = (float)((progressGlow * 0.3f) % 1.0);
            float glowX = barX + filledW * glowPos;
            float glowW = 20;
            if (glowX + glowW > barX + filledW) glowW = barX + filledW - glowX;
            if (glowW > 0) {
                int shineA = (int)(opacity * 80);
                Render2D.rect(glowX, barY, glowW, barH, withAlpha(0xFFFFFFFF, shineA), 1.5f);
            }
        }
        
        String pct = (int)(animatedProgress * 100) + "%";
        float pctW = Fonts.BOLD.getWidth(pct, 6f);
        int pctAlpha = (int)(opacity * 120);
        Fonts.BOLD.draw(pct, barX + barW + 8, barY - 1, 6f,
                withAlpha(0xFFFFFFFF, pctAlpha));
    }

    private void renderLoadingText(int width, int height, float opacity, long currentTime) {
        float fontSize = 10f;
        float baseY = height / 2f + 30;
        float centerX = width / 2f;
        
        if (currentTextAlpha > 0.01f && currentTextIndex < LOADING_TEXTS.length) {
            String currentText = LOADING_TEXTS[currentTextIndex];
            float currentWidth = Fonts.REGULARNEW.getWidth(currentText, fontSize);
            int alpha = (int) (opacity * currentTextAlpha * 220);

            Fonts.REGULARNEW.draw(currentText,
                    centerX - currentWidth / 2f,
                    baseY + currentTextOffsetY,
                    fontSize,
                    withAlpha(0xFFFFFFFF, alpha));
        }

        if (isTransitioning && newTextAlpha > 0.01f) {
            int nextIndex = currentTextIndex + 1;
            if (nextIndex < LOADING_TEXTS.length) {
                String nextText = LOADING_TEXTS[nextIndex];
                float nextWidth = Fonts.REGULARNEW.getWidth(nextText, fontSize);
                int alpha = (int) (opacity * newTextAlpha * 220);

                Fonts.REGULARNEW.draw(nextText,
                        centerX - nextWidth / 2f,
                        baseY + newTextOffsetY,
                        fontSize,
                        withAlpha(0xFFFFFFFF, alpha));
            }
        }
    }

    private void renderBottomInfo(int width, int height, float opacity) {
        float y = height - 20;
        int alpha = (int)(opacity * 50);
        float fontSize = 6f;
        
        String version = "Excel Client · 1.21.11";
        Fonts.BOLD.draw(version, 15, y, fontSize, withAlpha(0xFFFFFFFF, alpha));
        
        String copyright = "Excel Client";
        float copyrightW = Fonts.BOLD.getWidth(copyright, fontSize);
        Fonts.BOLD.draw(copyright, width - copyrightW - 15, y, fontSize, withAlpha(0xFFFFFFFF, alpha));

        if (!allTextsShown) {
            int dotCount = ((int)(pulseTime * 2f)) % 4;
            String dots = ".".repeat(dotCount);
            float dotsW = Fonts.BOLD.getWidth("...", fontSize);
            Fonts.BOLD.draw(dots, width / 2f - dotsW / 2f, y, fontSize,
                    withAlpha(0xFFFF7276, (int)(opacity * 80)));
        }
    }
    private float easeOutCubic(float x) {
        return 1f - (float) Math.pow(1f - x, 3);
    }
    public void markComplete() {
        resourcesLoaded = true;
        if (!allTextsShown) {
            allTextsShown = true;
            lastTextShownTime = Util.getMeasuringTimeMs();
            currentTextIndex = LOADING_TEXTS.length - 1;
            isTransitioning = false;
        }
    }

    public boolean isContentFadedOut() {
        return isFadingOut && contentAlpha <= 0.01f;
    }

    public boolean isReadyToClose() {
        return readyToClose;
    }

    public boolean isComplete() {
        return allTextsShown && resourcesLoaded;
    }

    public boolean isFadingOut() {
        return isFadingOut;
    }

    public float getContentAlpha() {
        return contentAlpha;
    }

    public void setProgress(float progress) {
        this.targetProgress = MathHelper.clamp(progress, 0f, 1f);
    }

    public float getProgress() {
        return targetProgress;
    }

    public void reset() {
        animatedProgress = 0f;
        targetProgress = 0f;
        pulseTime = 0f;
        lastRenderTime = 0L;
        startTime = Util.getMeasuringTimeMs();
        initialized = false;
        currentTextIndex = 0;
        currentTextOffsetY = 0f;
        currentTextAlpha = 1f;
        newTextOffsetY = -12f;
        newTextAlpha = 0f;
        lastTextChangeTime = startTime;
        isTransitioning = false;
        transitionStartTime = 0L;
        backgroundAlpha = 0f;
        contentAlpha = 0f;
        isFadingOut = false;
        readyToClose = false;
        resourcesLoaded = false;
        allTextsShown = false;
        lastTextShownTime = 0L;
        initParticles();
    }

    public long getStartTime() {
        return startTime;
    }

    private int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (MathHelper.clamp(alpha, 0, 255) << 24);
    }
}
