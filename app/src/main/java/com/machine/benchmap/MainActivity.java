package com.machine.benchmap;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.Locale;

/**
 * MainActivity - Montreal BenchMap v0.1.0
 * Minimalist Swiss UX, interactive filters, compass orientation, and fast navigation.
 */
public class MainActivity extends AppCompatActivity implements MontrealBenchMapView.BenchMapListener, SensorEventListener {

    private static final int PERMISSION_REQ_CODE = 101;

    private MontrealBenchMapView benchMapView;
    private View layoutHeader;
    private View layoutBottomControls;
    private TextView tvBenchCounter;

    // Filter Chips
    private MaterialButton chipFilterAll;
    private MaterialButton chipFilterParks;
    private MaterialButton chipFilterBackrest;
    private MaterialButton chipFilterWood;
    private MaterialButton[] filterChips;

    // Floating Action Controls
    private FloatingActionButton fabMyLocation;
    private MaterialButton btnNearest;

    // Detail Card
    private CardView cardDetail;
    private TextView tvBenchName, tvBenchType, tvBenchDistance, tvBenchCoords;
    private TextView tagMaterial, tagBackrest, tagSeats;
    private ImageView btnCloseDetail;
    private MaterialButton btnNavigate, btnShare;

    // Hardware Services
    private LocationManager locationManager;
    private SensorManager sensorManager;
    private Sensor rotationSensor;
    private Vibrator vibrator;

    private Location lastLocation = null;
    private Bench currentlySelectedBench = null;
    private int currentFilter = SpatialBenchIndex.FILTER_ALL;

    // Compass calculations
    private final float[] rotationMatrix = new float[9];
    private final float[] orientationAngles = new float[3];
    private long lastSensorUpdate = 0;

    private final LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(@NonNull Location location) {
            lastLocation = location;
            benchMapView.setUserLocation(location);
        }

        @Override
        public void onProviderEnabled(@NonNull String provider) {}

