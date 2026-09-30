package org.marj4n.smooth_fix.mixin.client;

import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.marj4n.smooth_fix.SmoothFix;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Obscure API 16's food-icon tooltip helper blindly applies Objects.requireNonNull to
 * Item#getFoodComponent(). EMI calls tooltip generation for every indexed stack, including
 * non-food items, which can turn that assumption into a NullPointerException while search is
 * being baked.
 *
 * Skipping only the food-icon subroutine for items without a food component preserves the rest
 * of Obscure API's tooltip pipeline and removes the exception from EMI search indexing.
 */
@Pseudo
@Mixin(targets = "com.obscuria.obscureapi.client.tooltips.TooltipBuilder$AttributeIcons", remap = false)
public abstract class ObscureTooltipFoodMixin {

    @Inject(
            method = "putFoodIcons",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private static void smoothFix$skipNullFoodComponent(
            List<Text> tooltip,
            ItemStack stack,
            CallbackInfo ci
    ) {
        if (stack == null || stack.isEmpty() || stack.getItem().getFoodComponent() == null) {
            SmoothFix.logPatchOnce(
                    "obscure_tooltip_null_food",
                    "Guarded Obscure API food tooltip generation for non-food stacks during search indexing."
            );
            ci.cancel();
        }
    }
}
