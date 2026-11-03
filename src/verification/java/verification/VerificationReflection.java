package verification;

import java.util.*;

/** Checked reflective views for test-only private state; no unchecked casts or warning suppression. */
public final class VerificationReflection {
    private VerificationReflection() {}

    /** Fake-connection SERVER regression fixture only; actual client/network acceptance is tested separately in Minecraft. */
    public static void advertiseBaseline(net.minecraft.server.network.ServerPlayerEntity player)
            throws ReflectiveOperationException {
        Object addon =
                Class.forName("net.fabricmc.fabric.impl.networking.server.ServerNetworkingImpl")
                        .getMethod(
                                "getAddon",
                                net.minecraft.server.network.ServerPlayNetworkHandler.class)
                        .invoke(null, player.networkHandler);
        var channels = addon.getClass().getSuperclass().getDeclaredField("sendableChannels");
        channels.setAccessible(true);
        addToSet(
                channels.get(addon),
                org.marj4n.smooth_fix.benchmark.BenchmarkProtocol.BASELINE_REQUEST);
    }

    public static void finishFixturePreflight(
            net.minecraft.server.network.ServerPlayerEntity player)
            throws ReflectiveOperationException {
        Class<?> controller = org.marj4n.smooth_fix.benchmark.AdvancedBenchmark.class;
        var field = controller.getDeclaredField("baselineGate");
        field.setAccessible(true);
        org.marj4n.smooth_fix.benchmark.BenchmarkBaselineGate<?> gate =
                org.marj4n.smooth_fix.benchmark.BenchmarkBaselineGate.class.cast(field.get(null));
        var pending = gate.getClass().getDeclaredField("pending");
        pending.setAccessible(true);
        org.marj4n.smooth_fix.benchmark.BenchmarkBaselineGate.Request<?> request =
                org.marj4n.smooth_fix.benchmark.BenchmarkBaselineGate.Request.class.cast(
                        pending.get(gate));
        if (request == null)
            throw new AssertionError("Fixture start did not request an original-state handshake");
        var accepted = gate.consume(player.getUuid(), request.run(), System.nanoTime());
        if (accepted == null) throw new AssertionError("Fixture original-state ACK rejected");
        var start =
                controller.getDeclaredMethod(
                        "startVerified",
                        net.minecraft.server.network.ServerPlayerEntity.class,
                        org.marj4n.smooth_fix.benchmark.AdvancedPlan.class,
                        UUID.class,
                        Map.class);
        start.setAccessible(true);
        start.invoke(
                null,
                player,
                org.marj4n.smooth_fix.benchmark.AdvancedPlan.class.cast(accepted.payload()),
                accepted.run(),
                org.marj4n.smooth_fix.benchmark.RecoverySnapshot.captureLocal(
                        player, player.interactionManager.getGameMode().getId()));
    }

    public static List<Map<?, ?>> maps(Object value) {
        if (!(value instanceof List<?> list)) throw new AssertionError("Expected report list");
        List<Map<?, ?>> result = new ArrayList<>();
        for (Object row : list) {
            if (!(row instanceof Map<?, ?> map)) throw new AssertionError("Expected report map");
            result.add(map);
        }
        return result;
    }

    public static <T> List<T> list(Object value, Class<T> type) {
        if (!(value instanceof List<?> list)) throw new AssertionError("Expected list");
        List<T> result = new ArrayList<>();
        for (Object item : list) result.add(type.cast(item));
        return result;
    }

    public static void addToSet(Object value, Object element) throws ReflectiveOperationException {
        if (!(value instanceof Set<?>)) throw new AssertionError("Expected set");
        Set.class.getMethod("add", Object.class).invoke(value, element);
    }

    public static void addToList(Object value, Object element) throws ReflectiveOperationException {
        if (!(value instanceof List<?>)) throw new AssertionError("Expected list");
        List.class.getMethod("add", Object.class).invoke(value, element);
    }
}
