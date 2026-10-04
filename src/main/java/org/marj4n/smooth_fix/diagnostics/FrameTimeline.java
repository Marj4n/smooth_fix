package org.marj4n.smooth_fix.diagnostics;
import java.util.*;
/** Exact 1-second totals, no per-bucket reservoirs; bounded to 600 seconds (~40KiB). */
public final class FrameTimeline {
    private final long[][] values=new long[8][600];
    private final long[][] overTarget = new long[2][600];
    private final long targetBudget;
    private int highest=-1;
    public FrameTimeline() { this(8_333_333L); }
    public FrameTimeline(long targetBudget) { this.targetBudget = targetBudget; }
    public synchronized void add(long elapsed,long duration,boolean work) {
        int second=(int)(elapsed/1_000_000_000L);if(second<0 || second>=600 || duration<0)return;highest=Math.max(highest,second);
        int base=work?4:0;values[base][second]++;values[base+1][second]+=duration;values[base+2][second]=Math.max(values[base+2][second],duration);if(duration>8_333_333L)values[base+3][second]++;
        if (duration > targetBudget) overTarget[work ? 1 : 0][second]++;
    }
    public synchronized List<Map<String,Object>> snapshot() {
        List<Map<String,Object>> out=new ArrayList<>();for(int second=0;second<=highest;second++) {
            Map<String,Object> bucket=new LinkedHashMap<>();bucket.put("second",second);
            bucket.put("targetBudgetMs", targetBudget / 1_000_000.0);
            bucket.put("intervalOverTargetBudget", overTarget[0][second]);
            bucket.put("workOverTargetBudget", overTarget[1][second]);
            for(int base:new int[]{0,4}){String prefix=base==0?"interval":"work";long count=values[base][second];bucket.put(prefix+"Samples",count);bucket.put(prefix+"MeanMs",count==0?0:values[base+1][second]/1e6/count);bucket.put(prefix+"MaxMs",values[base+2][second]/1e6);bucket.put(prefix+"Over8_33Ms",values[base+3][second]);}
            out.add(bucket);
        }return out;
    }
}
