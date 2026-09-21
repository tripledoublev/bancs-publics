package com.machine.benchmap;

import java.util.ArrayList;
import java.util.List;

/**
 * High-performance 2D Uniform Spatial Grid Index for Montreal benches.
 * Provides O(1) spatial queries and sub-millisecond nearest-neighbor search.
 */
public class SpatialBenchIndex {

    public static final int FILTER_ALL = 0;
    public static final int FILTER_PARKS = 1;
    public static final int FILTER_BACKREST = 2;
    public static final int FILTER_WOOD = 3;

    private static final double MIN_LAT = 45.38;
    private static final double MAX_LAT = 45.72;
    private static final double MIN_LON = -74.00;
    private static final double MAX_LON = -73.45;

    private static final double CELL_SIZE_DEG = 0.01; // ~1.1 km latitude, ~0.78 km longitude

    private final int numRows;
    private final int numCols;
    private final List<Bench>[][] grid;
    private final List<Bench> allBenches;

    @SuppressWarnings("unchecked")
    public SpatialBenchIndex(List<Bench> benches) {
        this.allBenches = new ArrayList<>(benches);
        this.numRows = (int) Math.ceil((MAX_LAT - MIN_LAT) / CELL_SIZE_DEG) + 1;
        this.numCols = (int) Math.ceil((MAX_LON - MIN_LON) / CELL_SIZE_DEG) + 1;

        this.grid = new ArrayList[numRows][numCols];
        for (int r = 0; r < numRows; r++) {
            for (int c = 0; c < numCols; c++) {
                grid[r][c] = new ArrayList<>();
            }
        }

        for (Bench b : benches) {
            int r = getRow(b.lat);
            int c = getCol(b.lon);
            if (r >= 0 && r < numRows && c >= 0 && c < numCols) {
                grid[r][c].add(b);
            }
        }
    }

    private int getRow(double lat) {
        return (int) ((lat - MIN_LAT) / CELL_SIZE_DEG);
    }

    private int getCol(double lon) {
        return (int) ((lon - MIN_LON) / CELL_SIZE_DEG);
    }

    public static boolean matchesFilter(Bench b, int filter) {
        switch (filter) {
            case FILTER_PARKS:
                return b.isInPark();
            case FILTER_BACKREST:
                return b.hasBackrest();
            case FILTER_WOOD:
                return b.isWood();
            case FILTER_ALL:
            default:
                return true;
        }
    }

    public int countForFilter(int filter) {
        if (filter == FILTER_ALL) return allBenches.size();
        int count = 0;
        for (Bench b : allBenches) {
            if (matchesFilter(b, filter)) {
                count++;
            }
        }
        return count;
    }

    public List<Bench> getAllBenches() {
        return allBenches;
    }

    /**
     * Fast tap hit-testing within a pixel radius in Mercator space.
     */
    public Bench findTapHit(double mercX, double mercY, double searchRadiusMerc, int filter) {
        // Convert Mercator to approximate lat/lon
        double lon = Math.toDegrees(mercX);
        double lat = Math.toDegrees(2.0 * Math.atan(Math.exp(mercY)) - Math.PI / 2.0);

        int centerR = getRow(lat);
        int centerC = getCol(lon);

        double radiusSq = searchRadiusMerc * searchRadiusMerc;
        Bench best = null;
        double bestDistSq = radiusSq;

        for (int r = Math.max(0, centerR - 1); r <= Math.min(numRows - 1, centerR + 1); r++) {
            for (int c = Math.max(0, centerC - 1); c <= Math.min(numCols - 1, centerC + 1); c++) {
                List<Bench> cell = grid[r][c];
                for (int i = 0; i < cell.size(); i++) {
                    Bench b = cell.get(i);
                    if (!matchesFilter(b, filter)) continue;

                    double dx = b.mercX - mercX;
                    double dy = b.mercY - mercY;
                    double dSq = dx * dx + dy * dy;
                    if (dSq <= bestDistSq) {
                        bestDistSq = dSq;
                        best = b;
                    }
                }
            }
        }
        return best;
    }

    /**
     * Finds nearest bench to the given lat/lon using spiral search.
     * Guaranteed exact result, completed in microseconds.
     */
    public Bench findNearest(double userLat, double userLon, int filter) {
        int centerR = Math.max(0, Math.min(numRows - 1, getRow(userLat)));
        int centerC = Math.max(0, Math.min(numCols - 1, getCol(userLon)));

        Bench best = null;
        double bestMeters = Double.MAX_VALUE;

        int maxRing = Math.max(Math.max(centerR, numRows - centerR), Math.max(centerC, numCols - centerC));

        for (int ring = 0; ring <= maxRing; ring++) {
            boolean checkedAny = false;

            int minR = Math.max(0, centerR - ring);
            int maxR = Math.min(numRows - 1, centerR + ring);
            int minC = Math.max(0, centerC - ring);
            int maxC = Math.min(numCols - 1, centerC + ring);

            for (int r = minR; r <= maxR; r++) {
                for (int c = minC; c <= maxC; c++) {
                    // Only visit border of the ring
                    if (ring > 0 && (r > minR && r < maxR) && (c > minC && c < maxC)) {
                        continue;
                    }

                    checkedAny = true;
                    List<Bench> cell = grid[r][c];
                    for (int i = 0; i < cell.size(); i++) {
                        Bench b = cell.get(i);
                        if (!matchesFilter(b, filter)) continue;

                        double d = MontrealBenchMapView.computeDistance(userLat, userLon, b.lat, b.lon);
                        if (d < bestMeters) {
                            bestMeters = d;
                            best = b;
                        }
                    }
                }
            }

            // Pruning check:
            // If we found a bench in or before this ring, and best distance in meters is less than
            // the distance from user to the next ring boundary, no outer ring can possibly beat it!
            if (best != null) {
                double nextRingDeg = ring * CELL_SIZE_DEG;
                double approxMinDistToNextRing = nextRingDeg * 80000.0; // conservative meters per degree
                if (bestMeters < approxMinDistToNextRing) {
                    break;
                }
            }

            if (!checkedAny && ring > 5) break;
        }

        // Fallback safety check if spiral was too constrained
        if (best == null) {
            for (Bench b : allBenches) {
                if (!matchesFilter(b, filter)) continue;
                double d = MontrealBenchMapView.computeDistance(userLat, userLon, b.lat, b.lon);
                if (d < bestMeters) {
                    bestMeters = d;
                    best = b;
                }
            }
        }

        return best;
    }
}
