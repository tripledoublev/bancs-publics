package map.bench.bancs_publics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * MeetupFinder: Finds and ranks the optimal public benches located halfway
 * between two friends, balancing walking distance fairness and environmental quality (parks).
 */
public class MeetupFinder {

    public static class MeetupBench {
        public final Bench bench;
        public final double distUserMeters;
        public final double distFriendMeters;
        public final double imbalanceMeters;
        public final double score;

        public MeetupBench(Bench bench, double distUserMeters, double distFriendMeters,
                           double imbalanceMeters, double score) {
            this.bench = bench;
            this.distUserMeters = distUserMeters;
            this.distFriendMeters = distFriendMeters;
            this.imbalanceMeters = imbalanceMeters;
            this.score = score;
        }

        public String getFairnessDescription() {
            if (imbalanceMeters < 50) {
                return "Écart : " + Math.round(imbalanceMeters) + " m (Équité parfaite)";
            } else if (imbalanceMeters < 200) {
                return "Écart : " + Math.round(imbalanceMeters) + " m (Très équitable)";
            } else {
                return "Écart : " + formatDistance(imbalanceMeters);
            }
        }

        public String getTravelComparison() {
            return "Vous: " + formatDistance(distUserMeters) + " • Ami: " + formatDistance(distFriendMeters);
        }

        private static String formatDistance(double meters) {
            if (meters < 1000) {
                return Math.round(meters) + " m";
            }
            return String.format(java.util.Locale.US, "%.1f km", meters / 1000.0);
        }
    }

    /**
     * Finds the top N candidate benches in between the user and friend.
     */
    public static List<MeetupBench> findHalfwayBenches(List<Bench> allBenches,
                                                      double userLat, double userLon,
                                                      double friendLat, double friendLon,
                                                      int maxResults) {
        List<MeetupBench> candidates = new ArrayList<>();
        if (allBenches == null || allBenches.isEmpty()) {
            return candidates;
        }

        double directDist = haversine(userLat, userLon, friendLat, friendLon);
        double maxDetourAllowed = Math.max(500.0, directDist * 1.35 + 250.0);
        double maxImbalanceAllowed = Math.max(350.0, directDist * 0.35);

        for (int i = 0; i < allBenches.size(); i++) {
            Bench b = allBenches.get(i);
            double du = haversine(userLat, userLon, b.lat, b.lon);
            double df = haversine(friendLat, friendLon, b.lat, b.lon);
            double total = du + df;
            double imbalance = Math.abs(du - df);

            if (total <= maxDetourAllowed && imbalance <= maxImbalanceAllowed) {
                // Park bonus (fosters pleasant outdoor meeting spots)
                double parkBonus = b.isInPark() ? 150.0 : 0.0;
                double score = total + (1.6 * imbalance) - parkBonus;
                candidates.add(new MeetupBench(b, du, df, imbalance, score));
            }
        }

        // Fallback if no bench met strict criteria: find benches closest to geometric midpoint
        if (candidates.isEmpty()) {
            double midLat = (userLat + friendLat) * 0.5;
            double midLon = (userLon + friendLon) * 0.5;
            for (int i = 0; i < allBenches.size(); i++) {
                Bench b = allBenches.get(i);
                double du = haversine(userLat, userLon, b.lat, b.lon);
                double df = haversine(friendLat, friendLon, b.lat, b.lon);
                double dMid = haversine(midLat, midLon, b.lat, b.lon);
                double imbalance = Math.abs(du - df);
                double score = (dMid * 2.0) + (1.5 * imbalance) - (b.isInPark() ? 100.0 : 0.0);
                candidates.add(new MeetupBench(b, du, df, imbalance, score));
            }
        }

        Collections.sort(candidates, new Comparator<MeetupBench>() {
            @Override
            public int compare(MeetupBench a, MeetupBench b) {
                return Double.compare(a.score, b.score);
            }
        });

        if (candidates.size() > maxResults) {
            return new ArrayList<>(candidates.subList(0, maxResults));
        }
        return candidates;
    }

    public static double haversine(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371000.0; // Earth radius in meters
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double rLat1 = Math.toRadians(lat1);
        double rLat2 = Math.toRadians(lat2);

        double a = Math.sin(dLat * 0.5) * Math.sin(dLat * 0.5)
                + Math.cos(rLat1) * Math.cos(rLat2) * Math.sin(dLon * 0.5) * Math.sin(dLon * 0.5);
        double c = 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a));
        return R * c;
    }
}
