package org.marj4n.smooth_fix.performance;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/** Substitute only the audited serialization call inside getModelId; the original serializer remains the oracle. */
public final class ModelVariantBytecode {
    private ModelVariantBytecode() {}

    public static boolean apply(ClassNode target, String modelId, String serializer) {
        MethodNode serial = null, model = null;
        for (var m : target.methods) {
            if (m.name.equals(serializer) && m.desc.equals("(Ljava/util/Map;)Ljava/lang/String;"))
                serial = m;
            if (m.name.equals(modelId) && m.desc.endsWith(";)Lnet/minecraft/class_1091;"))
                model = m;
        }
        // Descriptor naming differs in development; locate by mapped method and argument count
        // instead.
        if (model == null)
            for (var m : target.methods)
                if (m.name.equals(modelId)
                        && org.objectweb.asm.Type.getArgumentTypes(m.desc).length == 2) model = m;
        if (serial == null || model == null || fingerprint(serial) != 417744322) return false;
        MethodInsnNode found = null;
        for (var n : model.instructions)
            if (n instanceof MethodInsnNode call
                    && call.owner.equals(target.name)
                    && call.name.equals(serializer)
                    && call.desc.equals("(Ljava/util/Map;)Ljava/lang/String;")) {
                if (found != null) return false;
                found = call;
            }
        if (found == null) return false;
        model.instructions.set(
                found,
                new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        "org/marj4n/smooth_fix/performance/ModelVariantCache",
                        "variant",
                        "(Ljava/util/Map;)Ljava/lang/String;",
                        false));
        return true;
    }

    private static int fingerprint(MethodNode m) {
        var b = new StringBuilder();
        for (var i : m.instructions)
            if (i.getOpcode() >= 0) {
                b.append(i.getOpcode()).append(',');
                if (i instanceof VarInsnNode v) b.append(v.var);
                if (i instanceof IntInsnNode v) b.append(v.operand);
                if (i instanceof IincInsnNode v) b.append(v.var).append(':').append(v.incr);
                if (i instanceof FieldInsnNode v) b.append(v.owner).append(v.name).append(v.desc);
                if (i instanceof TypeInsnNode v)
                    b.append(v.desc.startsWith("net/minecraft/") ? "minecraft-type" : v.desc);
                if (i instanceof MultiANewArrayInsnNode v) b.append(v.desc).append(v.dims);
                if (i instanceof LdcInsnNode v) b.append(v.cst);
                if (i instanceof MethodInsnNode v)
                    b.append(
                            v.owner.startsWith("net/minecraft/")
                                    ? "minecraft-method"
                                    : v.owner + v.name + v.desc);
                b.append(';');
            }
        return b.toString().hashCode();
    }
}
