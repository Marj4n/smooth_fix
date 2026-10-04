package org.marj4n.smooth_fix.benchmark;
import net.minecraft.util.Identifier;
/** Versioned channels: both sides must have the same benchmark protocol. */
public final class BenchmarkProtocol {
    public static final Identifier CONTROL=new Identifier("smooth_fix","benchmark_control_v3");
    public static final Identifier RESPONSE=new Identifier("smooth_fix","benchmark_response_v3");
    public static final Identifier ADVANCED_CONTROL=new Identifier("smooth_fix","advanced_control_v2");
    public static final Identifier ADVANCED_RESPONSE=new Identifier("smooth_fix","advanced_response_v2");
    public static final Identifier RUN_RESULT=new Identifier("smooth_fix","benchmark_run_result_v1");
    public static final Identifier RUN_ACK=new Identifier("smooth_fix","benchmark_run_ack_v1");
    private BenchmarkProtocol() { }
}
