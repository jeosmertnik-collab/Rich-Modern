package excel.screens.auth;

import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.input.CharInput;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import excel.auth.AuthManager;
import excel.screens.menu.MainMenuScreen;
import excel.util.render.Render2D;
import excel.util.render.font.Fonts;

public class LoginScreen extends Screen {

    private static final Identifier EXCEL_TEXTURE = Identifier.of("excel", "textures/gui/excel.png");
    private static final float CARD_WIDTH = 340;
    private static final float CARD_HEIGHT = 320;
    private static final float INPUT_HEIGHT = 28;
    private static final float BTN_HEIGHT = 30;
    private static final float CORNER_RADIUS = 10f;
    private static final float INPUT_RADIUS = 6f;

    private final boolean showRegister;
    private String nickname = "";
    private String password = "";
    private String confirmPassword = "";
    private int focusedField = 0;
    private boolean loading = false;
    private String statusMessage = "";
    private boolean statusSuccess = false;
    private boolean goToRegister = false;
    private boolean goToLogin = false;

    private float animProgress = 0f;
    private long startTime;

    public LoginScreen() {
        this(false);
    }

    public LoginScreen(boolean showRegister) {
        super(Text.of("Excel Auth"));
        this.showRegister = showRegister;
    }

    @Override
    protected void init() {
        startTime = System.currentTimeMillis();
        if (showRegister) {
            goToRegister = true;
        }
    }

    @Override
    public void tick() {
        long elapsed = System.currentTimeMillis() - startTime;
        animProgress = Math.min(1f, elapsed / 400f);
        animProgress = 1f - (float) Math.pow(1f - animProgress, 3);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (goToRegister) {
            goToRegister = false;
            client.setScreen(new RegisterScreen());
            return;
        }
        if (goToLogin) {
            goToLogin = false;
            client.setScreen(new LoginScreen(false));
            return;
        }

        renderBackground(context, mouseX, mouseY, delta);

        float centerX = this.width / 2f;
        float centerY = this.height / 2f;
        float cardX = centerX - CARD_WIDTH / 2f;
        float cardY = centerY - CARD_HEIGHT / 2f + 20;

        float slideY = (1f - animProgress) * 30f;
        cardY += slideY;

        Render2D.rect(cardX, cardY, CARD_WIDTH, CARD_HEIGHT, 0xFF10101C, CORNER_RADIUS);
        Render2D.outline(cardX, cardY, CARD_WIDTH, CARD_HEIGHT, 1f, 0xFF28283C, CORNER_RADIUS);

        float logoSize = 44f;
        float logoX = centerX - logoSize / 2f;
        float logoY = cardY + 24;
        Render2D.rect(logoX, logoY, logoSize, logoSize, 0xFF6366F1, 10f);
        float texSize = 36f;
        context.drawTexture(RenderPipelines.GUI_TEXTURED, EXCEL_TEXTURE,
                (int)(centerX - texSize / 2f), (int)(logoY + (logoSize - texSize) / 2f),
                0, 0, (int)texSize, (int)texSize, 360, 360, 0xFFFFFFFF);

        Fonts.BOLD.draw("Excel Client", centerX - Fonts.BOLD.getWidth("Excel Client", 14f) / 2f, logoY + logoSize + 12f, 14f, 0xFFE6E6F5);

        String subtitle = showRegister ? "Create account" : "Sign in";
        Fonts.REGULAR.draw(subtitle, centerX - Fonts.REGULAR.getWidth(subtitle, 11f) / 2f, logoY + logoSize + 30f, 11f, 0xFF78788C);

        float fieldX = cardX + 30;
        float fieldW = CARD_WIDTH - 60;
        float fieldY = logoY + logoSize + 54;

        fieldY = renderInput(context, fieldX, fieldY, fieldW, "Nickname", nickname, focusedField == 0);
        fieldY += 10;
        fieldY = renderInput(context, fieldX, fieldY, fieldW, "Password", password, focusedField == 1);
        fieldY += 10;

        float btnY;
        if (showRegister) {
            fieldY = renderInput(context, fieldX, fieldY, fieldW, "Confirm password", confirmPassword, focusedField == 2);
            fieldY += 16;
            btnY = fieldY;
            renderButton(context, fieldX, btnY, fieldW, "Create Account", 0xFF6366F1);
        } else {
            btnY = fieldY;
            renderButton(context, fieldX, btnY, fieldW, "Sign In", 0xFF6366F1);
        }

        float linkY = btnY + BTN_HEIGHT + 10;
        if (showRegister) {
            String linkText = "Already have an account? Sign in";
            float linkW = Fonts.REGULAR.getWidth(linkText, 10f);
            Fonts.REGULAR.draw(linkText, centerX - linkW / 2f, linkY, 10f, 0xFF818CF8);
        } else {
            String linkText = "Don't have an account? Register";
            float linkW = Fonts.REGULAR.getWidth(linkText, 10f);
            Fonts.REGULAR.draw(linkText, centerX - linkW / 2f, linkY, 10f, 0xFF818CF8);
        }

        if (!statusMessage.isEmpty()) {
            int statusColor = statusSuccess ? 0xFF22C55E : 0xFFEF4444;
            Fonts.REGULAR.draw(statusMessage, centerX - Fonts.REGULAR.getWidth(statusMessage, 9f) / 2f, linkY + 16, 9f, statusColor);
        }

        if (loading) {
            Render2D.rect(cardX, cardY, CARD_WIDTH, CARD_HEIGHT, 0x80000000, CORNER_RADIUS);
            String loadingText = "Loading...";
            Fonts.BOLD.draw(loadingText, centerX - Fonts.BOLD.getWidth(loadingText, 13f) / 2f, centerY - 6f, 13f, 0xFFE6E6F5);
        }
    }

