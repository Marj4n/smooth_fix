package verification;

import net.minecraft.nbt.*;
import org.marj4n.smooth_fix.benchmark.CanonicalNbtHash;

public class CanonicalNbtHashCheck {
    static void same(NbtElement a, NbtElement b) {
        if (!CanonicalNbtHash.sha256(a).equals(CanonicalNbtHash.sha256(b)))
            throw new AssertionError("Equivalent NBT differs");
    }

    static void different(NbtElement a, NbtElement b) {
        if (CanonicalNbtHash.sha256(a).equals(CanonicalNbtHash.sha256(b)))
            throw new AssertionError("Changed NBT hidden");
    }

    public static void main(String[] args) throws Exception {
        // These two keys deliberately collide under String.hashCode().
        var a = new NbtCompound();
        a.putInt("Aa", 1);
        a.putInt("BB", 2);
        var b = new NbtCompound();
        b.putInt("BB", 2);
        b.putInt("Aa", 1);
        if (!a.equals(b)) throw new AssertionError("Fixture not semantically equal");
        same(a, b);
        var nested = new NbtCompound();
        nested.put("tag", a);
        var nestedOther = new NbtCompound();
        nestedOther.put("tag", b);
        same(nested, nestedOther);
        b.putInt("BB", 3);
        different(a, b);
        different(NbtInt.of(1), NbtLong.of(1));
        different(new NbtByteArray(new byte[] {1, 2}), new NbtByteArray(new byte[] {2, 1}));
        var x = new NbtList();
        x.add(NbtInt.of(1));
        x.add(NbtInt.of(2));
        var y = new NbtList();
        y.add(NbtInt.of(2));
        y.add(NbtInt.of(1));
        different(x, y);
        same(NbtString.of("雪 😈"), NbtString.of("雪 😈"));
        same(NbtFloat.of(-0f), NbtFloat.of(0f));
        different(NbtFloat.of(1f), NbtFloat.of(2f));
        same(new NbtIntArray(new int[] {1, 2}), new NbtIntArray(new int[] {1, 2}));
        different(new NbtLongArray(new long[] {1}), new NbtLongArray(new long[] {2}));
        System.out.println(
                "PASS: compound order/hash collisions, nested NBT, changed item tags, numeric types, array/list ordering, Unicode, Minecraft zero normalization and changed floats. Does not establish current Minecraft inventory recovery PASS.");
    }
}
