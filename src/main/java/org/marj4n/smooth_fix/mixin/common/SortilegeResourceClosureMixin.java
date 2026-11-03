package org.marj4n.smooth_fix.mixin.common;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import java.io.*;

/** OFF-by-default ownership fix for an exact audited Sortilege class; original parser bytes preserved. */
@Pseudo
@Mixin(targets = "net.lyof.sortilege.setup.ReloadListener", remap = false)
public abstract class SortilegeResourceClosureMixin {
    @WrapOperation(
            method = "reload",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Ljava/io/InputStream;readAllBytes()[B",
                            remap = false),
            require = 1)
    private byte[] smoothfix$closeConsumedRecipe(InputStream input, Operation<byte[]> original)
            throws IOException {
        byte[] bytes;
        try (input) {
            bytes = original.call(input);
        } catch (IOException | RuntimeException | Error error) {
            org.marj4n.smooth_fix.performance.SortilegeResourceCounters.failed();
            throw error;
        }
        org.marj4n.smooth_fix.performance.SortilegeResourceCounters.closed(bytes.length);
        return bytes;
    }
}
