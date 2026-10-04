import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.impl.metadata.DependencyOverrides;
import net.fabricmc.loader.impl.metadata.LoaderModMetadata;
import net.fabricmc.loader.impl.metadata.ModMetadataParser;
import net.fabricmc.loader.impl.metadata.VersionOverrides;
import org.marj4n.smooth_fix.bootstrap.ServerCompatibilityAdapter;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipFile;

/** Standalone check against original upstream JAR metadata; no JAR contents are rewritten. */
public final class MetadataScopeCheck {
    public static void main(String[] args) throws Exception {
        Method repair = ServerCompatibilityAdapter.class.getDeclaredMethod("repair", ModContainer.class, Set.class);
        repair.setAccessible(true);
        for (String path : args) {
            try (ZipFile jar = new ZipFile(path)) {
                LoaderModMetadata metadata = ModMetadataParser.parseMetadata(jar.getInputStream(jar.getEntry("fabric.mod.json")),
                        path, List.of(), new VersionOverrides(), new DependencyOverrides(Files.createTempDirectory("smooth-fix-check")), false);
                String target = metadata.getId().equals("shut-up-mcd") ? "shut-up-mcd.client.mixins.json" : "enhancedblockentities.mixins.json";
                List<String> clientBefore = List.copyOf(metadata.getMixinConfigs(EnvType.CLIENT));
                List<String> serverBefore = List.copyOf(metadata.getMixinConfigs(EnvType.SERVER));
                if (!serverBefore.contains(target)) throw new AssertionError("Fixture must contain the original mis-scoped config");
                var entrypointsBefore = metadata.getEntrypointKeys().stream().map(key -> metadata.getEntrypoints(key).toString()).toList();
                ModContainer container = (ModContainer) Proxy.newProxyInstance(ModContainer.class.getClassLoader(),
                        new Class<?>[]{ModContainer.class}, (proxy, method, values) -> method.getName().equals("getMetadata") ? metadata : null);
                repair.invoke(null, container, Set.of(target));
                if (metadata.getMixinConfigs(EnvType.SERVER).contains(target)) throw new AssertionError("Server scope remains incorrect");
                if (!clientBefore.equals(List.copyOf(metadata.getMixinConfigs(EnvType.CLIENT)))) throw new AssertionError("Client hooks changed");
                var entrypointsAfter = metadata.getEntrypointKeys().stream().map(key -> metadata.getEntrypoints(key).toString()).toList();
                if (!entrypointsBefore.equals(entrypointsAfter)) throw new AssertionError("Entrypoints changed");
                if (!metadata.loadsInEnvironment(EnvType.SERVER)) throw new AssertionError("Mod was disabled on server");
                System.out.println("PASS " + metadata.getId() + ": server hook corrected, client hooks/entrypoints/mod environment preserved");
            }
        }
    }
}
