package org.marj4n.smooth_fix.performance;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.file.*;

/** Publish only completed diagnostic/journal files. Never delete an existing target to evade a sharing lock. */
public final class AtomicFilePublication {
    private AtomicFilePublication() {}

    /** At most7 attempts and126ms retry delay; permanent access failures stay failures. Returns actual attempts. */
    public static int publish(Path staged, Path target) throws IOException {
        for (int attempt = 0; ; attempt++) {
            try {
                try {
                    Files.move(
                            staged,
                            target,
                            StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException unsupported) {
                    Files.move(staged, target, StandardCopyOption.REPLACE_EXISTING);
                }
                return attempt + 1;
            } catch (AccessDeniedException denied) {
                if (attempt >= 6) throw denied;
                try {
                    Thread.sleep(2L << attempt);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    var failure =
                            new InterruptedIOException(
                                    "Interrupted while publishing diagnostic/journal file; original target and staged data retained");
                    failure.initCause(interrupted);
                    failure.addSuppressed(denied);
                    throw failure;
                }
            }
        }
    }
}
