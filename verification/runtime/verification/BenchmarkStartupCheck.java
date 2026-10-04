package verification;

import org.marj4n.smooth_fix.benchmark.BenchmarkStartupGate;
import static org.marj4n.smooth_fix.benchmark.BenchmarkStartupGate.Decision.*;

/** Regression: entering chat/teleport/loading before readiness must not cancel an unmeasured stage. */
public final class BenchmarkStartupCheck {
    public static void main(String[] args) { run(); }
    public static void run() {
        BenchmarkStartupGate gate = new BenchmarkStartupGate();
        expect(gate.update(false, true, true), WAIT); // command chat / old world
        expect(gate.update(false, false, true), WAIT); // respawn packet not processed
        expect(gate.update(true, true, true), WAIT); // target world with loading screen
        expect(gate.update(true, false, false), WAIT); // window not yet focused
        if (gate.isReady()) throw new AssertionError("Readiness acknowledged during loading");
        expect(gate.update(true, false, true), START);
        expect(gate.update(true, false, true), CONTINUE);
        expect(gate.update(true, true, true), ABORT); // interruptions invalidate started stage
        BenchmarkStartupGate next = new BenchmarkStartupGate();
        expect(next.update(false, true, true), WAIT); // every dimension transition gets its own gate
        expect(next.update(true, false, true), START);
        expect(next.update(true, false, false), ABORT);
        BenchmarkStartupGate changed = new BenchmarkStartupGate();
        expect(changed.update(true, false, true), START);
        expect(changed.update(false, false, true), ABORT);
        System.out.println("SMOOTHFIX_VERIFY_PASS benchmark startup waits for world/screen/focus, starts once, resets per stage, and rejects later interruptions");
    }
    private static void expect(BenchmarkStartupGate.Decision actual, BenchmarkStartupGate.Decision expected) {
        if (actual != expected) throw new AssertionError("Expected " + expected + ", got " + actual);
    }
}
