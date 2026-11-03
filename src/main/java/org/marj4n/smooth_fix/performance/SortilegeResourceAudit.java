package org.marj4n.smooth_fix.performance;

import net.fabricmc.loader.api.FabricLoader;
import org.marj4n.smooth_fix.SmoothFix;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Refuses unreviewed versions/bytecode; never loads or modifies the upstream class itself. */
public final class SortilegeResourceAudit {
    public static final String CLASS_SHA256 =
            "132e522d67b7975c9df78fbfc47e9a4e6734d8382670de50f6ec0527ae3076cb";

    private SortilegeResourceAudit() {}

    /** Explicit dev launch override permits identical on-disk configs in independent A/B runs. */
    public static boolean requested(boolean configured) {
        String override = System.getProperty("smoothfix.experimental.sortilegeReloadStreamClosure");
        return override == null ? configured : "true".equals(override);
    }

    public static boolean supported() {
        var mod = FabricLoader.getInstance().getModContainer("sortilege");
        if (mod.isEmpty()
                || !mod.get().getMetadata().getVersion().getFriendlyString().equals("9.0"))
            return false;
        try {
            var path = mod.get().findPath("net/lyof/sortilege/setup/ReloadListener.class");
            if (path.isEmpty()) return false;
            String hash =
                    HexFormat.of()
                            .formatHex(
                                    MessageDigest.getInstance("SHA-256")
                                            .digest(Files.readAllBytes(path.get())));
            if (!CLASS_SHA256.equals(hash)) {
                SmoothFix.LOGGER.warn(
                        "Sortilege stream closure refused: installed bytecode is not audited ({})",
                        hash);
                return false;
            }
            SmoothFix.logPatchOnce(
                    "sortilege-recipe-resource-closure",
                    "Sortilege 9.0 recipe resource closure requested (audited bytecode; verify actual operation counters)");
            return true;
        } catch (Exception e) {
            SmoothFix.LOGGER.warn(
                    "Sortilege stream closure audit failed; leaving upstream behavior unchanged",
                    e);
            return false;
        }
    }
}
