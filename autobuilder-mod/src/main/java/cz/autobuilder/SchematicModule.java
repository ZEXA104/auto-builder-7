package cz.autobuilder;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SchematicModule extends Module {
    public Schematic schematic;
    public String fileName;
    public String status = "Vyber soubor ze složky schematics.";

    public final Setting mode = Setting.mode("Režim", "Příkazy", "Pokládání");
    public final Setting speed = Setting.number("Rychlost (akcí/s)", 40, 1, 400, 5);
    public final Setting reach = Setting.number("Dosah", 4.0, 2.0, 4.5, 0.5);

    private boolean building;
    private BlockPos origin;
    private boolean[] done;
    private int[] tries;
    private long[] lastTry;
    private Block[] blockOf;
    private int start;
    private long tickCount;
    private double acc;
    private String missing = null;

    public SchematicModule() {
        super("schematic", "Schematic Builder", "Postaví .litematic a .schem");
        settings.add(mode);
        settings.add(speed);
        settings.add(reach);
    }

    public boolean isBuilding() {
        return building;
    }

    public void load(Path p) throws IOException {
        Schematic s = Schematic.load(p);
        schematic = s;
        fileName = p.getFileName().toString();
        if (s.blocks.isEmpty()) {
            status = "Soubor je prázdný nebo ho nelze přečíst.";
        } else {
            status = "Načteno: " + s.blocks.size() + " bloků, " + s.sizeX + "x" + s.sizeY + "x" + s.sizeZ;
        }
    }

    public void startBuild(MinecraftClient mc) {
        if (schematic == null || schematic.blocks.isEmpty() || mc.player == null) {
            status = "Nejdřív vyber platný soubor.";
            return;
        }
        origin = mc.player.getBlockPos();
        setEnabled(true);
        acc = 0;
        tickCount = 0;
        missing = null;

        if (mode.index == 0) {
            Builder.start(schematic, origin);
        } else {
            int total = schematic.blocks.size();
            done = new boolean[total];
            tries = new int[total];
            lastTry = new long[total];
            start = 0;
            blockOf = new Block[schematic.states.size()];
            for (int i = 0; i < blockOf.length; i++) {
                String st = schematic.states.get(i);
                int b = st.indexOf('[');
                String nm = b >= 0 ? st.substring(0, b) : st;
                Identifier id = Identifier.tryParse(nm);
                Block blk = id == null ? null : Registries.BLOCK.get(id);
                blockOf[i] = (blk == null || blk == Blocks.AIR) ? null : blk;
            }
        }
        building = true;
        status = "Stavím...";
    }

    /** Seznam potřebných materiálů a kolik jich máš v inventáři. */
    public List<String> materialLines(MinecraftClient mc) {
        List<String> out = new ArrayList<>();
        if (schematic == null) return out;

        int[] counts = new int[schematic.states.size()];
        for (Schematic.Entry e : schematic.blocks) counts[e.state()]++;

        Map<String, Integer> need = new LinkedHashMap<>();
        for (int i = 0; i < counts.length; i++) {
            String st = schematic.states.get(i);
            int b = st.indexOf('[');
            String nm = b >= 0 ? st.substring(0, b) : st;
            need.merge(nm, counts[i], Integer::sum);
        }

        List<Map.Entry<String, Integer>> list = new ArrayList<>(need.entrySet());
        list.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

        for (Map.Entry<String, Integer> en : list) {
            String nm = en.getKey();
            int want = en.getValue();
            int have = 0;
            Identifier id = Identifier.tryParse(nm);
            Block blk = id == null ? null : Registries.BLOCK.get(id);
            if (blk != null && mc.player != null) {
                Item it = blk.asItem();
                for (int i = 0; i < 36; i++) {
                    ItemStack stack = mc.player.getInventory().getStack(i);
                    if (stack.isOf(it)) have += stack.getCount();
                }
            }
            String shortName = nm.startsWith("minecraft:") ? nm.substring(10) : nm;
            String line = shortName + ": " + want;
            if (have >= want) line += "  (máš dost)";
            else line += "  (máš " + have + ", chybí " + (want - have) + ")";
            out.add(line);
        }
        return out;
    }

    public void stopBuild() {
        building = false;
        Builder.stop();
        status = "Stavění zastaveno.";
    }

    @Override
    public void onDisable() {
        if (building) stopBuild();
    }

    @Override
    public Screen createScreen(Screen parent) {
        return new SchematicScreen(parent);
    }

    @Override
    public void onTick(MinecraftClient mc) {
        if (!building) return;
        if (mc.player == null || mc.world == null) {
            building = false;
            return;
        }
        tickCount++;
        acc += speed.value / 20.0;
        int n = (int) acc;
        acc -= n;
        if (n <= 0) return;

        if (mode.index == 0) {
            Builder.tick(mc, n);
            if (!Builder.active()) {
                building = false;
                status = "Hotovo!";
                mc.player.sendMessage(Text.literal("Schematic Builder: hotovo!"), true);
            }
        } else {
            tickPlace(mc, n);
        }
    }

    private void tickPlace(MinecraftClient mc, int n) {
        ClientPlayerEntity p = mc.player;
        ClientWorld w = mc.world;
        if (mc.interactionManager == null) return;

        int total = schematic.blocks.size();
        Vec3d eye = p.getEyePos();
        double r = reach.value;
        double r2 = r * r;
        int placed = 0, scanned = 0;
        missing = null;

        for (int i = start; i < total && scanned < 4000 && placed < n; i++) {
            if (done[i]) continue;
            scanned++;
            Schematic.Entry e = schematic.blocks.get(i);
            BlockPos pos = origin.add(e.x(), e.y(), e.z());
            Block target = blockOf[e.state()];
            if (target == null || target.asItem() == Items.AIR) { done[i] = true; continue; }

            BlockState cur = w.getBlockState(pos);
            if (cur.isOf(target)) { done[i] = true; continue; }
            if (!cur.isAir() && !cur.isReplaceable()) { done[i] = true; continue; }
            if (tries[i] > 8) { done[i] = true; continue; }
            if (tickCount - lastTry[i] < 6) continue;
            if (eye.squaredDistanceTo(Vec3d.ofCenter(pos)) > r2) continue;

            BlockHitResult hit = findSupport(w, pos);
            if (hit == null) continue;

            int slot = findItem(p, target.asItem());
            if (slot < 0) {
                missing = target.getName().getString();
                continue;
            }
            if (!selectSlot(mc, p, slot)) break;

            mc.interactionManager.interactBlock(p, Hand.MAIN_HAND, hit);
            p.swingHand(Hand.MAIN_HAND);
            tries[i]++;
            lastTry[i] = tickCount;
            placed++;
        }

        while (start < total && done[start]) start++;

        if (start >= total) {
            building = false;
            status = "Hotovo!";
            p.sendMessage(Text.literal("Schematic Builder: hotovo!"), true);
            return;
        }
        if (tickCount % 10 == 0) {
            int doneCount = 0;
            for (boolean d : done) if (d) doneCount++;
            String msg = "Schematic Builder: " + doneCount + " / " + total;
            if (missing != null) msg += "  |  chybí: " + missing;
            else if (placed == 0) msg += "  |  popojdi blíž ke stavbě";
            p.sendMessage(Text.literal(msg), true);
        }
    }

    private BlockHitResult findSupport(ClientWorld w, BlockPos pos) {
        for (Direction d : Direction.values()) {
            BlockPos nb = pos.offset(d);
            BlockState st = w.getBlockState(nb);
            if (st.isAir() || st.isReplaceable()) continue;
            Direction face = d.getOpposite();
            Vec3d hitPos = Vec3d.ofCenter(nb).add(face.getOffsetX() * 0.5, face.getOffsetY() * 0.5, face.getOffsetZ() * 0.5);
            return new BlockHitResult(hitPos, face, nb, false);
        }
        return null;
    }

    private int findItem(ClientPlayerEntity p, Item item) {
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < 36; i++) {
            if (inv.getStack(i).isOf(item)) return i;
        }
        return -1;
    }

    /** true = hotbar slot je vybraný a můžeme klást; false = čekáme, až se předmět přesune. */
    private boolean selectSlot(MinecraftClient mc, ClientPlayerEntity p, int idx) {
        if (idx < 9) {
            p.getInventory().setSelectedSlot(idx);
            return true;
        }
        mc.interactionManager.clickSlot(p.playerScreenHandler.syncId, idx, 8, SlotActionType.SWAP, p);
        p.getInventory().setSelectedSlot(8);
        return false;
    }
}
