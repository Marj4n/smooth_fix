package org.marj4n.smooth_fix.performance;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/** Audited 0.8.1 method only. Unknown upstream shapes remain untouched. */
public final class ResourceBarTemperatureBytecode {
    private ResourceBarTemperatureBytecode() { }

    public static int apply(ClassNode target, String playerOwner) {
        if (!target.name.equals("dev/muon/dynamic_resource_bars/render/HealthBarRenderer")) return 0;
        for (MethodNode method : target.methods) {
            if (!method.name.equals("getTemperatureScale")
                    || !method.desc.equals("(L" + playerOwner + ";)F")
                    || (method.access & Opcodes.ACC_STATIC) == 0) continue;
            int lookups = 0;
            boolean expectedName = false;
            boolean expectedInvoke = false;
            boolean expectedFallback = false;
            for (AbstractInsnNode node : method.instructions) {
                if (node instanceof MethodInsnNode call) {
                    if (call.owner.equals("org/marj4n/smooth_fix/performance/ResourceBarTemperature")) return 0;
                    if (call.owner.equals("java/lang/Class") && call.name.equals("getMethod")
                            && call.desc.equals("(Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;")) lookups++;
                    if (call.owner.equals("java/lang/reflect/Method") && call.name.equals("invoke")
                            && call.desc.equals("(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;")) expectedInvoke = true;
                }
                if (node instanceof LdcInsnNode constant
                        && "thermoo$getTemperatureScale".equals(constant.cst)) expectedName = true;
                if (node.getOpcode() == Opcodes.FCONST_0) expectedFallback = true;
            }
            boolean catchesException = method.tryCatchBlocks.stream()
                    .anyMatch(block -> "java/lang/Exception".equals(block.type));
            if (lookups != 1 || !expectedName || !expectedInvoke || !expectedFallback || !catchesException) return 0;
            method.instructions.clear();
            method.tryCatchBlocks.clear();
            if (method.localVariables != null) method.localVariables.clear();
            if (method.visibleLocalVariableAnnotations != null) method.visibleLocalVariableAnnotations.clear();
            if (method.invisibleLocalVariableAnnotations != null) method.invisibleLocalVariableAnnotations.clear();
            method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
            method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                    "org/marj4n/smooth_fix/performance/ResourceBarTemperature", "scale", "(Ljava/lang/Object;)F", false));
            method.instructions.add(new InsnNode(Opcodes.FRETURN));
            method.maxStack = 1;
            method.maxLocals = 1;
            return 1;
        }
        return 0;
    }
}
