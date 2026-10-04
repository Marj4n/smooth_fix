package org.marj4n.smooth_fix.performance;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/** Narrow guards for the tag-cache race and null tooltip observed in the supplied run. */
public final class EmiSearchSafetyBytecode {
    private EmiSearchSafetyBytecode() { }
    public static int apply(ClassNode target) {
        int changed = 0;
        if (target.name.equals("dev/emi/emi/runtime/EmiTagKey")) {
            // Every audited CACHE access is in these two static methods. Both use the same class monitor.
            for (MethodNode method : target.methods) {
                boolean cacheAccess = false;
                for (AbstractInsnNode node : method.instructions)
                    if (node instanceof FieldInsnNode field && field.owner.equals(target.name)
                            && field.name.equals("CACHE") && field.desc.equals("Ljava/util/Map;")) cacheAccess = true;
                if (cacheAccess && (method.name.equals("of") || method.name.equals("reload"))
                        && (method.access & Opcodes.ACC_STATIC) != 0 && (method.access & Opcodes.ACC_SYNCHRONIZED) == 0) {
                    method.access |= Opcodes.ACC_SYNCHRONIZED; changed++;
                }
            }
        } else if (target.name.equals("dev/emi/emi/search/TooltipQuery")) {
            for (MethodNode method : target.methods) {
                if (!method.name.equals("getText") || !method.desc.endsWith(")Ljava/util/List;")) continue;
                boolean alreadyPatched = false;
                for (AbstractInsnNode node : method.instructions)
                    if (node instanceof MethodInsnNode call && call.owner.equals("org/marj4n/smooth_fix/performance/EmiSearchSafetyBytecode")) alreadyPatched = true;
                if (alreadyPatched) continue;
                for (AbstractInsnNode node : method.instructions.toArray())
                    if (node instanceof MethodInsnNode call && call.owner.equals("dev/emi/emi/api/stack/EmiStack")
                            && call.name.equals("getTooltipText") && call.desc.equals("()Ljava/util/List;")) {
                        method.instructions.insert(call, new MethodInsnNode(Opcodes.INVOKESTATIC,
                                "org/marj4n/smooth_fix/performance/EmiSearchSafetyBytecode", "nonNullTooltip",
                                "(Ljava/util/List;)Ljava/util/List;", false));
                        changed++;
                    }
            }
        }
        return changed;
    }
    public static <T> java.util.List<T> nonNullTooltip(java.util.List<T> lines) {
        return lines == null ? java.util.List.of() : lines;
    }
}
