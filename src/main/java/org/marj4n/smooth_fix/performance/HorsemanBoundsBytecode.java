package org.marj4n.smooth_fix.performance;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import java.util.*;
/** Installed Horseman1.3.17: configuration is irrelevant for every non-horse receiver. */
public final class HorsemanBoundsBytecode {
 public static int apply(ClassNode target,String horse,String boat,String player,String box){
  int changed=0;String cfg="net/minecraftforge/common/ForgeConfigSpec$BooleanValue";
  for(MethodNode m:target.methods){
   if(!m.name.endsWith("horseman$onGetBoundingBox")||!m.desc.equals("(L"+box+";)L"+box+";")||(m.access&Opcodes.ACC_STATIC)!=0)continue;
   var first=code(m.instructions.getFirst());
   if(!(first instanceof FieldInsnNode f)||f.getOpcode()!=Opcodes.GETSTATIC||!f.owner.equals("io/github/mortuusars/horseman/Config$Common")||!f.name.equals("HORSE_IN_BOAT")||!f.desc.equals("L"+cfg+";"))continue;
   var a=next(first);if(!(a instanceof MethodInsnNode get)||!get.owner.equals(cfg)||!get.name.equals("get")||!get.desc.equals("()Ljava/lang/Object;"))continue;
   a=next(a);if(!(a instanceof TypeInsnNode cast)||cast.getOpcode()!=Opcodes.CHECKCAST||!cast.desc.equals("java/lang/Boolean"))continue;
   a=next(a);if(!(a instanceof MethodInsnNode bool)||!bool.owner.equals("java/lang/Boolean")||!bool.name.equals("booleanValue"))continue;
   a=next(a);if(!(a instanceof JumpInsnNode branch)||branch.getOpcode()!=Opcodes.IFNE)continue;
   a=next(a);if(!(a instanceof VarInsnNode arg)||arg.getOpcode()!=Opcodes.ALOAD||arg.var!=1||next(a)==null||next(a).getOpcode()!=Opcodes.ARETURN)continue;
   Map<String,Integer> calls=new HashMap<>();int horses=0,boats=0,players=0,fields=0,returns=0;boolean altered=false;
   for(AbstractInsnNode insn:m.instructions){
    int op=insn.getOpcode();if(op==Opcodes.PUTFIELD||op==Opcodes.PUTSTATIC||op==Opcodes.INVOKEDYNAMIC||op==Opcodes.MONITORENTER||op==Opcodes.MONITOREXIT)altered=true;
    if(insn instanceof FieldInsnNode)fields++;
    if(op==Opcodes.ARETURN)returns++;
    if(insn instanceof TypeInsnNode t&&op==Opcodes.INSTANCEOF){if(t.desc.equals(horse))horses++;else if(t.desc.equals(boat))boats++;else if(t.desc.equals(player))players++;else altered=true;}
    if(insn instanceof MethodInsnNode call)calls.merge(call.owner,1,Integer::sum);
   }
   Map<String,Integer> expected=new HashMap<>();expected.put(cfg,1);expected.put("java/lang/Boolean",1);expected.put(horse,1);expected.put(boat,1);expected.put(box,2);
   if(altered||fields!=1||returns!=4||horses!=1||boats!=1||players!=1||!calls.equals(expected))continue;
   LabelNode original=new LabelNode();InsnList guard=new InsnList();guard.add(new VarInsnNode(Opcodes.ALOAD,0));guard.add(new TypeInsnNode(Opcodes.INSTANCEOF,horse));guard.add(new JumpInsnNode(Opcodes.IFNE,original));guard.add(new VarInsnNode(Opcodes.ALOAD,1));guard.add(new InsnNode(Opcodes.ARETURN));guard.add(original);guard.add(new FrameNode(Opcodes.F_NEW,2,new Object[]{target.name,box},0,null));
   m.instructions.insert(guard);m.maxStack=Math.max(m.maxStack,2);changed++;
  }return changed;
 }
 private static AbstractInsnNode code(AbstractInsnNode n){while(n!=null&&n.getOpcode()<0)n=n.getNext();return n;}
 private static AbstractInsnNode next(AbstractInsnNode n){return code(n.getNext());}
 private HorsemanBoundsBytecode(){}
}
