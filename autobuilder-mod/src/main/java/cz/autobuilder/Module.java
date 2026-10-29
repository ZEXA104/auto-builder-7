package cz.autobuilder;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;

import java.util.ArrayList;
import java.util.List;

/** Základ každého modulu klienta. */
public abstract class Module {
    public final String id, name, description;
    public boolean enabled;
    public final List<Setting> settings = new ArrayList<>();

    protected Module(String id, String name, String description) {
        this.id = id;
        this.name = name;
        this.description = description;
    }

    public void toggle() {
        setEnabled(!enabled);
    }

    public void setEnabled(boolean e) {
        if (enabled == e) return;
        enabled = e;
        if (e) onEnable(); else onDisable();
    }

    public void onEnable() {}
    public void onDisable() {}
    public void onTick(MinecraftClient mc) {}

    public abstract Screen createScreen(Screen parent);
}
