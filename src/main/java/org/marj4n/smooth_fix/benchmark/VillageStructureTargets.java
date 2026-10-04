package org.marj4n.smooth_fix.benchmark;

import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.gen.structure.Structure;
import java.util.*;

/** Resolve the default village workload against the world's actual registry/data packs. */
public final class VillageStructureTargets {
    private VillageStructureTargets() { }
    public static boolean villagePath(String path){
        for(String token:path.split("[/_.-]"))if(token.equals("village") || token.equals("villages"))return true;
        return false;
    }
    public static RegistryEntryList<Structure> resolve(ServerWorld world){
        var registry=world.getRegistryManager().get(RegistryKeys.STRUCTURE);
        Set<RegistryEntry<Structure>> targets=new LinkedHashSet<>();
        registry.streamTagsAndEntries().forEach(pair->{if(villagePath(pair.getFirst().id().getPath()))pair.getSecond().forEach(targets::add);});
        registry.streamEntries().forEach(entry->{if(villagePath(entry.registryKey().getValue().getPath()))targets.add(entry);});
        // A village structure set can replace vanilla members with mod IDs lacking village tags.
        for(var set:world.getChunkManager().getStructurePlacementCalculator().getStructureSets())
            if(set.getKey().map(key->villagePath(key.getValue().getPath())).orElse(false))
                for(var member:set.value().structures())targets.add(member.structure());
        return RegistryEntryList.of(new ArrayList<>(targets));
    }
}
