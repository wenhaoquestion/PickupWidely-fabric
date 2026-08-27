package com.example.pickuprange.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

@Environment(EnvType.CLIENT)
public final class KeyBindings {
    private static KeyBinding openScreenKey;

    private KeyBindings() {
    }

    public static void register() {
        openScreenKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.pickuprange.open_screen",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_R,
                "key.category.pickuprange"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) {
                return;
            }
            while (openScreenKey.wasPressed()) {
                if (client.currentScreen == null) {
                    client.setScreen(new PickupRangeScreen());
                }
            }
        });
    }
}
