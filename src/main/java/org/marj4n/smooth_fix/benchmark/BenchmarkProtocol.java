package org.marj4n.smooth_fix.benchmark;
import net.minecraft.util.Identifier;
/** Versioned channels: both sides must have the same benchmark protocol. */
public final class BenchmarkProtocol {
    public static final Identifier CONTROL=new Identifier("smooth_fix","benchmark_control_v2");
    public static final Identifier RESPONSE=new Identifier("smooth_fix","benchmark_response_v2");
    public static final Identifier ADV_CONTROL=new Identifier("smooth_fix","advanced_control_v1");
    public static final Identifier ADV_RESPONSE=new Identifier("smooth_fix","advanced_response_v1");
    private BenchmarkProtocol() { }
}
