package org.marj4n.smooth_fix.benchmark;

import net.minecraft.server.network.ServerPlayerEntity;
import org.marj4n.smooth_fix.SmoothFix;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Optional PlayerAbilityLib bridge without a hard runtime dependency. */
final class BenchmarkPlayerAbilities {
    private static final String SOURCE_NAMESPACE = "smooth_fix";
    private static final String SOURCE_PATH = "advanced_benchmark";
    private static Object source;
    private static Object allowFlying;
    private static Object flying;
    private static Object invulnerable;
    private static boolean resolved;

    private BenchmarkPlayerAbilities() { }

    static void grantTemporary(ServerPlayerEntity player, boolean needsInvulnerability) {
        boolean pal = grantWithPal(player, needsInvulnerability);
        // Keep vanilla fields synchronized even when PAL is absent. When PAL is present these values
        // match its granted result and sendAbilitiesUpdate publishes them to the client.
        player.getAbilities().allowFlying = true;
        player.getAbilities().flying = true;
        if (needsInvulnerability) player.getAbilities().invulnerable = true;
        player.sendAbilitiesUpdate();
        if (pal) SmoothFix.logPatchOnce("benchmark_pal", "Advanced benchmark uses a temporary PlayerAbilityLib ability source.");
    }

    static void revokeTemporary(ServerPlayerEntity player) {
        resolve();
        if (source == null) return;
        revoke(player, flying);
        revoke(player, allowFlying);
        revoke(player, invulnerable);
    }

    private static boolean grantWithPal(ServerPlayerEntity player, boolean needsInvulnerability) {
        resolve();
        if (source == null || allowFlying == null) return false;
        grant(player, allowFlying);
        if (flying != null) grant(player, flying);
        if (needsInvulnerability && invulnerable != null) grant(player, invulnerable);
        return true;
    }

    private static void resolve() {
        if (resolved) return;
        resolved = true;
        try {
            Class<?> pal = Class.forName("io.github.ladysnake.pal.Pal");
            Class<?> vanilla = Class.forName("io.github.ladysnake.pal.VanillaAbilities");
            Method getSource = pal.getMethod("getAbilitySource", String.class, String.class);
            source = getSource.invoke(null, SOURCE_NAMESPACE, SOURCE_PATH);
            allowFlying = field(vanilla, "ALLOW_FLYING");
            flying = field(vanilla, "FLYING");
            invulnerable = field(vanilla, "INVULNERABLE");
        } catch (Throwable ignored) {
            source = null;
            allowFlying = null;
            flying = null;
            invulnerable = null;
        }
    }

    private static Object field(Class<?> type, String name) throws ReflectiveOperationException {
        Field field = type.getField(name);
        return field.get(null);
    }

    private static void grant(ServerPlayerEntity player, Object ability) {
        if (source == null || ability == null) return;
        invokeSource("grantTo", player, ability);
    }

    private static void revoke(ServerPlayerEntity player, Object ability) {
        if (source == null || ability == null) return;
        invokeSource("revokeFrom", player, ability);
    }

    private static void invokeSource(String name, ServerPlayerEntity player, Object ability) {
        try {
            for (Method method : source.getClass().getMethods()) {
                if (!method.getName().equals(name) || method.getParameterCount() != 2) continue;
                method.invoke(source, player, ability);
                return;
            }
        } catch (Throwable exception) {
            SmoothFix.LOGGER.debug("PlayerAbilityLib benchmark bridge {} failed", name, exception);
        }
    }
}
