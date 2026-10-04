package org.marj4n.smooth_fix.benchmark;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;
import net.minecraft.util.math.*;
import java.util.*;

/** Follows persistent waypoints and replans around stalled edges; never holds sneak. */
public final class GroundNavigator {
    public record Input(boolean forward, boolean jump, boolean sprint) {
        public static final Input NONE = new Input(false, false, false);
    }
    private GroundRoutePlanner.Search search;
    private final Deque<GroundRoutePlanner.Point> path = new ArrayDeque<>();
    private final Map<GroundRoutePlanner.Point, Integer> blocked = new HashMap<>();
    private Vec3d progressPosition, origin, lastTickPosition;
    private int ticks, checkTick, lastProgressTick, nextPlanTick, jumpAfter;
    private double goalX, goalZ;
    private String status = "Mencari jalur aman";
    private double distance, extent;
    public String status() { return status; }
    public double distance() { return distance; }
    public double extent() { return extent; }
    public GroundRoutePlanner.Point waypoint() { return path.peekFirst(); }
    public Input tick(World world, PlayerEntity player, double targetX, double targetZ, ActionMetrics metrics) {
        ticks++;
        if(lastTickPosition!=null){double delta=Math.hypot(player.getX()-lastTickPosition.x,player.getZ()-lastTickPosition.z);if(delta<=4)distance+=delta;else metrics.count("navigation_position_corrections_excluded");}
        lastTickPosition=player.getPos();
        if(origin!=null)extent=Math.max(extent,Math.hypot(player.getX()-origin.x,player.getZ()-origin.z));
        if (progressPosition == null) { progressPosition = origin = player.getPos(); lastProgressTick = ticks; }
        if (ticks - checkTick >= 20) {
            checkTick = ticks;
            double moved = Math.sqrt(player.getPos().squaredDistanceTo(progressPosition));
            double horizontal = Math.hypot(player.getX() - progressPosition.x, player.getZ() - progressPosition.z);
            if (horizontal > .25 && moved < 16) { lastProgressTick = ticks; metrics.count("navigation_progress_samples"); }
            progressPosition = player.getPos();
            if (ticks - lastProgressTick > 40 && !path.isEmpty()) {
                blocked.put(path.peekFirst(), ticks + 100); path.clear(); search = null; nextPlanTick = ticks;
                metrics.count("navigation_stall_replans"); status = "Terhalang; mencari jalan memutar";
            }
        }
        blocked.entrySet().removeIf(entry -> entry.getValue() < ticks);
        if (Math.hypot(targetX - goalX, targetZ - goalZ) > 3) { path.clear(); search = null; nextPlanTick = ticks; }
        if (search == null && path.isEmpty() && ticks >= nextPlanTick) {
            goalX = targetX; goalZ = targetZ;
            var terrain = new GroundCollisionTerrain(world, player);
            var start = new GroundRoutePlanner.Point(player.getBlockX(), player.getY(), player.getBlockZ());
            search = new GroundRoutePlanner.Search((from, dx, dz) -> {
                var point = terrain.step(from, dx, dz); return point != null && !blocked.containsKey(point) ? point : null;
            }, start, goalX, goalZ);
            metrics.count("navigation_plans");
        }
        if (search != null) {
            status = "Mencari jalur memutar (" + search.visited() + " node)";
            if (!search.advance(12, 750_000L)) return Input.NONE;
            path.addAll(search.path()); search = null; nextPlanTick = ticks + 20;
            if (path.isEmpty()) { status = "Jalur aman belum tersedia; menunggu chunk / mencari ulang"; metrics.count("navigation_no_safe_path"); return Input.NONE; }
        }
        while (!path.isEmpty()) {
            var point = path.peekFirst();
            if (Math.hypot(point.x() + .5 - player.getX(), point.z() + .5 - player.getZ()) < .28 && Math.abs(point.y() - player.getY()) < .6) path.removeFirst();
            else break;
        }
        if (path.isEmpty()) { status = "Waypoint selesai; merencanakan langkah berikutnya"; return Input.NONE; }
        var point = path.peekFirst();
        double dx = point.x() + .5 - player.getX(), dz = point.z() + .5 - player.getZ();
        float yaw = (float)(Math.toDegrees(Math.atan2(dz, dx)) - 90);
        player.setYaw(player.getYaw() + MathHelper.clamp(MathHelper.wrapDegrees(yaw - player.getYaw()), -35, 35));
        boolean aligned = Math.abs(MathHelper.wrapDegrees(yaw - player.getYaw())) < 35;
        boolean jump = aligned && player.isOnGround() && point.y() > player.getY() + .55 && ticks >= jumpAfter;
        if (jump) { jumpAfter = ticks + 14; metrics.count("navigation_step_jumps"); }
        status = jump ? "Melompati satu blok di jalur" : "Berjalan mengikuti jalur aman";
        if (player.horizontalCollision && ticks - lastProgressTick > 20) status = "Penghalang di depan; merencanakan jalan memutar";
        return new Input(aligned, jump, aligned && !jump && Math.abs(point.y() - player.getY()) < .55);
    }
}
