package com.example.pickuprange.client;

import com.example.pickuprange.PickupRangeClientMod;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * In-game GUI screen that lets players adjust their item and XP pickup ranges
 * using sliders or direct numeric inputs, without needing to type commands.
 *
 * <p>Opened via the configurable keybind (default: {@code R}).
 *
 * <p>Clicking <b>Apply</b> sends {@code /pickuprange set} and {@code /pickuprange setxp}
 * to the server. The server enforces permissions: if {@code allowPlayerOverride} is false,
 * the commands will be rejected and the player will see a chat error — no special handling
 * needed here.
 *
 * <p>The sliders are clamped to the server-provided {@code [minRange, maxRange]} bounds,
 * received via {@link com.example.pickuprange.network.SyncConfigPayload} on join.
 * Before server sync, the sliders use the default 0.5–64.0 bounds.
 */
@Environment(EnvType.CLIENT)
public class PickupRangeScreen extends Screen {

    private static final int PANEL_W  = 240;
    private static final int PANEL_H  = 192;
    private static final int SLIDER_W = 200;
    private static final int SLIDER_H = 20;
    private static final int INPUT_W  = 72;
    private static final int BTN_W    = 96;
    private static final Pattern RANGE_INPUT = Pattern.compile("\\d*(\\.\\d*)?");

    // Values being edited (updated by sliders in real time).
    private double pendingItemRange;
    private double pendingXpRange;
    private RangeSlider itemSlider;
    private RangeSlider xpSlider;
    private EditBox itemInput;
    private EditBox xpInput;
    private boolean syncingTextFields;
    private boolean wasItemInputFocused;
    private boolean wasXpInputFocused;

    public PickupRangeScreen() {
        super(Component.translatable("pickuprange.screen.title"));
        this.pendingItemRange = PickupRangeClientMod.getClientItemRange();
        this.pendingXpRange   = PickupRangeClientMod.getClientXpRange();
    }

    @Override
    protected void init() {
        int cx = width  / 2;
        int cy = height / 2;
        int sliderX = cx - SLIDER_W / 2;
        int inputX = cx - INPUT_W / 2;

        double min = PickupRangeClientMod.getClientMinRange();
        double max = PickupRangeClientMod.getClientMaxRange();
        int itemSliderY = cy - 52;
        int itemInputY = itemSliderY + 24;
        int xpSliderY = cy + 2;
        int xpInputY = xpSliderY + 24;
        int buttonY = cy + 58;

        // --- Item range slider ---
        itemSlider = addRenderableWidget(new RangeSlider(
                sliderX, itemSliderY, SLIDER_W, SLIDER_H,
                Component.translatable("pickuprange.screen.item_range"),
                pendingItemRange, min, max,
                this::onItemSliderChanged
        ));
        itemInput = addRenderableWidget(createRangeInput(
                inputX, itemInputY,
                Component.translatable("pickuprange.screen.item_range"),
                itemSlider, pendingItemRange
        ));

        // --- XP range slider ---
        xpSlider = addRenderableWidget(new RangeSlider(
                sliderX, xpSliderY, SLIDER_W, SLIDER_H,
                Component.translatable("pickuprange.screen.xp_range"),
                pendingXpRange, min, max,
                this::onXpSliderChanged
        ));
        xpInput = addRenderableWidget(createRangeInput(
                inputX, xpInputY,
                Component.translatable("pickuprange.screen.xp_range"),
                xpSlider, pendingXpRange
        ));

        // --- Apply button ---
        addRenderableWidget(Button.builder(
                Component.translatable("pickuprange.screen.apply"),
                btn -> applyAndClose()
        ).bounds(cx - BTN_W - 4, buttonY, BTN_W, SLIDER_H).build());

        // --- Cancel button ---
        addRenderableWidget(Button.builder(
                Component.translatable("gui.cancel"),
                btn -> onClose()
        ).bounds(cx + 4, buttonY, BTN_W, SLIDER_H).build());

        setInitialFocus(itemInput);
        wasItemInputFocused = itemInput.isFocused();
        wasXpInputFocused = xpInput.isFocused();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.render(graphics, mouseX, mouseY, delta);

        int cx = width  / 2;
        int cy = height / 2;

        // Title
        graphics.drawCenteredString(font, title, cx, cy - 84, 0xFFFFFF);
    }

