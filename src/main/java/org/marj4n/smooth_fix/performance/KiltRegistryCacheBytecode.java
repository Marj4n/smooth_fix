package org.marj4n.smooth_fix.performance;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
/** Match the audited bake shape before mutation; retain registry ID and debug-state work. */
public final class KiltRegistryCacheBytecode {
 private KiltRegistryCacheBytecode() { }
 public static final int EXPECTED_FINGERPRINT=1725649481;
 public static int fingerprint(MethodNode method){
  var s=new StringBuilder();
  for(var n:method.instructions)if(n.getOpcode()>=0){
   s.append(n.getOpcode()).append(':');
   if(n instanceof VarInsnNode v)s.append(v.var);
   if(n instanceof IntInsnNode v)s.append(v.operand);
   if(n instanceof MethodInsnNode v){boolean mc=v.owner.startsWith("net/minecraft/");s.append(mc?"MC":v.owner).append(mc?"M":v.name).append(v.desc.replaceAll("Lnet/minecraft/[^;]+;","LMC;"));}
   if(n instanceof FieldInsnNode v){boolean mc=v.owner.startsWith("net/minecraft/");s.append(mc?"MC":v.owner).append(mc?"F":v.name).append(v.desc.replaceAll("Lnet/minecraft/[^;]+;","LMC;"));}
   if(n instanceof TypeInsnNode v)s.append(v.desc.startsWith("net/minecraft/")?"MC":v.desc);
   if(n instanceof LdcInsnNode v)s.append(v.cst);
   if(n instanceof JumpInsnNode v){int position=0;for(var other:method.instructions){if(other==v.label)break;if(other.getOpcode()>=0)position++;}s.append(position);}
   s.append(';');
  }
  return s.toString().hashCode();
 }
 public static boolean apply(ClassNode target,String blockState,String initCache) {
  MethodNode bake=null;
  for(var m:target.methods)if(m.name.equals("onBake")&&m.desc.equals("(Lnet/minecraftforge/registries/IForgeRegistryInternal;Lnet/minecraftforge/registries/RegistryManager;)V")){if(bake!=null)return false;bake=m;}
  if(bake==null)return false;
  MethodInsnNode init=null;AbstractInsnNode exit=null;int calls=0,returns=0;
  for(var n:bake.instructions){
   if(n instanceof MethodInsnNode call){
    calls++;
    if(call.owner.equals("org/embeddedt/modernfix/blockstate/BlockStateCacheHandler"))return false;
    if(call.owner.equals(blockState)&&call.name.equals(initCache)&&call.desc.equals("()V")&&call.getOpcode()==Opcodes.INVOKEVIRTUAL){if(init!=null)return false;init=call;}
   }
   if(n.getOpcode()==Opcodes.RETURN){returns++;exit=n;}
  }
  // Audited original has 13 calls and one normal return. Raw source hashes are checked separately.
  if(init==null||returns!=1||calls!=13||fingerprint(bake)!=EXPECTED_FINGERPRINT)return false;
  bake.instructions.set(init,new MethodInsnNode(Opcodes.INVOKESTATIC,"org/marj4n/smooth_fix/performance/KiltRegistryCacheCounters","deferred","(Ljava/lang/Object;)V",false));
  var tail=new InsnList();tail.add(new InsnNode(Opcodes.ICONST_0));
  tail.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"org/embeddedt/modernfix/blockstate/BlockStateCacheHandler","rebuildParallel","(Z)V",false));
  tail.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"org/marj4n/smooth_fix/performance/KiltRegistryCacheCounters","invalidated","()V",false));
  bake.instructions.insertBefore(exit,tail);return true;
 }
}
