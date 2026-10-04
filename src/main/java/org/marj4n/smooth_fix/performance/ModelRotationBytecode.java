package org.marj4n.smooth_fix.performance;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/** Fail closed when another mod has changed the audited vanilla expression. No whole-method overwrite. */
public final class ModelRotationBytecode {
    private ModelRotationBytecode() { }

    public static int apply(ClassNode target, String matrixOwner, String multiplyName) {
        int changed = 0;
        for (MethodNode method : target.methods) {
            if (!method.desc.equals("(L" + matrixOwner + ";)V")) continue;
            for (AbstractInsnNode node = method.instructions.getFirst(); node != null; ) {
                AbstractInsnNode next = node.getNext();
                if (node instanceof TypeInsnNode allocation && allocation.getOpcode() == Opcodes.NEW
                        && allocation.desc.equals("org/joml/Quaternionf")) {
                    AbstractInsnNode[] sequence = new AbstractInsnNode[11];
                    sequence[0] = node;
                    for (int i = 1; i < sequence.length && sequence[i - 1] != null; i++) sequence[i] = sequence[i - 1].getNext();
                    if (matches(sequence, matrixOwner, multiplyName)) {
                        method.instructions.remove(sequence[0]);
                        method.instructions.remove(sequence[1]);
                        method.instructions.remove(sequence[2]);
                        method.instructions.set(sequence[9], new MethodInsnNode(Opcodes.INVOKESTATIC,
                                "org/marj4n/smooth_fix/performance/DirectModelRotation", "rotate",
                                "(L" + matrixOwner + ";FFF)V", false));
                        next = sequence[10].getNext();
                        method.instructions.remove(sequence[10]);
                        changed++;
                    }
                }
                node = next;
            }
        }
        return changed;
    }

    private static boolean matches(AbstractInsnNode[] s, String matrixOwner, String multiplyName) {
        if (s[10] == null || s[1].getOpcode() != Opcodes.DUP) return false;
        if (!(s[2] instanceof MethodInsnNode init) || init.getOpcode() != Opcodes.INVOKESPECIAL
                || !init.owner.equals("org/joml/Quaternionf") || !init.name.equals("<init>") || !init.desc.equals("()V")) return false;
        for (int i = 3; i <= 7; i += 2) {
            if (!(s[i] instanceof VarInsnNode load) || load.getOpcode() != Opcodes.ALOAD || load.var != 0
                    || !(s[i + 1] instanceof FieldInsnNode field) || field.getOpcode() != Opcodes.GETFIELD
                    || !field.desc.equals("F")) return false;
        }
        return s[9] instanceof MethodInsnNode rotation && rotation.getOpcode() == Opcodes.INVOKEVIRTUAL
                && rotation.owner.equals("org/joml/Quaternionf") && rotation.name.equals("rotationZYX")
                && rotation.desc.equals("(FFF)Lorg/joml/Quaternionf;")
                && s[10] instanceof MethodInsnNode multiply && multiply.getOpcode() == Opcodes.INVOKEVIRTUAL
                && multiply.owner.equals(matrixOwner) && multiply.name.equals(multiplyName)
                && multiply.desc.equals("(Lorg/joml/Quaternionf;)V");
    }
}
