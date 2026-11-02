package org.marj4n.smooth_fix.diagnostics;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/** Exact totals over the entire session; bounded, unbiased reservoir for percentiles. Allocated only while recording. */
public final class TimingSamples {
    private final long[] reservoir=new long[8192];
    private final long budget;
    private long count,total,max,overBudget,over100ms;
    private long random=0x58A34FBE7D3CL;
    public TimingSamples(long budget){this.budget=budget;}
    public synchronized void add(long nanos){
        if(nanos<0)return;
        count++;total+=nanos;max=Math.max(max,nanos);
        if(nanos>budget)overBudget++;
        if(nanos>100_000_000L)over100ms++;
        if(count<=reservoir.length)reservoir[(int)count-1]=nanos;
        else {
            random^=random<<13;random^=random>>>7;random^=random<<17;
            long index=Long.remainderUnsigned(random,count);
            if(index<reservoir.length)reservoir[(int)index]=nanos;
        }
    }
    public synchronized Map<String,Object> snapshot(){
        int size=(int)Math.min(count,reservoir.length);long[] sorted=Arrays.copyOf(reservoir,size);Arrays.sort(sorted);
        Map<String,Object> out=new LinkedHashMap<>();out.put("samples",count);out.put("percentileSamples",size);
        out.put("meanMs",count==0?0:total/1_000_000.0/count);out.put("maxMs",max/1_000_000.0);
        out.put("p50Ms",percentile(sorted,0.5));out.put("p999Ms",percentile(sorted,0.999));
        out.put("slowestOnePercentMeanMs",slowestMean(sorted,0.01));out.put("slowestPointOnePercentMeanMs",slowestMean(sorted,0.001));
        out.put("p95Ms",percentile(sorted,0.95));out.put("p99Ms",percentile(sorted,0.99));
        out.put("samplesOverBudget",overBudget);out.put("budgetMs",budget/1_000_000.0);out.put("samplesOver100Ms",over100ms);
        out.put("percentileMethod",count<=reservoir.length?"all session samples":"uniform reservoir over entire session (approximate percentiles)");
        return out;
    }
    public static Map<String,Object> frameRates(Map<String,Object> intervals) {
        Map<String,Object> rates=new LinkedHashMap<>();
        rates.put("averageFps",fps(intervals.get("meanMs")));rates.put("medianFps",fps(intervals.get("p50Ms")));
        rates.put("onePercentLowFps",fps(intervals.get("slowestOnePercentMeanMs")));
        rates.put("pointOnePercentLowFps",fps(intervals.get("slowestPointOnePercentMeanMs")));
        rates.put("method","FPS from inter-frame durations; lows use the mean of the slowest ceil(n*share) reservoir samples; approximate after 8192 intervals. Not GPU timings or reciprocal CPU work time.");
        return rates;
    }
    private static Double fps(Object ms){return ms instanceof Number n&&n.doubleValue()>0?1000/n.doubleValue():null;}
    private static double slowestMean(long[] sorted,double share){
        if(sorted.length==0)return 0;int count=Math.max(1,(int)Math.ceil(sorted.length*share));double total=0;
        for(int i=sorted.length-count;i<sorted.length;i++)total+=sorted[i];return total/count/1e6;
    }
    private static double percentile(long[] a,double q){return a.length==0?0:a[(int)Math.ceil(a.length*q)-1]/1_000_000.0;}
}
