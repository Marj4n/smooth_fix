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
 * The config intentionally contains only patch toggles. Every fix defaults to enabled,
 * but can be disabled independently if an upstream mod eventually fixes the problem.
 */
public final class SmoothFixConfig {
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    private static final Path CONFIG_PATH = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("smooth_fix.json");

    private static volatile SmoothFixConfig instance;

    public boolean emiCompatSpellEngine = true;
    public boolean emiLootTumbleweed = true;
    public boolean obscureTooltipNullFood = true;
    public boolean emiRepairEmptyIngredient = true;
    public boolean dedicatedServerClientMixinGuard = true;
    public boolean invalidEntityRotationGuard = true;
    public boolean diagnostics = true;
    public boolean modelRotationAllocationFix = true;
    public boolean bewitchmentSigilSearchFix = true;
    public boolean saintsDragonEntityScanFix = true;
    public boolean trinketsRegexAllocationFix = true;
    public boolean trinketsSnapshotAllocationFix = true;
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
                    "Could not read {}. Falling back to safe defaults.",
                    CONFIG_PATH,
                    exception
            );
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

    private SmoothFixConfig() {
    }
}
