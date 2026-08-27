package com.example.pickuprange.client;

import com.example.pickuprange.PickupRangeClientMod;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;
import java.util.function.Consumer;
import java.util.regex.Pattern;

@Environment(EnvType.CLIENT)
public final class PickupRangeScreen extends Screen {
    private static final int SLIDER_WIDTH = 200;
    private static final int HEIGHT = 20;
    private static final int INPUT_WIDTH = 72;
    private static final int BUTTON_WIDTH = 96;
    private static final Pattern RANGE_INPUT = Pattern.compile("\\d*(\\.\\d*)?");

    private double pendingItemRange;
    private double pendingXpRange;
    private RangeSlider itemSlider;
    private RangeSlider xpSlider;
    private TextFieldWidget itemInput;
    private TextFieldWidget xpInput;
    private boolean syncingText;

    public PickupRangeScreen() {
        super(new TranslatableText("pickuprange.screen.title"));
        pendingItemRange = PickupRangeClientMod.getClientItemRange();
        pendingXpRange = PickupRangeClientMod.getClientXpRange();
    }

    @Override
    protected void init() {
        int centerX = width / 2;
        int centerY = height / 2;
        double min = PickupRangeClientMod.getClientMinRange();
        double max = PickupRangeClientMod.getClientMaxRange();

        itemSlider = addDrawableChild(new RangeSlider(
                centerX - SLIDER_WIDTH / 2, centerY - 52, SLIDER_WIDTH, HEIGHT,
                new TranslatableText("pickuprange.screen.item_range"),
                pendingItemRange, min, max, value -> {
                    pendingItemRange = value;
                    if (itemInput != null && !itemInput.isFocused()) {
                        syncInput(itemInput, value);
                    }
                }));

        itemInput = addDrawableChild(createInput(centerX - INPUT_WIDTH / 2, centerY - 28,
                new TranslatableText("pickuprange.screen.item_range"),
                itemSlider, pendingItemRange));

        xpSlider = addDrawableChild(new RangeSlider(
                centerX - SLIDER_WIDTH / 2, centerY + 2, SLIDER_WIDTH, HEIGHT,
                new TranslatableText("pickuprange.screen.xp_range"),
                pendingXpRange, min, max, value -> {
                    pendingXpRange = value;
                    if (xpInput != null && !xpInput.isFocused()) {
                        syncInput(xpInput, value);
                    }
                }));

        xpInput = addDrawableChild(createInput(centerX - INPUT_WIDTH / 2, centerY + 26,
                new TranslatableText("pickuprange.screen.xp_range"),
                xpSlider, pendingXpRange));

        addDrawableChild(new ButtonWidget(
                centerX - BUTTON_WIDTH - 4, centerY + 58, BUTTON_WIDTH, HEIGHT,
                new TranslatableText("pickuprange.screen.apply"),
                button -> applyAndClose()));

        addDrawableChild(new ButtonWidget(
                centerX + 4, centerY + 58, BUTTON_WIDTH, HEIGHT,
                new TranslatableText("gui.cancel"),
                button -> onClose()));

        setFocused(itemInput);
        itemInput.setTextFieldFocused(true);
    }

    @Override
    public void tick() {
        if (itemInput != null) {
            itemInput.tick();
        }
        if (xpInput != null) {
            xpInput.tick();
        }
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        renderBackground(matrices);
        super.render(matrices, mouseX, mouseY, delta);
        drawCenteredText(matrices, textRenderer, title, width / 2, height / 2 - 84, 0xFFFFFF);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            if (itemInput != null && itemInput.isFocused()) {
                commitInput(itemInput, itemSlider);
                return true;
            }
            if (xpInput != null && xpInput.isFocused()) {
                commitInput(xpInput, xpSlider);
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private TextFieldWidget createInput(int x, int y, Text label,
                                        RangeSlider slider, double initial) {
        TextFieldWidget input = new TextFieldWidget(textRenderer, x, y,
                INPUT_WIDTH, HEIGHT, label);
        input.setMaxLength(8);
        input.setSuggestion(format(slider.getMin()) + " - " + format(slider.getMax()));
        input.setTextPredicate(text -> RANGE_INPUT.matcher(text).matches());
        input.setText(format(initial));
        input.setChangedListener(text -> {
            if (syncingText) {
                return;
            }
            Double parsed = parse(text);
            if (parsed != null && parsed >= slider.getMin() && parsed <= slider.getMax()) {
                slider.setActualValue(parsed);
            }
        });
        return input;
    }

    private void applyAndClose() {
        commitInput(itemInput, itemSlider);
        commitInput(xpInput, xpSlider);

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendChatMessage("/pickuprange set " + format(pendingItemRange));
            client.player.sendChatMessage("/pickuprange setxp " + format(pendingXpRange));
        }
        onClose();
    }

    private void commitInput(TextFieldWidget input, RangeSlider slider) {
        Double parsed = parse(input.getText());
        double value = parsed != null ? clamp(round(parsed), slider.getMin(), slider.getMax())
                : slider.getActualValue();
        slider.setActualValue(value);
        syncInput(input, value);
    }

    private void syncInput(TextFieldWidget input, double value) {
        syncingText = true;
        input.setText(format(value));
        syncingText = false;
    }

    private static Double parse(String text) {
        if (text == null || text.trim().isEmpty() || ".".equals(text)) {
            return null;
        }
        try {
            return Double.valueOf(text);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.1f", round(value));
    }

    private static final class RangeSlider extends SliderWidget {
        private final Text label;
        private final double min;
        private final double max;
        private final Consumer<Double> listener;

        private RangeSlider(int x, int y, int width, int height, Text label,
                            double initial, double min, double max,
                            Consumer<Double> listener) {
            super(x, y, width, height, new LiteralText(""), 0.0);
            this.label = label;
            this.min = min;
            this.max = max;
            this.listener = listener;
            setActualValue(initial);
        }

        private double getActualValue() {
            return max <= min ? round(min) : round(min + value * (max - min));
        }

        private double getMin() {
            return min;
        }

        private double getMax() {
            return max;
        }

        private void setActualValue(double actual) {
            value = max <= min ? 0.0 : (clamp(round(actual), min, max) - min) / (max - min);
            updateMessage();
            applyValue();
        }

        @Override
        protected void updateMessage() {
            setMessage(new LiteralText(label.getString() + ": " + format(getActualValue())));
        }

        @Override
        protected void applyValue() {
            listener.accept(getActualValue());
        }
    }
}
