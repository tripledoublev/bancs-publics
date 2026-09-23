package com.machine.benchmap;

import java.util.ArrayList;
import java.util.List;

/**
 * High-performance 2D Uniform Spatial Grid Index for Montreal benches.
 * Operates purely in precomputed Mercator space for zero-allocation,
 * zero-trigonometric queries and sub-millisecond nearest-neighbor search.
 */
public class SpatialBenchIndex {

    public final double minMercX;
    public final double maxMercX;
    public final double minMercY;
    public final double maxMercY;

    public final int numRows;
    public final int numCols;
    public final double cellW;
    public final double cellH;

    public final List<Bench>[][] grid;
    private final List<Bench> allBenches;

    @SuppressWarnings("unchecked")
    public SpatialBenchIndex(List<Bench> benches) {
        this.allBenches = new ArrayList<>(benches);

        double minX = -1.2925, maxX = -1.2815;
        double minY = 0.8895, maxY = 0.9000;
        for (int i = 0; i < benches.size(); i++) {
            Bench b = benches.get(i);
            if (b.mercX < minX) minX = b.mercX;
            if (b.mercX > maxX) maxX = b.mercX;
            if (b.mercY < minY) minY = b.mercY;
            if (b.mercY > maxY) maxY = b.mercY;
        }

        // Add padding margin to ensure perimeter benches fit securely
        this.minMercX = minX - 0.0005;
        this.maxMercX = maxX + 0.0005;
        this.minMercY = minY - 0.0005;
        this.maxMercY = maxY + 0.0005;

        this.numRows = 36;
        this.numCols = 36;
        this.cellW = (this.maxMercX - this.minMercX) / numCols;
        this.cellH = (this.maxMercY - this.minMercY) / numRows;

        this.grid = new ArrayList[numRows][numCols];
        for (int r = 0; r < numRows; r++) {
            for (int c = 0; c < numCols; c++) {
                grid[r][c] = new ArrayList<>();
            }
        }

        for (int i = 0; i < benches.size(); i++) {
            Bench b = benches.get(i);
            int r = getRow(b.mercY);
            int c = getCol(b.mercX);
            if (r >= 0 && r < numRows && c >= 0 && c < numCols) {
                grid[r][c].add(b);
            }
        }
    }

    public int getRow(double mercY) {
        return (int) ((mercY - minMercY) / cellH);
    }

    public int getCol(double mercX) {
        return (int) ((mercX - minMercX) / cellW);
    }

    public int getTotalCount() {
        return allBenches.size();
    }

    public List<Bench> getAllBenches() {
        return allBenches;
    }

    /**
     * Direct projection into preallocated float arrays for single GPU batch draw call.
     * Zero object allocation during viewport queries.
     * outCounts[0] = park points float count (parkPoints * 2)
     * outCounts[1] = street points float count (streetPoints * 2)
     */
    public void queryVisiblePoints(double viewMinX, double viewMaxX, double viewMinY, double viewMaxY,
                                   int step, float halfW, float halfH, double cX, double cY, float sc,
                                   float[] parkPts, float[] streetPts, int[] outCounts) {
        int r0 = Math.max(0, Math.min(numRows - 1, getRow(viewMinY)));
        int r1 = Math.max(0, Math.min(numRows - 1, getRow(viewMaxY)));
        int c0 = Math.max(0, Math.min(numCols - 1, getCol(viewMinX)));
        int c1 = Math.max(0, Math.min(numCols - 1, getCol(viewMaxX)));

        int parkIdx = 0;
        int streetIdx = 0;
        int parkMax = parkPts.length;
        int streetMax = streetPts.length;

        for (int r = r0; r <= r1; r++) {
            for (int c = c0; c <= c1; c++) {
                List<Bench> cell = grid[r][c];
                int sz = cell.size();
                for (int i = 0; i < sz; i += step) {
                    Bench b = cell.get(i);
                    if (b.mercX >= viewMinX && b.mercX <= viewMaxX &&
                            b.mercY >= viewMinY && b.mercY <= viewMaxY) {
                        float bx = halfW + (float) ((b.mercX - cX) * sc);
                        float by = halfH - (float) ((b.mercY - cY) * sc);
                        if (b.isInPark()) {
                            if (parkIdx + 2 <= parkMax) {
                                parkPts[parkIdx++] = bx;
                                parkPts[parkIdx++] = by;
                            }
                        } else {
                            if (streetIdx + 2 <= streetMax) {
                                streetPts[streetIdx++] = bx;
                                streetPts[streetIdx++] = by;
                            }
                        }
                    }
                }
            }
        }
        outCounts[0] = parkIdx;
        outCounts[1] = streetIdx;
    }

