package org.marj4n.smooth_fix.performance;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import java.util.ArrayList;
import java.util.List;

/** Replace only the verified entrySet stream/map/unmodifiable-set expression. */
public final class TrinketSnapshotBytecode {
    private TrinketSnapshotBytecode() { }

    public static int apply(ClassNode target) {
        if (!target.name.equals("io/wispforest/tclayer/ImmutableDelegatingMap")) return 0;
        for (MethodNode method : target.methods) {
            if (!method.name.equals("entrySet") || !method.desc.equals("()Ljava/util/Set;")) continue;
            List<AbstractInsnNode> code = new ArrayList<>();
            for (AbstractInsnNode instruction : method.instructions) if (instruction.getOpcode() >= 0) code.add(instruction);
            if (code.size() != 11
                    || !(code.get(0) instanceof VarInsnNode first) || first.getOpcode() != Opcodes.ALOAD || first.var != 0
                    || !(code.get(1) instanceof FieldInsnNode field) || field.getOpcode() != Opcodes.GETFIELD
                    || !field.owner.equals(target.name) || !field.name.equals("map") || !field.desc.equals("Ljava/util/Map;")
                    || !call(code.get(2), "java/util/Map", "entrySet", "()Ljava/util/Set;")
                    || !call(code.get(3), "java/util/Set", "stream", "()Ljava/util/stream/Stream;")
                    || !(code.get(4) instanceof VarInsnNode self) || self.getOpcode() != Opcodes.ALOAD || self.var != 0
                    || !(code.get(5) instanceof InvokeDynamicInsnNode mapper)
                    || !mapper.desc.equals("(L" + target.name + ";)Ljava/util/function/Function;")
                    || !call(code.get(6), "java/util/stream/Stream", "map", "(Ljava/util/function/Function;)Ljava/util/stream/Stream;")
                    || !call(code.get(7), "java/util/stream/Collectors", "toUnmodifiableSet", "()Ljava/util/stream/Collector;")
                    || !call(code.get(8), "java/util/stream/Stream", "collect", "(Ljava/util/stream/Collector;)Ljava/lang/Object;")
                    || !(code.get(9) instanceof TypeInsnNode cast) || cast.getOpcode() != Opcodes.CHECKCAST
                    || !cast.desc.equals("java/util/Set") || code.get(10).getOpcode() != Opcodes.ARETURN) continue;
            method.instructions.remove(code.get(3));
            method.instructions.set(code.get(6), new MethodInsnNode(Opcodes.INVOKESTATIC,
                    "org/marj4n/smooth_fix/performance/MappedSetSnapshots", "collect",
                    "(Ljava/util/Set;Ljava/util/function/Function;)Ljava/util/Set;", false));
            method.instructions.remove(code.get(7));
            method.instructions.remove(code.get(8));
            method.instructions.remove(code.get(9));
            return 1;
        }
        return 0;
    }

    private static boolean call(AbstractInsnNode node, String owner, String name, String descriptor) {
        return node instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name) && call.desc.equals(descriptor);
    }
}
