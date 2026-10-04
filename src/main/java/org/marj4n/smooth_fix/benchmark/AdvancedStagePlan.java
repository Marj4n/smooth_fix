package org.marj4n.smooth_fix.benchmark;

import java.util.List;

/** Pure stage plan shared by runtime code and the transition verification fixture. */
public final class AdvancedStagePlan {
    public record Stage(String name, String mode, int measurementSeconds, int timeoutSeconds, boolean requiresVerification) { }

    private static final List<Stage> STAGES = List.of(
            new Stage("real_terrain_loaded", "route", 12, 45, false),
            new Stage("cold_chunk_generation", "cold_route", 10, 60, false),
            new Stage("structure_scan", "structure", 12, 45, false),
            new Stage("vanilla_chest_loot", "container", 8, 30, true),
            new Stage("lootr_chest_loot", "lootr", 8, 30, true),
            new Stage("inventory_hotbar", "inventory", 8, 30, true),
            new Stage("emi_search_recipe", "emi", 8, 40, true),
            new Stage("combat_hit", "combat", 10, 30, true),
            new Stage("tnt_destruction", "tnt", 10, 30, true)
    );

    private AdvancedStagePlan() { }

    public static List<Stage> stages() { return STAGES; }

    public static int next(int current) {
        int candidate = current + 1;
        return candidate < STAGES.size() ? candidate : -1;
    }
}
