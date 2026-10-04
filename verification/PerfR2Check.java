import net.minecraft.util.math.BlockPos;
import org.marj4n.smooth_fix.performance.*;
import org.marj4n.smooth_fix.diagnostics.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
import java.util.*;
import java.util.function.Predicate;
import java.util.jar.JarFile;
/** Exact mod fixtures, vanilla search ordering and whole-recording profiler regression checks. */
public final class PerfR2Check {
    public static void main(String[] args)throws Exception {
        Random random=new Random(733);
        for(int n=0;n<10000;n++){
            BlockPos origin=new BlockPos(random.nextInt(500)-250,random.nextInt(300)-64,random.nextInt(500)-250);
            int radius=random.nextInt(6);List<Long> positions=new ArrayList<>();Set<Long> allowed=new HashSet<>();
            for(int k=0;k<random.nextInt(60);k++){long pos=origin.add(random.nextInt(17)-8,random.nextInt(17)-8,random.nextInt(17)-8).asLong();positions.add(pos);if(random.nextBoolean())allowed.add(pos);if(random.nextBoolean())positions.add(pos);}
            Predicate<BlockPos> predicate=p->positions.contains(p.asLong()) && allowed.contains(p.asLong());
            BlockPos expected=BlockPos.findClosest(origin,radius,radius,predicate).orElse(null);
            BlockPos actual=SigilSearch.find(origin,radius,predicate,positions);
            if(!Objects.equals(expected,actual))throw new AssertionError("Search ordering changed at "+origin+" radius "+radius+": "+expected+" != "+actual);
        }
        BlockPos zero=BlockPos.ORIGIN;List<Long> full=new ArrayList<>();for(int i=0;i<300;i++)full.add(zero.asLong());
        if(!zero.equals(SigilSearch.find(zero,1,p->true,full)))throw new AssertionError("large-list fallback");
        if(SigilSearch.find(zero,16,p->{throw new AssertionError("empty list evaluated predicate");},List.of())!=null)throw new AssertionError();
        System.out.println("PASS 10000 randomized sigil searches match vanilla, including bounds/ties/duplicates/stale entries; empty and >256 fallback");
        String[] inputs={"", "trinket_group_a-b", "prefixtrinket_group_a-b-c", "trinket_group_a-\ntrinket_group_b-", "trinket_group_-", "漢字", "trinket_group_x-\rtrinket_group_y-"};
        for(String input:inputs)compareRegex(input);
        for(int n=0;n<10000;n++){StringBuilder s=new StringBuilder();for(int k=0;k<20;k++)s.append(switch(random.nextInt(8)){case 0->"trinket_group_";case 1->"-";case 2->"\n";case 3->"\r";default->"abc";});compareRegex(s.toString());}
        System.out.println("PASS 10007 regex cases match original replacement; alternate-expression fallback");
        TimingSamples timings=new TimingSamples(50_000_000);for(int i=0;i<100000;i++)timings.add(i<50000?1_000_000:101_000_000);
        Map<String,Object> stats=timings.snapshot();if(((Number)stats.get("samples")).longValue()!=100000 || ((Number)stats.get("meanMs")).doubleValue()!=51 || ((Number)stats.get("samplesOverBudget")).longValue()!=50000 || ((Number)stats.get("maxMs")).doubleValue()!=101 || ((Number)stats.get("percentileSamples")).intValue()!=8192)throw new AssertionError(stats);
        StackSamples stacks=new StackSamples();for(int i=0;i<400;i++)stacks.add(new StackTraceElement[]{new StackTraceElement("early"+i,"tick","Test.java",i)});
        for(int i=0;i<500;i++)stacks.add(new StackTraceElement[]{new StackTraceElement("late.bewitchment.BWUtil","search","Test.java",i)});
        Map<String,Object> ss=stacks.snapshot();Map<?,?> estimates=(Map<?,?>)ss.get("stackCountEstimates");Map<?,?> errors=(Map<?,?>)ss.get("stackCountMaximumErrors");String late="late.bewitchment.BWUtil#search\n";
        if(!estimates.containsKey(late) || ((Number)estimates.get(late)).intValue()-((Number)errors.get(late)).intValue()!=500 || ((Number)ss.get("capturedSamples")).intValue()!=900)throw new AssertionError("late hot spot dropped");
        System.out.println("PASS exact 100000-session timing totals, bounded reservoir, late-hotspot capture with correct error bounds");
        ClassNode bw=load(args[0],"moriyashiine/bewitchment/mixin/sigil/LivingEntityMixin.class");
        if(BewitchmentSearchBytecode.apply(bw)!=2 || BewitchmentSearchBytecode.apply(bw)!=0)throw new AssertionError("Bewitchment exact fixture not matched/idempotent");
        for(MethodNode method:bw.methods)if(method.instructions.size()>0)new Analyzer<BasicValue>(new BasicVerifier()).analyze(bw.name,method);
        ClassNode changed=load(args[0],"moriyashiine/bewitchment/mixin/sigil/LivingEntityMixin.class");
        for(MethodNode method:changed.methods)for(AbstractInsnNode node:method.instructions)if(node instanceof FieldInsnNode f && f.name.equals("potentialSigils"))f.name="changedUpstream";
        if(BewitchmentSearchBytecode.apply(changed)!=0)throw new AssertionError("unverified predicate patched");
        ClassNode dragon=load(args[1],"com/leon/saintsdragons/client/camera/IgnivorusSkyfallScreenEffects.class");
        if(DragonScanBytecode.apply(dragon,"net/minecraft/class_638","method_18112")!=1 || DragonScanBytecode.apply(dragon,"net/minecraft/class_638","method_18112")!=0)throw new AssertionError("Saints exact fixture");
        for(MethodNode method:dragon.methods)if(method.instructions.size()>0)new Analyzer<BasicValue>(new BasicVerifier()).analyze(dragon.name,method);
        System.out.println("PASS exact Bewitchment 1.20-10 and Saints 0.9.85 bytecode, stack analyzer, idempotence and altered-upstream fallback");
        ClassNode snapshot=load(args[2],"io/wispforest/tclayer/ImmutableDelegatingMap.class");
        if(TrinketSnapshotBytecode.apply(snapshot)!=1 || TrinketSnapshotBytecode.apply(snapshot)!=0)throw new AssertionError("TC Layer snapshot fixture/idempotence");
        for(MethodNode method:snapshot.methods)if(method.instructions.size()>0)new Analyzer<BasicValue>(new BasicVerifier()).analyze(snapshot.name,method);
        ClassNode altered=load(args[2],"io/wispforest/tclayer/ImmutableDelegatingMap.class");
        for(MethodNode method:altered.methods)if(method.name.equals("entrySet"))for(AbstractInsnNode instruction:method.instructions)if(instruction instanceof MethodInsnNode call && call.name.equals("toUnmodifiableSet"))call.name="toSet";
        if(TrinketSnapshotBytecode.apply(altered)!=0)throw new AssertionError("changed snapshot expression patched");
        System.out.println("PASS exact TC Layer beta.14 snapshot ASM verifier, idempotence and altered-upstream fallback");
    }
    private static void compareRegex(String input){for(String replacement:new String[]{"", "X"})if(!input.replaceAll("(trinket_group_).*-",replacement).equals(TrinketNames.replace(input,"(trinket_group_).*-",replacement)))throw new AssertionError(input);if(!input.replaceAll("a","b").equals(TrinketNames.replace(input,"a","b")))throw new AssertionError();}
    private static ClassNode load(String jar,String entry)throws Exception {try(JarFile f=new JarFile(jar)){ClassNode node=new ClassNode();new ClassReader(f.getInputStream(f.getJarEntry(entry))).accept(node,0);return node;}}
}
