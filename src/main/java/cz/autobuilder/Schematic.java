package cz.autobuilder;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.util.math.BlockPos;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

/** Načte .litematic (gzip NBT) a převede ho na seznam příkazů setblock/fill. */
public class Schematic {
    public record Entry(int x, int y, int z, int state) {}

    public final List<String> states = new ArrayList<>();
    public final List<Entry> blocks = new ArrayList<>();
    public int sizeX, sizeY, sizeZ;

    public static Schematic load(Path path) throws IOException {
        NbtCompound root = NbtIo.readCompressed(path, NbtSizeTracker.ofUnlimitedBytes());
        NbtCompound regions = root.getCompoundOrEmpty("Regions");

        Schematic s = new Schematic();
        Map<String, Integer> stateIds = new HashMap<>();
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

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
                        if (st.equals("minecraft:air") || st.equals("minecraft:cave_air") || st.equals("minecraft:void_air")) continue;

                        Integer id = stateIds.get(st);
                        if (id == null) {
                            id = s.states.size();
                            s.states.add(st);
                            stateIds.put(st, id);
                        }
                        int wx = ox + x, wy = oy + y, wz = oz + z;
                        s.blocks.add(new Entry(wx, wy, wz, id));
                        minX = Math.min(minX, wx); minY = Math.min(minY, wy); minZ = Math.min(minZ, wz);
                        maxX = Math.max(maxX, wx); maxY = Math.max(maxY, wy); maxZ = Math.max(maxZ, wz);
                    }
                }
            }
        }

        if (s.blocks.isEmpty()) return s;

        final int fx = minX, fy = minY, fz = minZ;
        s.sizeX = maxX - minX + 1;
        s.sizeY = maxY - minY + 1;
        s.sizeZ = maxZ - minZ + 1;
        List<Entry> norm = new ArrayList<>(s.blocks.size());
        for (Entry e : s.blocks) norm.add(new Entry(e.x - fx, e.y - fy, e.z - fz, e.state));
        norm.sort(Comparator.comparingInt(Entry::y).thenComparingInt(Entry::z).thenComparingInt(Entry::x));
        s.blocks.clear();
        s.blocks.addAll(norm);
        return s;
    }

    /** Vrstva po vrstvě odspodu, souvislé řady v ose X se slučují do /fill. */
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
