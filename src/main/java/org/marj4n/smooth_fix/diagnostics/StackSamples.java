package org.marj4n.smooth_fix.diagnostics;

import java.util.LinkedHashMap;
import java.util.Map;

/** Space-Saving heavy hitters: late hotspots can replace early ones instead of being silently dropped. */
public final class StackSamples {
    private final Map<String,Counter> entries=new LinkedHashMap<>();
    private final Map<String,Integer> groups=new LinkedHashMap<>();
    private int total,replacements;
    private static final int LIMIT=256;
    private static final String[] GROUPS={"bewitchment","saintsdragons","aaaparticles","Effekseer","xaero","tclayer","trinkets","flywheel","sodiumdynamiclights","sodium","iris","smooth_classes","smooth_fix","extraspellattributes","fancymenu","dynamic_resource_bars","presencefootsteps","jeremyseqsdamageindicators","dev.emi"};
    public synchronized void add(StackTraceElement[] trace){
        StringBuilder text=new StringBuilder();
        for(int i=0;i<Math.min(24,trace.length);i++)text.append(trace[i].getClassName()).append('#').append(trace[i].getMethodName()).append('\n');
        String key=text.toString();total++;
        Counter counter=entries.get(key);
        if(counter!=null)counter.count++;
        else if(entries.size()<LIMIT)entries.put(key,new Counter(1,0));
        else {
            Map.Entry<String,Counter> minimum=null;
            for(var e:entries.entrySet())if(minimum==null||e.getValue().count<minimum.getValue().count)minimum=e;
            int old=minimum.getValue().count;entries.remove(minimum.getKey());entries.put(key,new Counter(old+1,old));replacements++;
        }
        String group=group(trace);groups.merge(group,1,Integer::sum);
    }
    private static String group(StackTraceElement[] trace){
        for(StackTraceElement e:trace){String c=e.getClassName();
            for(String name:GROUPS)
                if(c.contains(name))return name;
        }
        return "other_or_vanilla";
    }
    public synchronized Map<String,Object> snapshot(){
        Map<String,Integer> estimates=new LinkedHashMap<>(),errors=new LinkedHashMap<>();
        entries.entrySet().stream().sorted((a,b)->Integer.compare(b.getValue().count,a.getValue().count)).forEach(e->{estimates.put(e.getKey(),e.getValue().count);errors.put(e.getKey(),e.getValue().error);});
        Map<String,Object> out=new LinkedHashMap<>();out.put("capturedSamples",total);out.put("distinctStackLimit",LIMIT);
        out.put("replacedEntries",replacements);out.put("stackCountEstimates",estimates);out.put("stackCountMaximumErrors",errors);
        out.put("subsystemObservations",new LinkedHashMap<>(groups));
        out.put("interpretation","Every slow sample contributes. Stack count lies between estimate-error and estimate. These observations are not CPU percentages; subsystem labels use the first recognized frame.");
        return out;
    }
    private static final class Counter{int count;final int error;Counter(int count,int error){this.count=count;this.error=error;}}
}
