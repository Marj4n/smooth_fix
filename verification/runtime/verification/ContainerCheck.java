package verification;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.*;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import org.marj4n.smooth_fix.benchmark.ContainerSlots;
import java.util.*;
public final class ContainerCheck {
    private static class MixedHandler extends ScreenHandler {
        MixedHandler(ServerPlayerEntity player,SimpleInventory loot){super(null,123);
            addSlot(new Slot(player.getInventory(),0,0,0));addSlot(new Slot(loot,0,0,0));addSlot(new Slot(loot,1,0,0));
            addSlot(new Slot(loot,2,0,0){@Override public boolean canTakeItems(net.minecraft.entity.player.PlayerEntity p){return false;}});
            addSlot(new Slot(player.getInventory(),1,0,0));}
        @Override public boolean canUse(net.minecraft.entity.player.PlayerEntity player){return true;}
        @Override public ItemStack quickMove(net.minecraft.entity.player.PlayerEntity player,int slot){return ItemStack.EMPTY;}
    }
    public static void run(ServerPlayerEntity player){var loot=new SimpleInventory(3);loot.setStack(1,new ItemStack(Items.IRON_INGOT,16));loot.setStack(2,new ItemStack(Items.DIAMOND));var handler=new MixedHandler(player,loot);
        if(!ContainerSlots.loot(handler,player).equals(List.of(2)))throw new AssertionError("empty/player/locked slots selected");
        loot.setStack(1,ItemStack.EMPTY);if(!ContainerSlots.loot(handler,player).isEmpty())throw new AssertionError("depleted slots selected");
        loot.clear();if(ContainerSlots.hasItems(loot))throw new AssertionError("empty container accepted");
        System.out.println("SMOOTHFIX_VERIFY_PASS mixed native screen handler selects only nonempty takeable container slot; rejects player, locked, empty and depleted slots");
    }
}
