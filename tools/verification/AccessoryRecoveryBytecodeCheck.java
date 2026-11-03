import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.marj4n.smooth_fix.performance.AccessoriesLegacyReadBytecode;

public class AccessoryRecoveryBytecodeCheck {
    public static void main(String[] a) throws Exception {
        ClassNode c = new ClassNode();
        try (var jar = new JarFile(a[0])) {
            new ClassReader(
                            jar.getInputStream(
                                    jar.getJarEntry(
                                            "io/wispforest/accessories/impl/AccessoriesContainerImpl.class")))
                    .accept(c, 0);
        }
        if (AccessoriesLegacyReadBytecode.apply(
                        c, "net/minecraft/class_1277", "net/minecraft/class_2499", "method_7659")
                != 1) throw new AssertionError("Installed beta48 expression not patched");
        if (AccessoriesLegacyReadBytecode.apply(
                        c, "net/minecraft/class_1277", "net/minecraft/class_2499", "method_7659")
                != 0) throw new AssertionError("Patch not idempotent");
        int rewritten = 0;
        for (var m : c.methods)
            for (var i : m.instructions)
                if (i instanceof MethodInsnNode call
                        && call.owner.equals(
                                "org/marj4n/smooth_fix/performance/LegacyAccessoryRecovery")) {
                    rewritten++;
                    if (!(i.getNext() instanceof VarInsnNode v)
                            || v.getOpcode() != Opcodes.ASTORE
                            || v.var != 8)
                        throw new AssertionError(
                                "Reconstructed inventory not stored in original local");
                }
        if (rewritten != 1) throw new AssertionError("Unexpected rewrite count");
        System.out.println(
                "PASS installed beta48 removed-slot expression patched once, original local8 reassigned, second application rejected");
    }
}
