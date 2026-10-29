package cz.autobuilder;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.util.math.BlockPos;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

/** Načte .litematic nebo .schem a převede ho na seznam bloků. */
public class Schematic {
    public record Entry(int x, int y, int z, int state) {}

    public final List<String> states = new ArrayList<>();
    public final List<Entry> blocks = new ArrayList<>();
    public int sizeX, sizeY, sizeZ;

    private final Map<String, Integer> stateIds = new HashMap<>();
    private int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
    private int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

    public static Schematic load(Path path) throws IOException {
        String n = path.getFileName().toString().toLowerCase();
        if (n.endsWith(".schem")) return loadSchem(path);
        return loadLitematic(path);
    }

    private static boolean isAir(String st) {
        return st.equals("minecraft:air") || st.equals("minecraft:cave_air") || st.equals("minecraft:void_air");
    }

    private void add(int x, int y, int z, String st) {
        Integer id = stateIds.get(st);
        if (id == null) {
            id = states.size();
            states.add(st);
            stateIds.put(st, id);
        }
        blocks.add(new Entry(x, y, z, id));
        minX = Math.min(minX, x); minY = Math.min(minY, y); minZ = Math.min(minZ, z);
        maxX = Math.max(maxX, x); maxY = Math.max(maxY, y); maxZ = Math.max(maxZ, z);
    }

    private void finish() {
        if (blocks.isEmpty()) return;
        final int fx = minX, fy = minY, fz = minZ;
        sizeX = maxX - minX + 1;
        sizeY = maxY - minY + 1;
        sizeZ = maxZ - minZ + 1;
        List<Entry> norm = new ArrayList<>(blocks.size());
        for (Entry e : blocks) norm.add(new Entry(e.x - fx, e.y - fy, e.z - fz, e.state));
        norm.sort(Comparator.comparingInt(Entry::y).thenComparingInt(Entry::z).thenComparingInt(Entry::x));
        blocks.clear();
        blocks.addAll(norm);
    }

    private static Schematic loadLitematic(Path path) throws IOException {
        NbtCompound root = NbtIo.readCompressed(path, NbtSizeTracker.ofUnlimitedBytes());
        NbtCompound regions = root.getCompoundOrEmpty("Regions");
        Schematic s = new Schematic();

        for (String key : regions.getKeys()) {
            NbtCompound r = regions.getCompoundOrEmpty(key);
            NbtCompound pos = r.getCompoundOrEmpty("Position");
            NbtCompound size = r.getCompoundOrEmpty("Size");
            int px = pos.getInt("x", 0), py = pos.getInt("y", 0), pz = pos.getInt("z", 0);
            int sx = size.getInt("x", 0), sy = size.getInt("y", 0), sz = size.getInt("z", 0);
            int ax = Math.abs(sx), ay = Math.abs(sy), az = Math.abs(sz);
            if (ax == 0 || ay == 0 || az == 0) continue;

            int ox = sx < 0 ? px + sx + 1 : px;
            int oy = sy < 0 ? py + sy + 1 : py;
            int oz = sz < 0 ? pz + sz + 1 : pz;

            NbtList pal = r.getListOrEmpty("BlockStatePalette");
            String[] ps = new String[pal.size()];
            for (int i = 0; i < ps.length; i++) {
                NbtCompound e = pal.getCompoundOrEmpty(i);
                String name = e.getString("Name", "minecraft:air");
                NbtCompound props = e.getCompoundOrEmpty("Properties");
                if (props.getKeys().isEmpty()) {
                    ps[i] = name;
                } else {
                    StringBuilder sb = new StringBuilder(name).append('[');
                    boolean first = true;
                    for (String k : new TreeSet<>(props.getKeys())) {
                        if (!first) sb.append(',');
                        sb.append(k).append('=').append(props.getString(k, ""));
                        first = false;
                    }
                    ps[i] = sb.append(']').toString();
                }
            }
            if (ps.length == 0) continue;

            long[] arr = r.getLongArray("BlockStates").orElse(new long[0]);
            int bits = Math.max(2, 32 - Integer.numberOfLeadingZeros(ps.length - 1));
            long mask = (1L << bits) - 1L;

            for (int y = 0; y < ay; y++) {
                for (int z = 0; z < az; z++) {
                    for (int x = 0; x < ax; x++) {
                        long idx = ((long) y * az + z) * ax + x;
                        long startOffset = idx * bits;
                        int startArr = (int) (startOffset >>> 6);
                        int endArr = (int) (((idx + 1) * bits - 1) >>> 6);
                        int startBit = (int) (startOffset & 63L);
                        if (endArr >= arr.length) continue;
                        long v;
                        if (startArr == endArr) {
                            v = (arr[startArr] >>> startBit) & mask;
                        } else {
                            int endOffset = 64 - startBit;
                            v = ((arr[startArr] >>> startBit) | (arr[endArr] << endOffset)) & mask;
                        }
                        if (v >= ps.length) continue;
                        String st = ps[(int) v];
                        if (isAir(st)) continue;
                        s.add(ox + x, oy + y, oz + z, st);
                    }
                }
            }
        }
        s.finish();
        return s;
    }

