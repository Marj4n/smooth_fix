package org.marj4n.smooth_fix.benchmark;

import net.minecraft.nbt.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

/** Semantic compound ordering, exact tag types and list ordering; no item data is discarded. */
public final class CanonicalNbtHash {
    private CanonicalNbtHash() {}

    public static String sha256(NbtElement value) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            try (var out =
                    new DataOutputStream(
                            new DigestOutputStream(OutputStream.nullOutputStream(), digest))) {
                write(value, out);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    private static void text(String value, DataOutputStream out) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    private static void write(NbtElement value, DataOutputStream out) throws IOException {
        out.writeByte(value.getType());
        switch (value.getType()) {
            case 0 -> {}
            case 1 -> out.writeByte(((AbstractNbtNumber) value).byteValue());
            case 2 -> out.writeShort(((AbstractNbtNumber) value).shortValue());
            case 3 -> out.writeInt(((AbstractNbtNumber) value).intValue());
            case 4 -> out.writeLong(((AbstractNbtNumber) value).longValue());
            case 5 ->
                    out.writeInt(Float.floatToRawIntBits(((AbstractNbtNumber) value).floatValue()));
            case 6 ->
                    out.writeLong(
                            Double.doubleToRawLongBits(((AbstractNbtNumber) value).doubleValue()));
            case 7 -> {
                byte[] a = ((NbtByteArray) value).getByteArray();
                out.writeInt(a.length);
                out.write(a);
            }
            case 8 -> text(value.asString(), out);
            case 9 -> {
                var a = (NbtList) value;
                out.writeByte(a.getHeldType());
                out.writeInt(a.size());
                for (var element : a) write(element, out);
            }
            case 10 -> {
                var a = (NbtCompound) value;
                var keys = new ArrayList<>(a.getKeys());
                Collections.sort(keys);
                out.writeInt(keys.size());
                for (String key : keys) {
                    text(key, out);
                    write(a.get(key), out);
                }
            }
            case 11 -> {
                int[] a = ((NbtIntArray) value).getIntArray();
                out.writeInt(a.length);
                for (int element : a) out.writeInt(element);
            }
            case 12 -> {
                long[] a = ((NbtLongArray) value).getLongArray();
                out.writeInt(a.length);
                for (long element : a) out.writeLong(element);
            }
            default -> throw new IOException("Unsupported NBT tag type: " + value.getType());
        }
    }
}
