package org.marj4n.smooth_fix.benchmark;

import java.util.*;

/** Incremental, bounded A* over standing positions; no world generation or teleporting. */
public final class GroundRoutePlanner {
    public record Point(int x, double y, int z) { }
    public interface Terrain { Point step(Point from, int dx, int dz); }
    private record Entry(Point point, double cost, double score) { }
    public static final class Search {
        private final Terrain terrain;
        private final Point start;
        private final double targetX, targetZ;
        private final PriorityQueue<Entry> open = new PriorityQueue<>(Comparator.comparingDouble(Entry::score));
        private final Map<Point, Double> costs = new HashMap<>();
        private final Map<Point, Point> parents = new HashMap<>();
        private final Set<Point> closed = new HashSet<>();
        private Point best;
        private boolean done;
        public Search(Terrain terrain, Point start, double targetX, double targetZ) {
            this.terrain = terrain; this.start = best = start; this.targetX = targetX; this.targetZ = targetZ;
            costs.put(start, 0.0); open.add(new Entry(start, 0, heuristic(start)));
        }
        private double heuristic(Point point) { return Math.hypot(point.x + .5 - targetX, point.z + .5 - targetZ); }
        public boolean advance(int maximumNodes, long budgetNanos) {
            long deadline = System.nanoTime() + budgetNanos;
            for (int i = 0; !done && i < maximumNodes && System.nanoTime() < deadline; i++) {
                Entry entry = open.poll();
                if (entry == null || closed.size() >= 256) { done = true; break; }
                Point from = entry.point;
                if (!closed.add(from)) continue;
                if (heuristic(from) < heuristic(best)) best = from;
                if (heuristic(from) < .65) { best = from; done = true; break; }
                for (int direction = 0; direction < 4; direction++) {
                    int dx = direction == 0 ? 1 : direction == 1 ? -1 : 0;
                    int dz = direction == 2 ? 1 : direction == 3 ? -1 : 0;
                    if (Math.abs(from.x + dx - start.x) > 12 || Math.abs(from.z + dz - start.z) > 12) continue;
                    Point next = terrain.step(from, dx, dz);
                    if (next == null || closed.contains(next)) continue;
                    double cost = entry.cost + 1 + Math.abs(next.y - from.y) * .35;
                    if (cost >= costs.getOrDefault(next, Double.POSITIVE_INFINITY)) continue;
                    costs.put(next, cost); parents.put(next, from);
                    open.add(new Entry(next, cost, cost + heuristic(next)));
                }
            }
            return done;
        }
        public List<Point> path() {
            List<Point> path = new ArrayList<>();
            for (Point at = best; !at.equals(start); at = parents.get(at)) {
                if (at == null) return List.of();
                path.add(at);
            }
            Collections.reverse(path); return path;
        }
        public int visited() { return closed.size(); }
    }
    private GroundRoutePlanner() { }
}
