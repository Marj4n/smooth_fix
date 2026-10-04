package org.marj4n.smooth_fix.benchmark;

import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.*;
import java.util.*;

/** Editable scenarios, with strict bounds. Unknown modes are rejected before moving a player. */
public final class AdvancedPlan {
    public static final Set<String> MODES=Set.of("panorama","route","cold_route","structure","inventory","emi_search","emi_recipe","chest","lootr","combat","effects","bloodmoon","wither","tnt","break","weather","teleport","nether","end","save");
    public static final class Stage {
        public String name,mode,target="";
        public int count,seconds=60;
        public Stage() { }
        public Stage(String name,String mode,String target,int count) {this.name=name;this.mode=mode;this.target=target;this.count=count;}
    }
    public int structureRadiusChunks=16;
    public int structureSearchRadiusStepBlocks=10000;
    public int automaticallySelectedModStructures=3;
    public int optionalModStructureSearchSeconds=90;
    public List<String> emiQueries=new ArrayList<>(List.of("iron","@minecraft","#ingot","sword","@create","@bewitchment","/.*dragon.*/","zz_smoothfix_no_result"));
    public List<Stage> stages=new ArrayList<>(List.of(
            new Stage("real_world_panorama","panorama","",0),new Stage("loaded_terrain_route","route","",0),
            new Stage("new_terrain_route","cold_route","",0),new Stage("revisit_same_terrain","route","previous_cold",0),
            new Stage("real_village","structure","#minecraft:village",0),
            new Stage("natural_chest_or_fixture","chest","",0),new Stage("lootr_personal_chest","lootr","",0),
            new Stage("inventory_open_tooltips","inventory","",0),new Stage("emi_query_latency","emi_search","",0),
            new Stage("emi_recipe_navigation","emi_recipe","",0),
            new Stage("terrain_combat_32","combat","minecraft:husk",32),
            new Stage("terrain_effects_64","effects","minecraft:husk",64),
            new Stage("native_blood_moon","bloodmoon","minecraft:husk",64),
            new Stage("terrain_wither_10","wither","minecraft:wither",10),new Stage("terrain_tnt_16","tnt","",16),
            new Stage("native_block_breaks","break","",0),new Stage("rain_and_particles","weather","",0),
            new Stage("real_world_teleports","teleport","",0),new Stage("nether_terrain","nether","",0),
            new Stage("end_terrain","end","",0),new Stage("world_save","save","",0)));

    public static AdvancedPlan load() throws Exception {
        Path file=FabricLoader.getInstance().getConfigDir().resolve("smooth_fix/advanced_benchmark.json");
        var gson=new GsonBuilder().setPrettyPrinting().create();
        AdvancedPlan plan;
        if(Files.exists(file))plan=gson.fromJson(Files.readString(file),AdvancedPlan.class);
        else {plan=new AdvancedPlan();Files.createDirectories(file.getParent());Files.writeString(file,gson.toJson(plan));}
        if(plan==null || plan.stages==null || plan.stages.isEmpty() || plan.stages.size()>100)throw new IllegalArgumentException("1..100 stages required");
        if(plan.structureRadiusChunks<1 || plan.structureRadiusChunks>64 || plan.automaticallySelectedModStructures<0 || plan.automaticallySelectedModStructures>16)throw new IllegalArgumentException("Structure radius 1..64, auto selection 0..16");
        if(plan.structureSearchRadiusStepBlocks<100 || plan.structureSearchRadiusStepBlocks>1000000)throw new IllegalArgumentException("Structure search radius step 100..1000000 blocks");
        if(plan.optionalModStructureSearchSeconds<10 || plan.optionalModStructureSearchSeconds>600)throw new IllegalArgumentException("Optional structure search 10..600 seconds");
        for(Stage stage:plan.stages)if(stage==null || stage.name==null || stage.name.length()>100 || !MODES.contains(stage.mode)
                || stage.seconds<10 || stage.seconds>300 || stage.count<0 || stage.count>64 || stage.target==null || stage.target.length()>200
                || ((stage.mode.equals("wither") || stage.target.equals("minecraft:wither")) && stage.count>10) || (stage.mode.equals("tnt") && stage.count>32))throw new IllegalArgumentException("Invalid advanced stage");
        if(plan.emiQueries==null || plan.emiQueries.isEmpty() || plan.emiQueries.size()>32 || plan.emiQueries.stream().anyMatch(q->q==null || q.length()>128))throw new IllegalArgumentException("1..32 EMI queries, maximum 128 characters");
        return plan;
    }
}