    @Override
    public void tick() {
        super.tick();

        if (wasItemInputFocused && !itemInput.isFocused()) {
            commitRangeInput(itemInput, itemSlider);
        }
        if (wasXpInputFocused && !xpInput.isFocused()) {
            commitRangeInput(xpInput, xpSlider);
        }

        wasItemInputFocused = itemInput.isFocused();
        wasXpInputFocused = xpInput.isFocused();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            if (itemInput != null && itemInput.isFocused()) {
                commitRangeInput(itemInput, itemSlider);
                return true;
            }
            if (xpInput != null && xpInput.isFocused()) {
                commitRangeInput(xpInput, xpSlider);
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** Sends the pending values as commands and closes the screen. */
    private void applyAndClose() {
        commitRangeInput(itemInput, itemSlider);
        commitRangeInput(xpInput, xpSlider);

        var connection = Minecraft.getInstance().getConnection();
        if (connection != null) {
            connection.sendCommand("pickuprange set " + formatRange(pendingItemRange));
            connection.sendCommand("pickuprange setxp " + formatRange(pendingXpRange));
        }
        onClose();
    }

    /** The screen should not pause the game in multiplayer. */
    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private EditBox createRangeInput(int x, int y, Component label, RangeSlider slider, double initialValue) {
        EditBox input = new EditBox(font, x, y, INPUT_W, SLIDER_H, label);
        input.setMaxLength(8);
        input.setHint(Component.literal(formatRange(slider.getMin()) + " - " + formatRange(slider.getMax())));
        input.setFilter(text -> RANGE_INPUT.matcher(text).matches());
        input.setValue(formatRange(initialValue));
        input.setResponder(text -> onRangeInputChanged(input, slider, text));
        return input;
    }

    private void onItemSliderChanged(double value) {
        pendingItemRange = value;
        if (itemInput != null && !itemInput.isFocused()) {
            syncInputValue(itemInput, value);
        }
    }

    private void onXpSliderChanged(double value) {
        pendingXpRange = value;
        if (xpInput != null && !xpInput.isFocused()) {
            syncInputValue(xpInput, value);
        }
    }

    private void onRangeInputChanged(EditBox input, RangeSlider slider, String text) {
        if (syncingTextFields) {
            return;
        }

        Double parsed = parseRange(text);
        if (parsed == null) {
            return;
        }

        double normalized = normalizeRange(parsed);
        if (normalized < slider.getMin() || normalized > slider.getMax()) {
            return;
        }
        slider.setActualValue(normalized);
    }

    private void commitRangeInput(EditBox input, RangeSlider slider) {
        if (input == null || slider == null) {
            return;
        }

        Double parsed = parseRange(input.getValue());
        double committed = parsed != null
                ? clamp(normalizeRange(parsed), slider.getMin(), slider.getMax())
                : slider.getActualValue();

        slider.setActualValue(committed);
        syncInputValue(input, committed);
    }

    private void syncInputValue(EditBox input, double value) {
        syncingTextFields = true;
        input.setValue(formatRange(value));
        syncingTextFields = false;
    }

    private static Double parseRange(String text) {
        if (text == null || text.isBlank() || ".".equals(text)) {
            return null;
        }

        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static double normalizeRange(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String formatRange(double value) {
        return String.format(Locale.ROOT, "%.1f", normalizeRange(value));
    }

    // -------------------------------------------------------------------------
    // Inner slider widget
    // -------------------------------------------------------------------------

    /**
     * A horizontal slider that maps a [min, max] double range onto the [0, 1] internal value
     * of {@link AbstractSliderButton} and displays the current value with its label.
     */
    @Environment(EnvType.CLIENT)
    private static final class RangeSlider extends AbstractSliderButton {

        private final Component label;
        private final double min;
        private final double max;
        private final Consumer<Double> onChange;

        RangeSlider(int x, int y, int width, int height,
                    Component label,
                    double initial, double min, double max,
                    Consumer<Double> onChange) {
            super(x, y, width, height, Component.empty(), 0.0);
            this.label    = label;
            this.min      = min;
            this.max      = max;
            this.onChange = onChange;
            setActualValue(initial);
        }

        /** Returns the actual value in [min, max] from the normalized slider position. */
        private double getActualValue() {
            if (max <= min) {
                return normalizeRange(min);
            }
            return normalizeRange(min + value * (max - min));
        }

        private double getMin() {
            return min;
        }

        private double getMax() {
            return max;
        }

        private void setActualValue(double actualValue) {
            if (max <= min) {
                setNormalizedValue(0.0);
                return;
            }

            double clamped = clamp(normalizeRange(actualValue), min, max);
            setNormalizedValue((clamped - min) / (max - min));
        }

        /** Mirrors the private 1.20.6 slider setter while keeping callbacks intact. */
        private void setNormalizedValue(double normalized) {
            double previous = value;
            value = clamp(normalized, 0.0, 1.0);
            if (previous != value) {
                applyValue();
            }
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal(label.getString() + ": " + formatRange(getActualValue())));
        }

        @Override
        protected void applyValue() {
            onChange.accept(getActualValue());
        }
    }
}
