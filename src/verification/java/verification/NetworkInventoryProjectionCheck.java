package verification;

import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.network.PacketByteBuf;
import io.netty.buffer.Unpooled;
import org.marj4n.smooth_fix.benchmark.*;

public final class NetworkInventoryProjectionCheck {
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        SharedConstants.createGameVersion();
        Bootstrap.initialize();
        var a = new SimpleInventory(4);
        var stack = new ItemStack(Items.DIAMOND_SWORD);
        NbtCompound tag = new NbtCompound();
        tag.putInt("Aa", 1);
        tag.putInt("BB", 2);
        NbtCompound shared = new NbtCompound();
        shared.putString("value", "all shared fields retained");
        tag.put("modded:shared", shared);
        stack.setNbt(tag);
        a.setStack(0, stack);
        a.setStack(3, new ItemStack(Items.STONE, 42));
        var before = NetworkInventoryProjection.capture(a);
        require(before.size() == 4, "Empty slots omitted");
        var b = new SimpleInventory(4);
        var other = stack.copy();
        NbtCompound reordered = new NbtCompound();
        reordered.put("modded:shared", shared.copy());
        reordered.putInt("BB", 2);
        reordered.putInt("Aa", 1);
        other.setNbt(reordered);
        b.setStack(0, other);
        b.setStack(3, new ItemStack(Items.STONE, 42));
        require(
                CanonicalNbtHash.sha256(before)
                        .equals(CanonicalNbtHash.sha256(NetworkInventoryProjection.capture(b))),
                "Wire NBT ordering changed semantic comparison");
        b.getStack(0).getOrCreateNbt().getCompound("modded:shared").putString("value", "changed");
        require(
                !CanonicalNbtHash.sha256(before)
                        .equals(CanonicalNbtHash.sha256(NetworkInventoryProjection.capture(b))),
                "Changed shared modded tag ignored");
        b.setStack(0, stack.copy());
        b.getStack(3).setCount(41);
        require(!before.equals(NetworkInventoryProjection.capture(b)), "Changed count ignored");
        b.setStack(3, new ItemStack(Items.DIRT, 42));
        require(!before.equals(NetworkInventoryProjection.capture(b)), "Changed item ignored");
        var buffer = new PacketByteBuf(Unpooled.buffer());
        try {
            buffer.writeItemStack(ItemStack.EMPTY);
            buffer.writeByte(7);
            var decode =
                    NetworkInventoryProjection.class.getDeclaredMethod(
                            "decodeWrittenStack", PacketByteBuf.class);
            decode.setAccessible(true);
            try {
                decode.invoke(null, buffer);
                throw new AssertionError("Unexpected wire data silently ignored");
            } catch (java.lang.reflect.InvocationTargetException error) {
                require(
                        error.getCause() instanceof IllegalStateException,
                        "Wrong malformed-envelope failure");
            }
        } finally {
            require(buffer.release(), "Owned byte buffer not released");
        }
        var unsupported =
                java.util.Map.<String, Object>of(
                        "inventoryProjectionAvailable",
                        false,
                        "inventoryHashFormat",
                        "network-item-stack-v2",
                        "inventorySha256",
                        "unavailable");
        require(
                Boolean.FALSE.equals(RecoverySnapshot.compare(unsupported, unsupported).get("all")),
                "Two unavailable codecs falsely passed");
        System.out.println(
                "PASS: real vanilla item encoder AND detached receiver roundtrip/stability, all slots, complete shared modded NBT keys, canonical NBT order, changed item/count/shared tags, unsupported-envelope rejection and buffer release. Actual transformed Forge/Fabric peer acceptance remains pending.");
    }
}
