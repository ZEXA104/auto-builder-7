package cz.autobuilder;

import net.minecraft.client.MinecraftClient;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** Režim "Příkazy": fronta příkazů setblock/fill. */
public final class Builder {
    private static final ArrayDeque<String> queue = new ArrayDeque<>();
    private static int total = 0;
    private static int cooldown = 0;

    private Builder() {}

    public static void start(Schematic s, BlockPos origin) {
        queue.clear();
        queue.addAll(s.commands(origin));
        total = queue.size();
        cooldown = 0;
    }

    public static void stop() {
        queue.clear();
    }

    public static boolean active() {
        return !queue.isEmpty();
    }

    public static void tick(MinecraftClient mc, int perTick) {
        if (queue.isEmpty()) return;
        if (mc.player == null || mc.world == null) {
            queue.clear();
            return;
        }

        IntegratedServer server = mc.getServer();
        if (server != null) {
            ServerWorld world = server.getWorld(mc.world.getRegistryKey());
            if (world == null) return;
            List<String> batch = new ArrayList<>();
            for (int i = 0; i < perTick && !queue.isEmpty(); i++) batch.add(queue.poll());
            ServerCommandSource src = server.getCommandSource().withWorld(world).withSilent();
            server.execute(() -> {
                for (String cmd : batch) {
                    try {
                        server.getCommandManager().getDispatcher().execute(cmd, src);
                    } catch (Exception ignored) {
                    }
                }
            });
        } else {
            if (cooldown-- > 0) return;
            cooldown = 4;
            mc.player.networkHandler.sendChatCommand(queue.poll());
        }

        int done = total - queue.size();
        mc.player.sendMessage(Text.literal("Schematic Builder: " + done + " / " + total), true);
    }
}
