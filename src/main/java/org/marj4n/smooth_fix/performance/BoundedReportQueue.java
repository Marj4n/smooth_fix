package org.marj4n.smooth_fix.performance;

import java.io.IOException;
import java.lang.reflect.Array;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

/** Owns diagnostic documents only. No Minecraft object may cross this queue. */
public final class BoundedReportQueue {
    @FunctionalInterface
    public interface Writer {
        void write(Path file, Object snapshot) throws IOException;
    }

    public static final class SnapshotLimitException extends IOException {
        public SnapshotLimitException() {
            super("Diagnostic snapshot exceeds configured queue byte limit");
        }
    }

    private record Snapshot(Object value, long bytes) {}

    private static final class Job {
        final Path file;
        final long created = System.nanoTime();
        final CompletableFuture<Void> completion = new CompletableFuture<>();
        Snapshot snapshot;
        boolean urgent;

        Job(Path file, Snapshot snapshot, boolean urgent) {
            this.file = file;
            this.snapshot = snapshot;
            this.urgent = urgent;
        }
    }

    private final Object lock = new Object();
    private final LinkedHashMap<Path, Job> pending = new LinkedHashMap<>();
    private final int capacity;
    private final long byteLimit, deferNanos, frameBudget;
    private final Writer writer;
    private final Thread worker;
    private Job inFlight;
    private long pendingBytes, completed, coalesced, failed, writeNanos, deferred, maxPending;
    private volatile long frameMean, frameObserved;
    private boolean closing;

    public BoundedReportQueue(
            int capacity, long byteLimit, long deferNanos, long frameBudget, Writer writer) {
        if (capacity < 1 || byteLimit < 1 || deferNanos < 0 || frameBudget < 0)
            throw new IllegalArgumentException("Invalid report bounds");
        this.capacity = capacity;
        this.byteLimit = byteLimit;
        this.deferNanos = deferNanos;
        this.frameBudget = frameBudget;
        this.writer = Objects.requireNonNull(writer);
        worker = new Thread(this::run, "smooth-fix-report-io");
        worker.setDaemon(true);
        worker.start();
    }

    /** Copy on the owning thread; callers may immediately mutate their original maps. */
    public CompletableFuture<Void> submit(
            Path file, Object value, boolean urgent, long timeoutMillis) throws IOException {
        Snapshot snapshot = freeze(value, new IdentityHashMap<>(), 0);
        if (snapshot.bytes > byteLimit) throw new SnapshotLimitException();
        Path key = file.toAbsolutePath().normalize();
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        synchronized (lock) {
            for (; ; ) {
                if (closing) throw new IOException("Report queue has shut down");
                Job previous = pending.get(key);
                long retained =
                        pendingBytes
                                + (inFlight == null ? 0 : inFlight.snapshot.bytes)
                                - (previous == null ? 0 : previous.snapshot.bytes)
                                + snapshot.bytes;
                if ((previous != null || pending.size() < capacity) && retained <= byteLimit) {
                    if (previous != null) {
                        pendingBytes += snapshot.bytes - previous.snapshot.bytes;
                        previous.snapshot = snapshot;
                        previous.urgent |= urgent;
                        coalesced++;
                        lock.notifyAll();
                        return previous.completion;
                    }
                    Job job = new Job(key, snapshot, urgent);
                    pending.put(key, job);
                    pendingBytes += snapshot.bytes;
                    maxPending = Math.max(maxPending, pending.size());
                    lock.notifyAll();
                    return job.completion;
                }
                waitUntil(deadline, "Report queue admission timed out");
            }
        }
    }

    /** Scalar EWMA only, no per-frame allocations. A stale frame never blocks server work. */
    public void observeFrame(long nanos) {
        if (nanos <= 0) return;
        frameMean = frameMean == 0 ? nanos : frameMean + (nanos - frameMean) / 8;
        frameObserved = System.nanoTime();
    }

    public boolean cancelPending(Path file) {
        synchronized (lock) {
            Job job = pending.remove(file.toAbsolutePath().normalize());
            if (job == null) return false;
            pendingBytes -= job.snapshot.bytes;
            job.completion.cancel(false);
            lock.notifyAll();
            return true;
        }
    }

