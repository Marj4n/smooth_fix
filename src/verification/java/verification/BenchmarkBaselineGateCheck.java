package verification;

import java.util.*;
import org.marj4n.smooth_fix.benchmark.*;

public final class BenchmarkBaselineGateCheck {
    private static void require(boolean v, String reason) {
        if (!v) throw new AssertionError(reason);
    }

    public static void main(String[] args) {
        var gate = new BenchmarkBaselineGate<String>();
        var owner = UUID.randomUUID();
        var other = UUID.randomUUID();
        var request = gate.begin(owner, "original-plan", 100);
        require(gate.isPending(), "Not pending");
        try {
            gate.begin(owner, "overwrite", 101);
            throw new AssertionError("Pending original state overwritten");
        } catch (IllegalStateException expected) {
        }
        require(
                gate.consume(other, request.run(), 101) == null && gate.isPending(),
                "Wrong owner consumed pending start");
        require(
                gate.consume(owner, UUID.randomUUID(), 101) == null && gate.isPending(),
                "Wrong nonce consumed pending start");
        var accepted = gate.consume(owner, request.run(), 102);
        require(
                accepted != null && accepted.payload().equals("original-plan") && !gate.isPending(),
                "Valid original-state ACK rejected");
        require(
                gate.consume(owner, request.run(), 103) == null,
                "Duplicate ACK started another run");
        var expired = gate.begin(owner, "stale", 200);
        require(
                gate.consume(owner, expired.run(), expired.expires()) == null && !gate.isPending(),
                "Expired ACK started mutation");
        var timeout = gate.begin(owner, "timeout", 300);
        require(gate.expire(timeout.expires() - 1) == null, "Early timeout");
        require(
                gate.expire(timeout.expires()) != null && !gate.isPending(),
                "Timeout left live pending work");
        var cancelled = gate.begin(owner, "cancelled", 400);
        require(gate.cancel() != null && !gate.isPending(), "Cancellation retained request");
        require(gate.consume(owner, cancelled.run(), 401) == null, "Cancelled ACK accepted");
        var clientBefore =
                Map.<String, Object>of(
                        "inventoryProjectionAvailable",
                        true,
                        "inventorySha256",
                        "client-empty-tag",
                        "localSavedInventorySha256",
                        "full-client-NBT");
        var serverBefore =
                Map.<String, Object>of(
                        "inventoryProjectionAvailable",
                        true,
                        "inventorySha256",
                        "server-absent-tag");
        require(
                Boolean.TRUE.equals(
                        RecoverySnapshot.compare(clientBefore, clientBefore).get("all")),
                "Same original client falsely rejected");
        require(
                Boolean.FALSE.equals(
                        RecoverySnapshot.compare(serverBefore, clientBefore).get("all")),
                "Preexisting peer difference erased");
        require(
                Boolean.FALSE.equals(
                        RecoverySnapshot.compare(
                                        clientBefore,
                                        Map.of(
                                                "inventoryProjectionAvailable",
                                                true,
                                                "inventorySha256",
                                                "client-empty-tag",
                                                "localSavedInventorySha256",
                                                "changed-client-NBT"))
                                .get("all")),
                "Actual client-private NBT change ignored");
        System.out.println(
                "PASS: before-mutation owner/nonce gate, duplicate/stale/cancel/timeout rejection; complete local NBT changes fail while preexisting cross-peer differences stay explicit. Actual network start/recovery still pending.");
    }
}
