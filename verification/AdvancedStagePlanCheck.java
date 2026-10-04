import org.marj4n.smooth_fix.benchmark.AdvancedStagePlan;
import java.util.*;
public final class AdvancedStagePlanCheck {
  public static void main(String[] args) {
    var expected=List.of("real_terrain_loaded","cold_chunk_generation","structure_scan","vanilla_chest_loot","lootr_chest_loot","inventory_hotbar","emi_search_recipe","combat_hit","tnt_destruction");
    var seen=new ArrayList<String>();
    int i=0;
    while(i>=0){ var s=AdvancedStagePlan.stages().get(i); seen.add(s.name()); i=AdvancedStagePlan.next(i); }
    if(!seen.equals(expected)) throw new AssertionError("transition order="+seen);
    if(AdvancedStagePlan.next(expected.size()-1)!=-1) throw new AssertionError("suite must terminate");
    if(AdvancedStagePlan.stages().stream().anyMatch(s->s.timeoutSeconds()<=s.measurementSeconds())) throw new AssertionError("timeout must exceed measurement");
    System.out.println("PASS advanced stage transitions: "+String.join(" -> ", seen));
  }
}
