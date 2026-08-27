package com.example.pickuprange.client;

import com.example.pickuprange.PickupRangeMod;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * Registers the client keybind that opens the {@link PickupRangeScreen}.
 *
 * <p>Default key: {@code R} — players can rebind it in Minecraft's Controls settings
 * under the "Pickup Range" category.
 */
@Environment(EnvType.CLIENT)
public final class KeyBindings {

    private static final KeyMapping.Category PICKUP_RANGE_CATEGORY =
            KeyMapping.Category.register(Identifier.withDefaultNamespace("pickuprange"));

    private static KeyMapping openScreenKey;

    private KeyBindings() {}

    /**
     * Registers all keybindings. Must be called from
     * {@link com.example.pickuprange.PickupRangeClientMod#onInitializeClient()}.
     */
    public static void register() {
        openScreenKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.pickuprange.open_screen",
                GLFW.GLFW_KEY_R,
                PICKUP_RANGE_CATEGORY
        ));

        ClientTickEvents.END_CLIENT_TICK.register(KeyBindings::onClientTick);

        PickupRangeMod.LOGGER.debug("Key bindings registered.");
    }

    private static void onClientTick(Minecraft client) {
        if (client.player == null) return;

        while (openScreenKey.consumeClick()) {
            // Don't stack screens.
            if (client.screen == null) {
                client.setScreen(new PickupRangeScreen());
            }
        }
    }
}
