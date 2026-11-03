package verification;

import java.nio.file.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.marj4n.smooth_fix.performance.KiltRegistryCacheBytecode;

/** Real installed class, not a mirrored synthetic implementation. */
public final class KiltRegistryCacheBytecodeCheck {
    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    private static ClassNode read(byte[] raw) {
        var c = new ClassNode();
        new ClassReader(raw).accept(c, 0);
        return c;
    }

    public static void main(String[] args) throws Exception {
        byte[] raw = Files.readAllBytes(Path.of(args[0]));
        var c = read(raw);
        var before = new java.util.HashMap<String, Integer>();
        for (var m : c.methods)
            before.put(m.name + m.desc, KiltRegistryCacheBytecode.fingerprint(m));
        require(
                KiltRegistryCacheBytecode.apply(c, "net/minecraft/class_2680", "method_26200"),
                "Audited bake rejected");
        int invalidations = 0, deferred = 0;
        for (var m : c.methods) {
            if (!m.name.equals("onBake"))
                require(
                        before.get(m.name + m.desc) == KiltRegistryCacheBytecode.fingerprint(m),
                        "Unrelated registry callback changed");
            for (var n : m.instructions)
                if (n instanceof MethodInsnNode call) {
                    if (call.owner.equals(
                                    "org/embeddedt/modernfix/blockstate/BlockStateCacheHandler")
                            && call.name.equals("rebuildParallel")) invalidations++;
                    if (call.owner.equals(
                                    "org/marj4n/smooth_fix/performance/KiltRegistryCacheCounters")
                            && call.name.equals("deferred")) deferred++;
                    require(
                            !call.owner.equals("net/minecraft/class_2680")
                                    || !call.name.equals("method_26200"),
                            "Eager cache retained");
                }
        }
        require(invalidations == 1 && deferred == 1, "Wrong adaptation sites");
        require(
                !KiltRegistryCacheBytecode.apply(c, "net/minecraft/class_2680", "method_26200"),
                "Double application accepted");
        var changed = read(raw);
        for (var m : changed.methods)
            if (m.name.equals("onBake")) m.instructions.insert(new InsnNode(Opcodes.NOP));
        var writer = new ClassWriter(0);
        changed.accept(writer);
        byte[] snapshot = writer.toByteArray();
        require(
                !KiltRegistryCacheBytecode.apply(
                        changed, "net/minecraft/class_2680", "method_26200"),
                "Changed method accepted");
        writer = new ClassWriter(0);
        changed.accept(writer);
        require(java.util.Arrays.equals(snapshot, writer.toByteArray()), "Rejected class mutated");
        var wrong = read(raw);
        require(
                !KiltRegistryCacheBytecode.apply(wrong, "net/minecraft/class_2680", "wrong"),
                "Unexpected cache method accepted");
        writer = new ClassWriter(0);
        c.accept(writer);
        new ClassReader(writer.toByteArray());
        System.out.println(
                "PASS: actual audited callback adapted once; registry IDs/debug state and all other callbacks retained; changed/unknown/already-adapted method refused without mutation. Actual transformed runtime and save/rejoin remain pending.");
    }
}
