import org.joml.*;
import org.marj4n.smooth_fix.performance.DirectModelRotation;
import org.marj4n.smooth_fix.performance.ModelRotationBytecode;
import org.marj4n.smooth_fix.performance.WorkerPoolBytecode;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.util.CheckClassAdapter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Random;
import java.util.jar.JarFile;

/** Check numerical equivalence and fail-closed bytecode matching against the actual MC class. */
public class PerformanceCheck {
    public static void main(String[] args) throws Exception {
        Random random = new Random(4587);
        float maxError = 0;
        for (int sample=0; sample<100_000; sample++) {
            float x=(random.nextFloat()-0.5f)*12, y=(random.nextFloat()-0.5f)*12, z=(random.nextFloat()-0.5f)*12;
            Matrix4f before = new Matrix4f().translation(random.nextFloat()*20,random.nextFloat()*20,random.nextFloat()*20)
                    .rotateXYZ(random.nextFloat(),random.nextFloat(),random.nextFloat())
                    .scale(random.nextFloat()*3,random.nextFloat()*3,random.nextFloat()*3);
            if ((sample&3)==0) before.perspective(1.1f,1.8f,0.1f,1000);
            Matrix3f beforeNormal = new Matrix3f().rotationXYZ(random.nextFloat(),random.nextFloat(),random.nextFloat())
                    .scale(random.nextFloat()*3,random.nextFloat()*3,random.nextFloat()*3);
            Quaternionf q = new Quaternionf().rotationZYX(z,y,x);
            Matrix4f expected = new Matrix4f(before).rotate(q), actual = new Matrix4f(before);
            Matrix3f expectedNormal = new Matrix3f(beforeNormal).rotate(q), actualNormal = new Matrix3f(beforeNormal);
            DirectModelRotation.rotate(actual,actualNormal,z,y,x);
            float[] a=actual.get(new float[16]), b=expected.get(new float[16]);
            for(int i=0;i<16;i++) {
                float difference=java.lang.Math.abs(a[i]-b[i]);maxError=java.lang.Math.max(maxError,difference);
                if(difference>0.00003f)throw new AssertionError("position sample="+sample+" field="+i+" difference="+difference);
            }
            a=actualNormal.get(new float[9]); b=expectedNormal.get(new float[9]);
            for(int i=0;i<9;i++) {
                float difference=java.lang.Math.abs(a[i]-b[i]);maxError=java.lang.Math.max(maxError,difference);
                if(difference>0.00003f)throw new AssertionError("normal sample="+sample+" field="+i+" difference="+difference);
            }
            if(Float.floatToRawIntBits(actual.m30())!=Float.floatToRawIntBits(before.m30())
                    || Float.floatToRawIntBits(actual.m31())!=Float.floatToRawIntBits(before.m31())
                    || Float.floatToRawIntBits(actual.m32())!=Float.floatToRawIntBits(before.m32())
                    || Float.floatToRawIntBits(actual.m33())!=Float.floatToRawIntBits(before.m33()))throw new AssertionError("translation changed");
        }
        ClassNode node=load(args[0]);
        for(float value:new float[]{Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY}) {
            Matrix4f actual=new Matrix4f(),expected=new Matrix4f();Matrix3f actualNormal=new Matrix3f(),expectedNormal=new Matrix3f();
            DirectModelRotation.rotate(actual,actualNormal,value,0,0);
            Quaternionf original=new Quaternionf().rotationZYX(value,0,0);expected.rotate(original);expectedNormal.rotate(original);
            float[] a=actual.get(new float[16]),b=expected.get(new float[16]);
            for(int i=0;i<16;i++)if(Float.floatToIntBits(a[i])!=Float.floatToIntBits(b[i]))throw new AssertionError("invalid angle fallback position");
            a=actualNormal.get(new float[9]);b=expectedNormal.get(new float[9]);
            for(int i=0;i<9;i++)if(Float.floatToIntBits(a[i])!=Float.floatToIntBits(b[i]))throw new AssertionError("invalid angle fallback normal");
        }
        if(ModelRotationBytecode.apply(node,"net/minecraft/client/util/math/MatrixStack", "multiply")!=1)throw new AssertionError("original not matched");
        if(ModelRotationBytecode.apply(node,"net/minecraft/client/util/math/MatrixStack", "multiply")!=0)throw new AssertionError("not idempotent");
        ClassWriter writer=new ClassWriter(0);node.accept(writer);
        StringWriter errors=new StringWriter();CheckClassAdapter.verify(new ClassReader(writer.toByteArray()),false,new PrintWriter(errors));
        if(!errors.toString().isBlank())throw new AssertionError(errors);
        ClassNode changed=load(args[0]);
        for(MethodNode method:changed.methods) for(AbstractInsnNode insn=method.instructions.getFirst(); insn!=null; insn=insn.getNext()) {
            if(insn instanceof TypeInsnNode t && t.desc.equals("org/joml/Quaternionf") && t.getOpcode()==Opcodes.NEW) {
                method.instructions.insert(t,new InsnNode(Opcodes.NOP)); break;
            }
        }
        if(ModelRotationBytecode.apply(changed,"net/minecraft/client/util/math/MatrixStack", "multiply")!=0)throw new AssertionError("changed expression should be skipped");
        System.out.println("PASS 100000 randomized position/normal rotations; max absolute error="+maxError);
        System.out.println("PASS original bytecode substitution, ASM verifier, idempotence, altered-expression fallback");
        try(JarFile jar=new JarFile(args[0])) {
            ClassNode utility=new ClassNode();new ClassReader(jar.getInputStream(jar.getJarEntry("net/minecraft/util/Util.class"))).accept(utility,0);
            if(WorkerPoolBytecode.apply(utility)!=1 || WorkerPoolBytecode.apply(utility)!=0)throw new AssertionError("worker expression/idempotence");
            ClassWriter transformed=new ClassWriter(0);utility.accept(transformed);
            StringWriter workerErrors=new StringWriter();CheckClassAdapter.verify(new ClassReader(transformed.toByteArray()),false,new PrintWriter(workerErrors));
            if(!workerErrors.toString().isBlank())throw new AssertionError(workerErrors);
        }
        System.out.println("PASS original worker factory substitution, idempotence and ASM verifier");
    }
    private static ClassNode load(String path)throws Exception {
        try(JarFile jar=new JarFile(path)) {
            ClassNode node=new ClassNode();new ClassReader(jar.getInputStream(jar.getJarEntry("net/minecraft/client/model/ModelPart.class"))).accept(node,0);return node;
        }
    }
}
