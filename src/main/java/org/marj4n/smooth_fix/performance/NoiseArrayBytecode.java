package org.marj4n.smooth_fix.performance;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
/** Exact audited opcode footprints. Validate both sites before any mutation; mismatches retain upstream code. */
public final class NoiseArrayBytecode {
 private NoiseArrayBytecode() { }
 public static boolean apply(ClassNode target){
  MultiANewArrayInsnNode column=null,cube=null;MethodNode cm=null,im=null;
  for(var m:target.methods){if(m.name.equals("generateNoiseColumn")&&m.desc.equals("(IIII)[[D"))cm=m;
   if(m.name.equals("interpolateNoiseCube")&&m.desc.endsWith(";II)[[[[D"))im=m;}
  if(cm==null||im==null||fingerprint(cm)!=-1804660552||fingerprint(im)!=-1285828132)return false;
  for(var n:cm.instructions)if(n instanceof MultiANewArrayInsnNode a){if(column!=null||!a.desc.equals("[[D")||a.dims!=2)return false;column=a;}
  for(var n:im.instructions)if(n instanceof MultiANewArrayInsnNode a){if(cube!=null||!a.desc.equals("[[[[D")||a.dims!=4)return false;cube=a;}
  if(column==null||cube==null)return false;
  String owner="org/marj4n/smooth_fix/performance/NoiseArrayAllocation";
  cm.instructions.set(column,new MethodInsnNode(Opcodes.INVOKESTATIC,owner,"column","(II)[[D",false));
  im.instructions.set(cube,new MethodInsnNode(Opcodes.INVOKESTATIC,owner,"cube","(IIII)[[[[D",false));return true;
 }
 private static int fingerprint(MethodNode m){var s=new StringBuilder();for(var n:m.instructions)if(n.getOpcode()>=0){s.append(n.getOpcode()).append(',');if(n instanceof VarInsnNode v)s.append(v.var);if(n instanceof IntInsnNode v)s.append(v.operand);if(n instanceof IincInsnNode v)s.append(v.var).append(':').append(v.incr);if(n instanceof FieldInsnNode v)s.append(v.owner).append(v.name).append(v.desc);if(n instanceof TypeInsnNode v)s.append(v.desc);if(n instanceof MultiANewArrayInsnNode v)s.append(v.desc).append(v.dims);if(n instanceof LdcInsnNode v)s.append(v.cst);if(n instanceof MethodInsnNode v)s.append(v.owner.startsWith("net/minecraft/")?"minecraft-method":v.owner+v.name+v.desc);s.append(';');}return s.toString().hashCode();}
}