        @Override
        public void onProviderDisabled(@NonNull String provider) {}
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        }

        bindViews();
        setupWindowInsets();
        setupFilters();
        setupActions();

        benchMapView.setMapListener(this);
        checkLocationPermission();
    }

    private void bindViews() {
        benchMapView = findViewById(R.id.bench_map_view);
        layoutHeader = findViewById(R.id.layout_header);
        layoutBottomControls = findViewById(R.id.layout_bottom_controls);
        tvBenchCounter = findViewById(R.id.tv_bench_counter);

        chipFilterAll = findViewById(R.id.chip_filter_all);
        chipFilterParks = findViewById(R.id.chip_filter_parks);
        chipFilterBackrest = findViewById(R.id.chip_filter_backrest);
        chipFilterWood = findViewById(R.id.chip_filter_wood);
        filterChips = new MaterialButton[]{chipFilterAll, chipFilterParks, chipFilterBackrest, chipFilterWood};

        fabMyLocation = findViewById(R.id.fab_my_location);
        btnNearest = findViewById(R.id.btn_nearest);

        cardDetail = findViewById(R.id.card_detail);
        tvBenchName = findViewById(R.id.tv_bench_name);
        tvBenchType = findViewById(R.id.tv_bench_type);
        tvBenchDistance = findViewById(R.id.tv_bench_distance);
        tvBenchCoords = findViewById(R.id.tv_bench_coords);
        tagMaterial = findViewById(R.id.tag_material);
        tagBackrest = findViewById(R.id.tag_backrest);
        tagSeats = findViewById(R.id.tag_seats);
        btnCloseDetail = findViewById(R.id.btn_close_detail);
        btnNavigate = findViewById(R.id.btn_navigate);
        btnShare = findViewById(R.id.btn_share);
    }

    private void setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content), (v, windowInsets) -> {
            Insets statusInsets = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars());
            Insets navInsets = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars());

            float d = getResources().getDisplayMetrics().density;
            if (layoutHeader != null) {
                layoutHeader.setPadding(
                        layoutHeader.getPaddingLeft(),
                        statusInsets.top + (int) (8 * d),
                        layoutHeader.getPaddingRight(),
                        layoutHeader.getPaddingBottom()
                );
            }

            if (cardDetail != null && cardDetail.getLayoutParams() instanceof FrameLayout.LayoutParams) {
                FrameLayout.LayoutParams cardLp = (FrameLayout.LayoutParams) cardDetail.getLayoutParams();
                cardLp.bottomMargin = navInsets.bottom + (int) (14 * d);
                cardDetail.setLayoutParams(cardLp);
            }

            if (layoutBottomControls != null && layoutBottomControls.getLayoutParams() instanceof FrameLayout.LayoutParams) {
                FrameLayout.LayoutParams btnLp = (FrameLayout.LayoutParams) layoutBottomControls.getLayoutParams();
                btnLp.bottomMargin = navInsets.bottom + (int) (20 * d);
                layoutBottomControls.setLayoutParams(btnLp);
            }

            return windowInsets;
        });
    }

    private void setupFilters() {
        chipFilterAll.setOnClickListener(v -> selectFilter(SpatialBenchIndex.FILTER_ALL, chipFilterAll));
        chipFilterParks.setOnClickListener(v -> selectFilter(SpatialBenchIndex.FILTER_PARKS, chipFilterParks));
        chipFilterBackrest.setOnClickListener(v -> selectFilter(SpatialBenchIndex.FILTER_BACKREST, chipFilterBackrest));
        chipFilterWood.setOnClickListener(v -> selectFilter(SpatialBenchIndex.FILTER_WOOD, chipFilterWood));
    }

    private void selectFilter(int filterMode, MaterialButton selectedBtn) {
        triggerHapticTick();
        this.currentFilter = filterMode;

        int activeBg = ContextCompat.getColor(this, R.color.swiss_black);
        int inactiveBg = ContextCompat.getColor(this, R.color.bg_card);
        int activeText = Color.WHITE;
        int inactiveText = ContextCompat.getColor(this, R.color.text_secondary);
        int inactiveBorder = ContextCompat.getColor(this, R.color.card_border);

        for (MaterialButton btn : filterChips) {
            if (btn == selectedBtn) {
                btn.setBackgroundTintList(ColorStateList.valueOf(activeBg));
                btn.setTextColor(activeText);
                btn.setStrokeColor(ColorStateList.valueOf(activeBg));
            } else {
                btn.setBackgroundTintList(ColorStateList.valueOf(inactiveBg));
                btn.setTextColor(inactiveText);
                btn.setStrokeColor(ColorStateList.valueOf(inactiveBorder));
            }
        }

        benchMapView.setFilter(filterMode);
        updateCounterDisplay();
    }

    private void updateCounterDisplay() {
        int count = benchMapView.getFilteredCount();
        String formatted = String.format(Locale.CANADA_FRENCH, "%,d BANCS", count).replace(',', ' ');
        tvBenchCounter.setText(formatted);
    }

    private String formatCount(int num) {
        return String.format(Locale.CANADA_FRENCH, "%,d", num).replace(',', ' ');
    }

    private void setupActions() {
        fabMyLocation.setOnClickListener(v -> {
            triggerHapticTick();
            if (lastLocation != null) {
                benchMapView.centerOnUser();
            } else {
                checkLocationPermission();
                Toast.makeText(this, "Recherche du signal GPS...", Toast.LENGTH_SHORT).show();
            }
        });

        btnNearest.setOnClickListener(v -> {
            triggerHapticTick();
            Bench nearest = benchMapView.findNearestBench();
            if (nearest == null) {
                Toast.makeText(this, "Aucun banc correspondant au filtre actuel", Toast.LENGTH_SHORT).show();
            }
        });

        btnCloseDetail.setOnClickListener(v -> {
            triggerHapticTick();
            benchMapView.deselectBench();
        });

        tvBenchCoords.setOnClickListener(v -> {
            if (currentlySelectedBench != null) {
                triggerHapticTick();
                copyToClipboard("Coordonnées du banc",
                        String.format(Locale.US, "%.5f, %.5f", currentlySelectedBench.lat, currentlySelectedBench.lon));
                Toast.makeText(this, "Coordonnées copiées dans le presse-papier", Toast.LENGTH_SHORT).show();
            }
        });

        btnNavigate.setOnClickListener(v -> {
            if (currentlySelectedBench != null) {
                triggerHapticTick();
                openNavigation(currentlySelectedBench.lat, currentlySelectedBench.lon);
            }
        });

        btnShare.setOnClickListener(v -> {
            if (currentlySelectedBench != null) {
                triggerHapticTick();
                shareBench(currentlySelectedBench);
            }
        });
    }

    @Override
    public void onMapLoaded(int totalBenches) {
        runOnUiThread(() -> {
            updateCounterDisplay();
            int parkCount = benchMapView.getCountForFilter(SpatialBenchIndex.FILTER_PARKS);
            int backrestCount = benchMapView.getCountForFilter(SpatialBenchIndex.FILTER_BACKREST);
            int woodCount = benchMapView.getCountForFilter(SpatialBenchIndex.FILTER_WOOD);

            chipFilterAll.setText("Tous (" + formatCount(totalBenches) + ")");
            chipFilterParks.setText("🌲 Parcs (" + formatCount(parkCount) + ")");
            chipFilterBackrest.setText("💺 Dossier (" + formatCount(backrestCount) + ")");
            chipFilterWood.setText("🪵 Bois (" + formatCount(woodCount) + ")");
        });
    }

    @Override
    public void onBenchSelected(Bench bench, double distanceMeters) {
        this.currentlySelectedBench = bench;
        triggerHapticTick();

        runOnUiThread(() -> {
            boolean inPark = bench.isInPark();
            tvBenchName.setText(inPark ? bench.park : "Banc Public de Rue");
            tvBenchType.setText(inPark ? "Banc de parc public montréalais" : "Mobilier urbain de voirie");
            tvBenchCoords.setText(String.format(Locale.US, "%.5f° N, %.5f° W", bench.lat, Math.abs(bench.lon)));

            if (distanceMeters > 0) {
                if (distanceMeters < 1000) {
                    tvBenchDistance.setText(String.format(Locale.CANADA_FRENCH, "%.0f m", distanceMeters));
                } else {
                    tvBenchDistance.setText(String.format(Locale.CANADA_FRENCH, "%.1f km", distanceMeters / 1000.0));
                }
                tvBenchDistance.setVisibility(View.VISIBLE);
            } else {
                tvBenchDistance.setVisibility(View.GONE);
            }

            tagMaterial.setText("Matériau: " + bench.getFormattedMaterial());
            tagBackrest.setText("Dossier: " + bench.getFormattedBackrest());
            if (bench.seats == 1) {
                tagSeats.setText("1 place");
            } else if (bench.seats > 1) {
                tagSeats.setText(bench.seats + " places");
            } else {
                tagSeats.setText("Places: Standard");
            }

            cardDetail.setVisibility(View.VISIBLE);
        });
    }

    @Override
    public void onBenchDeselected() {
        this.currentlySelectedBench = null;
        runOnUiThread(() -> cardDetail.setVisibility(View.GONE));
    }

    private void openNavigation(double lat, double lon) {
        try {
            Uri navUri = Uri.parse("google.navigation:q=" + lat + "," + lon + "&mode=w");
            Intent navIntent = new Intent(Intent.ACTION_VIEW, navUri);
            navIntent.setPackage("com.google.android.apps.maps");
            if (navIntent.resolveActivity(getPackageManager()) != null) {
                startActivity(navIntent);
                return;
            }
        } catch (Exception ignored) {}

        try {
            Uri geoUri = Uri.parse("geo:" + lat + "," + lon + "?q=" + lat + "," + lon + "(Banc+Public)");
            Intent geoIntent = new Intent(Intent.ACTION_VIEW, geoUri);
            if (geoIntent.resolveActivity(getPackageManager()) != null) {
                startActivity(geoIntent);
                return;
            }
        } catch (Exception ignored) {}

        try {
            Uri webUri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=" + lat + "," + lon + "&travelmode=walking");
            Intent webIntent = new Intent(Intent.ACTION_VIEW, webUri);
            startActivity(webIntent);
        } catch (Exception e) {
            Toast.makeText(this, "Impossible d'ouvrir l'application de cartographie", Toast.LENGTH_SHORT).show();
        }
    }

    private void shareBench(Bench bench) {
        try {
            String title = bench.isInPark() ? bench.park : "Banc Public de Rue";
            String shareText = String.format(Locale.US,
                    "📍 %s (Montréal)\nCoordonnées: %.5f, %.5f\nhttps://maps.google.com/?q=%.5f,%.5f",
                    title, bench.lat, bench.lon, bench.lat, bench.lon);

            Intent sendIntent = new Intent(Intent.ACTION_SEND);
            sendIntent.putExtra(Intent.EXTRA_TEXT, shareText);
            sendIntent.setType("text/plain");
            startActivity(Intent.createChooser(sendIntent, "Partager l'emplacement du banc"));
        } catch (Exception e) {
            Toast.makeText(this, "Erreur lors du partage", Toast.LENGTH_SHORT).show();
        }
    }

    private void copyToClipboard(String label, String text) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            ClipData clip = ClipData.newPlainText(label, text);
            clipboard.setPrimaryClip(clip);
        }
    }

    private void triggerHapticTick() {
        if (vibrator != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(18, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(18);
            }
        }
    }

    private void checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                    PERMISSION_REQ_CODE);
        } else {
            startLocationUpdates();
        }
    }

    @SuppressLint("MissingPermission")
    private void startLocationUpdates() {
        try {
            if (locationManager != null) {
                if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    locationManager.requestLocationUpdates(
                            LocationManager.GPS_PROVIDER,
                            1000,
                            1.0f,
                            locationListener
                    );
                }
                if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    locationManager.requestLocationUpdates(
                            LocationManager.NETWORK_PROVIDER,
                            2000,
                            2.0f,
                            locationListener
                    );
                }

                Location bestLoc = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                Location netLoc = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                if (netLoc != null && (bestLoc == null || netLoc.getTime() > bestLoc.getTime())) {
                    bestLoc = netLoc;
                }

                if (bestLoc != null) {
                    lastLocation = bestLoc;
                    benchMapView.setUserLocation(bestLoc);
                }
            }
        } catch (Exception ignored) {}
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQ_CODE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startLocationUpdates();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (sensorManager != null && rotationSensor != null) {
            sensorManager.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_UI);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            startLocationUpdates();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
        if (locationManager != null) {
            locationManager.removeUpdates(locationListener);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            long now = System.currentTimeMillis();
            if (now - lastSensorUpdate < 60) return; // 15 Hz throttle
            lastSensorUpdate = now;

            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);
            SensorManager.getOrientation(rotationMatrix, orientationAngles);

            float azimuthRad = orientationAngles[0];
            float azimuthDeg = (float) Math.toDegrees(azimuthRad);
            if (azimuthDeg < 0) azimuthDeg += 360f;

            benchMapView.setUserHeading(azimuthDeg);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}
}
