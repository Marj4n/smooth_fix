package org.marj4n.smooth_fix.benchmark;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;
import org.marj4n.smooth_fix.diagnostics.TimingSamples;
import java.util.*;

/** Measurements retain their original stage token, so late chunk workers never contaminate a new stage. */
public final class ChunkMetrics {
    private static volatile Recording active;
    public record Span(Recording recording,String kind,long chunk,long started) { }
    public static final class Recording {
        final ServerWorld world;
        final Map<String,TimingSamples> timings=new LinkedHashMap<>();
        final Map<String,Set<Long>> positions=new LinkedHashMap<>();
        final Map<String,Integer> counters=new TreeMap<>();
        boolean truncated,closed;
        Recording(ServerWorld world){this.world=world;}
        synchronized void record(String kind,long position,long elapsed) {
            if(closed)return;
            timings.computeIfAbsent(kind,k->new TimingSamples(50_000_000)).add(elapsed);
            Set<Long> chunks=positions.computeIfAbsent(kind,k->new HashSet<>());
            if(chunks.size()<8192)chunks.add(position);else truncated=true;
        }
        public synchronized Map<String,Object> finish() {
            closed=true;Map<String,Object> out=new LinkedHashMap<>();
            timings.forEach((key,value)->out.put(key,value.snapshot()));
            Map<String,Integer> unique=new LinkedHashMap<>();positions.forEach((key,value)->unique.put(key,value.size()));
            out.put("uniqueChunksByHook",unique);out.put("uniqueChunkLimit",8192);out.put("uniqueCountsTruncated",truncated);out.put("mechanicCounters",new TreeMap<>(counters));
            out.put("measurementNote","Observed feature-generation, chunk NBT decode/encode and full-chunk load/unload event counts, including stage preparation. Event callback bookkeeping is not reported as chunk load/unload duration. Feature generation is not total generation latency. Encode/decode excludes disk/network wait. Load counts include cache reuse; zero feature-generation observations does not certify a cold route. Late worker completions after this stage are excluded. Missing hooks/zero observations are not a successful workload.");
            if(active==this)active=null;return out;
        }
    }
    private ChunkMetrics() { }
    public static Recording start(ServerWorld world){Recording r=new Recording(world);active=r;return r;}
    public static Span begin(ServerWorld world,ChunkPos pos,String kind) {
        Recording r=active;return r==null || r.world!=world?null:new Span(r,kind,pos.toLong(),System.nanoTime());
    }
    public static void end(Span span){if(span!=null)span.recording.record(span.kind,span.chunk,System.nanoTime()-span.started);}
    public static void loaded(ServerWorld world,WorldChunk chunk,boolean load){
        Recording r=active;if(r==null || r.world!=world)return;
        synchronized(r){if(r.closed)return;String kind=load?"fullChunkLoad":"fullChunkUnload";r.counters.merge(kind+"Events",1,Integer::sum);Set<Long> positions=r.positions.computeIfAbsent(kind,k->new HashSet<>());if(positions.size()<8192)positions.add(chunk.getPos().toLong());else r.truncated=true;}
    }
    public static void count(ServerWorld world,String key,int amount){Recording r=active;if(r!=null && r.world==world)synchronized(r){if(!r.closed)r.counters.merge(key,amount,Integer::sum);}}
}
