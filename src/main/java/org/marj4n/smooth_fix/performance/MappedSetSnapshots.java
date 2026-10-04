package org.marj4n.smooth_fix.performance;

import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;

/** Eager snapshot with the JDK's original collector; no cached inventory values or lazy views. */
public final class MappedSetSnapshots {
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static final Collector<Object, Set<Object>, Set<Object>> COLLECTOR =
            (Collector) Collectors.toUnmodifiableSet();
    private static final Supplier<Set<Object>> SUPPLIER = COLLECTOR.supplier();
    private static final BiConsumer<Set<Object>, Object> ACCUMULATOR = COLLECTOR.accumulator();
    private static final Function<Set<Object>, Set<Object>> FINISHER = COLLECTOR.finisher();

    private MappedSetSnapshots() { }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <I, O> Set<O> collect(Set<I> source, Function<? super I, ? extends O> mapper) {
        Set<Object> snapshot = SUPPLIER.get();
        // Keep the original sequential stream's spliterator traversal and collector semantics.
        source.spliterator().forEachRemaining(input -> ACCUMULATOR.accept(snapshot, mapper.apply(input)));
        return (Set) FINISHER.apply(snapshot);
    }
}
