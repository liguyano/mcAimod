package com.liguyano.mcaimod.client;

import java.util.Optional;
import java.util.Set;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

/** World-independent fishing-site and bite heuristics, independently unit-tested. */
public final class KurumiFishingLogic {
    public record Point(int x, int y, int z) {
        public Point offset(int dx, int dy, int dz) { return new Point(x + dx, y + dy, z + dz); }
        public double distanceSquared(Point p) {
            long dx=(long)x-p.x,dy=(long)y-p.y,dz=(long)z-p.z;
            return (double)(dx*dx+dy*dy+dz*dz);
        }
    }
    public record Spot(Point stand, Point waterCenter) {}

    public static Optional<Spot> choose(Set<Point> water, Point origin,
                                        Predicate<Point> safeStand) {
        Spot best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        Map<Point,Boolean> safeCache = new HashMap<>();
        for (Point center : water) {
            boolean open = true;
            for (int dx=-2;dx<=2 && open;dx++) for(int dz=-2;dz<=2;dz++)
                if (!water.contains(center.offset(dx,0,dz))) { open=false; break; }
            if (!open) continue;
            for(int dx=-7;dx<=7;dx++) for(int dz=-7;dz<=7;dz++) {
                int r2=dx*dx+dz*dz;
                if(r2<9 || r2>64) continue;
                for (int dy=0;dy<=2;dy++) {
                    Point shore=center.offset(dx,dy+1,dz);
                    if (!safeCache.computeIfAbsent(shore, safeStand::test)) continue;
                    double score=origin.distanceSquared(shore)+r2*0.05+dy*4;
                    if(score<bestScore) {bestScore=score;best=new Spot(shore,center);}
                }
            }
        }
        return Optional.ofNullable(best);
    }

    /** The synchronized bite packet produces a steep downward impulse after the bobber settles. */
    public static boolean bite(int hookAgeTicks, boolean inWater, double velocityY,
                               double previousVelocityY) {
        return hookAgeTicks >= 25 && inWater && velocityY <= -0.16
                && previousVelocityY > -0.16;
    }
    private KurumiFishingLogic() {}
}
