package org.marj4n.smooth_fix.mixin.client;

import net.minecraft.item.ItemStack;
import net.minecraft.recipe.Ingredient;
import org.marj4n.smooth_fix.SmoothFix;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * EMI 1.1.24 assumes every non-empty repair Ingredient exposes at least one matching stack.
 * Some modded armor/tool materials violate that assumption: Ingredient#isEmpty() reports false,
 * but Ingredient#getMatchingStacks() returns an empty array. EMI then indexes element zero while
 * building synthetic anvil repair recipes and logs ArrayIndexOutOfBoundsException.
 *
 * Treat those inconsistent ingredients as empty only inside EMI's repair-recipe scan. This skips
 * an invalid synthetic repair recipe instead of letting one malformed modded material interrupt
 * EMI registration. Vanilla repair behavior and the original ingredient are not modified.
 */
@Pseudo
@Mixin(targets = "dev.emi.emi.VanillaPlugin", remap = false)
public abstract class EmiRepairIngredientMixin {

    @Redirect(
            method = "addRepair",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/recipe/Ingredient;isEmpty()Z"
            ),
            require = 0,
            remap = true
    )
    private static boolean smoothFix$treatZeroStackRepairIngredientAsEmpty(Ingredient ingredient) {
        if (ingredient == null || ingredient.isEmpty()) {
            return true;
        }

        ItemStack[] stacks = ingredient.getMatchingStacks();
        if (stacks == null || stacks.length == 0) {
            SmoothFix.logPatchOnce(
                    "emi_repair_empty_ingredient",
                    "Skipped an invalid EMI synthetic repair recipe whose material ingredient exposes zero matching stacks."
            );
            return true;
        }

        return false;
    }
}
