package org.marj4n.smooth_fix.performance;

import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import org.marj4n.smooth_fix.SmoothFix;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

/** Exact-installed-version, default-OFF retirement of absent animation files after a successful reload. */
public final class AzureObsoleteAnimationKeys {
    private static final AtomicLong EPOCH = new AtomicLong();

    private AzureObsoleteAnimationKeys() {}

    public static void afterReload(MinecraftClient client, CompletableFuture<Void> future) {
        if (!Boolean.getBoolean("smoothfix.experimental.azureObsoleteAnimationKeys")) return;
        long epoch = EPOCH.incrementAndGet();
        future.whenComplete(
                (value, error) -> {
                    if (error != null) return;
                    client.execute(
                            () -> {
                                if (epoch != EPOCH.get() || !client.isOnThread()) return;
                                try {
                                    retire(client.getResourceManager());
                                } catch (Exception | LinkageError failure) {
                                    SmoothFix.logPatchOnce(
                                            "r4-azure-retirement-rejected",
                                            "Azure obsolete-key cleanup skipped or failed: "
                                                    + failure.getClass().getSimpleName());
                                }
                            });
                });
    }

    private static void retire(ResourceManager resources) throws Exception {
        if (!LifecycleRetentionCleanup.auditedJar(
                "azurelib-fabric-1.20.1-3.0.27.jar",
                "503f54351ebac2eabf31ab47364aad4a7ed1d4fc2bb5f93bc443735456f1e5b3")) return;
        Class<?> type =
                Class.forName("mod.azure.azurelib.rewrite.animation.cache.AzBakedAnimationCache");
        Object owner = type.getMethod("getInstance").invoke(null);
        Field field = type.getDeclaredField("bakedAnimations");
        field.setAccessible(true);
        if (!(field.get(owner) instanceof Map<?, ?> map))
            throw new IllegalStateException("Audited animation map shape changed");
        List<Identifier> obsolete = new ArrayList<>();
        for (Object key : map.keySet().toArray())
            if (key instanceof Identifier id
                    && id.getPath().startsWith("animations/")
                    && id.getPath().endsWith(".json")
                    && resources.getResource(id).isEmpty()) obsolete.add(id);
        int removed = 0;
        for (Identifier id : obsolete) if (map.remove(id) != null) removed++;
        if (removed > 0)
            SmoothFix.LOGGER.info(
                    "R4 experimental Azure reload retired {} absent animation resource keys; present resources untouched",
                    removed);
    }
}
