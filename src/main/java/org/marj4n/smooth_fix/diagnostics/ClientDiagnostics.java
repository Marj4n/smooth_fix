package org.marj4n.smooth_fix.diagnostics;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.text.Text;
import net.minecraft.client.MinecraftClient;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import org.marj4n.smooth_fix.SmoothFix;
import org.marj4n.smooth_fix.config.SmoothFixConfig;

/** This entrypoint is client-only; no client class is loaded by the server initializer. */
public final class ClientDiagnostics implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        if (SmoothFixConfig.get().saintsDragonEntityScanFix && net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("saintsdragons")) org.marj4n.smooth_fix.performance.RelevantDragonEntities.install();
        if (!SmoothFixConfig.get().diagnostics) return;
        org.marj4n.smooth_fix.benchmark.ClientBenchmark.install();
        org.marj4n.smooth_fix.benchmark.AdvancedClient.install();
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                ClientCommandManager.literal("smoothfixc").then(ClientCommandManager.literal("report").executes(context -> {
                    try {
                        String path = MemoryReport.write("client", MemoryReport.snapshot("client")).toString();
                        context.getSource().sendFeedback(Text.literal("Smooth Fix client RAM report: " + path));
                        return 1;
                    } catch (Exception exception) {
                        SmoothFix.LOGGER.error("Could not write Smooth Fix client report", exception);
                        context.getSource().sendError(Text.literal("Could not write report; check latest.log."));
                        return 0;
                    }
                }))
                        .then(ClientCommandManager.literal("profile")
                                .executes(context -> profile(context.getSource(), 120))
                                .then(ClientCommandManager.argument("seconds", IntegerArgumentType.integer(10,120))
                                        .executes(context -> profile(context.getSource(), IntegerArgumentType.getInteger(context,"seconds")))))
                        .then(ClientCommandManager.literal("stopstress").executes(context -> { org.marj4n.smooth_fix.benchmark.ClientBenchmark.stop(MinecraftClient.getInstance()); return 1; }))
                        .then(ClientCommandManager.literal("stopprofile").executes(context -> {
                            ClientFrameProfiler.stop();
                            context.getSource().sendFeedback(Text.literal("Smooth Fix frame profile will be saved."));
                            return 1;
                        }))));
    }

    private static int profile(net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource source, int seconds) {
        if (!ClientFrameProfiler.start(MinecraftClient.getInstance(), seconds)) {
            source.sendError(Text.literal("Join a world first; only one frame profile can run at a time."));
            return 0;
        }
        source.sendFeedback(Text.literal("Smooth Fix recording frame time for " + seconds + " seconds. Visit the village where FPS drops."));
        return 1;
    }
}