    private float renderInput(DrawContext context, float x, float y, float w, String label, String value, boolean focused) {
        Fonts.REGULAR.draw(label, x, y, 9f, 0xFF78788C);
        float inputY = y + 13;

        int bgColor = focused ? 0xFF1A1A2E : 0xFF141423;
        int borderColor = focused ? 0xFF6366F1 : 0xFF373750;
        Render2D.rect(x, inputY, w, INPUT_HEIGHT, bgColor, INPUT_RADIUS);
        Render2D.outline(x, inputY, w, INPUT_HEIGHT, 0.5f, borderColor, INPUT_RADIUS);

        if (focused) {
            long time = System.currentTimeMillis() / 500;
            if (time % 2 == 0) {
                float textW = Fonts.REGULAR.getWidth(value, 11f);
                Render2D.rect(x + 10 + textW + 1, inputY + 5, 1, INPUT_HEIGHT - 10, 0xFFE6E6F5);
            }
        }

        if (!value.isEmpty()) {
            String display = label.contains("assword") ? maskPassword(value) : value;
            Fonts.REGULAR.draw(display, x + 10, inputY + (INPUT_HEIGHT - 11) / 2f, 11f, 0xFFE6E6F5);
        } else if (!focused) {
            String placeholder;
            if (label.contains("Confirm")) {
                placeholder = "Repeat your password";
            } else if (label.contains("assword")) {
                placeholder = "At least 4 characters";
            } else {
                placeholder = "Enter your nickname";
            }
            Fonts.REGULAR.draw(placeholder, x + 10, inputY + (INPUT_HEIGHT - 11) / 2f, 11f, 0xFF555555);
        }

        return inputY + INPUT_HEIGHT;
    }

