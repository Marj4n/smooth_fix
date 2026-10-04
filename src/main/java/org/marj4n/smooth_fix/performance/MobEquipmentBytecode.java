package org.marj4n.smooth_fix.performance;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/** BMC 1.3.0 only changes OFFHAND; other slot queries need no weapon checks. */
public final class MobEquipmentBytecode {
    private static final String BMC = "me/Thelnfamous1/bettermobcombat/config/BMCServerConfigHelper";
    private static final String CALLBACK = "org/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable";
    private MobEquipmentBytecode() { }

    public static int apply(ClassNode target, String slotOwner) {
        int changed = 0;
        String descriptor = "(L" + slotOwner + ";L" + CALLBACK + ";)V";
        for (MethodNode method : target.methods) {
            if (!method.name.endsWith("pre_getItemBySlot") || !method.desc.equals(descriptor)
                    || (method.access & Opcodes.ACC_STATIC) != 0) continue;
            AbstractInsnNode first = method.instructions.getFirst();
            while (first != null && first.getOpcode() < 0) first = first.getNext();
            if (first instanceof VarInsnNode load && load.getOpcode() == Opcodes.ALOAD && load.var == 1
                    && nextCode(first) instanceof FieldInsnNode field && field.owner.equals(slotOwner)
                    && nextCode(field) instanceof JumpInsnNode branch && branch.getOpcode() == Opcodes.IF_ACMPEQ) continue;
            int blacklistCalls = 0, weaponCalls = 0, resultCalls = 0, comparisons = 0;
            boolean unexpectedCallback = false;
            FieldInsnNode offhand = null;
            for (AbstractInsnNode instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode call) {
                    if (call.owner.equals(BMC) && call.name.equals("isBlacklistedForBetterCombat")) blacklistCalls++;
                    if (call.owner.equals("net/bettercombat/logic/WeaponRegistry") && call.name.equals("getAttributes")) weaponCalls++;
                    if (call.owner.equals(CALLBACK) && call.name.equals("setReturnValue")) resultCalls++;
                    if (call.owner.startsWith("org/spongepowered/asm/mixin/injection/callback/")
                            && !call.name.equals("setReturnValue") && !call.name.equals("cancel")) unexpectedCallback = true;
                }
                if (instruction instanceof FieldInsnNode field && field.getOpcode() == Opcodes.GETSTATIC
                        && field.owner.equals(slotOwner) && field.desc.equals("L" + slotOwner + ";")) {
                    AbstractInsnNode before = previousCode(field), after = nextCode(field);
                    if (before instanceof VarInsnNode load && load.getOpcode() == Opcodes.ALOAD && load.var == 1
                            && after instanceof JumpInsnNode branch && branch.getOpcode() == Opcodes.IF_ACMPNE) {
                        offhand = field; comparisons++;
                    }
                }
            }
            // Changed upstream code or another injector retains the original handler.
            if (blacklistCalls != 1 || weaponCalls != 2 || resultCalls != 1
                    || comparisons != 1 || unexpectedCallback || offhand == null) continue;
            LabelNode original = new LabelNode();
            InsnList guard = new InsnList();
            guard.add(new VarInsnNode(Opcodes.ALOAD, 1));
            guard.add(new FieldInsnNode(Opcodes.GETSTATIC, offhand.owner, offhand.name, offhand.desc));
            guard.add(new JumpInsnNode(Opcodes.IF_ACMPEQ, original));
            guard.add(new InsnNode(Opcodes.RETURN));
            guard.add(original);
            guard.add(new FrameNode(Opcodes.F_NEW, 3, new Object[]{target.name, slotOwner, CALLBACK}, 0, null));
            method.instructions.insert(guard);
            method.maxStack = Math.max(method.maxStack, 2);
            changed++;
        }
        return changed;
    }

    private static AbstractInsnNode previousCode(AbstractInsnNode node) {
        do { node = node.getPrevious(); } while (node != null && node.getOpcode() < 0);
        return node;
    }
    private static AbstractInsnNode nextCode(AbstractInsnNode node) {
        do { node = node.getNext(); } while (node != null && node.getOpcode() < 0);
        return node;
    }
}
