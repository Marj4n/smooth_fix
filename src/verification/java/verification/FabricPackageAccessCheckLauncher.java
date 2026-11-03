package verification;

import net.fabricmc.loader.impl.transformer.PackageAccessFixer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.regex.Pattern;

/** Test-only: apply Fabric's actual development package-access fix, not gameplay mixins. */
public final class FabricPackageAccessCheckLauncher {
    public static void main(String[] args) throws Throwable {
        URL[] urls =
                Arrays.stream(
                                System.getProperty("java.class.path")
                                        .split(Pattern.quote(File.pathSeparator)))
                        .map(
                                path -> {
                                    try {
                                        return new File(path).toURI().toURL();
                                    } catch (Exception error) {
                                        throw new IllegalArgumentException(error);
                                    }
                                })
                        .toArray(URL[]::new);
        ClassLoader prior = Thread.currentThread().getContextClassLoader();
        try (var loader =
                new URLClassLoader(urls, ClassLoader.getPlatformClassLoader()) {
                    @Override
                    protected Class<?> findClass(String name) throws ClassNotFoundException {
                        if (!name.startsWith("net.minecraft.")) return super.findClass(name);
                        URL resource = findResource(name.replace('.', '/') + ".class");
                        if (resource == null) throw new ClassNotFoundException(name);
                        try (InputStream input = resource.openStream()) {
                            ClassWriter writer = new ClassWriter(0);
                            new ClassReader(input.readAllBytes())
                                    .accept(new PackageAccessFixer(Opcodes.ASM9, writer), 0);
                            byte[] bytes = writer.toByteArray();
                            return defineClass(name, bytes, 0, bytes.length);
                        } catch (Exception error) {
                            throw new ClassNotFoundException(name, error);
                        }
                    }
                }) {
            Thread.currentThread().setContextClassLoader(loader);
            System.out.println(
                    "Fixture applies Fabric Loader PackageAccessFixer to named Minecraft classes; no game-logic/Mixin replacement and no JAR/cache modification.");
            String check = args.length == 0 ? "verification.StatusEffectRecoveryCheck" : args[0];
            if (!java.util.Set.of(
                            "verification.StatusEffectRecoveryCheck",
                            "verification.NetworkInventoryProjectionCheck",
                            "verification.BenchmarkBaselineGateCheck",
                            "verification.ThinPlantOcclusionCheck")
                    .contains(check))
                throw new IllegalArgumentException("Unknown verification fixture");
            try {
                Class.forName(check, true, loader)
                        .getMethod("main", String[].class)
                        .invoke(null, (Object) new String[0]);
            } catch (InvocationTargetException error) {
                throw error.getCause();
            }
        } finally {
            Thread.currentThread().setContextClassLoader(prior);
        }
    }
}
