package org.marj4n.smooth_fix.benchmark;
import org.marj4n.smooth_fix.diagnostics.TimingSamples;
import java.util.*;
/** Bounded response-time observations, separate from overall FPS/frame intervals. */
public final class ActionMetrics {
    private final Map<String,TimingSamples> times=new TreeMap<>();
    private final Map<String,Integer> counts=new TreeMap<>();
    private final List<Map<String,Object>> events=new ArrayList<>();
    private int successes;
    private final Map<String,Integer> successKinds=new TreeMap<>();
    public synchronized void count(String key){counts.merge(key,1,Integer::sum);}
    public synchronized void success(String kind,long started,String detail) {
        long nanos=Math.max(0,System.nanoTime()-started);times.computeIfAbsent(kind,k->new TimingSamples(50_000_000)).add(nanos);successes++;
        successKinds.merge(kind,1,Integer::sum);
        if(events.size()<64)events.add(Map.of("kind",kind,"ms",nanos/1e6,"detail",detail));
    }
    public synchronized int successes(){return successes;}
    public synchronized boolean observed(String kind){return successKinds.containsKey(kind);}
    public synchronized boolean attempted(String kind){return counts.containsKey(kind);}
    public synchronized int countValue(String kind){return counts.getOrDefault(kind,0);}
    public synchronized Map<String,Object> snapshot(){Map<String,Object> out=new LinkedHashMap<>();out.put("counts",new TreeMap<>(counts));Map<String,Object> timing=new TreeMap<>();times.forEach((key,value)->timing.put(key,value.snapshot()));out.put("latencies",timing);out.put("successfulActions",successes);out.put("first64Events",new ArrayList<>(events));return out;}
}
