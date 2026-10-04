package org.marj4n.smooth_fix.mixin.common;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import org.marj4n.smooth_fix.benchmark.AdvancedBenchmark;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(Entity.class)
public abstract class BenchmarkDropOwnershipMixin {
    @Inject(method="dropStack(Lnet/minecraft/item/ItemStack;F)Lnet/minecraft/entity/ItemEntity;",at=@At("RETURN")) private void smoothfix$tagDrop(ItemStack stack,float y,CallbackInfoReturnable<ItemEntity> ci){if(ci.getReturnValue()!=null)AdvancedBenchmark.copyOwnership((Entity)(Object)this,ci.getReturnValue());}
}
