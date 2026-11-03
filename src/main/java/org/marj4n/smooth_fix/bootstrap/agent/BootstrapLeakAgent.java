package org.marj4n.smooth_fix.bootstrap.agent;

import java.lang.instrument.*;
import java.security.*;
import java.util.HexFormat;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Explicit opt-in bootstrap prototype. No on-disk upstream JAR changes. */
public final class BootstrapLeakAgent {
    public static final String TARGET = "xyz/bluspring/kilt/loader/asm/OverridingMethodWrappers";
    public static final String AUDITED_SHA256 =
            "4db995bc01e4f76a29befb42a3a0e07f7f9ce4d33f76acb1faa70561638c6621";

    public static void premain(String args, Instrumentation instrumentation) {
        boolean enabled = Boolean.getBoolean("smoothfix.experimental.kiltBootstrapStreamClosure");
        System.out.println(
                "R3_BOOTSTRAP_STREAM_AGENT enabled="
                        + enabled
                        + " (experimental, not a release default)");
        if (!enabled) return;
        instrumentation.addTransformer(
                new ClassFileTransformer() {
                    public byte[] transform(
                            ClassLoader loader,
                            String name,
                            Class<?> type,
                            ProtectionDomain domain,
                            byte[] bytes) {
                        if (!TARGET.equals(name)) return null;
                        try {
                            return patch(bytes);
                        } catch (Throwable failure) {
                            System.err.println(
                                    "R3_BOOTSTRAP_STREAM_AGENT REJECTED "
                                            + failure.getClass().getSimpleName()
                                            + ": "
                                            + failure.getMessage());
                            return null; // Fail closed: unsupported upstream remains unchanged,
                                         // never silently claim acceptance.
                        }
                    }
                });
    }

    public static byte[] patch(byte[] bytes) throws Exception {
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        if (!hash.equals(AUDITED_SHA256))
            throw new IllegalArgumentException("Unaudited class hash " + hash);
        var node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        int changed = 0;
        for (var method : node.methods)
            if (method.name.equals("getClassInfo")
                    && method.desc.equals(
                            "(Ljava/nio/file/Path;)Lorg/objectweb/asm/tree/ClassNode;")) {
                for (var instruction : method.instructions)
                    if (instruction instanceof MethodInsnNode call
                            && call.getOpcode() == Opcodes.INVOKESTATIC
                            && call.owner.equals("java/nio/file/Files")
                            && call.name.equals("newInputStream")
                            && call.desc.equals(
                                    "(Ljava/nio/file/Path;[Ljava/nio/file/OpenOption;)Ljava/io/InputStream;")) {
                        call.owner = "org/marj4n/smooth_fix/bootstrap/agent/ConsumedClassStream";
                        call.name = "open";
                        changed++;
                    }
            }
        if (changed != 1)
            throw new IllegalStateException("Expected one audited opening, found " + changed);
        var writer = new ClassWriter(0);
        node.accept(writer);
        System.out.println("R3_BOOTSTRAP_STREAM_AGENT transformed=1 auditedSha256=" + hash);
        return writer.toByteArray();
    }
}
