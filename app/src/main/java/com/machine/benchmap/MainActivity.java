package com.machine.benchmap;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
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
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Window;
import android.widget.EditText;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.ListView;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * MainActivity - Montreal BenchMap v0.1.0
 * Pure Swiss cartographic experience with exact street names and zero-clutter layout.
 */
public class MainActivity extends AppCompatActivity implements MontrealBenchMapView.BenchMapListener, SensorEventListener {

    private static final int PERMISSION_REQ_CODE = 101;
    private static final String PREFS_NAME = "benchmap_prefs";
    private static final String PREF_DARK_MODE = "key_dark_mode";

    private MontrealBenchMapView benchMapView;
    private View layoutHeader;
    private View layoutBottomControls;
    private TextView tvBenchCounter;

    // Header Swiss elements
    private MaterialCardView cardHeader;
    private TextView tvAppTitle;
    private ImageView btnThemeToggle;
    private ImageView btnMeetup;
    private boolean isDarkMode = false;

    // Rendez-vous (Meetup) Banner
    private MaterialCardView cardMeetupBanner;
    private TextView tvMeetupBannerTitle, tvMeetupBannerSubtitle;
    private MaterialButton btnMeetupCycle;
    private ImageView btnMeetupClose;

    // Meetup state
    private Double friendLat = null;
    private Double friendLon = null;
    private int friendBlurMeters = 0;
    private final List<MeetupFinder.MeetupBench> currentHalfwayBenches = new ArrayList<>();
    private int currentMeetupBenchIndex = 0;
    private int selectedBlurRadius = MeetupKey.BLUR_DISTRICT;

    // Controls
    private FloatingActionButton fabMyLocation;
    private MaterialButton btnSearch;
    private MaterialButton btnRandom;
    private MaterialButton btnNearest;

    // Detail Card
    private MaterialCardView cardDetail;
    private TextView tvBenchName, tvBenchType, tvBenchDistance, tvBenchCoords;
    private TextView tagMaterial, tagBackrest, tagSeats;
    private ImageView btnCloseDetail, btnCardRandom;
    private MaterialButton btnNavigate, btnShare;

    // Hardware Services
    private LocationManager locationManager;
    private SensorManager sensorManager;
    private Sensor rotationSensor;
    private Vibrator vibrator;

    private Location lastLocation = null;
    private Bench currentlySelectedBench = null;

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

