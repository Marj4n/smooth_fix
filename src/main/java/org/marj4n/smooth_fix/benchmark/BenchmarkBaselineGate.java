package org.marj4n.smooth_fix.benchmark;
import java.util.*;
/** Single owner-thread preflight, bounded to one request; no world state is changed by this gate. */
public final class BenchmarkBaselineGate<T> {
 public static final long BUDGET_NANOS=15_000_000_000L;
 public record Request<T>(UUID owner,UUID run,T payload,long expires) { }
 private Request<T> pending;
 public Request<T> begin(UUID owner,T payload,long now){if(pending!=null)throw new IllegalStateException("Original-state snapshot already pending");return pending=new Request<>(owner,UUID.randomUUID(),payload,now+BUDGET_NANOS);}
 public boolean isPending(){return pending!=null;}
 public Request<T> consume(UUID owner,UUID run,long now){if(pending==null)return null;if(now>=pending.expires){pending=null;return null;}if(!pending.owner.equals(owner)||!pending.run.equals(run))return null;var result=pending;pending=null;return result;}
 public Request<T> expire(long now){if(pending==null||now<pending.expires)return null;return cancel();}
 public Request<T> cancel(){var result=pending;pending=null;return result;}
}
