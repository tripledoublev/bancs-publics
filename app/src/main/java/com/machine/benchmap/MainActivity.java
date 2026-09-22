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
import android.util.Log;
import android.view.HapticFeedbackConstants;
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
import androidx.cardview.widget.CardView;

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
import com.google.android.material.switchmaterial.SwitchMaterial;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.graphics.Bitmap;
import android.widget.CheckBox;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import java.io.File;
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
    private static final String PREF_RENDER_STYLE = "key_render_style";

    private MontrealBenchMapView benchMapView;
    private View layoutHeader;
    private View layoutBottomControls;
    private TextView tvBenchCounter;

    // Header Swiss elements
    private MaterialCardView cardHeader;
    private View layoutCounterBadge;
    private ImageView btnMapStyle;
    private ImageView btnCollections;
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
    private ImageView btnCardFavorite;
    private LinearLayout layoutDetailSavedInfo;
    private TextView tvDetailCollectionsBadge;
    private LinearLayout cardDetailNote;
    private TextView tvDetailNote;
    private HorizontalScrollView scrollDetailPhotos;
    private LinearLayout layoutDetailPhotosStrip;
    private TextView btnDetailAddNotePhoto;
    private MaterialButton btnNavigate, btnShare;

    // Collections & Photos
    private BenchCollectionManager collectionManager;
    private Uri pendingCameraUri = null;
    private File pendingCameraFile = null;
    private ActivityResultLauncher<Uri> takePictureLauncher;
    private ActivityResultLauncher<String> pickImageLauncher;
    private String activePhotoTargetBenchId = null;
    private List<String> currentWorkingPhotos = null;
    private Runnable photoAddedCallback = null;

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

        collectionManager = BenchCollectionManager.getInstance(this);
        initPhotoLaunchers();

        bindViews();
        setupWindowInsets();
        initTheme();
        initRenderStyle();
        setupActions();

        benchMapView.setMapListener(this);
        benchMapView.setCustomBenches(collectionManager.getAllCustomBenches());
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
        layoutCounterBadge = findViewById(R.id.layout_counter_badge);
        layoutBottomControls = findViewById(R.id.layout_bottom_controls);
        tvBenchCounter = findViewById(R.id.tv_bench_counter);
        btnMapStyle = findViewById(R.id.btn_map_style);
        btnThemeToggle = findViewById(R.id.btn_theme_toggle);
        btnMeetup = findViewById(R.id.btn_meetup);
        btnCollections = findViewById(R.id.btn_collections);

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
        btnCardFavorite = findViewById(R.id.btn_card_favorite);
        layoutDetailSavedInfo = findViewById(R.id.layout_detail_saved_info);
        tvDetailCollectionsBadge = findViewById(R.id.tv_detail_collections_badge);
        cardDetailNote = findViewById(R.id.card_detail_note);
        tvDetailNote = findViewById(R.id.tv_detail_note);
        scrollDetailPhotos = findViewById(R.id.scroll_detail_photos);
        layoutDetailPhotosStrip = findViewById(R.id.layout_detail_photos_strip);
        btnDetailAddNotePhoto = findViewById(R.id.btn_detail_add_note_photo);
        btnNavigate = findViewById(R.id.btn_navigate);
        btnShare = findViewById(R.id.btn_share);
    }

    private void setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(getWindow().getDecorView(), (v, windowInsets) -> {
            Insets statusInsets = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars());
            Insets navInsets = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars());

            if (layoutHeader != null) {
                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) layoutHeader.getLayoutParams();
                lp.topMargin = statusInsets.top + (int) (4 * getResources().getDisplayMetrics().density);
                layoutHeader.setLayoutParams(lp);

                layoutHeader.post(() -> {
                    if (benchMapView != null) {
                        float margin = 12f * getResources().getDisplayMetrics().density;
                        benchMapView.setCompassTopMargin(layoutHeader.getBottom() + margin);
                    }
                });
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
        if (layoutCounterBadge != null) {
            GradientDrawable counterDrawable = new GradientDrawable();
            counterDrawable.setShape(GradientDrawable.RECTANGLE);
            counterDrawable.setCornerRadius(12f * d);
            counterDrawable.setColor(chipBg);
            counterDrawable.setStroke((int) (1f * d), borderCard);
            layoutCounterBadge.setBackground(counterDrawable);
        }
        if (tvBenchCounter != null) {
            tvBenchCounter.setTextColor(textPrimary);
        }
        if (btnThemeToggle != null) {
            btnThemeToggle.setImageResource(darkMode ? R.drawable.ic_theme_sun : R.drawable.ic_theme_moon);
            btnThemeToggle.setImageTintList(ColorStateList.valueOf(darkMode ? Color.parseColor("#F59E0B") : Color.parseColor("#111318")));
        }
        if (btnMeetup != null) {
            btnMeetup.setImageTintList(ColorStateList.valueOf(darkMode ? Color.parseColor("#60A5FA") : Color.parseColor("#2563EB")));
        }
        if (btnCollections != null) {
            btnCollections.setImageTintList(ColorStateList.valueOf(darkMode ? Color.parseColor("#F4F5F7") : Color.parseColor("#111318")));
        }
        if (btnMapStyle != null) {
            btnMapStyle.setImageTintList(ColorStateList.valueOf(darkMode ? Color.parseColor("#F4F5F7") : Color.parseColor("#111318")));
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

        GradientDrawable circleBtnFav = new GradientDrawable();
        circleBtnFav.setShape(GradientDrawable.OVAL);
        circleBtnFav.setColor(chipBg);
        if (btnCardFavorite != null) {
            btnCardFavorite.setBackground(circleBtnFav);
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

        if (cardDetailNote != null) {
            GradientDrawable noteBg = new GradientDrawable();
            noteBg.setShape(GradientDrawable.RECTANGLE);
            noteBg.setCornerRadius(10f * d);
            noteBg.setColor(chipBg);
            noteBg.setStroke((int) (1f * d), chipStroke);
            cardDetailNote.setBackground(noteBg);
        }
        if (tvDetailNote != null) {
            tvDetailNote.setTextColor(textPrimary);
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

        if (currentlySelectedBench != null) {
            updateDetailCardSavedState(currentlySelectedBench);
        }
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

        if (btnCollections != null) {
            btnCollections.setOnClickListener(v -> {
                triggerHapticTick();
                showCollectionsDialog();
            });
        }

        if (btnMapStyle != null) {
            btnMapStyle.setOnClickListener(v -> {
                triggerHapticTick();
                showMapStylesDialog();
            });
        }

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

        if (btnCardFavorite != null) {
            btnCardFavorite.setOnClickListener(v -> {
                triggerHapticTick();
                if (currentlySelectedBench != null) {
                    showSaveBenchDialog(currentlySelectedBench);
                }
            });
        }

        if (btnDetailAddNotePhoto != null) {
            btnDetailAddNotePhoto.setOnClickListener(v -> {
                triggerHapticTick();
                if (currentlySelectedBench != null) {
                    showSaveBenchDialog(currentlySelectedBench);
                }
            });
        }

        if (cardDetailNote != null) {
            cardDetailNote.setOnClickListener(v -> {
                triggerHapticTick();
                if (currentlySelectedBench != null) {
                    showSaveBenchDialog(currentlySelectedBench);
                }
            });
        }

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

            updateDetailCardSavedState(bench);

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

    @Override
    public void onMapLongPressed(double lat, double lon, float screenX, float screenY) {
        runOnUiThread(() -> {
            onBenchDeselected();
            showAddCustomBenchDialog(lat, lon);
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
        if (intent == null) return;
        if (intent.hasExtra("test_rotation")) {
            float rot = -1f;
            if (intent.getExtras() != null) {
                Object extra = intent.getExtras().get("test_rotation");
                if (extra instanceof Number) {
                    rot = ((Number) extra).floatValue();
                } else if (extra != null) {
                    try {
                        rot = Float.parseFloat(extra.toString());
                    } catch (Exception ignored) {}
                }
            }
            if (rot >= 0f) {
                final float finalRot = rot;
                Log.d("BenchMap", "handleIntent: applying test_rotation=" + finalRot);
                benchMapView.post(() -> benchMapView.setMapRotation(finalRot));
            }
        }
        if (intent.getBooleanExtra("test_compass_tap", false)) {
            Log.d("BenchMap", "handleIntent: triggering test_compass_tap");
            benchMapView.post(() -> benchMapView.onCompassTapped());
        }
        if (intent.hasExtra("test_scale")) {
            float s = -1f;
            if (intent.getExtras() != null) {
                Object extra = intent.getExtras().get("test_scale");
                if (extra instanceof Number) {
                    s = ((Number) extra).floatValue();
                } else if (extra != null) {
                    try {
                        s = Float.parseFloat(extra.toString());
                    } catch (Exception ignored) {}
                }
            }
            if (s > 0f) {
                final float finalScale = s;
                Log.d("BenchMap", "handleIntent: applying test_scale=" + finalScale);
                benchMapView.post(() -> benchMapView.setScale(finalScale));
            }
        }
        if (intent.hasExtra("test_style")) {
            String testStyle = intent.getStringExtra("test_style");
            if (testStyle != null) {
                Log.d("BenchMap", "handleIntent: applying test_style=" + testStyle);
                benchMapView.post(() -> benchMapView.setRenderStyle(MapRenderStyle.fromId(testStyle), false));
            }
        }
        if (intent.getData() == null) return;
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

        Location bestDeviceLoc = getBestDeviceLocation();
        if (bestDeviceLoc == null) {
            Toast.makeText(this, "Position GPS introuvable pour le calcul", Toast.LENGTH_SHORT).show();
            return;
        }
        double baseLat = bestDeviceLoc.getLatitude();
        double baseLon = bestDeviceLoc.getLongitude();
        String baseName = "Montréal";

        // Swiss theme coloring
        int bgCard = isDarkMode ? Color.parseColor("#181A20") : Color.parseColor("#FFFFFF");
        int textPri = isDarkMode ? Color.parseColor("#F4F5F7") : Color.parseColor("#111318");
        int textSec = isDarkMode ? Color.parseColor("#8E93A0") : Color.parseColor("#667085");
        int borderC = isDarkMode ? Color.parseColor("#262932") : Color.parseColor("#E4E7EC");
        int boxBg = isDarkMode ? Color.parseColor("#20232B") : Color.parseColor("#F2F4F7");

        View root = view.findViewById(R.id.dialog_meetup_root);
        if (root != null) {
            GradientDrawable rootDrawable = new GradientDrawable();
            rootDrawable.setShape(GradientDrawable.RECTANGLE);
            rootDrawable.setCornerRadii(new float[]{40, 40, 40, 40, 0, 0, 0, 0});
            rootDrawable.setColor(bgCard);
            root.setBackground(rootDrawable);
        }

        View explainer = view.findViewById(R.id.card_meetup_explainer);
        if (explainer != null) {
            GradientDrawable expBg = new GradientDrawable();
            expBg.setShape(GradientDrawable.RECTANGLE);
            expBg.setCornerRadius(12f * getResources().getDisplayMetrics().density);
            expBg.setColor(boxBg);
            expBg.setStroke((int) (1f * getResources().getDisplayMetrics().density), borderC);
            explainer.setBackground(expBg);
        }

        View boxKey = view.findViewById(R.id.box_key_display);
        if (boxKey != null) {
            GradientDrawable boxDrawable = new GradientDrawable();
            boxDrawable.setShape(GradientDrawable.RECTANGLE);
            boxDrawable.setCornerRadius(12f * getResources().getDisplayMetrics().density);
            boxDrawable.setColor(boxBg);
            boxDrawable.setStroke((int) (1f * getResources().getDisplayMetrics().density), borderC);
            boxKey.setBackground(boxDrawable);
        }

        if (etFriendKey != null) {
            GradientDrawable etBgDrawable = new GradientDrawable();
            etBgDrawable.setShape(GradientDrawable.RECTANGLE);
            etBgDrawable.setCornerRadius(12f * getResources().getDisplayMetrics().density);
            etBgDrawable.setColor(boxBg);
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
        String inviteLink = "benchmap://meet?k=" + key;
        if (tvGeneratedKey != null) {
            tvGeneratedKey.setText(inviteLink);
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

            // Auto-detect meetup key or link in clipboard
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip().getItemCount() > 0) {
                CharSequence clipText = cm.getPrimaryClip().getItemAt(0).getText();
                if (clipText != null) {
                    String clipStr = clipText.toString().trim();
                    if (clipStr.contains("meet?k=") || clipStr.contains("BM1-")) {
                        etFriendKey.setText(clipStr);
                        tvKeyError.setVisibility(View.INVISIBLE);
                        Toast.makeText(this, "Lien d'invitation détecté dans le presse-papier !", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });

        btnCopyKey.setOnClickListener(v -> {
            triggerHapticTick();
            copyToClipboard("BenchMap Rendez-vous", inviteLink);
            Toast.makeText(this, "Lien d'invitation copié !", Toast.LENGTH_SHORT).show();
        });

        btnShareInvite.setOnClickListener(v -> {
            triggerHapticTick();
            String text = MeetupKey.formatShareText(key, baseName);
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

    private Location getBestDeviceLocation() {
        if (lastLocation != null) {
            return lastLocation;
        }
        try {
            if (locationManager != null && (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED)) {
                Location gps = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                Location net = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                if (gps != null && net != null) {
                    return (net.getTime() > gps.getTime()) ? net : gps;
                }
                if (gps != null) return gps;
                if (net != null) return net;
            }
        } catch (Exception ignored) {}
        return null;
    }

    private void initPhotoLaunchers() {
        takePictureLauncher = registerForActivityResult(new ActivityResultContracts.TakePicture(), success -> {
            if (Boolean.TRUE.equals(success) && pendingCameraUri != null && activePhotoTargetBenchId != null) {
                String savedPath = collectionManager.importPhotoFromUri(pendingCameraUri, activePhotoTargetBenchId);
                if (savedPath != null && currentWorkingPhotos != null) {
                    currentWorkingPhotos.add(savedPath);
                    if (photoAddedCallback != null) {
                        photoAddedCallback.run();
                    }
                }
            }
        });

        pickImageLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null && activePhotoTargetBenchId != null) {
                String savedPath = collectionManager.importPhotoFromUri(uri, activePhotoTargetBenchId);
                if (savedPath != null && currentWorkingPhotos != null) {
                    currentWorkingPhotos.add(savedPath);
                    if (photoAddedCallback != null) {
                        photoAddedCallback.run();
                    }
                }
            }
        });
    }

    private void updateDetailCardSavedState(Bench bench) {
        if (bench == null) return;
        String bId = bench.getId();
        boolean isSaved = collectionManager.isBenchSaved(bId);
        SavedBench sb = collectionManager.getSavedBench(bId);
        List<BenchCollection> cols = collectionManager.getCollectionsForBench(bId);

        if (btnCardFavorite != null) {
            if (isSaved) {
                btnCardFavorite.setImageResource(R.drawable.ic_bookmark_filled);
                btnCardFavorite.setColorFilter(Color.parseColor("#E52B35"));
            } else {
                btnCardFavorite.setImageResource(R.drawable.ic_bookmark);
                btnCardFavorite.setColorFilter(isDarkMode ? Color.parseColor("#8E93A0") : Color.parseColor("#667085"));
            }
        }

        if (isSaved && sb != null) {
            if (btnDetailAddNotePhoto != null) btnDetailAddNotePhoto.setVisibility(View.GONE);
            if (layoutDetailSavedInfo != null) layoutDetailSavedInfo.setVisibility(View.VISIBLE);

            if (tvDetailCollectionsBadge != null) {
                if (!cols.isEmpty()) {
                    StringBuilder sbTitle = new StringBuilder("Dans : ");
                    for (int i = 0; i < cols.size(); i++) {
                        if (i > 0) sbTitle.append(" • ");
                        sbTitle.append(cols.get(i).getDisplayTitle());
                    }
                    tvDetailCollectionsBadge.setText(sbTitle.toString());
                    tvDetailCollectionsBadge.setVisibility(View.VISIBLE);
                } else {
                    tvDetailCollectionsBadge.setVisibility(View.GONE);
                }
            }

            if (cardDetailNote != null && tvDetailNote != null) {
                if (sb.hasNote()) {
                    tvDetailNote.setText(sb.note);
                    cardDetailNote.setVisibility(View.VISIBLE);
                } else {
                    cardDetailNote.setVisibility(View.GONE);
                }
            }

            if (scrollDetailPhotos != null && layoutDetailPhotosStrip != null) {
                if (sb.hasPhotos()) {
                    layoutDetailPhotosStrip.removeAllViews();
                    for (String photoPath : sb.photoPaths) {
                        View thumbView = getLayoutInflater().inflate(R.layout.item_photo_thumb, layoutDetailPhotosStrip, false);
                        MaterialCardView cvThumb = thumbView.findViewById(R.id.cv_thumb);
                        if (cvThumb != null) {
                            int thumbBg = isDarkMode ? Color.parseColor("#20232B") : Color.parseColor("#F2F4F7");
                            int thumbBorder = isDarkMode ? Color.parseColor("#2C303B") : Color.parseColor("#E4E7EC");
                            cvThumb.setCardBackgroundColor(thumbBg);
                            cvThumb.setStrokeColor(thumbBorder);
                        }
                        ImageView iv = thumbView.findViewById(R.id.iv_thumb);
                        View btnDel = thumbView.findViewById(R.id.btn_delete_thumb);
                        btnDel.setVisibility(View.GONE);

                        Bitmap bmp = BenchCollectionManager.loadThumbnail(this, photoPath, 140);
                        if (bmp != null) {
                            iv.setImageBitmap(bmp);
                        }
                        thumbView.setOnClickListener(v -> showPhotoViewerDialog(photoPath));
                        layoutDetailPhotosStrip.addView(thumbView);
                    }
                    scrollDetailPhotos.setVisibility(View.VISIBLE);
                } else {
                    scrollDetailPhotos.setVisibility(View.GONE);
                }
            }
        } else {
            if (layoutDetailSavedInfo != null) layoutDetailSavedInfo.setVisibility(View.GONE);
            if (btnDetailAddNotePhoto != null) btnDetailAddNotePhoto.setVisibility(View.VISIBLE);
        }
    }

    private void showCollectionsDialog() {
        final BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_collections, null);
        dialog.setContentView(view);

        int bgDialog = isDarkMode ? Color.parseColor("#181A20") : Color.parseColor("#FFFFFF");
        int textPri = isDarkMode ? Color.parseColor("#F4F5F7") : Color.parseColor("#111318");
        int textSec = isDarkMode ? Color.parseColor("#8E93A0") : Color.parseColor("#667085");
        int borderC = isDarkMode ? Color.parseColor("#262932") : Color.parseColor("#E4E7EC");
        int boxBg = isDarkMode ? Color.parseColor("#20232B") : Color.parseColor("#F2F4F7");

        View root = view.findViewById(R.id.dialog_collections_root);
        if (root != null) {
            GradientDrawable gd = new GradientDrawable();
            gd.setColor(bgDialog);
            gd.setCornerRadii(new float[]{48, 48, 48, 48, 0, 0, 0, 0});
            root.setBackground(gd);
        }

        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            int navBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            float d = getResources().getDisplayMetrics().density;
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), navBottom + (int)(24 * d));
            return insets;
        });

        View layoutViewCollections = view.findViewById(R.id.layout_view_collections);
        View layoutViewSingleCollection = view.findViewById(R.id.layout_view_single_collection);
        TextView tvCollectionsTitle = view.findViewById(R.id.tv_collections_title);
        TextView tvCollectionsSubtitle = view.findViewById(R.id.tv_collections_subtitle);
        MaterialButton btnNewCollection = view.findViewById(R.id.btn_new_collection);
        ImageView btnCollectionsClose = view.findViewById(R.id.btn_collections_close);
        RecyclerView rvCollections = view.findViewById(R.id.rv_collections);
        View layoutEmptyCollections = view.findViewById(R.id.layout_empty_collections);

        ImageView btnBackToCollections = view.findViewById(R.id.btn_back_to_collections);
        TextView tvDetailColTitle = view.findViewById(R.id.tv_detail_col_title);
        TextView tvDetailColSubtitle = view.findViewById(R.id.tv_detail_col_subtitle);
        ImageView btnDeleteCollection = view.findViewById(R.id.btn_delete_collection);
        ImageView btnSingleColClose = view.findViewById(R.id.btn_single_col_close);
        RecyclerView rvCollectionBenches = view.findViewById(R.id.rv_collection_benches);
        View layoutEmptySingleCollection = view.findViewById(R.id.layout_empty_single_collection);

        if (tvCollectionsTitle != null) tvCollectionsTitle.setTextColor(textPri);
        if (tvCollectionsSubtitle != null) tvCollectionsSubtitle.setTextColor(textSec);
        if (tvDetailColTitle != null) tvDetailColTitle.setTextColor(textPri);
        if (tvDetailColSubtitle != null) tvDetailColSubtitle.setTextColor(textSec);
        if (btnCollectionsClose != null) btnCollectionsClose.setColorFilter(textSec);
        if (btnSingleColClose != null) btnSingleColClose.setColorFilter(textSec);
        if (btnBackToCollections != null) btnBackToCollections.setColorFilter(textPri);

        dialog.setOnShowListener(d -> {
            View bs = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bs != null) {
                GradientDrawable bsBg = new GradientDrawable();
                bsBg.setColor(bgDialog);
                bsBg.setCornerRadii(new float[]{48, 48, 48, 48, 0, 0, 0, 0});
                bs.setBackground(bsBg);
                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bs);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                behavior.setSkipCollapsed(true);
            }
            if (dialog.getWindow() != null) {
                dialog.getWindow().setNavigationBarColor(bgDialog);
            }
        });

        if (btnCollectionsClose != null) btnCollectionsClose.setOnClickListener(v -> dialog.dismiss());
        if (btnSingleColClose != null) btnSingleColClose.setOnClickListener(v -> dialog.dismiss());

        class CollectionsViewController {
            void showAll() {
                layoutViewCollections.setVisibility(View.VISIBLE);
                layoutViewSingleCollection.setVisibility(View.GONE);

                List<BenchCollection> cols = collectionManager.getCollections();
                int totalSaved = collectionManager.getTotalSavedBenchesCount();
                tvCollectionsSubtitle.setText(String.format(Locale.CANADA_FRENCH, "%d bancs enregistrés sur cet appareil", totalSaved));

                if (cols.isEmpty() || (cols.size() == 1 && cols.get(0).size() == 0)) {
                    if (layoutEmptyCollections != null) layoutEmptyCollections.setVisibility(View.VISIBLE);
                } else {
                    if (layoutEmptyCollections != null) layoutEmptyCollections.setVisibility(View.GONE);
                }

                rvCollections.setLayoutManager(new LinearLayoutManager(MainActivity.this));
                rvCollections.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                    @NonNull
                    @Override
                    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                        View itemView = getLayoutInflater().inflate(R.layout.item_collection, parent, false);
                        return new RecyclerView.ViewHolder(itemView) {};
                    }

                    @Override
                    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                        BenchCollection col = cols.get(position);
                        TextView tvEmoji = holder.itemView.findViewById(R.id.tv_collection_emoji);
                        TextView tvTitle = holder.itemView.findViewById(R.id.tv_collection_title);
                        TextView tvCount = holder.itemView.findViewById(R.id.tv_collection_count);
                        ImageView ivChevron = holder.itemView.findViewById(R.id.iv_collection_chevron);

                        tvEmoji.setText(col.emoji);
                        if (isDarkMode) {
                            GradientDrawable emojiBg = new GradientDrawable();
                            emojiBg.setColor(boxBg);
                            emojiBg.setCornerRadius(10f * getResources().getDisplayMetrics().density);
                            emojiBg.setStroke((int)(1f * getResources().getDisplayMetrics().density), borderC);
                            tvEmoji.setBackground(emojiBg);
                        }
                        tvTitle.setText(col.name);
                        tvTitle.setTextColor(textPri);

                        String countText = col.size() + (col.size() > 1 ? " bancs" : " banc");
                        if (!col.description.isEmpty()) {
                            countText += " • " + col.description;
                        }
                        tvCount.setText(countText);
                        tvCount.setTextColor(textSec);
                        ivChevron.setColorFilter(textSec);

                        holder.itemView.setOnClickListener(v -> {
                            triggerHapticTick();
                            showSingle(col);
                        });
                    }

                    @Override
                    public int getItemCount() {
                        return cols.size();
                    }
                });
            }

            void showSingle(BenchCollection col) {
                layoutViewCollections.setVisibility(View.GONE);
                layoutViewSingleCollection.setVisibility(View.VISIBLE);

                tvDetailColTitle.setText(col.getDisplayTitle());
                String countText = col.size() + (col.size() > 1 ? " bancs enregistrés" : " banc enregistré");
                tvDetailColSubtitle.setText(countText);

                if (!col.isSystemList()) {
                    btnDeleteCollection.setVisibility(View.VISIBLE);
                    btnDeleteCollection.setOnClickListener(v -> {
                        triggerHapticTick();
                        collectionManager.deleteCollection(col.id);
                        Toast.makeText(MainActivity.this, "Liste supprimée", Toast.LENGTH_SHORT).show();
                        showAll();
                    });
                } else {
                    btnDeleteCollection.setVisibility(View.GONE);
                }

                btnBackToCollections.setOnClickListener(v -> {
                    triggerHapticTick();
                    showAll();
                });

                List<SavedBench> benches = collectionManager.getBenchesInCollection(col.id);
                if (benches.isEmpty()) {
                    layoutEmptySingleCollection.setVisibility(View.VISIBLE);
                    rvCollectionBenches.setVisibility(View.GONE);
                } else {
                    layoutEmptySingleCollection.setVisibility(View.GONE);
                    rvCollectionBenches.setVisibility(View.VISIBLE);

                    Location userLoc = getBestDeviceLocation();

                    rvCollectionBenches.setLayoutManager(new LinearLayoutManager(MainActivity.this));
                    rvCollectionBenches.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                        @NonNull
                        @Override
                        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                            View itemView = getLayoutInflater().inflate(R.layout.item_collection_bench, parent, false);
                            return new RecyclerView.ViewHolder(itemView) {};
                        }

                        @Override
                        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                            SavedBench sb = benches.get(position);
                            CardView cardThumb = holder.itemView.findViewById(R.id.card_bench_thumb);
                            if (cardThumb != null) cardThumb.setCardBackgroundColor(boxBg);
                            ImageView ivThumb = holder.itemView.findViewById(R.id.iv_bench_thumb);
                            TextView tvTitle = holder.itemView.findViewById(R.id.tv_bench_title);
                            TextView tvSub = holder.itemView.findViewById(R.id.tv_bench_sub);
                            TextView tvNote = holder.itemView.findViewById(R.id.tv_bench_note_preview);
                            TextView tvDist = holder.itemView.findViewById(R.id.tv_bench_dist);

                            tvTitle.setText(sb.name);
                            tvTitle.setTextColor(textPri);
                            tvSub.setText(sb.subtitle);
                            tvSub.setTextColor(textSec);

                            if (sb.hasPhotos()) {
                                Bitmap bmp = BenchCollectionManager.loadThumbnail(MainActivity.this, sb.getPrimaryPhoto(), 120);
                                if (bmp != null) {
                                    ivThumb.setPadding(0, 0, 0, 0);
                                    ivThumb.setColorFilter(null);
                                    ivThumb.setImageBitmap(bmp);
                                } else {
                                    ivThumb.setPadding(30, 30, 30, 30);
                                    ivThumb.setImageResource(R.drawable.ic_bookmark_filled);
                                    ivThumb.setColorFilter(sb.isCustom ? Color.parseColor("#FF6D00") : Color.parseColor("#E52B35"));
                                }
                            } else {
                                ivThumb.setPadding(30, 30, 30, 30);
                                ivThumb.setImageResource(R.drawable.ic_bookmark_filled);
                                ivThumb.setColorFilter(sb.isCustom ? Color.parseColor("#FF6D00") : Color.parseColor("#E52B35"));
                            }

                            if (sb.hasNote()) {
                                tvNote.setText("« " + sb.note + " »");
                                tvNote.setVisibility(View.VISIBLE);
                            } else {
                                tvNote.setVisibility(View.GONE);
                            }

                            if (userLoc != null) {
                                double dMeters = MontrealBenchMapView.computeDistance(userLoc.getLatitude(), userLoc.getLongitude(), sb.lat, sb.lon);
                                if (dMeters < 1000) {
                                    tvDist.setText(String.format(Locale.CANADA_FRENCH, "%.0f m", dMeters));
                                } else {
                                    tvDist.setText(String.format(Locale.CANADA_FRENCH, "%.1f km", dMeters / 1000.0));
                                }
                                tvDist.setVisibility(View.VISIBLE);
                            } else {
                                tvDist.setVisibility(View.GONE);
                            }

                            holder.itemView.setOnClickListener(v -> {
                                triggerHapticTick();
                                dialog.dismiss();
                                Bench match = benchMapView.findBenchById(sb.benchId);
                                if (match != null) {
                                    benchMapView.focusBench(match);
                                } else {
                                    Bench fallback = sb.toBench();
                                    benchMapView.focusBench(fallback);
                                }
                            });
                        }

                        @Override
                        public int getItemCount() {
                            return benches.size();
                        }
                    });
                }
            }
        }

        CollectionsViewController controller = new CollectionsViewController();
        btnNewCollection.setOnClickListener(v -> {
            triggerHapticTick();
            showNewCollectionDialog(controller::showAll);
        });

        controller.showAll();
        dialog.show();
    }

    private void showSaveBenchDialog(Bench bench) {
        if (bench == null) return;
        final String bId = bench.getId();
        final SavedBench existing = collectionManager.getSavedBench(bId);
        final List<BenchCollection> allCols = collectionManager.getCollections();
        final List<BenchCollection> benchCols = collectionManager.getCollectionsForBench(bId);

        final List<String> selectedColIds = new ArrayList<>();
        if (existing != null) {
            for (BenchCollection c : benchCols) {
                selectedColIds.add(c.id);
            }
        } else {
            selectedColIds.add(BenchCollection.DEFAULT_ID);
        }

        final List<String> workingPhotos = (existing != null) ? new ArrayList<>(existing.photoPaths) : new ArrayList<>();
        this.currentWorkingPhotos = workingPhotos;
        this.activePhotoTargetBenchId = bId;

        final BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_save_bench, null);
        dialog.setContentView(view);

        int bgDialog = isDarkMode ? Color.parseColor("#181A20") : Color.parseColor("#FFFFFF");
        int textPri = isDarkMode ? Color.parseColor("#F4F5F7") : Color.parseColor("#111318");
        int textSec = isDarkMode ? Color.parseColor("#8E93A0") : Color.parseColor("#667085");
        int borderC = isDarkMode ? Color.parseColor("#262932") : Color.parseColor("#E4E7EC");
        int boxBg = isDarkMode ? Color.parseColor("#20232B") : Color.parseColor("#F2F4F7");

        View root = view.findViewById(R.id.dialog_save_bench_root);
        if (root != null) {
            GradientDrawable gd = new GradientDrawable();
            gd.setColor(bgDialog);
            gd.setCornerRadii(new float[]{48, 48, 48, 48, 0, 0, 0, 0});
            root.setBackground(gd);
        }

        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            int navBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            float d = getResources().getDisplayMetrics().density;
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), navBottom + (int)(28 * d));
            return insets;
        });

        TextView tvSaveTitle = view.findViewById(R.id.tv_save_title);
        TextView tvSaveBenchName = view.findViewById(R.id.tv_save_bench_name);
        ImageView btnSaveDialogClose = view.findViewById(R.id.btn_save_dialog_close);
        LinearLayout layoutCollectionsCheckboxes = view.findViewById(R.id.layout_collections_checkboxes);
        TextView btnInlineNewCollection = view.findViewById(R.id.btn_inline_new_collection);
        EditText etBenchNote = view.findViewById(R.id.et_bench_note);
        LinearLayout layoutPhotosStrip = view.findViewById(R.id.layout_photos_strip);
        MaterialCardView btnAddPhoto = view.findViewById(R.id.btn_add_photo);
        ImageView ivAddPhotoIcon = view.findViewById(R.id.iv_add_photo_icon);
        TextView tvAddPhotoLabel = view.findViewById(R.id.tv_add_photo_label);
        MaterialButton btnSaveBenchConfirm = view.findViewById(R.id.btn_save_bench_confirm);
        MaterialButton btnRemoveSavedBench = view.findViewById(R.id.btn_remove_saved_bench);

        if (tvSaveTitle != null) tvSaveTitle.setTextColor(textPri);
        if (tvSaveBenchName != null) {
            tvSaveBenchName.setText(bench.getDisplayName() + " • " + bench.getDisplaySubtitle());
            tvSaveBenchName.setTextColor(textSec);
        }
        if (btnSaveDialogClose != null) {
            btnSaveDialogClose.setColorFilter(textSec);
            btnSaveDialogClose.setOnClickListener(v -> dialog.dismiss());
        }

        if (btnAddPhoto != null) {
            btnAddPhoto.setCardBackgroundColor(boxBg);
            btnAddPhoto.setStrokeColor(borderC);
        }
        if (ivAddPhotoIcon != null) {
            ivAddPhotoIcon.setColorFilter(textPri);
        }
        if (tvAddPhotoLabel != null) {
            tvAddPhotoLabel.setTextColor(textPri);
        }

        if (etBenchNote != null) {
            if (existing != null && existing.hasNote()) {
                etBenchNote.setText(existing.note);
            }
            GradientDrawable etBg = new GradientDrawable();
            etBg.setColor(boxBg);
            etBg.setCornerRadius(20);
            etBg.setStroke(2, borderC);
            etBenchNote.setBackground(etBg);
            etBenchNote.setTextColor(textPri);
            etBenchNote.setHintTextColor(textSec);
        }

        dialog.setOnShowListener(d -> {
            View bs = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bs != null) {
                GradientDrawable bsBg = new GradientDrawable();
                bsBg.setColor(bgDialog);
                bsBg.setCornerRadii(new float[]{48, 48, 48, 48, 0, 0, 0, 0});
                bs.setBackground(bsBg);
                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bs);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                behavior.setSkipCollapsed(true);
            }
            if (dialog.getWindow() != null) {
                dialog.getWindow().setNavigationBarColor(bgDialog);
            }
        });

        Runnable refreshCheckboxes = () -> {
            layoutCollectionsCheckboxes.removeAllViews();
            List<BenchCollection> cols = collectionManager.getCollections();
            for (BenchCollection col : cols) {
                LinearLayout row = new LinearLayout(MainActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(android.view.Gravity.CENTER_VERTICAL);
                row.setPadding(0, 8, 0, 8);

                CheckBox cb = new CheckBox(MainActivity.this);
                cb.setChecked(selectedColIds.contains(col.id));
                cb.setButtonTintList(ColorStateList.valueOf(Color.parseColor("#E52B35")));

                TextView tvTitle = new TextView(MainActivity.this);
                tvTitle.setText(col.getDisplayTitle());
                tvTitle.setTextColor(textPri);
                tvTitle.setTextSize(14);
                tvTitle.setTypeface(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD);
                tvTitle.setPadding(8, 0, 0, 0);

                TextView tvCount = new TextView(MainActivity.this);
                tvCount.setText(" (" + col.size() + ")");
                tvCount.setTextColor(textSec);
                tvCount.setTextSize(12);

                row.addView(cb);
                row.addView(tvTitle);
                row.addView(tvCount);

                View.OnClickListener toggle = v -> {
                    cb.setChecked(!cb.isChecked());
                    if (cb.isChecked()) {
                        if (!selectedColIds.contains(col.id)) selectedColIds.add(col.id);
                    } else {
                        selectedColIds.remove(col.id);
                    }
                };
                cb.setOnCheckedChangeListener((btn, checked) -> {
                    if (checked) {
                        if (!selectedColIds.contains(col.id)) selectedColIds.add(col.id);
                    } else {
                        selectedColIds.remove(col.id);
                    }
                });
                tvTitle.setOnClickListener(toggle);
                layoutCollectionsCheckboxes.addView(row);
            }
        };
        refreshCheckboxes.run();

        btnInlineNewCollection.setOnClickListener(v -> {
            triggerHapticTick();
            showNewCollectionDialog(() -> {
                List<BenchCollection> updatedList = collectionManager.getCollections();
                if (!updatedList.isEmpty()) {
                    BenchCollection newest = updatedList.get(updatedList.size() - 1);
                    selectedColIds.add(newest.id);
                }
                refreshCheckboxes.run();
            });
        });

        Runnable refreshPhotos = new Runnable() {
            @Override
            public void run() {
                for (int i = layoutPhotosStrip.getChildCount() - 1; i >= 0; i--) {
                    View child = layoutPhotosStrip.getChildAt(i);
                    if (child != btnAddPhoto) {
                        layoutPhotosStrip.removeViewAt(i);
                    }
                }
                for (String photoPath : workingPhotos) {
                    View thumbView = getLayoutInflater().inflate(R.layout.item_photo_thumb, layoutPhotosStrip, false);
                    MaterialCardView cvThumb = thumbView.findViewById(R.id.cv_thumb);
                    if (cvThumb != null) {
                        cvThumb.setCardBackgroundColor(boxBg);
                        cvThumb.setStrokeColor(borderC);
                    }
                    ImageView iv = thumbView.findViewById(R.id.iv_thumb);
                    View btnDel = thumbView.findViewById(R.id.btn_delete_thumb);

                    Bitmap bmp = BenchCollectionManager.loadThumbnail(MainActivity.this, photoPath, 140);
                    if (bmp != null) {
                        iv.setImageBitmap(bmp);
                    }
                    iv.setOnClickListener(v -> showPhotoViewerDialog(photoPath));
                    btnDel.setOnClickListener(v -> {
                        triggerHapticTick();
                        workingPhotos.remove(photoPath);
                        run();
                    });
                    layoutPhotosStrip.addView(thumbView);
                }
            }
        };
        refreshPhotos.run();
        this.photoAddedCallback = refreshPhotos;

        btnAddPhoto.setOnClickListener(v -> {
            triggerHapticTick();
            showPhotoSourcePicker();
        });

        if (existing != null || collectionManager.isBenchSaved(bId) || bench.isCustom) {
            btnRemoveSavedBench.setVisibility(View.VISIBLE);
            btnRemoveSavedBench.setText(bench.isCustom ? "Supprimer ce banc" : "Retirer de tous les favoris");
            btnRemoveSavedBench.setOnClickListener(v -> {
                triggerHapticTick();
                collectionManager.removeBenchCompletely(bId);
                if (bench.isCustom) {
                    benchMapView.removeCustomBench(bId);
                    onBenchDeselected();
                }
                dialog.dismiss();
                updateDetailCardSavedState(bench);
                Toast.makeText(MainActivity.this, bench.isCustom ? "Banc supprimé" : "Banc retiré de vos favoris", Toast.LENGTH_SHORT).show();
            });
        } else {
            btnRemoveSavedBench.setVisibility(View.GONE);
        }

        btnSaveBenchConfirm.setOnClickListener(v -> {
            triggerHapticTick();
            String noteText = etBenchNote.getText().toString().trim();
            SavedBench updated = new SavedBench(
                    bId, bench.lat, bench.lon, bench.getDisplayName(), bench.getDisplaySubtitle(),
                    bench.park, bench.street, bench.borough,
                    noteText, workingPhotos,
                    (existing != null) ? existing.addedAt : System.currentTimeMillis(),
                    System.currentTimeMillis(),
                    bench.isCustom, bench.material, bench.backrest, bench.seats
            );
            collectionManager.saveBenchWithCollections(updated, selectedColIds);
            dialog.dismiss();
            updateDetailCardSavedState(bench);
            Toast.makeText(MainActivity.this, "Banc mis à jour", Toast.LENGTH_SHORT).show();
        });

        dialog.show();
    }

    private void showAddCustomBenchDialog(double lat, double lon) {
        final String provisionalId = "custom_" + Bench.toBenchId(lat, lon);
        final List<String> customPhotos = new ArrayList<>();
        this.currentWorkingPhotos = customPhotos;
        this.activePhotoTargetBenchId = provisionalId;

        final BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_add_custom_bench, null);
        dialog.setContentView(view);

        int bgDialog = isDarkMode ? Color.parseColor("#181A20") : Color.parseColor("#FFFFFF");
        int textPri = isDarkMode ? Color.parseColor("#F4F5F7") : Color.parseColor("#111318");
        int textSec = isDarkMode ? Color.parseColor("#8E93A0") : Color.parseColor("#667085");
        int borderC = isDarkMode ? Color.parseColor("#262932") : Color.parseColor("#E4E7EC");
        int boxBg = isDarkMode ? Color.parseColor("#20232B") : Color.parseColor("#F2F4F7");

        View root = view.findViewById(R.id.dialog_add_custom_bench_root);
        if (root != null) {
            GradientDrawable gd = new GradientDrawable();
            gd.setColor(bgDialog);
            gd.setCornerRadii(new float[]{48, 48, 48, 48, 0, 0, 0, 0});
            root.setBackground(gd);
        }

        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            int navBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            float d = getResources().getDisplayMetrics().density;
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), navBottom + (int)(28 * d));
            return insets;
        });

        TextView tvTitle = view.findViewById(R.id.tv_custom_dialog_title);
        TextView tvCoords = view.findViewById(R.id.tv_custom_bench_coords);
        ImageView btnClose = view.findViewById(R.id.btn_custom_dialog_close);
        EditText etName = view.findViewById(R.id.et_custom_bench_name);
        EditText etLocation = view.findViewById(R.id.et_custom_bench_location);
        EditText etNote = view.findViewById(R.id.et_custom_bench_note);

        TextView chipWood = view.findViewById(R.id.chip_mat_wood);
        TextView chipMetal = view.findViewById(R.id.chip_mat_metal);
        TextView chipConcrete = view.findViewById(R.id.chip_mat_concrete);
        TextView chipStone = view.findViewById(R.id.chip_mat_stone);
        TextView chipOther = view.findViewById(R.id.chip_mat_other);

        TextView chipBackrestYes = view.findViewById(R.id.chip_backrest_yes);
        TextView chipBackrestNo = view.findViewById(R.id.chip_backrest_no);

        LinearLayout layoutPhotosStrip = view.findViewById(R.id.layout_custom_photos_strip);
        MaterialCardView btnAddPhoto = view.findViewById(R.id.btn_custom_add_photo);
        ImageView ivAddPhotoIcon = view.findViewById(R.id.iv_custom_add_photo_icon);
        TextView tvAddPhotoLabel = view.findViewById(R.id.tv_custom_add_photo_label);

        MaterialButton btnConfirm = view.findViewById(R.id.btn_custom_bench_confirm);

        if (tvTitle != null) tvTitle.setTextColor(textPri);
        if (tvCoords != null) {
            tvCoords.setText(String.format(Locale.US, "◆ %.5f° N, %.5f° W", lat, Math.abs(lon)));
            tvCoords.setTextColor(Color.parseColor("#FF6D00"));
        }
        if (btnClose != null) {
            btnClose.setColorFilter(textSec);
            btnClose.setOnClickListener(v -> dialog.dismiss());
        }

        float d = getResources().getDisplayMetrics().density;
        EditText[] editTexts = new EditText[]{etName, etLocation, etNote};
        for (EditText et : editTexts) {
            if (et != null) {
                GradientDrawable etBg = new GradientDrawable();
                etBg.setColor(boxBg);
                etBg.setCornerRadius(12f * d);
                etBg.setStroke((int) (1f * d), borderC);
                et.setBackground(etBg);
                et.setTextColor(textPri);
                et.setHintTextColor(textSec);
            }
        }

        final String[] selectedMaterial = new String[]{"Bois"};
        TextView[] matChips = new TextView[]{chipWood, chipMetal, chipConcrete, chipStone, chipOther};
        String[] matNames = new String[]{"Bois", "Métal", "Béton", "Pierre", "Autre"};

        Runnable refreshMatChips = () -> {
            for (int i = 0; i < matChips.length; i++) {
                TextView c = matChips[i];
                if (c == null) continue;
                boolean isSel = matNames[i].equalsIgnoreCase(selectedMaterial[0]);
                GradientDrawable cBg = new GradientDrawable();
                cBg.setCornerRadius(10f * d);
                if (isSel) {
                    cBg.setColor(Color.parseColor("#FF6D00"));
                    c.setTextColor(Color.WHITE);
                } else {
                    cBg.setColor(boxBg);
                    cBg.setStroke((int) (1f * d), borderC);
                    c.setTextColor(textPri);
                }
                c.setBackground(cBg);
            }
        };
        for (int i = 0; i < matChips.length; i++) {
            final int idx = i;
            if (matChips[i] != null) {
                matChips[i].setOnClickListener(v -> {
                    triggerHapticTick();
                    selectedMaterial[0] = matNames[idx];
                    refreshMatChips.run();
                });
            }
        }
        refreshMatChips.run();

        final int[] selectedBackrest = new int[]{1};
        Runnable refreshBackrestChips = () -> {
            boolean yes = selectedBackrest[0] == 1;
            if (chipBackrestYes != null) {
                GradientDrawable bgYes = new GradientDrawable();
                bgYes.setCornerRadius(10f * d);
                if (yes) {
                    bgYes.setColor(Color.parseColor("#FF6D00"));
                    chipBackrestYes.setTextColor(Color.WHITE);
                } else {
                    bgYes.setColor(boxBg);
                    bgYes.setStroke((int) (1f * d), borderC);
                    chipBackrestYes.setTextColor(textPri);
                }
                chipBackrestYes.setBackground(bgYes);
            }
            if (chipBackrestNo != null) {
                GradientDrawable bgNo = new GradientDrawable();
                bgNo.setCornerRadius(10f * d);
                if (!yes) {
                    bgNo.setColor(Color.parseColor("#FF6D00"));
                    chipBackrestNo.setTextColor(Color.WHITE);
                } else {
                    bgNo.setColor(boxBg);
                    bgNo.setStroke((int) (1f * d), borderC);
                    chipBackrestNo.setTextColor(textPri);
                }
                chipBackrestNo.setBackground(bgNo);
            }
        };
        if (chipBackrestYes != null) {
            chipBackrestYes.setOnClickListener(v -> {
                triggerHapticTick();
                selectedBackrest[0] = 1;
                refreshBackrestChips.run();
            });
        }
        if (chipBackrestNo != null) {
            chipBackrestNo.setOnClickListener(v -> {
                triggerHapticTick();
                selectedBackrest[0] = 0;
                refreshBackrestChips.run();
            });
        }
        refreshBackrestChips.run();

        if (btnAddPhoto != null) {
            btnAddPhoto.setCardBackgroundColor(boxBg);
            btnAddPhoto.setStrokeColor(borderC);
        }
        if (ivAddPhotoIcon != null) ivAddPhotoIcon.setColorFilter(textPri);
        if (tvAddPhotoLabel != null) tvAddPhotoLabel.setTextColor(textPri);

        Runnable refreshPhotos = new Runnable() {
            @Override
            public void run() {
                if (layoutPhotosStrip == null) return;
                for (int i = layoutPhotosStrip.getChildCount() - 1; i >= 0; i--) {
                    View child = layoutPhotosStrip.getChildAt(i);
                    if (child != btnAddPhoto) {
                        layoutPhotosStrip.removeViewAt(i);
                    }
                }
                for (String photoPath : customPhotos) {
                    View thumbView = getLayoutInflater().inflate(R.layout.item_photo_thumb, layoutPhotosStrip, false);
                    MaterialCardView cvThumb = thumbView.findViewById(R.id.cv_thumb);
                    if (cvThumb != null) {
                        cvThumb.setCardBackgroundColor(boxBg);
                        cvThumb.setStrokeColor(borderC);
                    }
                    ImageView iv = thumbView.findViewById(R.id.iv_thumb);
                    View btnDel = thumbView.findViewById(R.id.btn_delete_thumb);

                    Bitmap bmp = BenchCollectionManager.loadThumbnail(MainActivity.this, photoPath, 140);
                    if (bmp != null) {
                        iv.setImageBitmap(bmp);
                    }
                    iv.setOnClickListener(v -> showPhotoViewerDialog(photoPath));
                    btnDel.setOnClickListener(v -> {
                        triggerHapticTick();
                        customPhotos.remove(photoPath);
                        run();
                    });
                    layoutPhotosStrip.addView(thumbView);
                }
            }
        };
        this.photoAddedCallback = refreshPhotos;

        if (btnAddPhoto != null) {
            btnAddPhoto.setOnClickListener(v -> {
                triggerHapticTick();
                showPhotoSourcePicker();
            });
        }

        dialog.setOnShowListener(d1 -> {
            View bs = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bs != null) {
                GradientDrawable bsBg = new GradientDrawable();
                bsBg.setColor(bgDialog);
                bsBg.setCornerRadii(new float[]{48, 48, 48, 48, 0, 0, 0, 0});
                bs.setBackground(bsBg);
                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bs);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                behavior.setSkipCollapsed(true);
            }
            if (dialog.getWindow() != null) {
                dialog.getWindow().setNavigationBarColor(bgDialog);
            }
        });

        btnConfirm.setOnClickListener(v -> {
            triggerHapticTick();
            String name = (etName != null) ? etName.getText().toString().trim() : "";
            if (name.isEmpty()) {
                name = "Mon banc public";
            }
            String location = (etLocation != null) ? etLocation.getText().toString().trim() : "";
            String note = (etNote != null) ? etNote.getText().toString().trim() : "";

            Bench newBench = collectionManager.addCustomBench(
                    lat, lon, name, location, "Montréal",
                    selectedMaterial[0], selectedBackrest[0], note, customPhotos
            );

            benchMapView.addCustomBench(newBench);
            benchMapView.focusBench(newBench);
            dialog.dismiss();
            Toast.makeText(MainActivity.this, "Banc ajouté à « Mes bancs » ◆", Toast.LENGTH_SHORT).show();
        });

        dialog.show();
    }

    private void showPhotoSourcePicker() {
        BottomSheetDialog pickerDialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_photo_source_picker, null);
        pickerDialog.setContentView(view);

        int bgDialog = isDarkMode ? Color.parseColor("#181A20") : Color.parseColor("#FFFFFF");
        int cardBg = isDarkMode ? Color.parseColor("#20232B") : Color.parseColor("#F8F9FA");
        int cardBorder = isDarkMode ? Color.parseColor("#2C303B") : Color.parseColor("#E4E7EC");
        int textPri = isDarkMode ? Color.parseColor("#F4F5F7") : Color.parseColor("#111318");
        int textSec = isDarkMode ? Color.parseColor("#8E93A0") : Color.parseColor("#667085");

        View root = view.findViewById(R.id.dialog_photo_picker_root);
        if (root != null) {
            GradientDrawable gd = new GradientDrawable();
            gd.setColor(bgDialog);
            gd.setCornerRadii(new float[]{48, 48, 48, 48, 0, 0, 0, 0});
            root.setBackground(gd);
        }

        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            int navBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            float d = getResources().getDisplayMetrics().density;
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), navBottom + (int)(28 * d));
            return insets;
        });

        TextView tvTitle = view.findViewById(R.id.tv_photo_picker_title);
        TextView tvSubtitle = view.findViewById(R.id.tv_photo_picker_subtitle);
        MaterialCardView btnCam = view.findViewById(R.id.btn_photo_camera);
        MaterialCardView btnGal = view.findViewById(R.id.btn_photo_gallery);
        TextView tvCameraLabel = view.findViewById(R.id.tv_camera_label);
        TextView tvGalleryLabel = view.findViewById(R.id.tv_gallery_label);
        ImageView ivCamChevron = view.findViewById(R.id.iv_camera_chevron);
        ImageView ivGalChevron = view.findViewById(R.id.iv_gallery_chevron);
        MaterialButton btnCancel = view.findViewById(R.id.btn_photo_cancel);

        if (tvTitle != null) tvTitle.setTextColor(textPri);
        if (tvSubtitle != null) tvSubtitle.setTextColor(textSec);
        if (tvCameraLabel != null) tvCameraLabel.setTextColor(textPri);
        if (tvGalleryLabel != null) tvGalleryLabel.setTextColor(textPri);
        if (ivCamChevron != null) ivCamChevron.setColorFilter(textSec);
        if (ivGalChevron != null) ivGalChevron.setColorFilter(textSec);

        if (btnCam != null) {
            btnCam.setCardBackgroundColor(cardBg);
            btnCam.setStrokeColor(cardBorder);
            btnCam.setOnClickListener(v -> {
                triggerHapticTick();
                pickerDialog.dismiss();
                launchCameraCapture();
            });
        }

        if (btnGal != null) {
            btnGal.setCardBackgroundColor(cardBg);
            btnGal.setStrokeColor(cardBorder);
            btnGal.setOnClickListener(v -> {
                triggerHapticTick();
                pickerDialog.dismiss();
                pickImageLauncher.launch("image/*");
            });
        }

        if (btnCancel != null) {
            btnCancel.setTextColor(textSec);
            btnCancel.setOnClickListener(v -> {
                triggerHapticTick();
                pickerDialog.dismiss();
            });
        }

        pickerDialog.setOnShowListener(d -> {
            View bs = pickerDialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bs != null) {
                GradientDrawable bsBg = new GradientDrawable();
                bsBg.setColor(bgDialog);
                bsBg.setCornerRadii(new float[]{48, 48, 48, 48, 0, 0, 0, 0});
                bs.setBackground(bsBg);
                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bs);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                behavior.setSkipCollapsed(true);
            }
            if (pickerDialog.getWindow() != null) {
                pickerDialog.getWindow().setNavigationBarColor(bgDialog);
            }
        });
        pickerDialog.show();
    }

    private void launchCameraCapture() {
        try {
            File cacheDir = new File(getCacheDir(), "camera");
            cacheDir.mkdirs();
            pendingCameraFile = File.createTempFile("photo_", ".jpg", cacheDir);
            pendingCameraUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", pendingCameraFile);
            takePictureLauncher.launch(pendingCameraUri);
        } catch (Exception e) {
            Toast.makeText(this, "Impossible d'ouvrir l'appareil photo", Toast.LENGTH_SHORT).show();
        }
    }

    private void showNewCollectionDialog(Runnable onCreated) {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_new_collection, null);
        dialog.setContentView(view);

        int bgDialog = isDarkMode ? Color.parseColor("#181A20") : Color.parseColor("#FFFFFF");
        int textPri = isDarkMode ? Color.parseColor("#F4F5F7") : Color.parseColor("#111318");
        int textSec = isDarkMode ? Color.parseColor("#8E93A0") : Color.parseColor("#667085");
        int borderC = isDarkMode ? Color.parseColor("#262932") : Color.parseColor("#E4E7EC");
        int boxBg = isDarkMode ? Color.parseColor("#20232B") : Color.parseColor("#F2F4F7");

        View root = view.findViewById(R.id.dialog_new_col_root);
        if (root != null) {
            GradientDrawable gd = new GradientDrawable();
            gd.setColor(bgDialog);
            gd.setCornerRadii(new float[]{48, 48, 48, 48, 0, 0, 0, 0});
            root.setBackground(gd);
        }

        LinearLayout layoutEmojiPicker = view.findViewById(R.id.layout_emoji_picker);
        EditText etColName = view.findViewById(R.id.et_col_name);
        EditText etColDesc = view.findViewById(R.id.et_col_desc);
        MaterialButton btnConfirm = view.findViewById(R.id.btn_create_col_confirm);

        final String[] emojis = new String[]{"❤️", "⭐", "☕", "🌳", "📖", "🌅", "🥖", "🥪", "🎨", "🐾", "🍁", "🚴", "☀️", "🏙️", "🛋️"};
        final String[] selectedEmoji = new String[]{emojis[0]};

        final List<TextView> emojiViews = new ArrayList<>();
        for (String em : emojis) {
            TextView tv = new TextView(this);
            tv.setText(em);
            tv.setTextSize(22);
            tv.setGravity(android.view.Gravity.CENTER);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams((int) (48 * getResources().getDisplayMetrics().density), (int) (48 * getResources().getDisplayMetrics().density));
            lp.setMargins(0, 0, (int) (8 * getResources().getDisplayMetrics().density), 0);
            tv.setLayoutParams(lp);

            GradientDrawable gdEm = new GradientDrawable();
            gdEm.setCornerRadius(12 * getResources().getDisplayMetrics().density);
            if (em.equals(selectedEmoji[0])) {
                gdEm.setColor(Color.parseColor("#E52B35"));
            } else {
                gdEm.setColor(boxBg);
            }
            tv.setBackground(gdEm);
            tv.setOnClickListener(v -> {
                selectedEmoji[0] = em;
                for (int i = 0; i < emojiViews.size(); i++) {
                    GradientDrawable bg = new GradientDrawable();
                    bg.setCornerRadius(12 * getResources().getDisplayMetrics().density);
                    if (emojis[i].equals(selectedEmoji[0])) {
                        bg.setColor(Color.parseColor("#E52B35"));
                    } else {
                        bg.setColor(boxBg);
                    }
                    emojiViews.get(i).setBackground(bg);
                }
            });
            emojiViews.add(tv);
            layoutEmojiPicker.addView(tv);
        }

        if (etColName != null) {
            GradientDrawable etBg = new GradientDrawable();
            etBg.setColor(boxBg);
            etBg.setCornerRadius(20);
            etBg.setStroke(2, borderC);
            etColName.setBackground(etBg);
            etColName.setTextColor(textPri);
            etColName.setHintTextColor(textSec);
        }

        if (etColDesc != null) {
            GradientDrawable etBg2 = new GradientDrawable();
            etBg2.setColor(boxBg);
            etBg2.setCornerRadius(20);
            etBg2.setStroke(2, borderC);
            etColDesc.setBackground(etBg2);
            etColDesc.setTextColor(textPri);
            etColDesc.setHintTextColor(textSec);
        }

        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            int navBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            float d = getResources().getDisplayMetrics().density;
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), navBottom + (int)(24 * d));
            return insets;
        });

        dialog.setOnShowListener(d -> {
            View bs = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bs != null) {
                GradientDrawable bsBg = new GradientDrawable();
                bsBg.setColor(bgDialog);
                bsBg.setCornerRadii(new float[]{48, 48, 48, 48, 0, 0, 0, 0});
                bs.setBackground(bsBg);
                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bs);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                behavior.setSkipCollapsed(true);
            }
            if (dialog.getWindow() != null) {
                dialog.getWindow().setNavigationBarColor(bgDialog);
            }
        });

        btnConfirm.setOnClickListener(v -> {
            String name = etColName.getText().toString().trim();
            if (name.isEmpty()) {
                Toast.makeText(MainActivity.this, "Veuillez entrer un nom pour la liste", Toast.LENGTH_SHORT).show();
                return;
            }
            String desc = (etColDesc != null) ? etColDesc.getText().toString().trim() : "";
            collectionManager.createCollection(name, selectedEmoji[0], desc);
            dialog.dismiss();
            triggerHapticTick();
            Toast.makeText(MainActivity.this, "Liste « " + name + " » créée !", Toast.LENGTH_SHORT).show();
            if (onCreated != null) {
                onCreated.run();
            }
        });

        dialog.show();
    }

    private void showPhotoViewerDialog(String photoPath) {
        android.app.Dialog dialog = new android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        View view = getLayoutInflater().inflate(R.layout.dialog_photo_viewer, null);
        dialog.setContentView(view);

        ImageView iv = view.findViewById(R.id.iv_fullscreen_photo);
        View btnClose = view.findViewById(R.id.btn_close_photo);

        Bitmap bmp = BenchCollectionManager.loadThumbnail(this, photoPath, 1600);
        if (bmp != null) {
            iv.setImageBitmap(bmp);
        }
        btnClose.setOnClickListener(v -> dialog.dismiss());
        iv.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
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

    private void initRenderStyle() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String savedStyle = prefs.getString(PREF_RENDER_STYLE, "auto");
        if ("auto".equalsIgnoreCase(savedStyle)) {
            benchMapView.setRenderStyle(MapRenderStyle.getDailyStyle(), true);
        } else {
            benchMapView.setRenderStyle(MapRenderStyle.fromId(savedStyle), false);
        }
    }

    private void showMapStylesDialog() {
        final BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_map_styles, null);
        dialog.setContentView(view);

        int bgDialog = isDarkMode ? Color.parseColor("#181A20") : Color.parseColor("#FFFFFF");
        int textPri = isDarkMode ? Color.parseColor("#F4F5F7") : Color.parseColor("#101828");
        int textSec = isDarkMode ? Color.parseColor("#8E93A0") : Color.parseColor("#667085");
        int cardBg = isDarkMode ? Color.parseColor("#20232B") : Color.parseColor("#F8F9FA");
        int borderC = isDarkMode ? Color.parseColor("#2C303B") : Color.parseColor("#E4E7EC");

        View root = view.findViewById(R.id.dialog_styles_root);
        if (root != null) {
            GradientDrawable gd = new GradientDrawable();
            gd.setColor(bgDialog);
            gd.setCornerRadii(new float[]{48, 48, 48, 48, 0, 0, 0, 0});
            root.setBackground(gd);
        }

        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            int navBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            float d = getResources().getDisplayMetrics().density;
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), navBottom + (int)(24 * d));
            return insets;
        });

        dialog.setOnShowListener(d -> {
            View bs = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bs != null) {
                GradientDrawable bsBg = new GradientDrawable();
                bsBg.setColor(bgDialog);
                bsBg.setCornerRadii(new float[]{48, 48, 48, 48, 0, 0, 0, 0});
                bs.setBackground(bsBg);
            }
        });

        TextView tvTitle = view.findViewById(R.id.tv_styles_title);
        TextView tvSubtitle = view.findViewById(R.id.tv_styles_subtitle);
        tvTitle.setTextColor(textPri);
        tvSubtitle.setTextColor(textSec);

        MaterialCardView cardDaily = view.findViewById(R.id.card_daily_rotation);
        cardDaily.setCardBackgroundColor(cardBg);
        cardDaily.setStrokeColor(borderC);

        TextView tvDailyTitle = view.findViewById(R.id.tv_daily_switch_title);
        TextView tvDailySub = view.findViewById(R.id.tv_daily_switch_sub);
        tvDailyTitle.setTextColor(textPri);
        tvDailySub.setTextColor(textSec);

        SwitchMaterial switchDaily = view.findViewById(R.id.switch_daily_auto);
        switchDaily.setChecked(benchMapView.isDailyAuto());

        LinearLayout layoutList = view.findViewById(R.id.layout_styles_list);
        layoutList.removeAllViews();

        MapRenderStyle todayStyle = MapRenderStyle.getDailyStyle();
        boolean isAuto = benchMapView.isDailyAuto();

        MapRenderStyle[] allStyles = MapRenderStyle.values();

        for (MapRenderStyle style : allStyles) {
            View itemView = getLayoutInflater().inflate(R.layout.item_map_style, layoutList, false);
            MaterialCardView cardItem = itemView.findViewById(R.id.card_style_item);
            TextView tvEmoji = itemView.findViewById(R.id.tv_style_emoji);
            TextView tvItemTitle = itemView.findViewById(R.id.tv_style_title);
            TextView tvItemSub = itemView.findViewById(R.id.tv_style_subtitle);
            TextView badgeToday = itemView.findViewById(R.id.badge_today);
            ImageView ivCheck = itemView.findViewById(R.id.iv_style_check);

            tvEmoji.setText(style.emoji);
            tvItemTitle.setText(style.title);
            tvItemTitle.setTextColor(textPri);
            tvItemSub.setText(style.subtitle);
            tvItemSub.setTextColor(textSec);
            cardItem.setCardBackgroundColor(cardBg);
            cardItem.setStrokeColor(borderC);

            if (style == todayStyle) {
                badgeToday.setVisibility(View.VISIBLE);
            } else {
                badgeToday.setVisibility(View.GONE);
            }

            boolean isSelected = (!isAuto && benchMapView.getRenderStyle() == style) || (isAuto && style == todayStyle);
            ivCheck.setVisibility(isSelected ? View.VISIBLE : View.GONE);
            if (isSelected) {
                cardItem.setStrokeColor(Color.parseColor("#E52B35"));
            }

            cardItem.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                benchMapView.setRenderStyle(style, false);

                getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit()
                        .putString(PREF_RENDER_STYLE, style.id)
                        .apply();

                dialog.dismiss();
                Toast.makeText(this, "Style appliqué : " + style.title, Toast.LENGTH_SHORT).show();
            });

            layoutList.addView(itemView);
        }

        switchDaily.setOnCheckedChangeListener((buttonView, isChecked) -> {
            buttonView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            if (isChecked) {
                benchMapView.setRenderStyle(MapRenderStyle.getDailyStyle(), true);
                getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit()
                        .putString(PREF_RENDER_STYLE, "auto")
                        .apply();
                dialog.dismiss();
                Toast.makeText(this, "Rotation quotidienne activée (" + todayStyle.title + ")", Toast.LENGTH_SHORT).show();
            } else {
                benchMapView.setRenderStyle(benchMapView.getRenderStyle(), false);
                getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit()
                        .putString(PREF_RENDER_STYLE, benchMapView.getRenderStyle().id)
                        .apply();
            }
        });

        dialog.show();
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
