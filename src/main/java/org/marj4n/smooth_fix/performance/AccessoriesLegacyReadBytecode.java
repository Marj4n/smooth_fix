package org.marj4n.smooth_fix.performance;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/** Audited beta48 readContainers expression only; reject unknown bytecode instead of guessing. */
public final class AccessoriesLegacyReadBytecode {
    private AccessoriesLegacyReadBytecode() {}

    private static AbstractInsnNode prev(AbstractInsnNode n) {
        do {
            n = n.getPrevious();
        } while (n != null && n.getOpcode() < 0);
        return n;
    }

    public static int apply(ClassNode type, String inventory, String nbt, String readName) {
        MethodNode method = null;
        MethodInsnNode match = null;
        int local = -1, count = 0;
        for (var m : type.methods)
            if (m.name.equals("readContainers"))
                for (var n : m.instructions.toArray())
                    if (n instanceof MethodInsnNode call
                            && call.owner.equals(inventory)
                            && call.name.equals(readName)
                            && call.desc.equals("(L" + nbt + ";)V")) {
                        var cast = prev(call);
                        var get = cast == null ? null : prev(cast);
                        var key = get == null ? null : prev(get);
                        var carrier = key == null ? null : prev(key);
                        var receiver = carrier == null ? null : prev(carrier);
                        if (!(cast instanceof TypeInsnNode t)
                                || t.getOpcode() != Opcodes.CHECKCAST
                                || !t.desc.equals(nbt)
                                || !(get instanceof MethodInsnNode g)
                                || !g.owner.equals("io/wispforest/endec/util/MapCarrier")
                                || !g.name.equals("get")
                                || !(key instanceof VarInsnNode k)
                                || k.getOpcode() != Opcodes.ALOAD
                                || !(carrier instanceof VarInsnNode c)
                                || c.getOpcode() != Opcodes.ALOAD
                                || c.var != 0
                                || !(receiver instanceof VarInsnNode v)
                                || v.getOpcode() != Opcodes.ALOAD
                                || v.var <= 2) return 0;
                        method = m;
                        match = call;
                        local = v.var;
                        count++;
                    }
        if (count != 1) return 0;
        var replacement =
                new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        "org/marj4n/smooth_fix/performance/LegacyAccessoryRecovery",
                        "read",
                        "(L" + inventory + ";L" + nbt + ";)L" + inventory + ";",
                        false);
        method.instructions.set(match, replacement);
        method.instructions.insert(replacement, new VarInsnNode(Opcodes.ASTORE, local));
        return 1;
    }
}
