package org.marj4n.smooth_fix.benchmark;

/** Player-facing descriptions shared by server chat and the client HUD. */
public final class AdvancedStageInfo {
    public static String task(String mode) {
        return switch (mode) {
            case "panorama" -> "Observing terrain, entity and particle rendering";
            case "route" -> "Walking through loaded terrain";
            case "cold_route" -> "Walking into new terrain; measuring chunk generation";
            case "structure" -> "Finding a structure, then walking around the surrounding area";
            case "chest" -> "Opening chests and transferring loot";
            case "lootr" -> "Opening personal Lootr chests and transferring loot";
            case "inventory" -> "Opening inventory, rendering tooltips, swapping slots and hotbar";
            case "emi_search" -> "Waiting for EMI, searching items and checking results";
            case "emi_recipe" -> "Opening EMI recipes and checking outputs";
            case "combat" -> "Approaching benchmark mobs and attacking repeatedly";
            case "effects" -> "Observing mobs with active status effects";
            case "bloodmoon" -> "Observing the native Blood Moon and mob crowds";
            case "wither" -> "Observing native Withers and combat";
            case "tnt" -> "Observing TNT explosions and terrain changes";
            case "break" -> "Walking and breaking terrain blocks";
            case "weather" -> "Observing rain, lightning and particles";
            case "explore" -> "Exploring arena terrain and measuring chunk loading";
            case "baseline" -> "Observing arena rendering and ticks without extra mobs";
            case "village" -> "Observing villagers and native AI activity in the arena";
            case "teleport", "teleports" -> "Teleporting repeatedly; measuring terrain loading";
            case "nether" -> "Walking through safe Nether terrain";
            case "end" -> "Walking through safe End terrain";
            case "save" -> "Saving the actual world copy and measuring save time";
            default -> "Preparing the workload";
        };
    }
    public static String legacyTask(String name,String mode){return task(name.equals("baseline")?"baseline":name.startsWith("village")?"village":name.startsWith("wither")?"wither":mode);}
    public static boolean walking(String mode) {
        return switch (mode) {
            case "route", "cold_route", "structure", "combat", "effects", "bloodmoon", "wither", "break", "weather", "nether", "end" -> true;
            default -> false;
        };
    }
    private AdvancedStageInfo() { }
}
