package verification;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.MinecraftClient;
import org.joml.*;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Headless production client classloading/math test; exits before a GL window or account is needed. */
public class ClientRuntimeCheck {
    public static void run() {
        BenchmarkStartupCheck.run();
        ModelPart part = new ModelPart(List.of(), Map.of());
        MatrixStack stack = new MatrixStack();
        Random random = new Random(630);
        for(int test=0;test<1000;test++) {
            stack.loadIdentity();
            part.pivotX=random.nextFloat()*16;part.pivotY=random.nextFloat()*16;part.pivotZ=random.nextFloat()*16;
            part.pitch=random.nextFloat()*6;part.yaw=random.nextFloat()*6;part.roll=random.nextFloat()*6;
            part.xScale=0.5f+random.nextFloat()*2;part.yScale=0.5f+random.nextFloat()*2;part.zScale=0.5f+random.nextFloat()*2;
            MatrixStack vanilla = new MatrixStack();
            vanilla.translate(part.pivotX/16,part.pivotY/16,part.pivotZ/16);
            vanilla.multiply(new Quaternionf().rotationZYX(part.roll,part.yaw,part.pitch));
            vanilla.scale(part.xScale,part.yScale,part.zScale);
            part.rotate(stack);
            if(!stack.peek().getPositionMatrix().equals(vanilla.peek().getPositionMatrix(),0.00002f)
                    || !stack.peek().getNormalMatrix().equals(vanilla.peek().getNormalMatrix(),0.00002f))throw new AssertionError("Transformed ModelPart changed result");
        }
        System.out.println("SMOOTHFIX_VERIFY_PASS production client ModelPart: 1000 rotations/pivots/scales matched vanilla");
        boolean startFrame=false, endWork=false;
        for(java.lang.reflect.Method method:MinecraftClient.class.getDeclaredMethods()) {
            startFrame |= method.getName().contains("smoothfix$startFrame");
            endWork |= method.getName().contains("smoothfix$endWork");
        }
        if(!startFrame || !endWork)throw new AssertionError("Client frame mixin did not apply");
        System.out.println("SMOOTHFIX_VERIFY_PASS production MinecraftClient frame timing mixin applied");
        if(!(net.minecraft.util.Util.getMainWorkerExecutor() instanceof java.util.concurrent.ForkJoinPool pool)
                || pool.getParallelism()!=2)throw new AssertionError("final background pool budget not applied");
        System.out.println("SMOOTHFIX_VERIFY_PASS final client main worker parallelism=2");
        new org.marj4n.smooth_fix.diagnostics.ClientDiagnostics().onInitializeClient();
        System.out.println("SMOOTHFIX_VERIFY_PASS client benchmark packets, tick controller and command registration linked");
        try {
            if(net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("emi"))AdvancedClientCheck.run();
            if(net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("saintsdragons")) {
                Class.forName("com.leon.saintsdragons.client.camera.IgnivorusSkyfallScreenEffects",false,ClientRuntimeCheck.class.getClassLoader());
                System.out.println("SMOOTHFIX_VERIFY_PASS production Saints Dragons Skyfall class transformed");
            }
            if(net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("tclayer")) {
                SnapshotCheck.run();
                Class<?> wrapper=Class.forName("dev.emi.trinkets.compat.WrappingTrinketsUtils");
                Object result=wrapper.getMethod("filterGroupInfo",String.class).invoke(null,"prefixtrinket_group_test-a-b");
                if(!"prefixb".equals(result))throw new AssertionError("production regex result changed: "+result);
                System.out.println("SMOOTHFIX_VERIFY_PASS production TC Layer regex redirect preserves result");
            }
        } catch(Exception e){throw new AssertionError(e);}
        System.exit(0);
    }
}
