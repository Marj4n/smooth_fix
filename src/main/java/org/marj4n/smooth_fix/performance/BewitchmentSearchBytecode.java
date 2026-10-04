package org.marj4n.smooth_fix.performance;

import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/** Opt-in replacement of the two verified sigil predicates after upstream mixins have merged. */
public final class BewitchmentSearchBytecode {
    private static final String STATE="moriyashiine/bewitchment/common/world/BWWorldState";
    private static final String UTIL="moriyashiine/bewitchment/common/misc/BWUtil";
    private BewitchmentSearchBytecode() { }
    public static int apply(ClassNode target) {
        int changed=0;
        for(MethodNode method:target.methods) {
            for(AbstractInsnNode instruction:method.instructions.toArray()) {
                if(!(instruction instanceof MethodInsnNode call) || call.getOpcode()!=Opcodes.INVOKESTATIC
                        || !call.owner.equals(UTIL) || !call.name.equals("getClosestBlockPos")) continue;
                AbstractInsnNode previous=previousCode(call);
                if(!(previous instanceof InvokeDynamicInsnNode lambda))continue;
                boolean verified=false;
                for(Object arg:lambda.bsmArgs) if(arg instanceof Handle h) {
                    MethodNode predicate=target.methods.stream().filter(m->m.name.equals(h.getName())&&m.desc.equals(h.getDesc())).findFirst().orElse(null);
                    if(predicate!=null) for(AbstractInsnNode insn:predicate.instructions.toArray())
                        if(insn instanceof FieldInsnNode field && field.owner.equals(STATE) && field.name.equals("potentialSigils")
                                && field.desc.equals("Ljava/util/List;"))verified=true;
                }
                if(!verified)continue;
                int stateLocal=-1;
                for(AbstractInsnNode cursor=lambda.getPrevious();cursor!=null;cursor=cursor.getPrevious()) {
                    if(cursor instanceof MethodInsnNode get && get.owner.equals(STATE)&&get.name.equals("get")) {
                        AbstractInsnNode store=nextCode(cursor);
                        if(store instanceof VarInsnNode var && var.getOpcode()==Opcodes.ASTORE)stateLocal=var.var;
                        break;
                    }
                }
                if(stateLocal<0)continue;
                InsnList extra=new InsnList();extra.add(new VarInsnNode(Opcodes.ALOAD,stateLocal));
                extra.add(new FieldInsnNode(Opcodes.GETFIELD,STATE,"potentialSigils","Ljava/util/List;"));
                method.instructions.insertBefore(call,extra);
                call.owner="org/marj4n/smooth_fix/performance/SigilSearch";
                call.name="find";call.desc=call.desc.replace("Ljava/util/function/Predicate;)","Ljava/util/function/Predicate;Ljava/util/List;)");
                method.maxStack++;changed++;
            }
        }
        return changed;
    }
    private static AbstractInsnNode previousCode(AbstractInsnNode n){do{n=n.getPrevious();}while(n!=null&&n.getOpcode()<0);return n;}
    private static AbstractInsnNode nextCode(AbstractInsnNode n){do{n=n.getNext();}while(n!=null&&n.getOpcode()<0);return n;}
}
