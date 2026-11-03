package org.marj4n.smooth_fix.performance;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import java.util.*;

/** Preserve Kilt's default-valued context after each successful call without allocating new ThreadLocal entries. */
public final class KiltRenderContextBytecode {
    private KiltRenderContextBytecode() {}

    public static boolean apply(ClassNode target) {
        for (var m : target.methods) {
            if (!m.name.equals("getQuads")
                    || !m.desc.endsWith(")Ljava/util/List;")
                    || !m.tryCatchBlocks.isEmpty()) continue;
            var args = Type.getArgumentTypes(m.desc);
            if (args.length != 5
                    || !args[3].getDescriptor()
                            .equals("Lnet/minecraftforge/client/model/data/ModelData;")) continue;
            List<MethodInsnNode> clears = new ArrayList<>();
            Set<String> fields = new HashSet<>();
            int writes = 0;
            for (var n : m.instructions)
                if (n instanceof MethodInsnNode call
                        && call.owner.equals("java/lang/ThreadLocal")) {
                    if (call.name.equals("set") && call.desc.equals("(Ljava/lang/Object;)V"))
                        writes++;
                    if (call.name.equals("remove") && call.desc.equals("()V")) {
                        var previous = call.getPrevious();
                        while (previous != null && previous.getOpcode() < 0)
                            previous = previous.getPrevious();
                        if (!(previous instanceof FieldInsnNode f)
                                || f.getOpcode() != Opcodes.GETSTATIC
                                || !f.owner.equals(target.name)
                                || !f.desc.equals("Ljava/lang/ThreadLocal;")
                                || !Set.of("kilt$modelData", "kilt$renderType").contains(f.name))
                            return false;
                        fields.add(f.name);
                        clears.add(call);
                    }
                }
            if (writes != 2 || clears.size() != 2 || fields.size() != 2) return false;
            for (var call : clears) {
                var n = call.getPrevious();
                while (n.getOpcode() < 0) n = n.getPrevious();
                var field = (FieldInsnNode) n;
                if (field.name.equals("kilt$modelData"))
                    m.instructions.insertBefore(
                            call,
                            new FieldInsnNode(
                                    Opcodes.GETSTATIC,
                                    "net/minecraftforge/client/model/data/ModelData",
                                    "EMPTY",
                                    "Lnet/minecraftforge/client/model/data/ModelData;"));
                else m.instructions.insertBefore(call, new InsnNode(Opcodes.ACONST_NULL));
                m.instructions.set(
                        call,
                        new MethodInsnNode(
                                Opcodes.INVOKEVIRTUAL,
                                "java/lang/ThreadLocal",
                                "set",
                                "(Ljava/lang/Object;)V",
                                false));
            }
            m.maxStack = Math.max(m.maxStack, 2);
            return true;
        }
        return false;
    }
}
