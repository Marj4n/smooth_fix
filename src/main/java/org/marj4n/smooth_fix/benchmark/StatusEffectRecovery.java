package org.marj4n.smooth_fix.benchmark;

import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/** Journal complete vanilla instance NBT, including hidden effects and factor data. */
public final class StatusEffectRecovery {
    private StatusEffectRecovery() {}

    public static NbtList capture(Collection<StatusEffectInstance> effects) throws IOException {
        List<StatusEffectInstance> sorted = new ArrayList<>(effects);
        // Unknown instance subclasses can carry source/entity fields absent from NBT.
        // Refuse BEFORE the benchmark mutates the player rather than losing them.
        for (var effect : sorted) validateEntireHiddenChain(effect);
        sorted.sort(
                Comparator.comparing(
                        effect ->
                                Registries.STATUS_EFFECT.getId(effect.getEffectType()).toString()));
        NbtList result = new NbtList();
        for (var effect : sorted) result.add(effect.writeNbt(new NbtCompound()));
        return result;
    }

    private static volatile java.lang.reflect.Field hiddenLink;

    /** Resolve by exact field TYPE, never an obfuscated name; unknown layouts fail before mutation. */
    private static java.lang.reflect.Field hiddenLink() throws IOException {
        var cached = hiddenLink;
        if (cached != null) return cached;
        var fields =
                java.util.Arrays.stream(StatusEffectInstance.class.getDeclaredFields())
                        .filter(
                                f ->
                                        !java.lang.reflect.Modifier.isStatic(f.getModifiers())
                                                && f.getType() == StatusEffectInstance.class)
                        .toList();
        if (fields.size() != 1)
            throw new IOException(
                    "Unsupported status-effect hidden-link layout; refusing incomplete journal");
        try {
            cached = fields.get(0);
            cached.setAccessible(true);
            hiddenLink = cached;
            return cached;
        } catch (RuntimeException failure) {
            throw new IOException("Cannot verify complete hidden-effect chain", failure);
        }
    }

    private static void validateEntireHiddenChain(StatusEffectInstance effect) throws IOException {
        var visited = new java.util.IdentityHashMap<StatusEffectInstance, Boolean>();
        int depth = 0;
        while (effect != null) {
            if (effect.getClass() != StatusEffectInstance.class)
                throw new IOException(
                        "Benchmark cannot safely journal effect-instance subtype: "
                                + effect.getClass().getName());
            if (++depth > 64 || visited.put(effect, Boolean.TRUE) != null)
                throw new IOException("Unsupported cyclic or excessive hidden-effect chain");
            try {
                effect = (StatusEffectInstance) hiddenLink().get(effect);
            } catch (IllegalAccessException failure) {
                throw new IOException("Cannot inspect hidden-effect chain", failure);
            }
        }
    }

    public static List<StatusEffectInstance> decode(NbtList journal) throws IOException {
        List<StatusEffectInstance> result = new ArrayList<>();
        for (int i = 0; i < journal.size(); i++) {
            var effect = StatusEffectInstance.fromNbt(journal.getCompound(i));
            if (effect == null)
                throw new IOException(
                        "Unknown status effect in recovery journal; refusing partial restoration");
            result.add(effect);
        }
        return result;
    }
}
