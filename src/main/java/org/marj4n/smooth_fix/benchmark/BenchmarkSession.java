package org.marj4n.smooth_fix.benchmark;
import java.lang.management.ManagementFactory;
import java.util.*;
import org.marj4n.smooth_fix.config.SmoothFixConfig;
/** Non-secret process identity; stage repetitions never count as independent JVM launches. */
public final class BenchmarkSession {
 private BenchmarkSession() { }
 private static String property(String key,String fallback){String value=System.getProperty(key,fallback);return value.matches("[A-Za-z0-9_.-]{1,96}")?value:fallback;}
 public static Map<String,Object> snapshot(){
  var runtime=ManagementFactory.getRuntimeMXBean();var c=SmoothFixConfig.get();var out=new LinkedHashMap<String,Object>();
  out.put("sessionId",property("smoothfix.benchmark.sessionId","unspecified"));out.put("condition",property("smoothfix.benchmark.condition","unspecified"));
  out.put("optimization",property("smoothfix.benchmark.optimization","none"));out.put("pid",ProcessHandle.current().pid());out.put("jvmStartEpochMillis",runtime.getStartTime());
  out.put("javaVersion",System.getProperty("java.version"));out.put("maximumHeapBytes",Runtime.getRuntime().maxMemory());
  out.put("jvmMemoryAndGcArguments",runtime.getInputArguments().stream().filter(v->v.startsWith("-Xmx")||v.startsWith("-Xms")||v.startsWith("-XX:")&&!v.contains("StartFlightRecording")&&!v.contains("ErrorFile")&&!v.contains("HeapDumpPath")).toList());
  out.put("jfrConfiguredAtLaunch",runtime.getInputArguments().stream().anyMatch(v->v.startsWith("-XX:StartFlightRecording")));
  out.put("stackSampling",c.benchmarkStackSampling);out.put("gcCollectors",ManagementFactory.getGarbageCollectorMXBeans().stream().map(v->v.getName()).toList());
  out.put("candidateFlags",Map.of("betterCavesNoiseAllocationFix",c.betterCavesNoiseAllocationFix,"kiltRenderContextAllocationFix",c.kiltRenderContextAllocationFix,"immutableModelVariantCache",c.immutableModelVariantCache,"sortilegeReloadStreamClosureFix",org.marj4n.smooth_fix.performance.SortilegeResourceAudit.requested(c.sortilegeReloadStreamClosureFix)));return out;
 }
}
