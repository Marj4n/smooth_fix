package org.marj4n.smooth_fix.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.marj4n.smooth_fix.SmoothFix;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Small early-load config used by the mixin plugin.
 *
 * Accepted fixes default to enabled; experimental R2 adapters remain opt-in until
 * fullpack correctness and performance acceptance. Patches can be disabled independently.
 */
public final class SmoothFixConfig {
    private static final Gson GSON =
            new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private static final Path CONFIG_PATH =
            FabricLoader.getInstance().getConfigDir().resolve("smooth_fix.json");

    private static volatile SmoothFixConfig instance;

    public boolean emiCompatSpellEngine = true;
    public boolean emiLootTumbleweed = true;
    public boolean obscureTooltipNullFood = true;
    public boolean emiRepairEmptyIngredient = true;
    public boolean dedicatedServerClientMixinGuard = true;
    public boolean invalidEntityRotationGuard = true;
    public boolean diagnostics = true;

    /** Own diagnostic JSON only; never applies to Minecraft saves/network/chunk jobs. */
    public boolean backgroundReportIo = true;

    /** Experimental, opt-in frame-pressure deferral of noncritical diagnostic writes. */
    public boolean adaptiveReportScheduling = false;

    public int reportQueueCapacity = 4;
    public int reportQueueMiB = 32;
    public int reportMaximumDeferralMillis = 250;
    public boolean hideInventoryAttributesButton = true;
    public boolean hideRedundantAccessoryButtons = true;
    public boolean accessoriesLegacyRecoveryFix = true;

    /** R2 major gates; experimental particle work remains off until differential/runtime approval. */
    public boolean entityOptimization = true;

    public boolean particleOptimization = false;
    public boolean chunkEfficiency = true;
    public boolean memoryOptimization = true;
    public boolean profilingTelemetry = true;

    /** Benchmark-only stack sampling; frame/tick counters remain available when disabled. */
    public boolean benchmarkStackSampling = true;

    /** Experimental: noise math parity passed; complete saved-world parity is unresolved. */
    public boolean betterCavesNoiseAllocationFix = false;

    /** Experimental audited Smooth Bridge/Kilt 1.0.0 adapter; fullpack gain not accepted. */
    public boolean kiltRenderContextAllocationFix = false;

    /** Experimental bounded serialization cache; full reload/lifecycle coverage is incomplete. */
    public boolean immutableModelVariantCache = false;

    /** Experimental: closes fully consumed recipe streams in audited Sortilege 9.0 only. */
    public boolean sortilegeReloadStreamClosureFix = false;

    /** Experimental missing Forge-side ModernFix cache bridge under audited Kilt; OFF pending lifecycle validation. */
    public boolean kiltModernFixRegistryCacheBridge = false;

    public boolean modelRotationAllocationFix = true;
    public boolean bewitchmentSigilSearchFix = true;
    public boolean saintsDragonEntityScanFix = true;
    public boolean saintsDragonSoundScanFix = true;
    public boolean resourceBarTemperatureLookupFix = true;

    /** Laptop opt-in: target discovery is cached for at most one simulation tick. */
    public boolean presenceFootstepTargetCacheFix = false;

    public boolean trinketsRegexAllocationFix = true;
    public boolean trinketsSnapshotAllocationFix = true;
    public boolean accessoriesEmptyBufferFlushFix = true;
    public boolean emiSearchSafetyFix = true;
    public boolean betterMobCombatEquipmentSlotFix = true;

    /** Suppress Core's optional shader-addon suggestion; does not unload installed mods. */
    public boolean enhancedCelestialsShaderPromptSuppression = true;

    /** Recording budget only; does not change the FPS cap or game settings. */
    public int diagnosticTargetFps = 120;

    public boolean confirmedTeleportAnchorFix = true;

    /** Zero leaves upstream worker sizing untouched. Does not resize C2ME or Sodium pools. */
    public int backgroundWorkerLimit = 2;

    public static SmoothFixConfig get() {
        SmoothFixConfig current = instance;
        if (current != null) {
            return current;
        }

        synchronized (SmoothFixConfig.class) {
            current = instance;
            if (current == null) {
                current = load();
                instance = current;
            }
            return current;
        }
    }

    private static SmoothFixConfig load() {
        if (!Files.isRegularFile(CONFIG_PATH)) {
            SmoothFixConfig defaults = new SmoothFixConfig();
            save(defaults);
            return defaults;
        }

        try (Reader reader = Files.newBufferedReader(CONFIG_PATH, StandardCharsets.UTF_8)) {
            SmoothFixConfig loaded = GSON.fromJson(reader, SmoothFixConfig.class);
            if (loaded == null) {
                throw new IOException("Config file contained no object");
            }
            return loaded;
        } catch (Exception exception) {
            SmoothFix.LOGGER.warn(
                    "Could not read {}. Falling back to safe defaults.", CONFIG_PATH, exception);
            return new SmoothFixConfig();
        }
    }

    private static void save(SmoothFixConfig config) {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH, StandardCharsets.UTF_8)) {
                GSON.toJson(config, writer);
            }
        } catch (Exception exception) {
            SmoothFix.LOGGER.warn("Could not create default {}.", CONFIG_PATH, exception);
        }
    }

    private SmoothFixConfig() {}
}
