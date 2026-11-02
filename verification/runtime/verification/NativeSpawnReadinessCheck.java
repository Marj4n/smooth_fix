package verification;
import org.marj4n.smooth_fix.benchmark.NativeSpawnReadiness;
public final class NativeSpawnReadinessCheck {
 public static void main(String[] args){
  var gate=new NativeSpawnReadiness(100);
  for(int i=0;i<60;i++)require(!gate.observe(100,0,100,0),"Hidden native crowd must not start");
  for(int i=0;i<19;i++)require(!gate.observe(100,100,100,100),"Wait for consecutive settled ticks");
  require(!gate.observe(100,100,99,100),"One still-NoAI actor resets stability");
  for(int i=0;i<19;i++)require(!gate.observe(100,100,100,100),"Reset cannot retain prior progress");
  require(gate.observe(100,100,100,100),"Naturally settled full crowd becomes ready");
  var min=new NativeSpawnReadiness(1);for(int i=0;i<39;i++)require(!min.observe(1,1,1,1),"Minimum discovery period");require(min.observe(1,1,1,1),"Minimum40ticks satisfied");
  var mismatch=new NativeSpawnReadiness(300);for(int i=0;i<100;i++)require(!mismatch.observe(299,299,299,299),"Missing native actor cannot pass");
  boolean rejected=false;try{mismatch.observe(1,2,1,1);}catch(IllegalArgumentException e){rejected=true;}require(rejected,"Impossible observation rejected");
  System.out.println("PASS: no timed crowd starts while hidden, NoAI, animation-pending, missing or unstable; natural readiness needs40ticks plus20 consecutive settled observations. Actual Spawn Animations runtime still required.");
 }
 private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
