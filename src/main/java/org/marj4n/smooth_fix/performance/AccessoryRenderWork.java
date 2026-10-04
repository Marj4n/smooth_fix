package org.marj4n.smooth_fix.performance;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.inventory.Inventory;
import org.marj4n.smooth_fix.SmoothFix;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.Map;

/** Inspect live slots; never cache equipment or suppress an accessory renderer. */
public final class AccessoryRenderWork {
    private static final class Access {
        static final MethodHandle CONTAINERS, EQUIPMENT, COSMETICS;
        static {
            MethodHandle containers = null, equipment = null, cosmetics = null;
            try {
                var lookup = MethodHandles.publicLookup();
                var capability = Class.forName("io.wispforest.accessories.api.AccessoriesCapability");
                var container = Class.forName("io.wispforest.accessories.api.AccessoriesContainer");
                containers = lookup.unreflect(capability.getMethod("getContainers"));
                equipment = lookup.unreflect(container.getMethod("getAccessories"));
                cosmetics = lookup.unreflect(container.getMethod("getCosmeticAccessories"));
            } catch (ReflectiveOperationException exception) {
                SmoothFix.LOGGER.warn("Accessories slot inspection unavailable; retaining upstream buffer flush", exception);
            }
            CONTAINERS = containers; EQUIPMENT = equipment; COSMETICS = cosmetics;
        }
    }
    private static volatile boolean inspectionFailed;
    private AccessoryRenderWork() { }

    public static void drawIfEquipped(VertexConsumerProvider.Immediate provider, Object capability) {
        if (inspectionFailed || Access.CONTAINERS == null || capability == null) { provider.draw(); return; }
        boolean equipped = true;
        try {
            equipped = false;
            var containers = (Map<?, ?>) Access.CONTAINERS.invoke(capability);
            for (Object container : containers.values()) {
                Inventory actual = (Inventory) Access.EQUIPMENT.invoke(container);
                Inventory cosmetic = (Inventory) Access.COSMETICS.invoke(container);
                if (hasItems(actual) || hasItems(cosmetic)) { equipped = true; break; }
            }
        } catch (Throwable exception) {
            if (exception instanceof Error error) throw error;
            equipped = true; inspectionFailed = true;
            SmoothFix.LOGGER.warn("Accessories slot inspection failed; retaining upstream buffer flush", exception);
        }
        if (equipped) provider.draw();
    }
    private static boolean hasItems(Inventory inventory) {
        for (int slot = 0; slot < inventory.size(); slot++) if (!inventory.getStack(slot).isEmpty()) return true;
        return false;
    }
}
