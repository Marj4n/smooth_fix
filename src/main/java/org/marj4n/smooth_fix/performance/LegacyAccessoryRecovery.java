package org.marj4n.smooth_fix.performance;

import net.minecraft.inventory.SimpleInventory;
import net.minecraft.nbt.NbtList;

/** Native removed-slot recovery needs room for every saved entry, including cosmetics. */
public final class LegacyAccessoryRecovery {
    private LegacyAccessoryRecovery() {}

    public static SimpleInventory read(SimpleInventory ignored, NbtList saved) {
        SimpleInventory recovered = new SimpleInventory(saved.size());
        recovered.readNbtList(saved);
        return recovered;
    }
}
