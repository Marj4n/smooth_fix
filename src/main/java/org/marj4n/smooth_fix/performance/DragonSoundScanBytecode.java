package org.marj4n.smooth_fix.performance;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import java.util.Map;

/** Replace the six 0.9.85 full-world sound scans with event-maintained typed sets. */
public final class DragonSoundScanBytecode {
    private static final String BASE = "com/leon/saintsdragons/";
    private static final Map<String, String> TARGETS = Map.of(
            BASE + "client/sound/cindervane/CindervaneFireBodySoundController", BASE + "server/entity/dragons/cindervane/Cindervane",
            BASE + "client/sound/ignivorus/IgnivorusFireBreathSoundController", BASE + "server/entity/dragons/ignivorus/Ignivorus",
            BASE + "client/sound/raevyx/RaevyxDiveSoundController", BASE + "server/entity/dragons/raevyx/Raevyx",
            BASE + "client/sound/raevyx/RaevyxLightningBeamSoundController", BASE + "server/entity/dragons/raevyx/Raevyx",
            BASE + "client/sound/volitans/VolitansBreathSoundController", BASE + "server/entity/dragons/volitans/Volitans",
            BASE + "client/sound/volitans/VolitansBurrowSoundController", BASE + "server/entity/dragons/volitans/Volitans");

    private DragonSoundScanBytecode() { }

    public static int apply(ClassNode target, String clientOwner, String worldOwner, String entitiesMethod) {
        String dragonType = TARGETS.get(target.name);
        if (dragonType == null) return 0;
        for (MethodNode method : target.methods) {
            if (!method.name.equals("tick") || !method.desc.equals("(L" + clientOwner + ";)V")) continue;
            boolean checkedType = false;
            MethodInsnNode scan = null;
            for (AbstractInsnNode node : method.instructions) {
                if (node instanceof TypeInsnNode type && type.getOpcode() == Opcodes.INSTANCEOF) {
                    if (type.desc.equals(dragonType)) checkedType = true;
                    else return 0;
                }
                if (node instanceof MethodInsnNode call) {
                    if (call.owner.equals("org/marj4n/smooth_fix/performance/RelevantDragonEntities")) return 0;
                    if (call.owner.equals(worldOwner) && call.name.equals(entitiesMethod)
                            && call.desc.equals("()Ljava/lang/Iterable;") && call.getOpcode() == Opcodes.INVOKEVIRTUAL) {
                        if (scan != null) return 0;
                        scan = call;
                    }
                }
            }
            if (!checkedType || scan == null) return 0;
            method.instructions.insertBefore(scan, new LdcInsnNode(dragonType.replace('/', '.')));
            method.instructions.set(scan, new MethodInsnNode(Opcodes.INVOKESTATIC,
                    "org/marj4n/smooth_fix/performance/RelevantDragonEntities", "entities",
                    "(L" + worldOwner + ";Ljava/lang/String;)Ljava/lang/Iterable;", false));
            method.maxStack++;
            return 1;
        }
        return 0;
    }
}
