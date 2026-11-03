package org.marj4n.smooth_fix.benchmark;

import io.netty.buffer.Unpooled;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registries;

/** Compare complete stable native receiver-visible item payload; private save NBT is a separate server gate. */
public final class NetworkInventoryProjection {
    private NetworkInventoryProjection() {}

    public static NbtList capture(Inventory inventory) {
        NbtList result = new NbtList();
        PacketByteBuf buffer = new PacketByteBuf(Unpooled.buffer());
        try {
            for (int slot = 0; slot < inventory.size(); slot++) {
                // Simulate the REAL native receiver, including Kilt/custom readShareTag behavior.
                // These are detached stacks; the inventory's original stack is never decoded into.
                buffer.clear();
                buffer.writeItemStack(inventory.getStack(slot));
                ItemStack received = buffer.readItemStack();
                if (buffer.isReadable())
                    throw new IllegalStateException("Native item decoder left unread payload");
                buffer.clear();
                buffer.writeItemStack(received);
                NbtCompound projected = decodeWrittenStack(buffer);
                // A changing/non-idempotent codec cannot be used as a recovery equality oracle.
                buffer.clear();
                buffer.writeItemStack(received);
                ItemStack receivedAgain = buffer.readItemStack();
                if (buffer.isReadable())
                    throw new IllegalStateException("Repeated native decoder left unread payload");
                buffer.clear();
                buffer.writeItemStack(receivedAgain);
                if (!projected.equals(decodeWrittenStack(buffer)))
                    throw new IllegalStateException(
                            "Native item receiver projection is not stable");
                projected.putInt("Slot", slot);
                result.add(projected);
            }
            return result;
        } finally {
            buffer.release();
        }
    }

    /** Strict standard 1.20.1 envelope; native writeItemStack supplies ALL shared item NBT. */
    static NbtCompound decodeWrittenStack(PacketByteBuf buffer) {
        NbtCompound result = new NbtCompound();
        boolean present = buffer.readBoolean();
        result.putBoolean("present", present);
        if (present) {
            int rawId = buffer.readVarInt();
            if (rawId < 0 || rawId >= Registries.ITEM.size())
                throw new IllegalStateException("Unrecognized network item ID");
            result.putString("id", Registries.ITEM.getId(Registries.ITEM.get(rawId)).toString());
            result.putInt("Count", buffer.readByte());
            NbtCompound tag = buffer.readNbt();
            if (tag != null) result.put("tag", tag);
        }
        if (buffer.isReadable())
            throw new IllegalStateException(
                    "Unsupported modified item-wire envelope; refusing incomplete peer comparison");
        return result;
    }
}
