package org.marj4n.smooth_fix.benchmark;
/** Read-only readiness gate: natural visibility and native AI must settle before timed measurement. */
public final class NativeSpawnReadiness {
 private final int expected; private int ticks,stableTicks;
 public NativeSpawnReadiness(int expected){if(expected<1)throw new IllegalArgumentException("Positive crowd required");this.expected=expected;}
 public boolean observe(int alive,int visible,int aiReady,int animationFinished){
  if(alive<0||visible<0||aiReady<0||animationFinished<0||visible>alive||aiReady>alive||animationFinished>alive)throw new IllegalArgumentException("Inconsistent native observation");
  ticks++;boolean settled=alive==expected&&visible==expected&&aiReady==expected&&animationFinished==expected;
  stableTicks=settled?stableTicks+1:0;return ticks>=40&&stableTicks>=20;
 }
 public int ticks(){return ticks;}
 public int stableTicks(){return stableTicks;}
}
