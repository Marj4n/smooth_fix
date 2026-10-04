package verification;

import org.marj4n.smooth_fix.performance.MappedSetSnapshots;
import java.lang.reflect.Constructor;
import java.util.*;
import java.util.function.*;
import java.util.stream.Collectors;

/** Compare transformed upstream snapshots, not a mock of the inventory API. */
public final class SnapshotCheck {
    private static volatile Object sink;
    private SnapshotCheck() { }

    public static void run() throws Exception {
        Class<?> type=Class.forName("io.wispforest.tclayer.ImmutableDelegatingMap");
        Constructor<?> constructor=type.getDeclaredConstructor(String.class,Class.class,Class.class,Map.class,
                UnaryOperator.class,UnaryOperator.class,BiFunction.class,Function.class);
        constructor.setAccessible(true);
        Random random=new Random(1764);
        for(int test=0;test<1000;test++) {
            Map<String,Integer> source=new LinkedHashMap<>();
            for(int i=0;i<random.nextInt(65);i++)source.put("slot_"+i,random.nextInt(9));
            UnaryOperator<String> keys=key->key.replace("slot_", "trinket_");
            BiFunction<String,Integer,String> values=(key,value)->key+":"+value;
            Map<String,String> wrapped=(Map<String,String>)constructor.newInstance("verification",String.class,String.class,
                    source,keys,UnaryOperator.identity(),values,Function.identity());
            Set<Map.Entry<String,String>> expected=original(source,keys,values);
            Set<Map.Entry<String,String>> actual=wrapped.entrySet();
            if(!expected.equals(actual) || !new ArrayList<>(expected).equals(new ArrayList<>(actual)))throw new AssertionError("snapshot/order changed");
            source.put("slot_new",test);
            if(!expected.equals(actual) || !original(source,keys,values).equals(wrapped.entrySet()))throw new AssertionError("snapshot/live refresh changed");
            if(!actual.isEmpty()) {
                try {actual.iterator().next().setValue("changed");throw new AssertionError("mutable entry");}catch(UnsupportedOperationException correct) { }
            }
            try {actual.add(Map.entry("extra","extra"));throw new AssertionError("mutable set");}catch(UnsupportedOperationException correct) { }
        }
        Map<String,Integer> nullSource=new LinkedHashMap<>();nullSource.put("one",1);nullSource.put("two",2);
        Map<?,?> wrapped=(Map<?,?>)constructor.newInstance("verification",String.class,String.class,nullSource,
                UnaryOperator.identity(),UnaryOperator.identity(),(BiFunction<String,Integer,String>)(key,value)->null,Function.identity());
        try {wrapped.entrySet();throw new AssertionError("null value accepted");}catch(NullPointerException correct) { }
        System.out.println("SMOOTHFIX_VERIFY_PASS actual transformed TC Layer: 1000 snapshot/order/mutation cases; immutable entries, null rejection, eager fresh values");
    }

    private static Set<Map.Entry<String,String>> original(Map<String,Integer> source, UnaryOperator<String> keys,
            BiFunction<String,Integer,String> values) {
        return source.entrySet().stream().map(entry->Map.entry(keys.apply(entry.getKey()),values.apply(entry.getKey(),entry.getValue()))).collect(Collectors.toUnmodifiableSet());
    }

    public static void main(String[] args) {
        Set<Integer> source=new LinkedHashSet<>();for(int i=0;i<24;i++)source.add(i);
        Function<Integer,Map.Entry<Integer,Integer>> mapper=input->Map.entry(input,input+3);
        for(int i=0;i<20000;i++) {
            sink=source.stream().map(mapper).collect(Collectors.toUnmodifiableSet());sink=MappedSetSnapshots.collect(source,mapper);
        }
        com.sun.management.ThreadMXBean bean=(com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();
        bean.setThreadAllocatedMemoryEnabled(true);
        for(int trial=0;trial<3;trial++) {
            long id=Thread.currentThread().getId(),oldAlloc=bean.getThreadAllocatedBytes(id),begin=System.nanoTime();
            for(int i=0;i<100000;i++)sink=source.stream().map(mapper).collect(Collectors.toUnmodifiableSet());
            long oldNanos=System.nanoTime()-begin;oldAlloc=bean.getThreadAllocatedBytes(id)-oldAlloc;
            long newAlloc=bean.getThreadAllocatedBytes(id);begin=System.nanoTime();
            for(int i=0;i<100000;i++)sink=MappedSetSnapshots.collect(source,mapper);
            long newNanos=System.nanoTime()-begin;newAlloc=bean.getThreadAllocatedBytes(id)-newAlloc;
            System.out.println("SNAPSHOT_MICRO trial="+trial+" entries=24 calls=100000 originalBytesPerCall="+(oldAlloc/100000.0)+" revisedBytesPerCall="+(newAlloc/100000.0)+" originalNsPerCall="+(oldNanos/100000.0)+" revisedNsPerCall="+(newNanos/100000.0));
            if(newAlloc>=oldAlloc)throw new AssertionError("snapshot allocation did not decrease");
        }
    }
}