    private void renderButton(DrawContext context, float x, float y, float w, String text, int color) {
        Render2D.rect(x, y, w, BTN_HEIGHT, color, INPUT_RADIUS);
        float textW = Fonts.BOLD.getWidth(text, 11f);
        Fonts.BOLD.draw(text, x + (w - textW) / 2f, y + (BTN_HEIGHT - 11) / 2f, 11f, 0xFFFFFFFF);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        float mx = (float) click.x();
        float my = (float) click.y();
        float centerX = this.width / 2f;
        float cardX = centerX - CARD_WIDTH / 2f;
        float cardY = this.height / 2f - CARD_HEIGHT / 2f + 20 + (1f - animProgress) * 30f;
        float fieldX = cardX + 30;
        float fieldW = CARD_WIDTH - 60;
        float fieldY = cardY + 44 + 44 + 54;

        if (mx >= fieldX && mx <= fieldX + fieldW) {
            if (my >= fieldY && my <= fieldY + INPUT_HEIGHT) {
                focusedField = 0;
                return true;
            }
            fieldY += INPUT_HEIGHT + 10 + 13;
            if (my >= fieldY && my <= fieldY + INPUT_HEIGHT) {
                focusedField = 1;
                return true;
            }
            if (showRegister) {
                fieldY += INPUT_HEIGHT + 10 + 13;
                if (my >= fieldY && my <= fieldY + INPUT_HEIGHT) {
                    focusedField = 2;
                    return true;
                }
                fieldY += INPUT_HEIGHT + 16;
            } else {
                fieldY += INPUT_HEIGHT + 16;
            }

            if (my >= fieldY && my <= fieldY + BTN_HEIGHT) {
                submitForm();
                return true;
            }
        }

        float linkY = fieldY + BTN_HEIGHT + 10;
        if (my >= linkY - 4 && my <= linkY + 14) {
            if (showRegister) {
                goToLogin = true;
            } else {
                goToRegister = true;
            }
            return true;
        }

        focusedField = -1;
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean keyPressed(KeyInput key) {
        if (key.key() == 256) {
            if (showRegister) {
                goToLogin = true;
            } else {
                client.setScreen(null);
            }
            return true;
        }
        if (key.key() == 257 || key.key() == 335) {
            submitForm();
            return true;
        }
        if (key.key() == 258) {
            focusedField = (focusedField + 1) % (showRegister ? 3 : 2);
            return true;
        }
        return super.keyPressed(key);
    }

    @Override
    public boolean charTyped(CharInput chr) {
        if (chr.codepoint() == 8 || chr.codepoint() == 127) {
            deleteChar();
            return true;
        }
        if (chr.codepoint() >= 32 && chr.codepoint() < 127) {
            String c = Character.toString((char) chr.codepoint());
            switch (focusedField) {
                case 0 -> {
                    if (nickname.length() < 16) nickname += c;
                    return true;
                }
                case 1 -> {
                    if (password.length() < 32) password += c;
                    return true;
                }
                case 2 -> {
                    if (confirmPassword.length() < 32) confirmPassword += c;
                    return true;
                }
            }
        }
        return super.charTyped(chr);
    }

    private void deleteChar() {
        switch (focusedField) {
            case 0 -> {
                if (!nickname.isEmpty()) nickname = nickname.substring(0, nickname.length() - 1);
            }
            case 1 -> {
                if (!password.isEmpty()) password = password.substring(0, password.length() - 1);
            }
            case 2 -> {
                if (!confirmPassword.isEmpty()) confirmPassword = confirmPassword.substring(0, confirmPassword.length() - 1);
            }
        }
    }

    private void submitForm() {
        if (loading) return;

        if (nickname.isEmpty() || password.isEmpty()) {
            statusMessage = "Fill in all fields";
            statusSuccess = false;
            return;
        }

        loading = true;
        statusMessage = "";
        AuthManager.getInstance().login(nickname, password).thenAccept(result -> {
            loading = false;
            statusMessage = result.message;
            statusSuccess = result.success;
            if (result.success) {
                delayedSwitch(() -> client.setScreen(new MainMenuScreen()));
            }
        });
    }

    private void delayedSwitch(Runnable action) {
        new Thread(() -> {
            try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
            client.execute(action);
        }).start();
    }

    private String maskPassword(String p) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < p.length(); i++) sb.append('*');
        return sb.toString();
    }

    @Override
    public boolean shouldPause() {
        return true;
    }
}
