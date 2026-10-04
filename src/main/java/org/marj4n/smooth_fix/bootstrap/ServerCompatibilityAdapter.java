package org.marj4n.smooth_fix.bootstrap;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.LanguageAdapter;
import net.fabricmc.loader.api.LanguageAdapterException;
import net.fabricmc.loader.api.ModContainer;
import org.marj4n.smooth_fix.SmoothFix;
import org.marj4n.smooth_fix.config.SmoothFixConfig;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * Fabric constructs language adapters before registering mixin configurations.
 * A preLaunch/main entrypoint is too late to repair another mod's config plugin.
 * No mod files, entrypoints, dependencies, or client-side behavior are removed.
 */
public final class ServerCompatibilityAdapter implements LanguageAdapter {
    private static final Map<String, Target> CLIENT_CONFIGS = Map.of(
            "shut-up-mcd", new Target("1.2.0", Set.of("shut-up-mcd.client.mixins.json")),
            "enhancedblockentities", new Target("0.13.2", Set.of("enhancedblockentities.mixins.json"))
    );
    private record Target(String version, Set<String> configs) { }

    public ServerCompatibilityAdapter() {
        FabricLoader loader = FabricLoader.getInstance();
        if (loader.getEnvironmentType() != EnvType.SERVER
                || !SmoothFixConfig.get().dedicatedServerClientMixinGuard) {
            return;
        }
        for (Map.Entry<String, Target> entry : CLIENT_CONFIGS.entrySet()) {
            loader.getModContainer(entry.getKey()).ifPresent(mod -> {
                if (mod.getMetadata().getVersion().getFriendlyString().equals(entry.getValue().version())) {
                    repair(mod, entry.getValue().configs());
                } else {
                    SmoothFix.LOGGER.warn("Server client-hook guard was audited for {} {}, found {}; leaving its metadata unchanged.",
                            entry.getKey(), entry.getValue().version(), mod.getMetadata().getVersion().getFriendlyString());
                }
            });
        }
    }

    private static void repair(ModContainer mod, Set<String> configNames) {
        Object metadata = mod.getMetadata();
        // This is deliberately scoped to the inspected Fabric metadata schema.
        // Public ModMetadata exposes no setter for mixin environments.
        if (!metadata.getClass().getName().equals("net.fabricmc.loader.impl.metadata.V1ModMetadata")) {
            SmoothFix.LOGGER.warn("Server client-hook guard cannot inspect metadata for {}. No metadata changed.",
                    mod.getMetadata().getId());
            return;
        }
        try {
            Field mixinsField = metadata.getClass().getDeclaredField("mixins");
            mixinsField.setAccessible(true);
            for (Object entry : (Collection<?>) mixinsField.get(metadata)) {
                Field configField = entry.getClass().getDeclaredField("config");
                Field environmentField = entry.getClass().getDeclaredField("environment");
                configField.setAccessible(true);
                environmentField.setAccessible(true);
                String name = (String) configField.get(entry);
                if (!configNames.contains(name)) {
                    continue;
                }
                Object clientEnvironment = null;
                for (Object candidate : environmentField.getType().getEnumConstants()) {
                    if (((Enum<?>) candidate).name().equals("CLIENT")) {
                        clientEnvironment = candidate;
                        break;
                    }
                }
                if (clientEnvironment == null) {
                    throw new IllegalStateException("Fabric CLIENT mixin environment is unavailable");
                }
                environmentField.set(entry, clientEnvironment);
                SmoothFix.LOGGER.info("[server_client_hooks] Corrected {} / {} to client-side mixin scope; mod remains installed.",
                        mod.getMetadata().getId(), name);
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            SmoothFix.LOGGER.error("Cannot repair client mixin scope for {} on this Fabric Loader. Original JAR is unchanged.",
                    mod.getMetadata().getId(), exception);
        }
    }

    @Override
    public <T> T create(ModContainer mod, String value, Class<T> type) throws LanguageAdapterException {
        return LanguageAdapter.getDefault().create(mod, value, type);
    }
}
