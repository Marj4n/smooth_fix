package org.marj4n.smooth_fix.benchmark;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.inventory.Inventory;
import net.minecraft.entity.player.PlayerEntity;
import java.util.*;
/** Actual container slots, including mod handlers that do not place player slots last. */
public final class ContainerSlots {
    public static List<Integer> loot(ScreenHandler handler,PlayerEntity player) {
        List<Integer> result=new ArrayList<>();
        for(int i=0;i<handler.slots.size();i++){
            var slot=handler.getSlot(i);
            if(slot.inventory!=player.getInventory() && slot.hasStack() && slot.canTakeItems(player))result.add(i);
        }
        return result;
    }
    public static boolean hasItems(Inventory inventory){for(int i=0;i<inventory.size();i++)if(!inventory.getStack(i).isEmpty())return true;return false;}
    private ContainerSlots(){}
}