    public void awaitIdle(long timeoutMillis) throws IOException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        synchronized (lock) {
            while (!pending.isEmpty() || inFlight != null)
                waitUntil(deadline, "Report drain timed out");
        }
    }

    public void close(long timeoutMillis) throws IOException {
        if (timeoutMillis <= 0)
            throw new IllegalArgumentException("Shutdown requires a finite positive timeout");
        synchronized (lock) {
            closing = true;
            lock.notifyAll();
        }
        try {
            worker.join(timeoutMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted report shutdown", e);
        }
        if (worker.isAlive())
            throw new IOException("Report worker did not drain before shutdown deadline");
    }

    public Map<String, Long> metrics() {
        synchronized (lock) {
            return Map.of(
                    "pending",
                    (long) pending.size(),
                    "retainedSnapshotBytes",
                    pendingBytes + (inFlight == null ? 0 : inFlight.snapshot.bytes),
                    "completed",
                    completed,
                    "coalesced",
                    coalesced,
                    "failed",
                    failed,
                    "writeNanos",
                    writeNanos,
                    "pressureDeferrals",
                    deferred,
                    "maximumPending",
                    maxPending);
        }
    }

    private void waitUntil(long deadline, String error) throws IOException {
        long remaining = deadline - System.nanoTime();
        if (remaining <= 0) throw new IOException(error);
        try {
            TimeUnit.NANOSECONDS.timedWait(lock, remaining);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted report queue", e);
        }
    }

    private void run() {
        try {
            for (; ; ) {
                Job job;
                synchronized (lock) {
                    while (pending.isEmpty() && !closing) lock.wait();
                    if (pending.isEmpty()) return;
                    job = pending.values().iterator().next();
                    for (Job candidate : pending.values())
                        if (candidate.urgent) {
                            job = candidate;
                            break;
                        }
                    long now = System.nanoTime();
                    if (!closing
                            && !job.urgent
                            && frameBudget > 0
                            && frameMean > frameBudget
                            && now - frameObserved < 250_000_000L
                            && now - job.created < deferNanos) {
                        deferred++;
                        TimeUnit.NANOSECONDS.timedWait(
                                lock, Math.min(25_000_000L, deferNanos - (now - job.created)));
                        continue;
                    }
                    pending.remove(job.file);
                    pendingBytes -= job.snapshot.bytes;
                    inFlight = job;
                }
                long start = System.nanoTime();
                IOException failure = null;
                try {
                    writer.write(job.file, job.snapshot.value);
                } catch (Exception e) {
                    failure =
                            e instanceof IOException io
                                    ? io
                                    : new IOException("Diagnostic writer failed", e);
                }
                synchronized (lock) {
                    writeNanos += System.nanoTime() - start;
                    inFlight = null;
                    if (failure == null) completed++;
                    else failed++;
                    lock.notifyAll();
                }
                if (failure == null) job.completion.complete(null);
                else job.completion.completeExceptionally(failure);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            synchronized (lock) {
                closing = true;
                for (Job job : pending.values())
                    job.completion.completeExceptionally(
                            new IOException("Report worker interrupted", e));
                pending.clear();
                pendingBytes = 0;
                lock.notifyAll();
            }
        }
    }

    private Snapshot freeze(Object value, IdentityHashMap<Object, Boolean> ancestors, int depth)
            throws IOException {
        if (depth > 64) throw new IOException("Diagnostic nesting exceeds limit");
        if (value == null) return new Snapshot(null, 8);
        if (value instanceof String s) return new Snapshot(s, 40L + 2L * s.length());
        if (value instanceof Boolean
                || value instanceof Byte
                || value instanceof Short
                || value instanceof Integer
                || value instanceof Long
                || value instanceof Float
                || value instanceof Double
                || value instanceof java.math.BigDecimal
                || value instanceof java.math.BigInteger) return new Snapshot(value, 24);
        if (ancestors.put(value, Boolean.TRUE) != null)
            throw new IOException("Cyclic diagnostic snapshot");
        try {
            long bytes = 48;
            if (value instanceof Map<?, ?> map) {
                Map<String, Object> copy = new LinkedHashMap<>();
                for (var entry : map.entrySet()) {
                    if (!(entry.getKey() instanceof String key))
                        throw new IOException("Diagnostic object keys must be strings");
                    Snapshot child = freeze(entry.getValue(), ancestors, depth + 1);
                    copy.put(key, child.value);
                    bytes += child.bytes + 56 + 2L * key.length();
                    if (bytes > byteLimit) throw new SnapshotLimitException();
                }
                return new Snapshot(Collections.unmodifiableMap(copy), bytes);
            }
            if (value instanceof Collection<?> collection) {
                if (collection.size() > byteLimit / 8) throw new SnapshotLimitException();
                List<Object> copy = new ArrayList<>(Math.min(collection.size(), 1024));
                for (Object item : collection) {
                    Snapshot child = freeze(item, ancestors, depth + 1);
                    copy.add(child.value);
                    bytes += child.bytes + 8;
                    if (bytes > byteLimit) throw new SnapshotLimitException();
                }
                return new Snapshot(Collections.unmodifiableList(copy), bytes);
            }
            if (value.getClass().isArray()) {
                int size = Array.getLength(value);
                if (size > byteLimit / 8) throw new SnapshotLimitException();
                List<Object> copy = new ArrayList<>(Math.min(size, 1024));
                for (int i = 0; i < size; i++) {
                    Snapshot child = freeze(Array.get(value, i), ancestors, depth + 1);
                    copy.add(child.value);
                    bytes += child.bytes + 8;
                    if (bytes > byteLimit) throw new SnapshotLimitException();
                }
                return new Snapshot(Collections.unmodifiableList(copy), bytes);
            }
            throw new IOException(
                    "Unsupported mutable diagnostic value: " + value.getClass().getName());
        } finally {
            ancestors.remove(value);
        }
    }
}