    private static Schematic loadSchem(Path path) throws IOException {
        NbtCompound root = NbtIo.readCompressed(path, NbtSizeTracker.ofUnlimitedBytes());
        if (root.contains("Schematic")) root = root.getCompoundOrEmpty("Schematic");

        int w = root.getShort("Width", (short) 0) & 0xFFFF;
        int h = root.getShort("Height", (short) 0) & 0xFFFF;
        int l = root.getShort("Length", (short) 0) & 0xFFFF;

        NbtCompound palC;
        byte[] data;
        if (root.contains("Blocks")) {
            NbtCompound b = root.getCompoundOrEmpty("Blocks");
            palC = b.getCompoundOrEmpty("Palette");
            data = b.getByteArray("Data").orElse(new byte[0]);
        } else {
            palC = root.getCompoundOrEmpty("Palette");
            data = root.getByteArray("BlockData").orElse(new byte[0]);
        }

        Schematic s = new Schematic();
        int maxIdx = -1;
        for (String k : palC.getKeys()) maxIdx = Math.max(maxIdx, palC.getInt(k, 0));
        String[] ps = new String[maxIdx + 1];
        for (String k : palC.getKeys()) ps[palC.getInt(k, 0)] = k;
        if (w == 0 || h == 0 || l == 0 || ps.length == 0) return s;

        int p = 0;
        long total = (long) w * h * l;
        for (long i = 0; i < total && p < data.length; i++) {
            int value = 0, shift = 0;
            int bt;
            do {
                bt = data[p++];
                value |= (bt & 0x7F) << shift;
                shift += 7;
            } while ((bt & 0x80) != 0 && p < data.length);

            if (value < 0 || value >= ps.length || ps[value] == null) continue;
            String st = ps[value];
            if (isAir(st)) continue;
            int x = (int) (i % w);
            int z = (int) ((i / w) % l);
            int y = (int) (i / ((long) w * l));
            s.add(x, y, z, st);
        }
        s.finish();
        return s;
    }

    /** Pro režim "Příkazy": souvislé řady v ose X se slučují do /fill. */
    public List<String> commands(BlockPos origin) {
        List<String> out = new ArrayList<>();
        int i = 0;
        while (i < blocks.size()) {
            Entry a = blocks.get(i);
            int j = i;
            while (j + 1 < blocks.size()) {
                Entry n = blocks.get(j + 1);
                Entry p = blocks.get(j);
                if (n.y == a.y && n.z == a.z && n.state == a.state && n.x == p.x + 1) j++;
                else break;
            }
            Entry b = blocks.get(j);
            String st = states.get(a.state);
            int x1 = origin.getX() + a.x, y = origin.getY() + a.y, z = origin.getZ() + a.z;
            if (j == i) {
                out.add("setblock " + x1 + " " + y + " " + z + " " + st);
            } else {
                int x2 = origin.getX() + b.x;
                out.add("fill " + x1 + " " + y + " " + z + " " + x2 + " " + y + " " + z + " " + st);
            }
            i = j + 1;
        }
        return out;
    }
}
