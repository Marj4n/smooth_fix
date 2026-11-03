package verification;

import org.marj4n.smooth_fix.performance.AtomicFilePublication;
import java.nio.file.*;
import java.nio.channels.FileChannel;
import com.sun.nio.file.ExtendedOpenOption;
import java.util.concurrent.*;

public final class AtomicFilePublicationCheck {
    public static void main(String[] args) throws Exception {
        Path base = Path.of("build", "verification").toAbsolutePath().normalize();
        Files.createDirectories(base);
        Path dir = Files.createTempDirectory(base, "atomic-publication-");
        Path target = dir.resolve("report.json"), staged = dir.resolve("report.tmp");
        try {
            Files.writeString(target, "old");
            Files.writeString(staged, "new");
            require(
                    AtomicFilePublication.publish(staged, target) == 1,
                    "Normal publish uses one attempt");
            require(
                    Files.readString(target).equals("new") && !Files.exists(staged),
                    "Complete new content published");
            if (!System.getProperty("os.name").startsWith("Windows")) {
                System.out.println(
                        "PASS generic publication; SKIPPED native Windows NOSHARE_DELETE cases on this OS");
                return;
            }
            Files.writeString(target, "old-locked");
            Files.writeString(staged, "new-after-release");
            var hold =
                    FileChannel.open(
                            target, StandardOpenOption.READ, ExtendedOpenOption.NOSHARE_DELETE);
            Thread release =
                    new Thread(
                            () -> {
                                try {
                                    Thread.sleep(35);
                                    hold.close();
                                } catch (Exception e) {
                                    throw new RuntimeException(e);
                                }
                            },
                            "owned-verification-sharing-release");
            release.start();
            int attempts;
            try {
                attempts = AtomicFilePublication.publish(staged, target);
            } finally {
                hold.close();
                release.join();
            }
            require(attempts > 1, "Actual Windows sharing denial caused retries");
            require(
                    Files.readString(target).equals("new-after-release"),
                    "Publish succeeds after real reader releases");
            Files.writeString(target, "keep-original");
            Files.writeString(staged, "keep-staged");
            boolean failed = false;
            try (var locked =
                    FileChannel.open(
                            target, StandardOpenOption.READ, ExtendedOpenOption.NOSHARE_DELETE)) {
                try {
                    AtomicFilePublication.publish(staged, target);
                } catch (AccessDeniedException e) {
                    failed = true;
                }
            }
            require(
                    failed
                            && Files.readString(target).equals("keep-original")
                            && Files.readString(staged).equals("keep-staged"),
                    "Permanent denial fails closed and retains both files");
            System.out.println(
                    "PASS: actual Windows sharing denial/release retried (attempts="
                            + attempts
                            + "); permanent lock preserves old/staged content; no destination deletion or swallowed failure.");
        } finally {
            Files.deleteIfExists(staged);
            Files.deleteIfExists(target);
            Files.deleteIfExists(dir);
        }
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
