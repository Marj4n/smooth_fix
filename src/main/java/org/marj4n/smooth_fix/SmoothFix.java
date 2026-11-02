package org.marj4n.smooth_fix;

import net.fabricmc.api.ModInitializer;
import org.marj4n.smooth_fix.config.SmoothFixConfig;
import org.marj4n.smooth_fix.diagnostics.ServerDiagnostics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class SmoothFix implements ModInitializer {
    public static final String MOD_ID = "smooth_fix";
    public static final String MOD_NAME = "Smooth Fix";
    public static final String BUILD_ID = "1.0.5-horizon-r4-dev-20261102";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);

    private static final Set<String> LOGGED_PATCHES = ConcurrentHashMap.newKeySet();

    @Override
    public void onInitialize() {
        SmoothFixConfig config = SmoothFixConfig.get();
        org.marj4n.smooth_fix.performance.LifecycleRetentionCleanup.install();
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            if (net.fabricmc.loader.api.FabricLoader.getInstance().getEnvironmentType() == net.fabricmc.api.EnvType.SERVER)
                org.marj4n.smooth_fix.performance.ReportIo.shutdown();
        });

        if (config.diagnostics && config.profilingTelemetry) {
            ServerDiagnostics.install();
            org.marj4n.smooth_fix.benchmark.ServerBenchmark.install();
            org.marj4n.smooth_fix.benchmark.AdvancedBenchmark.install();
        }

        LOGGER.info("{} initialized.", MOD_NAME);
        LOGGER.info("Build {}: model rotation allocation fix={}, confirmed teleport anchor={}, background worker limit={}",
                BUILD_ID, config.modelRotationAllocationFix, config.confirmedTeleportAnchorFix, config.backgroundWorkerLimit);
        LOGGER.info("Server client-hook guard={}, invalid rotation guard={}, diagnostics={}",
                config.dedicatedServerClientMixinGuard, config.invalidEntityRotationGuard, config.diagnostics);
        LOGGER.info(
                "Configured patches: EMI Compat/Spell Engine={}, EMI Loot/Tumbleweed={}, Obscure API tooltip={}, EMI repair ingredient={}",
                config.emiCompatSpellEngine,
                config.emiLootTumbleweed,
                config.obscureTooltipNullFood,
                config.emiRepairEmptyIngredient
        );
    }

    public static void logPatchOnce(String patchId, String message) {
        if (LOGGED_PATCHES.add(patchId)) {
            LOGGER.info("[{}] {}", patchId, message);
        }
    }

    public SmoothFix() {
    }
}
