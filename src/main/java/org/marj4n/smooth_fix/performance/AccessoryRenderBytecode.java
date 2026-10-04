package org.marj4n.smooth_fix.performance;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/** Replace only beta.48's initial pre-slot flush; retain all later effect/UI flushes. */
public final class AccessoryRenderBytecode {
    private static final String CAPABILITY = "io/wispforest/accessories/api/AccessoriesCapability";
    private AccessoryRenderBytecode() { }
    public static int apply(ClassNode target, String immediateOwner, String drawName) {
        if (!target.name.equals("io/wispforest/accessories/client/AccessoriesRenderLayer")) return 0;
        for (MethodNode method : target.methods) {
            if (!method.name.equals("render") || !method.desc.endsWith("FFFFFF)V")) continue;
            int capabilityLocal = -1;
            MethodInsnNode initialFlush = null;
            boolean slotsSeen = false;
            for (AbstractInsnNode node : method.instructions) {
                if (!(node instanceof MethodInsnNode call)) continue;
                if (call.owner.equals("org/marj4n/smooth_fix/performance/AccessoryRenderWork")) return 0;
                if (call.owner.equals(CAPABILITY) && call.name.equals("get")
                        && call.getOpcode() == Opcodes.INVOKESTATIC && call.desc.endsWith("L" + CAPABILITY + ";")) {
                    AbstractInsnNode next = call.getNext();
                    while (next != null && next.getOpcode() < 0) next = next.getNext();
                    if (next instanceof VarInsnNode store && store.getOpcode() == Opcodes.ASTORE) capabilityLocal = store.var;
                }
                if (call.owner.equals(CAPABILITY) && call.name.equals("getContainers") && call.desc.equals("()Ljava/util/Map;")) slotsSeen = true;
                if (call.owner.equals(immediateOwner) && call.name.equals(drawName) && call.desc.equals("()V")
                        && call.getOpcode() == Opcodes.INVOKEVIRTUAL && !slotsSeen) {
                    if (initialFlush != null) return 0;
                    initialFlush = call;
                }
            }
            if (initialFlush == null || capabilityLocal < 0 || !slotsSeen) continue;
            method.instructions.insertBefore(initialFlush, new VarInsnNode(Opcodes.ALOAD, capabilityLocal));
            method.instructions.set(initialFlush, new MethodInsnNode(Opcodes.INVOKESTATIC,
                    "org/marj4n/smooth_fix/performance/AccessoryRenderWork", "drawIfEquipped",
                    "(L" + immediateOwner + ";Ljava/lang/Object;)V", false));
            method.maxStack = Math.max(method.maxStack, 2);
            return 1;
        }
        return 0;
    }
}
