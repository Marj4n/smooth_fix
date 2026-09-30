package org.marj4n.smooth_fix;

import net.fabricmc.api.ModInitializer;
import org.marj4n.smooth_fix.config.SmoothFixConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class SmoothFix implements ModInitializer {
    public static final String MOD_ID = "smooth_fix";
    public static final String MOD_NAME = "Smooth Fix";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);

    private static final Set<String> LOGGED_PATCHES = ConcurrentHashMap.newKeySet();

    @Override
    public void onInitialize() {
        SmoothFixConfig config = SmoothFixConfig.get();

        LOGGER.info("{} initialized.", MOD_NAME);
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
