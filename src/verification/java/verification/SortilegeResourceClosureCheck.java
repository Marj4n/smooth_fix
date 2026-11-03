package verification;

import org.marj4n.smooth_fix.mixin.common.SortilegeResourceClosureMixin;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.objectweb.asm.*;
import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.zip.*;

public class SortilegeResourceClosureCheck {
    static final class Subject extends SortilegeResourceClosureMixin {}

    static final class Input extends InputStream {
        final ByteArrayInputStream bytes;
        int closes;
        IOException readError, closeError;

        Input(byte[] bytes) {
            this.bytes = new ByteArrayInputStream(bytes);
        }

        public int read() throws IOException {
            if (readError != null) throw readError;
            return bytes.read();
        }

        public int read(byte[] b, int off, int n) throws IOException {
            if (readError != null) throw readError;
            return bytes.read(b, off, n);
        }

        public void close() throws IOException {
            closes++;
            if (closeError != null) throw closeError;
        }
    }

    @SuppressWarnings("unchecked")
    static <T, E extends Throwable> T rethrow(Throwable e) throws E {
        throw (E) e;
    }

    static void require(boolean b, String message) {
        if (!b) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        byte[] target;
        try (var jar = new ZipFile(args[0]);
                var in =
                        jar.getInputStream(
                                jar.getEntry("net/lyof/sortilege/setup/ReloadListener.class"))) {
            target = in.readAllBytes();
        }
        String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(target));
        require(
                sha.equals("132e522d67b7975c9df78fbfc47e9a4e6734d8382670de50f6ec0527ae3076cb"),
                "Unreviewed target bytecode");
        int[] sites = {0};
        new ClassReader(target)
                .accept(
                        new ClassVisitor(Opcodes.ASM9) {
                            public MethodVisitor visitMethod(
                                    int a, String n, String d, String s, String[] e) {
                                if (!n.equals("reload")) return null;
                                return new MethodVisitor(Opcodes.ASM9) {
                                    public void visitMethodInsn(
                                            int op,
                                            String owner,
                                            String name,
                                            String desc,
                                            boolean itf) {
                                        if (owner.equals("java/io/InputStream")
                                                && name.equals("readAllBytes")
                                                && desc.equals("()[B")) sites[0]++;
                                    }
                                };
                            }
                        },
                        ClassReader.SKIP_FRAMES);
        require(sites[0] == 1, "Exact consumption site count changed");
        Method method =
                SortilegeResourceClosureMixin.class.getDeclaredMethod(
                        "smoothfix$closeConsumedRecipe", InputStream.class, Operation.class);
        method.setAccessible(true);
        var subject = new Subject();
        int[] calls = {0};
        Operation<byte[]> original =
                values -> {
                    calls[0]++;
                    try {
                        return ((InputStream) values[0]).readAllBytes();
                    } catch (IOException e) {
                        return SortilegeResourceClosureCheck.<byte[], RuntimeException>rethrow(e);
                    }
                };
        byte[] expected =
                "{\"type\":\"sortilege:enchanting_catalyst\",\"text\":\"é›ª\"}"
                        .getBytes(StandardCharsets.UTF_8);
        var good = new Input(expected);
        byte[] actual = (byte[]) method.invoke(subject, good, original);
        require(
                Arrays.equals(expected, actual) && good.closes == 1 && calls[0] == 1,
                "Changed bytes, duplicate original call or missing close");
        var bad = new Input(expected);
        bad.readError = new IOException("original read failure");
        bad.closeError = new IOException("secondary close failure");
        try {
            method.invoke(subject, bad, original);
            throw new AssertionError("Read error swallowed");
        } catch (InvocationTargetException e) {
            require(e.getCause() == bad.readError, "Original failure replaced");
            require(
                    e.getCause().getSuppressed().length == 1
                            && e.getCause().getSuppressed()[0] == bad.closeError,
                    "Close failure not suppressed correctly");
        }
        require(bad.closes == 1, "Failed read leaked its owned stream");
        var counts = org.marj4n.smooth_fix.performance.SortilegeResourceCounters.snapshot();
        require(
                counts.get("successfulClosedReads") == 1L
                        && counts.get("consumedBytes") == expected.length
                        && counts.get("failedReadOrCloseAttempts") == 1L,
                "Operation diagnostic counters inaccurate");
        System.out.println(
                "PASS: exact installed target SHA/site count, original operation called once, identical UTF-8 bytes, close on success/failure, original exception and suppression preserved. Prototype only; no Minecraft mixin/runtime/performance acceptance.");
    }
}
