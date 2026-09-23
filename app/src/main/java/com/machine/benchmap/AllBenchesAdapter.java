package com.machine.benchmap;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class AllBenchesAdapter extends RecyclerView.Adapter<AllBenchesAdapter.BenchViewHolder> {

    public interface OnBenchClickListener {
        void onBenchClicked(Bench bench);
    }

    public static class BenchItem {
        public final Bench bench;
        public double distanceMeters;
        public final String title;
        public final String subtitle;
        public final String borough;
        public final boolean isPark;
        public final String searchKey;
        public final String compactSearchKey;

        public BenchItem(Bench b, double dist) {
            this.bench = b;
            this.distanceMeters = dist;
            this.title = b.getDisplayName();
            this.subtitle = b.getDisplaySubtitle();
            this.borough = b.borough != null ? b.borough.trim() : "";
            this.isPark = b.isInPark();

            String combined = title + " " + subtitle + " " + borough + " " + b.street + " " + b.park;
            this.searchKey = normalize(combined);
            this.compactSearchKey = searchKey.replaceAll("[\\s\\-_'’]+", "");
        }
    }

    public enum SortMode {
        PROXIMITY,
        BOROUGH
    }

    private final List<BenchItem> allMasterItems = new ArrayList<>(10000);
    private final List<BenchItem> displayedItems = new ArrayList<>(10000);
    private final OnBenchClickListener clickListener;

    private SortMode currentSortMode = SortMode.PROXIMITY;
    private String currentFilterQuery = "";
    private String currentCompactQuery = "";
    private boolean isDarkMode = false;

    public AllBenchesAdapter(OnBenchClickListener clickListener) {
        this.clickListener = clickListener;
    }

    public void setDarkMode(boolean darkMode) {
        this.isDarkMode = darkMode;
        notifyDataSetChanged();
    }

    public void setBenches(List<Bench> benches, double refLat, double refLon) {
        allMasterItems.clear();
        if (benches != null) {
            for (Bench b : benches) {
                double dist = computeDistance(refLat, refLon, b.lat, b.lon);
                allMasterItems.add(new BenchItem(b, dist));
            }
        }
        applyFilterAndSort();
    }

    public void updateDistances(double refLat, double refLon) {
        for (BenchItem item : allMasterItems) {
            item.distanceMeters = computeDistance(refLat, refLon, item.bench.lat, item.bench.lon);
        }
        if (currentSortMode == SortMode.PROXIMITY) {
            applyFilterAndSort();
        } else {
            notifyDataSetChanged();
        }
    }

    public void setSortMode(SortMode mode) {
        if (this.currentSortMode != mode) {
            this.currentSortMode = mode;
            applyFilterAndSort();
        }
    }

    public SortMode getSortMode() {
        return currentSortMode;
    }

    public void filter(String query) {
        if (query == null || query.trim().isEmpty()) {
            this.currentFilterQuery = "";
            this.currentCompactQuery = "";
        } else {
            this.currentFilterQuery = normalize(query.trim());
            this.currentCompactQuery = this.currentFilterQuery.replaceAll("[\\s\\-_'’]+", "");
        }
        applyFilterAndSort();
    }

    public int getDisplayedCount() {
        return displayedItems.size();
    }

    private void applyFilterAndSort() {
        displayedItems.clear();
        boolean hasFilter = !currentFilterQuery.isEmpty();

        for (BenchItem item : allMasterItems) {
            if (!hasFilter) {
                displayedItems.add(item);
            } else {
                if (item.searchKey.contains(currentFilterQuery)
                        || (!currentCompactQuery.isEmpty() && item.compactSearchKey.contains(currentCompactQuery))) {
                    displayedItems.add(item);
                }
            }
        }

        if (currentSortMode == SortMode.PROXIMITY) {
            Collections.sort(displayedItems, (a, b) -> Double.compare(a.distanceMeters, b.distanceMeters));
        } else {
            Collections.sort(displayedItems, (a, b) -> {
                int cmp = a.borough.compareToIgnoreCase(b.borough);
                if (cmp != 0) return cmp;
                return a.title.compareToIgnoreCase(b.title);
            });
        }

        notifyDataSetChanged();
    }

    private static String normalize(String input) {
        if (input == null) return "";
        String n = java.text.Normalizer.normalize(input, java.text.Normalizer.Form.NFD);
        return n.replaceAll("\\p{M}", "").toLowerCase(Locale.FRENCH);
    }

    @NonNull
    @Override
    public BenchViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_bench_inventory, parent, false);
        return new BenchViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull BenchViewHolder holder, int position) {
        BenchItem item = displayedItems.get(position);
        holder.bind(item, currentSortMode, isDarkMode, clickListener);
    }

    @Override
    public int getItemCount() {
        return displayedItems.size();
    }

    public static class BenchViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvIcon;
        private final TextView tvTitle;
        private final TextView tvSubtitle;
        private final TextView tvBadge;
        private final TextView tvChevron;

        public BenchViewHolder(@NonNull View itemView) {
            super(itemView);
            tvIcon = itemView.findViewById(R.id.tv_item_icon);
            tvTitle = itemView.findViewById(R.id.tv_item_title);
            tvSubtitle = itemView.findViewById(R.id.tv_item_subtitle);
            tvBadge = itemView.findViewById(R.id.tv_item_badge);
            tvChevron = itemView.findViewById(R.id.tv_item_chevron);
        }

        public void bind(BenchItem item, SortMode sortMode, boolean darkMode, OnBenchClickListener listener) {
            tvTitle.setText(item.title);
            tvSubtitle.setText(item.subtitle);

            // Icon representation
            if (item.bench.isCustom) {
                tvIcon.setText("📍");
            } else if (item.isPark) {
                tvIcon.setText("🌳");
            } else {
                tvIcon.setText("🪑");
            }

            // Theme colors
            int textPri = darkMode ? Color.parseColor("#F4F5F7") : Color.parseColor("#111318");
            int textSec = darkMode ? Color.parseColor("#8E93A0") : Color.parseColor("#667085");
            int textMuted = darkMode ? Color.parseColor("#64748B") : Color.parseColor("#94A3B8");

            tvTitle.setTextColor(textPri);
            tvSubtitle.setTextColor(textSec);
            tvChevron.setTextColor(textMuted);

            // Icon background
            int iconBg = darkMode ? Color.parseColor("#20232B") : Color.parseColor("#F2F4F7");
            int iconBorder = darkMode ? Color.parseColor("#2C303B") : Color.parseColor("#E4E7EC");
            GradientDrawable iconDrawable = new GradientDrawable();
            iconDrawable.setCornerRadius(10 * itemView.getResources().getDisplayMetrics().density);
            iconDrawable.setColor(iconBg);
            iconDrawable.setStroke(1, iconBorder);
            tvIcon.setBackground(iconDrawable);

            // Badge text & background
            GradientDrawable badgeDrawable = new GradientDrawable();
            badgeDrawable.setCornerRadius(12 * itemView.getResources().getDisplayMetrics().density);

            if (sortMode == SortMode.PROXIMITY) {
                String distStr;
                if (item.distanceMeters < 1000) {
                    distStr = (int) item.distanceMeters + " m";
                } else {
                    distStr = String.format(Locale.FRENCH, "%.1f km", item.distanceMeters / 1000.0);
                }
                tvBadge.setText(distStr);

                int badgeBg = darkMode ? Color.parseColor("#2E1214") : Color.parseColor("#FEF2F2");
                int badgeStroke = darkMode ? Color.parseColor("#481B1F") : Color.parseColor("#FECACA");
                int badgeText = darkMode ? Color.parseColor("#FF6467") : Color.parseColor("#DE3831");
                badgeDrawable.setColor(badgeBg);
                badgeDrawable.setStroke(1, badgeStroke);
                tvBadge.setBackground(badgeDrawable);
                tvBadge.setTextColor(badgeText);
            } else {
                String bName = item.borough.isEmpty() ? "Montréal" : item.borough;
                // Truncate long borough names for clean pill display
                if (bName.length() > 14) {
                    bName = bName.substring(0, 12) + "…";
                }
                tvBadge.setText(bName);

                int badgeBg = darkMode ? Color.parseColor("#20232B") : Color.parseColor("#F2F4F7");
                int badgeStroke = darkMode ? Color.parseColor("#2C303B") : Color.parseColor("#E4E7EC");
                badgeDrawable.setColor(badgeBg);
                badgeDrawable.setStroke(1, badgeStroke);
                tvBadge.setBackground(badgeDrawable);
                tvBadge.setTextColor(textSec);
            }

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onBenchClicked(item.bench);
                }
            });
        }
    }

    private static double computeDistance(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return 6371000.0 * c;
    }
}
