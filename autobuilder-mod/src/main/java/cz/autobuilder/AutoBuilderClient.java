package cz.autobuilder;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class AutoBuilderClient implements ClientModInitializer {
    private static KeyBinding openKey;
    private static final List<KeyBinding> moduleKeys = new ArrayList<>();

    @Override
    public void onInitializeClient() {
        KeyBinding.Category category = KeyBinding.Category.create(Identifier.of("autobuilder", "main"));

        openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.autobuilder.open",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                category
        ));

        // Každý modul dostane vlastní klávesu (výchozí: nenastaveno), nastavíš ji v Ovládání.
        for (Module m : ModuleManager.ALL) {
            moduleKeys.add(KeyBindingHelper.registerKeyBinding(new KeyBinding(
                    "key.autobuilder.module." + m.id,
                    InputUtil.Type.KEYSYM,
                    GLFW.GLFW_KEY_UNKNOWN,
                    category
            )));
        }

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openKey.wasPressed()) {
                if (client.currentScreen == null && client.player != null) {
                    client.setScreen(new BuilderScreen());
                }
            }
            for (int i = 0; i < moduleKeys.size(); i++) {
                while (moduleKeys.get(i).wasPressed()) {
                    Module m = ModuleManager.ALL.get(i);
                    m.toggle();
                    if (client.player != null) {
                        client.player.sendMessage(Text.literal(m.name + ": " + (m.enabled ? "zapnuto" : "vypnuto")), true);
                    }
                }
            }
            ModuleManager.tick(client);
        });
    }
}
