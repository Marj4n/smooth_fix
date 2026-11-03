package org.marj4n.smooth_fix.bootstrap.agent;

import java.io.*;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicLong;

/** Only the audited fully-consumed Kilt class-reading call is redirected here. */
public final class ConsumedClassStream {
    private static final AtomicLong opened = new AtomicLong(),
            closed = new AtomicLong(),
            bytes = new AtomicLong();

    private ConsumedClassStream() {}

    public static InputStream open(Path path, OpenOption... options) throws IOException {
        opened.incrementAndGet();
        byte[] content;
        try (InputStream input = Files.newInputStream(path, options)) {
            content = input.readAllBytes();
        }
        closed.incrementAndGet();
        bytes.addAndGet(content.length);
        return new ByteArrayInputStream(content);
    }

    public static long[] snapshot() {
        return new long[] {opened.get(), closed.get(), bytes.get()};
    }
}
