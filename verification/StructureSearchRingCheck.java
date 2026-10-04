import org.marj4n.smooth_fix.benchmark.StructureSearchRing;
import java.util.*;

public final class StructureSearchRingCheck {
    public static void main(String[] args){
        var cursor=new StructureSearchRing(0,40);Set<StructureSearchRing.Cell> cells=new HashSet<>();while(cursor.hasNext())if(!cells.add(cursor.next()))throw new AssertionError("repeated candidate region");
        if(cells.size()!=81*81)throw new AssertionError("region count");for(int x=-40;x<=40;x++)for(int z=-40;z<=40;z++)if(!cells.contains(new StructureSearchRing.Cell(x,z)))throw new AssertionError("missed region");
        int radius=10000;for(int i=1;i<=6;i++){if(radius!=i*10000)throw new AssertionError("radius expansion");radius=StructureSearchRing.nextRadius(radius,10000,60000);}
        if(radius!=60000)throw new AssertionError("world bound overshoot");
        for(long distance:new long[]{0,9999,10000,10001,19999,20000,20001,30000}){
            int hits=0;for(int i=0;i<3;i++)if(StructureSearchRing.inBand(distance*distance,i*10000,(i+1)*10000))hits++;
            if(hits!=1)throw new AssertionError("overlap or gap at "+distance);
        }
        if(!StructureSearchRing.inBand(42_000_000L*42_000_000L,40_000_000,43_000_000))throw new AssertionError("overflow at world edge");
        System.out.println("PASS expanding 10000/20000/30000/40000/50000/60000-block bands; exact boundary ownership; 6561 unique region cells; negative coordinates and world-edge overflow");
    }
}
