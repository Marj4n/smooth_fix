package org.marj4n.smooth_fix.benchmark;

/** Loading/command screens may precede readiness; interruptions after readiness invalidate the run. */
public final class BenchmarkStartupGate {
    public enum Decision { WAIT, START, CONTINUE, ABORT }
    private boolean ready;

    public Decision update(boolean worldReady, boolean screenOpen, boolean focused) {
        boolean playable = worldReady && !screenOpen && focused;
        if (!ready) {
            if (!playable) return Decision.WAIT;
            ready = true;
            return Decision.START;
        }
        return playable ? Decision.CONTINUE : Decision.ABORT;
    }

    public boolean isReady() { return ready; }
}
