package org.marj4n.smooth_fix.performance;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.ClickableWidget;
import org.marj4n.smooth_fix.config.SmoothFixConfig;

/** Remove exact redundant Accessories/Curios entrypoints; keep side tabs and key bindings. */
public final class InventoryAccessoryButtons {
    private InventoryAccessoryButtons() {}

    public static void install() {
        var loader = FabricLoader.getInstance();
        if (!loader.getModContainer("accessories")
                .map(
                        m ->
                                m.getMetadata()
                                        .getVersion()
                                        .getFriendlyString()
                                        .equals("1.0.0-beta.48+1.20.1"))
                .orElse(false)) return;
        ScreenEvents.AFTER_INIT.register(
                (client, screen, w, h) -> {
                    if (!(screen instanceof InventoryScreen)
                            || !SmoothFixConfig.get().hideRedundantAccessoryButtons) return;
                    ClickableWidget nativeButton = null;
                    for (var field : InventoryScreen.class.getDeclaredFields())
                        if (field.getName().contains("accessoryButton")
                                && ClickableWidget.class.isAssignableFrom(field.getType()))
                            try {
                                field.setAccessible(true);
                                nativeButton = (ClickableWidget) field.get(screen);
                            } catch (ReflectiveOperationException error) {
                                org.marj4n.smooth_fix.SmoothFix.LOGGER.warn(
                                        "Cannot inspect audited Accessories inventory button",
                                        error);
                            }
                    final var target = nativeButton;
                    Screens.getButtons(screen)
                            .removeIf(
                                    button ->
                                            button == target
                                                    || button.getClass()
                                                            .getName()
                                                            .equals(
                                                                    "top.theillusivec4.curios.client.gui.CuriosButton"));
                });
    }
}
