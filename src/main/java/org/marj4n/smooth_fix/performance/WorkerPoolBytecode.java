package org.marj4n.smooth_fix.performance;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/** Replace only straight-line, non-escaping four-argument pool construction in merged Util. */
public final class WorkerPoolBytecode {
    private static final String POOL = "java/util/concurrent/ForkJoinPool";
    private static final String ARGS = "(ILjava/util/concurrent/ForkJoinPool$ForkJoinWorkerThreadFactory;Ljava/lang/Thread$UncaughtExceptionHandler;Z)";
    private WorkerPoolBytecode() { }

    public static int apply(ClassNode target) {
        int changed = 0;
        for (MethodNode method : target.methods) {
            for (AbstractInsnNode node = method.instructions.getFirst(); node != null; node = node.getNext()) {
                if (!(node instanceof MethodInsnNode constructor) || node.getOpcode() != Opcodes.INVOKESPECIAL
                        || !constructor.owner.equals(POOL) || !constructor.name.equals("<init>")
                        || !constructor.desc.equals(ARGS + "V")) continue;
                AbstractInsnNode cursor = node.getPrevious();
                while (cursor != null) {
                    if (cursor instanceof FrameNode || cursor instanceof JumpInsnNode || cursor instanceof TableSwitchInsnNode
                            || cursor instanceof LookupSwitchInsnNode || cursor instanceof VarInsnNode variable && variable.getOpcode()==Opcodes.ASTORE) break;
                    if (cursor instanceof TypeInsnNode allocation && allocation.getOpcode()==Opcodes.NEW && allocation.desc.equals(POOL)) {
                        AbstractInsnNode duplicate=allocation.getNext();
                        if (duplicate != null && duplicate.getOpcode()==Opcodes.DUP) {
                            method.instructions.remove(allocation);
                            method.instructions.remove(duplicate);
                            MethodInsnNode factory=new MethodInsnNode(Opcodes.INVOKESTATIC,
                                    "org/marj4n/smooth_fix/performance/WorkerPoolBudget", "create",ARGS+"L"+POOL+";",false);
                            method.instructions.set(node,factory);
                            node=factory;
                            changed++;
                        }
                        break;
                    }
                    cursor=cursor.getPrevious();
                }
            }
        }
        return changed;
    }
}