    /**
     * Efficient range query for visible benches in Mercator space.
     */
    public void queryVisibleBenches(double viewMinX, double viewMaxX, double viewMinY, double viewMaxY, int step, List<Bench> result) {
        int r0 = Math.max(0, Math.min(numRows - 1, getRow(viewMinY)));
        int r1 = Math.max(0, Math.min(numRows - 1, getRow(viewMaxY)));
        int c0 = Math.max(0, Math.min(numCols - 1, getCol(viewMinX)));
        int c1 = Math.max(0, Math.min(numCols - 1, getCol(viewMaxX)));

        for (int r = r0; r <= r1; r++) {
            for (int c = c0; c <= c1; c++) {
                List<Bench> cell = grid[r][c];
                int sz = cell.size();
                for (int i = 0; i < sz; i += step) {
                    Bench b = cell.get(i);
                    if (b.mercX >= viewMinX && b.mercX <= viewMaxX &&
                            b.mercY >= viewMinY && b.mercY <= viewMaxY) {
                        result.add(b);
                    }
                }
            }
        }
    }

    /**
     * Fast tap hit-testing within a pixel radius in Mercator space.
     * Zero lat/lon trigonometric conversions required.
     */
    public Bench findTapHit(double mercX, double mercY, double searchRadiusMerc) {
        int centerR = getRow(mercY);
        int centerC = getCol(mercX);

        double radiusSq = searchRadiusMerc * searchRadiusMerc;
        Bench best = null;
        double bestDistSq = radiusSq;

        int minR = Math.max(0, centerR - 1);
        int maxR = Math.min(numRows - 1, centerR + 1);
        int minC = Math.max(0, centerC - 1);
        int maxC = Math.min(numCols - 1, centerC + 1);

        for (int r = minR; r <= maxR; r++) {
            for (int c = minC; c <= maxC; c++) {
                List<Bench> cell = grid[r][c];
                int sz = cell.size();
                for (int i = 0; i < sz; i++) {
                    Bench b = cell.get(i);
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
     * Finds nearest bench to the given lat/lon using spiral ring search with distance pruning.
     */
    public Bench findNearest(double userLat, double userLon) {
        double userMercX = MontrealBenchMapView.lonToMercatorX(userLon);
        double userMercY = MontrealBenchMapView.latToMercatorY(userLat);

        int centerR = Math.max(0, Math.min(numRows - 1, getRow(userMercY)));
        int centerC = Math.max(0, Math.min(numCols - 1, getCol(userMercX)));

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
                    if (ring > 0 && (r > minR && r < maxR) && (c > minC && c < maxC)) {
                        continue;
                    }

                    checkedAny = true;
                    List<Bench> cell = grid[r][c];
                    int sz = cell.size();
                    for (int i = 0; i < sz; i++) {
                        Bench b = cell.get(i);
                        double d = MontrealBenchMapView.computeDistance(userLat, userLon, b.lat, b.lon);
                        if (d < bestMeters) {
                            bestMeters = d;
                            best = b;
                        }
                    }
                }
            }

            if (best != null) {
                double nextRingMerc = ring * Math.min(cellW, cellH);
                double approxMinDistToNextRing = nextRingMerc * 4400000.0;
                if (bestMeters < approxMinDistToNextRing) {
                    break;
                }
            }

            if (!checkedAny && ring > 5) break;
        }

        if (best == null) {
            for (int i = 0; i < allBenches.size(); i++) {
                Bench b = allBenches.get(i);
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
