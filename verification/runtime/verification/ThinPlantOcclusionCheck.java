package verification;
import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EmptyBlockView;
import org.marj4n.smooth_fix.benchmark.ThinPlantOcclusion;
public final class ThinPlantOcclusionCheck {
 public static void main(String[] args){
  SharedConstants.createGameVersion();Bootstrap.initialize();
  for(var block:new net.minecraft.block.Block[]{Blocks.TALL_GRASS,Blocks.FERN,Blocks.DANDELION})if(!ThinPlantOcclusion.eligible(block.getDefaultState(),EmptyBlockView.INSTANCE,BlockPos.ORIGIN))throw new AssertionError("Known thin plant rejected: "+block);
  for(var block:new net.minecraft.block.Block[]{Blocks.OAK_LEAVES,Blocks.STONE,Blocks.GLASS,Blocks.POWDER_SNOW,Blocks.AIR,Blocks.STRUCTURE_VOID,Blocks.CACTUS})if(ThinPlantOcclusion.eligible(block.getDefaultState(),EmptyBlockView.INSTANCE,BlockPos.ORIGIN))throw new AssertionError("Opaque/unknown/non-plant shape bypassed: "+block);
  System.out.println("PASS: actual vanilla thin plants classified narrowly; leaves/solid/glass/powder snow/air/unknown non-plants excluded. Actual camera still requires full native collider ray MISS and unchanged rendering/visual gates.");
 }
}
