package org.marj4n.smooth_fix.benchmark;
import net.minecraft.block.BlockState;
import net.minecraft.block.PlantBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
/** Observer selection only. Does not change any rendered block, mob, AI or culling. */
public final class ThinPlantOcclusion {
 private ThinPlantOcclusion() { }
 public static boolean eligible(BlockState state,BlockView world,BlockPos position){
  return state.getBlock() instanceof PlantBlock && state.getCollisionShape(world,position).isEmpty();
 }
}
