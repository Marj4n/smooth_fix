package org.marj4n.smooth_fix.performance;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
public final class DragonScanBytecode {
    private DragonScanBytecode() { }
    public static int apply(ClassNode target,String worldOwner,String entitiesMethod) {
        int count=0;
        for(MethodNode method:target.methods) {
            if(!method.name.equals("sample") || !method.desc.equals("(F)[F"))continue;
            boolean verified=false;
            for(AbstractInsnNode node:method.instructions.toArray())if(node instanceof TypeInsnNode type && type.getOpcode()==Opcodes.INSTANCEOF && type.desc.equals("com/leon/saintsdragons/server/entity/dragons/ignivorus/Ignivorus"))verified=true;
            if(!verified)continue;
            for(AbstractInsnNode node:method.instructions.toArray())if(node instanceof MethodInsnNode call && call.owner.equals(worldOwner) && call.name.equals(entitiesMethod) && call.desc.equals("()Ljava/lang/Iterable;")) {
                call.setOpcode(Opcodes.INVOKESTATIC);call.owner="org/marj4n/smooth_fix/performance/RelevantDragonEntities";
                call.name="entities";call.desc="(L"+worldOwner+";)Ljava/lang/Iterable;";call.itf=false;count++;
            }
        }
        return count;
    }
}