        Window window = getWindow();
        WindowCompat.setDecorFitsSystemWindows(window, false);
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        }

        setContentView(R.layout.activity_main);

        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        }

        bindViews();
        setupWindowInsets();
        initTheme();
        setupActions();

        benchMapView.setMapListener(this);
        checkLocationPermission();
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void bindViews() {
        benchMapView = findViewById(R.id.bench_map_view);
        layoutHeader = findViewById(R.id.layout_header);
        cardHeader = findViewById(R.id.card_header);
        tvAppTitle = findViewById(R.id.tv_app_title);
        layoutBottomControls = findViewById(R.id.layout_bottom_controls);
        tvBenchCounter = findViewById(R.id.tv_bench_counter);
        btnThemeToggle = findViewById(R.id.btn_theme_toggle);
        btnMeetup = findViewById(R.id.btn_meetup);

        cardMeetupBanner = findViewById(R.id.card_meetup_banner);
        tvMeetupBannerTitle = findViewById(R.id.tv_meetup_banner_title);
        tvMeetupBannerSubtitle = findViewById(R.id.tv_meetup_banner_subtitle);
        btnMeetupCycle = findViewById(R.id.btn_meetup_cycle);
        btnMeetupClose = findViewById(R.id.btn_meetup_close);

        fabMyLocation = findViewById(R.id.fab_my_location);
        btnSearch = findViewById(R.id.btn_search);
        btnNearest = findViewById(R.id.btn_nearest);
        btnRandom = findViewById(R.id.btn_random);

        cardDetail = findViewById(R.id.card_detail);
        tvBenchName = findViewById(R.id.tv_bench_name);
        tvBenchType = findViewById(R.id.tv_bench_type);
        tvBenchDistance = findViewById(R.id.tv_bench_distance);
        tvBenchCoords = findViewById(R.id.tv_bench_coords);
        tagMaterial = findViewById(R.id.tag_material);
        tagBackrest = findViewById(R.id.tag_backrest);
        tagSeats = findViewById(R.id.tag_seats);
        btnCloseDetail = findViewById(R.id.btn_close_detail);
        btnCardRandom = findViewById(R.id.btn_card_random);
        btnNavigate = findViewById(R.id.btn_navigate);
        btnShare = findViewById(R.id.btn_share);
    }

    private void setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(getWindow().getDecorView(), (v, windowInsets) -> {
            Insets statusInsets = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars());
            Insets navInsets = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars());

            if (layoutHeader != null) {
                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) layoutHeader.getLayoutParams();
                lp.topMargin = statusInsets.top + (int) (12 * getResources().getDisplayMetrics().density);
                layoutHeader.setLayoutParams(lp);
            }

            if (layoutBottomControls != null) {
                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) layoutBottomControls.getLayoutParams();
                lp.bottomMargin = navInsets.bottom + (int) (20 * getResources().getDisplayMetrics().density);
                layoutBottomControls.setLayoutParams(lp);
            }

            if (cardDetail != null) {
                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) cardDetail.getLayoutParams();
                lp.bottomMargin = navInsets.bottom + (int) (16 * getResources().getDisplayMetrics().density);
                cardDetail.setLayoutParams(lp);
            }

            return windowInsets;
        });
    }

    private void initTheme() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean defaultDark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        boolean initialDark = prefs.getBoolean(PREF_DARK_MODE, defaultDark);
        applyTheme(initialDark);
    }

    private void applyTheme(boolean darkMode) {
        this.isDarkMode = darkMode;
        benchMapView.setDarkMode(darkMode);

        int bgCard = darkMode ? Color.parseColor("#16171B") : Color.parseColor("#FFFFFF");
        int borderCard = darkMode ? Color.parseColor("#262932") : Color.parseColor("#E4E7EC");
        int textPrimary = darkMode ? Color.parseColor("#F4F5F7") : Color.parseColor("#111318");
        int textMuted = darkMode ? Color.parseColor("#8E93A0") : Color.parseColor("#667085");
        int chipBg = darkMode ? Color.parseColor("#20232B") : Color.parseColor("#F2F4F7");
        int chipStroke = darkMode ? Color.parseColor("#2C303B") : Color.parseColor("#E4E7EC");
        int chipText = darkMode ? Color.parseColor("#F4F5F7") : Color.parseColor("#344054");
        float d = getResources().getDisplayMetrics().density;

        // 1. Header Card
        if (cardHeader != null) {
            cardHeader.setCardBackgroundColor(bgCard);
            cardHeader.setStrokeColor(borderCard);
        }
        if (tvAppTitle != null) {
            tvAppTitle.setTextColor(textPrimary);
        }
        if (tvBenchCounter != null) {
            GradientDrawable counterDrawable = new GradientDrawable();
            counterDrawable.setShape(GradientDrawable.RECTANGLE);
            counterDrawable.setCornerRadius(12f * d);
            counterDrawable.setColor(chipBg);
            counterDrawable.setStroke((int) (1f * d), borderCard);
            tvBenchCounter.setBackground(counterDrawable);
            tvBenchCounter.setTextColor(textPrimary);
        }
        if (btnThemeToggle != null) {
            btnThemeToggle.setImageResource(darkMode ? R.drawable.ic_theme_sun : R.drawable.ic_theme_moon);
            btnThemeToggle.setImageTintList(ColorStateList.valueOf(darkMode ? Color.parseColor("#F59E0B") : Color.parseColor("#111318")));
        }
        if (btnMeetup != null) {
            btnMeetup.setImageTintList(ColorStateList.valueOf(darkMode ? Color.parseColor("#60A5FA") : Color.parseColor("#2563EB")));
        }

        // Rendez-vous Banner Styling
        if (cardMeetupBanner != null) {
            cardMeetupBanner.setCardBackgroundColor(bgCard);
            cardMeetupBanner.setStrokeColor(darkMode ? Color.parseColor("#3B82F6") : Color.parseColor("#2563EB"));
        }
        if (tvMeetupBannerTitle != null) {
            tvMeetupBannerTitle.setTextColor(darkMode ? Color.parseColor("#60A5FA") : Color.parseColor("#2563EB"));
        }
        if (tvMeetupBannerSubtitle != null) {
            tvMeetupBannerSubtitle.setTextColor(textMuted);
        }
        if (btnMeetupCycle != null) {
            btnMeetupCycle.setBackgroundColor(chipBg);
            btnMeetupCycle.setTextColor(darkMode ? Color.parseColor("#60A5FA") : Color.parseColor("#2563EB"));
            btnMeetupCycle.setStrokeColor(ColorStateList.valueOf(borderCard));
        }

        // 2. Bottom Floating Controls
        if (fabMyLocation != null) {
            fabMyLocation.setBackgroundTintList(ColorStateList.valueOf(bgCard));
            fabMyLocation.setImageTintList(ColorStateList.valueOf(textPrimary));
        }
        if (btnSearch != null) {
            btnSearch.setBackgroundColor(bgCard);
            btnSearch.setTextColor(textPrimary);
            btnSearch.setStrokeColor(ColorStateList.valueOf(borderCard));
        }
        if (btnRandom != null) {
            btnRandom.setBackgroundColor(bgCard);
            btnRandom.setTextColor(textPrimary);
            btnRandom.setStrokeColor(ColorStateList.valueOf(borderCard));
        }
        if (btnNearest != null) {
            btnNearest.setBackgroundColor(darkMode ? Color.parseColor("#F8FAFC") : Color.parseColor("#0F172A"));
            btnNearest.setTextColor(darkMode ? Color.parseColor("#0F172A") : Color.parseColor("#FFFFFF"));
        }

        // 3. Detail Card
        if (cardDetail != null) {
            cardDetail.setCardBackgroundColor(bgCard);
            cardDetail.setStrokeColor(borderCard);
        }
        if (tvBenchName != null) {
            tvBenchName.setTextColor(textPrimary);
        }
        if (tvBenchType != null) {
            tvBenchType.setTextColor(textMuted);
        }
        if (tvBenchCoords != null) {
            tvBenchCoords.setTextColor(textMuted);
        }

        // Circle Buttons
        GradientDrawable circleBtn1 = new GradientDrawable();
        circleBtn1.setShape(GradientDrawable.OVAL);
        circleBtn1.setColor(chipBg);
        if (btnCloseDetail != null) {
            btnCloseDetail.setBackground(circleBtn1);
            btnCloseDetail.setImageTintList(ColorStateList.valueOf(textMuted));
        }

        GradientDrawable circleBtn2 = new GradientDrawable();
        circleBtn2.setShape(GradientDrawable.OVAL);
        circleBtn2.setColor(chipBg);
        if (btnCardRandom != null) {
            btnCardRandom.setBackground(circleBtn2);
            btnCardRandom.setImageTintList(ColorStateList.valueOf(textMuted));
        }

        // Attribute Chips
        GradientDrawable d1 = new GradientDrawable();
        d1.setShape(GradientDrawable.RECTANGLE);
        d1.setCornerRadius(10f * d);
        d1.setColor(chipBg);
        d1.setStroke((int) (1f * d), chipStroke);
        if (tagMaterial != null) {
            tagMaterial.setBackground(d1);
            tagMaterial.setTextColor(chipText);
        }

        GradientDrawable d2 = new GradientDrawable();
        d2.setShape(GradientDrawable.RECTANGLE);
        d2.setCornerRadius(10f * d);
        d2.setColor(chipBg);
        d2.setStroke((int) (1f * d), chipStroke);
        if (tagBackrest != null) {
            tagBackrest.setBackground(d2);
            tagBackrest.setTextColor(chipText);
        }

        GradientDrawable d3 = new GradientDrawable();
        d3.setShape(GradientDrawable.RECTANGLE);
        d3.setCornerRadius(10f * d);
        d3.setColor(chipBg);
        d3.setStroke((int) (1f * d), chipStroke);
        if (tagSeats != null) {
            tagSeats.setBackground(d3);
            tagSeats.setTextColor(chipText);
        }

        // Distance chip in detail card
        if (tvBenchDistance != null) {
            GradientDrawable distD = new GradientDrawable();
            distD.setShape(GradientDrawable.RECTANGLE);
            distD.setCornerRadius(12f * d);
            distD.setColor(darkMode ? Color.parseColor("#2E1214") : Color.parseColor("#FEF3F2"));
            distD.setStroke((int) (1f * d), darkMode ? Color.parseColor("#481B1F") : Color.parseColor("#FECDCA"));
            tvBenchDistance.setBackground(distD);
            tvBenchDistance.setTextColor(darkMode ? Color.parseColor("#FF6467") : Color.parseColor("#D92D20"));
        }

        // Share button
        if (btnShare != null) {
            btnShare.setBackgroundColor(chipBg);
            btnShare.setTextColor(textPrimary);
            btnShare.setStrokeColor(ColorStateList.valueOf(borderCard));
        }

        // 4. Status Bar & Navigation Bar Appearance
        try {
            Window window = getWindow();
            WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(window, window.getDecorView());
            if (controller != null) {
                controller.setAppearanceLightStatusBars(!darkMode);
                controller.setAppearanceLightNavigationBars(!darkMode);
            }
        } catch (Exception ignored) {}
    }

    private void setupActions() {
        btnThemeToggle.setOnClickListener(v -> {
            triggerHapticTick();
            boolean newMode = !isDarkMode;
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .edit()
                    .putBoolean(PREF_DARK_MODE, newMode)
                    .apply();
            applyTheme(newMode);
        });

        fabMyLocation.setOnClickListener(v -> {
            triggerHapticTick();
            if (lastLocation != null) {
                benchMapView.centerOnUser();
            } else {
                checkLocationPermission();
                Toast.makeText(this, "Recherche du signal GPS...", Toast.LENGTH_SHORT).show();
            }
        });

        btnSearch.setOnClickListener(v -> {
            triggerHapticTick();
            showSearchDialog();
        });

        btnNearest.setOnClickListener(v -> {
            triggerHapticTick();
            Bench nearest = benchMapView.findNearestBench();
            if (nearest == null) {
                Toast.makeText(this, "Aucun banc trouvé", Toast.LENGTH_SHORT).show();
            }
        });

        btnRandom.setOnClickListener(v -> {
            triggerHapticTick();
            Bench rand = benchMapView.selectRandomBench();
            if (rand == null) {
                Toast.makeText(this, "Chargement des bancs...", Toast.LENGTH_SHORT).show();
            }
        });

        btnCloseDetail.setOnClickListener(v -> {
            triggerHapticTick();
            benchMapView.deselectBench();
        });

        btnCardRandom.setOnClickListener(v -> {
            triggerHapticTick();
            Bench rand = benchMapView.selectRandomBench();
            if (rand == null) {
                Toast.makeText(this, "Chargement des bancs...", Toast.LENGTH_SHORT).show();
            }
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

        if (btnMeetup != null) {
            btnMeetup.setOnClickListener(v -> {
                triggerHapticTick();
                showMeetupDialog();
            });
        }

        if (btnMeetupCycle != null) {
            btnMeetupCycle.setOnClickListener(v -> {
                triggerHapticTick();
                cycleNextMeetupBench();
            });
        }

        if (btnMeetupClose != null) {
            btnMeetupClose.setOnClickListener(v -> {
                triggerHapticTick();
                clearMeetupMode();
            });
        }
    }

    @Override
    public void onMapLoaded(int totalBenches) {
        runOnUiThread(() -> {
            String formatted = String.format(Locale.CANADA_FRENCH, "%,d BANCS", totalBenches).replace(',', ' ');
            tvBenchCounter.setText(formatted);
        });
    }

    @Override
    public void onBenchSelected(Bench bench, double distanceMeters) {
        this.currentlySelectedBench = bench;
        triggerHapticTick();

        runOnUiThread(() -> {
            tvBenchName.setText(bench.getDisplayName());
            tvBenchType.setText(bench.getDisplaySubtitle());
            tvBenchCoords.setText(String.format(Locale.US, "%.5f° N, %.5f° W", bench.lat, Math.abs(bench.lon)));

            if (benchMapView.isMeetupActive() && friendLat != null && friendLon != null) {
                double uLat = (lastLocation != null) ? lastLocation.getLatitude() : MontrealBenchMapView.CENTER_LAT;
                double uLon = (lastLocation != null) ? lastLocation.getLongitude() : MontrealBenchMapView.CENTER_LON;
                double du = MeetupFinder.haversine(uLat, uLon, bench.lat, bench.lon);
                double df = MeetupFinder.haversine(friendLat, friendLon, bench.lat, bench.lon);
                double imb = Math.abs(du - df);

                tvBenchDistance.setText(String.format(Locale.CANADA_FRENCH, "Vous: %s • Ami: %s",
                        formatMeters(du), formatMeters(df)));
                tvBenchDistance.setVisibility(View.VISIBLE);

                String fairDesc = (imb < 50) ? "Écart " + Math.round(imb) + " m (Équité parfaite)" :
                        "Écart " + formatMeters(imb) + " (" + (imb < 200 ? "Très équitable" : "Mi-chemin") + ")";
                tvBenchCoords.setText(String.format(Locale.US, "%.5f° N, %.5f° W • %s", bench.lat, Math.abs(bench.lon), fairDesc));
            } else if (distanceMeters > 0) {
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
                tagSeats.setText("Places: 2–3 (standard)");
            }

            cardDetail.setVisibility(View.VISIBLE);
            if (layoutBottomControls != null) {
                layoutBottomControls.setVisibility(View.GONE);
            }
        });
    }

    @Override
    public void onBenchDeselected() {
        this.currentlySelectedBench = null;
        runOnUiThread(() -> {
            cardDetail.setVisibility(View.GONE);
            if (layoutBottomControls != null) {
                layoutBottomControls.setVisibility(View.VISIBLE);
            }
        });
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
            String title = bench.getDisplayName();
            String locationDesc = !bench.borough.isEmpty() ? (title + ", " + bench.borough + " (Montréal)") : (title + " (Montréal)");

            String seatsStr = (bench.seats == 1) ? "1 place" : ((bench.seats > 1) ? (bench.seats + " places") : "2–3 places (standard)");

            StringBuilder sb = new StringBuilder();
            sb.append("📍 ").append(locationDesc).append("\n");
            sb.append("Matériau: ").append(bench.getFormattedMaterial()).append("\n");
            sb.append("Dossier: ").append(bench.getFormattedBackrest()).append("\n");
            sb.append("Places: ").append(seatsStr).append("\n");
            sb.append(String.format(Locale.US, "Coordonnées: %.5f, %.5f\n", bench.lat, bench.lon));
            sb.append(String.format(Locale.US, "https://maps.google.com/?q=%.5f,%.5f", bench.lat, bench.lon));

            Intent sendIntent = new Intent(Intent.ACTION_SEND);
            sendIntent.putExtra(Intent.EXTRA_TEXT, sb.toString());
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

    private String formatMeters(double meters) {
        if (meters < 1000) {
            return Math.round(meters) + " m";
        }
        return String.format(Locale.CANADA_FRENCH, "%.1f km", meters / 1000.0);
    }

    private void handleIntent(Intent intent) {
        if (intent == null || intent.getData() == null) return;
        Uri data = intent.getData();
        if ("benchmap".equalsIgnoreCase(data.getScheme()) && "meet".equalsIgnoreCase(data.getHost())) {
            String key = data.getQueryParameter("k");
            if (key != null && !key.isEmpty()) {
                applyFriendKey(key);
            }
        }
    }

    private void showMeetupDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_meetup, null);
        dialog.setContentView(view);

        ImageView btnDialogClose = view.findViewById(R.id.btn_dialog_close);
        MaterialButton tabBtnShare = view.findViewById(R.id.tab_btn_share);
        MaterialButton tabBtnJoin = view.findViewById(R.id.tab_btn_join);
        View layoutTabShare = view.findViewById(R.id.layout_tab_share);
        View layoutTabJoin = view.findViewById(R.id.layout_tab_join);

        TextView tvGeneratedKey = view.findViewById(R.id.tv_generated_key);
        MaterialButton btnCopyKey = view.findViewById(R.id.btn_copy_key);
        MaterialButton btnShareInvite = view.findViewById(R.id.btn_share_invite);

        EditText etFriendKey = view.findViewById(R.id.et_friend_key);
        MaterialButton btnPasteKey = view.findViewById(R.id.btn_paste_key);
        TextView tvKeyError = view.findViewById(R.id.tv_key_error);
        MaterialButton btnCalculateMeetup = view.findViewById(R.id.btn_calculate_meetup);

        final double baseLat = (currentlySelectedBench != null) ? currentlySelectedBench.lat :
                ((lastLocation != null) ? lastLocation.getLatitude() : MontrealBenchMapView.CENTER_LAT);
        final double baseLon = (currentlySelectedBench != null) ? currentlySelectedBench.lon :
                ((lastLocation != null) ? lastLocation.getLongitude() : MontrealBenchMapView.CENTER_LON);
        final String baseName = (currentlySelectedBench != null) ? currentlySelectedBench.getDisplayName() : "Montréal";

        // Dynamic theme styling for dialog
        int bgDialog = isDarkMode ? Color.parseColor("#181A20") : Color.parseColor("#FFFFFF");
        int textPri = isDarkMode ? Color.parseColor("#F4F5F7") : Color.parseColor("#111318");
        int textSec = isDarkMode ? Color.parseColor("#8E93A0") : Color.parseColor("#667085");
        int borderC = isDarkMode ? Color.parseColor("#262932") : Color.parseColor("#E4E7EC");
        int boxBg = isDarkMode ? Color.parseColor("#20232B") : Color.parseColor("#F2F4F7");

        View dialogRoot = view.findViewById(R.id.dialog_meetup_root);
        TextView tvDialogTitle = view.findViewById(R.id.tv_dialog_title);
        if (dialogRoot != null) {
            GradientDrawable dialogBgDrawable = new GradientDrawable();
            dialogBgDrawable.setColor(bgDialog);
            dialogBgDrawable.setCornerRadii(new float[]{48, 48, 48, 48, 0, 0, 0, 0});
            dialogRoot.setBackground(dialogBgDrawable);
        }
        if (tvDialogTitle != null) {
            tvDialogTitle.setTextColor(textPri);
        }
        if (btnDialogClose != null) {
            btnDialogClose.setColorFilter(textSec);
            btnDialogClose.setOnClickListener(v -> dialog.dismiss());
        }
        View boxKeyDisplay = view.findViewById(R.id.box_key_display);
        if (boxKeyDisplay != null) {
            GradientDrawable boxBgDrawable = new GradientDrawable();
            boxBgDrawable.setColor(boxBg);
            boxBgDrawable.setCornerRadius(20);
            boxBgDrawable.setStroke(2, borderC);
            boxKeyDisplay.setBackground(boxBgDrawable);
        }
        if (tvGeneratedKey != null) {
            tvGeneratedKey.setTextColor(textPri);
        }
        if (etFriendKey != null) {
            GradientDrawable etBgDrawable = new GradientDrawable();
            etBgDrawable.setColor(boxBg);
            etBgDrawable.setCornerRadius(20);
            etBgDrawable.setStroke(2, borderC);
            etFriendKey.setBackground(etBgDrawable);
            etFriendKey.setTextColor(textPri);
            etFriendKey.setHintTextColor(textSec);
        }
        dialog.setOnShowListener(d -> {
            View bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                bottomSheet.setBackgroundColor(Color.TRANSPARENT);
            }
        });

        // Always generate key with quartier (district) blur level
        String key = MeetupKey.encode(baseLat, baseLon, MeetupKey.BLUR_DISTRICT);
        if (tvGeneratedKey != null) {
            tvGeneratedKey.setText(key);
        }

        // Clean modern tab switching
        Runnable updateTabs = () -> {
            boolean isShare = layoutTabShare.getVisibility() == View.VISIBLE;
            tabBtnShare.setBackgroundColor(isShare ? Color.parseColor("#E52B35") : boxBg);
            tabBtnShare.setTextColor(isShare ? Color.WHITE : textPri);
            tabBtnShare.setStrokeColor(ColorStateList.valueOf(isShare ? Color.parseColor("#E52B35") : borderC));
            tabBtnShare.setStrokeWidth(isShare ? 0 : 2);

            tabBtnJoin.setBackgroundColor(!isShare ? Color.parseColor("#E52B35") : boxBg);
            tabBtnJoin.setTextColor(!isShare ? Color.WHITE : textPri);
            tabBtnJoin.setStrokeColor(ColorStateList.valueOf(!isShare ? Color.parseColor("#E52B35") : borderC));
            tabBtnJoin.setStrokeWidth(!isShare ? 0 : 2);
        };
        updateTabs.run();

        tabBtnShare.setOnClickListener(v -> {
            triggerHapticTick();
            layoutTabShare.setVisibility(View.VISIBLE);
            layoutTabJoin.setVisibility(View.GONE);
            updateTabs.run();
        });

        tabBtnJoin.setOnClickListener(v -> {
            triggerHapticTick();
            layoutTabShare.setVisibility(View.GONE);
            layoutTabJoin.setVisibility(View.VISIBLE);
            updateTabs.run();
        });

        btnCopyKey.setOnClickListener(v -> {
            triggerHapticTick();
            String k = tvGeneratedKey.getText().toString();
            copyToClipboard("BenchMap Meetup Key", k);
            Toast.makeText(this, "Clé copiée dans le presse-papier !", Toast.LENGTH_SHORT).show();
        });

        btnShareInvite.setOnClickListener(v -> {
            triggerHapticTick();
            String k = tvGeneratedKey.getText().toString();
            String text = MeetupKey.formatShareText(k, baseName);
            Intent sendIntent = new Intent(Intent.ACTION_SEND);
            sendIntent.putExtra(Intent.EXTRA_TEXT, text);
            sendIntent.setType("text/plain");
            startActivity(Intent.createChooser(sendIntent, "Inviter un ami"));
            dialog.dismiss();
        });

        btnPasteKey.setOnClickListener(v -> {
            triggerHapticTick();
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip().getItemCount() > 0) {
                CharSequence text = cm.getPrimaryClip().getItemAt(0).getText();
                if (text != null) {
                    etFriendKey.setText(text.toString().trim());
                    tvKeyError.setVisibility(View.INVISIBLE);
                }
            } else {
                Toast.makeText(this, "Presse-papier vide", Toast.LENGTH_SHORT).show();
            }
        });

        btnCalculateMeetup.setOnClickListener(v -> {
            triggerHapticTick();
            String input = etFriendKey.getText().toString();
            MeetupKey.DecodedLocation decoded = MeetupKey.decode(input);
            if (decoded == null) {
                tvKeyError.setVisibility(View.VISIBLE);
                tvKeyError.setText("Clé invalide ou mal orthographiée");
                return;
            }
            dialog.dismiss();
            startMeetupMode(decoded.lat, decoded.lon, decoded.blurMeters);
        });

        dialog.show();
    }

    private void showSearchDialog() {
        BottomSheetDialog searchDialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_search, null);
        searchDialog.setContentView(view);

        int bgDialog = isDarkMode ? Color.parseColor("#181A20") : Color.parseColor("#FFFFFF");
        int textPri = isDarkMode ? Color.parseColor("#F4F5F7") : Color.parseColor("#111318");
        int textSec = isDarkMode ? Color.parseColor("#8E93A0") : Color.parseColor("#667085");
        int borderC = isDarkMode ? Color.parseColor("#262932") : Color.parseColor("#E4E7EC");
        int boxBg = isDarkMode ? Color.parseColor("#20232B") : Color.parseColor("#F2F4F7");

        View root = view.findViewById(R.id.dialog_search_root);
        if (root != null) {
            GradientDrawable dialogBgDrawable = new GradientDrawable();
            dialogBgDrawable.setColor(bgDialog);
            dialogBgDrawable.setCornerRadii(new float[]{48, 48, 48, 48, 0, 0, 0, 0});
            root.setBackground(dialogBgDrawable);
        }

        View dragHandle = view.findViewById(R.id.search_drag_handle);
        if (dragHandle != null) {
            GradientDrawable handleBg = new GradientDrawable();
            handleBg.setColor(borderC);
            handleBg.setCornerRadius(10);
            dragHandle.setBackground(handleBg);
        }

        View searchBarBox = view.findViewById(R.id.search_bar_box);
        if (searchBarBox != null) {
            GradientDrawable boxBgDrawable = new GradientDrawable();
            boxBgDrawable.setColor(boxBg);
            boxBgDrawable.setCornerRadius(22);
            boxBgDrawable.setStroke(2, borderC);
            searchBarBox.setBackground(boxBgDrawable);
        }

        EditText etSearchQuery = view.findViewById(R.id.et_search_query);
        ImageView btnSearchClear = view.findViewById(R.id.btn_search_clear);
        TextView btnSearchClose = view.findViewById(R.id.btn_search_close);
        TextView tvSearchStatus = view.findViewById(R.id.tv_search_status);
        ListView listSearchResults = view.findViewById(R.id.list_search_results);
        TextView tvSearchEmpty = view.findViewById(R.id.tv_search_empty);

        if (etSearchQuery != null) {
            etSearchQuery.setTextColor(textPri);
            etSearchQuery.setHintTextColor(textSec);
        }
        if (btnSearchClear != null) btnSearchClear.setColorFilter(textSec);
        if (btnSearchClose != null) {
            btnSearchClose.setTextColor(textSec);
            btnSearchClose.setOnClickListener(v -> {
                triggerHapticTick();
                searchDialog.dismiss();
            });
        }
        if (tvSearchStatus != null) tvSearchStatus.setTextColor(textSec);
        if (tvSearchEmpty != null) tvSearchEmpty.setTextColor(textSec);

        if (searchDialog.getWindow() != null) {
            searchDialog.getWindow().setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            );
        }

        searchDialog.setOnShowListener(d -> {
            BottomSheetDialog dialog = (BottomSheetDialog) d;
            FrameLayout bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                bottomSheet.setBackgroundColor(Color.TRANSPARENT);
                BottomSheetBehavior<FrameLayout> behavior = BottomSheetBehavior.from(bottomSheet);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                behavior.setSkipCollapsed(true);
                ViewGroup.LayoutParams lp = bottomSheet.getLayoutParams();
                if (lp != null) {
                    lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
                    bottomSheet.setLayoutParams(lp);
                }
            }
        });

        List<Bench> allBenches = benchMapView.getAllBenches();
        final List<Bench> filteredList = new ArrayList<>();

        BaseAdapter adapter = new BaseAdapter() {
            @Override
            public int getCount() {
                return filteredList.size();
            }

            @Override
            public Bench getItem(int position) {
                return filteredList.get(position);
            }

            @Override
            public long getItemId(int position) {
                return position;
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                if (convertView == null) {
                    convertView = getLayoutInflater().inflate(R.layout.item_search_bench, parent, false);
                }
                Bench bench = getItem(position);
                TextView tvIcon = convertView.findViewById(R.id.tv_item_icon);
                TextView tvTitle = convertView.findViewById(R.id.tv_item_title);
                TextView tvSubtitle = convertView.findViewById(R.id.tv_item_subtitle);
                TextView tvDistance = convertView.findViewById(R.id.tv_item_distance);

                tvTitle.setText(bench.getDisplayName());
                tvTitle.setTextColor(textPri);
                tvSubtitle.setText(bench.getDisplaySubtitle());
                tvSubtitle.setTextColor(textSec);
                tvIcon.setText(bench.isInPark() ? "🌳" : "🪑");

                if (lastLocation != null) {
                    double distMeters = MontrealBenchMapView.computeDistance(
                            lastLocation.getLatitude(), lastLocation.getLongitude(), bench.lat, bench.lon);
                    tvDistance.setVisibility(View.VISIBLE);
                    if (distMeters < 1000) {
                        tvDistance.setText(String.format(Locale.FRENCH, "%d m", (int) distMeters));
                    } else {
                        tvDistance.setText(String.format(Locale.FRENCH, "%.1f km", distMeters / 1000.0));
                    }
                    tvDistance.setTextColor(Color.parseColor("#E52B35"));
                } else {
                    tvDistance.setVisibility(View.GONE);
                }

                convertView.setOnClickListener(v -> {
                    triggerHapticTick();
                    searchDialog.dismiss();
                    benchMapView.focusBench(bench);
                });

                return convertView;
            }
        };
        listSearchResults.setAdapter(adapter);

        Runnable performFilter = () -> {
            String rawQuery = (etSearchQuery != null && etSearchQuery.getText() != null)
                    ? etSearchQuery.getText().toString().trim() : "";

            btnSearchClear.setVisibility(rawQuery.isEmpty() ? View.GONE : View.VISIBLE);
            filteredList.clear();

            if (rawQuery.isEmpty()) {
                // Curated top highlights across Montreal
                String[] topLocations = new String[]{
                    "Mont-Royal", "La Fontaine", "Sainte-Catherine", "Saint-Laurent",
                    "Wellington", "Jarry", "Saint-Denis", "Notre-Dame", "Laurier", "Maisonneuve"
                };
                for (String loc : topLocations) {
                    for (Bench b : allBenches) {
                        if (b.getDisplayName().toLowerCase(Locale.ROOT).contains(loc.toLowerCase(Locale.ROOT)) ||
                            b.street.toLowerCase(Locale.ROOT).contains(loc.toLowerCase(Locale.ROOT))) {
                            filteredList.add(b);
                            break;
                        }
                    }
                }
                tvSearchStatus.setText("SUGGESTIONS (" + filteredList.size() + ")");
            } else {
                String normalizedQuery = normalizeForSearch(rawQuery);
                String[] tokens = normalizedQuery.split("\\s+");
                int limit = 60;
                for (Bench b : allBenches) {
                    String searchable = normalizeForSearch(
                        b.street + " " + b.park + " " + b.borough + " " + b.getAddress() + " " + b.material + " " + b.backrest
                    );
                    boolean allMatch = true;
                    for (String token : tokens) {
                        if (!token.isEmpty() && !searchable.contains(token)) {
                            allMatch = false;
                            break;
                        }
                    }
                    if (allMatch) {
                        filteredList.add(b);
                        if (filteredList.size() >= limit) break;
                    }
                }
                tvSearchStatus.setText(filteredList.size() + " RÉSULTAT" + (filteredList.size() > 1 ? "S" : ""));
            }

            boolean hasResults = !filteredList.isEmpty();
            listSearchResults.setVisibility(hasResults ? View.VISIBLE : View.GONE);
            tvSearchEmpty.setVisibility(hasResults ? View.GONE : View.VISIBLE);
            adapter.notifyDataSetChanged();
        };

        performFilter.run();

        btnSearchClear.setOnClickListener(v -> {
            triggerHapticTick();
            etSearchQuery.setText("");
            performFilter.run();
        });

        etSearchQuery.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                performFilter.run();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        searchDialog.show();
        if (etSearchQuery != null) {
            etSearchQuery.post(() -> {
                etSearchQuery.requestFocus();
                android.view.inputmethod.InputMethodManager imm =
                    (android.view.inputmethod.InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showSoftInput(etSearchQuery, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
                }
            });
        }
    }

    private static String normalizeForSearch(String str) {
        if (str == null) return "";
        String nfd = java.text.Normalizer.normalize(str, java.text.Normalizer.Form.NFD);
        return nfd.replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).replace('-', ' ');
    }

    public boolean applyFriendKey(String key) {
        MeetupKey.DecodedLocation decoded = MeetupKey.decode(key);
        if (decoded != null) {
            startMeetupMode(decoded.lat, decoded.lon, decoded.blurMeters);
            return true;
        } else {
            Toast.makeText(this, "Clé de rencontre invalide", Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    public void startMeetupMode(double fLat, double fLon, int fBlur) {
        this.friendLat = fLat;
        this.friendLon = fLon;
        this.friendBlurMeters = fBlur;

        double uLat = (lastLocation != null) ? lastLocation.getLatitude() : MontrealBenchMapView.CENTER_LAT;
        double uLon = (lastLocation != null) ? lastLocation.getLongitude() : MontrealBenchMapView.CENTER_LON;

        List<Bench> all = benchMapView.getAllBenches();
        currentHalfwayBenches.clear();
        currentHalfwayBenches.addAll(MeetupFinder.findHalfwayBenches(all, uLat, uLon, fLat, fLon, 25));

        if (currentHalfwayBenches.isEmpty()) {
            Toast.makeText(this, "Aucun banc trouvé à mi-chemin", Toast.LENGTH_SHORT).show();
            return;
        }

        List<Bench> rawBenches = new ArrayList<>();
        for (MeetupFinder.MeetupBench mb : currentHalfwayBenches) {
            rawBenches.add(mb.bench);
        }

        benchMapView.setMeetup(fLat, fLon, fBlur, rawBenches);
        benchMapView.fitBounds(uLat, uLon, fLat, fLon);

        double directDist = MeetupFinder.haversine(uLat, uLon, fLat, fLon);
        String distStr = formatMeters(directDist);

        cardMeetupBanner.setVisibility(View.VISIBLE);
        tvMeetupBannerTitle.setText("🤝 RENDEZ-VOUS ACTIF");
        tvMeetupBannerSubtitle.setText("Ami à " + distStr + " • " + currentHalfwayBenches.size() + " bancs équitables trouvés");

        selectMeetupBench(0);
        Toast.makeText(this, "Bancs à mi-chemin calculés avec succès !", Toast.LENGTH_SHORT).show();
    }

    private void selectMeetupBench(int index) {
        if (currentHalfwayBenches.isEmpty()) return;
        currentMeetupBenchIndex = (index + currentHalfwayBenches.size()) % currentHalfwayBenches.size();
        MeetupFinder.MeetupBench mb = currentHalfwayBenches.get(currentMeetupBenchIndex);

        benchMapView.selectBench(mb.bench);
        benchMapView.animateToCoords(mb.bench.lat, mb.bench.lon, 2200000f);

        tvMeetupBannerTitle.setText(String.format(Locale.US, "🤝 BANC À MI-CHEMIN #%d / %d",
                (currentMeetupBenchIndex + 1), currentHalfwayBenches.size()));
    }

    private void cycleNextMeetupBench() {
        selectMeetupBench(currentMeetupBenchIndex + 1);
    }

    public void clearMeetupMode() {
        friendLat = null;
        friendLon = null;
        friendBlurMeters = 0;
        currentHalfwayBenches.clear();
        currentMeetupBenchIndex = 0;
        benchMapView.clearMeetup();
        cardMeetupBanner.setVisibility(View.GONE);
        Toast.makeText(this, "Mode Rendez-vous terminé", Toast.LENGTH_SHORT).show();
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
