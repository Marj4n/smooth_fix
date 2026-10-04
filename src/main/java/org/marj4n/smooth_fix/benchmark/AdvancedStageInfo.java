package org.marj4n.smooth_fix.benchmark;

/** Player-facing descriptions shared by server chat and the client HUD. */
public final class AdvancedStageInfo {
    public static String task(String mode) {
        return switch (mode) {
            case "panorama" -> "Mengamati render terrain, entity dan partikel";
            case "route" -> "Berjalan di terrain yang sudah tersedia";
            case "cold_route" -> "Berjalan ke terrain baru; ukur chunk generation";
            case "structure" -> "Cari struktur, lalu berjalan mengamati area sekitar";
            case "chest" -> "Membuka chest dan memindahkan loot";
            case "lootr" -> "Membuka Lootr personal chest dan mengambil loot";
            case "inventory" -> "Membuka inventory, tooltip, swap slot dan hotbar";
            case "emi_search" -> "Menunggu EMI ready, mencari item dan memeriksa hasil";
            case "emi_recipe" -> "Membuka resep EMI dan memeriksa output";
            case "combat" -> "Mendekati mob benchmark dan menyerang berulang";
            case "effects" -> "Mengamati mob dengan efek aktif";
            case "bloodmoon" -> "Mengamati Blood Moon native dan kerumunan mob";
            case "wither" -> "Mengamati Wither native dan pertarungan";
            case "tnt" -> "Mengamati ledakan TNT dan perubahan terrain";
            case "break" -> "Berjalan dan memecahkan blok terrain";
            case "weather" -> "Mengamati hujan, petir dan partikel";
            case "teleport" -> "Teleport berulang; ukur loading terrain";
            case "nether" -> "Berjalan di terrain Nether yang aman";
            case "end" -> "Berjalan di terrain End yang aman";
            case "save" -> "Menyimpan world asli salinan dan mengukur waktunya";
            default -> "Menyiapkan workload";
        };
    }
    public static boolean walking(String mode) {
        return switch (mode) {
            case "route", "cold_route", "structure", "combat", "effects", "bloodmoon", "wither", "break", "weather", "nether", "end" -> true;
            default -> false;
        };
    }
    private AdvancedStageInfo() { }
}
