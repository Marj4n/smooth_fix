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
    private final LinkedHashMap<Long, Integer> visits = new LinkedHashMap<>();
    private long previousCell = Long.MIN_VALUE;
    private Vec3d progressPosition, origin, lastTickPosition;
    private int ticks, checkTick, lastProgressTick, nextPlanTick, jumpAfter;
    private double goalX, goalZ;
    private String status = "Finding a safe route";
    private double distance, extent;
    public String status() { return status; }
    public double distance() { return distance; }
    public double extent() { return extent; }
    public GroundRoutePlanner.Point waypoint() { return path.peekFirst(); }
    public void resetPath(){path.clear();search=null;nextPlanTick=ticks;progressPosition=lastTickPosition=null;}
    public Input tick(World world, PlayerEntity player, double targetX, double targetZ, ActionMetrics metrics) {
        return tick(world,player,targetX,targetZ,metrics,false);
    }
    public Input tick(World world, PlayerEntity player, double targetX, double targetZ, ActionMetrics metrics,boolean roam) {
        ticks++;
        if(lastTickPosition!=null){double delta=Math.hypot(player.getX()-lastTickPosition.x,player.getZ()-lastTickPosition.z);if(delta<=4)distance+=delta;else metrics.count("navigation_position_corrections_excluded");}
        lastTickPosition=player.getPos();
        if(origin!=null)extent=Math.max(extent,Math.hypot(player.getX()-origin.x,player.getZ()-origin.z));
        if (origin == null) origin = player.getPos();
        if (progressPosition == null) { progressPosition = player.getPos(); lastProgressTick = ticks; }
        if (roam) {
            long cell = cell(player.getBlockX(), player.getBlockZ());
            if (cell != previousCell) {
                previousCell = cell;
                visits.put(cell, Math.min(8, visits.getOrDefault(cell, 0) + 1));
                if (visits.size() > 512) visits.remove(visits.keySet().iterator().next());
            }
        }
        if (ticks - checkTick >= 20) {
            checkTick = ticks;
            double moved = Math.sqrt(player.getPos().squaredDistanceTo(progressPosition));
            double horizontal = Math.hypot(player.getX() - progressPosition.x, player.getZ() - progressPosition.z);
            if (horizontal > .25 && moved < 16) { lastProgressTick = ticks; metrics.count("navigation_progress_samples"); }
            progressPosition = player.getPos();
            if (ticks - lastProgressTick > 40 && !path.isEmpty()) {
                blocked.put(path.peekFirst(), ticks + 100); path.clear(); search = null; nextPlanTick = ticks;
                metrics.count("navigation_stall_replans"); status = "Blocked; planning a detour";
            }
        }
        blocked.entrySet().removeIf(entry -> entry.getValue() < ticks);
        if (!roam && Math.hypot(targetX - goalX, targetZ - goalZ) > 3) { path.clear(); search = null; nextPlanTick = ticks; }
        if (search == null && path.isEmpty() && ticks >= nextPlanTick) {
            goalX = targetX; goalZ = targetZ;
            var terrain = new GroundCollisionTerrain(world, player);
            var start = new GroundRoutePlanner.Point(player.getBlockX(), player.getY(), player.getBlockZ());
            search = new GroundRoutePlanner.Search((from, dx, dz) -> {
                var point = terrain.step(from, dx, dz); return point != null && !blocked.containsKey(point) ? point : null;
            }, start, goalX, goalZ, roam, point ->
                    1.25 * Math.hypot(point.x() + .5 - origin.x, point.z() + .5 - origin.z)
                            - Math.min(12, visits.getOrDefault(cell(point.x(), point.z()), 0) * 3));
            metrics.count("navigation_plans");
        }
        if (search != null) {
            status = "Planning a detour (" + search.visited() + " nodes)";
            if (!search.advance(12, 750_000L)) return new Input(false,player.isTouchingWater(),false);
            path.addAll(search.path()); search = null; nextPlanTick = ticks + 20;
            if (path.isEmpty()) { status = "No safe route yet; waiting for chunks / replanning"; metrics.count("navigation_no_safe_path"); return new Input(false,player.isTouchingWater(),false); }
        }
        while (!path.isEmpty()) {
            var point = path.peekFirst();
            if (Math.hypot(point.x() + .5 - player.getX(), point.z() + .5 - player.getZ()) < .28 && Math.abs(point.y() - player.getY()) < .6) path.removeFirst();
            else break;
        }
        if (path.isEmpty()) { status = "Waypoint reached; planning the next step"; return new Input(false,player.isTouchingWater(),false); }
        var point = path.peekFirst();
        var terrain=new GroundCollisionTerrain(world,player);
        double dx = point.x() + .5 - player.getX(), dz = point.z() + .5 - player.getZ();
        float yaw = (float)(Math.toDegrees(Math.atan2(dz, dx)) - 90);
        player.setYaw(player.getYaw() + MathHelper.clamp(MathHelper.wrapDegrees(yaw - player.getYaw()), -35, 35));
        boolean aligned = Math.abs(MathHelper.wrapDegrees(yaw - player.getYaw())) < 12;
        boolean swimming=player.isTouchingWater();
        if(swimming){status="Swimming toward the surface / safe shore";metrics.count("navigation_swimming_ticks");return new Input(aligned,true,false);}
        double speed=player.getVelocity().horizontalLength();
        // Native motion can outlive forward input. Brake before an unsupported ledge or a sharp turn.
        // Do not extrapolate past a safe corner before the following waypoint defines its turn.
        double lookahead=Math.min(Math.hypot(dx,dz),Math.max(.6,speed*4+.35));
        boolean safe=terrain.safeMotion(player.getX(),player.getY(),player.getZ(),dx,dz,lookahead);
        if(!aligned || (!safe && point.y()<=player.getY()+.55)){
            player.setVelocity(0,player.getVelocity().y,0);
            if(!safe){blocked.put(point,ticks+100);path.clear();search=null;nextPlanTick=ticks+1;metrics.count("navigation_edge_brakes");status="Ledge / ground changed; braking and finding another route";}
            return new Input(false,player.isTouchingWater(),false);
        }
        boolean jump = aligned && player.isOnGround() && point.y() > player.getY() + .55 && ticks >= jumpAfter;
        if (jump) { jumpAfter = ticks + 14; metrics.count("navigation_step_jumps"); }
        status = jump ? "Jumping a one-block step on the route" : "Following the safe ground route";
        if (player.horizontalCollision && ticks - lastProgressTick > 20) status = "Obstacle ahead; planning a detour";
        boolean sprint=aligned && !jump && Math.abs(point.y()-player.getY())<.55 && !roam
                && terrain.safeMotion(player.getX(),player.getY(),player.getZ(),dx,dz,2.5);
        return new Input(aligned, jump, sprint);
    }
    private static long cell(int x, int z) { return ((long)x << 32) ^ (z & 0xffffffffL); }
}
