package cz.autobuilder;

import net.minecraft.client.MinecraftClient;

import java.util.List;

public final class ModuleManager {
    public static final SchematicModule SCHEMATIC = new SchematicModule();
    public static final List<Module> ALL = List.of(SCHEMATIC);

    private ModuleManager() {}

    public static void tick(MinecraftClient mc) {
        for (Module m : ALL) {
            if (m.enabled) m.onTick(mc);
        }
    }
}
